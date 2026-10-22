#pragma once

#include <QObject>
#include <QString>
#include <QStringList>

#include "include/global/Utils.hpp"

namespace Subscription {
    struct SubscriptionInfo {
        long long upload = 0;
        long long download = 0;
        long long total = 0;
        long long expire = 0;

        enum Warning { None, Expired, QuotaUsedUp, ExpiresSoon, QuotaLow };
        static constexpr long long SoonSecs = 3LL * 24 * 3600;

        [[nodiscard]] long long used() const { return upload + download; }

        static SubscriptionInfo Parse(const QString &raw) {
            SubscriptionInfo info;
            for (const auto &part : raw.split(';', Qt::SkipEmptyParts)) {
                const auto eq = part.indexOf('=');
                if (eq <= 0) continue;
                const auto key = part.left(eq).trimmed().toLower();
                const auto value = static_cast<long long>(part.mid(eq + 1).trimmed().toDouble());
                if (key == "upload") info.upload = value;
                else if (key == "download") info.download = value;
                else if (key == "total") info.total = value;
                else if (key == "expire") info.expire = value;
            }
            return info;
        }

        [[nodiscard]] Warning GetWarning(long long nowSecs) const {
            if (expire > 0 && expire <= nowSecs) return Expired;
            if (total > 0 && used() >= total) return QuotaUsedUp;
            if (expire > 0 && expire - nowSecs <= SoonSecs) return ExpiresSoon;
            if (total > 0 && used() * 10 >= total * 9) return QuotaLow;
            return None;
        }

        [[nodiscard]] QString WarningText(long long nowSecs) const {
            switch (GetWarning(nowSecs)) {
                case Expired: return QObject::tr("Subscription has expired");
                case QuotaUsedUp: return QObject::tr("The traffic limit is used up");
                case ExpiresSoon: return QObject::tr("Subscription expires %1").arg(DisplayTime(expire, QLocale::ShortFormat));
                case QuotaLow: return QObject::tr("%1% of the traffic is used").arg(used() * 100 / total);
                default: return {};
            }
        }
    };
}
