#include "include/api/RPC.h"
#include <utility>

#include "include/global/Configs.hpp"

#include <QLocalSocket>
#include <QDataStream>
#include <QAtomicInt>
#include <QMap>
#include <QMutex>
#include <QObject>
#include <QThread>

#include <mutex>
#include <condition_variable>
#include <chrono>
#include <atomic>
#include <exception>
#include <memory>

namespace API {

    class Client::LocalSocketChannel {

        struct PendingCall {
            std::mutex          mu;
            std::condition_variable cv;
            bool                done   = false;
            quint8              status = 1;
            QByteArray          data;
        };

        QThread      *io_thread;
        QObject      *io_anchor;
        QLocalSocket *sock = nullptr;
        QByteArray    read_buf;

        QAtomicInt    next_id{1};
        std::mutex    pending_mu;
        QMap<quint32, std::shared_ptr<PendingCall>> pending;

        std::atomic<bool> connected_{false};

        static constexpr int kIOTimeoutMs = 30000;

    public:
        static constexpr int CallOK = 0;
        static constexpr int CallNotConnected = -1919;

    private:

        void onReadyRead() {
            if (sock == nullptr) return;
            read_buf += sock->readAll();
            processBuffer();
        }

        void wakeAllWithError() {
            connected_.store(false, std::memory_order_release);
            std::lock_guard<std::mutex> lock(pending_mu);
            for (auto &call : pending) {
                std::lock_guard<std::mutex> cg(call->mu);
                call->done = true;
                call->cv.notify_one();
            }
            pending.clear();
        }

        void processBuffer() {
            while (read_buf.size() >= 9) {
                quint32 reqId, dataLen;
                quint8  status;
                {
                    QDataStream ds(read_buf);
                    ds.setByteOrder(QDataStream::LittleEndian);
                    ds >> reqId >> status >> dataLen;
                }

                qint64 totalSize = qint64(9) + dataLen;
                if (read_buf.size() < totalSize) break;

                QByteArray data = read_buf.mid(9, static_cast<int>(dataLen));
                read_buf.remove(0, totalSize);

                std::shared_ptr<PendingCall> call;
                {
                    std::lock_guard<std::mutex> lock(pending_mu);
                    call = pending.value(reqId, nullptr);
                    if (call) pending.remove(reqId);
                }
                if (call) {
                    std::lock_guard<std::mutex> cg(call->mu);
                    call->status = status;
                    call->data   = std::move(data);
                    call->done   = true;
                    call->cv.notify_one();
                }
            }
        }

    public:
        LocalSocketChannel() {
            io_thread = new QThread;
            io_anchor = new QObject;
            io_anchor->moveToThread(io_thread);
            io_thread->start();
        }

        ~LocalSocketChannel() {
            wakeAllWithError();

            QMetaObject::invokeMethod(io_anchor, [this]() {
                if (sock) {
                    sock->close();
                    delete sock;
                    sock = nullptr;
                }
                io_thread->quit();
            }, Qt::QueuedConnection);

            io_thread->wait();
            delete io_anchor;
            delete io_thread;
        }

        void Reconnect(QLocalSocket *newSock) {
            wakeAllWithError();

            newSock->setParent(nullptr);
            newSock->moveToThread(io_thread);

            QMetaObject::invokeMethod(io_anchor, [this, newSock]() {
                if (sock) {
                    sock->disconnect(io_anchor);
                    sock->close();
                    sock->deleteLater();
                }
                sock = newSock;
                read_buf.clear();
                QObject::connect(sock, &QLocalSocket::readyRead, io_anchor,
                    [this]() { onReadyRead(); });
                QObject::connect(sock, &QLocalSocket::disconnected, io_anchor,
                    [this]() { wakeAllWithError(); });
                if (sock->bytesAvailable() > 0) onReadyRead();
            }, Qt::QueuedConnection);

            connected_.store(true, std::memory_order_release);
        }

        int Call(const QString &methodName, const std::string &req,
                 std::vector<uint8_t> &rsp, int timeout_ms = 0) {
            if (!connected_.load(std::memory_order_acquire)) return CallNotConnected;

            const int ms = (timeout_ms > 0) ? timeout_ms : kIOTimeoutMs;

            quint32 reqId = static_cast<quint32>(next_id.fetchAndAddOrdered(1));

            auto methodBytes = methodName.toUtf8();
            auto reqBytes    = QByteArray::fromStdString(req);

            QByteArray frame;
            {
                QDataStream ds(&frame, QIODevice::WriteOnly);
                ds.setByteOrder(QDataStream::LittleEndian);
                ds << reqId;
                ds << static_cast<quint16>(methodBytes.size());
                ds.writeRawData(methodBytes.constData(), methodBytes.size());
                ds << static_cast<quint32>(reqBytes.size());
                ds.writeRawData(reqBytes.constData(), reqBytes.size());
            }

            auto call = std::make_shared<PendingCall>();
            {
                std::lock_guard<std::mutex> lock(pending_mu);
                pending[reqId] = call;
            }

            QMetaObject::invokeMethod(io_anchor, [this, frame]() {
                if (sock) sock->write(frame);
            }, Qt::QueuedConnection);

            std::unique_lock<std::mutex> lock(call->mu);
            bool ok = call->cv.wait_for(lock,
                std::chrono::milliseconds(ms),
                [&call] { return call->done; });
            lock.unlock();

            if (!ok) {
                bool claimedByReader = false;
                {
                    std::lock_guard<std::mutex> plock(pending_mu);
                    if (pending.remove(reqId) == 0) claimedByReader = true;
                }
                if (claimedByReader) {
                    lock.lock();
                    ok = call->cv.wait_for(lock,
                        std::chrono::milliseconds(ms),
                        [&call] { return call->done; });
                    lock.unlock();
                }
            }

            std::lock_guard<std::mutex> g(call->mu);
            if (!ok || call->status != 0) {
                if (ok && call->status != 0) {
                    MW_show_log("[Core error] " + QString::fromUtf8(call->data));
                    rsp.assign(call->data.begin(), call->data.end());
                }
                return 1;
            }
            rsp.assign(call->data.begin(), call->data.end());
            return 0;
        }
    };

    namespace {
        template <typename T>
        bool tryDeserialize(const std::vector<uint8_t> &resp, T &out) {
            try {
                out = spb::pb::deserialize<T>(resp);
                return true;
            } catch (const std::exception &e) {
                MW_show_log(QString("[RPC] dropped malformed response: ") + e.what());
                return false;
            } catch (...) {
                MW_show_log("[RPC] dropped malformed response");
                return false;
            }
        }
    }

    Client::~Client() = default;

    Client::Client() {
        this->channel = std::make_unique<LocalSocketChannel>();
    }

    void Client::Reconnect(QLocalSocket *socket) {
        channel->Reconnect(socket);
    }

#define NOT_OK      \
    *rpcOK = false; \
    MW_show_log(QString("IPC call failed (code %1)\n").arg(status));

    QString Client::Start(bool *rpcOK, const libcore::LoadConfigReq &request) {
        libcore::ErrorResp reply;
        std::vector<uint8_t> resp;
        auto status = channel->Call("Start", spb::pb::serialize<std::string>(request), resp);

        if (status == LocalSocketChannel::CallOK && tryDeserialize(resp, reply)) {
            *rpcOK = true;
            return QString::fromStdString(reply.error.value());
        } else {
            NOT_OK
            return "";
        }
    }

    QString Client::Stop(bool *rpcOK) {
        libcore::EmptyReq request;
        libcore::ErrorResp reply;
        std::vector<uint8_t> resp;
        auto status = channel->Call("Stop", spb::pb::serialize<std::string>(request), resp);

        if (status == LocalSocketChannel::CallOK && tryDeserialize(resp, reply)) {
            *rpcOK = true;
            return QString::fromStdString(reply.error.value());
        } else {
            NOT_OK
            return "";
        }
    }

    libcore::QueryStatsResp Client::QueryStats() {
        libcore::EmptyReq request;
        libcore::QueryStatsResp reply;
        std::vector<uint8_t> resp;
        auto status = channel->Call("QueryStats", spb::pb::serialize<std::string>(request), resp, 500);

        if (status == LocalSocketChannel::CallOK && tryDeserialize(resp, reply)) {
            return reply;
        }
        return {};
    }

    libcore::TestResp Client::Test(bool *rpcOK, const libcore::TestReq &request, QString *coreError) {
        libcore::TestResp reply;
        std::vector<uint8_t> resp;
        auto status = channel->Call("Test", spb::pb::serialize<std::string>(request), resp);

        if (status == LocalSocketChannel::CallOK && tryDeserialize(resp, reply)) {
            *rpcOK = true;
            return reply;
        } else {
            if (coreError && !resp.empty())
                *coreError = QString::fromUtf8(reinterpret_cast<const char *>(resp.data()), static_cast<int>(resp.size()));
            NOT_OK
            return {};
        }
    }

    void Client::StopTests(bool *rpcOK) {
        const libcore::EmptyReq request;
        std::vector<uint8_t> resp;
        auto status = channel->Call("StopTest", spb::pb::serialize<std::string>(request), resp);

        if (status == LocalSocketChannel::CallOK) {
            *rpcOK = true;
        } else {
            NOT_OK
        }
    }

    libcore::QueryURLTestResponse Client::QueryURLTest(bool *rpcOK)
    {
        libcore::EmptyReq request;
        libcore::QueryURLTestResponse reply;
        std::vector<uint8_t> resp;
        auto status = channel->Call("QueryURLTest", spb::pb::serialize<std::string>(request), resp);

        if (status == LocalSocketChannel::CallOK && tryDeserialize(resp, reply)) {
            *rpcOK = true;
            return reply;
        } else {
            NOT_OK
            return {};
        }
    }

    libcore::IPTestResp Client::IPTest(bool *rpcOK, const libcore::IPTestRequest &request, QString *coreError) {
        libcore::IPTestResp reply;
        std::vector<uint8_t> resp;
        auto status = channel->Call("IPTest", spb::pb::serialize<std::string>(request), resp);

        if (status == LocalSocketChannel::CallOK && tryDeserialize(resp, reply)) {
            *rpcOK = true;
            return reply;
        } else {
            if (coreError && !resp.empty())
                *coreError = QString::fromUtf8(reinterpret_cast<const char *>(resp.data()), static_cast<int>(resp.size()));
            NOT_OK
            return {};
        }
    }

    libcore::QueryIPTestResponse Client::QueryIPTest(bool *rpcOK) {
        libcore::EmptyReq request;
        libcore::QueryIPTestResponse reply;
        std::vector<uint8_t> resp;
        auto status = channel->Call("QueryIPTest", spb::pb::serialize<std::string>(request), resp);

        if (status == LocalSocketChannel::CallOK && tryDeserialize(resp, reply)) {
            *rpcOK = true;
            return reply;
        } else {
            NOT_OK
            return {};
        }
    }

    libcore::GetDefaultInterfaceResponse Client::GetDefaultInterface(bool *rpcOK) const {
        libcore::EmptyReq request;
        libcore::GetDefaultInterfaceResponse reply;
        std::vector<uint8_t> resp;
        auto status = channel->Call("GetDefaultInterface", spb::pb::serialize<std::string>(request), resp);

        if (status == LocalSocketChannel::CallOK && tryDeserialize(resp, reply)) {
            *rpcOK = true;
            return reply;
        } else {
            NOT_OK
            return {};
        }
    }

    libcore::QueryAutoSelectorsResponse Client::QueryAutoSelectors(bool *rpcOK) const {
        libcore::EmptyReq request;
        libcore::QueryAutoSelectorsResponse reply;
        std::vector<uint8_t> resp;
        auto status = channel->Call("QueryAutoSelectors", spb::pb::serialize<std::string>(request), resp);

        if (status == LocalSocketChannel::CallOK && tryDeserialize(resp, reply)) {
            *rpcOK = true;
            return reply;
        } else {
            NOT_OK
            return {};
        }
    }

    QString Client::AutoSelectorAction(bool *rpcOK, const QString &tag, const QString &action,
                                       const QString &member) const {
        libcore::AutoSelectorActionRequest request;
        request.tag = tag.toStdString();
        request.action = action.toStdString();
        request.member = member.toStdString();
        libcore::ErrorResp reply;
        std::vector<uint8_t> resp;
        auto status = channel->Call("AutoSelectorAction", spb::pb::serialize<std::string>(request), resp);

        if (status == LocalSocketChannel::CallOK && tryDeserialize(resp, reply)) {
            *rpcOK = true;
            return QString::fromStdString(reply.error.value());
        } else {
            NOT_OK
            return "IPC error";
        }
    }

    QString Client::SetSystemDNS(bool *rpcOK, const bool clear) const {
        libcore::SetSystemDNSRequest request{clear};
        std::vector<uint8_t> resp;
        auto status = channel->Call("SetSystemDNS", spb::pb::serialize<std::string>(request), resp);

        if (status == LocalSocketChannel::CallOK) {
            *rpcOK = true;
            return "";
        } else {
            NOT_OK
            return "IPC error";
        }
    }

    libcore::QueryConnectionsResp Client::QueryConnections() const
    {
        libcore::EmptyReq request;
        libcore::QueryConnectionsResp reply;
        std::vector<uint8_t> resp;
        auto status = channel->Call("QueryConnections", spb::pb::serialize<std::string>(request), resp);

        if (status == LocalSocketChannel::CallOK && tryDeserialize(resp, reply)) {
            return reply;
        }
        if (status != LocalSocketChannel::CallOK) MW_show_log("Failed to query connections: IPC error");
        return {};
    }

    QString Client::CheckConfig(bool* rpcOK, const QString& config, bool isXray) const
    {
        libcore::LoadConfigReq request;
        if (isXray)
        {
            request.need_xray = true;
            request.xray_config = config.toStdString();
        } else
        {
            request.core_config = config.toStdString();
        }
        libcore::ErrorResp reply;
        std::vector<uint8_t> resp;
        auto status = channel->Call("CheckConfig", spb::pb::serialize<std::string>(request), resp);

        if (status == LocalSocketChannel::CallOK && tryDeserialize(resp, reply))
        {
            *rpcOK = true;
            return QString::fromStdString(reply.error.value());
        } else
        {
            NOT_OK
            return "IPC error";
        }
    }

    bool Client::IsPrivileged(bool* rpcOK) const
    {
        libcore::EmptyReq request;
        libcore::IsPrivilegedResponse reply;
        std::vector<uint8_t> resp;
        auto status = channel->Call("IsPrivileged", spb::pb::serialize<std::string>(request), resp);

        if (status == LocalSocketChannel::CallOK && tryDeserialize(resp, reply))
        {
            *rpcOK = true;
            return reply.has_privilege.value();
        } else
        {
            NOT_OK
            return false;
        }
    }

    libcore::SpeedTestResponse Client::SpeedTest(bool *rpcOK, const libcore::SpeedTestRequest &request, QString *coreError)
    {
        libcore::SpeedTestResponse reply;
        std::vector<uint8_t> resp;
        auto status = channel->Call("SpeedTest", spb::pb::serialize<std::string>(request), resp);

        if (status == LocalSocketChannel::CallOK && tryDeserialize(resp, reply)) {
            *rpcOK = true;
            return reply;
        } else {
            if (coreError && !resp.empty())
                *coreError = QString::fromUtf8(reinterpret_cast<const char *>(resp.data()), static_cast<int>(resp.size()));
            NOT_OK
            return {};
        }
    }

    libcore::QuerySpeedTestResponse Client::QueryCurrentSpeedTests(bool *rpcOK)
    {
        const libcore::EmptyReq request;
        libcore::QuerySpeedTestResponse reply;
        std::vector<uint8_t> resp;
        auto status = channel->Call("QuerySpeedTest", spb::pb::serialize<std::string>(request), resp);

        if (status == LocalSocketChannel::CallOK && tryDeserialize(resp, reply)) {
            *rpcOK = true;
            return reply;
        } else {
            NOT_OK
            return {};
        }
    }

    libcore::QueryCountryTestResponse Client::QueryCountryTestResults(bool* rpcOK)
    {
        const libcore::EmptyReq request;
        libcore::QueryCountryTestResponse reply;
        std::vector<uint8_t> resp;
        auto status = channel->Call("QueryCountryTest", spb::pb::serialize<std::string>(request), resp);

        if (status == LocalSocketChannel::CallOK && tryDeserialize(resp, reply)) {
            *rpcOK = true;
            return reply;
        } else {
            NOT_OK
            return {};
        }
    }

    libcore::GenWgKeyPairResponse Client::GenWgKeyPair(bool *rpcOK)
    {
        const libcore::EmptyReq request;
        libcore::GenWgKeyPairResponse reply;
        std::vector<uint8_t> resp;
        auto status = channel->Call("GenWgKeyPair", spb::pb::serialize<std::string>(request), resp);

        if (status == LocalSocketChannel::CallOK && tryDeserialize(resp, reply)) {
            *rpcOK = true;
            return reply;
        } else {
            NOT_OK
            return {};
        }
    }

}
