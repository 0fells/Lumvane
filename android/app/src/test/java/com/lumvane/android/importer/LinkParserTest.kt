package com.lumvane.android.importer

import com.lumvane.android.config.common.transportJson
import com.lumvane.android.config.model.AnyTlsBean
import com.lumvane.android.config.model.HttpBean
import com.lumvane.android.config.model.HysteriaBean
import com.lumvane.android.config.model.JuicityBean
import com.lumvane.android.config.model.MieruBean
import com.lumvane.android.config.model.NaiveBean
import com.lumvane.android.config.model.OutboundBean
import com.lumvane.android.config.model.ShadowTlsBean
import com.lumvane.android.config.model.ShadowsocksBean
import com.lumvane.android.config.model.SocksBean
import com.lumvane.android.config.model.SshBean
import com.lumvane.android.config.model.TransportConfig
import com.lumvane.android.config.model.TrojanBean
import com.lumvane.android.config.model.TrustTunnelBean
import com.lumvane.android.config.model.TuicBean
import com.lumvane.android.config.model.VlessBean
import com.lumvane.android.config.model.VmessBean
import com.lumvane.android.config.model.WireGuardBean
import com.lumvane.android.config.model.XrayVlessBean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinkParserTest {
    private val uuid = "11111111-2222-3333-4444-555555555555"

    private inline fun <reified T : OutboundBean> parse(link: String, opts: ParseOptions = ParseOptions()): T {
        val b = LinkParser.parse(link, opts)
        assertNotNull(b, "not parsed: $link")
        return assertIs<T>(b)
    }

    private fun roundTrip(link: String, opts: ParseOptions = ParseOptions()): OutboundBean {
        val b1 = LinkParser.parse(link, opts)
        assertNotNull(b1, "not parsed: $link")
        val exported = LinkExporter.export(b1)
        assertNotNull(exported, "not exported: $b1")
        val b2 = LinkParser.parse(exported, opts)
        assertEquals(b1, b2, "round trip mismatch via $exported")
        return b1
    }

    @Test
    fun vlessRealityGoesToXrayByDefaultAndKeepsFields() {
        val link = "vless://$uuid@192.0.2.10:443?encryption=none&flow=xtls-rprx-vision&security=reality&sni=&pbk=DUMMYPUBLICKEY0123456789&sid=ab12cd&spx=%2F&fp=chrome&type=tcp#Reality%20Node"
        val b = parse<XrayVlessBean>(link)
        assertEquals("Reality Node", b.name)
        assertEquals("192.0.2.10", b.server)
        assertEquals(443, b.serverPort)
        assertEquals("xtls-rprx-vision", b.flow)
        assertEquals("reality", b.streamSetting.security)
        assertEquals("raw", b.streamSetting.network)
        assertEquals("", b.streamSetting.reality.serverName)
        assertEquals("DUMMYPUBLICKEY0123456789", b.streamSetting.reality.password)
        assertEquals("ab12cd", b.streamSetting.reality.shortId)
        assertEquals("/", b.streamSetting.reality.spiderX)
        assertEquals("chrome", b.streamSetting.reality.fingerprint)
        roundTrip(link)
    }

    @Test
    fun vlessRealityAsSingBoxWhenPreferenceIsXhttpOnly() {
        val link = "vless://$uuid@192.0.2.10:443?security=reality&sni=example.com&pbk=DUMMYKEY&sid=ab&fp=firefox&flow=xtls-rprx-vision#r"
        val b = parse<VlessBean>(link, ParseOptions(xrayVlessPreference = 0))
        assertTrue(b.tls.enabled)
        assertTrue(b.tls.reality.enabled)
        assertEquals("DUMMYKEY", b.tls.reality.publicKey)
        assertEquals("ab", b.tls.reality.shortId)
        assertEquals("example.com", b.tls.serverName)
        assertEquals("firefox", b.tls.utls.fingerprint)
        assertTrue(b.tls.utls.enabled)
        assertEquals("xudp", b.packetEncoding)
        roundTrip(link, ParseOptions(0))
    }

    @Test
    fun vlessXrayRawTypeIsPlainTcpForSingBox() {
        val link = "vless://$uuid@192.0.2.10:443?security=tls&sni=example.com&type=raw#r"
        val b = parse<VlessBean>(link, ParseOptions(xrayVlessPreference = 0))
        assertEquals("", b.transport.type)
        assertTrue(transportJson(TransportConfig(type = "raw")).isEmpty())
    }

    @Test
    fun vlessWebSocketTls() {
        val link = "vless://$uuid@example.com:8443?security=tls&sni=cdn.example.com&alpn=h2,http/1.1&type=ws&host=cdn.example.com&path=%2Fws%3Fed%3D2048&fp=chrome&allowInsecure=1#ws"
        val b = parse<VlessBean>(link, ParseOptions(0))
        assertEquals("ws", b.transport.type)
        assertEquals("/ws?ed=2048", b.transport.path)
        assertEquals("cdn.example.com", b.transport.host)
        assertEquals(listOf("h2", "http/1.1"), b.tls.alpn)
        assertTrue(b.tls.insecure)
        assertEquals(8443, b.serverPort)
        roundTrip(link, ParseOptions(0))
    }

    @Test
    fun vlessGrpcAndHttpUpgradeAndH2() {
        val grpc = parse<VlessBean>("vless://$uuid@example.com:443?security=tls&sni=example.com&type=grpc&serviceName=svc#g", ParseOptions(0))
        assertEquals("grpc", grpc.transport.type)
        assertEquals("svc", grpc.transport.serviceName)
        roundTrip("vless://$uuid@example.com:443?security=tls&sni=example.com&type=grpc&serviceName=svc#g", ParseOptions(0))

        val hu = parse<VlessBean>("vless://$uuid@example.com:80?type=httpupgrade&host=example.com&path=%2Fhu#h", ParseOptions(0))
        assertEquals("httpupgrade", hu.transport.type)
        assertEquals("/hu", hu.transport.path)
        assertTrue(!hu.tls.enabled)

        val h2 = parse<VlessBean>("vless://$uuid@example.com:443?security=tls&type=h2&host=example.com&path=%2F#h2", ParseOptions(0))
        assertEquals("http", h2.transport.type)
        assertEquals("GET", h2.transport.method)
    }

    @Test
    fun vlessXhttpWithExtraAndXmux() {
        val extra = """{"xPaddingBytes":"100-1000","noGRPCHeader":true,"scMaxEachPostBytes":1000000,"xmux":{"maxConcurrency":"16-32","hKeepAlivePeriod":30,"cMaxReuseTimes":0},"customUnknown":{"a":1},"headers":{"X-Test":"1"}}"""
        val link = "vless://$uuid@192.0.2.20:443?encryption=none&security=tls&sni=example.com&alpn=h2&fp=chrome&type=xhttp&host=example.com&path=%2Fx&mode=packet-up&extra=" + UrlParts.encode(extra) + "#xh"
        val b = parse<XrayVlessBean>(link)
        val x = b.streamSetting.xhttp
        assertEquals("xhttp", b.streamSetting.network)
        assertEquals("packet-up", x.mode)
        assertEquals("/x", x.path)
        assertEquals("100-1000", x.xPaddingBytes)
        assertTrue(x.noGrpcHeader)
        assertEquals("1000000", x.scMaxEachPostBytes)
        assertEquals("16-32", x.maxConcurrency)
        assertEquals(30L, x.hKeepAlivePeriod)
        assertEquals(listOf("X-Test", "1"), x.headers)
        assertTrue(x.rawExtra.containsKey("customUnknown"))
        assertEquals("tls", b.streamSetting.security)
        assertEquals(listOf("h2"), b.streamSetting.tls.alpn)
        roundTrip(link)
    }

    @Test
    fun vlessExtraAsPythonReprIsAccepted() {
        val extra = "{'xPaddingBytes': '10-20', 'noSSEHeader': True}"
        val b = parse<XrayVlessBean>("vless://$uuid@192.0.2.20:443?type=xhttp&extra=" + UrlParts.encode(extra))
        assertEquals("10-20", b.streamSetting.xhttp.xPaddingBytes)
        assertTrue(b.streamSetting.xhttp.noSseHeader)
    }

    @Test
    fun vlessXrayWsAndGrpcStreams() {
        val ws = parse<XrayVlessBean>("vless://$uuid@192.0.2.20:443?encryption=mlkem768x25519plus.native.0rtt.DUMMY&type=ws&host=example.com&path=%2Fp&ed=2048#w")
        assertEquals("mlkem768x25519plus.native.0rtt.DUMMY", ws.encryption)
        assertEquals(2048, ws.streamSetting.ws.ed)
        roundTrip("vless://$uuid@192.0.2.20:443?encryption=mlkem768x25519plus.native.0rtt.DUMMY&type=ws&host=example.com&path=%2Fp&ed=2048&mux=true&mux_concurrency=4#w")
        val g = parse<XrayVlessBean>("vless://$uuid@192.0.2.20:443?encryption=x25519&type=grpc&serviceName=svc&authority=example.com&mode=multi")
        assertTrue(g.streamSetting.grpc.multiMode)
        assertEquals("example.com", g.streamSetting.grpc.authority)
    }

    @Test
    fun vmessBase64Json() {
        val json = """{"v":"2","ps":"vm test","add":"example.com","port":"443","id":"$uuid","aid":"0","scy":"auto","net":"ws","type":"none","host":"example.com","path":"/ray","tls":"tls","sni":"example.com","alpn":"h2,http/1.1","fp":"chrome"}"""
        val link = "vmess://" + UrlParts.b64Encode(json.toByteArray())
        val b = parse<VmessBean>(link)
        assertEquals("vm test", b.name)
        assertEquals(443, b.serverPort)
        assertEquals("ws", b.transport.type)
        assertEquals("/ray", b.transport.path)
        assertTrue(b.tls.enabled)
        assertEquals("example.com", b.tls.serverName)
        assertEquals(listOf("h2", "http/1.1"), b.tls.alpn)
        assertEquals("chrome", b.tls.utls.fingerprint)
        roundTrip(link)
    }

    @Test
    fun vmessBase64JsonH2AndGrpc() {
        fun link(net: String, path: String) = "vmess://" + UrlParts.b64Encode(
            """{"ps":"x","add":"192.0.2.30","port":8080,"id":"$uuid","aid":1,"net":"$net","host":"example.com","path":"$path","tls":""}""".toByteArray(),
        )
        assertEquals("http", parse<VmessBean>(link("h2", "/")).transport.type)
        val g = parse<VmessBean>(link("grpc", "svcname"))
        assertEquals("svcname", g.transport.serviceName)
        assertEquals(1, g.alterId)
        assertEquals(8080, g.serverPort)
        assertEquals("", parse<VmessBean>(link("tcp", "/")).transport.type)
        assertEquals("", parse<VmessBean>(link("raw", "/")).transport.type)
    }

    @Test
    fun vmessStandardUrlFormat() {
        val link = "vmess://$uuid@192.0.2.31:443?encryption=aes-128-gcm&security=tls&sni=example.com&type=ws&path=%2Fv&alterId=2#std"
        val b = parse<VmessBean>(link)
        assertEquals("aes-128-gcm", b.security)
        assertEquals(2, b.alterId)
        assertEquals("ws", b.transport.type)
        roundTrip(link)
    }

    @Test
    fun trojanWithWebSocket() {
        val link = "trojan://p%40ss@example.com:443?security=tls&sni=example.com&type=ws&host=example.com&path=%2Ftr&alpn=h2#tr"
        val b = parse<TrojanBean>(link)
        assertEquals("p@ss", b.password)
        assertTrue(b.tls.enabled)
        assertEquals("ws", b.transport.type)
        roundTrip(link)
    }

    @Test
    fun trojanWithoutSecurityParamImpliesTls() {
        val b = parse<TrojanBean>("trojan://secret@example.com:443?sni=example.com#t")
        assertTrue(b.tls.enabled)
        val none = parse<TrojanBean>("trojan://secret@example.com:443?security=none#t")
        assertTrue(!none.tls.enabled)
    }

    @Test
    fun shadowsocksSip002AndLegacyAndPlugin() {
        val userinfo = UrlParts.b64UrlEncode("chacha20-ietf-poly1305:pa:ss".toByteArray())
        val a = parse<ShadowsocksBean>("ss://$userinfo@192.0.2.40:8388/?plugin=obfs-local%3Bobfs%3Dhttp%3Bobfs-host%3Dexample.com#ss1")
        assertEquals("chacha20-ietf-poly1305", a.method)
        assertEquals("pa:ss", a.password)
        assertEquals("obfs-local", a.plugin)
        assertEquals("obfs=http;obfs-host=example.com", a.pluginOpts)
        assertEquals("ss1", a.name)
        roundTrip("ss://$userinfo@192.0.2.40:8388/?plugin=obfs-local%3Bobfs%3Dhttp%3Bobfs-host%3Dexample.com#ss1")

        val legacy = "ss://" + UrlParts.b64Encode("aes-256-gcm:secret@192.0.2.41:443".toByteArray()) + "#old"
        val l = parse<ShadowsocksBean>(legacy)
        assertEquals("aes-256-gcm", l.method)
        assertEquals("secret", l.password)
        assertEquals("192.0.2.41", l.server)
        assertEquals(443, l.serverPort)
        assertEquals("old", l.name)
    }

    @Test
    fun shadowsocks2022KeepsPasswordInUserinfo() {
        val link = "ss://2022-blake3-aes-128-gcm:Zm9vYmFyZm9vYmFyMTIzNA%3D%3D@192.0.2.42:8388#s22"
        val b = parse<ShadowsocksBean>(link)
        assertEquals("2022-blake3-aes-128-gcm", b.method)
        assertEquals("Zm9vYmFyZm9vYmFyMTIzNA==", b.password)
        roundTrip(link)
    }

    @Test
    fun hysteria2SalamanderObfsPortHoppingPinAndInsecure() {
        val link = "hysteria2://pass%3Aword@192.0.2.50:443/?obfs=salamander&obfs-password=obfspw&sni=example.com&insecure=1&pinSHA256=AB:CD:EF&mport=20000-30000,40000&upmbps=50&downmbps=100&hop_interval=30s&alpn=h3#hy2"
        val b = parse<HysteriaBean>(link)
        assertEquals("2", b.protocolVersion)
        assertEquals("pass:word", b.password)
        assertEquals("obfspw", b.obfs)
        assertEquals("", b.obfsType)
        assertEquals(listOf("20000:30000", "40000:40000"), b.serverPorts)
        assertEquals(443, b.serverPort)
        assertTrue(b.tls.insecure)
        assertEquals(listOf("AB:CD:EF"), b.tls.certificatePublicKeySha256)
        assertEquals("example.com", b.tls.serverName)
        assertEquals(50, b.upMbps)
        assertEquals("30s", b.hopInterval)
        assertTrue(b.tls.enabled)
        roundTrip(link)
    }

    @Test
    fun hy2SchemeAliasAndPortListInAuthority() {
        val link = "hy2://secret@example.com:20000-30000,443/?sni=example.com#ports"
        val b = parse<HysteriaBean>(link)
        assertEquals("2", b.protocolVersion)
        assertEquals(0, b.serverPort)
        assertEquals(listOf("20000:30000", "443:443"), b.serverPorts)
        assertEquals("example.com", b.server)
        val exported = LinkExporter.export(b)!!
        assertTrue(exported.startsWith("hysteria2://secret@example.com:20000-30000,443-443"), exported)
        assertEquals(b.serverPorts.map { it.replace(":", ":") }.size, LinkParser.parse(exported).let { (it as HysteriaBean).serverPorts.size })
    }

    @Test
    fun hysteria2GeckoObfs() {
        val link = "hysteria2://pw@192.0.2.51:8443/?obfs=gecko&obfs-password=x&obfs-min-packet-size=100&obfs-max-packet-size=200&bbr_profile=standard&sni=example.com"
        val b = parse<HysteriaBean>(link)
        assertEquals("gecko", b.obfsType)
        assertEquals(100, b.obfsMinPacketSize)
        assertEquals(200, b.obfsMaxPacketSize)
        assertEquals("standard", b.bbrProfile)
        roundTrip(link)
    }

    @Test
    fun hysteriaV1() {
        val link = "hysteria://192.0.2.52:5678/?auth=authstr&obfsParam=obfsp&peer=example.com&insecure=1&upmbps=10&downmbps=20&alpn=hysteria&recv_window=1000#h1"
        val b = parse<HysteriaBean>(link)
        assertEquals("1", b.protocolVersion)
        assertEquals("authstr", b.auth)
        assertEquals("STRING", b.authType)
        assertEquals("obfsp", b.obfs)
        assertEquals("example.com", b.tls.serverName)
        assertEquals(1000, b.recvWindow)
        roundTrip(link)
    }

    @Test
    fun tuic() {
        val link = "tuic://$uuid:tuicpass@192.0.2.60:443/?congestion_control=bbr&udp_relay_mode=native&alpn=h3&sni=example.com&zero_rtt_handshake=true&allow_insecure=1#tuic"
        val b = parse<TuicBean>(link)
        assertEquals(uuid, b.uuid)
        assertEquals("tuicpass", b.password)
        assertEquals("bbr", b.congestionControl)
        assertEquals("native", b.udpRelayMode)
        assertTrue(b.zeroRttHandshake)
        assertTrue(b.tls.insecure)
        roundTrip(link)
    }

    @Test
    fun anytls() {
        val link = "anytls://secret@192.0.2.61:443/?sni=example.com&insecure=1&idle_session_timeout=60s&min_idle_session=3#any"
        val b = parse<AnyTlsBean>(link)
        assertEquals("secret", b.password)
        assertEquals("60s", b.idleSessionTimeout)
        assertEquals(3, b.minIdleSession)
        assertTrue(b.tls.insecure)
        roundTrip(link)
    }

    @Test
    fun socksAndHttp() {
        val s = parse<SocksBean>("socks5://user:pw@192.0.2.70:1081#s")
        assertEquals("user", s.username)
        assertEquals("pw", s.password)
        assertEquals(5, s.version)
        assertEquals(4, parse<SocksBean>("socks4://192.0.2.70:1080").version)
        val b64 = UrlParts.b64Encode("bob:secret".toByteArray())
        val v2 = parse<SocksBean>("socks://$b64@192.0.2.70:1080")
        assertEquals("bob", v2.username)
        assertEquals("secret", v2.password)
        roundTrip("socks5://user:pw@192.0.2.70:1081#s")

        val h = parse<HttpBean>("https://user:pw@192.0.2.71:8443/?sni=example.com#h")
        assertTrue(h.tls.enabled)
        assertEquals("pw", h.password)
        roundTrip("https://user:pw@192.0.2.71:8443/?sni=example.com#h")
        val plain = parse<HttpBean>("http://192.0.2.72:8080")
        assertTrue(!plain.tls.enabled)
    }

    @Test
    fun wireguardLinkWithAmnezia() {
        val link = "wg://192.0.2.80:51820/?private_key=DUMMYPRIVATE%3D&public_key=DUMMYPUBLIC%3D&pre_shared_key=DUMMYPSK%3D&local_address=10.0.0.2-fd00::2&reserved=1-2-3&mtu=1280&enable_amnezia=true&jc=4&jmin=40&jmax=70&s1=10&s2=20&h1=1&h2=2&h3=3&h4=4&persistent_keepalive_interval=25#wg"
        val b = parse<WireGuardBean>(link)
        assertEquals("192.0.2.80", b.peer.address)
        assertEquals(51820, b.peer.port)
        assertEquals("DUMMYPRIVATE=", b.privateKey)
        assertEquals(listOf("10.0.0.2/32", "fd00::2/128"), b.address)
        assertEquals(listOf(1, 2, 3), b.peer.reserved)
        assertEquals(1280, b.mtu)
        assertTrue(b.enableAmnezia)
        assertEquals(4, b.jc)
        assertEquals("1", b.h1)
        assertEquals("25", b.peer.persistentKeepalive)
        roundTrip(link)
    }

    @Test
    fun wireguardConfFile() {
        val conf = """
            [Interface]
            PrivateKey = DUMMYPRIVATE=
            Address = 10.7.0.2/32, fd86::2/128
            DNS = 192.0.2.1
            MTU = 1380
            Jc = 5
            Jmin = 50
            Jmax = 1000
            S1 = 30
            S2 = 40
            H1 = 111
            H2 = 222
            H3 = 333
            H4 = 444

            [Peer]
            PublicKey = DUMMYPUBLIC=
            PresharedKey = DUMMYPSK=
            AllowedIPs = 0.0.0.0/0, ::/0
            Endpoint = example.com:51820
            PersistentKeepalive = 25
        """.trimIndent()
        val w = assertNotNull(LinkParser.parseWireGuardConf(conf, "home"))
        assertEquals("home", w.name)
        assertEquals("example.com", w.peer.address)
        assertEquals(51820, w.peer.port)
        assertEquals(listOf("10.7.0.2/32", "fd86::2/128"), w.address)
        assertEquals(1380, w.mtu)
        assertTrue(w.enableAmnezia)
        assertEquals(5, w.jc)
        assertEquals("444", w.h4)
        assertEquals("DUMMYPSK=", w.peer.preSharedKey)
        val again = assertNotNull(LinkParser.parseWireGuardConf(LinkExporter.exportWireGuardConf(w), "home"))
        assertEquals(w, again)
        val v6 = assertNotNull(LinkParser.parseWireGuardConf(conf.replace("example.com:51820", "[2001:db8::1]:51820")))
        assertEquals("2001:db8::1", v6.peer.address)
    }

    @Test
    fun sshNaiveJuicityTrustTunnelShadowTlsMieru() {
        val key = "-----BEGIN DUMMY KEY-----\nabc\n-----END DUMMY KEY-----"
        val sshLink = "ssh://192.0.2.90:2222/?user=root&password=pw&private_key=" + UrlParts.encode(UrlParts.b64Encode(key.toByteArray()).trimEnd('=')) +
            "&client_version=SSH-2.0-Test#ssh"
        val ssh = parse<SshBean>(sshLink)
        assertEquals("root", ssh.user)
        assertEquals(key, ssh.privateKey)
        assertEquals(2222, ssh.serverPort)
        roundTrip(sshLink)
        val sshUserinfo = parse<SshBean>("ssh://bob:pw@192.0.2.90")
        assertEquals("bob", sshUserinfo.user)
        assertEquals(22, sshUserinfo.serverPort)

        val naive = parse<NaiveBean>("naive+quic://u:p@example.com:443/?congestion_control=bbr&uot=1#n")
        assertTrue(naive.quic)
        assertEquals("bbr", naive.congestionControl)
        assertTrue(naive.uot)
        roundTrip("naive+quic://u:p@example.com:443/?congestion_control=bbr&uot=1#n")
        assertTrue(!parse<NaiveBean>("naive+https://u:p@example.com").quic)

        val jui = parse<JuicityBean>("juicity://$uuid:pw@192.0.2.91:443/?sni=example.com#j")
        assertEquals(uuid, jui.uuid)
        roundTrip("juicity://$uuid:pw@192.0.2.91:443/?sni=example.com#j")

        val tt = parse<TrustTunnelBean>("tt://user:pw@192.0.2.92:443/?health_check=true&congestion_control=bbr&sni=example.com#tt")
        assertTrue(tt.healthCheck)
        assertTrue(tt.quic)
        roundTrip("tt://user:pw@192.0.2.92:443/?health_check=true&congestion_control=bbr&sni=example.com#tt")

        val stls = parse<ShadowTlsBean>("shadowtls://:stlspw@192.0.2.93:443/?version=3&sni=example.com#st")
        assertEquals(3, stls.version)
        assertEquals("stlspw", stls.password)
        roundTrip("shadowtls://:stlspw@192.0.2.93:443/?version=3&sni=example.com#st")

        val mieru = parse<MieruBean>("mierus://user:pw@192.0.2.94?profile=default&port=9000&protocol=TCP&port=9010-9020&protocol=TCP&multiplexing=MULTIPLEXING_LOW#mi")
        assertEquals(9000, mieru.serverPort)
        assertEquals("9010-9020", mieru.serverPorts)
        assertEquals("MULTIPLEXING_LOW", mieru.multiplexing)
        roundTrip("mierus://user:pw@192.0.2.94?profile=default&port=9000&protocol=TCP&port=9010-9020&protocol=TCP&multiplexing=MULTIPLEXING_LOW#mi")
        assertNull(LinkParser.parse("mieru://AAAABBBB"))
    }

    @Test
    fun deepLinkAndJsonLink() {
        val json = """{"type":"trojan","tag":"dl","server":"192.0.2.100","server_port":443,"password":"pw","tls":{"enabled":true,"server_name":"example.com"}}"""
        val b = parse<TrojanBean>("lumvane://add/" + UrlParts.b64UrlEncode(json.toByteArray()))
        assertEquals("dl", b.name)
        assertEquals("example.com", b.tls.serverName)
        val hy = """{"type":"hysteria2","tag":"h","server":"192.0.2.101","server_port":443,"password":"pw","obfs":{"type":"salamander","password":"o"}}"""
        val h = parse<HysteriaBean>("json://x#" + UrlParts.b64UrlEncode(hy.toByteArray()))
        assertEquals("2", h.protocolVersion)
        assertEquals("o", h.obfs)
        val xray = """{"protocol":"vless","tag":"xr","settings":{"address":"192.0.2.102","port":443,"id":"$uuid","encryption":"none"}}"""
        assertIs<XrayVlessBean>(LinkParser.parse("lumvane://add/" + UrlParts.b64UrlEncode(xray.toByteArray())))
    }

    @Test
    fun garbageIsRejected() {
        assertNull(LinkParser.parse(""))
        assertNull(LinkParser.parse("hello world"))
        assertNull(LinkParser.parse("vless://"))
        assertNull(LinkParser.parse("vless://@:"))
        assertNull(LinkParser.parse("vmess://!!!!"))
        assertNull(LinkParser.parse("ss://###"))
        assertNull(LinkParser.parse("ftp://example.com"))
    }

    @Test
    fun ipv6HostAndEmojiName() {
        val b = parse<TrojanBean>("trojan://pw@[2001:db8::1]:443?sni=example.com#%F0%9F%87%A9%F0%9F%87%AA%20DE%2001")
        assertEquals("2001:db8::1", b.server)
        assertEquals("🇩🇪 DE 01", b.name)
        roundTrip("trojan://pw@[2001:db8::1]:443?sni=example.com#%F0%9F%87%A9%F0%9F%87%AA%20DE%2001")
    }
}
