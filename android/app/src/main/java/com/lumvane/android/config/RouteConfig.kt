package com.lumvane.android.config

import com.lumvane.android.config.common.putIfNotEmpty
import com.lumvane.android.config.common.strings
import com.lumvane.android.config.model.OutboundIds
import com.lumvane.android.config.model.RouteRule
import com.lumvane.android.config.model.RuleType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class RouteInput(
    val defaultOutboundId: Int = OutboundIds.PROXY,
    val rules: List<RouteRule> = emptyList(),
    val isRaw: Boolean = false,
    val rawRoute: String = "",
    val preventModifications: Boolean = false,
)

object RoutePresets {
    private class Preset(val name: String, val defaultOutbound: Int, val ruleOutbound: Int, val ruleSets: List<String>, val privateDirect: Boolean)

    private val presets = listOf(
        Preset("Bypass Russia", OutboundIds.PROXY, OutboundIds.DIRECT,
            listOf("geoip-ru", "geosite-ru", "geosite-category-ru", "geosite-ru-available-only-inside"), true),
        Preset("Proxy Russia Blocked", OutboundIds.DIRECT, OutboundIds.PROXY, listOf("geoip-ru-blocked", "geosite-ru-blocked"), false),
        Preset("Bypass China", OutboundIds.PROXY, OutboundIds.DIRECT, listOf("geoip-cn", "geosite-cn", "geosite-geolocation-cn"), true),
        Preset("Proxy China Blocked", OutboundIds.DIRECT, OutboundIds.PROXY, listOf("geosite-gfw"), false),
        Preset("Bypass Iran", OutboundIds.PROXY, OutboundIds.DIRECT, listOf("geoip-ir", "geosite-ir", "geosite-category-ir"), true),
    )

    val names: List<String> get() = presets.map { it.name }

    fun byName(name: String): RouteInput? = presets.firstOrNull { it.name == name }?.let(::build)

    fun all(): Map<String, RouteInput> = presets.associate { it.name to build(it) }

    private fun build(p: Preset): RouteInput {
        val rules = mutableListOf(RouteRule(name = "DNS", action = "hijack-dns", protocol = "dns"))
        if (p.privateDirect) {
            rules += RouteRule(name = "Private", action = "route", ipIsPrivate = true, outboundId = OutboundIds.DIRECT)
        }
        rules += RouteRule(name = p.name, action = "route", outboundId = p.ruleOutbound, ruleSet = p.ruleSets)
        return RouteInput(defaultOutboundId = p.defaultOutbound, rules = rules)
    }

    fun default(): RouteInput = RouteInput(rules = listOf(RouteRule(name = "Route DNS", action = "hijack-dns", protocol = "dns")))
}

object RuleSetCatalog {
    private val map: Map<String, String> by lazy {
        val stream = RuleSetCatalog::class.java.getResourceAsStream("/com/lumvane/android/config/srslist.tsv")
            ?: return@lazy emptyMap()
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.mapNotNull { l ->
                val i = l.indexOf('\t')
                if (i <= 0) null else l.substring(0, i) to l.substring(i + 1).trim()
            }.toMap()
        }
    }

    fun url(key: String): String? = map[key]

    val size: Int get() = map.size

    private val mirrors = listOf(
        null,
        "https://testingcf.jsdelivr.net/gh",
        "https://gcore.jsdelivr.net/gh",
        "https://quantil.jsdelivr.net/gh",
        "https://fastly.jsdelivr.net/gh",
        "https://cdn.jsdelivr.net/gh",
    )

    fun mirror(link: String, mirror: Int): String {
        if (mirror == 0) return link
        val prefix = "https://raw.githubusercontent.com/"
        if (!link.startsWith(prefix)) return link
        val base = mirrors.getOrNull(mirror) ?: mirrors[1]!!
        val parts = link.removePrefix(prefix).split('/').filter { it.isNotEmpty() }
        val sb = StringBuilder(base)
        parts.forEachIndexed { i, item -> sb.append(if (i == 2) "@$item" else "/$item") }
        return sb.toString()
    }

    private fun isSrsUrl(item: String): Boolean =
        (item.startsWith("http://") || item.startsWith("https://")) && item.substringBefore('?').substringAfterLast('/').contains(".srs")

    fun tag(item: String): String {
        if (!isSrsUrl(item)) return item
        val file = item.substringBefore('?').substringAfterLast('/')
        return file.replace(".srs", "-srs") + "-" + Integer.toUnsignedString(item.hashCode())
    }

    fun definition(item: String, mirror: Int, localDir: java.io.File? = null): JsonObject? {
        if (isSrsUrl(item)) {
            return buildJsonObject {
                put("type", "remote"); put("tag", tag(item)); put("format", "binary"); put("url", item)
            }
        }
        val url = url(item) ?: return null
        val local = localDir?.let { java.io.File(it, "$item.srs") }?.takeIf { it.isFile }
        if (local != null) {
            return buildJsonObject {
                put("type", "local"); put("tag", item); put("format", "binary"); put("path", local.absolutePath)
            }
        }
        return buildJsonObject {
            put("type", "remote"); put("tag", item); put("format", "binary"); put("url", mirror(url, mirror))
        }
    }

    const val ADBLOCK_TAG = "lumvane-adblocksingbox"
    const val ADBLOCK_URL = "https://raw.githubusercontent.com/217heidai/adblockfilters/main/rules/adblocksingbox.srs"

    fun adblockDefinition(mirror: Int): JsonObject = buildJsonObject {
        put("type", "remote"); put("tag", ADBLOCK_TAG); put("format", "binary"); put("url", mirror(ADBLOCK_URL, mirror))
    }
}

private fun valid(list: List<String>) = list.any { it.isNotBlank() }

fun ruleJson(rule: RouteRule, outboundTag: String?): JsonObject = buildJsonObject {
    var action = rule.action
    if (rule.ipVersion.isNotEmpty()) rule.ipVersion.toIntOrNull()?.let { put("ip_version", it) }
    putIfNotEmpty("network", rule.network)
    putIfNotEmpty("protocol", rule.protocol)
    fun arr(key: String, v: List<String>) { if (valid(v)) put(key, strings(v)) }
    fun nums(key: String, v: List<String>) {
        if (valid(v)) put(key, JsonArray(v.mapNotNull { it.trim().toIntOrNull() }.map { JsonPrimitive(it) }))
    }
    arr("inbound", rule.inbound)
    arr("domain", rule.domain)
    arr("domain_suffix", rule.domainSuffix)
    arr("domain_keyword", rule.domainKeyword)
    arr("domain_regex", rule.domainRegex)
    arr("source_ip_cidr", rule.sourceIpCidr)
    if (rule.sourceIpIsPrivate) put("source_ip_is_private", true)
    arr("ip_cidr", rule.ipCidr)
    if (rule.ipIsPrivate) put("ip_is_private", true)
    nums("source_port", rule.sourcePort)
    arr("source_port_range", rule.sourcePortRange)
    nums("port", rule.port)
    arr("port_range", rule.portRange)
    arr("package_name", rule.processName)
    arr("process_path", rule.processPath)
    arr("process_path_regex", rule.processPathRegex)
    arr("wifi_ssid", rule.wifiSsid)
    arr("wifi_bssid", rule.wifiBssid)
    if (valid(rule.ruleSet)) put("rule_set", strings(rule.ruleSet.map { RuleSetCatalog.tag(it) }))
    if (rule.invert) put("invert", true)
    if (action == "route") {
        if (rule.outboundId == OutboundIds.BLOCK) action = "reject"
        if (rule.outboundId == -4) action = "hijack-dns"
    }
    put("action", action)
    if (action == "reject") {
        putIfNotEmpty("reject_method", rule.rejectMethod)
        if (rule.noDrop) put("no_drop", true)
    }
    if (action == "route" || action == "route-options" || action == "bypass") {
        putIfNotEmpty("override_address", rule.overrideAddress)
        rule.overridePort.toIntOrNull()?.takeIf { it > 0 }?.let { put("override_port", it) }
        if (action == "route" || action == "bypass") put("outbound", outboundTag ?: "proxy")
    }
    if (action == "sniff" && rule.sniffOverrideDest) put("override_destination", true)
    if (action == "resolve") putIfNotEmpty("strategy", rule.strategy)
}

fun RouteRule.isEmptyStub(): Boolean {
    if (type == RuleType.CUSTOM) {
        val keys = ruleJson(this, null).keys
        return when (action) {
            "route-options", "hijack-dns" -> keys.size <= 1
            else -> false
        }
    }
    return when (type) {
        RuleType.SIMPLE_ADDRESS_PROXY, RuleType.SIMPLE_ADDRESS_BYPASS, RuleType.SIMPLE_ADDRESS_BLOCK, RuleType.SIMPLE_ADDRESS_WARP_BYPASS ->
            domain.isEmpty() && domainSuffix.isEmpty() && domainKeyword.isEmpty() && domainRegex.isEmpty() && ruleSet.isEmpty() && ipCidr.isEmpty()
        else -> processName.isEmpty() && processPath.isEmpty()
    }
}

