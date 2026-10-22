#include <QApplication>
#include <QDir>
#include <QFile>
#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <QMessageBox>
#include <QTemporaryDir>
#include <QTimer>
#include <QtTest>

#include "include/configs/generate.h"
#include "include/configs/sub/GroupUpdater.hpp"
#include "include/configs/sub/SubscriptionInfo.hpp"
#include "include/database/GroupsRepo.h"
#include "include/database/entities/RouteProfile.h"
#include "include/global/Configs.hpp"
#include "include/global/Utils.hpp"

namespace {
    void collectMismatches(const QJsonObject &expected, const QJsonObject &actual, const QString &path, QStringList &out) {
        for (auto it = expected.begin(); it != expected.end(); ++it) {
            const auto key = path.isEmpty() ? it.key() : path + "." + it.key();
            const auto have = actual.value(it.key());
            if (it->isObject() && have.isObject()) {
                collectMismatches(it->toObject(), have.toObject(), key, out);
            } else if (*it != have) {
                out << QString("%1: want %2, got %3").arg(key,
                    QString::fromUtf8(QJsonDocument(QJsonArray{*it}).toJson(QJsonDocument::Compact)),
                    QString::fromUtf8(QJsonDocument(QJsonArray{have}).toJson(QJsonDocument::Compact)));
            }
        }
    }

    QJsonValue lookup(const QJsonObject &root, const QString &dotted) {
        QJsonValue v = root;
        for (const auto &part : dotted.split('.')) {
            if (!v.isObject()) return {};
            v = v.toObject().value(part);
        }
        return v;
    }

    std::shared_ptr<Configs::Profile> parseOne(const QString &link) {
        Subscription::RawUpdater updater;
        updater.update(link);
        return updater.updated_order.size() == 1 ? updater.updated_order.first() : nullptr;
    }
}

class LumvaneTests : public QObject {
    Q_OBJECT

    QTemporaryDir dataDir;

private slots:
    void initTestCase() {
        QVERIFY(dataDir.isValid());
        QDir::setCurrent(dataDir.path());
        MW_show_log = [](const QString &) {};
        MW_notify = [](const QString &, const QString &) {};
        MW_dialog_message = [](MwMessage, const QStringList &) {};
        Configs::initDB(QDir(dataDir.path()).filePath("lumvane.db").toStdString());
    }

    void sharedShareLinkVectors() {
        QFile file(QStringLiteral(LUMVANE_TESTDATA_DIR "/share-links.json"));
        QVERIFY2(file.open(QIODevice::ReadOnly), qPrintable(file.fileName()));
        const auto vectors = QJsonDocument::fromJson(file.readAll()).array();
        QVERIFY(!vectors.isEmpty());

        QStringList failures;
        for (const auto &item : vectors) {
            const auto v = item.toObject();
            const auto name = v["name"].toString();
            auto ent = parseOne(v["link"].toString());
            if (!ent) {
                failures << name + ": link not parsed";
                continue;
            }
            const bool wantXray = v["engine"].toString() == "xray";
            const bool isXray = ent->type == "xrayvless";
            if (wantXray != isXray) {
                failures << QString("%1: profile type %2, want Xray = %3").arg(name, ent->type, wantXray ? "true" : "false");
                continue;
            }
            if (wantXray) continue;
            const auto out = ent->outbound->Build().object;
            QStringList mismatches;
            collectMismatches(v["expect"].toObject(), out, "", mismatches);
            for (const auto &m : mismatches) failures << name + ": " + m;
            for (const auto &path : v["absent"].toArray()) {
                if (!lookup(out, path.toString()).isUndefined()) failures << name + ": " + path.toString() + " must be absent";
            }
        }
        QVERIFY2(failures.isEmpty(), qPrintable(failures.join('\n')));
    }

    void clashHysteria2KeepsHopInterval() {
        Subscription::RawUpdater updater;
        updater.updateClash(QStringLiteral(
            "proxies:\n"
            "  - name: hy\n"
            "    type: hysteria2\n"
            "    server: 192.0.2.55\n"
            "    port: 443\n"
            "    password: pw\n"
            "    ports: 20000-30000\n"
            "    hop-interval: 30\n"
            "    obfs: salamander\n"
            "    obfs-password: o\n"
            "    sni: example.com\n"));
        QCOMPARE(updater.updated_order.size(), 1);
        const auto out = updater.updated_order.first()->outbound->Build().object;
        QCOMPARE(out["type"].toString(), QString("hysteria2"));
        QCOMPARE(out["hop_interval"].toString(), QString("30s"));
        QCOMPARE(out["server_ports"].toArray().first().toString(), QString("20000:30000"));
        QCOMPARE(out["password"].toString(), QString("pw"));
    }

    void hysteria1ImportsOldAndNewWindowNames() {
        for (const auto &json : {
                 QStringLiteral(R"({"type":"hysteria","tag":"o","server":"192.0.2.24","server_port":443,"auth_str":"a","recv_window_conn":300,"recv_window":150,"disable_mtu_discovery":true})"),
                 QStringLiteral(R"({"type":"hysteria","tag":"n","server":"192.0.2.24","server_port":443,"auth_str":"a","connection_receive_window":300,"stream_receive_window":150,"disable_path_mtu_discovery":true})"),
             }) {
            auto ent = Configs::ProfilesRepo::NewProfile("hysteria");
            QVERIFY(ent->outbound->ParseFromJson(QJsonDocument::fromJson(json.toUtf8()).object()));
            const auto out = ent->outbound->Build().object;
            QCOMPARE(out["connection_receive_window"].toInt(), 300);
            QCOMPARE(out["stream_receive_window"].toInt(), 150);
            QCOMPARE(out["disable_path_mtu_discovery"].toBool(), true);
            QVERIFY(!out.contains("recv_window"));
            QVERIFY(!out.contains("recv_window_conn"));
            QVERIFY(!out.contains("disable_mtu_discovery"));
        }
    }

    void trustTunnelKeepsSpoofOverTcp() {
        auto ent = parseOne(QStringLiteral("tt://user:pw@192.0.2.20:443?sni=example.com&tls_spoof=decoy.example.com#tt"));
        QVERIFY(ent);
        const auto out = ent->outbound->Build().object;
        QCOMPARE(out["tls"].toObject()["spoof"].toString(), QString("decoy.example.com"));
    }

    void routeShareLinkRoundTrip() {
        Configs::RouteProfile source;
        source.name = "Shared";
        source.defaultOutboundID = Configs::directID;
        auto ports = std::make_shared<Configs::RouteRule>();
        ports->name = "ports";
        ports->ip_version = "4";
        ports->port = {"443", "8443"};
        ports->source_port = {"1000"};
        ports->domain_suffix = {"example.com"};
        ports->outboundID = Configs::proxyID;
        ports->override_port = "8080";
        auto reject = std::make_shared<Configs::RouteRule>();
        reject->name = "reject";
        reject->action = "reject";
        reject->rejectMethod = "drop";
        reject->domain = {"ads.example.com"};
        auto warp = std::make_shared<Configs::RouteRule>();
        warp->name = "warp";
        warp->domain = {"warp.example.com"};
        warp->outboundID = Configs::warpBypassID;
        source.Rules << ports << reject << warp;

        const auto link = source.ToShareLink();
        QVERIFY(link.startsWith("lumvane://route/"));
        QString fatal, warnings;
        bool oldArray = false;
        auto parsed = Configs::RouteProfile::FromShareInput(link, &fatal, &warnings, &oldArray);
        QVERIFY2(parsed, qPrintable(fatal));
        QCOMPARE(parsed->name, QString("Shared"));
        QCOMPARE(parsed->defaultOutboundID, int(Configs::directID));
        QCOMPARE(parsed->Rules.size(), 3);
        QCOMPARE(parsed->Rules[2]->outboundID, int(Configs::warpBypassID));
        const auto p = parsed->Rules[0];
        QCOMPARE(p->ip_version, QString("4"));
        QCOMPARE(p->port, QStringList({"443", "8443"}));
        QCOMPARE(p->source_port, QStringList({"1000"}));
        QCOMPARE(p->override_port, QString("8080"));
        QCOMPARE(p->outboundID, int(Configs::proxyID));
        QCOMPARE(parsed->Rules[1]->action, QString("reject"));
        QCOMPARE(parsed->Rules[1]->rejectMethod, QString("drop"));
    }

    void sharedRouteShareVector() {
        QFile file(QStringLiteral(LUMVANE_TESTDATA_DIR "/route-share.json"));
        QVERIFY(file.open(QIODevice::ReadOnly));
        QString fatal, warnings;
        bool oldArray = false;
        auto r = Configs::RouteProfile::FromShareInput(QString::fromUtf8(file.readAll()), &fatal, &warnings, &oldArray);
        QVERIFY2(r, qPrintable(fatal));
        QCOMPARE(r->name, QString("Shared"));
        QCOMPARE(r->defaultOutboundID, int(Configs::directID));
        QCOMPARE(r->Rules.size(), 4);
        QCOMPARE(r->Rules[0]->ip_version, QString("4"));
        QCOMPARE(r->Rules[0]->port, QStringList({"443", "8443"}));
        QCOMPARE(r->Rules[0]->source_port, QStringList({"1000"}));
        QCOMPARE(r->Rules[0]->override_port, QString("8080"));
        QCOMPARE(r->Rules[0]->outboundID, int(Configs::proxyID));
        QCOMPARE(r->Rules[1]->rejectMethod, QString("drop"));
        QCOMPARE(r->Rules[2]->type, int(Configs::simpleAddressBypass));
        QCOMPARE(r->Rules[2]->rule_set, QStringList({"geosite-category-ads-all"}));
        QCOMPARE(r->Rules[2]->outboundID, int(Configs::directID));
        QCOMPARE(r->Rules[3]->outboundID, int(Configs::proxyID));
        QVERIFY(warnings.contains("Missing Server"));
    }

    void subscriptionWarnings() {
        using Subscription::SubscriptionInfo;
        const long long now = 1000000000LL;
        const long long day = 24 * 3600;
        QCOMPARE(SubscriptionInfo::Parse("").GetWarning(now), SubscriptionInfo::None);
        QCOMPARE(SubscriptionInfo::Parse(QString("upload=1; download=2; total=100; expire=%1").arg(now + 10 * day)).GetWarning(now), SubscriptionInfo::None);
        QCOMPARE(SubscriptionInfo::Parse(QString("expire=%1").arg(now - 1)).GetWarning(now), SubscriptionInfo::Expired);
        QCOMPARE(SubscriptionInfo::Parse(QString("expire=%1").arg(now + 2 * day)).GetWarning(now), SubscriptionInfo::ExpiresSoon);
        QCOMPARE(SubscriptionInfo::Parse("upload=40; download=50; total=100").GetWarning(now), SubscriptionInfo::QuotaLow);
        QCOMPARE(SubscriptionInfo::Parse("download=100; total=100").GetWarning(now), SubscriptionInfo::QuotaUsedUp);
        const auto big = SubscriptionInfo::Parse("upload=455727941; download=6174315083; total=1073741824000; expire=1709251200");
        QCOMPARE(big.used(), 455727941LL + 6174315083LL);
        QVERIFY(!SubscriptionInfo::Parse("upload=95; total=100").WarningText(now).isEmpty());
    }

    void fullConfigForProfile() {
        auto ent = parseOne(QStringLiteral("trojan://secret@192.0.2.13:443?security=tls&sni=example.com#tr"));
        QVERIFY(ent);
        const auto groups = Configs::dataManager->groupsRepo->GetAllGroupIds();
        QVERIFY(!groups.isEmpty());
        QVERIFY(Configs::dataManager->profilesRepo->AddProfile(ent, groups.first()));
        const auto res = Configs::BuildSingBoxConfig(ent);
        QVERIFY2(res->error.isEmpty(), qPrintable(res->error));
        const auto cfg = res->coreConfig;
        QVERIFY(cfg.contains("dns"));
        QVERIFY(cfg.contains("route"));
        bool hasProxy = false;
        for (const auto &o : cfg["outbounds"].toArray()) {
            const auto ob = o.toObject();
            if (ob["type"].toString() == "trojan" && ob["server"].toString() == "192.0.2.13") hasProxy = true;
        }
        QVERIFY2(hasProxy, "the trojan profile is missing from the outbounds");
        bool hasMixed = false;
        for (const auto &i : cfg["inbounds"].toArray()) {
            if (i.toObject()["type"].toString() == "mixed") hasMixed = true;
        }
        QVERIFY(hasMixed);
    }
};

int main(int argc, char *argv[]) {
    if (qEnvironmentVariableIsEmpty("QT_QPA_PLATFORM")) qputenv("QT_QPA_PLATFORM", "offscreen");
    QApplication app(argc, argv);
    static QStringList modalMessages;
    QTimer modalWatch;
    QObject::connect(&modalWatch, &QTimer::timeout, [] {
        if (auto box = qobject_cast<QMessageBox *>(QApplication::activeModalWidget())) {
            modalMessages << box->windowTitle() + ": " + box->text();
            box->reject();
        }
    });
    modalWatch.start(50);
    LumvaneTests tests;
    const int failed = QTest::qExec(&tests, argc, argv);
    for (const auto &m : modalMessages) fprintf(stderr, "unexpected message box: %s\n", qPrintable(m));
    return failed ? failed : static_cast<int>(modalMessages.size());
}

#include "tst_lumvane.moc"
