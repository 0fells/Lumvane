package com.lumvane.android.config

import com.lumvane.android.config.model.*
import com.lumvane.android.config.outbounds.AutoTags
import com.lumvane.android.data.AppSettings
import com.lumvane.android.data.AutoMode
import com.lumvane.android.data.AutoScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutoConfigTest {
    private val uuid = "00000000-0000-4000-8000-000000000001"
    private val tls = TlsConfig(enabled = true, serverName = "example.com")

    private val beans: Map<Int, OutboundBean> = mapOf(
        1 to VlessBean(name = "vless", server = "192.0.2.10", serverPort = 443, uuid = uuid, tls = tls),
        2 to HysteriaBean(name = "hy2", server = "192.0.2.20", serverPort = 443, protocolVersion = "2", password = "dummy", tls = tls),
        3 to XrayVlessBean(
            name = "xray", server = "192.0.2.30", serverPort = 443, uuid = uuid,
            streamSetting = XrayStreamSetting(network = "raw", security = "tls", tls = XrayTls(serverName = "example.com")),
        ),
        4 to TrojanBean(name = "trojan", server = "192.0.2.40", serverPort = 443, password = "dummy", tls = tls),
        5 to CustomBean(name = "broken", subtype = CustomBean.OUTBOUND, config = "{not json"),
        6 to ChainBean(name = "chain", list = listOf(1, 4)),
    )

    private fun builder(map: Map<Int, OutboundBean> = beans): ConfigBuilder {
        var port = 42000
        return ConfigBuilder({ map[it] }, ConfigOptions(allocatePort = { ++port }, randomSecret = { "SECRET$port" }, systemDnsServers = listOf("192.0.2.53")))
    }

    private fun auto(ids: List<Int>, mode: AutoMode = AutoMode.ON, preferred: Int = -1, settings: AppSettings = AppSettings(autoMode = mode), map: Map<Int, OutboundBean> = beans) =
        builder(map).buildAuto(AutoRequest(mode, ids, preferred), settings)

    private fun root(c: GeneratedConfig) = Json.parseToJsonElement(c.singBoxJson).jsonObject
    private fun JsonObject.byTag(tag: String) =
        this["outbounds"]!!.jsonArray.map { it.jsonObject }.first { it["tag"]?.jsonPrimitive?.content == tag }

    private fun group(c: GeneratedConfig) = root(c).byTag("proxy")
    private fun members(c: GeneratedConfig) = group(c)["outbounds"]!!.jsonArray.map { it.jsonPrimitive.content }

    @Test
    fun urltestOverMixedProtocols() {
        val c = auto(listOf(1, 2, 3, 4, 6))
        val g = group(c)
        assertEquals("urltest", g["type"]!!.jsonPrimitive.content)
        assertEquals(listOf("auto-1-0", "auto-2-0", "auto-3-0", "auto-4-0", "auto-6-0"), members(c))
        assertEquals("http://cp.cloudflare.com/", g["url"]!!.jsonPrimitive.content)
        assertEquals("60s", g["interval"]!!.jsonPrimitive.content)
        assertEquals("1800s", g["idle_timeout"]!!.jsonPrimitive.content)
        assertTrue(g["interrupt_exist_connections"]!!.jsonPrimitive.boolean)
        val r = root(c)
        assertEquals("vless", r.byTag("auto-1-0")["type"]!!.jsonPrimitive.content)
        assertEquals("hysteria2", r.byTag("auto-2-0")["type"]!!.jsonPrimitive.content)
        assertEquals("socks", r.byTag("auto-3-0")["type"]!!.jsonPrimitive.content)
        assertEquals("trojan", r.byTag("auto-4-0")["type"]!!.jsonPrimitive.content)
        assertEquals("trojan", r.byTag("auto-6-0")["type"]!!.jsonPrimitive.content)
        assertEquals("auto-6-1", r.byTag("auto-6-0")["detour"]!!.jsonPrimitive.content)
        assertEquals("proxy", r["route"]!!.jsonObject["final"]!!.jsonPrimitive.content)
        val x = assertNotNull(c.xray)
        assertTrue(x.lazyStart)
        assertTrue(x.xrayConfig.contains("auto-3-0"))
        assertEquals(listOf(1, 2, 3, 4, 6), members(c).map { AutoTags.profileId(it) })
    }

    @Test
    fun groupKnobs() {
        val d = group(auto(listOf(1, 2), preferred = 2))
        assertEquals(75, d["tolerance"]!!.jsonPrimitive.int)
        assertTrue(d["prefer_first"]!!.jsonPrimitive.boolean)
        assertNull(group(auto(listOf(1, 2)))["prefer_first"])
        assertNull(group(auto(listOf(1, 2), preferred = 5))["prefer_first"])
        val custom = AppSettings(autoMode = AutoMode.ON, autoToleranceMs = 120, autoIntervalSec = 30, autoTestUrl = "http://example.com/t")
        val g = group(auto(listOf(1, 2), settings = custom))
        assertEquals(120, g["tolerance"]!!.jsonPrimitive.int)
        assertEquals("30s", g["interval"]!!.jsonPrimitive.content)
        assertEquals("http://example.com/t", g["url"]!!.jsonPrimitive.content)
        val huge = AppSettings(autoMode = AutoMode.ON, autoToleranceMs = 90_000, autoIntervalSec = 99_999)
        val h = group(auto(listOf(1, 2), settings = huge))
        assertEquals(30_000, h["tolerance"]!!.jsonPrimitive.int)
        assertEquals("3600s", h["interval"]!!.jsonPrimitive.content)
        assertTrue(h["idle_timeout"]!!.jsonPrimitive.content.removeSuffix("s").toInt() >= 3600)
    }

    @Test
    fun preferredProfileGoesFirst() {
        assertEquals("auto-4-0", members(auto(listOf(1, 2, 4), AutoMode.ON, preferred = 4)).first())
    }

    @Test
    fun brokenCandidatesAreSkippedWithWarnings() {
        val c = auto(listOf(1, 5, 99, 2))
        assertEquals(listOf("auto-1-0", "auto-2-0"), members(c))
        assertEquals(2, c.warnings.size)
        assertTrue(c.warnings.any { it.contains("#5") })
        assertTrue(c.warnings.any { it.contains("#99") })
    }

    @Test
    fun fewerThanTwoUsableCandidatesFails() {
        assertFailsWith<ConfigBuildException> { auto(listOf(1)) }
        assertFailsWith<ConfigBuildException> { auto(listOf(1, 5, 99)) }
        assertFailsWith<ConfigBuildException> { auto(emptyList()) }
        assertFailsWith<ConfigBuildException> { auto(listOf(1, 2), AutoMode.OFF) }
    }

    @Test
    fun noFindProcessAndNoProcessRules() {
        val route = root(auto(listOf(1, 2)))["route"]!!.jsonObject
        assertNull(route["find_process"])
        val rules = route["rules"]!!.jsonArray.map { it.jsonObject }
        assertTrue(rules.none { it.containsKey("process_name") || it.containsKey("process_path") })
        val plain = Json.parseToJsonElement(builder().build(1, beans[1]!!, AppSettings()).singBoxJson).jsonObject["route"]!!.jsonObject
        assertNull(plain["find_process"])
    }

    @Test
    fun candidateScopes() {
        val all = listOf(AutoCandidate(1, 1), AutoCandidate(2, 1), AutoCandidate(3, 2))
        assertEquals(listOf(1, 2, 3), selectAutoCandidates(AppSettings(), all, 1))
        assertEquals(listOf(3), selectAutoCandidates(AppSettings(autoScope = AutoScope.GROUP), all, 2))
        assertEquals(listOf(2), selectAutoCandidates(AppSettings(autoScope = AutoScope.MANUAL, autoProfileIds = listOf(2, 77)), all, 1))
    }

    @Test
    fun settingsRoundTrip() {
        val s = AppSettings(autoMode = AutoMode.ON, autoScope = AutoScope.MANUAL, autoProfileIds = listOf(3, 4), autoIntervalSec = 90)
        val back = com.lumvane.android.data.SettingsCodec.decode(com.lumvane.android.data.SettingsCodec.encode(s))
        assertEquals(s, back)
    }

    @Test
    fun legacyModesReadAsOn() {
        val codec = com.lumvane.android.data.SettingsCodec
        assertEquals(AutoMode.ON, codec.decode(mapOf("autoMode" to "FASTEST")).autoMode)
        assertEquals(AutoMode.ON, codec.decode(mapOf("autoMode" to "RESERVE")).autoMode)
        assertEquals(AutoMode.OFF, codec.decode(mapOf("autoMode" to "OFF")).autoMode)
    }

    @Test
    fun urlTestGroupDetection() {
        assertTrue(com.lumvane.android.service.hasUrlTestGroup(auto(listOf(1, 2)).singBoxJson))
        assertTrue(!com.lumvane.android.service.hasUrlTestGroup(builder().build(1, beans[1]!!, AppSettings()).singBoxJson))
    }
}
