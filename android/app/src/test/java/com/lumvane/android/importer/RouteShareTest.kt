package com.lumvane.android.importer

import com.lumvane.android.config.model.OutboundIds
import com.lumvane.android.config.model.RouteRule
import com.lumvane.android.config.model.RuleType
import java.io.File
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class RouteShareTest {
    private fun sharedJson(): String {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            File(dir, "testdata/route-share.json").takeIf { it.isFile }?.let { return it.readText() }
            dir = dir.parentFile
        }
        fail("testdata/route-share.json not found")
    }

    private fun link(json: String) = "lumvane://route/" + Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())

    @Test
    fun decodesDesktopShareFormat() {
        val byName = mapOf("Office" to 42)
        for (input in listOf(sharedJson(), link(sharedJson()), Base64.getEncoder().encodeToString(sharedJson().toByteArray()))) {
            val r = assertNotNull(RouteShare.decode(input) { byName[it] }, input.take(40))
            assertEquals("Shared", r.name)
            assertEquals(OutboundIds.DIRECT, r.defaultOutboundId)
            assertEquals(4, r.rules.size)
            val ports = r.rules[0]
            assertEquals("4", ports.ipVersion)
            assertEquals(listOf("443", "8443"), ports.port)
            assertEquals(listOf("1000"), ports.sourcePort)
            assertEquals("8080", ports.overridePort)
            assertEquals(OutboundIds.PROXY, ports.outboundId)
            assertEquals("reject", r.rules[1].action)
            assertEquals("drop", r.rules[1].rejectMethod)
            assertEquals(RuleType.SIMPLE_ADDRESS_BYPASS, r.rules[2].type)
            assertEquals(listOf("geosite-category-ads-all"), r.rules[2].ruleSet)
            assertEquals(OutboundIds.DIRECT, r.rules[2].outboundId)
            assertEquals(OutboundIds.PROXY, r.rules[3].outboundId, "unknown profile falls back to proxy")
            assertEquals(listOf("Missing Server"), r.missingOutbounds)
        }
    }

    @Test
    fun encodeDecodeRoundTripKeepsProfileNames() {
        val rules = listOf(
            RouteRule(name = "office", domainSuffix = listOf("corp.example.com"), outboundId = 7, port = listOf("443")),
            RouteRule(name = "block", domain = listOf("ads.example.com"), outboundId = OutboundIds.BLOCK, rejectMethod = "default"),
            RouteRule(name = "gone", domain = listOf("gone.example.com"), outboundId = 99),
        )
        val l = RouteShare.encode("Mine", OutboundIds.PROXY, rules) { if (it == 7) "Office" else null }
        assertTrue(RouteShare.isRouteLink(l))
        val r = assertNotNull(RouteShare.decode(l) { if (it == "Office") 13 else null })
        assertEquals(2, r.rules.size, "a rule whose profile is gone is not shared")
        assertEquals(13, r.rules[0].outboundId)
        assertEquals(listOf("443"), r.rules[0].port)
        assertEquals("reject", r.rules[1].action)
        assertEquals(OutboundIds.BLOCK, r.rules[1].outboundId)
        assertEquals("default", r.rules[1].rejectMethod)
    }

    @Test
    fun rawProfileRemapsIdsByName() {
        val json = """{"kind":"lumvane-route-profile","v":1,"name":"Raw","raw":true,"prevent_modifications":true,
            "route":{"rules":[{"domain":["a.example.com"],"outbound":5},{"logical":{"rules":[{"outbound":6}]}}],"final":-2},
            "outbound_names":{"5":"Office","6":"Lost"}}"""
        val r = assertNotNull(RouteShare.decode(json) { if (it == "Office") 21 else null })
        assertTrue(r.isRaw && r.preventModifications)
        assertTrue(r.rawRoute.contains("\"outbound\":21"), r.rawRoute)
        assertTrue(r.rawRoute.contains("\"outbound\":-1"), r.rawRoute)
        assertTrue(r.rawRoute.contains("\"final\":-2"), r.rawRoute)
        assertEquals(listOf("Lost"), r.missingOutbounds)
    }

    @Test
    fun legacyRuleArrayAndWarpBypass() {
        val r = assertNotNull(RouteShare.decode("""[{"domain":["a.example.com"],"action":"route","outbound":"warp-bypass"},{"port":[53],"action":"hijack-dns"}]""") { null })
        assertEquals(listOf("imported rule #1", "imported rule #2"), r.rules.map { it.name })
        assertEquals(OutboundIds.WARP_BYPASS, r.rules[0].outboundId)
        assertEquals(listOf("53"), r.rules[1].port)
        val l = RouteShare.encode("W", OutboundIds.PROXY, listOf(RouteRule(name = "w", domain = listOf("a.example.com"), outboundId = OutboundIds.WARP_BYPASS))) { null }
        assertEquals(OutboundIds.WARP_BYPASS, assertNotNull(RouteShare.decode(l) { null }).rules.single().outboundId)
    }

    @Test
    fun rejectsForeignInput() {
        assertNull(RouteShare.decode("""{"kind":"other"}""") { null })
        assertNull(RouteShare.decode("lumvane://route/") { null })
        assertNull(RouteShare.decode("not a route") { null })
    }

    @Test
    fun remoteRouteLinks() {
        val body = "https://example.com/r1.json#Office rules\nftp://example.com/x\nhttps://cdn.example.com/r2.json\nhttps://example.com/r3.json#A+B%20C\n"
        val l = "lumvane://remoteroute/" + Base64.getEncoder().encodeToString(body.toByteArray())
        val r = RouteShare.decodeRemote(l)
        assertEquals(listOf(RemoteRoute("Office rules", "https://example.com/r1.json"), RemoteRoute("cdn.example.com", "https://cdn.example.com/r2.json"), RemoteRoute("A+B C", "https://example.com/r3.json")), r)
        assertTrue(RouteShare.decodeRemote("lumvane://route/abc").isEmpty())
    }
}
