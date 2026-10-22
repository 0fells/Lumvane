#pragma once

#include <QHash>
#include <QtGlobal>

namespace Sys {
    class ProcessMetrics {
    public:
        struct Sample {
            bool ok = false;
            qint64 rssBytes = 0;
            double cpuPercent = 0.0;
        };

        Sample sample(qint64 pid);

    private:
        struct Prior {
            quint64 cpuTimeNs = 0;
            qint64 wallNs = 0;
        };
        QHash<qint64, Prior> prior_;
    };
}
