package com.lumvane.android.importer

import com.lumvane.android.config.model.OutboundIds
import com.lumvane.android.config.model.RouteRule
import com.lumvane.android.config.model.RuleType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.util.Base64

data class SharedRoute(
    val name: String,
    val defaultOutboundId: Int,
    val rules: List<RouteRule>,
    val isRaw: Boolean = false,
    val rawRoute: String = "",
    val preventModifications: Boolean = false,
    val missingOutbounds: List<String> = emptyList(),
)

data class RemoteRoute(val name: String, val url: String)

object RouteShare {
    const val KIND = "lumvane-route-profile"
    private const val ROUTE_PREFIX = "lumvane://route/"
    private const val REMOTE_PREFIX = "lumvane://remoteroute/"
    private val json = Json { ignoreUnknownKeys = true }

    fun isRouteLink(text: String) = text.trim().startsWith(ROUTE_PREFIX, ignoreCase = true)
    fun isRemoteRouteLink(text: String) = text.trim().startsWith(REMOTE_PREFIX, ignoreCase = true)

    private val typeTokens = mapOf(
        RuleType.SIMPLE_ADDRESS_PROXY to "simple_address_proxy",
        RuleType.SIMPLE_ADDRESS_BYPASS to "simple_address_bypass",
        RuleType.SIMPLE_ADDRESS_BLOCK to "simple_address_block",
        RuleType.SIMPLE_PROCESS_NAME_PROXY to "simple_process_name_proxy",
        RuleType.SIMPLE_PROCESS_NAME_BYPASS to "simple_process_name_bypass",
        RuleType.SIMPLE_PROCESS_NAME_BLOCK to "simple_process_name_block",
        RuleType.SIMPLE_PROCESS_PATH_PROXY to "simple_process_path_proxy",
        RuleType.SIMPLE_PROCESS_PATH_BYPASS to "simple_process_path_bypass",
        RuleType.SIMPLE_PROCESS_PATH_BLOCK to "simple_process_path_block",
        RuleType.SIMPLE_ADDRESS_WARP_BYPASS to "simple_address_warp_bypass",
        RuleType.SIMPLE_PROCESS_NAME_WARP_BYPASS to "simple_process_name_warp_bypass",
        RuleType.SIMPLE_PROCESS_PATH_WARP_BYPASS to "simple_process_path_warp_bypass",
    )
    private val tokenTypes = typeTokens.entries.associate { (k, v) -> v to k }

    private fun specialName(id: Int) = when (id) {
        OutboundIds.PROXY -> "proxy"
        OutboundIds.DIRECT -> "direct"
        OutboundIds.BLOCK -> "block"
        OutboundIds.WARP_BYPASS -> "warp-bypass"
        else -> null
    }

    private fun specialId(name: String) = when (name) {
        "proxy" -> OutboundIds.PROXY
        "direct" -> OutboundIds.DIRECT
        "block" -> OutboundIds.BLOCK
        "warp-bypass" -> OutboundIds.WARP_BYPASS
        else -> null
    }

    private fun b64(s: String): String? {
        val t = s.trim()
        for (decoder in listOf(Base64.getUrlDecoder(), Base64.getDecoder())) {
            runCatching { return String(decoder.decode(t.trimEnd('=')), Charsets.UTF_8) }
        }
        return null
    }

    private fun linkData(text: String, prefix: String): String? =
        text.trim().substring(prefix.length).substringBefore('#').substringBefore('?').takeIf { it.isNotEmpty() }

    fun decode(text: String, outboundByName: (String) -> Int?): SharedRoute? {
        val data = if (isRouteLink(text)) linkData(text, ROUTE_PREFIX) ?: return null else text.trim()
        val element = parseElement(data) ?: b64(data)?.let(::parseElement) ?: return null
        if (element is JsonArray) {
            val missing = LinkedHashSet<String>()
            var n = 1
            val rules = element.mapNotNull { it as? JsonObject }.map { o ->
                parseRule(o, outboundByName, missing).let { r -> if (r.name.isEmpty()) r.copy(name = "imported rule #${n++}") else r }
            }
            if (rules.isEmpty()) return null
            return SharedRoute("", OutboundIds.PROXY, rules, missingOutbounds = missing.toList())
        }
        val root = element as? JsonObject ?: return null
        if ((root["kind"] as? JsonPrimitive)?.contentOrNull != KIND) return null
        val missing = LinkedHashSet<String>()
        val name = root.str("name")
        if (root.bool("raw")) {
            val names = root["outbound_names"] as? JsonObject ?: JsonObject(emptyMap())
            val route = remapRaw(root["route"] as? JsonObject ?: JsonObject(emptyMap()), names, outboundByName, missing)
            return SharedRoute(name, OutboundIds.PROXY, emptyList(), isRaw = true, rawRoute = route.toString(),
                preventModifications = root.bool("prevent_modifications"), missingOutbounds = missing.toList())
        }
        var unnamed = 1
        val rules = (root["rules"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map { o ->
            parseRule(o, outboundByName, missing).let { r -> if (r.name.isEmpty()) r.copy(name = "rule_${unnamed++}") else r }
        }
        val default = specialId(root.str("default_outbound")) ?: OutboundIds.PROXY
        return SharedRoute(name, default, rules, missingOutbounds = missing.toList())
    }

    fun decodeRemote(text: String): List<RemoteRoute> {
        if (!isRemoteRouteLink(text)) return emptyList()
        val data = linkData(text, REMOTE_PREFIX)?.let(::b64) ?: return emptyList()
        return data.lines().map { it.trim() }.filter { it.startsWith("http://", true) || it.startsWith("https://", true) }.mapNotNull { line ->
            val url = line.substringBefore('#')
            val host = runCatching { java.net.URI(url).host }.getOrNull()
            if (host.isNullOrEmpty()) return@mapNotNull null
            val raw = line.substringAfter('#', "")
            val fragment = runCatching { java.net.URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8") }.getOrDefault(raw)
            RemoteRoute(fragment.ifEmpty { host }, url)
        }
    }

    fun encode(name: String, defaultOutboundId: Int, rules: List<RouteRule>, nameOf: (Int) -> String?): String {
        val root = buildJsonObject {
            put("kind", KIND)
            put("v", 1)
            put("name", name)
            put("default_outbound", specialName(defaultOutboundId) ?: "proxy")
            put("rules", JsonArray(rules.mapNotNull { ruleJson(it, nameOf) }))
        }
        val b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(root.toString().toByteArray(Charsets.UTF_8))
        return ROUTE_PREFIX + b64
    }

    private fun parseElement(s: String): JsonElement? = runCatching { json.parseToJsonElement(s) }.getOrNull()
        ?.takeIf { it is JsonObject || it is JsonArray }

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull.orEmpty()
    private fun JsonObject.bool(k: String) = (this[k] as? JsonPrimitive)?.booleanOrNull ?: false
    private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()

    private fun scalar(e: JsonElement?): String? {
        val p = e as? JsonPrimitive ?: return null
        if (p.isString) return p.content
        p.longOrNull?.let { return it.toString() }
        p.doubleOrNull?.let { return it.toLong().toString() }
        return p.contentOrNull
    }

    private fun list(e: JsonElement?): List<String> = when (e) {
        is JsonArray -> e.mapNotNull { scalar(it) }.filter { it.isNotEmpty() }
        is JsonPrimitive -> listOfNotNull(scalar(e)).filter { it.isNotEmpty() }
        else -> emptyList()
    }

    private fun resolve(e: JsonElement?, outboundByName: (String) -> Int?, missing: MutableSet<String>): Int {
        val p = e as? JsonPrimitive ?: return OutboundIds.PROXY
        if (!p.isString) {
            val id = p.intOrNull ?: return OutboundIds.PROXY
            if (specialName(id) != null) return id
            missing += id.toString()
            return OutboundIds.PROXY
        }
        specialId(p.content)?.let { return it }
        outboundByName(p.content)?.let { return it }
        missing += p.content
        return OutboundIds.PROXY
    }

    private fun parseRule(o: JsonObject, outboundByName: (String) -> Int?, missing: MutableSet<String>): RouteRule {
        val b = { k: String -> (o[k] as? JsonPrimitive)?.booleanOrNull ?: (scalar(o[k]) == "true") }
        val s = { k: String -> scalar(o[k]).orEmpty() }
        val action = s("action").ifEmpty { "route" }
        return RouteRule(
            name = s("name"),
            type = tokenTypes[s("type")] ?: RuleType.CUSTOM,
            ipVersion = s("ip_version"),
            network = s("network"),
            protocol = s("protocol"),
            inbound = list(o["inbound"]),
            domain = list(o["domain"]),
            domainSuffix = list(o["domain_suffix"]),
            domainKeyword = list(o["domain_keyword"]),
            domainRegex = list(o["domain_regex"]),
            sourceIpCidr = list(o["source_ip_cidr"]),
            sourceIpIsPrivate = b("source_ip_is_private"),
            ipCidr = list(o["ip_cidr"]),
            ipIsPrivate = b("ip_is_private"),
            sourcePort = list(o["source_port"]),
            sourcePortRange = list(o["source_port_range"]),
            port = list(o["port"]),
            portRange = list(o["port_range"]),
            processName = list(o["process_name"]),
            processPath = list(o["process_path"]),
            processPathRegex = list(o["process_path_regex"]),
            wifiSsid = list(o["wifi_ssid"]),
            wifiBssid = list(o["wifi_bssid"]),
            ruleSet = list(o["rule_set"]),
            invert = b("invert"),
            outboundId = when (action) {
                "reject" -> OutboundIds.BLOCK
                "route", "bypass" -> resolve(o["outbound"], outboundByName, missing)
                else -> OutboundIds.DIRECT
            },
            action = action,
            rejectMethod = s("reject_method"),
            noDrop = b("no_drop"),
            overrideAddress = s("override_address"),
            overridePort = s("override_port"),
            sniffOverrideDest = b("override_destination"),
            strategy = s("strategy"),
        )
    }

    private fun ruleJson(r: RouteRule, nameOf: (Int) -> String?): JsonObject? {
        val action = when {
            r.action == "route" && r.outboundId == OutboundIds.BLOCK -> "reject"
            else -> r.action
        }
        val outbound = if (action == "route" || action == "bypass") {
            specialName(r.outboundId) ?: nameOf(r.outboundId) ?: return null
        } else null
        fun strings(v: List<String>) = JsonArray(v.filter { it.isNotEmpty() }.map(::JsonPrimitive))
        fun ports(v: List<String>) = JsonArray(v.mapNotNull { it.trim().toIntOrNull() }.map(::JsonPrimitive))
        return buildJsonObject {
            r.ipVersion.toIntOrNull()?.let { put("ip_version", it) }
            if (r.network.isNotEmpty()) put("network", r.network)
            if (r.protocol.isNotEmpty()) put("protocol", r.protocol)
            listOf(
                "inbound" to r.inbound, "domain" to r.domain, "domain_suffix" to r.domainSuffix,
                "domain_keyword" to r.domainKeyword, "domain_regex" to r.domainRegex, "source_ip_cidr" to r.sourceIpCidr,
                "ip_cidr" to r.ipCidr, "source_port_range" to r.sourcePortRange, "port_range" to r.portRange,
                "process_name" to r.processName, "process_path" to r.processPath, "process_path_regex" to r.processPathRegex,
                "wifi_ssid" to r.wifiSsid, "wifi_bssid" to r.wifiBssid, "rule_set" to r.ruleSet,
            ).forEach { (k, v) -> if (v.any { it.isNotEmpty() }) put(k, strings(v)) }
            if (r.sourceIpIsPrivate) put("source_ip_is_private", true)
            if (r.ipIsPrivate) put("ip_is_private", true)
            if (r.sourcePort.isNotEmpty()) put("source_port", ports(r.sourcePort))
            if (r.port.isNotEmpty()) put("port", ports(r.port))
            if (r.invert) put("invert", true)
            put("action", action)
            if (action == "reject") {
                if (r.rejectMethod.isNotEmpty()) put("reject_method", r.rejectMethod)
                if (r.noDrop) put("no_drop", true)
            }
            if (action == "route" || action == "route-options" || action == "bypass") {
                if (r.overrideAddress.isNotEmpty()) put("override_address", r.overrideAddress)
                r.overridePort.toIntOrNull()?.takeIf { it > 0 }?.let { put("override_port", it) }
            }
            outbound?.let { put("outbound", it) }
            if (action == "sniff" && r.sniffOverrideDest) put("override_destination", true)
            if (action == "resolve" && r.strategy.isNotEmpty()) put("strategy", r.strategy)
            put("name", r.name)
            put("type", typeTokens[r.type] ?: "custom")
        }
    }

    private fun remapRaw(node: JsonElement, names: JsonObject, outboundByName: (String) -> Int?, missing: MutableSet<String>): JsonObject =
        remapRawRec(node, names, outboundByName, missing) as JsonObject

    private fun remapRawRec(node: JsonElement, names: JsonObject, outboundByName: (String) -> Int?, missing: MutableSet<String>): JsonElement =
        when (node) {
            is JsonObject -> JsonObject(node.mapValues { (k, v) ->
                val id = (v as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
                if ((k == "outbound" || k == "final") && id != null) {
                    if (id < 0) return@mapValues v
                    val nm = (names[id.toString()] as? JsonPrimitive)?.contentOrNull.orEmpty()
                    val local = nm.takeIf { it.isNotEmpty() }?.let(outboundByName)
                    if (local == null) missing += nm.ifEmpty { id.toString() }
                    JsonPrimitive(local ?: OutboundIds.PROXY)
                } else remapRawRec(v, names, outboundByName, missing)
            })
            is JsonArray -> JsonArray(node.map { remapRawRec(it, names, outboundByName, missing) })
            else -> node
        }
}
