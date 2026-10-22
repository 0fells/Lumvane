#pragma once

#include <QString>
#include <QHash>
#include <QMutex>
#include <atomic>

#include "include/database/TrafficStatsRepo.h"

namespace Stats {
    constexpr int DIRECT_STAT_PROFILE_ID = -101;

    inline const QString SPEEDTEST_APP_NAME = QStringLiteral("Speedtest");

    class TrafficStatsManager {
    public:
        void Init();

        void AddConfigDelta(int profileId, long long up, long long down);

        void AddAppDelta(const QString& processName, const QString& path,
                         long long up, long long down);

        void Flush();

        void SnapshotConfigMeta(int profileId, const QString& name, const QString& groupName,
                                const QString& type, const QString& serverAddress);
        void EnsureDirectMeta();

    private:
        struct Delta { long long up = 0; long long down = 0; };

        QMutex mu;
        QHash<int, Delta> configAccum;
        QHash<QString, Delta> appAccum;
        QHash<QString, QString> appMetaSeen;
        long long currentBucket = 0;

        std::atomic<bool> started{false};

        void drainLocked(long long bucket, QList<Configs::ConfigTrafficRow>& cfg,
                         QList<Configs::AppTrafficRow>& app);
        void writeRows(const QList<Configs::ConfigTrafficRow>& cfg,
                       const QList<Configs::AppTrafficRow>& app);

        void runRollupOnce();

        [[nodiscard]] static bool aggregationDisabled();
    };

    extern TrafficStatsManager* trafficStatsManager;
}
