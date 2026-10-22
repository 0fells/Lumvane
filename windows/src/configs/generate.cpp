#include "include/configs/generate.h"
#include "include/api/RPC.h"
#include "include/configs/AutoSelectorPlan.h"
#include "include/global/Configs.hpp"

#include <QApplication>
#include <QFileInfo>
#include <QHostAddress>


#include "include/database/GroupsRepo.h"
#include "include/database/ProfilesRepo.h"
#include "include/database/RoutesRepo.h"


#include "include/database/entities/Profile.h"
#ifdef Q_OS_LINUX
#include "include/sys/linux/systemChecks.h"
#endif

#include <algorithm>
#include <string_view>
#include <srslist.h>

namespace {
    std::string_view ruleSetUrl(std::string_view key) {
        auto it = std::lower_bound(ruleSetList.begin(), ruleSetList.end(), key,
            [](const auto& e, std::string_view k) { return e.first < k; });
        return (it != ruleSetList.end() && it->first == key) ? it->second : std::string_view{};
    }

}

namespace Configs {
    namespace {

        namespace tags {
            constexpr auto proxy = "proxy";
            constexpr auto direct = "direct";
            constexpr auto warpBypass = "warp-bypass";

            constexpr auto dnsRemote = "dns-remote";
            constexpr auto dnsDirect = "dns-direct";
            constexpr auto dnsLocal = "dns-local";
            constexpr auto dnsFake = "dns-fake";
            constexpr auto dnsTailscale = "dns-tailscale";

            constexpr auto dnsIn = "dns-in";
            constexpr auto mixedIn = "mixed-in";
            constexpr auto tunIn = "tun-in";
            constexpr auto redirectIn = "hijack";
            constexpr auto dnsServerIn = "hijack-dns";
            constexpr auto xrayFullConfigIn = "lumvane-bridge";

            constexpr auto adblockRuleSet = "lumvane-adblocksingbox";

            constexpr auto mainChainPrefix = "config";
            constexpr auto routeChainPrefix = "route";
            constexpr auto poolChainPrefix = "pool";
            constexpr auto testChainPrefix = "proxy";
            constexpr auto testXrayFullPrefix = "xrayfull";
            constexpr auto bridgePrefix = "bridge";
        }

        QString hopTag(const QString &prefix, int index) { return prefix + "-" + Int2String(index); }

        QString bridgeTagFor(const QString &singIngressTag) {
            return QString(tags::bridgePrefix) + "-" + singIngressTag;
        }

        struct DomainSelectors {
            QJsonArray ruleSets;
            QJsonArray domains;
            QJsonArray suffixes;
            QJsonArray keywords;
            QJsonArray regexes;

            [[nodiscard]] bool hasInlineConditions() const {
                return !domains.isEmpty() || !suffixes.isEmpty() || !keywords.isEmpty() || !regexes.isEmpty();
            }
        };

        struct SelectorSink {
            QJsonArray *ruleSets = nullptr;
            QJsonArray *domains = nullptr;
            QJsonArray *suffixes = nullptr;
            QJsonArray *keywords = nullptr;
            QJsonArray *regexes = nullptr;
            QJsonArray *ipCIDRs = nullptr;
        };

        SelectorSink sinkFor(DomainSelectors &selectors) {
            return {
                .ruleSets = &selectors.ruleSets,
                .domains = &selectors.domains,
                .suffixes = &selectors.suffixes,
                .keywords = &selectors.keywords,
                .regexes = &selectors.regexes,
            };
        }

        void parseSelectorList(const QStringList &items, const SelectorSink &sink) {
            const std::pair<QLatin1String, QJsonArray *> kinds[] = {
                {QLatin1String("ruleset:"), sink.ruleSets},
                {QLatin1String("domain:"), sink.domains},
                {QLatin1String("suffix:"), sink.suffixes},
                {QLatin1String("keyword:"), sink.keywords},
                {QLatin1String("regex:"), sink.regexes},
                {QLatin1String("ip:"), sink.ipCIDRs},
            };
            for (const auto &item : items) {
                for (const auto &[prefix, target] : kinds) {
                    if (!item.startsWith(prefix)) continue;
                    if (target != nullptr) *target << item.mid(prefix.size());
                    break;
                }
            }
        }

        struct DNSDeps {
            bool needDirectDnsRules = false;
            DomainSelectors direct;
            bool needProxyDnsRules = false;
            DomainSelectors proxy;
        };

        struct TunDeps {
            QJsonArray directIPSets;
            QJsonArray directIPCIDRs;
            QSet<QString> hijackedPrivateRanges;
        };

        struct RoutingDeps {
            QStringList neededRuleSets;
            std::map<int, QString> outboundMap;
            struct RouteOutboundGroup {
                QList<int> hopIDs;
                std::shared_ptr<Profile> chainWrapper;
            };
            QList<RouteOutboundGroup> routeOutboundGroups;
        };

        struct BuildPrerequisites {
            DNSDeps dns;
            DomainSelectors hijack;
            TunDeps tun;
            RoutingDeps routing;
        };

        struct coreBridgeConfig {
            bool needed = false;
            int port = -1;
            QString auth;
            QString host = "127.0.0.1";
        };

        struct BuildContext {
            bool forTest = false;
            bool tunEnabled = false;
            bool singToXrayTransitioned = false;
            bool xrayToSingTransitioned = false;
            bool proxyUsesXray = false;
            std::shared_ptr<Profile> ent = std::make_shared<Profile>(nullptr, nullptr);
            BuildPrerequisites prerequisites;
            osType os = getOS();

            QString error;
            QJsonArray outbounds;
            QJsonArray endpoints;
            QJsonArray xrayOutbounds;
            QList<QString> xrayIngressTags;
            QList<QString> singIngressTags;
            QList<coreBridgeConfig> singToXrayBridges;
            QList<coreBridgeConfig> xrayToSingBridges;
            std::shared_ptr<BuildConfigResult> result = std::make_shared<BuildConfigResult>();
        };

        QString bridgeIngressMismatch(const BuildContext &ctx) {
            if (ctx.xrayToSingBridges.size() != ctx.singIngressTags.size())
                return "xray to sing-box bridges count does not match ingress tags count";
            return {};
        }

        bool prefixesOverlap(const QString &lhs, const QString &rhs) {
            const auto a = QHostAddress::parseSubnet(lhs);
            const auto b = QHostAddress::parseSubnet(rhs);
            if (a.second < 0 || b.second < 0) return false;
            if (a.first.protocol() != b.first.protocol()) return false;
            return a.second <= b.second ? b.first.isInSubnet(a.first, a.second)
                                        : a.first.isInSubnet(b.first, b.second);
        }

        struct RawPrefix {
            QByteArray addr;
            int bits = -1;
        };

        RawPrefix parsePrefix(const QString &cidr) {
            const auto parsed = QHostAddress::parseSubnet(cidr);
            if (parsed.second < 0) return {};
            RawPrefix prefix;
            prefix.bits = parsed.second;
            if (parsed.first.protocol() == QAbstractSocket::IPv4Protocol) {
                const auto v4 = parsed.first.toIPv4Address();
                prefix.addr.resize(4);
                for (int i = 0; i < 4; ++i) prefix.addr[i] = char((v4 >> (24 - 8 * i)) & 0xFF);
            } else if (parsed.first.protocol() == QAbstractSocket::IPv6Protocol) {
                const auto v6 = parsed.first.toIPv6Address();
                prefix.addr = QByteArray(reinterpret_cast<const char *>(v6.c), 16);
            } else {
                return {};
            }
            return prefix;
        }

        QString prefixToString(const RawPrefix &prefix) {
            QHostAddress addr;
            if (prefix.addr.size() == 4) {
                quint32 v4 = 0;
                for (int i = 0; i < 4; ++i) v4 = (v4 << 8) | quint8(prefix.addr[i]);
                addr = QHostAddress(v4);
            } else {
                Q_IPV6ADDR v6;
                for (int i = 0; i < 16; ++i) v6[i] = quint8(prefix.addr[i]);
                addr = QHostAddress(v6);
            }
            return addr.toString() + "/" + QString::number(prefix.bits);
        }

        bool prefixContains(const RawPrefix &outer, const RawPrefix &inner) {
            if (outer.bits < 0 || inner.bits < 0) return false;
            if (outer.addr.size() != inner.addr.size() || outer.bits > inner.bits) return false;
            const int wholeBytes = outer.bits / 8;
            if (outer.addr.left(wholeBytes) != inner.addr.left(wholeBytes)) return false;
            const int restBits = outer.bits % 8;
            if (restBits == 0) return true;
            const auto mask = quint8(0xFF << (8 - restBits));
            return (quint8(outer.addr[wholeBytes]) & mask) == (quint8(inner.addr[wholeBytes]) & mask);
        }

        QStringList subtractPrefix(const QStringList &ranges, const QString &hole) {
            const auto cut = parsePrefix(hole);
            if (cut.bits < 0) return ranges;
            QStringList out;
            for (const auto &entry : ranges) {
                const auto range = parsePrefix(entry);
                if (prefixContains(cut, range)) continue;
                if (!prefixContains(range, cut)) {
                    out << entry;
                    continue;
                }
                for (int bits = range.bits + 1; bits <= cut.bits; ++bits) {
                    RawPrefix sibling{cut.addr, bits};
                    const int flipped = bits - 1;
                    sibling.addr[flipped / 8] = char(quint8(sibling.addr[flipped / 8]) ^ quint8(0x80 >> (flipped % 8)));
                    for (int i = bits; i < int(sibling.addr.size()) * 8; ++i)
                        sibling.addr[i / 8] = char(quint8(sibling.addr[i / 8]) & ~quint8(0x80 >> (i % 8)));
                    out << prefixToString(sibling);
                }
            }
            return out;
        }

        QJsonArray extraCoreProcessPaths(const QString &corePath) {
            auto path = corePath;
#ifdef Q_OS_WIN
            path.replace("/", "\\");
#endif
            return QJsonArray{path};
        }

        QJsonObject socksBridgeInbound(const QString &tag, const coreBridgeConfig &bridge) {
            return QJsonObject{
                {"type", "socks"},
                {"tag", tag},
                {"listen", bridge.host},
                {"listen_port", bridge.port},
                {"users", QJsonArray{QJsonObject{
                    {"username", bridge.auth},
                    {"password", bridge.auth},
                }}},
            };
        }

        QJsonObject xraySocksInbound(const QString &tag, const coreBridgeConfig &bridge) {
            return QJsonObject{
                {"tag", tag},
                {"listen", bridge.host},
                {"port", bridge.port},
                {"protocol", "socks"},
                {"settings", QJsonObject{
                    {"auth", "password"},
                    {"udp", true},
                    {"accounts", QJsonArray{QJsonObject{
                        {"user", bridge.auth},
                        {"pass", bridge.auth},
                    }}},
                }},
            };
        }

        void appendDnsRoute(QJsonArray &rules, const QJsonObject &conditions,
                            const QString &strategy, const QString &server) {
            QString blockedType;
            if (strategy == "ipv4_only") blockedType = "AAAA";
            else if (strategy == "ipv6_only") blockedType = "A";
            if (!blockedType.isEmpty()) {
                auto block = conditions;
                block["query_type"] = blockedType;
                block["action"] = "predefined";
                block["rcode"] = "NOERROR";
                rules += block;
            }
            auto route = conditions;
            route["action"] = "route";
            route["server"] = server;
            rules += route;
        }

        void appendDnsRoutingRules(QJsonArray &rules, const DomainSelectors &selectors,
                                   const QString &strategy, const QString &server) {
            if (!selectors.ruleSets.isEmpty()) {
                appendDnsRoute(rules, QJsonObject{{"rule_set", selectors.ruleSets}}, strategy, server);
            }
            if (selectors.hasInlineConditions()) {
                appendDnsRoute(rules, QJsonObject{
                    {"domain", selectors.domains},
                    {"domain_suffix", selectors.suffixes},
                    {"domain_keyword", selectors.keywords},
                    {"domain_regex", selectors.regexes},
                }, strategy, server);
            }
        }

        QString genTunName() {
            auto tun_name = "lumvane-tun";
#ifdef Q_OS_MACOS
            tun_name = "";
#endif
            return tun_name;
        }

        bool isCustomFullConfig(const std::shared_ptr<Profile> &profile) {
            return profile->type == "custom" && profile->Custom() != nullptr &&
                   profile->Custom()->type == Custom::CustomFullConfig;
        }

        bool isXrayFullConfig(const std::shared_ptr<Profile> &profile) {
            return profile->outbound != nullptr && profile->outbound->IsXrayFullConfig();
        }

        bool usesXrayCore(const std::shared_ptr<Profile> &profile) {
            return profile->outbound != nullptr &&
                   (profile->outbound->IsXray() || profile->outbound->IsXrayFullConfig());
        }

        QStringList outboundServerDomains(const std::shared_ptr<Profile> &ent)
        {
            QStringList domains;
            if (ent == nullptr || ent->outbound == nullptr) return domains;
            if (ent->outbound->IsXrayFullConfig()) {
                if (auto custom = ent->Custom(); custom != nullptr) {
                    for (const auto &addr : custom->GetXrayFullConfigServerDomains())
                        if (!addr.isEmpty() && !IsIpAddress(addr)) domains << addr;
                }
                return domains;
            }
            if (auto addr = ent->outbound->GetAddress(); !addr.isEmpty() && !IsIpAddress(addr)) domains << addr;
            return domains;
        }

        QStringList getChainDomains(const std::shared_ptr<Profile> &ent, QString &error)
        {
            QStringList domains;
            auto chain = ent->Chain();
            if (!chain)
            {
                error = "Ent is Nullptr after cast to chain in getChainDomains, data is corrupted";
                return domains;
            }
            for (int id : chain->list)
            {
                if (auto subEnt = dataManager->profilesRepo->GetProfile(id); subEnt != nullptr)
                {
                    if (subEnt->outbound != nullptr && subEnt->outbound->IsExtraCore()) continue;
                    domains << outboundServerDomains(subEnt);
                }
            }
            return domains;
        }

        QStringList getAutoSelectorDomains(const std::shared_ptr<Profile> &ent)
        {
            QStringList domains;
            const auto plan = PlanAutoSelector(ent);
            if (!plan.error.isEmpty()) return domains;
            for (int id : plan.build)
            {
                if (auto member = dataManager->profilesRepo->GetProfile(id); member != nullptr)
                    domains << outboundServerDomains(member);
            }
            return domains;
        }

        QStringList getEntDomains(const QList<int> &entIDs, QString &error)
        {
            QStringList domains;
            for (const auto &id: entIDs)
            {
                if (auto ent = dataManager->profilesRepo->GetProfile(id); ent != nullptr)
                {
                    if (ent->outbound != nullptr && ent->outbound->IsExtraCore()) continue;
                    if (ent->type == "chain") domains << getChainDomains(ent, error);
                    else if (ent->type == "autoselector") domains << getAutoSelectorDomains(ent);
                    else domains << outboundServerDomains(ent);
                }
            }

            return domains;
        }

        bool proxyPathUsesXray(const std::shared_ptr<Profile> &ent)
        {
            if (ent->type == "chain") {
                if (auto chain = ent->Chain(); chain != nullptr) {
                    for (int pid : chain->list) {
                        auto hop = dataManager->profilesRepo->GetProfile(pid);
                        if (hop != nullptr && usesXrayCore(hop)) return true;
                    }
                }
                return false;
            }
            if (ent->type == "autoselector") {
                const auto plan = PlanAutoSelector(ent);
                for (int pid : plan.build) {
                    auto member = dataManager->profilesRepo->GetProfile(pid);
                    if (member != nullptr && usesXrayCore(member)) return true;
                }
                return false;
            }
            return usesXrayCore(ent);
        }

        std::shared_ptr<Profile> getWarpProfile() {
            const auto &settings = *dataManager->settingsRepo;
            auto warpProfile = std::make_shared<Profile>();
            warpProfile->name = "warp";
            warpProfile->id = warpProfileID;
            warpProfile->type = "wireguard";
            auto outbound = std::make_shared<wireguard>();
            outbound->name = "warp";
            outbound->server = settings.warp_ep.contains(":") ? SubStrBefore(settings.warp_ep, ":") : settings.warp_ep;
            outbound->server_port = settings.warp_ep.contains(":") ? SubStrAfter(settings.warp_ep, ":").toInt() : 2408;
            outbound->private_key = settings.warp_private_key;
            outbound->address = settings.warp_ifc_addrs;
            auto peer = std::make_shared<Peer>();
            peer->public_key = settings.warp_public_key;
            peer->address = outbound->server;
            peer->port = outbound->server_port;
            peer->reserved = QStringList2QListInt(settings.warp_reserved);
            peer->persistent_keepalive = "10";
            outbound->peer = peer;
            outbound->mtu = 1280;

            warpProfile->outbound = outbound;
            return warpProfile;
        }

        std::shared_ptr<Profile> resolveExtraCoreProfile(const std::shared_ptr<Profile> &ent)
        {
            if (ent->outbound != nullptr && ent->outbound->IsExtraCore()) return ent;
            if (ent->type != "chain") return nullptr;
            auto chain = ent->Chain();
            if (chain == nullptr || chain->list.isEmpty()) return nullptr;
            auto firstEnt = dataManager->profilesRepo->GetProfile(chain->list[0]);
            if (firstEnt != nullptr && firstEnt->outbound != nullptr && firstEnt->outbound->IsExtraCore())
                return firstEnt;
            return nullptr;
        }

        void calculatePrerequisites(BuildContext &ctx) {
            const auto &settings = *dataManager->settingsRepo;
            ctx.tunEnabled = settings.spmode_vpn;
            ctx.os = getOS();
            auto &preReqs = ctx.prerequisites;

            auto routeChain = dataManager->routesRepo->GetRouteProfile(settings.current_route_id);
            if (routeChain == nullptr) {
                ctx.error = "Routing profile does not exist, try resetting the route profile in Routing Settings";
                return;
            }

            if (settings.enable_warp &&
                (settings.warp_private_key.isEmpty() ||
                 settings.warp_public_key.isEmpty() ||
                 settings.warp_ep.isEmpty() ||
                 settings.warp_ifc_addrs.isEmpty())) {
                ctx.error = "Warp is enabled but its config has not been generated. Please generate the Warp config first in Routing Settings.";
                return;
            }

            auto addDirectDomains = [&preReqs](const QStringList &addrs) {
                for (const auto &addr : addrs) preReqs.dns.direct.domains << addr;
                preReqs.dns.needDirectDnsRules = true;
            };

            auto neededOutbounds = routeChain->get_used_outbounds();
            auto neededRuleSets = routeChain->get_used_rule_sets();
            preReqs.routing.outboundMap[-1] = tags::proxy;
            preReqs.routing.outboundMap[-2] = tags::direct;
            preReqs.routing.outboundMap[warpBypassID] = settings.enable_warp ? tags::warpBypass : tags::proxy;
            int suffix = 0;
            if (proxyPathUsesXray(ctx.ent)) ctx.proxyUsesXray = true;
            for (const auto &item: *neededOutbounds) {
                if (item < 0) continue;
                auto neededEnt = dataManager->profilesRepo->GetProfile(item);
                if (neededEnt == nullptr) {
                    ctx.error = "The routing profile is referencing outbounds that no longer exist, consider revising your settings";
                    return;
                }
                if ((neededEnt->outbound != nullptr && neededEnt->outbound->IsExtraCore()) || isCustomFullConfig(neededEnt) || isXrayFullConfig(neededEnt)) {
                    ctx.error = "Outbounds used in routing profile cannot use an extra core or be a custom full config";
                    return;
                }
                if (neededEnt->type == "chain") {
                    auto chain = neededEnt->Chain();
                    if (chain == nullptr || chain->list.isEmpty()) {
                        ctx.error = "Chain outbound in routing profile is empty or corrupted";
                        return;
                    }
                    for (int hopID : chain->list) {
                        auto hopEnt = dataManager->profilesRepo->GetProfile(hopID);
                        if (hopEnt == nullptr) {
                            ctx.error = "Chain outbound in routing profile contains a missing profile";
                            return;
                        }
                        if ((hopEnt->outbound != nullptr && hopEnt->outbound->IsExtraCore()) || isCustomFullConfig(hopEnt) || isXrayFullConfig(hopEnt) || hopEnt->type == "chain") {
                            ctx.error = "Chain hops in routing profile cannot use an extra core, a custom full config, or be of type chain";
                            return;
                        }
                        if (usesXrayCore(hopEnt)) ctx.proxyUsesXray = true;
                        if (auto addrs = getEntDomains({hopID}, ctx.error); !addrs.empty()) {
                            if (!ctx.error.isEmpty()) return;
                            addDirectDomains(addrs);
                        }
                    }
                    preReqs.routing.outboundMap[item] = hopTag(tags::routeChainPrefix, suffix);
                    QList<int> reversedHops;
                    for (int idx = chain->list.size() - 1; idx >= 0; idx--) reversedHops << chain->list[idx];
                    preReqs.routing.routeOutboundGroups << RoutingDeps::RouteOutboundGroup{reversedHops, neededEnt};
                    suffix += static_cast<int>(chain->list.size());
                } else {
                    if (usesXrayCore(neededEnt)) ctx.proxyUsesXray = true;
                    if (auto entAddrs = getEntDomains({neededEnt->id}, ctx.error); !entAddrs.empty())
                    {
                        if (!ctx.error.isEmpty()) return;
                        addDirectDomains(entAddrs);
                    }
                    preReqs.routing.outboundMap[item] = hopTag(tags::routeChainPrefix, suffix++);
                    preReqs.routing.routeOutboundGroups << RoutingDeps::RouteOutboundGroup{QList<int>{item}, nullptr};
                }
            }

            for (const auto &item: *neededRuleSets) {
                preReqs.routing.neededRuleSets << item;
            }

            if (settings.enable_dns_routing) {
                auto sets = routeChain->get_direct_sites();
                parseSelectorList(sets, sinkFor(preReqs.dns.direct));
                if (!sets.isEmpty()) preReqs.dns.needDirectDnsRules = true;

                auto proxySets = routeChain->get_proxy_sites();
                parseSelectorList(proxySets, sinkFor(preReqs.dns.proxy));
                if (!proxySets.isEmpty()) preReqs.dns.needProxyDnsRules = true;
            }
            if (auto entAddrs = getEntDomains({ctx.ent->id}, ctx.error); !entAddrs.isEmpty())
            {
                if (!ctx.error.isEmpty()) return;
                addDirectDomains(entAddrs);
            }
            if (auto group = dataManager->groupsRepo->GetGroup(ctx.ent->gid); group != nullptr)
            {
                QList<int> groupEnts;
                if (auto frontEntID = group->front_proxy_id; frontEntID >= 0) groupEnts << frontEntID;
                if (auto landingEntID = group->landing_proxy_id; landingEntID >= 0) groupEnts << landingEntID;
                for (const auto &id : groupEnts)
                {
                    if (auto pe = dataManager->profilesRepo->GetProfile(id); pe != nullptr && usesXrayCore(pe)) ctx.proxyUsesXray = true;
                }
                auto addrs = getEntDomains(groupEnts, ctx.error);
                if (!ctx.error.isEmpty()) return;
                if (!addrs.isEmpty()) addDirectDomains(addrs);
            }

            if (settings.enable_dns_server) {
                parseSelectorList(settings.dns_server_rules, sinkFor(preReqs.hijack));
            }
            for (auto ruleSet : preReqs.hijack.ruleSets) {
                if (!preReqs.routing.neededRuleSets.contains(ruleSet.toString())) preReqs.routing.neededRuleSets.append(ruleSet.toString());
            }

            parseSelectorList(routeChain->get_direct_ips(), {
                .ruleSets = &preReqs.tun.directIPSets,
                .ipCIDRs = &preReqs.tun.directIPCIDRs,
            });

            for (const auto &cidr : routeChain->get_hijacked_ips()) {
                for (const auto &range : tunBypassablePrivateRanges()) {
                    if (prefixesOverlap(range, cidr)) preReqs.tun.hijackedPrivateRanges << range;
                }
            }

            auto extraCoreEnt = resolveExtraCoreProfile(ctx.ent);
            if (extraCoreEnt == nullptr) return;
            auto outbound = extraCoreEnt->ExtraCore();
            if (outbound == nullptr)
            {
                MW_show_log("INVALID ENT TYPE, NEEDED EXTRACORE GOT NULLPTR");
                ctx.error = "failed to cast to extracore, type is: " + extraCoreEnt->type;
                return;
            }
            auto &extraCoreData = *ctx.result->extraCoreData;
            extraCoreData.path = QFileInfo(outbound->extraCorePath).canonicalFilePath();
            extraCoreData.args = outbound->extraCoreArgs;
            extraCoreData.config = outbound->extraCoreConf;
            extraCoreData.noLog = outbound->noLogs;
        }

        void buildLogSection(BuildContext &ctx) {
            ctx.result->coreConfig.insert("log", QJsonObject{{"level", dataManager->settingsRepo->log_level}});
        }

        void buildNTPSection(BuildContext &ctx) {
            const auto &settings = *dataManager->settingsRepo;
            if (!settings.enable_ntp) return;
            ctx.result->coreConfig["ntp"] = QJsonObject{
                {"enabled", true},
                {"server", settings.ntp_server_address},
                {"server_port", settings.ntp_server_port},
                {"interval", settings.ntp_interval},
                {"detour", (settings.ntp_outbound == tags::proxy && !ctx.forTest) ? tags::proxy : tags::direct},
            };
        }

        void buildCertificateSection(BuildContext &ctx) {
            ctx.result->coreConfig.insert("certificate",
                QJsonObject{{"store", dataManager->settingsRepo->use_mozilla_certs ? "mozilla" : "system"}});
        }

        QJsonObject buildDnsObj(BuildContext &ctx, QString address) {
            if (address.startsWith("local")) {
                if (ctx.tunEnabled && ctx.os == Darwin) {
                    return {
                        {"type", "udp"},
                        {"server", dataManager->settingsRepo->core_box_underlying_dns}
                    };
                }
                return {{"type", "local"}};
            }
            if (address.startsWith("dhcp://")) {
                auto ifcName = address.replace("dhcp://", "");
                if (ifcName == "auto") ifcName = "";
                return {
                    {"type", "dhcp"},
                    {"interface", ifcName},
                };
            }
            QString addr = address;
            int port = -1;
            QString type = "udp";
            QString path = "";
            if (address.startsWith("tcp://")) {
                type = "tcp";
                addr = addr.replace("tcp://", "");
            }
            if (address.startsWith("tls://")) {
                type = "tls";
                addr = addr.replace("tls://", "");
            }
            if (address.startsWith("quic://")) {
                type = "quic";
                addr = addr.replace("quic://", "");
            }
            if (address.startsWith("https://")) {
                type = "https";
                addr = addr.replace("https://", "");
                auto slashIndex = addr.indexOf("/");
                if (slashIndex != -1) {
                    path = addr.mid(slashIndex);
                    addr = addr.left(slashIndex);
                }
            }
            if (address.startsWith("h3://")) {
                type = "h3";
                addr = addr.replace("h3://", "");
                auto slashIndex = addr.indexOf("/");
                if (slashIndex != -1) {
                    path = addr.mid(slashIndex);
                    addr = addr.left(slashIndex);
                }
            }
            if (addr.startsWith("[")) {
                const auto close = addr.indexOf("]");
                if (close > 0) {
                    const auto rest = addr.mid(close + 1);
                    if (rest.startsWith(":")) port = rest.mid(1).toInt();
                    addr = addr.mid(1, close - 1);
                }
            } else if (addr.count(":") == 1) {
                auto spl = addr.split(":");
                addr = spl[0];
                port = spl[1].toInt();
            }
            QJsonObject res = {
                {"type", type},
                {"server", addr},
            };
            if (port != -1) res["server_port"] = port;
            if (!path.isEmpty()) res["path"] = path;
            return res;
        }

        QString upgradeUdpDnsToDoH(const QString &server) {
            static const QMap<QString, QString> known = {
                {"8.8.8.8", "https://8.8.8.8/dns-query"},
                {"8.8.4.4", "https://8.8.4.4/dns-query"},
                {"1.1.1.1", "https://1.1.1.1/dns-query"},
                {"1.0.0.1", "https://1.0.0.1/dns-query"},
                {"1.1.1.2", "https://1.1.1.2/dns-query"},
                {"1.0.0.2", "https://1.0.0.2/dns-query"},
                {"1.1.1.3", "https://1.1.1.3/dns-query"},
                {"1.0.0.3", "https://1.0.0.3/dns-query"},
                {"9.9.9.9", "https://9.9.9.9/dns-query"},
                {"149.112.112.112", "https://149.112.112.112/dns-query"},
                {"94.140.14.14", "https://94.140.14.14/dns-query"},
                {"94.140.15.15", "https://94.140.15.15/dns-query"},
            };
            return known.value(server, "https://8.8.8.8/dns-query");
        }

        void buildDNSSection(BuildContext &ctx, bool useDnsObj = true) {
            const auto &settings = *dataManager->settingsRepo;
            if (getOS() == Darwin && settings.core_box_underlying_dns.isEmpty() && settings.spmode_vpn)
            {
                ctx.error = QObject::tr("Local DNS and Tun mode do not work together, please set an IP to be used as the Local DNS server in the Routing Settings -> Local override");
                return;
            }

            if (settings.use_dns_object && useDnsObj) {
                ctx.result->coreConfig["dns"] = QString2QJsonObject(settings.dns_object);
                return;
            }

            const auto &dns = ctx.prerequisites.dns;
            const auto &hijack = ctx.prerequisites.hijack;
            bool isTailscale = ctx.ent->type == "tailscale";
            QJsonArray servers;
            QJsonArray rules;
            if (!ctx.forTest) {
                auto remoteDnsObj = buildDnsObj(ctx, settings.remote_dns);
                if (ctx.proxyUsesXray && ( remoteDnsObj.value("type").toString() == "udp" || remoteDnsObj.value("type").toString() == "quic" )) {
                    remoteDnsObj = buildDnsObj(ctx, upgradeUdpDnsToDoH(remoteDnsObj.value("server").toString()));
                }
                remoteDnsObj["tag"] = tags::dnsRemote;
                remoteDnsObj["domain_resolver"] = tags::dnsLocal;
                remoteDnsObj["detour"] = tags::proxy;
                servers += remoteDnsObj;

                if (isTailscale)
                {
                    auto tailscale = ctx.ent->Tailscale();
                    if (tailscale != nullptr)
                    {
                        servers += QJsonObject{
                            {"type", "tailscale"},
                            {"tag", tags::dnsTailscale},
                            {"endpoint", tags::proxy},
                            {"accept_default_resolvers", tailscale->globalDNS},
                        };

                        rules.prepend(QJsonObject{
                            {"domain_suffix", QJsonArray{"ts.net", "tailscale.net"}},
                            {"action", "route"},
                            {"server", tags::dnsTailscale},
                        });
                    }

                    rules.prepend(QJsonObject{
                        {"domain", QJsonArray{
                            "controlplane.tailscale.com",
                            "login.tailscale.com",
                            "log.tailscale.io"
                        }},
                        {"domain_suffix", QJsonArray{
                            "tailscale.com",
                            "tailscale.net",
                            "tailscale.io"
                        }},
                        {"action", "route"},
                        {"server", tags::dnsDirect},
                    });
                }
            }

            auto directDnsObj = buildDnsObj(ctx, settings.direct_dns);
            directDnsObj["tag"] = tags::dnsDirect;
            directDnsObj["domain_resolver"] = tags::dnsLocal;
            servers.append(directDnsObj);

            if (!ctx.forTest) {
                rules += QJsonObject{
                        {"domain", "localhost"},
                        {"action", "predefined"},
                        {"query_type", "A"},
                        {"rcode", "NOERROR"},
                        {"answer", "localhost. IN A 127.0.0.1"},
                    };

                rules += QJsonObject{
                        {"domain", "localhost"},
                        {"action", "predefined"},
                        {"query_type", "AAAA"},
                        {"rcode", "NXDOMAIN"},
                    };
            }

            if (!ctx.forTest && ctx.tunEnabled && !settings.vpn_ipv6) {
                rules += QJsonObject{
                        {"inbound", QJsonArray{tags::tunIn}},
                        {"query_type", "AAAA"},
                        {"action", "predefined"},
                        {"rcode", "NOERROR"},
                    };
            }

            if (!ctx.forTest && ctx.proxyUsesXray) {
                appendDnsRoute(rules, QJsonObject{{"inbound", QJsonArray{tags::dnsIn}}},
                               settings.direct_dns_strategy, tags::dnsDirect);
            }

            if (!ctx.forTest && !ctx.result->extraCoreData->path.isEmpty())
            {
                appendDnsRoute(rules, QJsonObject{{"process_path", extraCoreProcessPaths(ctx.result->extraCoreData->path)}},
                               settings.direct_dns_strategy, tags::dnsDirect);
            }

            if (settings.enable_dns_server && !ctx.forTest)
            {
                auto addHijackRules = [&](const QJsonObject &conditions) {
                    auto v4 = conditions;
                    v4["query_type"] = "A";
                    v4["action"] = "predefined";
                    v4["rcode"] = "NOERROR";
                    v4["answer"] = QString("* IN A %1").arg(settings.dns_v4_resp);
                    rules += v4;

                    if (settings.dns_v6_resp.isEmpty()) return;
                    auto v6 = conditions;
                    v6["query_type"] = "AAAA";
                    v6["action"] = "predefined";
                    v6["rcode"] = "NOERROR";
                    v6["answer"] = QString("* IN AAAA %1").arg(settings.dns_v6_resp);
                    rules += v6;
                };

                if (!hijack.ruleSets.isEmpty())
                {
                    addHijackRules(QJsonObject{{"rule_set", hijack.ruleSets}});
                }
                if (!hijack.domains.isEmpty() || !hijack.suffixes.isEmpty() || !hijack.regexes.isEmpty())
                {
                    addHijackRules(QJsonObject{
                                {"domain", hijack.domains},
                                {"domain_suffix", hijack.suffixes},
                                {"domain_regex", hijack.regexes},
                            });
                }
            }

            if (settings.fake_dns) {
                servers += QJsonObject{
                        {"tag", tags::dnsFake},
                        {"type", "fakeip"},
                        {"inet4_range", "198.18.0.0/15"},
                        {"inet6_range", "fc00::/18"},
                    };
                rules += QJsonObject{
                        {"query_type", QJsonArray{
                            "A",
                            "AAAA"
                        }},
                     {"action", "route"},
                     {"server", tags::dnsFake}
                };
            }

            if (dns.needDirectDnsRules) {
                appendDnsRoutingRules(rules, dns.direct, settings.direct_dns_strategy, tags::dnsDirect);
            }

            const bool useDirectFinalDNS = settings.dns_final_out == tags::direct;

            if (dns.needProxyDnsRules && useDirectFinalDNS) {
                appendDnsRoutingRules(rules, dns.proxy, settings.remote_dns_strategy, tags::dnsRemote);
            }

            const QString finalStrategy = useDirectFinalDNS ? settings.direct_dns_strategy : settings.remote_dns_strategy;
            appendDnsRoute(rules, QJsonObject{}, finalStrategy, useDirectFinalDNS ? tags::dnsDirect : tags::dnsRemote);

            auto dnsLocalAddress = settings.core_box_underlying_dns.isEmpty() ? "local" : settings.core_box_underlying_dns;
            auto dnsLocalObj = buildDnsObj(ctx, dnsLocalAddress);
            dnsLocalObj["tag"] = tags::dnsLocal;
            servers += dnsLocalObj;

            auto dnsObj = QJsonObject{
                {"servers", servers},
                {"rules", rules},
                {"cache_capacity", settings.dns_cache_capacity},
            };
            if (settings.dns_disable_cache) dnsObj["disable_cache"] = true;
            if (settings.dns_disable_expire) dnsObj["disable_expire"] = true;
            if (settings.dns_optimistic_cache && !settings.dns_disable_cache && !settings.dns_disable_expire) {
                dnsObj["optimistic"] = true;
            }
            if (settings.dns_reverse_mapping) dnsObj["reverse_mapping"] = true;
            if (finalStrategy.startsWith("prefer_")) dnsObj["strategy"] = finalStrategy;
            ctx.result->coreConfig["dns"] = dnsObj;
        }

        void buildInboundSection(BuildContext &ctx) {
            if (ctx.forTest) return;
            const auto &settings = *dataManager->settingsRepo;
            const auto &tun = ctx.prerequisites.tun;
            QJsonArray inbounds;

            if (!settings.disable_mixed_inbound) {
                QJsonObject inboundObj;
                inboundObj["tag"] = tags::mixedIn;
                inboundObj["type"] = "mixed";
                inboundObj["listen"] = settings.inbound_address;
                inboundObj["listen_port"] = settings.inbound_socks_port;
                if (settings.inbound_auth) {
                    inboundObj["users"] = QJsonArray{
                        QJsonObject{
                                                {"username", settings.inbound_user},
                                                {"password", settings.inbound_pass}
                        }
                    };
                }
                inbounds += inboundObj;
            }

            if (settings.spmode_vpn) {
                QJsonObject inboundObj;
                inboundObj["tag"] = tags::tunIn;
                inboundObj["type"] = "tun";
                inboundObj["interface_name"] = genTunName();
                inboundObj["auto_route"] = true;
                inboundObj["mtu"] = settings.vpn_mtu;
                inboundObj["stack"] = settings.vpn_implementation;
                inboundObj["strict_route"] = settings.vpn_strict_route;
                if (ctx.os == Linux && settings.vpn_auto_redirect) inboundObj["auto_redirect"] = true;
                const auto tunIPv4CIDR = settings.vpn_tun_ipv4_cidr;
                const auto tunIPv6CIDR = settings.vpn_tun_ipv6_cidr;
                ctx.result->tunIPv4CIDR = tunIPv4CIDR;
                auto tunAddress = QJsonArray{tunIPv4CIDR};
                if (settings.vpn_ipv6) tunAddress += tunIPv6CIDR;
                inboundObj["address"] = tunAddress;

                QJsonArray routeExcludeAddrs;
                QStringList excludedRanges;
                if (!settings.disable_private_range_bypass) {
                    routeExcludeAddrs = {"127.0.0.0/8", "255.255.255.255/32"};
                    for (const auto &range : tunBypassablePrivateRanges()) {
                        if (!tun.hijackedPrivateRanges.contains(range)) excludedRanges << range;
                    }
                }
                QJsonArray routeExcludeSets;
                if (settings.enable_tun_routing)
                {
                    for (auto item: tun.directIPCIDRs) excludedRanges << item.toString();
                    for (auto item: tun.directIPSets) routeExcludeSets << item;
                }

                if (ctx.os == Darwin) excludedRanges = subtractPrefix(excludedRanges, tunIPv4CIDR);
                for (const auto &range : excludedRanges) routeExcludeAddrs << range;
                inboundObj["route_exclude_address"] = routeExcludeAddrs;
                if (!routeExcludeSets.isEmpty()) inboundObj["route_exclude_address_set"] = routeExcludeSets;
                inbounds += inboundObj;
            }

            inbounds.prepend(QJsonObject{
                {"tag", tags::dnsIn},
                {"type", "direct"},
                {"listen", "127.0.0.1"},
                {"listen_port", settings.core_dns_in_port}
            });

            if (settings.enable_redirect) {
                inbounds.prepend(QJsonObject{
                    {"tag", tags::redirectIn},
                    {"type", "direct"},
                    {"listen", settings.redirect_listen_address},
                    {"listen_port", settings.redirect_listen_port},
                });
            }
            if (settings.enable_dns_server) {
                inbounds.prepend(QJsonObject{
                    {"tag", tags::dnsServerIn},
                    {"type", "direct"},
                    {"listen", settings.dns_server_listen_lan ? "0.0.0.0" : "127.1.1.1"},
                    {"listen_port", settings.dns_server_listen_port},
                });
            }

            QJSONARRAY_ADD(inbounds, QString2QJsonObject(settings.custom_inbound)["inbounds"].toArray())
            ctx.result->coreConfig["inbounds"] = inbounds;
        }

        struct chainScan {
            int hopCount = 0;
            int extraCoreCount = 0;
            int extraCoreIdx = -1;
            int xrayFullConfigCount = 0;
            int xrayFullConfigIdx = -1;
            int xrayHopCount = 0;
            bool hasCustomFullConfig = false;
            int coreTransitions = 0;
        };

        QString chainScanError(const chainScan &scan) {
            if (scan.hasCustomFullConfig)
                return "Custom full config profiles cannot be used in a chain; only custom outbound profiles are chainable";
            if (scan.extraCoreCount > 1)
                return "Only one extra-core profile is allowed in a chain";
            if (scan.xrayFullConfigCount > 1)
                return "Only one custom Xray full config profile is allowed in a chain";
            if (scan.extraCoreCount > 0 && scan.xrayFullConfigCount > 0)
                return "Extra-core and custom Xray full config profiles cannot be combined in a chain";
            if (scan.xrayFullConfigCount > 0 && scan.xrayHopCount > 0)
                return "Custom Xray full config cannot be combined with other Xray hops in a chain (only one Xray instance is supported at a time)";
            if (scan.extraCoreCount == 1 && scan.hopCount > 1 && scan.extraCoreIdx != scan.hopCount - 1)
                return "Extra-core profiles can only be the final hop in a chain (top of the chain editor)";
            if (scan.xrayFullConfigCount == 1 && scan.hopCount > 1 && scan.xrayFullConfigIdx != scan.hopCount - 1)
                return "Custom Xray full config can only be the final hop in a chain (top of the chain editor)";
            if (scan.coreTransitions > 2)
                return "Too many core transitions, the valid sequence is: (optional sing-box chain)->(optional xray chain)->(optional sing-box chain)";
            return {};
        }

        void entIDListtoEntList(BuildContext &ctx, const QList<int> &entIDs, QList<std::shared_ptr<Profile>> &ents, QString &error)
        {
            chainScan scan;
            bool inXray = false;
            for (auto id : entIDs)
            {
                if (id == warpProfileID) {
                    if (inXray) {
                        ctx.xrayToSingTransitioned = true;
                        scan.coreTransitions++;
                    }
                    inXray = false;
                    ents.append(getWarpProfile());
                    continue;
                }
                auto ent = dataManager->profilesRepo->GetProfile(id);
                if (ent == nullptr)
                {
                    error = "Null proxy in chain, you may want to check your configs";
                    return;
                }
                if (!inXray && ent->outbound->IsXray()) {
                    ctx.singToXrayTransitioned = true;
                    scan.coreTransitions++;
                }
                if (inXray && !ent->outbound->IsXray()) {
                    ctx.xrayToSingTransitioned = true;
                    scan.coreTransitions++;
                }
                inXray = ent->outbound->IsXray();
                if (ent->type == "chain")
                {
                    error = "Chain in Chain is not allowed";
                    return;
                }
                if (ent->type == "autoselector")
                {
                    error = "An auto selector cannot be used as a hop; it is not a fixed server";
                    return;
                }
                if (ent->outbound != nullptr && ent->outbound->IsExtraCore()) {
                    scan.extraCoreCount++;
                    scan.extraCoreIdx = static_cast<int>(ents.size());
                }
                if (ent->outbound != nullptr && ent->outbound->IsXrayFullConfig()) {
                    scan.xrayFullConfigCount++;
                    scan.xrayFullConfigIdx = static_cast<int>(ents.size());
                }
                if (ent->outbound != nullptr && ent->outbound->IsXray()) {
                    scan.xrayHopCount++;
                }
                if (isCustomFullConfig(ent)) scan.hasCustomFullConfig = true;
                ents.append(ent);
            }
            scan.hopCount = static_cast<int>(ents.size());
            if (auto scanError = chainScanError(scan); !scanError.isEmpty()) error = scanError;
        }

        QList<int> unwrapChain(int entID) {
            auto ent = dataManager->profilesRepo->GetProfile(entID);
            if (ent == nullptr)
            {
                return {};
            }
            if (ent->type == "chain") {
                auto chain = ent->Chain();
                if (chain == nullptr) return {};
                QList<int> reversed;
                for (int idx = chain->list.size() - 1; idx >= 0; idx--) reversed.append(chain->list[idx]);
                return reversed;
            }
            return {entID};
        }

        struct hopChainOptions {
            QString prefix;
            bool includeProxy = false;
            bool link = true;
            int startSuffix = 0;
            bool markIngress = false;
            bool warpWrap = false;
        };

        void buildSingboxChain(BuildContext &ctx, const QList<std::shared_ptr<Profile>> &ents, const hopChainOptions &opts) {
            for (int idx = 0; idx < ents.size(); idx++)
            {
                auto tag = hopTag(opts.prefix, opts.startSuffix + idx);
                QString nextTag;
                if (idx < ents.size() - 1) nextTag = hopTag(opts.prefix, opts.startSuffix + idx + 1);
                if (opts.includeProxy && idx == 0) tag = tags::proxy;
                if (opts.warpWrap && idx == 1) tag = tags::warpBypass;
                if (opts.markIngress && idx == 0) ctx.singIngressTags << tag;
                const auto& ent = ents[idx];
                auto [object, error] = ent->outbound->Build();
                if (!error.isEmpty())
                {
                    ctx.error += error;
                    return;
                }
                object["tag"] = tag;
                if (!nextTag.isEmpty() && opts.link) object["detour"] = nextTag;
                if (opts.warpWrap && idx == 0) object["detour"] = tags::warpBypass;
                if (ent->outbound->IsEndpoint())
                {
                    ctx.endpoints.append(object);
                } else
                {
                    ctx.outbounds.append(object);
                }
            }
        }

        void buildXrayChain(BuildContext &ctx, const QList<std::shared_ptr<Profile>> &ents, const hopChainOptions &opts,
                            const coreBridgeConfig &bridgeConfig) {
            for (int idx = 0; idx < ents.size(); idx++)
            {
                auto tag = hopTag(opts.prefix, opts.startSuffix + idx);
                QString nextTag;
                if (idx < ents.size() - 1 || bridgeConfig.needed) nextTag = hopTag(opts.prefix, opts.startSuffix + idx + 1);
                if (opts.includeProxy && idx == 0) tag = tags::proxy;
                if (idx == 0) ctx.xrayIngressTags << tag;
                const auto& ent = ents[idx];
                auto [object, error] = ent->outbound->BuildXray();
                if (!error.isEmpty())
                {
                    ctx.error += error;
                    return;
                }
                object["tag"] = tag;
                if (!nextTag.isEmpty() && (opts.link || bridgeConfig.needed)) object["proxySettings"] = QJsonObject{
                    {"tag", nextTag},
                    {"transportLayer", true}
                };
                ctx.xrayOutbounds.append(object);
            }
            if (bridgeConfig.needed) {
                ctx.xrayOutbounds.append(QJsonObject{
                    {"tag", hopTag(opts.prefix, opts.startSuffix + static_cast<int>(ents.size()))},
                    {"protocol", "socks"},
                    {"settings", QJsonObject{
                        {"address", bridgeConfig.host},
                        {"port", bridgeConfig.port},
                        {"user", bridgeConfig.auth},
                        {"pass", bridgeConfig.auth},
                    }},
                });
            }
        }

        struct ChainBuildRequest {
            QList<int> hopIDs;
            QString prefix;
            bool includeProxy = false;
            bool link = true;
            int startSuffix = 0;
            int singToXrayPort = -1;
            int xrayToSingPort = -1;
            int xrayFullConfigPort = -1;
            bool soleXrayInbound = false;
            bool warpWrap = false;
        };

        QString buildOutboundChain(BuildContext &ctx, const ChainBuildRequest &req)
        {
            const auto ingressTag = req.includeProxy ? QString(tags::proxy) : hopTag(req.prefix, req.startSuffix);

            ctx.singToXrayTransitioned = false;
            ctx.xrayToSingTransitioned = false;
            QList<std::shared_ptr<Profile>> ents;
            entIDListtoEntList(ctx, req.hopIDs, ents, ctx.error);
            if (!ctx.error.isEmpty()) return ingressTag;

            if (!ents.isEmpty() && ents.last()->outbound != nullptr && ents.last()->outbound->IsXrayFullConfig()) {
                auto custom = ents.last()->Custom();
                if (custom == nullptr) {
                    ctx.error = "Failed to cast to Custom for Xray full config hop";
                    return ingressTag;
                }
                auto userXrayConfig = QString2QJsonObject(custom->config);
                if (userXrayConfig.isEmpty()) {
                    ctx.error = "Custom Xray full config is not valid JSON";
                    return ingressTag;
                }
                int port = req.xrayFullConfigPort;
                if (port <= 0) port = MkManyPorts(1, custom->bridgeHost)[0];
                if (port <= 0) {
                    ctx.error = "Could not reserve a local port for the custom Xray full config bridge";
                    return ingressTag;
                }
                custom->bridgePort = port;
                custom->bridgeAuth = GetRandomString(32);

                auto bridgeInbound = xraySocksInbound(tags::xrayFullConfigIn,
                    {true, custom->bridgePort, custom->bridgeAuth, custom->bridgeHost});
                bridgeInbound["sniffing"] = QJsonObject{
                    {"enabled", true},
                    {"destOverride", QJsonArray{"http", "tls", "quic"}},
                    {"routeOnly", false}
                };
                auto inbounds = (ctx.forTest || req.soleXrayInbound) ? QJsonArray()
                                                                     : userXrayConfig["inbounds"].toArray();
                inbounds.prepend(bridgeInbound);
                userXrayConfig["inbounds"] = inbounds;

                ctx.result->xrayConfig = userXrayConfig;
                ctx.result->isXrayNeeded = true;
            }

            QList<std::shared_ptr<Profile>> initialSingEnts;
            QList<std::shared_ptr<Profile>> xrayEnts;
            QList<std::shared_ptr<Profile>> tailingSingEnts;
            for (const auto& ent : ents) {
                if (ent->outbound->IsXray()) xrayEnts.append(ent);
                else {
                    if (xrayEnts.isEmpty()) initialSingEnts.append(ent);
                    else tailingSingEnts.append(ent);
                }
            }
            QList<int> ports;
            auto bridgePort = [&ports](int given) {
                if (given > 0) return given;
                if (ports.isEmpty()) ports = MkManyPorts(2);
                return ports.takeFirst();
            };
            if (ctx.singToXrayTransitioned) {
                const int port = bridgePort(req.singToXrayPort);
                if (port <= 0) {
                    ctx.error = "Could not reserve a local port for the sing-box -> Xray bridge";
                    return ingressTag;
                }
                coreBridgeConfig singToXrayBridgeConf = {true, port, GetRandomString(32)};
                ctx.singToXrayBridges << singToXrayBridgeConf;
                auto bridgeEnt = ProfilesRepo::NewProfile("socks");
                auto socksOutbound = bridgeEnt->Socks();
                socksOutbound->username = singToXrayBridgeConf.auth;
                socksOutbound->password = singToXrayBridgeConf.auth;
                socksOutbound->server = singToXrayBridgeConf.host;
                socksOutbound->server_port = singToXrayBridgeConf.port;
                initialSingEnts << bridgeEnt;
            }
            coreBridgeConfig xrayToSingBridgeConf;
            if (ctx.xrayToSingTransitioned) {
                const int port = bridgePort(req.xrayToSingPort);
                if (port <= 0) {
                    ctx.error = "Could not reserve a local port for the Xray -> sing-box bridge";
                    return ingressTag;
                }
                xrayToSingBridgeConf = {true, port, GetRandomString(32)};
                ctx.xrayToSingBridges << xrayToSingBridgeConf;
            }

            const hopChainOptions leadingOpts{
                .prefix = req.prefix,
                .includeProxy = req.includeProxy,
                .link = req.link,
                .startSuffix = req.startSuffix,
                .markIngress = false,
                .warpWrap = req.warpWrap,
            };
            const int tailingStartSuffix = req.startSuffix + static_cast<int>(initialSingEnts.size());
            if (!initialSingEnts.isEmpty()) {
                buildSingboxChain(ctx, initialSingEnts, leadingOpts);
            }
            if (!xrayEnts.isEmpty()) {
                buildXrayChain(ctx, xrayEnts, leadingOpts, xrayToSingBridgeConf);
            }
            if (!tailingSingEnts.isEmpty()) {
                buildSingboxChain(ctx, tailingSingEnts, {
                    .prefix = req.prefix,
                    .includeProxy = false,
                    .link = req.link,
                    .startSuffix = tailingStartSuffix,
                    .markIngress = true,
                    .warpWrap = false,
                });
            }

            if (!ents.isEmpty()) {
                TrafficChainGroup group;
                group.profiles = ents;
                if (!tailingSingEnts.isEmpty()) {
                    group.watchTag = hopTag(req.prefix, tailingStartSuffix);
                } else {
                    group.watchTag = ingressTag;
                }
                ctx.result->chainGroups.append(group);
            }
            return ingressTag;
        }

        struct memberBridges
        {
            bool singToXray = false;
            bool xrayToSing = false;
            bool xrayFullConfig = false;

            [[nodiscard]] int count() const
            {
                return (singToXray ? 1 : 0) + (xrayToSing ? 1 : 0) + (xrayFullConfig ? 1 : 0);
            }
        };

        memberBridges bridgesFor(const QList<int> &hopIDs)
        {
            memberBridges needed;
            bool inXray = false;
            for (int id : hopIDs)
            {
                auto hop = dataManager->profilesRepo->GetProfile(id);
                if (hop == nullptr || hop->outbound == nullptr) continue;
                if (hop->outbound->IsXrayFullConfig()) needed.xrayFullConfig = true;
                const bool xray = hop->outbound->IsXray();
                if (xray && !inXray) needed.singToXray = true;
                if (!xray && inXray) needed.xrayToSing = true;
                inXray = xray;
            }
            return needed;
        }

        QString buildAutoSelectorGroup(BuildContext &ctx, const std::shared_ptr<Group> &group, bool warpWrap)
        {
            const auto &settings = *dataManager->settingsRepo;
            auto selector = ctx.ent->AutoSelector();
            if (selector == nullptr)
            {
                ctx.error = "Ent is nullptr after cast to auto selector, data is corrupted";
                return {};
            }
            selector->Normalize();
            const auto plan = PlanAutoSelector(ctx.ent);
            if (!plan.error.isEmpty())
            {
                ctx.error = plan.error;
                return {};
            }

            const QString groupTag = warpWrap ? tags::warpBypass : tags::proxy;
            const int chainGroupsBefore = static_cast<int>(ctx.result->chainGroups.size());

            AutoSelectorBuildInfo info;
            info.groupTag = groupTag;
            info.profile = ctx.ent;

            struct plannedMember
            {
                std::shared_ptr<Profile> ent;
                QList<int> hopIDs;
                memberBridges bridges;
            };
            QList<plannedMember> planned;
            int bridgeCount = 0;
            for (int id : plan.build)
            {
                auto member = dataManager->profilesRepo->GetProfile(id);
                if (member == nullptr) continue;
                QList<int> hopIDs;
                if (group->landing_proxy_id >= 0) hopIDs.append(group->landing_proxy_id);
                hopIDs.append(id);
                if (group->front_proxy_id >= 0) hopIDs.append(group->front_proxy_id);
                const auto bridges = bridgesFor(hopIDs);
                bridgeCount += bridges.count();
                planned.append({member, hopIDs, bridges});
            }
            auto bridgePorts = MkManyPorts(bridgeCount);
            int portIdx = 0;
            
            const auto builtAt = QDateTime::currentSecsSinceEpoch();
            QJsonArray warm;
            QString pinnedTag;

            QJsonArray memberTags;
            int idx = 0;
            for (const auto &[member, hopIDs, bridges] : planned)
            {
                const int singToXrayPort = bridges.singToXray ? bridgePorts[portIdx++] : -1;
                const int xrayToSingPort = bridges.xrayToSing ? bridgePorts[portIdx++] : -1;
                const int xrayFullConfigPort = bridges.xrayFullConfig ? bridgePorts[portIdx++] : -1;

                const auto tag = buildOutboundChain(ctx, {
                    .hopIDs = hopIDs,
                    .prefix = hopTag(tags::poolChainPrefix, idx),
                    .singToXrayPort = singToXrayPort,
                    .xrayToSingPort = xrayToSingPort,
                    .xrayFullConfigPort = xrayFullConfigPort,
                    .soleXrayInbound = bridges.xrayFullConfig,
                });
                if (!ctx.error.isEmpty()) return {};
                if (bridges.xrayFullConfig)
                {
                    if (ctx.result->xrayConfig.isEmpty())
                    {
                        ctx.error = "Custom Xray full config member produced no Xray config";
                        return {};
                    }
                    ctx.result->xrayFullConfigs << QJsonObject2QString(ctx.result->xrayConfig, false);
                    ctx.result->xrayConfig = QJsonObject();
                    ctx.result->isXrayNeeded = false;
                }
                memberTags.append(tag);
                info.members.append({tag, member});
                if (member->id == selector->pinnedID) pinnedTag = tag;
                if (member->latency != 0 && member->latency_at > 0 && selector->resultValidityMins > 0)
                {
                    if (const auto age = builtAt - member->latency_at;
                        age >= 0 && age <= static_cast<qint64>(selector->resultValidityMins) * 60)
                    {
                        warm.append(QJsonObject{
                            {"tag", tag},
                            {"rtt", member->latency > 0 ? member->latency : 0},
                            {"age", static_cast<double>(age)},
                        });
                    }
                }
                if (!ctx.result->chainGroups.isEmpty())
                    ctx.result->chainGroups.last().profiles.append(ctx.ent);
                idx++;
            }
            if (memberTags.isEmpty())
            {
                ctx.error = "Auto selector produced no usable members";
                return {};
            }

            if (warpWrap)
            {
                QList<std::shared_ptr<Profile>> credited;
                while (ctx.result->chainGroups.size() > chainGroupsBefore)
                    credited << ctx.result->chainGroups.takeLast().profiles;
                TrafficChainGroup warpGroup;
                warpGroup.watchTag = tags::proxy;
                warpGroup.profiles = credited;
                ctx.result->chainGroups.append(warpGroup);
            }

            QJsonObject groupObject{
                {"type", "auto-selector"},
                {"tag", groupTag},
                {"outbounds", memberTags},
                {"url", selector->testURL.isEmpty() ? settings.test_latency_url : selector->testURL},
                {"interval", Int2String(selector->intervalSec) + "s"},
                {"bench_interval", Int2String(selector->benchIntervalSec) + "s"},
                {"watch_interval", Int2String(selector->watchIntervalSec) + "s"},
                {"active_size", selector->activeSize},
                {"sampling", selector->sampling},
                {"tolerance", selector->toleranceMs},
                {"expected", selector->expected},
                {"dial_retries", selector->dialRetries},
                {"interrupt_exist_connections", selector->interruptOnSwitch},
            };
            if (!warm.isEmpty()) groupObject["warm"] = warm;
            if (!pinnedTag.isEmpty()) groupObject["pinned"] = pinnedTag;
            if (selector->maxRTTms > 0) groupObject["max_rtt"] = Int2String(selector->maxRTTms) + "ms";
            groupObject["connectivity_url"] = selector->connectivityURL.isEmpty()
                                                  ? settings.test_latency_url
                                                  : selector->connectivityURL;
            if (selector->balance)
            {
                groupObject["balance"] = true;
                groupObject["balance_mode"] = selector->balanceMode;
                groupObject["balance_interval"] = Int2String(selector->balanceIntervalSec) + "s";
            }
            ctx.outbounds.append(groupObject);
            ctx.result->autoSelectors.append(info);
            return groupTag;
        }

        void buildWarpInFrontOfSelector(BuildContext &ctx)
        {
            auto warpEnt = getWarpProfile();
            auto [warpObject, warpError] = warpEnt->outbound->Build();
            if (!warpError.isEmpty())
            {
                ctx.error += warpError;
                return;
            }
            warpObject["tag"] = tags::proxy;
            warpObject["detour"] = tags::warpBypass;
            if (warpEnt->outbound->IsEndpoint()) ctx.endpoints.append(warpObject);
            else ctx.outbounds.append(warpObject);
        }

        void buildOutboundsSection(BuildContext &ctx) {
            auto group = dataManager->groupsRepo->GetGroup(ctx.ent->gid);
            if (group == nullptr)
            {
                ctx.error = "No group found for ent, data is corrupted";
                return;
            }
            const bool warpWrap = dataManager->settingsRepo->enable_warp;
            if (ctx.ent->type == "autoselector")
            {
                buildAutoSelectorGroup(ctx, group, warpWrap);
                if (!ctx.error.isEmpty()) return;
                if (warpWrap)
                {
                    buildWarpInFrontOfSelector(ctx);
                    if (!ctx.error.isEmpty()) return;
                }
            }
            else
            {
                QList<int> entIDs;
                if (group->landing_proxy_id >= 0) entIDs.prepend(group->landing_proxy_id);
                if (ctx.ent->type == "chain")
                {
                    auto chain = ctx.ent->Chain();
                    if (chain == nullptr)
                    {
                        ctx.error = "Ent is nullptr after cast to chain, data is corrupted";
                        return;
                    }
                    for (int idx = chain->list.size()-1; idx >=0; idx--) entIDs.append(chain->list[idx]);
                } else
                {
                    entIDs.append(ctx.ent->id);
                }
                if (group->front_proxy_id >= 0) entIDs.append(group->front_proxy_id);
                if (warpWrap) {
                    entIDs.prepend(warpProfileID);
                }
                buildOutboundChain(ctx, {
                    .hopIDs = entIDs,
                    .prefix = tags::mainChainPrefix,
                    .includeProxy = true,
                    .warpWrap = warpWrap,
                });

                if (ctx.ent->type == "chain" && !ctx.result->chainGroups.isEmpty()) {
                    ctx.result->chainGroups.last().profiles.append(ctx.ent);
                }
            }

            int routeSuffix = 0;
            for (const auto& routeGroup : ctx.prerequisites.routing.routeOutboundGroups) {
                buildOutboundChain(ctx, {
                    .hopIDs = routeGroup.hopIDs,
                    .prefix = tags::routeChainPrefix,
                    .link = routeGroup.hopIDs.size() > 1,
                    .startSuffix = routeSuffix,
                });
                if (routeGroup.chainWrapper != nullptr && !ctx.result->chainGroups.isEmpty()) {
                    ctx.result->chainGroups.last().profiles.append(routeGroup.chainWrapper);
                }
                routeSuffix += static_cast<int>(routeGroup.hopIDs.size());
            }

            if (auto mismatch = bridgeIngressMismatch(ctx); !mismatch.isEmpty()) {
                ctx.error = mismatch;
                return;
            }
            QJsonArray inboundArr;
            if (ctx.result->coreConfig.contains("inbounds")) {
                inboundArr = ctx.result->coreConfig["inbounds"].toArray();
            }
            for (qsizetype idx = 0; idx < ctx.xrayToSingBridges.size(); idx++) {
                inboundArr.append(socksBridgeInbound(bridgeTagFor(ctx.singIngressTags[idx]), ctx.xrayToSingBridges[idx]));
            }
            ctx.result->coreConfig["inbounds"] = inboundArr;

            ctx.outbounds.append(QJsonObject{
            {"type", "direct"},
            {"tag", tags::direct}
            });

            ctx.result->coreConfig["endpoints"] = ctx.endpoints;
            ctx.result->coreConfig["outbounds"] = ctx.outbounds;
        }

        QJsonArray buildRuleSetArray(const BuildContext &ctx) {
            QJsonArray ruleSetArray;
            for (const auto &item: ctx.prerequisites.routing.neededRuleSets) {
                if (auto url = QUrl(item); url.isValid() && url.fileName().contains(".srs")) {
                    ruleSetArray += QJsonObject{
                                {"type", "remote"},
                                {"tag", get_rule_set_name(item)},
                                {"format", "binary"},
                                {"url", item},
                            };
                }
                else
                    if (auto url = ruleSetUrl(item.toStdString()); !url.empty()) {
                        ruleSetArray += QJsonObject{
                                    {"type", "remote"},
                                    {"tag", item},
                                    {"format", "binary"},
                                    {"url", get_jsdelivr_link(QString::fromUtf8(url.data(), url.size()))},
                                };
                    }
            }

            if (dataManager->settingsRepo->adblock_enable) {
                ruleSetArray += QJsonObject{
                            {"type", "remote"},
                            {"tag", tags::adblockRuleSet},
                            {"format", "binary"},
                            {"url", get_jsdelivr_link("https://raw.githubusercontent.com/217heidai/adblockfilters/main/rules/adblocksingbox.srs")},
                        };
            }
            return ruleSetArray;
        }

        void buildRouteSection(BuildContext &ctx) {
            const auto &settings = *dataManager->settingsRepo;
            auto routeChain = dataManager->routesRepo->GetRouteProfile(settings.current_route_id);
            if (routeChain == nullptr) {
                ctx.error = "Routing profile does not exist, try resetting the route profile in Routing Settings";
                return;
            }
            routeChain = std::make_shared<RouteProfile>(*routeChain);
            const auto &routeDeps = ctx.prerequisites.routing;

            QJsonObject rawRouteObj;
            if (routeChain->isRaw) {
                rawRouteObj = QString2QJsonObject(routeChain->rawRoute);
                if (rawRouteObj.isEmpty()) {
                    ctx.error = "Raw routing profile is not a valid JSON object";
                    return;
                }
                rawRouteObj = RouteProfile::TranslateRawOutbounds(rawRouteObj, routeDeps.outboundMap);
                if (routeChain->preventModifications) {
                    ctx.result->coreConfig["route"] = rawRouteObj;
                    return;
                }
            }

            struct InjectedRules {
                QJsonObject sniff;
                QJsonObject resolve;
                QJsonObject dnsHijack;
                QJsonObject dnsInReject;
                QJsonObject redirectSniff;
            } injected;

            if (!routeChain->isRaw) {
                injected.sniff = QJsonObject{{"action", "sniff"}};
                if (!settings.resolve_domain_strategy.isEmpty()) {
                    injected.resolve = QJsonObject{
                        {"inbound", QJsonArray{tags::mixedIn, tags::tunIn}},
                        {"action", "resolve"},
                        {"strategy", settings.resolve_domain_strategy},
                    };
                }
                injected.dnsHijack = QJsonObject{
                    {"protocol", "dns"},
                    {"action", "hijack-dns"},
                };
                if (settings.enable_redirect && !ctx.forTest) {
                    injected.redirectSniff = QJsonObject{
                        {"inbound", QJsonArray{tags::redirectIn}},
                        {"action", "sniff"},
                        {"override_destination", true},
                    };
                }
            }
            if (!ctx.forTest) {
                injected.dnsInReject = QJsonObject{
                    {"inbound", tags::dnsIn},
                    {"action", "reject"},
                };
            }

            auto profileRules = routeChain->isRaw ? rawRouteObj.value("rules").toArray()
                                                  : routeChain->get_route_rules(false, routeDeps.outboundMap);

            QJsonObject extraCoreDirect;
            if (!ctx.result->extraCoreData->path.isEmpty())
            {
                extraCoreDirect = QJsonObject{
                    {"action", "route"},
                    {"process_path", extraCoreProcessPaths(ctx.result->extraCoreData->path)},
                    {"outbound", tags::direct},
                };
            }

            auto ruleSetArray = buildRuleSetArray(ctx);

            if (auto mismatch = bridgeIngressMismatch(ctx); !mismatch.isEmpty()) {
                ctx.error = mismatch;
                return;
            }
            QJsonArray bridgeRules;
            for (qsizetype idx = 0; idx < ctx.xrayToSingBridges.size(); idx++) {
                bridgeRules.append(QJsonObject{
                    {"inbound", bridgeTagFor(ctx.singIngressTags[idx])},
                    {"action", "route"},
                    {"outbound", ctx.singIngressTags[idx]},
                });
            }

            if (routeChain->isRaw) {
                for (const auto& rs : rawRouteObj.value("rule_set").toArray()) ruleSetArray.append(rs);
            }

            const int defOut = routeChain->defaultOutboundID;

            QJsonArray routeRules;
            for (const auto& r : bridgeRules) routeRules.append(r);
            if (!extraCoreDirect.isEmpty()) routeRules.append(extraCoreDirect);
            auto appendIfSet = [&routeRules](const QJsonObject& r) { if (!r.isEmpty()) routeRules.append(r); };
            appendIfSet(injected.sniff);
            appendIfSet(injected.resolve);
            appendIfSet(injected.dnsHijack);
            appendIfSet(injected.dnsInReject);
            appendIfSet(injected.redirectSniff);
            for (const auto& r : profileRules) routeRules.append(r);
            if (!routeChain->isRaw && defOut == blockID) {
                routeRules.append(QJsonObject{{"action", "reject"}});
            }

            QJsonObject route = routeChain->isRaw ? rawRouteObj : QJsonObject{};
            route["rules"] = routeRules;
            route["rule_set"] = ruleSetArray;
            if (routeChain->isRaw) {
                if (!route.contains("final")) route["final"] = tags::proxy;
            } else if (defOut == blockID) {
                route["final"] = tags::direct;
            } else if (defOut == warpBypassID) {
                route["final"] = settings.enable_warp ? tags::warpBypass : tags::proxy;
            } else {
                route["final"] = outboundIDToString(defOut);
            }
            if (settings.enable_stats && !route.contains("find_process"))  route["find_process"] = true;
            if (!route.contains("default_domain_resolver"))
                route["default_domain_resolver"] = QJsonObject{
                                        {"server", tags::dnsDirect},
                                        {"strategy", settings.default_domain_strategy}};
            if (settings.spmode_vpn && !route.contains("auto_detect_interface")) route["auto_detect_interface"] = true;

            ctx.result->coreConfig["route"] = route;
        }

        void buildExperimentalSection(BuildContext &ctx) {
            if (ctx.forTest) return;
            const auto &settings = *dataManager->settingsRepo;

            QJsonObject experimentalObj;
            QJsonObject clash_api = {
                {"default_mode", ""}
            };
            if (settings.core_box_clash_api > 0){
                clash_api = {
                    {"external_controller", settings.core_box_clash_listen_addr + ":" + Int2String(settings.core_box_clash_api)},
                    {"secret", settings.core_box_clash_api_secret},
                    {"external_ui", "dashboard"},
                    };
            }
            if (settings.core_box_clash_api > 0 || settings.enable_stats)
            {
                experimentalObj["clash_api"] = clash_api;
            }

            experimentalObj["cache_file"] = QJsonObject{
                {"enabled", true},
                {"store_fakeip", true},
                {"store_dns", true}
            };

            ctx.result->coreConfig["experimental"] = experimentalObj;
        }

        void buildXrayConfig(BuildContext &ctx) {
            if (ctx.xrayOutbounds.isEmpty()) return;
            ctx.result->isXrayNeeded = true;
            QJsonArray inbounds;
            QJsonArray routeRules;

            if (ctx.xrayIngressTags.size() != ctx.singToXrayBridges.size()) {
                ctx.error = "xray ingress tags size does not match bridge count!";
                return;
            }

            for (qsizetype i = 0; i < ctx.xrayIngressTags.size(); i++) {
                const auto outboundTag = ctx.xrayIngressTags[i];
                const auto inboundTag = outboundTag + "-" + "inbound";
                inbounds << xraySocksInbound(inboundTag, ctx.singToXrayBridges[i]);
                routeRules << QJsonObject{
                    {"type", "field"},
                    {"inboundTag", QJsonArray{inboundTag}},
                    {"outboundTag", outboundTag}
                };
            }

            ctx.result->xrayConfig["log"] = QJsonObject{
            {"loglevel", dataManager->settingsRepo->xray_log_level},
            {"access", dataManager->settingsRepo->xray_log_level == "info" ? "" : "none"}
            };
            ctx.result->xrayConfig["inbounds"] = inbounds;
            ctx.result->xrayConfig["outbounds"] = ctx.xrayOutbounds;
            ctx.result->xrayConfig["routing"] = QJsonObject{
                {"domainStrategy", "AsIs"},
                {"rules", routeRules},
            };
        }

        enum class testCandidate {
            Build,
            XrayFullConfig,
            Skip,
        };

        struct testCandidateKind {
            testCandidate kind = testCandidate::Build;
            const char *skipReason = nullptr;
        };

        testCandidateKind classifyTestCandidate(const std::shared_ptr<Profile> &profile)
        {
            if (profile->outbound != nullptr && profile->outbound->IsExtraCore())
                return {testCandidate::Skip, "Skipping extra-core conf"};
            if (profile->outbound != nullptr && profile->outbound->IsXrayFullConfig())
                return {testCandidate::XrayFullConfig, nullptr};
            if (profile->type == "chain")
            {
                if (auto chain = profile->Chain(); chain != nullptr) {
                    for (int hopID : chain->list) {
                        auto hopEnt = dataManager->profilesRepo->GetProfile(hopID);
                        if (hopEnt != nullptr && hopEnt->outbound != nullptr &&
                            (hopEnt->outbound->IsExtraCore() || hopEnt->outbound->IsXrayFullConfig()))
                            return {testCandidate::Skip, "Skipping chain with terminal (extra-core or Xray full config) hop (cannot test)"};
                    }
                }
                return {testCandidate::Build, nullptr};
            }
            if (profile->type == "tailscale")
                return {testCandidate::Skip, "Skipping Tailscale conf"};
            if (profile->type == "autoselector")
                return {testCandidate::Skip, "Skipping auto selector conf (test its members instead)"};
            return {testCandidate::Build, nullptr};
        }

    }

    std::shared_ptr<BuildConfigResult> BuildSingBoxConfig(const std::shared_ptr<Profile>& ent) {
        if (ent->type == "custom")
        {
            auto res = std::make_shared<BuildConfigResult>();
            auto custom = ent->Custom();
            if (custom == nullptr)
            {
                res->error = "Corrupted data, needed custom ent, got nullptr";
                return res;
            }
            if (custom->type == Custom::CustomFullConfig)
            {
                res->coreConfig = custom->Build().object;
                return res;
            }
        }

        BuildContext ctx;
        ctx.ent = ent;

        auto failed = [&ctx] {
            if (ctx.error.isEmpty()) return false;
            MW_show_log("Config build error:" + ctx.error);
            ctx.result->error = ctx.error;
            return true;
        };

        calculatePrerequisites(ctx);
        if (failed()) return ctx.result;

        buildLogSection(ctx);
        buildNTPSection(ctx);
        buildDNSSection(ctx);
        if (failed()) return ctx.result;

        buildCertificateSection(ctx);
        buildInboundSection(ctx);
        if (failed()) return ctx.result;

        buildOutboundsSection(ctx);
        if (failed()) return ctx.result;

        buildRouteSection(ctx);
        if (failed()) return ctx.result;

        buildExperimentalSection(ctx);
        if (failed()) return ctx.result;

        buildXrayConfig(ctx);
        if (failed()) return ctx.result;

        return ctx.result;
    }

    bool IsValid(const std::shared_ptr<Profile>& ent)
    {
        if (ent->type == "autoselector")
        {
            const auto plan = PlanAutoSelector(ent);
            if (!plan.error.isEmpty())
            {
                MW_show_log("Invalid auto selector: " + plan.error);
                return false;
            }
            return !plan.build.isEmpty();
        }
        if (ent->type == "chain")
        {
            auto chain = ent->Chain();
            if (chain == nullptr)
            {
                MW_show_log("Corrupted data, needed chain ent, got nullptr");
                return false;
            }
            for (int eId : chain->list)
            {
                auto e = dataManager->profilesRepo->GetProfile(eId);
                if (e == nullptr)
                {
                    MW_show_log("Null ent in validator");
                    return false;
                }
                if (!IsValid(e))
                {
                    MW_show_log("Invalid ent in chain: ID=" + QString::number(eId));
                    return false;
                }
            }
            return true;
        }
        QJsonObject conf;
        bool fullConf = false;
        if (ent->type == "custom")
        {
            auto custom = ent->Custom();
            if (custom == nullptr)
            {
                MW_show_log("Corrupted data in isValid, needed custom ent, got nullptr");
                return false;
            }
            if (custom->type == Custom::CustomFullConfig)
            {
                conf = QString2QJsonObject(custom->config);
                fullConf = true;
            }
            if (custom->type == Custom::CustomXrayFullConfig)
            {
                if (QString2QJsonObject(custom->config).isEmpty()) {
                    MW_show_log("Custom Xray full config is not valid JSON");
                    return false;
                }
                return true;
            }
        }
        if (!fullConf && ent->outbound->IsXray())
        {
            auto [out, err] = ent->outbound->BuildXray();
            if (!err.isEmpty())
            {
                MW_show_log("Invalid Xray ent " + ent->outbound->name + ": " + err);
                return false;
            }
            QJsonObject xrayConf{
                {"outbounds", QJsonArray{out}},
            };
            bool ok;
            auto resp = API::defaultClient->CheckConfig(&ok, QJsonObject2QString(xrayConf, true), true);
            if (!ok)
            {
                MW_show_log("Failed to Call the Core: " + resp);
                return false;
            }
            if (resp.isEmpty()) return true;
            MW_show_log("Invalid Xray ent " + ent->outbound->name + ": " + resp);
            return false;
        }
        if (!fullConf)
        {
            auto out = ent->outbound->Build();
            auto outArr = QJsonArray{out.object};
            auto key = ent->outbound->IsEndpoint() ? "endpoints" : "outbounds";
            conf = {
                {key, outArr},
                };
        }
        bool ok;
        conf.insert("log", QJsonObject{{"level", dataManager->settingsRepo->log_level}});
        auto resp = API::defaultClient->CheckConfig(&ok, QJsonObject2QString(conf, true));
        if (!ok)
        {
            MW_show_log("Failed to Call the Core: " + resp);
            return false;
        }
        if (resp.isEmpty()) return true;
        MW_show_log("Invalid ent " + ent->outbound->name + ": " + resp);
        return false;
    }

    std::shared_ptr<BuildTestConfigResult> BuildTestConfig(const QList<std::shared_ptr<Profile> > &profiles)
    {
        auto res = std::make_shared<BuildTestConfigResult>();
        BuildContext ctx;
        ctx.forTest = true;
        QList<int> entIDs;
        for (const auto& proxy : profiles) entIDs << proxy->id;
        ctx.prerequisites.dns.direct.domains = QListStr2QJsonArray(getEntDomains(entIDs, ctx.error));
        if (!ctx.prerequisites.dns.direct.domains.isEmpty()) ctx.prerequisites.dns.needDirectDnsRules = true;
        buildDNSSection(ctx, false);
        if (!ctx.error.isEmpty())
        {
            res->error = ctx.error;
            return res;
        }
        buildLogSection(ctx);
        buildCertificateSection(ctx);
        buildNTPSection(ctx);
        int suffix = 1;

        int xrayPortIdx=0;
        int xrayCount=0;
        int chainCount=0;
        for (const auto& proxy : profiles) {
            if (proxy->outbound->IsXray()) xrayCount++;
            if (proxy->type == "chain") chainCount++;
        }
        auto xrayPorts = MkManyPorts(xrayCount + 2*chainCount);

        for (const auto& item : profiles)
        {
            const auto candidate = classifyTestCandidate(item);
            if (candidate.kind == testCandidate::Skip)
            {
                MW_show_log(candidate.skipReason);
                continue;
            }
            if (candidate.kind == testCandidate::XrayFullConfig)
            {
                if (!IsValid(item)) {
                    MW_show_log("Skipping invalid custom Xray full config: " + item->outbound->name);
                    item->SetLatency(-1);
                    continue;
                }
                auto tag = buildOutboundChain(ctx, {
                    .hopIDs = {item->id},
                    .prefix = hopTag(tags::testXrayFullPrefix, item->id),
                });
                if (!ctx.error.isEmpty()) {
                    res->error = ctx.error;
                    return res;
                }
                if (!ctx.result->isXrayNeeded || ctx.result->xrayConfig.isEmpty()) {
                    MW_show_log("Custom Xray full config produced no Xray config: " + item->outbound->name);
                    item->SetLatency(-1);
                    continue;
                }
                res->xrayFullConfigs << QJsonObject2QString(ctx.result->xrayConfig, false);
                ctx.result->xrayConfig = QJsonObject();
                ctx.result->isXrayNeeded = false;
                res->outboundTags << tag;
                res->tag2entID.insert(tag, item->id);
                continue;
            }
            if (!IsValid(item)) {
                MW_show_log("Skipping invalid config: " + item->outbound->name);
                item->SetLatency(-1);
                continue;
            }
            if (item->type == "custom")
            {
                auto custom = item->Custom();
                if (custom == nullptr)
                {
                    MW_show_log("Corrupted data in build test config");
                    res->error = "Corrupted data in build test config";
                    return res;
                }
                if (custom->type == Custom::CustomFullConfig)
                {
                    auto obj = QString2QJsonObject(custom->config);
                    obj["inbounds"] = QJsonArray();
                    res->fullConfigs[item->id] = QJsonObject2QString(obj, true);
                    continue;
                }
            }
            auto IDs = unwrapChain(item->id);
            auto group = dataManager->groupsRepo->GetGroup(item->gid);
            if (group == nullptr) {
                res->error = "Null group on profile, data is corrupted";
                return res;
            }
            if (group->landing_proxy_id >= 0) IDs.prepend(group->landing_proxy_id);
            if (group->front_proxy_id >= 0) IDs.append(group->front_proxy_id);
            int singToXrayPort = -1;
            int xrayToSingPort = -1;
            if (item->outbound->IsXray()) singToXrayPort = xrayPorts[xrayPortIdx++];
            if (item->type == "chain") {
                singToXrayPort = xrayPorts[xrayPortIdx++];
                xrayToSingPort = xrayPorts[xrayPortIdx++];
            }
            auto tag = buildOutboundChain(ctx, {
                .hopIDs = IDs,
                .prefix = hopTag(tags::testChainPrefix, suffix),
                .singToXrayPort = singToXrayPort,
                .xrayToSingPort = xrayToSingPort,
            });
            if (!ctx.error.isEmpty()) {
                res->error = ctx.error;
                return res;
            }
            res->outboundTags << tag;
            res->tag2entID.insert(tag, item->id);
            suffix++;
        }
        buildXrayConfig(ctx);
        if (!ctx.error.isEmpty()) {
            res->error = ctx.error;
            return res;
        }
        ctx.outbounds << QJsonObject{{"type", "direct"}, {"tag", tags::direct}};
        ctx.result->coreConfig["outbounds"] = ctx.outbounds;
        ctx.result->coreConfig["endpoints"] = ctx.endpoints;
        if (auto mismatch = bridgeIngressMismatch(ctx); !mismatch.isEmpty()) {
            res->error = mismatch;
            return res;
        }
        QJsonArray inboundArr;
        QJsonArray bridgeRules;
        for (qsizetype idx = 0; idx < ctx.xrayToSingBridges.size(); idx++) {
            const auto bridgeTag = bridgeTagFor(ctx.singIngressTags[idx]);
            inboundArr.append(socksBridgeInbound(bridgeTag, ctx.xrayToSingBridges[idx]));
            bridgeRules.append(QJsonObject{
                {"inbound", bridgeTag},
                {"action", "route"},
                {"outbound", ctx.singIngressTags[idx]},
            });
        }
        QJsonObject testRoute{
                {"auto_detect_interface", true},
                {"default_domain_resolver", QJsonObject{
                        {"server", tags::dnsDirect},
                        {"strategy", dataManager->settingsRepo->default_domain_strategy},
                   }}
        };
        if (!bridgeRules.isEmpty()) testRoute["rules"] = bridgeRules;
        ctx.result->coreConfig["route"] = testRoute;
        ctx.result->coreConfig["inbounds"] = inboundArr;
        res->coreConfig = ctx.result->coreConfig;
        res->xrayConfig = ctx.result->xrayConfig;
        res->isXrayNeeded = ctx.result->isXrayNeeded;

        return res;
    }
}
