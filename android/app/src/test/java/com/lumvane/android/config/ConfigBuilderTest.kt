package com.lumvane.android.config

import com.lumvane.android.config.model.*
import com.lumvane.android.data.AppSettings
import com.lumvane.android.data.PerAppMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigBuilderTest {
    private val uuid = "00000000-0000-4000-8000-000000000001"
    private val settings = AppSettings()

    private class Fixture(val beans: Map<Int, OutboundBean>) {
        var port = 41000
        val builder = ConfigBuilder(
            { beans[it] },
            ConfigOptions(allocatePort = { ++port }, randomSecret = { "SECRET${port}" }, systemDnsServers = listOf("192.0.2.53")),
        )
    }

    private fun build(
        bean: OutboundBean,
        beans: Map<Int, OutboundBean> = emptyMap(),
        settings: AppSettings = this.settings,
        route: RouteInput = RouteInput(),
        profileId: Int = 100,
        landing: Int = -1,
        front: Int = -1,
    ): GeneratedConfig = Fixture(beans).builder.build(profileId, bean, settings, route, landing, front)

    private fun root(c: GeneratedConfig): JsonObject = Json.parseToJsonElement(c.singBoxJson).jsonObject
    private fun JsonObject.arr(k: String): JsonArray = this[k]!!.jsonArray
    private fun JsonObject.obj(k: String): JsonObject = this[k]!!.jsonObject
    private fun JsonObject.str(k: String): String = this[k]!!.jsonPrimitive.content
    private fun JsonObject.strs(k: String): List<String> = arr(k).map { it.jsonPrimitive.content }
    private fun JsonObject.byTag(section: String, tag: String): JsonObject =
        arr(section).map { it.jsonObject }.first { it["tag"]?.jsonPrimitive?.content == tag }

    private fun proxy(c: GeneratedConfig): JsonObject = root(c).byTag("outbounds", "proxy")

    @Test
    fun vlessRealityVisionWithoutSni() {
        val bean = VlessBean(
            server = "192.0.2.10", serverPort = 443, uuid = uuid, flow = "xtls-rprx-vision",
            tls = TlsConfig(
                enabled = true, disableSni = true,
                utls = UTls(true, "firefox"), reality = Reality(true, "DUMMYPUBLICKEY0000000000000000000000000000", "ab12cd34"),
            ),
        )
        val c = build(bean)
        assertNull(c.xray)
        val p = proxy(c)
        assertEquals("vless", p.str("type"))
        assertEquals("192.0.2.10", p.str("server"))
        assertEquals(443, p["server_port"]!!.jsonPrimitive.int)
        assertEquals(uuid, p.str("uuid"))
        assertEquals("xtls-rprx-vision", p.str("flow"))
        assertEquals("xudp", p.str("packet_encoding"))
        assertNull(p["transport"], "tcp = no transport")
        val tls = p.obj("tls")
        assertTrue(tls["enabled"]!!.jsonPrimitive.boolean)
        assertTrue(tls["disable_sni"]!!.jsonPrimitive.boolean)
        assertNull(tls["server_name"], "no SNI")
        assertEquals("firefox", tls.obj("utls").str("fingerprint"))
        assertTrue(tls.obj("utls")["enabled"]!!.jsonPrimitive.boolean)
        assertEquals("ab12cd34", tls.obj("reality").str("short_id"))
        assertTrue(tls.obj("reality").str("public_key").startsWith("DUMMY"))
        assertNull(tls["spoof"])

        val r = root(c)
        assertEquals("proxy", r.obj("route").str("final"))
        assertTrue(r.obj("route")["auto_detect_interface"]!!.jsonPrimitive.boolean)
        val tun = r.byTag("inbounds", "tun-in")
        assertEquals("tun", tun.str("type"))
        assertTrue(tun["auto_route"]!!.jsonPrimitive.boolean)
        assertEquals(1500, tun["mtu"]!!.jsonPrimitive.int)
        assertEquals("system", tun.str("stack"))
        assertEquals(listOf("172.19.0.1/24"), tun.strs("address"))
        assertEquals("172.19.0.1/24", c.tunIpv4Cidr)
        assertTrue(r.arr("inbounds").none { it.jsonObject["tag"]?.jsonPrimitive?.content == "mixed-in" })
        val authed = root(build(bean, settings = settings.copy(inboundAuth = true, inboundUser = "u", inboundPass = "dummy")))
        assertEquals("mixed", authed.byTag("inbounds", "mixed-in").str("type"))
        assertEquals("127.0.0.1", authed.byTag("inbounds", "mixed-in").str("listen"))
        assertEquals(1, authed.byTag("inbounds", "mixed-in").arr("users").size)
        val proxyOnly = root(build(bean, settings = settings.copy(vpnMode = false)))
        assertEquals("mixed", proxyOnly.byTag("inbounds", "mixed-in").str("type"))
        assertEquals("direct", r.byTag("outbounds", "direct").str("type"))
        assertEquals(0, r.arr("endpoints").size)
        val dns = r.obj("dns")
        val remote = dns.arr("servers").map { it.jsonObject }.first { it.str("tag") == "dns-remote" }
        assertEquals("https", remote.str("type"))
        assertEquals("proxy", remote.str("detour"))
        assertEquals("dns-remote", dns.arr("rules").last().jsonObject.str("server"))
    }

    @Test
    fun realityFallsBackToRandomUtlsAndGlobalFingerprint() {
        val tls = TlsConfig(enabled = true, serverName = "example.com", reality = Reality(true, "PUB", ""))
        val p = proxy(build(VlessBean(server = "192.0.2.10", serverPort = 443, uuid = uuid, tls = tls)))
        assertEquals("random", p.obj("tls").obj("utls").str("fingerprint"))
        val p2 = proxy(build(VlessBean(server = "192.0.2.10", serverPort = 443, uuid = uuid, tls = tls), settings = settings.copy(utlsFingerprint = "chrome")))
        assertEquals("chrome", p2.obj("tls").obj("utls").str("fingerprint"))
    }

    @Test
    fun vlessRealityXhttpGoesThroughXray() {
        val bean = XrayVlessBean(
            server = "198.51.100.7", serverPort = 443, uuid = uuid, flow = "",
            streamSetting = XrayStreamSetting(
                network = "xhttp", security = "reality",
                reality = XrayReality(serverName = "example.com", fingerprint = "chrome", password = "DUMMYREALITYKEY", shortId = "0123abcd", spiderX = "/"),
                xhttp = XrayXhttp(
                    host = "example.com", path = "/xh", mode = "auto",
                    maxConcurrency = "16-32", maxConnections = "0", hMaxRequestTimes = "600-900", hKeepAlivePeriod = 30,
                    xPaddingBytes = "100-1000", noGrpcHeader = true,
                ),
            ),
            multiplex = XrayMultiplex(enabled = false, useDefault = true),
        )
        val c = build(bean)
        val r = root(c)
        val p = r.byTag("outbounds", "proxy")
        assertEquals("socks", p.str("type"))
        assertEquals("127.0.0.1", p.str("server"))
        val port = p["server_port"]!!.jsonPrimitive.int
        assertEquals(41001, port)
        assertEquals("SECRET41001", p.str("username"))
        assertEquals(p.str("username"), p.str("password"))
        assertNull(p["tls"])

        val x = assertNotNull(c.xray)
        assertEquals("127.0.0.1:5533", x.outboundDnsAddress)
        assertEquals("UseIP", x.outboundDnsStrategy)
        assertFalse(x.lazyStart)
        assertTrue(x.fullConfigs.isEmpty())
        val xc = Json.parseToJsonElement(x.xrayConfig).jsonObject
        assertEquals("warning", xc.obj("log").str("loglevel"))
        val inb = xc.arr("inbounds").single().jsonObject
        assertEquals("proxy-inbound", inb.str("tag"))
        assertEquals("socks", inb.str("protocol"))
        assertEquals("127.0.0.1", inb.str("listen"))
        assertEquals(port, inb["port"]!!.jsonPrimitive.int)
        assertEquals(p.str("username"), inb.obj("settings").arr("accounts").single().jsonObject.str("user"))
        val rule = xc.obj("routing").arr("rules").single().jsonObject
        assertEquals("proxy-inbound", rule.arr("inboundTag").single().jsonPrimitive.content)
        assertEquals("proxy", rule.str("outboundTag"))

        val out = xc.arr("outbounds").single().jsonObject
        assertEquals("proxy", out.str("tag"))
        assertEquals("vless", out.str("protocol"))
        val s = out.obj("settings")
        assertEquals("198.51.100.7", s.str("address"))
        assertEquals(uuid, s.str("id"))
        assertEquals("none", s.str("encryption"))
        assertNull(s["flow"])
        val ss = out.obj("streamSettings")
        assertEquals("xhttp", ss.str("network"))
        assertEquals("reality", ss.str("security"))
        val rs = ss.obj("realitySettings")
        assertEquals("example.com", rs.str("serverName"))
        assertEquals("chrome", rs.str("fingerprint"))
        assertEquals("DUMMYREALITYKEY", rs.str("password"))
        assertEquals("0123abcd", rs.str("shortId"))
        val xh = ss.obj("xhttpSettings")
        assertEquals("/xh", xh.str("path"))
        assertEquals("auto", xh.str("mode"))
        val extra = xh.obj("extra")
        assertEquals("100-1000", extra.str("xPaddingBytes"))
        assertTrue(extra["noGRPCHeader"]!!.jsonPrimitive.boolean)
        val xmux = extra.obj("xmux")
        assertEquals("16-32", xmux.str("maxConcurrency"))
        assertEquals("600-900", xmux.str("hMaxRequestTimes"))
        assertEquals(30, xmux["hKeepAlivePeriod"]!!.jsonPrimitive.int)
        assertNull(out["mux"])

        val dnsIn = r.byTag("inbounds", "dns-in")
        assertEquals(5533, dnsIn["listen_port"]!!.jsonPrimitive.int)
        val dnsRules = r.obj("dns").arr("rules").map { it.jsonObject }
        assertTrue(dnsRules.any { it["inbound"]?.jsonArray?.single()?.jsonPrimitive?.content == "dns-in" && it.str("server") == "dns-direct" })
        assertTrue(r.obj("route").arr("rules").any { it.jsonObject["inbound"]?.jsonPrimitive?.content == "dns-in" && it.jsonObject.str("action") == "reject" })
    }

    @Test
    fun hysteria2SalamanderUdp443() {
        val bean = HysteriaBean(
            server = "192.0.2.20", serverPort = 443, protocolVersion = "2", password = "dummy-pass", obfs = "dummy-obfs",
            upMbps = 50, downMbps = 200,
            tls = TlsConfig(enabled = true, serverName = "example.com", alpn = listOf("h3"), utls = UTls(true, "chrome")),
        )
        val p = proxy(build(bean))
        assertEquals("hysteria2", p.str("type"))
        assertEquals(443, p["server_port"]!!.jsonPrimitive.int)
        assertNull(p["server_ports"])
        assertEquals("dummy-pass", p.str("password"))
        assertEquals("salamander", p.obj("obfs").str("type"))
        assertEquals("dummy-obfs", p.obj("obfs").str("password"))
        assertEquals(50, p["up_mbps"]!!.jsonPrimitive.int)
        assertEquals("example.com", p.obj("tls").str("server_name"))
        assertEquals(listOf("h3"), p.obj("tls").strs("alpn"))
        assertNull(p.obj("tls")["utls"], "uTLS does not apply to QUIC")
    }

    @Test
    fun hysteria2PortRangeHopping() {
        val bean = HysteriaBean(
            server = "hy.example.com", serverPort = 443, protocolVersion = "2", password = "dummy-pass",
            serverPorts = listOf("20000-30000"), hopInterval = "30", hopIntervalMax = "60s", obfs = "dummy-obfs",
            tls = TlsConfig(enabled = true, serverName = "example.com"),
        )
        val c = build(bean)
        val p = proxy(c)
        assertEquals(listOf("20000:30000"), p.strs("server_ports"))
        assertEquals("30s", p.str("hop_interval"))
        assertEquals("60s", p.str("hop_interval_max"))
        val dnsRules = root(c).obj("dns").arr("rules").map { it.jsonObject }
        val rule = dnsRules.first { (it["domain"] as? JsonArray)?.map { d -> d.jsonPrimitive.content } == listOf("hy.example.com") }
        assertEquals("dns-direct", rule.str("server"))
    }

    @Test
    fun hysteria1() {
        val bean = HysteriaBean(
            server = "192.0.2.21", serverPort = 8443, protocolVersion = "1", authType = "STRING", auth = "dummy-auth", obfs = "o",
            recvWindow = 100, recvWindowConn = 200, disableMtuDiscovery = true,
            tls = TlsConfig(enabled = true, serverName = "example.com"),
        )
        val p = proxy(build(bean))
        assertEquals("hysteria", p.str("type"))
        assertEquals("dummy-auth", p.str("auth_str"))
        assertEquals("o", p.str("obfs"))
        assertEquals(100, p["stream_receive_window"]!!.jsonPrimitive.int)
        assertEquals(200, p["connection_receive_window"]!!.jsonPrimitive.int)
        assertEquals(true, p["disable_path_mtu_discovery"]!!.jsonPrimitive.boolean)
        for (deprecated in listOf("recv_window", "recv_window_conn", "disable_mtu_discovery")) {
            assertFalse(p.containsKey(deprecated), deprecated)
        }
    }

    @Test
    fun trojanOverWebSocket() {
        val bean = TrojanBean(
            server = "trojan.example.com", serverPort = 443, password = "dummy",
            tls = TlsConfig(enabled = true, serverName = "trojan.example.com", alpn = listOf("http/1.1")),
            transport = TransportConfig(type = "ws", host = "cdn.example.com", path = "/ws?ed=2048", headers = listOf("X-Test: 1")),
        )
        val p = proxy(build(bean))
        assertEquals("trojan", p.str("type"))
        assertEquals("dummy", p.str("password"))
        val t = p.obj("transport")
        assertEquals("ws", t.str("type"))
        assertEquals("/ws", t.str("path"))
        assertEquals(2048, t["max_early_data"]!!.jsonPrimitive.int)
        assertEquals("Sec-WebSocket-Protocol", t.str("early_data_header_name"))
        assertEquals("cdn.example.com", t.obj("headers").str("Host"))
        assertEquals("1", t.obj("headers").str("X-Test"))
    }

    @Test
    fun vmessGrpcWithMultiplexDefaults() {
        val bean = VmessBean(
            server = "192.0.2.30", serverPort = 443, uuid = uuid, security = "auto",
            tls = TlsConfig(enabled = true, serverName = "example.com"),
            transport = TransportConfig(type = "grpc", serviceName = "svc"),
            multiplex = MultiplexConfig(enabled = true, unspecified = false),
        )
        val p = proxy(build(bean, settings = settings.copy(muxProtocol = "h2mux", muxConcurrency = 4)))
        assertEquals("vmess", p.str("type"))
        assertNull(p["security"], "auto is the default")
        assertEquals("svc", p.obj("transport").str("service_name"))
        val m = p.obj("multiplex")
        assertEquals("h2mux", m.str("protocol"))
        assertEquals(4, m["max_streams"]!!.jsonPrimitive.int)
    }

    @Test
    fun shadowsocksPluginIsSplit() {
        val bean = ShadowsocksBean(
            server = "192.0.2.40", serverPort = 8388, method = "2022-blake3-aes-128-gcm", password = "ZHVtbXk=",
            plugin = "v2ray-plugin;mode=websocket", uot = true,
        )
        val p = proxy(build(bean))
        assertEquals("shadowsocks", p.str("type"))
        assertEquals("2022-blake3-aes-128-gcm", p.str("method"))
        assertEquals("v2ray-plugin", p.str("plugin"))
        assertEquals("mode=websocket", p.str("plugin_opts"))
        assertTrue(p["udp_over_tcp"]!!.jsonPrimitive.boolean)
        assertNull(p["multiplex"])
    }

    @Test
    fun tuicAndAnyTlsAlwaysCarryTls() {
        val tuic = proxy(build(TuicBean(
            server = "192.0.2.50", serverPort = 443, uuid = uuid, password = "dummy", congestionControl = "bbr", udpRelayMode = "native",
            zeroRttHandshake = true, tls = TlsConfig(serverName = "example.com", alpn = listOf("h3")),
        )))
        assertEquals("tuic", tuic.str("type"))
        assertEquals("bbr", tuic.str("congestion_control"))
        assertEquals("native", tuic.str("udp_relay_mode"))
        assertTrue(tuic["zero_rtt_handshake"]!!.jsonPrimitive.boolean)
        assertTrue(tuic.obj("tls")["enabled"]!!.jsonPrimitive.boolean)

        val any = proxy(build(AnyTlsBean(server = "192.0.2.51", serverPort = 443, password = "dummy", tls = TlsConfig(serverName = "example.com"))))
        assertEquals("anytls", any.str("type"))
        assertEquals("30s", any.str("idle_session_check_interval"))
        assertEquals(5, any["min_idle_session"]!!.jsonPrimitive.int)
        assertEquals("example.com", any.obj("tls").str("server_name"))
    }

    @Test
    fun wireguardIsAnEndpointWithAmnezia() {
        val bean = WireGuardBean(
            privateKey = "DUMMYPRIVATEKEY", address = listOf("10.7.0.2/32"), mtu = 1280, workerCount = 2,
            peer = WireGuardPeer(address = "192.0.2.60", port = 51820, publicKey = "DUMMYPUBLICKEY", reserved = listOf(1, 2, 3), persistentKeepalive = "25"),
            enableAmnezia = true, jc = 4, jmin = 40, jmax = 70, s1 = 10, s2 = 20, h1 = "100", headerProtectionKey = "DUMMYHPK",
        )
        val c = build(bean)
        val r = root(c)
        assertEquals(0, r.arr("outbounds").filter { it.jsonObject["type"]?.jsonPrimitive?.content == "wireguard" }.size)
        val e = r.byTag("endpoints", "proxy")
        assertEquals("wireguard", e.str("type"))
        assertEquals(listOf("10.7.0.2/32"), e.strs("address"))
        assertEquals(1280, e["mtu"]!!.jsonPrimitive.int)
        assertEquals(2, e["workers"]!!.jsonPrimitive.int)
        val peer = e.arr("peers").single().jsonObject
        assertEquals("192.0.2.60", peer.str("address"))
        assertEquals(51820, peer["port"]!!.jsonPrimitive.int)
        assertEquals(25, peer["persistent_keepalive_interval"]!!.jsonPrimitive.int)
        assertEquals(listOf("0.0.0.0/0", "::/0"), peer.strs("allowed_ips"))
        assertEquals(listOf(1, 2, 3), peer.arr("reserved").map { it.jsonPrimitive.int })
        val awg = e.obj("amnezia_wg")
        assertEquals(4, awg["jc"]!!.jsonPrimitive.int)
        assertEquals("100", awg.str("h1"))
        assertEquals("DUMMYHPK", awg.str("header_protection_key"))
        assertNull(awg["s3"])
        val range = bean.copy(peer = bean.peer.copy(persistentKeepalive = "22-30"), enableAmnezia = false)
        val p2 = root(build(range)).byTag("endpoints", "proxy").arr("peers").single().jsonObject
        assertEquals("22-30", (p2["persistent_keepalive_interval"] as JsonPrimitive).content)
    }

    @Test
    fun chainLinksHopsWithDetour() {
        val entry = VlessBean(server = "192.0.2.70", serverPort = 443, uuid = uuid, tls = TlsConfig(enabled = true, serverName = "example.com"))
        val exit = TrojanBean(server = "exit.example.com", serverPort = 443, password = "dummy", tls = TlsConfig(enabled = true, serverName = "exit.example.com"))
        val chain = ChainBean(list = listOf(1, 2))
        val c = build(chain, beans = mapOf(1 to entry, 2 to exit))
        val r = root(c)
        val first = r.byTag("outbounds", "proxy")
        assertEquals("trojan", first.str("type"))
        assertEquals("config-1", first.str("detour"))
        val second = r.byTag("outbounds", "config-1")
        assertEquals("vless", second.str("type"))
        assertNull(second["detour"])
        assertNull(c.xray)
    }

    @Test
    fun chainThroughXrayHop() {
        val xray = XrayVlessBean(
            server = "198.51.100.9", serverPort = 443, uuid = uuid,
            streamSetting = XrayStreamSetting(network = "raw", security = "tls", tls = XrayTls(serverName = "example.com")),
        )
        val exit = TrojanBean(server = "exit.example.com", serverPort = 443, password = "dummy", tls = TlsConfig(enabled = true, serverName = "exit.example.com"))
        val c = build(ChainBean(list = listOf(1, 2)), beans = mapOf(1 to xray, 2 to exit))
        val r = root(c)
        val head = r.byTag("outbounds", "proxy")
        assertEquals("trojan", head.str("type"))
        assertEquals("config-1", head.str("detour"))
        val bridge = r.byTag("outbounds", "config-1")
        assertEquals("socks", bridge.str("type"))
        assertEquals("127.0.0.1", bridge.str("server"))
        val x = assertNotNull(c.xray)
        val xc = Json.parseToJsonElement(x.xrayConfig).jsonObject
        assertEquals(bridge["server_port"]!!.jsonPrimitive.int, xc.arr("inbounds").single().jsonObject["port"]!!.jsonPrimitive.int)
        val xo = xc.arr("outbounds").single().jsonObject
        assertEquals("tls", xo.obj("streamSettings").str("security"))
        assertEquals("example.com", xo.obj("streamSettings").obj("tlsSettings").str("serverName"))
    }

    @Test
    fun chainRules() {
        val a = TrojanBean(server = "a.example.com", serverPort = 443)
        assertFailsWith<ConfigBuildException> { build(ChainBean(list = listOf(1)), beans = mapOf(1 to ChainBean(list = listOf(2)))) }
        assertFailsWith<ConfigBuildException> { build(ChainBean(list = emptyList())) }
        assertFailsWith<ConfigBuildException> { build(ChainBean(list = listOf(1, 9)), beans = mapOf(1 to a)) }
    }

    @Test
    fun autoSelectorBuildsPool() {
        val m1 = TrojanBean(server = "m1.example.com", serverPort = 443, password = "p", tls = TlsConfig(enabled = true))
        val m2 = HysteriaBean(server = "192.0.2.80", serverPort = 443, protocolVersion = "2", password = "p")
        val sel = AutoSelectorBean(name = "auto", pool = listOf(1, 2, 100, 77), pinnedId = 2, intervalSec = 120, balance = true, maxRttMs = 800)
        val c = build(sel, beans = mapOf(1 to m1, 2 to m2), profileId = 100)
        val r = root(c)
        val g = r.byTag("outbounds", "proxy")
        assertEquals("auto-selector", g.str("type"))
        assertEquals(listOf("pool-0-0", "pool-1-0"), g.strs("outbounds"))
        assertEquals("pool-1-0", g.str("pinned"))
        assertEquals("120s", g.str("interval"))
        assertEquals("800ms", g.str("max_rtt"))
        assertTrue(g["balance"]!!.jsonPrimitive.boolean)
        assertEquals("http://cp.cloudflare.com/", g.str("url"))
        assertEquals("trojan", r.byTag("outbounds", "pool-0-0").str("type"))
        assertEquals("hysteria2", r.byTag("outbounds", "pool-1-0").str("type"))
        assertNull(c.xray)
        assertFailsWith<ConfigBuildException> { build(AutoSelectorBean(pool = emptyList())) }
    }

    @Test
    fun autoSelectorWithXrayMemberUsesLazySidecar() {
        val x = XrayVlessBean(server = "198.51.100.5", serverPort = 443, uuid = uuid)
        val c = build(AutoSelectorBean(pool = listOf(1), intervalSec = 300), beans = mapOf(1 to x))
        val p = assertNotNull(c.xray)
        assertTrue(p.lazyStart)
        assertEquals(600, p.idleSeconds)
        assertEquals("socks", root(c).byTag("outbounds", "pool-0-0").str("type"))
    }

    @Test
    fun customBeans() {
        val ob = CustomBean(subtype = CustomBean.OUTBOUND, config = """{"type":"direct","tag":"x"}""")
        assertEquals("direct", proxy(build(ob)).str("type"))
        val full = CustomBean(subtype = CustomBean.FULL_CONFIG, config = """{"outbounds":[{"type":"direct","tag":"d"}]}""")
        val c = build(full)
        assertEquals("d", root(c).arr("outbounds").single().jsonObject.str("tag"))
        assertNull(c.xray)
        assertFailsWith<ConfigBuildException> { build(CustomBean(subtype = CustomBean.FULL_CONFIG, config = "nope")) }

        val xfull = CustomBean(subtype = CustomBean.XRAY_FULL_CONFIG, config = """{"outbounds":[{"protocol":"freedom","tag":"f"}],"inbounds":[{"tag":"mine","port":1,"protocol":"socks"}]}""")
        val c2 = build(xfull)
        val bridge = proxy(c2)
        assertEquals("socks", bridge.str("type"))
        val xc = Json.parseToJsonElement(assertNotNull(c2.xray).xrayConfig).jsonObject
        assertEquals("lumvane-bridge", xc.arr("inbounds").first().jsonObject.str("tag"))
        assertEquals(2, xc.arr("inbounds").size)
        assertEquals(bridge["server_port"]!!.jsonPrimitive.int, xc.arr("inbounds").first().jsonObject["port"]!!.jsonPrimitive.int)
    }

    @Test
    fun everyBeanTypeProducesAnOutbound() {
        val tls = TlsConfig(enabled = true, serverName = "example.com")
        val beans: List<OutboundBean> = listOf(
            SocksBean(server = "192.0.2.1", serverPort = 1080, username = "u", password = "p", uot = true),
            HttpBean(server = "192.0.2.2", serverPort = 8080, tls = tls, headers = listOf("A", "b")),
            ShadowsocksBean(server = "192.0.2.3", serverPort = 1, method = "none"),
            VmessBean(server = "192.0.2.4", serverPort = 1, uuid = uuid),
            VlessBean(server = "192.0.2.5", serverPort = 1, uuid = uuid),
            TrojanBean(server = "192.0.2.6", serverPort = 1, password = "p"),
            HysteriaBean(server = "192.0.2.8", serverPort = 1, protocolVersion = "2"),
            TuicBean(server = "192.0.2.9", serverPort = 1, uuid = uuid),
            JuicityBean(server = "192.0.2.10", serverPort = 1, uuid = uuid),
            TrustTunnelBean(server = "192.0.2.11", serverPort = 1, quic = true, congestionControl = "bbr"),
            NaiveBean(server = "192.0.2.12", serverPort = 1, quic = true),
            AnyTlsBean(server = "192.0.2.13", serverPort = 1),
            ShadowTlsBean(server = "192.0.2.14", serverPort = 1, version = 3, password = "p"),
            MieruBean(server = "192.0.2.15", serverPort = 1, serverPorts = "9000-9010, 9020-9030"),
            SshBean(server = "192.0.2.16", serverPort = 22, user = "u", hostKey = listOf("k")),
            DirectBean(),
        )
        val expectedType = mapOf(
            "socks" to "socks", "http" to "http", "shadowsocks" to "shadowsocks", "vmess" to "vmess", "vless" to "vless", "trojan" to "trojan",
            "hysteria" to "hysteria2", "tuic" to "tuic", "juicity" to "juicity", "trusttunnel" to "trusttunnel", "naive" to "naive",
            "anytls" to "anytls", "shadowtls" to "shadowtls", "mieru" to "mieru", "ssh" to "ssh", "direct" to "direct",
        )
        for (b in beans) {
            val p = proxy(build(b))
            assertEquals(expectedType[b.typeName], p.str("type"), b.typeName)
        }
        val mieru = proxy(build(beans.first { it is MieruBean }))
        assertEquals(listOf("9000-9010", "9020-9030"), mieru.strs("server_ports"))
        assertEquals(mapOf("A" to "b"), proxy(build(beans[1])).obj("headers").mapValues { it.value.jsonPrimitive.content })
        val shadowTls = proxy(build(beans.first { it is ShadowTlsBean }))
        assertEquals(3, shadowTls["version"]!!.jsonPrimitive.int)
    }

    @Test
    fun russiaPresetRouting() {
        val preset = assertNotNull(RoutePresets.byName("Bypass Russia"))
        val bean = TrojanBean(server = "p.example.com", serverPort = 443, password = "x")
        val c = build(bean, route = preset, settings = settings.copy(adblockEnable = true, rulesetMirror = 1))
        val r = root(c)
        val route = r.obj("route")
        assertEquals("proxy", route.str("final"))
        val rules = route.arr("rules").map { it.jsonObject }
        assertTrue(rules.any { it["action"]?.jsonPrimitive?.content == "sniff" })
        val hijack = rules.filter { it["action"]?.jsonPrimitive?.content == "hijack-dns" }
        assertTrue(hijack.isNotEmpty())
        val priv = rules.first { it["ip_is_private"] != null }
        assertEquals("direct", priv.str("outbound"))
        val ru = rules.first { it["rule_set"]?.jsonArray?.any { s -> s.jsonPrimitive.content == "geoip-ru" } == true }
        assertEquals("direct", ru.str("outbound"))
        val adIdx = rules.indexOfFirst { it["rule_set"]?.jsonArray?.single()?.jsonPrimitive?.content == "lumvane-adblocksingbox" }
        val firstRoute = rules.indexOfFirst { it["action"]?.jsonPrimitive?.content == "route" }
        assertTrue(adIdx in 0 until firstRoute)

        val sets = route.arr("rule_set").map { it.jsonObject }
        val geoip = sets.first { it.str("tag") == "geoip-ru" }
        assertEquals("remote", geoip.str("type"))
        assertEquals("binary", geoip.str("format"))
        assertEquals("https://testingcf.jsdelivr.net/gh/MetaCubeX/meta-rules-dat@sing/geo/geoip/ru.srs", geoip.str("url"))
        assertTrue(sets.any { it.str("tag") == "geosite-category-ru" })
        assertTrue(sets.any { it.str("tag") == "lumvane-adblocksingbox" })
        val dnsRule = r.obj("dns").arr("rules").map { it.jsonObject }.first { it["rule_set"] != null }
        assertEquals("dns-direct", dnsRule.str("server"))
        assertTrue("geosite-ru" in dnsRule.strs("rule_set"))
        assertFalse(dnsRule.strs("rule_set").any { it.startsWith("geoip") })

        val c2 = build(bean, route = preset, settings = settings.copy(rulesetMirror = 0))
        val u = root(c2).obj("route").arr("rule_set").map { it.jsonObject }.first { it.str("tag") == "geoip-ru" }.str("url")
        assertEquals("https://raw.githubusercontent.com/MetaCubeX/meta-rules-dat/sing/geo/geoip/ru.srs", u)
    }

    @Test
    fun presetsExistForRuCnIr() {
        assertEquals(5, RoutePresets.all().size)
        for ((name, p) in RoutePresets.all()) {
            for (rs in p.rules.flatMap { it.ruleSet }) assertNotNull(RuleSetCatalog.url(rs), "$name: $rs")
        }
        assertEquals(2209, RuleSetCatalog.size)
    }

    @Test
    fun routeRulesOutboundsAndPackageNames() {
        val extra = TrojanBean(server = "x.example.com", serverPort = 443, password = "p", tls = TlsConfig(enabled = true))
        val rules = listOf(
            RouteRule(name = "apps", processName = listOf("org.example.app"), outboundId = 5),
            RouteRule(name = "blk", domainSuffix = listOf("example.org"), outboundId = OutboundIds.BLOCK),
            RouteRule(name = "d", domain = listOf("d.example.com"), outboundId = OutboundIds.DIRECT, port = listOf("443")),
        )
        val c = build(TrojanBean(server = "192.0.2.1", serverPort = 1), beans = mapOf(5 to extra), route = RouteInput(OutboundIds.DIRECT, rules))
        val r = root(c)
        val rr = r.obj("route").arr("rules").map { it.jsonObject }
        val apps = rr.first { it["package_name"] != null }
        assertEquals("route-0", apps.str("outbound"))
        assertEquals(listOf("org.example.app"), apps.strs("package_name"))
        val blk = rr.first { it["domain_suffix"] != null }
        assertEquals("reject", blk.str("action"))
        assertNull(blk["outbound"])
        assertEquals(443, rr.first { it["port"] != null }.arr("port").single().jsonPrimitive.int)
        assertEquals("direct", r.obj("route").str("final"))
        assertEquals("trojan", r.byTag("outbounds", "route-0").str("type"))
        val domains = r.obj("dns").arr("rules").map { it.jsonObject }.first { (it["domain"] as? JsonArray)?.size == 2 }.strs("domain")
        assertTrue("x.example.com" in domains && "d.example.com" in domains)
        assertFailsWith<ConfigBuildException> { build(extra, route = RouteInput(rules = rules)) }
    }

    @Test
    fun rawRouteTranslatesOutboundIds() {
        val extra = TrojanBean(server = "x.example.com", serverPort = 443, password = "p")
        val raw = """{"rules":[{"domain":["a.example.com"],"action":"route","outbound":7},{"domain":["b.example.com"],"action":"route","outbound":-2}],"final":-1,"rule_set":[{"type":"local","tag":"mine","format":"binary","path":"/x.srs"}]}"""
        val c = build(TrojanBean(server = "192.0.2.1", serverPort = 1), beans = mapOf(7 to extra), route = RouteInput(isRaw = true, rawRoute = raw))
        val route = root(c).obj("route")
        val rules = route.arr("rules").map { it.jsonObject }
        assertEquals("route-0", rules.first { it["domain"]?.jsonArray?.single()?.jsonPrimitive?.content == "a.example.com" }.str("outbound"))
        assertEquals("direct", rules.first { it["domain"]?.jsonArray?.single()?.jsonPrimitive?.content == "b.example.com" }.str("outbound"))
        assertEquals("proxy", route.str("final"))
        assertEquals("mine", route.arr("rule_set").single().jsonObject.str("tag"))
        val c2 = build(TrojanBean(server = "192.0.2.1", serverPort = 1), beans = mapOf(7 to extra), route = RouteInput(isRaw = true, rawRoute = raw, preventModifications = true))
        assertEquals(2, root(c2).obj("route").arr("rules").size)
    }

    @Test
    fun androidInboundsFollowSettings() {
        val s = settings.copy(
            allowLan = true, inboundSocksPort = 7890, inboundAuth = true, inboundUser = "u", inboundPass = "p",
            vpnMtu = 1400, vpnStrictRoute = true, vpnIpv6 = true, vpnImplementation = "mixed",
            perAppMode = PerAppMode.DENY_LISTED, perAppPackages = listOf("org.example.bank"),
        )
        val r = root(build(TrojanBean(server = "192.0.2.1", serverPort = 1), settings = s))
        val mixed = r.byTag("inbounds", "mixed-in")
        assertEquals("0.0.0.0", mixed.str("listen"))
        assertEquals(7890, mixed["listen_port"]!!.jsonPrimitive.int)
        assertEquals("u", mixed.arr("users").single().jsonObject.str("username"))
        val tun = r.byTag("inbounds", "tun-in")
        assertEquals(1400, tun["mtu"]!!.jsonPrimitive.int)
        assertEquals("mixed", tun.str("stack"))
        assertTrue(tun["strict_route"]!!.jsonPrimitive.boolean)
        assertEquals(2, tun.strs("address").size)
        assertEquals(listOf("org.example.bank"), tun.strs("exclude_package"))
        assertNull(tun["include_package"])
        assertTrue("10.0.0.0/8" in tun.strs("route_exclude_address"))
        assertFalse(r.obj("dns").arr("rules").any { it.jsonObject["inbound"]?.jsonArray?.singleOrNull()?.jsonPrimitive?.content == "tun-in" })

        val proxyOnly = root(build(TrojanBean(server = "192.0.2.1", serverPort = 1), settings = settings.copy(vpnMode = false)))
        assertTrue(proxyOnly.arr("inbounds").none { it.jsonObject.str("type") == "tun" })
        val allow = root(build(TrojanBean(server = "192.0.2.1", serverPort = 1), settings = settings.copy(perAppMode = PerAppMode.ALLOW_LISTED, perAppPackages = listOf("a.b"))))
        assertEquals(listOf("a.b"), allow.byTag("inbounds", "tun-in").strs("include_package"))
    }

    @Test
    fun perAppOffIgnoresPackages() {
        val r = root(build(TrojanBean(server = "192.0.2.1", serverPort = 1), settings = settings.copy(perAppMode = PerAppMode.OFF, perAppPackages = listOf("a.b"))))
        val tun = r.byTag("inbounds", "tun-in")
        assertNull(tun["include_package"])
        assertNull(tun["exclude_package"])
    }

    @Test
    fun downloadedRuleSetIsLocalOthersRemote() {
        val dir = kotlin.io.path.createTempDirectory("vmcfg").toFile()
        try {
            java.io.File(dir, "ruleset").mkdirs()
            java.io.File(dir, "ruleset/geoip-ru.srs").writeBytes(byteArrayOf(1))
            val preset = assertNotNull(RoutePresets.byName("Bypass Russia"))
            val bean = TrojanBean(server = "p.example.com", serverPort = 443, password = "x")
            val builder = ConfigBuilder({ null }, ConfigOptions(dataDir = dir.absolutePath, allocatePort = { 41001 }, randomSecret = { "S" }))
            val sets = root(builder.build(100, bean, settings, preset, -1, -1)).obj("route").arr("rule_set").map { it.jsonObject }
            val local = sets.first { it.str("tag") == "geoip-ru" }
            assertEquals("local", local.str("type"))
            assertEquals(java.io.File(dir, "ruleset/geoip-ru.srs").absolutePath, local.str("path"))
            assertNull(local["url"])
            val remote = sets.first { it.str("tag") == "geosite-ru" }
            assertEquals("remote", remote.str("type"))
            assertTrue(remote.str("url").startsWith("https://testingcf.jsdelivr.net/gh/"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun localDnsMapsToNetworkDns() {
        val r = root(build(TrojanBean(server = "192.0.2.1", serverPort = 1)))
        val servers = r.obj("dns").arr("servers").map { it.jsonObject }
        val direct = servers.first { it.str("tag") == "dns-direct" }
        assertEquals("udp", direct.str("type"))
        assertEquals("192.0.2.53", direct.str("server"))
        assertEquals("dns-local", direct.str("domain_resolver"))
        assertEquals("192.0.2.53", servers.first { it.str("tag") == "dns-local" }.str("server"))
        assertTrue(r.obj("dns").arr("rules").any { it.jsonObject["inbound"]?.jsonArray?.singleOrNull()?.jsonPrimitive?.content == "tun-in" })
    }

    @Test
    fun fakeIpAndStrategies() {
        val s = settings.copy(fakeDns = true, remoteDns = "tls://1.1.1.1", remoteDnsStrategy = "ipv4_only", dnsFinalOut = "remote")
        val dns = root(build(VlessBean(server = "192.0.2.1", serverPort = 1), settings = s)).obj("dns")
        val servers = dns.arr("servers").map { it.jsonObject }
        assertEquals("fakeip", servers.first { it.str("tag") == "dns-fake" }.str("type"))
        assertEquals("tls", servers.first { it.str("tag") == "dns-remote" }.str("type"))
        val last = dns.arr("rules").map { it.jsonObject }.takeLast(2)
        assertEquals("AAAA", last[0].str("query_type"))
        assertEquals("predefined", last[0].str("action"))
        assertEquals("dns-remote", last[1].str("server"))
    }

    @Test
    fun warpWrapsTheProxy() {
        val s = settings.copy(
            enableWarp = true, warpPrivateKey = "DUMMYPRIV", warpPublicKey = "DUMMYPUB", warpEp = "192.0.2.90:2408",
            warpIfcAddrs = listOf("172.16.0.2/32"), warpReserved = listOf("1", "2", "3"),
        )
        val r = root(build(TrojanBean(server = "192.0.2.1", serverPort = 443, password = "p"), settings = s))
        val warp = r.byTag("endpoints", "proxy")
        assertEquals("wireguard", warp.str("type"))
        assertEquals("warp-bypass", warp.str("detour"))
        assertEquals("trojan", r.byTag("outbounds", "warp-bypass").str("type"))
        assertFailsWith<ConfigBuildException> { build(TrojanBean(server = "192.0.2.1", serverPort = 443), settings = s.copy(warpPrivateKey = "")) }
    }

    @Test
    fun tailscaleIsEndpointWithMagicDns() {
        val bean = TailscaleBean(authKey = "tskey-dummy", hostname = "phone", globalDns = true)
        val r = root(build(bean, settings = settings.copy(), ))
        val e = r.byTag("endpoints", "proxy")
        assertEquals("tailscale", e.str("type"))
        assertEquals("tskey-dummy", e.str("auth_key"))
        val ts = r.obj("dns").arr("servers").map { it.jsonObject }.first { it.str("tag") == "dns-tailscale" }
        assertTrue(ts["accept_default_resolvers"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun experimentalAndLog() {
        val r = root(build(TrojanBean(server = "192.0.2.1", serverPort = 1), settings = settings.copy(logLevel = "warn", coreBoxClashApi = 9090)))
        assertEquals("warn", r.obj("log").str("level"))
        assertEquals("127.0.0.1:9090", r.obj("experimental").obj("clash_api").str("external_controller"))
        assertTrue(r.obj("experimental").obj("cache_file")["enabled"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun globalFragmentAndMuxDefaults() {
        val s = settings.copy(fragmentDefaultOn = true, muxDefaultOn = true, muxProtocol = "yamux", muxConcurrency = 6)
        val bean = TrojanBean(server = "192.0.2.1", serverPort = 443, tls = TlsConfig(enabled = true, serverName = "example.com"))
        val p = proxy(build(bean, settings = s))
        assertTrue(p.obj("tls")["fragment"]!!.jsonPrimitive.boolean)
        assertEquals("yamux", p.obj("multiplex").str("protocol"))
        assertEquals(6, p.obj("multiplex")["max_streams"]!!.jsonPrimitive.int)
        val custom = proxy(build(bean.copy(dial = DialFields(tcpFastOpen = true)), settings = s.copy(fragmentImplementation = "custom")))
        assertNull(custom.obj("tls")["fragment"])
        assertEquals("10-100", custom.obj("tls_fragment").str("size"))
        assertNull(custom["tcp_fast_open"])
    }
}
