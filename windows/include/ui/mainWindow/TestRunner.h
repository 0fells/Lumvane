#pragma once

#include <QHash>
#include <QList>
#include <QMap>
#include <QMutex>
#include <QPair>
#include <QString>
#include <QStringList>

#include <atomic>
#include <functional>
#include <memory>

#ifndef Q_MOC_RUN
#include <core/windows/gen/libcore.pb.h>
#endif

#include "include/database/entities/Profile.h"

class MainWindow;

class TestRunner {
public:
    explicit TestRunner(MainWindow* mw) : mw_(mw) {}

    TestRunner(const TestRunner&) = delete;
    TestRunner& operator=(const TestRunner&) = delete;

    void runUrlTests(const QList<int>& profileIDs, const std::function<void()>& onFinished = {});

    void runIpTests(const QList<int>& profileIDs);

    void runSpeedTests(const QList<int>& profileIDs, bool testCurrent = false);

    void stop();

    bool isRunning();

    bool isTestingCurrent() const { return testingCurrent_.load(); }

private:
    enum class LatencyKind { Url, Ip };

    struct Target {
        QString coreConfig;
        QString xrayConfig;
        QStringList xrayFullConfigs;
        QStringList outboundTags;
        QMap<QString, int> tag2entID;
        int entID = -1;
        bool useDefaultOutbound = false;
        bool testCurrent = false;
    };

    void runLatencyGroup(LatencyKind kind, const QList<int>& requestedIDs,
                         const std::function<void()>& onFinished);

    void runUrlProbe(const Target& target);

    void runIpProbe(const Target& target);

    void runSpeedProbe(const Target& target);

    void applyUrlResult(const std::shared_ptr<Configs::Profile>& ent, const libcore::URLTestResp& res);

    void applyIpResult(const std::shared_ptr<Configs::Profile>& ent, const libcore::IPTestRes& res);

    QString contextName(int entID) const;

    void pollSpeedTest(const QMap<QString, int>& tag2entID, bool testCurrent);

    void pollCountryTest(const QMap<QString, int>& tag2entID, bool testCurrent);

    void creditTraffic(const std::shared_ptr<Configs::Profile>& profile, const QString& tag,
                       qint64 curUp, qint64 curDown);

    MainWindow* mw_;

    QMutex session_;
    std::atomic<quint64> sessionGen_ = 0;
    std::atomic<bool> stopRequested_ = false;
    std::atomic<bool> testingCurrent_ = false;

    QMutex creditMu_;
    QHash<QString, QPair<qint64, qint64>> credited_;
};
