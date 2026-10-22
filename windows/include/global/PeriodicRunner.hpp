#pragma once

#include <QString>
#include <functional>
#include <vector>

class QTimer;

namespace Lumvane {

struct PeriodicTask {
    QString id;
    std::function<int()> intervalMinutes;
    std::function<qint64()> lastRun;
    std::function<void(qint64)> setLastRun;
    std::function<void()> run;
};

class PeriodicRunner {
public:
    static PeriodicRunner* instance();

    void Add(PeriodicTask task);

    void CheckNow();

private:
    PeriodicRunner();
    void tick();

    QTimer* m_timer = nullptr;
    std::vector<PeriodicTask> m_tasks;
};

}
