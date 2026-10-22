package com.lumvane.android.importer

import com.google.zxing.common.BitMatrix
import com.lumvane.android.config.model.ChainBean
import com.lumvane.android.config.model.CustomBean
import com.lumvane.android.config.model.HysteriaBean
import com.lumvane.android.config.model.ShadowsocksBean
import com.lumvane.android.config.model.TrojanBean
import com.lumvane.android.config.model.VlessBean
import com.lumvane.android.config.model.WireGuardBean
import com.lumvane.android.config.model.XrayVlessBean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MiscImporterTest {
    @Test
    fun urlParts() {
        val u = assertNotNull(UrlParts.parse("scheme://us%3Aer:p%40ss@[2001:db8::2]:8443/path?a=1&b=x%20y&a=2&flag#frag%20ment"))
        assertEquals("scheme", u.scheme)
        assertEquals("us:er", u.user)
        assertEquals("p@ss", u.password)
        assertEquals("2001:db8::2", u.host)
        assertEquals(8443, u.port)
        assertEquals("2", u.get("a"))
        assertEquals(listOf("1", "2"), u.all("a"))
        assertEquals("x y", u.get("b"))
        assertEquals("", u.get("flag"))
        assertEquals("frag ment", u.fragment)
        assertNull(UrlParts.parse("no-scheme"))
        assertEquals(0, assertNotNull(UrlParts.parse("x://h:99999")).port)
        assertEquals("a b\u00e9", UrlParts.percentDecode("a%20b%C3%A9"))
        assertEquals("%25%20", UrlParts.encode("% "))
    }

    @Test
    fun base64Variants() {
        val raw = "any carnal pleas".toByteArray()
        listOf("YW55IGNhcm5hbCBwbGVhcw==", "YW55IGNhcm5hbCBwbGVhcw", "YW55IGNhcm5hbCBwbGVhcw==\n").forEach {
            assertEquals(String(raw), UrlParts.b64DecodeToString(it))
        }
        assertNull(UrlParts.b64Decode("not base64!"))
        assertNull(UrlParts.b64Decode("A"))
        assertEquals(listOf(0xfb.toByte(), 0xff.toByte()), UrlParts.b64Decode("-_8")!!.toList())
    }

    private fun toArgb(m: BitMatrix, scale: Int = 4, margin: Int = 16): Triple<IntArray, Int, Int> {
        val w = m.width * scale + margin * 2
        val h = m.height * scale + margin * 2
        val px = IntArray(w * h) { 0xFFFFFFFF.toInt() }
        for (x in 0 until m.width) for (y in 0 until m.height) if (m[x, y]) {
            for (dx in 0 until scale) for (dy in 0 until scale) px[(y * scale + dy + margin) * w + x * scale + dx + margin] = 0xFF000000.toInt()
        }
        return Triple(px, w, h)
    }

    @Test
    fun qrEncodeDecodeRoundTrip() {
        val link = "vless://11111111-2222-3333-4444-555555555555@192.0.2.1:443?security=tls&sni=example.com#QR%20test"
        val (px, w, h) = toArgb(QrCodes.encode(link, 100))
        assertEquals(link, QrCodes.decode(px, w, h))
        val inv = IntArray(px.size) { if (px[it] == 0xFFFFFFFF.toInt()) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        assertEquals(link, QrCodes.decode(inv, w, h))
        assertNull(QrCodes.decode(IntArray(64 * 64) { 0xFFFFFFFF.toInt() }, 64, 64))
        val bean = LinkParser.parse(QrCodes.decode(px, w, h)!!)
        assertIs<VlessBean>(bean).also { assertEquals("QR test", it.name) }
    }

    @Test
    fun transferFileRoundTripSkipsChainAndAutoSelector() {
        val trojan = TrojanBean(name = "t", server = "192.0.2.1", serverPort = 443, password = "pw")
        val hy = HysteriaBean(name = "h", server = "192.0.2.2", serverPort = 443, protocolVersion = "2", password = "pw", obfs = "o")
        val file = TransferFile(
            groups = listOf(
                TransferGroup("Group A", url = "https://example.com/sub", info = "upload=1", profiles = listOf(
                    TransferProfile("t", trojan), TransferProfile("h", hy), TransferProfile("c", ChainBean("c", listOf(1, 2))),
                )),
                TransferGroup("Empty"),
            ),
        )
        val json = ProfileTransfer.encode(file)
        assertTrue("\"type\": \"trojan\"" in json && "lumvane-android" in json)
        val back = assertNotNull(ProfileTransfer.decode(json))
        assertEquals(2, back.groups.size)
        assertEquals(listOf<Any>(trojan, hy, ChainBean("c", listOf(1, 2))), back.groups[0].profiles.map { it.bean })
        assertEquals("https://example.com/sub", back.groups[0].url)
        assertNull(ProfileTransfer.decode("{\"format\":\"other\",\"groups\":[]}"))
        assertNull(ProfileTransfer.decode("garbage"))
    }

    @Test
    fun windowsDbConversion() {
        val uuid = "11111111-2222-3333-4444-555555555555"
        val groups = listOf(
            WinGroup(1, "Default", "", "", false, false, false, 0, listOf(3, 1, 2)),
            WinGroup(2, "Sub", "https://example.com/s", "upload=1", false, true, false, 123, emptyList()),
        )
        val profiles = listOf(
            WinProfile(1, "vless", "first", 1, """{"type":"vless","tag":"first","server":"192.0.2.1","server_port":443,"uuid":"$uuid","packet_encoding":"xudp","tls":{"enabled":true,"server_name":"example.com"}}"""),
            WinProfile(2, "xrayvless", "second", 1, """{"tag":"second","protocol":"vless","settings":{"address":"192.0.2.2","port":443,"id":"$uuid","encryption":"none"},"streamSettings":{"network":"ws","security":"none","wsSettings":{"path":"/p?ed=2048","host":"example.com"}}}"""),
            WinProfile(3, "hysteria", "third", 1, """{"type":"hysteria2","tag":"third","server":"192.0.2.3","server_port":443,"password":"pw","tls":{"enabled":true}}"""),
            WinProfile(4, "chain", "chain", 2, """{"name":"chain","list":[1,2]}"""),
            WinProfile(5, "custom", "cust", 2, """{"name":"cust","type":"custom","subtype":"outbound","config":"{\"type\":\"direct\"}"}"""),
            WinProfile(6, "wireguard", "wg", 9, """{"type":"wireguard","tag":"wg","private_key":"P=","address":["10.0.0.2/32"],"peers":[{"address":"192.0.2.9","port":51820,"public_key":"K="}]}"""),
            WinProfile(7, "vless", "broken", 2, "not json"),
        )
        val c = WindowsDbImport.convert(groups, profiles)
        assertEquals(3, c.groups.size)
        assertEquals(listOf("third", "first", "second"), c.groups[0].second.map { it.name })
        assertIs<VlessBean>(c.groups[0].second[1].bean)
        val x = assertIs<XrayVlessBean>(c.groups[0].second[2].bean)
        assertEquals(2048, x.streamSetting.ws.ed)
        assertEquals("/p", x.streamSetting.ws.path)
        assertEquals(listOf("chain", "cust"), c.groups[1].second.map { it.name })
        assertEquals(ChainBean("chain", listOf(1, 2)), c.groups[1].second[0].bean)
        assertEquals(4L, c.groups[1].second[0].sourceId)
        assertIs<CustomBean>(c.groups[1].second[1].bean)
        assertEquals("Sub", c.groups[1].first.name)
        assertEquals("Imported", c.groups[2].first.name)
        assertIs<WireGuardBean>(c.groups[2].second.single().bean)
        assertEquals(mapOf("vless" to 1), c.skipped)
        assertEquals(6, c.profileCount)
    }

    @Test
    fun linkExportOfUnsupportedBeansIsNull() {
        assertNull(LinkExporter.export(ChainBean("c", listOf(1))))
        assertNull(LinkExporter.export(CustomBean("c", CustomBean.OUTBOUND, "{}")))
        val ss = ShadowsocksBean(name = "n a", server = "192.0.2.1", serverPort = 8388, method = "aes-256-gcm", password = "pw")
        assertEquals(ss, LinkParser.parse(LinkExporter.export(ss)!!))
        assertEquals("n a", LinkExporter.export(ss, "n a")?.substringAfter('#')?.let { UrlParts.percentDecode(it) })
    }
}
