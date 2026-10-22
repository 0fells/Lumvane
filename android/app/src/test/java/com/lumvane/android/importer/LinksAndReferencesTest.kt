package com.lumvane.android.importer

import com.lumvane.android.config.model.AutoSelectorBean
import com.lumvane.android.config.model.ChainBean
import com.lumvane.android.config.model.TrojanBean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinksAndReferencesTest {
    private val view = "android.intent.action.VIEW"
    private val send = "android.intent.action.SEND"

    @Test
    fun incomingViewLinks() {
        assertEquals("vless://abc@example.com:443#n", IncomingPayload.extract(view, " vless://abc@example.com:443#n ", null))
        assertEquals("lumvane://add/e30", IncomingPayload.extract(view, "lumvane://add/e30", null))
        assertEquals("hy2://pw@192.0.2.1:443", IncomingPayload.extract(view, "hy2://pw@192.0.2.1:443", null))
        assertNull(IncomingPayload.extract(view, "https://example.com/x", null))
        assertNull(IncomingPayload.extract(view, null, null))
    }

    @Test
    fun incomingSendText() {
        assertEquals("trojan://pw@example.com:443", IncomingPayload.extract(send, null, "  trojan://pw@example.com:443\n"))
        assertEquals("https://example.com/sub", IncomingPayload.extract(send, null, "https://example.com/sub"))
        assertNull(IncomingPayload.extract(send, null, "   "))
        assertNull(IncomingPayload.extract(send, null, "x".repeat(IncomingPayload.MAX_LENGTH + 1)))
        assertNull(IncomingPayload.extract("android.intent.action.MAIN", "vless://a@example.com:1", "t"))
    }

    @Test
    fun deepLinkPayloadIsParsedByImporter() {
        val json = """{"type":"trojan","tag":"t","server":"192.0.2.1","server_port":443,"password":"pw"}"""
        val b64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())
        val payload = assertNotNull(IncomingPayload.extract(view, "lumvane://add/$b64", null))
        assertIs<TrojanBean>(ContentParser.parse(payload).profiles.single().bean)
    }

    @Test
    fun exportImportRemapsReferences() {
        val auto = AutoSelectorBean(name = "a", gid = 7, pool = listOf(10, 11, 99), pinnedId = 11, lastBuilt = listOf(10))
        val file = TransferFile(
            groups = listOf(
                TransferGroup(
                    "G", id = 7,
                    profiles = listOf(
                        TransferProfile("t1", TrojanBean(name = "t1", server = "192.0.2.1", serverPort = 443, password = "pw"), id = 10),
                        TransferProfile("t2", TrojanBean(name = "t2", server = "192.0.2.2", serverPort = 443, password = "pw"), id = 11),
                        TransferProfile("c", ChainBean("c", listOf(10, 11, 12)), id = 12),
                        TransferProfile("a", auto, id = 13),
                    ),
                ),
            ),
        )
        val back = assertNotNull(ProfileTransfer.decode(ProfileTransfer.encode(file)))
        val g = back.groups.single()
        assertEquals(7L, g.id)
        assertEquals(listOf(10L, 11L, 12L, 13L), g.profiles.map { it.id })
        assertEquals(auto, g.profiles[3].bean)
        assertEquals(listOf(10L, 11L, 12L), ProfileTransfer.referencedProfileIds(g.profiles[2].bean))

        val profileMap = mapOf(10L to 101L, 11L to 102L, 12L to 103L, 13L to 104L)
        val groupMap = mapOf(7L to 55L)
        assertEquals(ChainBean("c", listOf(101, 102, 103)), remapReferences(g.profiles[2].bean, profileMap, groupMap))
        val ra = assertIs<AutoSelectorBean>(remapReferences(g.profiles[3].bean, profileMap, groupMap))
        assertEquals(55, ra.gid)
        assertEquals(listOf(101, 102), ra.pool)
        assertEquals(102, ra.pinnedId)
        assertEquals(listOf(101), ra.lastBuilt)
        val orphan = assertIs<AutoSelectorBean>(remapReferences(auto, emptyMap(), emptyMap()))
        assertEquals(-1, orphan.gid)
        assertEquals(-1, orphan.pinnedId)
        assertEquals(ChainBean("c", emptyList()), remapReferences(ChainBean("c", listOf(10, 99, 12)), profileMap, groupMap))
    }

    @Test
    fun version1ExportStillDecodes() {
        val old = """{"format":"lumvane-android","version":1,"groups":[{"name":"G","profiles":[{"name":"t","bean":{"type":"trojan","server":"192.0.2.1","serverPort":443,"password":"pw"}}]}]}"""
        val f = assertNotNull(ProfileTransfer.decode(old))
        assertEquals(0L, f.groups.single().id)
        assertEquals(0L, f.groups.single().profiles.single().id)
    }

    @Test
    fun windowsChainAndAutoSelectorDecoding() {
        val groups = listOf(WinGroup(3, "G", "", "", false, false, false, 0, listOf(1, 2, 3, 4)))
        val sel = """{"name":"sel","type":"autoselector","gid":3,"name_filter":"x","country_filter":"NL,DE","exclude_unavailable":false,
            "pool_cap":500,"build_limit":100,"result_validity_mins":60,"test_url":"https://example.com/t","connectivity_url":"https://example.com/c",
            "interval_sec":120,"bench_interval_sec":240,"watch_interval_sec":20,"active_size":6,"sampling":5,"tolerance_ms":150,"max_rtt_ms":900,
            "expected":2,"dial_retries":1,"interrupt_on_switch":false,"balance":true,"balance_mode":"connection","balance_interval_sec":45,
            "pool":[1,2],"pool_ranked_at":1700000000,"pinned_id":2,"last_built":[1],"last_built_at":1700000100,"history":[{"t":1}]}"""
        val profiles = listOf(
            WinProfile(1, "trojan", "a", 3, """{"type":"trojan","tag":"a","server":"192.0.2.1","server_port":443,"password":"pw"}"""),
            WinProfile(2, "trojan", "b", 3, """{"type":"trojan","tag":"b","server":"192.0.2.2","server_port":443,"password":"pw"}"""),
            WinProfile(3, "chain", "ch", 3, """{"name":"ch","type":"chain","list":[1,2]}"""),
            WinProfile(4, "autoselector", "sel", 3, sel),
        )
        val c = WindowsDbImport.convert(groups, profiles)
        val items = c.groups.single().second
        assertEquals(4, items.size)
        assertTrue(c.skipped.isEmpty())
        assertEquals(listOf(1L, 2L, 3L, 4L), items.map { it.sourceId })
        assertEquals(ChainBean("ch", listOf(1, 2)), items[2].bean)
        val a = assertIs<AutoSelectorBean>(items[3].bean)
        assertEquals(3, a.gid)
        assertEquals("NL,DE", a.countryFilter)
        assertEquals(false, a.excludeUnavailable)
        assertEquals(500, a.poolCap)
        assertEquals(240, a.benchIntervalSec)
        assertEquals(900, a.maxRttMs)
        assertEquals("connection", a.balanceMode)
        assertEquals(listOf(1, 2), a.pool)
        assertEquals(1700000000L, a.poolRankedAt)
        assertEquals(2, a.pinnedId)
        assertEquals(1, a.history.size)

        val profileMap = mapOf(1L to 21L, 2L to 22L, 3L to 23L, 4L to 24L)
        val r = assertIs<AutoSelectorBean>(remapReferences(a, profileMap, mapOf(3L to 9L)))
        assertEquals(9, r.gid)
        assertEquals(listOf(21, 22), r.pool)
        assertEquals(22, r.pinnedId)
        assertEquals(ChainBean("ch", listOf(21, 22)), remapReferences(items[2].bean, profileMap, emptyMap()))
    }
}
