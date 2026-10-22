package com.lumvane.android.importer

import com.lumvane.android.config.model.CustomBean
import com.lumvane.android.config.model.HysteriaBean
import com.lumvane.android.config.model.ShadowsocksBean
import com.lumvane.android.config.model.TrojanBean
import com.lumvane.android.config.model.TuicBean
import com.lumvane.android.config.model.VlessBean
import com.lumvane.android.config.model.VmessBean
import com.lumvane.android.config.model.WireGuardBean
import com.lumvane.android.config.model.XrayVlessBean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ContentParserTest {
    private val uuid = "11111111-2222-3333-4444-555555555555"
    private val vless = "vless://$uuid@192.0.2.1:443?security=tls&sni=example.com#one"
    private val trojan = "trojan://pw@192.0.2.2:443?sni=example.com#two"
    private val ss = "ss://" + UrlParts.b64UrlEncode("aes-128-gcm:pw".toByteArray()) + "@192.0.2.3:8388#three"

    @Test
    fun multiLineTextWithCommentsAndGarbage() {
        val text = "# my servers\n$vless\n\n// skipped\r\n$trojan\nnot a link\n$ss\n"
        val r = ContentParser.parse(text)
        assertEquals(listOf("one", "two", "three"), r.profiles.map { it.name })
        assertEquals(1, r.skipped)
        assertIs<VlessBean>(r.profiles[0].bean)
        assertIs<TrojanBean>(r.profiles[1].bean)
        assertIs<ShadowsocksBean>(r.profiles[2].bean)
    }

    @Test
    fun base64Subscription() {
        val body = UrlParts.b64Encode("$vless\n$trojan\n$ss".toByteArray())
        val r = ContentParser.parse(body)
        assertEquals(3, r.profiles.size)
        val wrapped = body.chunked(40).joinToString("\r\n")
        assertEquals(3, ContentParser.parse(wrapped).profiles.size)
        assertEquals(3, ContentParser.parse(UrlParts.b64UrlEncode("$vless\n$trojan\n$ss".toByteArray())).profiles.size)
    }

    @Test
    fun nameFallsBackToEndpoint() {
        val r = ContentParser.parse("trojan://pw@192.0.2.9:443?sni=example.com")
        assertEquals("192.0.2.9:443", r.profiles.single().name)
    }

    @Test
    fun singBoxJsonSubscription() {
        val json = """
        {"log":{},"outbounds":[
          {"type":"selector","tag":"sel","outbounds":["a"]},
          {"type":"vless","tag":"a","server":"192.0.2.10","server_port":443,"uuid":"$uuid","flow":"xtls-rprx-vision",
           "tls":{"enabled":true,"server_name":"example.com","utls":{"enabled":true,"fingerprint":"chrome"},
                  "reality":{"enabled":true,"public_key":"DUMMYKEY","short_id":"ab"}},
           "transport":{"type":"ws","path":"/p","headers":{"Host":"example.com","X-A":"b"}}},
          {"type":"hysteria2","tag":"b","server":"192.0.2.11","server_port":443,"password":"pw","server_ports":["20000:30000"],
           "obfs":{"type":"salamander","password":"o"},"tls":{"enabled":true,"insecure":true}},
          {"type":"direct","tag":"direct"}
        ],"endpoints":[
          {"type":"wireguard","tag":"w","address":["10.0.0.2/32"],"private_key":"DUMMYPRIV=","peers":[{"address":"192.0.2.12","port":51820,"public_key":"DUMMYPUB=","reserved":[1,2,3]}]}
        ]}
        """.trimIndent()
        val r = ContentParser.parse(json)
        assertEquals(listOf("a", "b", "w"), r.profiles.map { it.name })
        val a = assertIs<VlessBean>(r.profiles[0].bean)
        assertTrue(a.tls.reality.enabled)
        assertEquals("example.com", a.transport.host)
        assertEquals(listOf("X-A", "b"), a.transport.headers)
        val b = assertIs<HysteriaBean>(r.profiles[1].bean)
        assertEquals("o", b.obfs)
        assertEquals(listOf("20000:30000"), b.serverPorts)
        val w = assertIs<WireGuardBean>(r.profiles[2].bean)
        assertEquals(listOf(1, 2, 3), w.peer.reserved)
    }

    @Test
    fun singBoxJsonHysteria1WindowsOldAndNewNames() {
        val modern = """{"type":"hysteria","tag":"n","server":"192.0.2.23","server_port":443,"auth_str":"a",
            "connection_receive_window":200,"stream_receive_window":100,"disable_path_mtu_discovery":true}"""
        val n = assertIs<HysteriaBean>(ContentParser.parse(modern).profiles.single().bean)
        assertEquals(200, n.recvWindowConn)
        assertEquals(100, n.recvWindow)
        assertTrue(n.disableMtuDiscovery)
        val legacy = """{"type":"hysteria","tag":"o","server":"192.0.2.24","server_port":443,"auth_str":"a",
            "recv_window_conn":300,"recv_window":150,"disable_mtu_discovery":true}"""
        val o = assertIs<HysteriaBean>(ContentParser.parse(legacy).profiles.single().bean)
        assertEquals(300, o.recvWindowConn)
        assertEquals(150, o.recvWindow)
        assertTrue(o.disableMtuDiscovery)
    }

    @Test
    fun singBoxJsonArrayAndSingleObject() {
        val arr = """[{"type":"tuic","tag":"t","server":"192.0.2.20","server_port":443,"uuid":"$uuid","password":"pw","tls":{"enabled":true}}]"""
        assertIs<TuicBean>(ContentParser.parse(arr).profiles.single().bean)
        val single = """{"type":"trojan","tag":"s","server":"192.0.2.21","server_port":443,"password":"pw"}"""
        assertIs<TrojanBean>(ContentParser.parse(single).profiles.single().bean)
        val unknown = """{"type":"somefuture","tag":"x","server":"192.0.2.22"}"""
        val c = assertIs<CustomBean>(ContentParser.parse(unknown).profiles.single().bean)
        assertEquals(CustomBean.OUTBOUND, c.subtype)
    }

    @Test
    fun xrayJson() {
        val json = """{"outbounds":[
          {"protocol":"vless","tag":"xv","settings":{"vnext":[{"address":"192.0.2.30","port":443,"users":[{"id":"$uuid","encryption":"none","flow":"xtls-rprx-vision"}]}]},
           "streamSettings":{"network":"xhttp","security":"reality","realitySettings":{"serverName":"example.com","fingerprint":"chrome","publicKey":"DUMMYKEY","shortId":"ab"},
             "xhttpSettings":{"path":"/x","mode":"packet-up","extra":{"xPaddingBytes":"100-200","xmux":{"maxConcurrency":"4"}}}}},
          {"protocol":"vmess","tag":"xm","settings":{}},
          {"protocol":"freedom","tag":"direct"}
        ]}"""
        val r = ContentParser.parse(json)
        assertEquals(listOf("xv", "xm"), r.profiles.map { it.name })
        val v = assertIs<XrayVlessBean>(r.profiles[0].bean)
        assertEquals("xhttp", v.streamSetting.network)
        assertEquals("DUMMYKEY", v.streamSetting.reality.password)
        assertEquals("100-200", v.streamSetting.xhttp.xPaddingBytes)
        assertEquals("4", v.streamSetting.xhttp.maxConcurrency)
        assertEquals(CustomBean.XRAY_OUTBOUND, assertIs<CustomBean>(r.profiles[1].bean).subtype)
    }

    @Test
    fun xrayFullConfigArrayDropsInbounds() {
        val json = """[{"remarks":"cfg","inbounds":[{"port":10808}],"outbounds":[{"protocol":"vless","tag":"p","settings":{}}]}]"""
        val c = assertIs<CustomBean>(ContentParser.parse(json).profiles.single().bean)
        assertEquals("cfg", c.name)
        assertEquals(CustomBean.XRAY_FULL_CONFIG, c.subtype)
        assertTrue("inbounds" !in c.config)
    }

    @Test
    fun sip008() {
        val json = """{"version":1,"servers":[{"server":"192.0.2.40","server_port":8388,"password":"pw","method":"aes-256-gcm","remarks":"sip","plugin":"simple-obfs","plugin_opts":"obfs=tls"}]}"""
        val s = assertIs<ShadowsocksBean>(ContentParser.parse(json).profiles.single().bean)
        assertEquals("sip", s.name)
        assertEquals("obfs-local", s.plugin)
        assertEquals(8388, s.serverPort)
    }

    @Test
    fun clashYaml() {
        val yaml = """
            port: 7890
            proxies:
              - name: "ss-node"
                type: ss
                server: 192.0.2.50
                port: 8388
                cipher: aes-256-gcm
                password: "pw"
                plugin: obfs
                plugin-opts: {mode: tls, host: example.com}
              - {name: vm, type: vmess, server: 192.0.2.51, port: 443, uuid: $uuid, alterId: 0, cipher: auto, tls: true, servername: example.com, network: ws, ws-opts: {path: /ws, headers: {Host: example.com}}}
              - name: vl
                type: vless
                server: 192.0.2.52
                port: 443
                uuid: $uuid
                tls: true
                flow: xtls-rprx-vision
                servername: example.com
                client-fingerprint: chrome
                reality-opts: {public-key: DUMMYKEY, short-id: ab}
              - name: vx
                type: vless
                server: 192.0.2.53
                port: 443
                uuid: $uuid
                tls: true
                network: xhttp
                xhttp-opts: {path: /x, mode: auto}
              - name: tr
                type: trojan
                server: 192.0.2.54
                port: 443
                password: pw
                sni: example.com
                skip-cert-verify: true
              - name: hy
                type: hysteria2
                server: 192.0.2.55
                port: 443
                password: pw
                obfs: salamander
                obfs-password: o
                up: "50 Mbps"
                down: 100
                ports: 20000-30000
                hop-interval: 30
                sni: example.com
              - name: tu
                type: tuic
                server: 192.0.2.56
                port: 443
                uuid: $uuid
                password: pw
                congestion-controller: bbr
                udp-relay-mode: native
              - name: unsupported
                type: ssr
                server: 192.0.2.57
                port: 1
            proxy-groups: []
        """.trimIndent()
        val r = ContentParser.parse(yaml)
        assertEquals(listOf("ss-node", "vm", "vl", "vx", "tr", "hy", "tu"), r.profiles.map { it.name })
        val ss = assertIs<ShadowsocksBean>(r.profiles[0].bean)
        assertEquals("obfs-local", ss.plugin)
        assertEquals("obfs=tls;obfs-host=example.com", ss.pluginOpts)
        val vm = assertIs<VmessBean>(r.profiles[1].bean)
        assertEquals("ws", vm.transport.type)
        assertEquals("example.com", vm.transport.host)
        assertTrue(vm.tls.enabled)
        val vl = assertIs<VlessBean>(r.profiles[2].bean)
        assertTrue(vl.tls.reality.enabled)
        assertEquals("ab", vl.tls.reality.shortId)
        assertEquals("chrome", vl.tls.utls.fingerprint)
        assertEquals("xhttp", assertIs<XrayVlessBean>(r.profiles[3].bean).streamSetting.network)
        assertTrue(assertIs<TrojanBean>(r.profiles[4].bean).tls.insecure)
        val hy = assertIs<HysteriaBean>(r.profiles[5].bean)
        assertEquals(50, hy.upMbps)
        assertEquals(100, hy.downMbps)
        assertEquals(listOf("20000:30000"), hy.serverPorts)
        assertEquals("30", hy.hopInterval)
        assertEquals("o", hy.obfs)
        assertEquals("bbr", assertIs<TuicBean>(r.profiles[6].bean).congestionControl)
    }

    @Test
    fun hostileYamlDoesNotThrow() {
        val bomb = "proxies:\n" + (1..40).joinToString("\n") { "  - &a$it [*a${it - 1}]" }
        ContentParser.parse(bomb)
        ContentParser.parse("proxies: !!python/object/apply:os.system ['id']")
        ContentParser.parse("proxies:\n  - {name: x, type: ss")
    }

    @Test
    fun wireguardConfText() {
        val conf = "[Interface]\nPrivateKey = DUMMYPRIV=\nAddress = 10.0.0.2\n\n[Peer]\nPublicKey = DUMMYPUB=\nEndpoint = 192.0.2.60:51820\n"
        val w = assertIs<WireGuardBean>(ContentParser.parse(conf).profiles.single().bean)
        assertEquals(listOf("10.0.0.2/32"), w.address)
        assertEquals("192.0.2.60:51820", ContentParser.parse(conf).profiles.single().name)
    }

    @Test
    fun jsonBlockInsideLinkList() {
        val text = "$vless\n{\"type\":\"trojan\",\n \"tag\":\"blk\",\"server\":\"192.0.2.70\",\"server_port\":443,\"password\":\"pw\"}\n$trojan"
        assertEquals(listOf("one", "blk", "two"), ContentParser.parse(text).profiles.map { it.name })
    }

    @Test
    fun emptyAndWhitespace() {
        assertTrue(ContentParser.parse("").isEmpty)
        assertTrue(ContentParser.parse("   \n  ").isEmpty)
    }

    @Test
    fun subscriptionUrlDetection() {
        assertTrue(ImportInput.isSubscriptionUrl("https://example.com/sub?token=abc"))
        assertTrue(!ImportInput.isSubscriptionUrl("https://example.com/sub\nvless://x"))
        assertTrue(!ImportInput.isSubscriptionUrl("vless://x@y:1"))
        assertEquals("example.com", ImportInput.groupNameForUrl("https://example.com/sub?token=abc"))
    }
}
