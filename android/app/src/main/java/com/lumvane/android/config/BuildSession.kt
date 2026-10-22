package com.lumvane.android.config

import com.lumvane.android.config.common.BuildEnv
import com.lumvane.android.config.common.TUN_BYPASSABLE_PRIVATE_RANGES
import com.lumvane.android.config.common.jsonObject as newJsonObject
import com.lumvane.android.config.common.parseObjectOrNull
import com.lumvane.android.config.common.prefixesOverlap
import com.lumvane.android.config.common.strings
import com.lumvane.android.config.model.*
import com.lumvane.android.config.outbounds.*
import com.lumvane.android.data.AppSettings
import com.lumvane.android.data.PerAppMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private object Tags {
    const val PROXY = "proxy"
    const val DIRECT = "direct"
    const val WARP_BYPASS = "warp-bypass"
    const val DNS_REMOTE = "dns-remote"
    const val DNS_DIRECT = "dns-direct"
    const val DNS_LOCAL = "dns-local"
    const val DNS_FAKE = "dns-fake"
    const val DNS_TAILSCALE = "dns-tailscale"
    const val DNS_IN = "dns-in"
    const val MIXED_IN = "mixed-in"
    const val TUN_IN = "tun-in"
    const val XRAY_FULL_IN = "lumvane-bridge"
    const val MAIN_PREFIX = "config"
    const val ROUTE_PREFIX = "route"
    const val POOL_PREFIX = "pool"
}

private const val LOOPBACK = "127.0.0.1"
private const val MAX_AUTO_MEMBERS = 50

private fun hopTag(prefix: String, index: Int) = "$prefix-$index"

private fun bridgeTagFor(singIngressTag: String) = "bridge-$singIngressTag"

private class Bridge(val port: Int, val auth: String, val host: String = LOOPBACK)

private class Selectors {
    val ruleSets = LinkedHashSet<String>()
    val domains = mutableListOf<String>()
    val suffixes = mutableListOf<String>()
    val keywords = mutableListOf<String>()
    val regexes = mutableListOf<String>()
    fun hasInline() = domains.isNotEmpty() || suffixes.isNotEmpty() || keywords.isNotEmpty() || regexes.isNotEmpty()
}

private class RouteGroup(val hops: List<OutboundBean>)

internal class BuildSession(
    private val resolver: ProfileResolver,
    private val options: ConfigOptions,
    private val settings: AppSettings,
    private val route: RouteInput,
    private val profileId: Int,
    private val bean: OutboundBean,
    private val landingProxyId: Int,
    private val frontProxyId: Int,
    private val auto: AutoRequest? = null,
) {
    private val env = BuildEnv(settings, options.dataDir)
    private val tunEnabled = settings.vpnMode

    private val outboundMap = mutableMapOf(
        OutboundIds.PROXY to Tags.PROXY,
        OutboundIds.DIRECT to Tags.DIRECT,
        OutboundIds.WARP_BYPASS to if (settings.enableWarp) Tags.WARP_BYPASS else Tags.PROXY,
    )
    private val routeGroups = mutableListOf<RouteGroup>()
    private val neededRuleSets = LinkedHashSet<String>()
    private val dnsDirect = Selectors()
    private val dnsProxy = Selectors()
    private var needDirectDnsRules = false
    private var needProxyDnsRules = false
    private val tunDirectIpSets = mutableListOf<String>()
    private val tunDirectIpCidrs = mutableListOf<String>()
    private val hijackedPrivateRanges = linkedSetOf<String>()
    private var proxyUsesXray = false
    private var rawRoute: JsonObject? = null

    private val outbounds = mutableListOf<JsonObject>()
    private val endpoints = mutableListOf<JsonObject>()
    private val xrayOutbounds = mutableListOf<JsonObject>()
    private val xrayIngressTags = mutableListOf<String>()
    private val singIngressTags = mutableListOf<String>()
    private val singToXrayBridges = mutableListOf<Bridge>()
    private val xrayToSingBridges = mutableListOf<Bridge>()
    private val xrayFullConfigs = mutableListOf<JsonObject>()
    private var mainXrayFull: JsonObject? = null
    private var tunIpv4Cidr = ""
    private var autoMembers: List<Pair<Int, OutboundBean>> = emptyList()
    private val warnings = mutableListOf<String>()

    private fun fail(msg: String): Nothing = throw ConfigBuildException(msg)

    private fun resolve(id: Int): OutboundBean? = resolver.bean(id)

    fun run(): GeneratedConfig {
        auto?.let { autoMembers = resolveAutoMembers(it) }
        calculatePrerequisites()
        val core = linkedMapOf<String, JsonElement>()
        core["log"] = newJsonObject { put("level", settings.logLevel) }
        buildNtp()?.let { core["ntp"] = it }
        core["dns"] = buildDns()
        core["certificate"] = newJsonObject { put("store", if (settings.useMozillaCerts) "mozilla" else "system") }
        val inbounds = buildInbounds().toMutableList()
        buildOutbounds(inbounds)
        core["inbounds"] = JsonArray(inbounds)
        core["endpoints"] = JsonArray(endpoints)
        core["outbounds"] = JsonArray(outbounds)
        core["route"] = buildRoute()
        buildExperimental()?.let { core["experimental"] = it }
        val xray = buildXray()
        val text = Json.encodeToString(JsonObject.serializer(), JsonObject(core))
        return GeneratedConfig(text, xray, tunIpv4Cidr, warnings.toList())
    }

    private fun usesXray(b: OutboundBean) = b.isXray() || b.isXrayFullConfig()

    private fun groupProxies(): List<OutboundBean> =
        listOfNotNull(landingProxyId.takeIf { it >= 0 }?.let(::resolve), frontProxyId.takeIf { it >= 0 }?.let(::resolve))

    private fun poolMembers(sel: AutoSelectorBean): List<Pair<Int, OutboundBean>> {
        if (auto != null && sel === bean) return autoMembers
        val out = mutableListOf<Pair<Int, OutboundBean>>()
        for (id in sel.pool) {
            if (id == profileId) continue
            val m = resolve(id) ?: continue
            if (m is ChainBean || m is AutoSelectorBean || m is TailscaleBean) continue
            if (m is CustomBean && m.subtype == CustomBean.FULL_CONFIG) continue
            if (m is CustomBean && m.parsedConfig() == null) continue
            out += id to m
            if (out.size >= sel.buildLimit.coerceAtLeast(1)) break
        }
        return out
    }

    private fun chainHops(chain: ChainBean): List<OutboundBean> {
        if (chain.list.isEmpty()) fail("Chain outbound is empty or corrupted")
        return chain.list.reversed().map { resolve(it) ?: fail("Null proxy in chain, you may want to check your configs") }
    }

    private fun domainsOf(b: OutboundBean): List<String> = when (b) {
        is ChainBean -> chainHops(b).flatMap(::serverDomains)
        is AutoSelectorBean -> poolMembers(b).flatMap { serverDomains(it.second) }
        else -> serverDomains(b)
    }

    private fun proxyPathUsesXray(b: OutboundBean): Boolean = when (b) {
        is ChainBean -> chainHops(b).any(::usesXray)
        is AutoSelectorBean -> poolMembers(b).any { usesXray(it.second) }
        else -> usesXray(b)
    }

    private fun addDirectDomains(domains: List<String>) {
        if (domains.isEmpty()) return
        dnsDirect.domains += domains
        needDirectDnsRules = true
    }

    private fun calculatePrerequisites() {
        if (settings.enableWarp &&
            (settings.warpPrivateKey.isEmpty() || settings.warpPublicKey.isEmpty() || settings.warpEp.isEmpty() || settings.warpIfcAddrs.isEmpty())
        ) {
            fail("Warp is enabled but its config has not been generated. Please generate the Warp config first in Routing Settings.")
        }
        if (route.isRaw) {
            rawRoute = parseObjectOrNull(route.rawRoute)?.takeIf { it.isNotEmpty() }
                ?: fail("Raw routing profile is not a valid JSON object")
        }

        if (proxyPathUsesXray(bean)) proxyUsesXray = true

        val neededIds = LinkedHashSet<Int>()
        if (route.isRaw) collectRawOutboundIds(rawRoute!!, neededIds)
        else route.rules.filter { it.action == "route" || it.action == "bypass" }.forEach { neededIds += it.outboundId }
        var suffix = 0
        for (id in neededIds) {
            if (id < 0) continue
            val needed = resolve(id)
                ?: fail("The routing profile is referencing outbounds that no longer exist, consider revising your settings")
            if ((needed is CustomBean && needed.subtype == CustomBean.FULL_CONFIG) || needed.isXrayFullConfig()) {
                fail("Outbounds used in routing profile cannot use an extra core or be a custom full config")
            }
            if (needed is AutoSelectorBean) fail("An auto selector cannot be used by a routing profile")
            if (needed is ChainBean) {
                val hops = chainHops(needed)
                for (h in hops) {
                    if (h is ChainBean || h.isXrayFullConfig() || (h is CustomBean && h.subtype == CustomBean.FULL_CONFIG)) {
                        fail("Chain hops in routing profile cannot use an extra core, a custom full config, or be of type chain")
                    }
                    if (usesXray(h)) proxyUsesXray = true
                    addDirectDomains(serverDomains(h))
                }
                outboundMap[id] = hopTag(Tags.ROUTE_PREFIX, suffix)
                routeGroups += RouteGroup(hops)
                suffix += hops.size
            } else {
                if (usesXray(needed)) proxyUsesXray = true
                addDirectDomains(serverDomains(needed))
                outboundMap[id] = hopTag(Tags.ROUTE_PREFIX, suffix++)
                routeGroups += RouteGroup(listOf(needed))
            }
        }

        if (!route.isRaw) {
            route.rules.forEach { r -> r.ruleSet.forEach { neededRuleSets += it } }
        }

        if (settings.enableDnsRouting && !route.isRaw) {
            for (r in route.rules) {
                if (r.action != "route") continue
                val target = when (r.outboundId) {
                    OutboundIds.DIRECT -> dnsDirect
                    OutboundIds.PROXY -> dnsProxy
                    else -> continue
                }
                r.ruleSet.filter { it.startsWith("geosite-") }.forEach { target.ruleSets += it }
                target.domains += r.domain
                target.suffixes += r.domainSuffix
                target.keywords += r.domainKeyword
                target.regexes += r.domainRegex
                if (target === dnsDirect) needDirectDnsRules = true else needProxyDnsRules = true
            }
        }

        addDirectDomains(domainsOf(bean))
        for (g in groupProxies()) {
            if (usesXray(g)) proxyUsesXray = true
            addDirectDomains(domainsOf(g))
        }

        if (!route.isRaw) {
            for (r in route.rules) {
                if (r.action == "route" && r.outboundId == OutboundIds.DIRECT) {
                    r.ruleSet.filter { it.startsWith("geoip-") }.forEach { tunDirectIpSets += it }
                    tunDirectIpCidrs += r.ipCidr
                }
                if (r.action == "route" && r.outboundId == OutboundIds.DIRECT) continue
                if (r.action != "route" && r.action != "reject") continue
                val cidrs = (if (r.ipIsPrivate) TUN_BYPASSABLE_PRIVATE_RANGES else emptyList()) + r.ipCidr
                for (cidr in cidrs) for (range in TUN_BYPASSABLE_PRIVATE_RANGES) {
                    if (prefixesOverlap(range, cidr)) hijackedPrivateRanges += range
                }
            }
        }
    }

    private fun collectRawOutboundIds(node: JsonElement, out: MutableSet<Int>) {
        when (node) {
            is JsonObject -> for ((k, v) in node) {
                if ((k == "outbound" || k == "final") && v is JsonPrimitive && !v.isString) {
                    v.content.toDoubleOrNull()?.let { out += it.toInt() }
                } else collectRawOutboundIds(v, out)
            }
            is JsonArray -> node.forEach { collectRawOutboundIds(it, out) }
            else -> {}
        }
    }

    private fun translateRawOutbounds(node: JsonElement): JsonElement = when (node) {
        is JsonObject -> JsonObject(node.mapValues { (k, v) ->
            if ((k == "outbound" || k == "final") && v is JsonPrimitive && !v.isString) {
                JsonPrimitive(v.content.toDoubleOrNull()?.toInt()?.let { outboundMap[it] } ?: Tags.PROXY)
            } else translateRawOutbounds(v)
        })
        is JsonArray -> JsonArray(node.map(::translateRawOutbounds))
        else -> node
    }

    private fun buildNtp(): JsonObject? {
        if (!settings.enableNtp) return null
        return newJsonObject {
            put("enabled", true)
            put("server", settings.ntpServerAddress)
            put("server_port", settings.ntpServerPort)
            put("interval", settings.ntpInterval)
            put("detour", if (settings.ntpOutbound == Tags.PROXY) Tags.PROXY else Tags.DIRECT)
        }
    }

    private fun localDns(): JsonObject {
        val under = settings.coreBoxUnderlyingDns
        val server = under.ifEmpty { options.systemDnsServers.firstOrNull() ?: options.fallbackLocalDns }
        return dnsServerObj(server, allowLocal = false)
    }

    private fun dnsServerObj(address: String, allowLocal: Boolean = true): JsonObject {
        if (allowLocal && address.startsWith("local")) return localDns()
        if (address.startsWith("dhcp://")) {
            val ifc = address.removePrefix("dhcp://").let { if (it == "auto") "" else it }
            return newJsonObject { put("type", "dhcp"); put("interface", ifc) }
        }
        var addr = address
        var type = "udp"
        var path = ""
        for ((prefix, t) in listOf("tcp://" to "tcp", "tls://" to "tls", "quic://" to "quic", "https://" to "https", "h3://" to "h3")) {
            if (address.startsWith(prefix)) {
                type = t
                addr = address.removePrefix(prefix)
                if (t == "https" || t == "h3") {
                    val slash = addr.indexOf('/')
                    if (slash != -1) {
                        path = addr.substring(slash)
                        addr = addr.substring(0, slash)
                    }
                }
                break
            }
        }
        var port = -1
        if (addr.startsWith("[")) {
            val close = addr.indexOf(']')
            if (close > 0) {
                val rest = addr.substring(close + 1)
                if (rest.startsWith(":")) port = rest.substring(1).toIntOrNull() ?: 0
                addr = addr.substring(1, close)
            }
        } else if (addr.count { it == ':' } == 1) {
            port = addr.substringAfter(':').toIntOrNull() ?: 0
            addr = addr.substringBefore(':')
        }
        return newJsonObject {
            put("type", type)
            put("server", addr)
            if (port != -1) put("server_port", port)
            if (path.isNotEmpty()) put("path", path)
        }
    }

    private fun upgradeUdpDnsToDoH(server: String): String {
        val known = setOf(
            "8.8.8.8", "8.8.4.4", "1.1.1.1", "1.0.0.1", "1.1.1.2", "1.0.0.2", "1.1.1.3", "1.0.0.3",
            "9.9.9.9", "149.112.112.112", "94.140.14.14", "94.140.15.15",
        )
        return if (server in known) "https://$server/dns-query" else "https://8.8.8.8/dns-query"
    }

    private fun appendDnsRoute(rules: MutableList<JsonObject>, conditions: Map<String, JsonElement>, strategy: String, server: String) {
        val blocked = when (strategy) {
            "ipv4_only" -> "AAAA"
            "ipv6_only" -> "A"
            else -> ""
        }
        if (blocked.isNotEmpty()) {
            rules += JsonObject(conditions + mapOf(
                "query_type" to JsonPrimitive(blocked), "action" to JsonPrimitive("predefined"), "rcode" to JsonPrimitive("NOERROR"),
            ))
        }
        rules += JsonObject(conditions + mapOf("action" to JsonPrimitive("route"), "server" to JsonPrimitive(server)))
    }

    private fun appendDnsRoutingRules(rules: MutableList<JsonObject>, s: Selectors, strategy: String, server: String) {
        if (s.ruleSets.isNotEmpty()) appendDnsRoute(rules, mapOf("rule_set" to strings(s.ruleSets)), strategy, server)
        if (s.hasInline()) {
            val cond = linkedMapOf<String, JsonElement>()
            if (s.domains.isNotEmpty()) cond["domain"] = strings(s.domains)
            if (s.suffixes.isNotEmpty()) cond["domain_suffix"] = strings(s.suffixes)
            if (s.keywords.isNotEmpty()) cond["domain_keyword"] = strings(s.keywords)
            if (s.regexes.isNotEmpty()) cond["domain_regex"] = strings(s.regexes)
            appendDnsRoute(rules, cond, strategy, server)
        }
    }

    private fun buildDns(): JsonObject {
        if (settings.useDnsObject) {
            parseObjectOrNull(settings.dnsObject)?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        val servers = mutableListOf<JsonObject>()
        val rules = mutableListOf<JsonObject>()
        val isTailscale = bean is TailscaleBean

        var remote = dnsServerObj(settings.remoteDns)
        val remoteType = (remote["type"] as? JsonPrimitive)?.content
        if (proxyUsesXray && (remoteType == "udp" || remoteType == "quic")) {
            remote = dnsServerObj(upgradeUdpDnsToDoH((remote["server"] as JsonPrimitive).content))
        }
        servers += JsonObject(remote + mapOf(
            "tag" to JsonPrimitive(Tags.DNS_REMOTE),
            "domain_resolver" to JsonPrimitive(Tags.DNS_LOCAL),
            "detour" to JsonPrimitive(Tags.PROXY),
        ))

        if (isTailscale) {
            servers += newJsonObject {
                put("type", "tailscale"); put("tag", Tags.DNS_TAILSCALE); put("endpoint", Tags.PROXY)
                put("accept_default_resolvers", (bean as TailscaleBean).globalDns)
            }
            rules += newJsonObject {
                put("domain", strings(listOf("controlplane.tailscale.com", "login.tailscale.com", "log.tailscale.io")))
                put("domain_suffix", strings(listOf("tailscale.com", "tailscale.net", "tailscale.io")))
                put("action", "route"); put("server", Tags.DNS_DIRECT)
            }
            rules += newJsonObject {
                put("domain_suffix", strings(listOf("ts.net", "tailscale.net")))
                put("action", "route"); put("server", Tags.DNS_TAILSCALE)
            }
        }

        val direct = dnsServerObj(settings.directDns)
        servers += JsonObject(direct + mapOf("tag" to JsonPrimitive(Tags.DNS_DIRECT), "domain_resolver" to JsonPrimitive(Tags.DNS_LOCAL)))

        rules += newJsonObject {
            put("domain", "localhost"); put("action", "predefined"); put("query_type", "A")
            put("rcode", "NOERROR"); put("answer", "localhost. IN A 127.0.0.1")
        }
        rules += newJsonObject {
            put("domain", "localhost"); put("action", "predefined"); put("query_type", "AAAA"); put("rcode", "NXDOMAIN")
        }
        if (tunEnabled && !settings.vpnIpv6) {
            rules += newJsonObject {
                put("inbound", strings(listOf(Tags.TUN_IN))); put("query_type", "AAAA")
                put("action", "predefined"); put("rcode", "NOERROR")
            }
        }
        if (proxyUsesXray) {
            appendDnsRoute(rules, mapOf("inbound" to strings(listOf(Tags.DNS_IN))), settings.directDnsStrategy, Tags.DNS_DIRECT)
        }
        if (settings.fakeDns) {
            servers += newJsonObject {
                put("tag", Tags.DNS_FAKE); put("type", "fakeip")
                put("inet4_range", "198.18.0.0/15"); put("inet6_range", "fc00::/18")
            }
            rules += newJsonObject {
                put("query_type", strings(listOf("A", "AAAA"))); put("action", "route"); put("server", Tags.DNS_FAKE)
            }
        }
        if (needDirectDnsRules) appendDnsRoutingRules(rules, dnsDirect, settings.directDnsStrategy, Tags.DNS_DIRECT)

        val directFinal = settings.dnsFinalOut == Tags.DIRECT
        if (needProxyDnsRules && directFinal) appendDnsRoutingRules(rules, dnsProxy, settings.remoteDnsStrategy, Tags.DNS_REMOTE)

        val finalStrategy = if (directFinal) settings.directDnsStrategy else settings.remoteDnsStrategy
        appendDnsRoute(rules, emptyMap(), finalStrategy, if (directFinal) Tags.DNS_DIRECT else Tags.DNS_REMOTE)

        servers += JsonObject(localDns() + mapOf("tag" to JsonPrimitive(Tags.DNS_LOCAL)))

        return newJsonObject {
            put("servers", JsonArray(servers))
            put("rules", JsonArray(rules))
            put("cache_capacity", settings.dnsCacheCapacity)
            if (settings.dnsDisableCache) put("disable_cache", true)
            if (settings.dnsDisableExpire) put("disable_expire", true)
            if (settings.dnsOptimisticCache && !settings.dnsDisableCache && !settings.dnsDisableExpire) put("optimistic", true)
            if (settings.dnsReverseMapping) put("reverse_mapping", true)
            if (finalStrategy.startsWith("prefer_")) put("strategy", finalStrategy)
        }
    }

    private fun buildInbounds(): List<JsonObject> {
        val inbounds = mutableListOf<JsonObject>()

        if (proxyUsesXray) {
            inbounds += newJsonObject {
                put("tag", Tags.DNS_IN); put("type", "direct"); put("listen", LOOPBACK); put("listen_port", settings.coreDnsInPort)
            }
        }

        if (settings.mixedInboundEnabled) {
            inbounds += newJsonObject {
                put("tag", Tags.MIXED_IN)
                put("type", "mixed")
                put("listen", if (settings.allowLan) "0.0.0.0" else settings.inboundAddress)
                put("listen_port", if (settings.randomInboundPort) options.allocatePort() else settings.inboundSocksPort)
                if (settings.inboundAuth) {
                    put("users", JsonArray(listOf(newJsonObject {
                        put("username", settings.inboundUser); put("password", settings.inboundPass)
                    })))
                }
            }
        }

        if (tunEnabled) {
            tunIpv4Cidr = settings.vpnTunIpv4Cidr
            val excludedRanges = mutableListOf<String>()
            val routeExclude = mutableListOf<String>()
            if (!settings.disablePrivateRangeBypass) {
                routeExclude += listOf("127.0.0.0/8", "255.255.255.255/32")
                TUN_BYPASSABLE_PRIVATE_RANGES.filterTo(excludedRanges) { it !in hijackedPrivateRanges }
            }
            val excludeSets = mutableListOf<String>()
            if (settings.enableTunRouting) {
                excludedRanges += tunDirectIpCidrs
                excludeSets += tunDirectIpSets.map { RuleSetCatalog.tag(it) }
                tunDirectIpSets.forEach { neededRuleSets += it }
            }
            routeExclude += excludedRanges
            inbounds += newJsonObject {
                put("tag", Tags.TUN_IN)
                put("type", "tun")
                put("auto_route", true)
                put("mtu", settings.vpnMtu)
                put("stack", settings.vpnImplementation)
                put("strict_route", settings.vpnStrictRoute)
                put("address", strings(listOf(settings.vpnTunIpv4Cidr) + if (settings.vpnIpv6) listOf(settings.vpnTunIpv6Cidr) else emptyList()))
                put("route_exclude_address", strings(routeExclude))
                if (excludeSets.isNotEmpty()) put("route_exclude_address_set", strings(excludeSets.distinct()))
                if (settings.perAppPackages.isNotEmpty()) {
                    when (settings.perAppMode) {
                        PerAppMode.ALLOW_LISTED -> put("include_package", strings(settings.perAppPackages))
                        PerAppMode.DENY_LISTED -> put("exclude_package", strings(settings.perAppPackages))
                        PerAppMode.OFF -> {}
                    }
                }
            }
        }

        parseObjectOrNull(settings.customInbound)?.get("inbounds")?.let { custom ->
            (custom as? JsonArray)?.forEach { (it as? JsonObject)?.let(inbounds::add) }
        }
        return inbounds
    }

    private fun warpBean(): WireGuardBean {
        val ep = settings.warpEp
        val host = if (ep.contains(":")) ep.substringBeforeLast(":") else ep
        val port = if (ep.contains(":")) ep.substringAfterLast(":").toIntOrNull() ?: 2408 else 2408
        return WireGuardBean(
            name = "warp",
            privateKey = settings.warpPrivateKey,
            address = settings.warpIfcAddrs,
            mtu = 1280,
            peer = WireGuardPeer(
                address = host, port = port, publicKey = settings.warpPublicKey,
                reserved = settings.warpReserved.mapNotNull { it.trim().toIntOrNull() },
                persistentKeepalive = "10",
            ),
        )
    }

    private fun withTag(obj: JsonObject, tag: String, detour: String?): JsonObject {
        val out = LinkedHashMap<String, JsonElement>()
        obj["type"]?.let { out["type"] = it }
        out["tag"] = JsonPrimitive(tag)
        for ((k, v) in obj) if (k != "type" && k != "tag") out[k] = v
        if (detour != null) out["detour"] = JsonPrimitive(detour)
        return JsonObject(out)
    }

    private fun xraySocksInbound(tag: String, b: Bridge): JsonObject = newJsonObject {
        put("tag", tag); put("listen", b.host); put("port", b.port); put("protocol", "socks")
        put("settings", newJsonObject {
            put("auth", "password"); put("udp", true)
            put("accounts", JsonArray(listOf(newJsonObject { put("user", b.auth); put("pass", b.auth) })))
        })
    }

    private fun socksBridgeInbound(tag: String, b: Bridge): JsonObject = newJsonObject {
        put("type", "socks"); put("tag", tag); put("listen", b.host); put("listen_port", b.port)
        put("users", JsonArray(listOf(newJsonObject { put("username", b.auth); put("password", b.auth) })))
    }

    private fun port(): Int = options.allocatePort().also { if (it <= 0) fail("Could not reserve a local port for the core bridge") }

    private class ChainResult(val ingressTag: String, val xrayFull: JsonObject?)

    private fun buildOutboundChain(
        hops: List<OutboundBean>,
        prefix: String,
        includeProxy: Boolean = false,
        link: Boolean = true,
        startSuffix: Int = 0,
        warpWrap: Boolean = false,
        soleXrayInbound: Boolean = false,
    ): ChainResult {
        val ingressTag = if (includeProxy) Tags.PROXY else hopTag(prefix, startSuffix)
        chainScanError(hops)?.let { fail(it) }

        var singToXray = false
        var xrayToSing = false
        var inXray = false
        for (h in hops) {
            val x = h.isXray()
            if (!inXray && x) singToXray = true
            if (inXray && !x) xrayToSing = true
            inXray = x
        }

        var xrayFull: JsonObject? = null
        var effective = hops
        val last = hops.lastOrNull()
        if (last != null && last is CustomBean && last.isXrayFullConfig) {
            val user = last.parsedConfig() ?: fail("Custom Xray full config is not valid JSON")
            val bridge = Bridge(port(), options.randomSecret())
            val inbound = JsonObject(xraySocksInbound(Tags.XRAY_FULL_IN, bridge) + mapOf(
                "sniffing" to newJsonObject {
                    put("enabled", true)
                    put("destOverride", strings(listOf("http", "tls", "quic")))
                    put("routeOnly", false)
                },
            ))
            val userInbounds = if (soleXrayInbound) emptyList() else (user["inbounds"] as? JsonArray)?.toList().orEmpty()
            xrayFull = JsonObject(user + mapOf("inbounds" to JsonArray(listOf<JsonElement>(inbound) + userInbounds)))
            effective = hops.dropLast(1) + SocksBean(
                server = bridge.host, serverPort = bridge.port, username = bridge.auth, password = bridge.auth,
            )
        }

        val initialSing = mutableListOf<OutboundBean>()
        val xrayHops = mutableListOf<OutboundBean>()
        val tailingSing = mutableListOf<OutboundBean>()
        for (h in effective) {
            if (h.isXray()) xrayHops += h
            else if (xrayHops.isEmpty()) initialSing += h else tailingSing += h
        }

        if (singToXray) {
            val b = Bridge(port(), options.randomSecret())
            singToXrayBridges += b
            initialSing += SocksBean(server = b.host, serverPort = b.port, username = b.auth, password = b.auth)
        }
        var xrayToSingBridge: Bridge? = null
        if (xrayToSing) {
            xrayToSingBridge = Bridge(port(), options.randomSecret())
            xrayToSingBridges += xrayToSingBridge
        }

        if (initialSing.isNotEmpty()) {
            buildSingboxChain(initialSing, prefix, includeProxy, link, startSuffix, markIngress = false, warpWrap = warpWrap)
        }
        if (xrayHops.isNotEmpty()) {
            buildXrayChain(xrayHops, prefix, includeProxy, link, startSuffix, xrayToSingBridge)
        }
        if (tailingSing.isNotEmpty()) {
            buildSingboxChain(tailingSing, prefix, false, link, startSuffix + initialSing.size, markIngress = true, warpWrap = false)
        }
        return ChainResult(ingressTag, xrayFull)
    }

    private fun buildSingboxChain(
        ents: List<OutboundBean>, prefix: String, includeProxy: Boolean, link: Boolean, startSuffix: Int,
        markIngress: Boolean, warpWrap: Boolean,
    ) {
        for ((idx, ent) in ents.withIndex()) {
            var tag = hopTag(prefix, startSuffix + idx)
            val nextTag = if (idx < ents.size - 1) hopTag(prefix, startSuffix + idx + 1) else null
            if (includeProxy && idx == 0) tag = Tags.PROXY
            if (warpWrap && idx == 1) tag = Tags.WARP_BYPASS
            if (markIngress && idx == 0) singIngressTags += tag
            val json = buildSingBoxOutbound(ent, env) ?: fail("Cannot build ${ent.typeName} as a sing-box outbound")
            var detour: String? = if (nextTag != null && link) nextTag else null
            if (warpWrap && idx == 0) detour = Tags.WARP_BYPASS
            val tagged = withTag(json, tag, detour)
            if (ent.isEndpoint()) endpoints += tagged else outbounds += tagged
        }
    }

    private fun buildXrayChain(
        ents: List<OutboundBean>, prefix: String, includeProxy: Boolean, link: Boolean, startSuffix: Int, bridge: Bridge?,
    ) {
        for ((idx, ent) in ents.withIndex()) {
            var tag = hopTag(prefix, startSuffix + idx)
            val nextTag = if (idx < ents.size - 1 || bridge != null) hopTag(prefix, startSuffix + idx + 1) else null
            if (includeProxy && idx == 0) tag = Tags.PROXY
            if (idx == 0) xrayIngressTags += tag
            val json = buildXrayOutbound(ent, env) ?: fail("Cannot build ${ent.typeName} as an Xray outbound")
            val out = LinkedHashMap<String, JsonElement>(json)
            out["tag"] = JsonPrimitive(tag)
            if (nextTag != null && (link || bridge != null)) {
                out["proxySettings"] = newJsonObject { put("tag", nextTag); put("transportLayer", true) }
            }
            xrayOutbounds += JsonObject(out)
        }
        if (bridge != null) {
            xrayOutbounds += newJsonObject {
                put("tag", hopTag(prefix, startSuffix + ents.size))
                put("protocol", "socks")
                put("settings", newJsonObject {
                    put("address", bridge.host); put("port", bridge.port); put("user", bridge.auth); put("pass", bridge.auth)
                })
            }
        }
    }

    private fun buildOutbounds(inbounds: MutableList<JsonObject>) {
        val warpWrap = settings.enableWarp
        if (auto != null) {
            buildAutoGroup(warpWrap)
            if (warpWrap) endpoints += withTag(buildSingBoxOutbound(warpBean(), env)!!, Tags.PROXY, Tags.WARP_BYPASS)
        } else if (bean is AutoSelectorBean) {
            buildAutoSelectorGroup(bean, warpWrap)
            if (warpWrap) {
                val json = buildSingBoxOutbound(warpBean(), env)!!
                endpoints += withTag(json, Tags.PROXY, Tags.WARP_BYPASS)
            }
        } else {
            val hops = mutableListOf<OutboundBean>()
            if (warpWrap) hops += warpBean()
            landingProxyId.takeIf { it >= 0 }?.let { hops += resolve(it) ?: fail("Landing proxy of the group no longer exists") }
            if (bean is ChainBean) hops += chainHops(bean) else hops += bean
            frontProxyId.takeIf { it >= 0 }?.let { hops += resolve(it) ?: fail("Front proxy of the group no longer exists") }
            val res = buildOutboundChain(hops, Tags.MAIN_PREFIX, includeProxy = true, warpWrap = warpWrap)
            mainXrayFull = res.xrayFull
        }

        var routeSuffix = 0
        for (g in routeGroups) {
            buildOutboundChain(g.hops, Tags.ROUTE_PREFIX, link = g.hops.size > 1, startSuffix = routeSuffix)
            routeSuffix += g.hops.size
        }

        if (xrayToSingBridges.size != singIngressTags.size) {
            fail("xray to sing-box bridges count does not match ingress tags count")
        }
        xrayToSingBridges.forEachIndexed { i, b -> inbounds += socksBridgeInbound(bridgeTagFor(singIngressTags[i]), b) }

        outbounds += newJsonObject { put("type", "direct"); put("tag", Tags.DIRECT) }
    }

    private fun buildAutoSelectorGroup(sel: AutoSelectorBean, warpWrap: Boolean) {
        val groupTag = if (warpWrap) Tags.WARP_BYPASS else Tags.PROXY
        val tags = mutableListOf<String>()
        var pinnedTag: String? = null
        var idx = 0
        for ((id, member) in poolMembers(sel)) {
            val hops = mutableListOf<OutboundBean>()
            landingProxyId.takeIf { it >= 0 }?.let { hops += resolve(it) ?: fail("Landing proxy of the group no longer exists") }
            hops += member
            frontProxyId.takeIf { it >= 0 }?.let { hops += resolve(it) ?: fail("Front proxy of the group no longer exists") }
            if (chainScanError(hops) != null) continue
            val res = buildOutboundChain(hops, hopTag(Tags.POOL_PREFIX, idx), soleXrayInbound = true)
            res.xrayFull?.let { xrayFullConfigs += it }
            tags += res.ingressTag
            if (id == sel.pinnedId) pinnedTag = res.ingressTag
            idx++
        }
        if (tags.isEmpty()) fail("Auto selector produced no usable members")
        outbounds += sel.toSingBox(env, groupTag, tags, pinnedTag)
    }

    private fun label(id: Int, b: OutboundBean?) = "#$id" + (b?.name?.takeIf { it.isNotEmpty() }?.let { " \"$it\"" } ?: "")

    private fun candidateHops(m: OutboundBean): List<OutboundBean> = if (m is ChainBean) chainHops(m) else listOf(m)

    private fun candidateError(m: OutboundBean): String? {
        if (m is AutoSelectorBean) return "auto selector cannot be a candidate"
        if (m is TailscaleBean) return "tailscale cannot be a candidate"
        if (m is CustomBean && m.subtype == CustomBean.FULL_CONFIG) return "custom full config cannot be a candidate"
        return try {
            val hops = candidateHops(m)
            chainScanError(hops)
                ?: hops.firstNotNullOfOrNull { h ->
                    when {
                        h is TailscaleBean -> "tailscale cannot be a hop"
                        h.isXrayFullConfig() -> if ((h as CustomBean).parsedConfig() == null) "invalid Xray full config" else null
                        h.isXray() -> if (buildXrayOutbound(h, env) == null) "cannot build ${h.typeName} for Xray" else null
                        else -> if (buildSingBoxOutbound(h, env) == null) "cannot build ${h.typeName}" else null
                    }
                }
        } catch (e: Exception) {
            e.message ?: e.javaClass.simpleName
        }
    }

    private fun resolveAutoMembers(req: AutoRequest): List<Pair<Int, OutboundBean>> {
        val ids = LinkedHashSet<Int>()
        if (req.preferredId >= 0) ids += req.preferredId
        ids += req.candidateIds
        val out = mutableListOf<Pair<Int, OutboundBean>>()
        for (id in ids) {
            val m = resolve(id)
            if (m == null) { warnings += "Auto: profile #$id not found, skipped"; continue }
            val err = candidateError(m)
            if (err != null) { warnings += "Auto: profile ${label(id, m)} skipped: $err"; continue }
            if (out.size >= MAX_AUTO_MEMBERS) { warnings += "Auto: more than $MAX_AUTO_MEMBERS candidates, ${label(id, m)} and the rest skipped"; break }
            out += id to m
        }
        if (out.size < 2) fail("Auto mode needs at least 2 usable profiles, found ${out.size}")
        return out
    }

    private fun buildAutoGroup(warpWrap: Boolean) {
        val groupTag = if (warpWrap) Tags.WARP_BYPASS else Tags.PROXY
        val tags = mutableListOf<String>()
        val builtIds = mutableListOf<Int>()
        for ((id, member) in autoMembers) {
            val hops = candidateHops(member)
            val mark = Marks()
            try {
                val res = buildOutboundChain(hops, AutoTags.prefix(id), soleXrayInbound = true)
                res.xrayFull?.let { xrayFullConfigs += it }
                tags += res.ingressTag
                builtIds += id
            } catch (e: Exception) {
                mark.rollback()
                warnings += "Auto: profile ${label(id, member)} skipped: ${e.message ?: e.javaClass.simpleName}"
            }
        }
        if (tags.size < 2) fail("Auto mode needs at least 2 usable profiles, found ${tags.size}")
        val preferFirst = auto!!.preferredId >= 0 && builtIds.firstOrNull() == auto.preferredId
        outbounds += buildUrlTestGroup(settings, groupTag, tags, preferFirst)
    }

    private inner class Marks {
        private val o = outbounds.size; private val e = endpoints.size; private val x = xrayOutbounds.size
        private val xi = xrayIngressTags.size; private val si = singIngressTags.size
        private val sb = singToXrayBridges.size; private val xb = xrayToSingBridges.size; private val xf = xrayFullConfigs.size
        fun rollback() {
            fun <T> MutableList<T>.cut(n: Int) { while (size > n) removeAt(size - 1) }
            outbounds.cut(o); endpoints.cut(e); xrayOutbounds.cut(x); xrayIngressTags.cut(xi)
            singIngressTags.cut(si); singToXrayBridges.cut(sb); xrayToSingBridges.cut(xb); xrayFullConfigs.cut(xf)
        }
    }

    private fun buildRoute(): JsonObject {
        val raw = rawRoute?.let { translateRawOutbounds(it) as JsonObject }
        if (raw != null && route.preventModifications) return raw

        val sniff = if (raw == null) newJsonObject { put("action", "sniff") } else null
        val resolve = if (raw == null && settings.resolveDomainStrategy.isNotEmpty()) newJsonObject {
            put("inbound", strings(listOf(Tags.MIXED_IN, Tags.TUN_IN))); put("action", "resolve")
            put("strategy", settings.resolveDomainStrategy)
        } else null
        val dnsHijack = if (raw == null) newJsonObject { put("protocol", "dns"); put("action", "hijack-dns") } else null
        val dnsInReject = if (proxyUsesXray) newJsonObject { put("inbound", Tags.DNS_IN); put("action", "reject") } else null

        val profileRules: List<JsonElement> = if (raw != null) {
            (raw["rules"] as? JsonArray)?.toList().orEmpty()
        } else structuredRules()

        val ruleSets = mutableListOf<JsonElement>()
        val seen = HashSet<String>()
        val ruleSetDir = options.dataDir.takeIf { it.isNotEmpty() }?.let { java.io.File(it, "ruleset") }
        for (item in neededRuleSets) {
            if (!seen.add(item)) continue
            RuleSetCatalog.definition(item, settings.rulesetMirror, ruleSetDir)?.let { ruleSets += it }
        }
        if (settings.adblockEnable) ruleSets += RuleSetCatalog.adblockDefinition(settings.rulesetMirror)
        if (raw != null) (raw["rule_set"] as? JsonArray)?.let { ruleSets.addAll(it) }

        val rules = mutableListOf<JsonElement>()
        xrayToSingBridges.forEachIndexed { i, _ ->
            rules += newJsonObject {
                put("inbound", bridgeTagFor(singIngressTags[i])); put("action", "route"); put("outbound", singIngressTags[i])
            }
        }
        listOfNotNull(sniff, resolve, dnsHijack, dnsInReject).forEach { rules += it }
        rules += profileRules
        if (raw == null && route.defaultOutboundId == OutboundIds.BLOCK) rules += newJsonObject { put("action", "reject") }

        val out = LinkedHashMap<String, JsonElement>(raw ?: JsonObject(emptyMap()))
        out["rules"] = JsonArray(rules)
        out["rule_set"] = JsonArray(ruleSets)
        if (raw != null) {
            if ("final" !in out) out["final"] = JsonPrimitive(Tags.PROXY)
        } else {
            out["final"] = JsonPrimitive(
                when (route.defaultOutboundId) {
                    OutboundIds.BLOCK -> Tags.DIRECT
                    OutboundIds.WARP_BYPASS -> if (settings.enableWarp) Tags.WARP_BYPASS else Tags.PROXY
                    OutboundIds.DIRECT -> Tags.DIRECT
                    else -> Tags.PROXY
                },
            )
        }
        if ("default_domain_resolver" !in out) {
            out["default_domain_resolver"] = newJsonObject {
                put("server", Tags.DNS_DIRECT)
                if (settings.defaultDomainStrategy.isNotEmpty()) put("strategy", settings.defaultDomainStrategy)
            }
        }
        if ("auto_detect_interface" !in out) out["auto_detect_interface"] = JsonPrimitive(true)
        return JsonObject(out)
    }

    private fun structuredRules(): List<JsonElement> {
        val res = mutableListOf<JsonElement>()
        var addedAdblock = false
        fun adblockRule() = newJsonObject { put("action", "reject"); put("rule_set", strings(listOf(RuleSetCatalog.ADBLOCK_TAG))) }
        for (r in route.rules) {
            if (r.isEmptyStub()) continue
            val json = ruleJson(r, outboundMap[r.outboundId])
            if (!addedAdblock && settings.adblockEnable && (json["action"] as? JsonPrimitive)?.content == "route") {
                res += adblockRule()
                addedAdblock = true
            }
            res += json
        }
        if (!addedAdblock && settings.adblockEnable) res += adblockRule()
        return res
    }

    private fun buildExperimental(): JsonObject? {
        val clashPort = settings.coreBoxClashApi
        var clash: JsonObject? = null
        if (clashPort > 0) {
            clash = newJsonObject {
                put("external_controller", "${settings.coreBoxClashListenAddr}:$clashPort")
                put("secret", settings.coreBoxClashApiSecret)
            }
        } else if (settings.enableStats) {
            clash = newJsonObject { put("default_mode", "") }
        }
        return newJsonObject {
            clash?.let { put("clash_api", it) }
            put("cache_file", newJsonObject { put("enabled", true); put("store_fakeip", true); put("store_dns", true) })
        }
    }

    private fun xrayStrategy(): String = when (settings.directDnsStrategy) {
        "prefer_ipv4" -> "UseIPv4v6"
        "prefer_ipv6" -> "UseIPv6v4"
        "ipv4_only" -> "ForceIPv4"
        "ipv6_only" -> "ForceIPv6"
        else -> "UseIP"
    }

    private fun buildXray(): XrayStartParams? {
        var merged: JsonObject? = null
        if (xrayOutbounds.isNotEmpty()) {
            if (xrayIngressTags.size != singToXrayBridges.size) fail("xray ingress tags size does not match bridge count!")
            val inbounds = mutableListOf<JsonElement>()
            val rules = mutableListOf<JsonElement>()
            xrayIngressTags.forEachIndexed { i, outTag ->
                val inTag = "$outTag-inbound"
                inbounds += xraySocksInbound(inTag, singToXrayBridges[i])
                rules += newJsonObject {
                    put("type", "field"); put("inboundTag", strings(listOf(inTag))); put("outboundTag", outTag)
                }
            }
            merged = newJsonObject {
                put("log", newJsonObject {
                    put("loglevel", settings.xrayLogLevel)
                    put("access", if (settings.xrayLogLevel == "info") "" else "none")
                })
                put("inbounds", JsonArray(inbounds))
                put("outbounds", JsonArray(xrayOutbounds))
                put("routing", newJsonObject { put("domainStrategy", "AsIs"); put("rules", JsonArray(rules)) })
            }
        }
        val full = mutableListOf<JsonObject>()
        val single = mainXrayFull
        if (single != null) {
            if (merged == null) merged = single else full += single
        }
        full += xrayFullConfigs
        if (merged == null && full.isEmpty()) return null
        val isSelector = bean is AutoSelectorBean
        return XrayStartParams(
            xrayConfig = merged?.let { Json.encodeToString(JsonObject.serializer(), it) }.orEmpty(),
            fullConfigs = full.map { Json.encodeToString(JsonObject.serializer(), it) },
            outboundDnsAddress = "$LOOPBACK:${settings.coreDnsInPort}",
            outboundDnsStrategy = xrayStrategy(),
            lazyStart = isSelector,
            idleSeconds = if (isSelector) maxOf(120, (bean as AutoSelectorBean).intervalSec * 2) else 0,
            fullIdleSeconds = 0,
        )
    }
}
