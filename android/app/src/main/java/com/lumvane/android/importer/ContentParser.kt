package com.lumvane.android.importer

import com.lumvane.android.config.model.CustomBean
import com.lumvane.android.config.model.OutboundBean
import com.lumvane.android.config.model.ShadowsocksBean
import com.lumvane.android.config.model.endpoint
import com.lumvane.android.config.model.typeName
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class ImportedProfile(val name: String, val bean: OutboundBean, val sourceId: Long = 0)

class ImportResult(val profiles: List<ImportedProfile>, val skipped: Int) {
    val isEmpty get() = profiles.isEmpty()
}

fun displayName(bean: OutboundBean): String {
    if (bean.name.isNotBlank()) return bean.name
    bean.endpoint?.let { (h, p) -> if (h.isNotEmpty()) return if (p > 0) "$h:$p" else h }
    return bean.typeName
}

object ContentParser {
    fun parse(text: String, options: ParseOptions = ParseOptions()): ImportResult {
        val out = ArrayList<ImportedProfile>()
        val skipped = intArrayOf(0)
        update(text.trim(), needParse = true, isBase64Decoded = false, options = options, out = out, skipped = skipped)
        return ImportResult(out, skipped[0])
    }

    private fun add(out: MutableList<ImportedProfile>, bean: OutboundBean?): Boolean {
        if (bean == null) return false
        out += ImportedProfile(displayName(bean), bean)
        return true
    }

    private fun update(
        str: String, needParse: Boolean, isBase64Decoded: Boolean, options: ParseOptions,
        out: MutableList<ImportedProfile>, skipped: IntArray, depth: Int = 0,
    ) {
        if (depth > 4 || str.isBlank()) return
        if (!isBase64Decoded && !str.startsWith("{") && !str.startsWith("[")) {
            LinkParser.b64Text(str)?.let { decoded ->
                update(decoded.trim(), true, true, options, out, skipped, depth + 1)
                return
            }
        }

        val doc: JsonElement? = if (str.startsWith("{") || str.startsWith("[")) {
            runCatching { kotlinx.serialization.json.Json.parseToJsonElement(str) }.getOrNull()
        } else null
        if (doc != null) {
            updateJson(doc, str, options, out)
            return
        }

        if (str.lineSequence().any { it.trimStart().startsWith("proxies:") }) {
            ClashParser.parse(str).forEach { add(out, it) }
            return
        }

        if (str.contains("[Interface]") && str.contains("[Peer]")) {
            add(out, LinkParser.parseWireGuardConf(str))
            return
        }

        if (str.contains('\n') && needParse) {
            for (line in disect(str)) update(line.trim(), false, false, options, out, skipped, depth + 1)
            return
        }

        if (str.startsWith("//") || str.startsWith("#") || str.length < 2) return

        if (str.startsWith("{")) {
            skipped[0]++
            return
        }
        if (!add(out, LinkParser.parse(str, options))) skipped[0]++
    }

    internal fun disect(str: String): List<String> {
        val res = ArrayList<String>()
        var idx = 0
        val sz = str.length
        while (idx < sz) {
            val c = str[idx]
            if (c == '\n' || c == '\r') { idx++; continue }
            if (c == '{') {
                val end = jsonEnd(str, idx)
                if (end == -1) return res
                res += str.substring(idx, end + 1)
                idx = end + 1
                continue
            }
            var nl = str.indexOf('\n', idx)
            if (nl == -1) nl = sz
            res += str.substring(idx, nl)
            idx = nl + 1
        }
        return res
    }

    private fun jsonEnd(str: String, begin: Int): Int {
        var counter = 1
        for (i in begin + 1 until str.length) {
            if (str[i] == '{') counter++
            if (str[i] == '}') counter--
            if (counter == 0) return i
        }
        return -1
    }

    private val INFRA = setOf("direct", "block", "dns", "selector", "urltest", "dns-out", "any")

    private fun updateJson(doc: JsonElement, raw: String, options: ParseOptions, out: MutableList<ImportedProfile>) {
        if (doc is JsonObject) {
            val obs = (doc["outbounds"] as? JsonArray)?.filterIsInstance<JsonObject>().orEmpty()
            if (obs.any { it.containsKey("protocol") }) {
                obs.forEach { add(out, xrayOutbound(it)) }
                return
            }
            if (doc.containsKey("protocol")) {
                add(out, xrayOutbound(doc))
                return
            }
            if (doc.containsKey("outbounds") || doc.containsKey("endpoints")) {
                val items = (doc["outbounds"] as? JsonArray).orEmpty() + (doc["endpoints"] as? JsonArray).orEmpty()
                items.filterIsInstance<JsonObject>().forEach { add(out, SingBoxJson.parseOutbound(it)) }
                return
            }
            if (doc.containsKey("type") && doc["type"] is JsonPrimitive) {
                val b = SingBoxJson.parseOutbound(doc)
                if (b != null) add(out, b)
                else if (doc["type"].str() !in INFRA) add(out, CustomBean(doc["tag"].str(), CustomBean.OUTBOUND, raw))
                return
            }
            if (doc.containsKey("version") && doc.containsKey("servers")) {
                (doc["servers"] as? JsonArray)?.filterIsInstance<JsonObject>()?.forEach { s -> add(out, sip008(s)) }
            }
            return
        }
        if (doc is JsonArray && doc.isNotEmpty()) {
            val first = doc.first() as? JsonObject ?: return
            if (first.containsKey("protocol")) {
                doc.filterIsInstance<JsonObject>().forEach { add(out, xrayOutbound(it)) }
                return
            }
            if (first.containsKey("outbounds") && (first["outbounds"] as? JsonArray)?.any { (it as? JsonObject)?.containsKey("protocol") == true } == true) {
                for (c in doc.filterIsInstance<JsonObject>()) {
                    if (!c.containsKey("outbounds")) continue
                    val cfg = JsonObject(c.filterKeys { it != "inbounds" })
                    add(out, CustomBean(c["remarks"].str(), CustomBean.XRAY_FULL_CONFIG, cfg.toString()))
                }
                return
            }
            if (first.containsKey("type")) {
                doc.filterIsInstance<JsonObject>().forEach { add(out, SingBoxJson.parseOutbound(it)) }
            }
        }
    }

    private fun JsonElement?.str(): String = (this as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun xrayOutbound(o: JsonObject): OutboundBean? =
        if (o["protocol"].str() == "vless") SingBoxJson.parseXrayVless(o) ?: SingBoxJson.xrayOutboundAsCustom(o)
        else SingBoxJson.xrayOutboundAsCustom(o)

    private fun sip008(s: JsonObject): OutboundBean? {
        val server = s["server"].str()
        val method = s["method"].str()
        val password = s["password"].str()
        if (server.isEmpty() || method.isEmpty() || password.isEmpty()) return null
        val port = s["server_port"].str().toDoubleOrNull()?.toInt() ?: 0
        return ShadowsocksBean(
            name = s["remarks"].str(), server = server, serverPort = port, method = method, password = password,
            plugin = s["plugin"].str().replace("simple-obfs", "obfs-local"), pluginOpts = s["plugin_opts"].str(),
        )
    }
}
