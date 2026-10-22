#pragma once

#include <QString>
#include <QList>
#include <QMutex>

#include "include/database/entities/Profile.h"
#include "include/configs/generate.h"

namespace Stats {
    struct TrafficLooperEntry {
        QString tag;
        double downlink_rate = 0;
        double uplink_rate = 0;
    };

    inline QString DisplaySpeed(const std::shared_ptr<TrafficLooperEntry> &entry) {
        return UNICODE_LRO + QString("%1↑ %2↓").arg(ReadableSize(entry->uplink_rate), ReadableSize(entry->downlink_rate));
    }

    struct TrafficLooperGroup {
        QString watchTag;
        QList<std::shared_ptr<Configs::Profile>> profiles;
        long long last_update = 0;
        double uplink_rate = 0;
        double downlink_rate = 0;
        bool dirty = false;
    };

    class TrafficLooper {
    public:
        bool loop_enabled = false;
        bool looping = false;
        QMutex loop_mutex;

        std::shared_ptr<TrafficLooperEntry> proxy;
        std::shared_ptr<TrafficLooperEntry> direct;

        void UpdateAll();

        void Loop();

        void PersistTraffic();

        void SetChainGroups(const QList<Configs::TrafficChainGroup>& configGroups);

    private:
        QList<TrafficLooperGroup> groups;
        long long direct_last_update = 0;
    };

    extern TrafficLooper *trafficLooper;
}
