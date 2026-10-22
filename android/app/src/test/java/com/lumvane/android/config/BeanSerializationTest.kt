package com.lumvane.android.config

import com.lumvane.android.config.model.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BeanSerializationTest {
    private val tls = TlsConfig(
        enabled = true, serverName = "example.com", insecure = true, alpn = listOf("h2", "http/1.1"),
        fragment = true, fragmentUnspecified = false, tlsTricks = true, tlsTricksUnspecified = false,
        ech = Ech(true, listOf("cfg"), "", "ech.example.com"),
        utls = UTls(true, "chrome"),
        reality = Reality(true, "pubkey", "abcd"),
    )
    private val mux = MultiplexConfig(
        enabled = true, unspecified = false, protocol = "h2mux", maxConnections = 4, padding = true,
        brutal = TcpBrutal(true, 50, 100),
    )
    private val dial = DialFields(reuseAddr = true, connectTimeout = "5s", tcpFastOpen = true, bindInterface = "wlan0")

    private val beans: List<OutboundBean> = listOf(
        SocksBean("s", "1.2.3.4", 1080, dial, "u", "p", 5, true),
        HttpBean("h", "h.example.com", 8080, dial, "u", "p", "/x", listOf("A: b"), tls),
        ShadowsocksBean("ss", "ss.example.com", 8388, dial, "2022-blake3-aes-128-gcm", "pw", "obfs-local", "obfs=tls", true, mux),
        VmessBean(
            "vm", "vm.example.com", 443, dial, "uuid", "aes-128-gcm", 0, true, true, tls, "xudp",
            TransportConfig(type = "ws", host = "h", path = "/ws", headers = listOf("X: y"), maxEarlyData = 2048, earlyDataHeaderName = "Sec-WebSocket-Protocol"),
            mux,
        ),
        VlessBean(
            "vl", "vl.example.com", 443, dial, "uuid", "xtls-rprx-vision", tls, "xudp", mux,
            TransportConfig(type = "grpc", serviceName = "svc", idleTimeout = "15s", pingTimeout = "15s"),
        ),
        VlessBean("vl-http", "x", 1, transport = TransportConfig(type = "httpupgrade", host = "h", path = "/u")),
        TrojanBean("tj", "tj.example.com", 443, dial, "pw", tls, mux, TransportConfig(type = "http", method = "GET")),
        XrayVlessBean(
            "xv", "xv.example.com", 443, dial, "uuid", "none", "xtls-rprx-vision-udp443",
            XrayStreamSetting(
                network = "xhttp", security = "reality",
                tls = XrayTls("sni", "sha", "name", listOf("h2"), "chrome"),
                reality = XrayReality("sni", "chrome", "pbk", "sid", "/spider"),
                xhttp = XrayXhttp(
                    host = "h", path = "/p", mode = "stream-up",
                    rawExtra = buildJsonObject { put("k", 1) }, headers = listOf("A: b"),
                    xPaddingBytes = "100-1000", xPaddingObfsMode = true, scMaxBufferedPosts = 30, serverMaxHeaderBytes = 8192,
                    rawXmux = buildJsonObject { put("maxConcurrency", "16-32") },
                    maxConcurrency = "16-32", hKeepAlivePeriod = 60,
                ),
                ws = XrayWs("/ws", "h", 2048, listOf("A: b"), 30),
                httpUpgrade = XrayHttpUpgrade("/u", 0, "h", emptyList()),
                grpc = XrayGrpc("auth", "svc", true),
            ),
            XrayMultiplex(true, false, 8, 16),
        ),
        HysteriaBean(
            "hy1", "hy.example.com", 443, dial, protocolVersion = "1", upMbps = 50, downMbps = 100, obfs = "o",
            authType = "str", auth = "a", recvWindowConn = 1, recvWindow = 2, disableMtuDiscovery = true, tls = tls,
        ),
        HysteriaBean(
            "hy2", "hy.example.com", 443, dial, protocolVersion = "2",
            serverPorts = listOf("20000:30000", "40000:40100"), hopInterval = "30s", hopIntervalMax = "60s",
            password = "pw", obfs = "salamander-pw", obfsType = "gecko", obfsMinPacketSize = 10, obfsMaxPacketSize = 99,
            bbrProfile = "aggressive", disableChromeParrot = true, tls = tls,
        ),
        TuicBean("tu", "tu.example.com", 443, dial, "uuid", "pw", "bbr", "native", true, true, "10s", tls),
        JuicityBean("ju", "ju.example.com", 443, dial, "uuid", "pw", tls),
        TrustTunnelBean("tt", "tt.example.com", 443, dial, "u", "p", "bbr", true, true, tls),
        NaiveBean("nv", "nv.example.com", 443, dial, "u", "p", "bbr", true, true, tls),
        AnyTlsBean("at", "at.example.com", 443, dial, "pw", "10s", "20s", 3, tls),
        ShadowTlsBean("st", "st.example.com", 443, dial, 3, "pw", tls),
        MieruBean("mi", "mi.example.com", 9000, dial, "UDP", "u", "p", "MULTIPLEXING_HIGH", "pattern", "9000-9010,9020-9030"),
        SshBean("ssh", "ssh.example.com", 22, dial, "root", "pw", "KEY", "/k", "pp", listOf("hk"), listOf("ssh-ed25519"), "SSH-2.0-x"),
        WireGuardBean(
            "wg", dial, "priv", WireGuardPeer("wg.example.com", 51820, "pub", "psk", listOf(1, 2, 3), "22-30"),
            listOf("10.0.0.2/32", "fd00::2/128"), 1280, true, 4, "30s",
            true, 5, 50, 1000, 10, 20, 30, 40, "h1", "h2", "h3", "h4", "i1", "i2", "i3", "i4", "i5",
            "hpk", "10-20", "120s", "5s", "180s", "10s", "5",
        ),
        TailscaleBean(
            "ts", dial, "/state", "key", "https://ctl", true, "host", true, "exit", true,
            listOf("10.0.0.0/8"), true, true,
        ),
        DirectBean("d", dial),
        ChainBean("c", listOf(3, 1, 2)),
        CustomBean("cu", CustomBean.XRAY_FULL_CONFIG, "{\"a\":1}"),
        AutoSelectorBean(
            name = "as", gid = 2, nameFilter = "^x", countryFilter = "US,DE", testUrl = "http://t/", balance = true,
            balanceMode = "connection", pool = listOf(1, 2), poolRankedAt = 99, pinnedId = 5, lastBuilt = listOf(2),
            lastBuiltAt = 100,
            history = JsonArray(listOf(buildJsonObject { put("id", 1); put("name", "n") })),
        ),
    )

    @Test
    fun everyBeanRoundTrips() {
        for (bean in beans) {
            val text = BeanJson.encode(bean)
            val back = BeanJson.decode(text)
            assertEquals(bean, back, "round trip failed for ${bean.typeName}: $text")
            assertTrue("\"type\":\"${bean.typeName}\"" in text, "discriminator in $text")
        }
    }

    @Test
    fun defaultsRoundTrip() {
        val defaults = listOf(
            SocksBean(), HttpBean(), ShadowsocksBean(), VmessBean(), VlessBean(), TrojanBean(), XrayVlessBean(),
            HysteriaBean(), TuicBean(), JuicityBean(), TrustTunnelBean(), NaiveBean(), AnyTlsBean(), ShadowTlsBean(),
            MieruBean(), SshBean(), WireGuardBean(), TailscaleBean(), DirectBean(), ChainBean(), CustomBean(), AutoSelectorBean(),
        )
        assertEquals(22, defaults.map { it.typeName }.toSet().size)
        for (b in defaults) assertEquals(b, BeanJson.decode(BeanJson.encode(b)))
    }

    @Test
    fun allTypesCovered() {
        val covered = beans.map { it.typeName }.toSet()
        val expected = setOf(
            "socks", "http", "shadowsocks", "vmess", "vless", "trojan", "xrayvless", "hysteria", "tuic", "juicity",
            "trusttunnel", "naive", "anytls", "shadowtls", "mieru", "ssh", "wireguard", "tailscale", "direct",
            "chain", "custom", "autoselector",
        )
        assertEquals(expected, covered)
    }

    @Test
    fun hysteriaPortHoppingSurvives() {
        val b = BeanJson.decode(BeanJson.encode(beans.first { it is HysteriaBean && it.isV2 })) as HysteriaBean
        assertEquals(listOf("20000:30000", "40000:40100"), b.serverPorts)
        assertEquals("gecko", b.obfsType)
    }

    @Test
    fun missingFieldsUseDefaultsAndUnknownIgnored() {
        val b = BeanJson.decode("""{"type":"vless","server":"a","serverPort":443,"uuid":"u","future_field":1}""")
        assertIs<VlessBean>(b)
        assertEquals("xudp", b.packetEncoding)
        assertTrue(b.multiplex.unspecified)
    }

    @Test
    fun unknownTypeIsNull() {
        assertNull(BeanJson.decodeOrNull("""{"type":"nope"}"""))
        assertNull(BeanJson.decodeOrNull("garbage"))
    }

    @Test
    fun endpointAndTypes() {
        assertEquals("1.2.3.4" to 1080, (beans[0] as SocksBean).let { it.endpoint })
        assertEquals("wg.example.com" to 51820, beans.first { it is WireGuardBean }.endpoint)
        assertNull(ChainBean().endpoint)
    }

    @Test
    fun routeRuleRoundTrip() {
        val r = RouteRule(
            name = "r", type = RuleType.SIMPLE_ADDRESS_BYPASS, domainSuffix = listOf("ru"), ipCidr = listOf("10.0.0.0/8"),
            processName = listOf("com.app"), outboundId = OutboundIds.BLOCK, action = "reject", rejectMethod = "drop",
        )
        val s = BeanJson.json.encodeToString(RouteRule.serializer(), r)
        assertEquals(r, BeanJson.json.decodeFromString(RouteRule.serializer(), s))
    }

    @Suppress("unused")
    private val keepImports: List<Any> = listOf(JsonObject(emptyMap()), JsonPrimitive(1))
}
