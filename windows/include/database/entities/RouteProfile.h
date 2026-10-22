#pragma once

#include "include/database/entities/RouteRule.h"
#include <QUrl>
#include <QJsonArray>
#include <QStringList>

namespace Configs {
    const int INVALID_ID = -99999;

    inline QStringList tunBypassablePrivateRanges() {
        return {"10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "169.254.0.0/16", "224.0.0.0/4"};
    }

    enum simpleAction{bypass, block, proxy, warpBypass};
    inline QString simpleActionToString(simpleAction action)
    {
        if (action == bypass) return {"direct"};
        if (action == block) return {"block"};
        if (action == proxy) return {"proxy"};
        if (action == warpBypass) return {"warp-bypass"};
        return {"invalid"};
    }

    class RouteProfile {
    public:
        int id = -1;
        QString name = "";
        QList<std::shared_ptr<RouteRule>> Rules;
        int defaultOutboundID = proxyID;

        bool isRaw = false;
        QString rawRoute = "";
        bool preventModifications = false;

        bool isRemote = false;
        QString remoteURL = "";
        bool autoUpdate = false;
        qint64 remoteLastUpdate = 0;

        RouteProfile() = default;

        RouteProfile(const RouteProfile& other);

        static QList<std::shared_ptr<RouteRule>> parseJsonArray(const QJsonArray& arr, QString* parseError, QString* warnings = nullptr);

        QJsonArray get_route_rules(bool forView = false, std::map<int, QString> outboundMap = {});

        QJsonObject ToShareObject();
        QString ToShareLink();
        static std::shared_ptr<RouteProfile> FromShareInput(const QString& input, QString* fatalError, QString* warnings, bool* wasOldArray);

        static QList<std::shared_ptr<RouteProfile>> FromRemoteRoutesLink(const QString& input, bool* wasRemoteRouteLink, QString* error);

        static QList<int> CollectRawOutboundIds(const QJsonObject& route);
        static QJsonObject TranslateRawOutbounds(const QJsonObject& route, const std::map<int, QString>& outboundMap);

        static std::shared_ptr<RouteProfile> GetDefaultChain();

        std::shared_ptr<QList<int>> get_used_outbounds();

        std::shared_ptr<QStringList> get_used_rule_sets();

        QStringList get_direct_sites();

        QStringList get_proxy_sites();

        QStringList get_direct_ips();

        QStringList get_hijacked_ips();

        bool IsEmpty();

        void ResetRules();

        void ResetSimpleRule(ruleType type);

        QString GetSimpleRules(simpleAction action);

        QString UpdateSimpleRules(const QString& content, simpleAction action);

        void FilterEmptyRules();
    private:
        static bool add_simple_rule(const QString& content, const std::shared_ptr<RouteRule>& rule, ruleType type);

        static bool add_simple_address_rule(const QString& content, const std::shared_ptr<RouteRule>& rule);

        static bool add_simple_process_rule(const QString& content, const std::shared_ptr<RouteRule>& rule);

        std::shared_ptr<RouteRule> get_simple_rule_by_type(ruleType type);

        static ruleType get_rule_type(const QString& content, simpleAction action);

        static QList<std::shared_ptr<RouteRule>> get_simple_rules();

        static void reset_simple_rule(std::shared_ptr<RouteRule>& rule);
    };
}
