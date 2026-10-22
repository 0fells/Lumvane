package com.lumvane.android.importer

import com.lumvane.android.config.model.AnyTlsBean
import com.lumvane.android.config.model.HttpBean
import com.lumvane.android.config.model.HysteriaBean
import com.lumvane.android.config.model.MultiplexConfig
import com.lumvane.android.config.model.OutboundBean
import com.lumvane.android.config.model.Reality
import com.lumvane.android.config.model.ShadowsocksBean
import com.lumvane.android.config.model.SocksBean
import com.lumvane.android.config.model.SshBean
import com.lumvane.android.config.model.TlsConfig
import com.lumvane.android.config.model.TransportConfig
import com.lumvane.android.config.model.TrojanBean
import com.lumvane.android.config.model.TuicBean
import com.lumvane.android.config.model.UTls
import com.lumvane.android.config.model.VlessBean
import com.lumvane.android.config.model.VmessBean
import com.lumvane.android.config.model.XrayGrpc
import com.lumvane.android.config.model.XrayReality
import com.lumvane.android.config.model.XrayStreamSetting
import com.lumvane.android.config.model.XrayTls
import com.lumvane.android.config.model.XrayVlessBean
import com.lumvane.android.config.model.XrayWs
import com.lumvane.android.config.model.XrayXhttp
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

object ClashParser {
    @Suppress("UNCHECKED_CAST")
    private fun yaml(text: String): Any? {
        val opts = LoaderOptions().apply { codePointLimit = 64 * 1024 * 1024; maxAliasesForCollections = 50 }
        return Yaml(SafeConstructor(opts)).load<Any?>(text.replace("\u0000", "").removePrefix("﻿"))
    }

    fun looksLikeClash(text: String) = text.contains("proxies:")

    fun parse(text: String): List<OutboundBean> {
        val root = runCatching { yaml(text) }.getOrNull() as? Map<*, *> ?: return emptyList()
        val proxies = root["proxies"] as? List<*> ?: return emptyList()
        return proxies.mapNotNull { (it as? Map<*, *>)?.let { m -> runCatching { parseProxy(m) }.getOrNull() } }
    }

    private fun Map<*, *>.s(k: String): String = this[k]?.let { if (it is Map<*, *> || it is List<*>) "" else it.toString() } ?: ""
    private fun Map<*, *>.i(k: String): Int = when (val v = this[k]) { is Number -> v.toInt(); is String -> v.toIntOrNull() ?: 0; else -> 0 }
    private fun Map<*, *>.b(k: String): Boolean = when (val v = this[k]) { is Boolean -> v; is Number -> v.toInt() == 1; is String -> v == "true" || v == "1"; else -> false }
    private fun Map<*, *>.m(k: String): Map<*, *>? = this[k] as? Map<*, *>
    private fun Map<*, *>.l(k: String): List<String> = (this[k] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()

    private fun tls(p: Map<*, *>, force: Boolean = false): TlsConfig {
        val server = p.s("server")
        val sni = p.s("servername").ifEmpty { p.s("sni") }.ifEmpty { server }
        val fp = p.s("client-fingerprint")
        val pk = p.m("reality-opts")?.s("public-key").orEmpty()
        return TlsConfig(
            enabled = force || p.b("tls"), serverName = sni, insecure = p.b("skip-cert-verify"), alpn = p.l("alpn"),
            utls = UTls(fp.isNotEmpty(), fp),
            reality = if (pk.isNotEmpty()) Reality(true, pk, p.m("reality-opts")?.s("short-id").orEmpty()) else Reality(),
        )
    }

    private fun mux(p: Map<*, *>): MultiplexConfig {
        val s = p.m("smux") ?: return MultiplexConfig()
        return MultiplexConfig(
            enabled = s.b("enabled"), unspecified = false, protocol = s.s("protocol"),
            maxConnections = if (s.i("max-streams") > 0) 0 else s.i("max-connections"),
            minStreams = if (s.i("max-streams") > 0) 0 else s.i("min-streams"),
            maxStreams = s.i("max-streams"), padding = s.b("padding"),
        )
    }

    private fun transport(p: Map<*, *>): TransportConfig {
        val network = p.s("network")
        val ws = p.m("ws-opts")
        if (ws != null || network == "ws") {
            val w = ws ?: emptyMap<Any, Any>()
            val hdr = w.m("headers")
            var host = p.s("servername")
            val headers = ArrayList<String>()
            hdr?.forEach { (k, v) -> headers += k.toString(); headers += v.toString() }
            if (host.isEmpty()) host = hdr?.get("Host")?.toString().orEmpty()
            val upgrade = w.b("v2ray-http-upgrade")
            return TransportConfig(
                type = if (upgrade) "httpupgrade" else "ws", host = host, path = w.s("path"), headers = headers,
                maxEarlyData = if (upgrade) 0 else w.i("max-early-data"),
                earlyDataHeaderName = if (upgrade) "" else w.s("early-data-header-name"),
            )
        }
        p.m("grpc-opts")?.let { g -> if (g.s("grpc-service-name").isNotEmpty()) return TransportConfig(type = "grpc", serviceName = g.s("grpc-service-name")) }
        val h2 = p.m("h2-opts")
        if (h2 != null || network == "h2") {
            val w = h2 ?: emptyMap<Any, Any>()
            return TransportConfig(type = "http", host = w.l("host").firstOrNull().orEmpty(), path = w.s("path"))
        }
        p.m("http-opts")?.let { h ->
            if (h.s("method").isNotEmpty()) {
                return TransportConfig(
                    type = "http", method = h.s("method"), path = h.l("path").firstOrNull().orEmpty(),
                    host = (h.m("headers")?.get("Host") as? List<*>)?.firstOrNull()?.toString().orEmpty(),
                )
            }
        }
        return TransportConfig()
    }

    private fun anyToMbps(s: String): Int {
        if (s.isEmpty()) return 0
        s.toIntOrNull()?.let { return it }
        val m = Regex("^(\\d+)([KMGTkmgt]?)([Bb]?)").find(s) ?: return 0
        val v = m.groupValues[1].toDouble()
        var n = when (m.groupValues[2].uppercase()) { "K" -> 0.001; "G" -> 1000.0; "T" -> 1_000_000.0; else -> 1.0 }
        if (m.groupValues[3].uppercase() == "B") n *= 8.0
        return (v * n).toInt()
    }

    fun parseProxy(p: Map<*, *>): OutboundBean? {
        val type = p.s("type")
        val name = p.s("name")
        val server = p.s("server")
        val port = p.i("port")
        if (server.isEmpty()) return null
        return when (type) {
            "socks5" -> SocksBean(name = name, server = server, serverPort = port, username = p.s("username"), password = p.s("password"))
            "http" -> HttpBean(name = name, server = server, serverPort = port, username = p.s("username"), password = p.s("password"), tls = tls(p))
            "ss" -> {
                var plugin = ""
                var opts = ""
                val po = p.m("plugin-opts")
                when (p.s("plugin")) {
                    "v2ray-plugin" -> {
                        plugin = "v2ray-plugin"
                        val l = ArrayList<String>()
                        if (po?.b("tls") == true) l += "tls"
                        po?.s("host")?.takeIf { it.isNotEmpty() }?.let { l += "host=$it" }
                        po?.s("path")?.takeIf { it.isNotEmpty() }?.let { l += "path=$it" }
                        po?.s("mode")?.takeIf { it.isNotEmpty() }?.let { l += "mode=$it" }
                        if (po?.b("mux") == true) l += "mux"
                        opts = l.joinToString(";")
                    }
                    "obfs" -> {
                        plugin = "obfs-local"
                        val l = ArrayList<String>()
                        po?.s("mode")?.takeIf { it.isNotEmpty() }?.let { l += "obfs=$it" }
                        po?.s("host")?.takeIf { it.isNotEmpty() }?.let { l += "obfs-host=$it" }
                        opts = l.joinToString(";")
                    }
                }
                ShadowsocksBean(
                    name = name, server = server, serverPort = port, method = p.s("cipher"), password = p.s("password"),
                    plugin = plugin, pluginOpts = opts, uot = p.b("udp-over-tcp"), multiplex = mux(p),
                )
            }
            "vmess" -> VmessBean(
                name = name, server = server, serverPort = port, uuid = p.s("uuid"),
                security = p.s("cipher").ifEmpty { "auto" }, alterId = p.i("alterId"),
                tls = tls(p), packetEncoding = p.s("packet-encoding"), transport = transport(p), multiplex = mux(p),
            )
            "vless" -> {
                val enc = p.s("encryption")
                if (p.s("network") == "xhttp" || (enc.isNotEmpty() && enc != "none")) parseXrayVless(p, name, server, port, enc)
                else VlessBean(
                    name = name, server = server, serverPort = port, uuid = p.s("uuid"), flow = p.s("flow"),
                    tls = tls(p), packetEncoding = p.s("packet-encoding"), multiplex = mux(p), transport = transport(p),
                )
            }
            "trojan" -> TrojanBean(
                name = name, server = server, serverPort = port, password = p.s("password"),
                tls = tls(p, force = true), multiplex = mux(p), transport = transport(p),
            )
            "anytls" -> {
                val d = AnyTlsBean()
                AnyTlsBean(
                    name = name, server = server, serverPort = port, password = p.s("password"),
                    idleSessionCheckInterval = p.i("idle-session-check-interval").takeIf { it > 0 }?.let { "${it}s" } ?: d.idleSessionCheckInterval,
                    idleSessionTimeout = p.i("idle-session-timeout").takeIf { it > 0 }?.let { "${it}s" } ?: d.idleSessionTimeout,
                    minIdleSession = p.i("min-idle-session").takeIf { it > 0 } ?: d.minIdleSession,
                    tls = tls(p, force = true),
                )
            }
            "ssh" -> SshBean(
                name = name, server = server, serverPort = port, user = p.s("username"), password = p.s("password"),
                privateKey = p.s("private-key"), privateKeyPassphrase = p.s("private-key-passphrase"),
                hostKey = p.l("host-key"), hostKeyAlgorithms = p.l("host-key-algorithms"),
            )
            "tuic" -> TuicBean(
                name = name, server = p.s("ip").ifEmpty { server }, serverPort = port, uuid = p.s("uuid"), password = p.s("password"),
                congestionControl = p.s("congestion-controller"), udpRelayMode = p.s("udp-relay-mode"),
                zeroRttHandshake = p.b("reduce-rtt"),
                heartbeat = p.i("heartbeat-interval").takeIf { it > 0 }?.let { "${it}ms" } ?: "",
                tls = tls(p, force = true),
            )
            "hysteria", "hysteria2" -> {
                val v1 = type == "hysteria"
                var h = HysteriaBean(
                    name = name, server = server, serverPort = port, protocolVersion = if (v1) "1" else "2",
                    serverPorts = p.s("ports").takeIf { it.isNotEmpty() }?.let { LinkParser.normalizePorts(it.split(',', '/')) } ?: emptyList(),
                    hopInterval = p.s("hop-interval"),
                    upMbps = anyToMbps(p.s("up")), downMbps = anyToMbps(p.s("down")), tls = tls(p, force = true),
                )
                h = if (v1) {
                    h.copy(
                        auth = p.s("auth-str").ifEmpty { p.s("auth_str") },
                        authType = if (p.s("auth-str").isNotEmpty() || p.s("auth_str").isNotEmpty()) "STRING" else "",
                        obfs = p.s("obfs"),
                        recvWindow = p.i("recv-window").takeIf { it > 0 } ?: p.i("recv_window"),
                        recvWindowConn = p.i("recv-window-conn").takeIf { it > 0 } ?: p.i("recv_window_conn"),
                        disableMtuDiscovery = p.b("disable_mtu_discovery"),
                    )
                } else h.copy(password = p.s("password"), obfs = p.s("obfs-password"))
                h
            }
            else -> null
        }
    }

    private fun parseXrayVless(p: Map<*, *>, name: String, server: String, port: Int, enc: String): XrayVlessBean {
        var network = p.s("network").ifEmpty { "raw" }
        if (network == "tcp") network = "raw"
        val isReality = p.m("reality-opts")?.s("public-key").orEmpty().isNotEmpty()
        val sni = p.s("servername").ifEmpty { p.s("sni") }.ifEmpty { server }
        val fp = p.s("client-fingerprint")
        var ss = XrayStreamSetting(network = if (network in XrayStreamSetting.NETWORKS) network else "raw")
        if (p.b("tls")) {
            ss = if (isReality) {
                val r = p.m("reality-opts")!!
                ss.copy(security = "reality", reality = XrayReality(sni, fp, r.s("public-key"), r.s("short-id")))
            } else ss.copy(security = "tls", tls = XrayTls(serverName = sni, alpn = p.l("alpn"), fingerprint = fp))
        }
        when (ss.network) {
            "xhttp" -> p.m("xhttp-opts")?.let { x ->
                val rs = x.m("reuse-settings")
                ss = ss.copy(xhttp = XrayXhttp(
                    host = x.s("host"), path = x.s("path"), mode = x.s("mode").ifEmpty { "auto" },
                    xPaddingObfsMode = x.b("x-padding-obfs-mode"), xPaddingKey = x.s("x-padding-key"),
                    xPaddingHeader = x.s("x-padding-header"), xPaddingPlacement = x.s("x-padding-placement"),
                    xPaddingMethod = x.s("x-padding-method"), scMinPostsIntervalMs = x.s("sc-min-posts-interval-ms"),
                    maxConcurrency = rs?.s("max-concurrency").orEmpty(), maxConnections = rs?.s("max-connections").orEmpty(),
                    cMaxReuseTimes = rs?.s("c-max-reuse-times").orEmpty(), hMaxRequestTimes = rs?.s("h-max-request-times").orEmpty(),
                    hMaxReusableSecs = rs?.s("h-max-reusable-secs").orEmpty(),
                    hKeepAlivePeriod = rs?.s("h-keep-alive-period")?.toLongOrNull() ?: 0,
                ))
            }
            "ws" -> {
                val w = p.m("ws-opts")
                var path = w?.s("path").orEmpty()
                var ed = 0
                if (path.contains("?ed=")) { ed = path.substringAfter("?ed=").toIntOrNull() ?: 0; path = path.substringBefore("?ed=") }
                val headers = ArrayList<String>()
                w?.m("headers")?.forEach { (k, v) -> headers += k.toString(); headers += v.toString() }
                ss = ss.copy(ws = XrayWs(path, p.s("servername").ifEmpty { w?.m("headers")?.get("Host")?.toString().orEmpty() }, ed, headers))
            }
            "grpc" -> ss = ss.copy(grpc = XrayGrpc(serviceName = p.m("grpc-opts")?.s("grpc-service-name").orEmpty()))
        }
        return XrayVlessBean(
            name = name, server = server, serverPort = port, uuid = p.s("uuid"),
            encryption = enc.ifEmpty { "none" }, flow = p.s("flow"), streamSetting = ss,
        )
    }
}
