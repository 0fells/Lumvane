#pragma once
#include <QMutex>
#include <QWaitCondition>
#include <QString>
#include <QHash>
#include <QSet>
#include <QPair>
#include <atomic>

namespace Stats
{
    constexpr int IDKEY = 242315;

    enum ConnectionSort
    {
        Default,
        ByDownload,
        ByUpload,
        ByProcess,
        ByTraffic,
        ByOutbound,
        ByProtocol,
        ByDownloadSpeed,
        ByUploadSpeed,
        BySpeed
    };

    class ConnectionMetadata
    {
        public:
        QString id;
        long long createdAtMs;
        long long upload;
        long long download;
        QString outbound;
        QString network;
        QString dest;
        QString protocol;
        QString domain;
        QString process;
        QString processPath;
        long long closedAtMs = 0;
        long long uploadSpeed = 0;
        long long downloadSpeed = 0;
    };

    class ConnectionLister
    {
    public:
        ConnectionLister();

        bool suspend = true;

        void Loop();

        void ForceUpdate();

        void SetInView(bool inView);

        void stopLoop();

        void setSort(ConnectionSort newSort);

        ConnectionSort getSort() const { return sort; }

    private:
        void update();

        struct SpeedSample
        {
            qint64 upload = 0;
            qint64 download = 0;
            qint64 sampledAtMs = 0;
            qint64 upSpeed = 0;
            qint64 downSpeed = 0;
        };
        QHash<QString, SpeedSample> speedSamples_;

        QMutex mu;

        QMutex waitMu_;
        QWaitCondition waitCond_;
        std::atomic<bool> inView_{false};

        bool stop = false;

        std::shared_ptr<QSet<QString>> state;

        ConnectionSort sort = Default;

        bool asc = false;

        QHash<QString, QPair<qint64, qint64>> lastBytes_;
        QSet<QString> accountedClosed_;
    };

    extern ConnectionLister* connection_lister;
}
