#pragma once

#include "Database.h"
#include <QString>
#include <QList>
#include <mutex>
#include <string>

namespace Configs {
    struct ConfigTrafficRow {
        long long bucket_start = 0;
        int profile_id = 0;
        long long up = 0;
        long long down = 0;
    };

    struct AppTrafficRow {
        long long bucket_start = 0;
        QString process_name;
        long long up = 0;
        long long down = 0;
    };

    struct ConfigUsage {
        int profile_id = 0;
        long long up = 0;
        long long down = 0;
    };

    struct AppUsage {
        QString process_name;
        long long up = 0;
        long long down = 0;
    };

    struct TrafficSeriesPoint {
        long long bucket_start = 0;
        long long up = 0;
        long long down = 0;
    };

    struct ConfigMetaRow {
        int profile_id = 0;
        QString name;
        QString group_name;
        QString type;
        QString server_address;
        long long first_seen = 0;
        long long last_seen = 0;
    };

    struct AppMetaRow {
        QString process_name;
        QString last_path;
        long long first_seen = 0;
        long long last_seen = 0;
    };

    class TrafficStatsRepo {
    public:
        explicit TrafficStatsRepo(Database& database);

        void UpsertConfigMinuteBatch(const QList<ConfigTrafficRow>& rows);
        void UpsertAppMinuteBatch(const QList<AppTrafficRow>& rows);

        void UpsertConfigMeta(const ConfigMetaRow& meta);
        void UpsertAppMeta(const QString& processName, const QString& lastPath, long long nowSecs);

        void RollupMinuteToHour(long long olderThanSecs);
        void PruneHour(long long olderThanSecs);

        QList<ConfigUsage> QueryConfigUsage(long long fromSecs, long long toSecs);
        QList<AppUsage> QueryAppUsage(long long fromSecs, long long toSecs);
        QList<ConfigMetaRow> GetAllConfigMeta();
        QList<AppMetaRow> GetAllAppMeta();

        QList<TrafficSeriesPoint> QueryConfigSeries(long long fromSecs, long long toSecs, long long bucketSecs, long long utcOffsetSecs);
        QList<TrafficSeriesPoint> QueryAppSeries(long long fromSecs, long long toSecs, long long bucketSecs, long long utcOffsetSecs);

    private:
        Database& db;
        std::mutex mu;

        void createTables() const;
        static std::string bucketExpr(long long bucketSecs, long long utcOffsetSecs);
    };
}
