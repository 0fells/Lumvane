#pragma once

#include <QList>
#include <QString>
#include <memory>

namespace Configs
{
    class Profile;
    class autoSelector;

    enum class AutoSelectorSkip
    {
        None = 0,
        Missing,
        MetaType,
        CoreTransitions,
        ExtraCore,
        FullConfig,
        Malformed,
        Tailscale,
        NameFilter,
        CountryFilter,
        Unavailable,
        XrayFullChained,
    };

    QString AutoSelectorSkipReason(AutoSelectorSkip skip);

    struct AutoSelectorPlan
    {
        QList<int> pool;
        QList<int> build;

        int membersInGroup = 0;
        int eligible = 0;
        int rankedByTest = 0;
        QList<QPair<AutoSelectorSkip, int>> skipped;
        bool truncated = false;
        int poolCapUsed = 0;
        int buildLimitUsed = 0;
        bool needsRanking = false;

        QString error;

        [[nodiscard]] int skippedCount() const
        {
            int total = 0;
            for (const auto &entry : skipped) total += entry.second;
            return total;
        }
    };

    AutoSelectorPlan PlanAutoSelector(const std::shared_ptr<Profile> &ent);

    QList<int> AutoSelectorRankingCandidates(const std::shared_ptr<Profile> &ent);

    QList<int> AutoSelectorUnmeasuredCandidates(const std::shared_ptr<Profile> &ent,
                                                const QList<int> &stale = {});

    QList<int> RerankAutoSelectorPool(const std::shared_ptr<Profile> &ent);
}
