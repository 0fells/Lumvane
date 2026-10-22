package com.lumvane.android.sub

import com.lumvane.android.config.model.AutoSelectorBean
import com.lumvane.android.config.model.OutboundBean
import com.lumvane.android.config.model.TrojanBean
import com.lumvane.android.data.AppSettings
import com.lumvane.android.data.Profile
import com.lumvane.android.importer.ImportedProfile
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubTest {
    private fun trojan(name: String, host: String, pw: String = "pw", port: Int = 443) =
        TrojanBean(name = name, server = host, serverPort = port, password = pw)

    private fun profile(id: Long, bean: OutboundBean, name: String = bean.name) = Profile(id = id, groupId = 1, name = name, bean = bean)
    private fun imp(bean: OutboundBean) = ImportedProfile(bean.name, bean)

    @Test
    fun diffKeepsUpdatesAddsAndDeletes() {
        val old = listOf(
            profile(1, trojan("keep", "192.0.2.1")),
            profile(2, trojan("renamed-old", "192.0.2.2")),
            profile(3, trojan("pw-changed", "192.0.2.3", "old")),
            profile(4, trojan("gone", "192.0.2.4")),
            profile(5, AutoSelectorBean(name = "auto")),
        )
        val new = listOf(
            imp(trojan("brand-new", "192.0.2.9")),
            imp(trojan("pw-changed", "192.0.2.3", "new")),
            imp(trojan("keep", "192.0.2.1")),
            imp(trojan("renamed-new", "192.0.2.2")),
        )
        val plan = SubscriptionDiff.compute(old, new)
        assertEquals(listOf(4L), plan.delete)
        assertEquals(listOf("brand-new"), plan.add.map { it.name })
        assertEquals(setOf(3L, 2L), plan.update.map { it.first }.toSet())
        assertEquals(
            listOf(Slot.New(0), Slot.Existing(3), Slot.Existing(1), Slot.Existing(2), Slot.Existing(5)),
            plan.order,
        )
    }

    @Test
    fun diffIdenticalSubscriptionChangesNothing() {
        val old = listOf(profile(1, trojan("a", "192.0.2.1")), profile(2, trojan("b", "192.0.2.2")))
        val plan = SubscriptionDiff.compute(old, old.map { imp(it.bean) })
        assertTrue(plan.add.isEmpty() && plan.delete.isEmpty() && plan.update.isEmpty())
        assertEquals(listOf(Slot.Existing(1), Slot.Existing(2)), plan.order)
    }

    @Test
    fun diffHandlesDuplicatesAndProtectedProfiles() {
        val old = listOf(profile(1, trojan("dup", "192.0.2.1")), profile(2, trojan("dup", "192.0.2.1")), profile(3, trojan("running", "192.0.2.3")))
        val new = listOf(imp(trojan("dup", "192.0.2.1")))
        val plan = SubscriptionDiff.compute(old, new, protectedIds = setOf(3L))
        assertEquals(listOf(2L), plan.delete)
        assertEquals(listOf(3L), plan.kept)
        assertEquals(listOf(Slot.Existing(1), Slot.Existing(3)), plan.order)
    }

    @Test
    fun clearModeReplacesEverythingExceptStickyAndProtected() {
        val old = listOf(profile(1, trojan("a", "192.0.2.1")), profile(2, AutoSelectorBean(name = "auto")), profile(3, trojan("b", "192.0.2.2")))
        val plan = SubscriptionDiff.compute(old, listOf(imp(trojan("n", "192.0.2.5"))), protectedIds = setOf(3L), clear = true)
        assertEquals(listOf(1L), plan.delete)
        assertEquals(listOf(Slot.New(0), Slot.Existing(2), Slot.Existing(3)).size, plan.order.size)
        assertTrue(plan.order.containsAll(listOf(Slot.Existing(2), Slot.Existing(3), Slot.New(0))))
    }

    @Test
    fun subscriptionUserInfo() {
        val i = SubscriptionInfo.parse("upload=455727941; download=6174315083; total=1073741824000; expire=1709251200")
        assertEquals(455727941L, i.upload)
        assertEquals(1073741824000L, i.total)
        assertEquals(1709251200L, i.expire)
        assertEquals(455727941L + 6174315083L, i.used)
        assertTrue(!SubscriptionInfo.parse("").hasData)
        assertTrue(!SubscriptionInfo.parse("garbage").hasData)
    }

    @Test
    fun userAgentAndHwid() {
        assertEquals("Lumvane/1.0.0", SubscriptionClient.userAgent(AppSettings()))
        assertEquals("Custom/9", SubscriptionClient.userAgent(AppSettings(userAgent = "Custom/9")))
        val dev = DeviceDetails("hw-1", "Android", "14", "Pixel")
        val h = SubscriptionClient.hwidHeaders(AppSettings(subCustomHwidParams = "hwid=custom, model=X1, bogus=1, os=bad\nvalue"), dev)
        assertEquals("custom", h["x-hwid"])
        assertEquals("X1", h["x-device-model"])
        assertEquals("Android", h["x-device-os"])
        assertEquals("14", h["x-ver-os"])
    }

    @Test
    fun clientFetchesBodyAndHeaders() { runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody("trojan://pw@192.0.2.1:443#x")
                    .setHeader("Subscription-UserInfo", "upload=1; download=2; total=3; expire=4")
                    .setHeader("Profile-Update-Interval", "12")
                    .setHeader("Profile-Title", "base64:" + com.lumvane.android.importer.UrlParts.b64Encode("My Sub".toByteArray())),
            )
            server.start()
            val client = SubscriptionClient(device = { DeviceDetails("hw-1", "Android", "14", "Pixel") })
            val r = client.fetch(server.url("/sub").toString(), AppSettings(userAgent = "UA-Test/1", subSendHwid = true))
            assertEquals("trojan://pw@192.0.2.1:443#x", r.body)
            assertEquals("upload=1; download=2; total=3; expire=4", r.userInfo)
            assertEquals(3L, r.info.total)
            assertEquals(12, r.updateIntervalHours)
            assertEquals("My Sub", r.title)
            val req = server.takeRequest()
            assertEquals("UA-Test/1", req.getHeader("User-Agent"))
            assertEquals("hw-1", req.getHeader("x-hwid"))
            assertEquals("Pixel", req.getHeader("x-device-model"))
        }
    } }

    @Test
    fun clientRejectsHttpErrorsAndBadUrls() { runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(403))
            server.start()
            val client = SubscriptionClient()
            assertFailsWith<SubscriptionException> { client.fetch(server.url("/").toString()) }
            assertFailsWith<SubscriptionException> { client.fetch("not a url") }
            server.enqueue(MockResponse().setBody("ok"))
            SubscriptionClient(device = { DeviceDetails("secret") }).fetch(server.url("/").toString())
            server.takeRequest()
            assertNull(server.takeRequest().getHeader("x-hwid"))
        }
    } }

    @Test
    fun downloadWritesAtomicallyAndKeepsOldFileOnFailure() { runBlocking {
        val dir = File(System.getProperty("java.io.tmpdir"), "vm-sub-test-" + System.nanoTime()).apply { mkdirs() }
        try {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setBody("SRS-DATA"))
                server.enqueue(MockResponse().setResponseCode(500))
                server.start()
                val f = File(dir, "geosite-test.srs")
                val client = SubscriptionClient()
                assertEquals(8L, client.download(server.url("/a.srs").toString(), f))
                assertEquals("SRS-DATA", f.readText())
                assertFailsWith<SubscriptionException> { client.download(server.url("/a.srs").toString(), f) }
                assertEquals("SRS-DATA", f.readText())
                assertEquals(listOf("geosite-test.srs"), dir.list()!!.toList())
            }
        } finally {
            dir.deleteRecursively()
        }
    } }

    @Test
    fun ruleSetCatalogAndMirrors() {
        val tsv = "geoip-ru\thttps://raw.githubusercontent.com/MetaCubeX/meta-rules-dat/sing/geo/geoip/ru.srs\n" +
            "geosite-x@ads\thttps://raw.githubusercontent.com/MetaCubeX/meta-rules-dat/sing/geo/geosite/x@ads.srs\n\nbroken-line\n"
        val cat = RuleSetCatalog.parse(tsv.byteInputStream())
        assertEquals(2, cat.size)
        val url = cat.urlFor("geoip-ru")!!
        assertEquals(url, RuleSetCatalog.mirrorUrl(url, 0))
        assertEquals("https://testingcf.jsdelivr.net/gh/MetaCubeX/meta-rules-dat@sing/geo/geoip/ru.srs", RuleSetCatalog.mirrorUrl(url, 1))
        assertEquals("https://cdn.jsdelivr.net/gh/MetaCubeX/meta-rules-dat@sing/geo/geoip/ru.srs", RuleSetCatalog.mirrorUrl(url, 5))
        assertEquals("https://example.com/a.srs", RuleSetCatalog.mirrorUrl("https://example.com/a.srs", 5))
        assertNull(cat.urlFor("nope"))
    }

    @Test
    fun bundledCatalogIsComplete() {
        val cat = RuleSetCatalog.bundled()
        assertEquals(2209, cat.size)
        assertTrue("geosite-google" in cat || cat.tags.any { it.startsWith("geosite-") })
        assertTrue(cat.tags.all { cat.urlFor(it)!!.startsWith("https://") })
    }

    @Test
    fun ruleSetUpdaterDownloadsKnownTagsOnly() { runBlocking {
        val dir = File(System.getProperty("java.io.tmpdir"), "vm-rs-test-" + System.nanoTime()).apply { mkdirs() }
        try {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setBody("DATA"))
                server.start()
                val cat = RuleSetCatalog(mapOf("geoip-test" to server.url("/geoip-test.srs").toString(), "geoip-fail" to server.url("/x").toString()))
                val up = RuleSetUpdater(SubscriptionClient(), cat, dir)
                val res = up.update(listOf("geoip-test", "../evil", "nope"))
                assertEquals(listOf(true, false, false), res.map { it.ok })
                assertEquals("DATA", File(dir, "geoip-test.srs").readText())
                assertEquals(listOf("geoip-fail"), up.missing(listOf("geoip-test", "geoip-fail")))
                assertFailsWith<IllegalArgumentException> { up.fileFor("../evil") }
            }
        } finally {
            dir.deleteRecursively()
        }
    } }

    @Test
    fun schedulerInterval() {
        assertEquals(30L, SubscriptionScheduler.intervalMinutes(10))
        assertEquals(360L, SubscriptionScheduler.intervalMinutes(360))
    }

    @Test
    fun changeText() {
        val r = UpdateResult(listOf("a"), listOf("b"), listOf("c"), listOf("d"), "", null)
        assertEquals("[+] a\n[~] b\n[-] c\n[=] d", r.changeText())
        assertEquals("Nothing", UpdateResult(emptyList(), emptyList(), emptyList(), emptyList(), "", null).changeText())
    }

    @Test
    fun subscriptionWarnings() {
        val now = 1_000_000_000L
        val day = 24 * 3600L
        assertNull(SubscriptionInfo().warning(now))
        assertNull(SubscriptionInfo(download = 10, total = 100, expire = now + 10 * day).warning(now))
        assertEquals(SubscriptionInfo.Warning.EXPIRED, SubscriptionInfo(expire = now - 1).warning(now))
        assertEquals(SubscriptionInfo.Warning.EXPIRES_SOON, SubscriptionInfo(expire = now + 2 * day).warning(now))
        assertEquals(SubscriptionInfo.Warning.QUOTA_LOW, SubscriptionInfo(upload = 40, download = 50, total = 100).warning(now))
        assertEquals(90, SubscriptionInfo(upload = 40, download = 50, total = 100).usedPercent)
        assertEquals(SubscriptionInfo.Warning.QUOTA_USED_UP, SubscriptionInfo(download = 100, total = 100, expire = now + 2 * day).warning(now))
        assertEquals(SubscriptionInfo.Warning.EXPIRED, SubscriptionInfo(download = 100, total = 100, expire = now).warning(now))
    }
}
