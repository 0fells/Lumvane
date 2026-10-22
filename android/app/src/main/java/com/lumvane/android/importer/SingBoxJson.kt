package com.lumvane.android.importer

import com.lumvane.android.config.model.AnyTlsBean
import com.lumvane.android.config.model.CustomBean
import com.lumvane.android.config.model.DialFields
import com.lumvane.android.config.model.Ech
import com.lumvane.android.config.model.HttpBean
import com.lumvane.android.config.model.HysteriaBean
import com.lumvane.android.config.model.JuicityBean
import com.lumvane.android.config.model.MieruBean
import com.lumvane.android.config.model.MultiplexConfig
import com.lumvane.android.config.model.NaiveBean
import com.lumvane.android.config.model.OutboundBean
import com.lumvane.android.config.model.Reality
import com.lumvane.android.config.model.ShadowTlsBean
import com.lumvane.android.config.model.ShadowsocksBean
import com.lumvane.android.config.model.SocksBean
import com.lumvane.android.config.model.SshBean
import com.lumvane.android.config.model.TcpBrutal
import com.lumvane.android.config.model.TlsConfig
import com.lumvane.android.config.model.TransportConfig
import com.lumvane.android.config.model.TrojanBean
import com.lumvane.android.config.model.TrustTunnelBean
import com.lumvane.android.config.model.TuicBean
import com.lumvane.android.config.model.UTls
import com.lumvane.android.config.model.VlessBean
import com.lumvane.android.config.model.VmessBean
import com.lumvane.android.config.model.WireGuardBean
import com.lumvane.android.config.model.WireGuardPeer
import com.lumvane.android.config.model.XrayMultiplex
import com.lumvane.android.config.model.XrayReality
import com.lumvane.android.config.model.XrayStreamSetting
import com.lumvane.android.config.model.XrayTls
import com.lumvane.android.config.model.XrayVlessBean
import com.lumvane.android.config.model.XrayWs
import com.lumvane.android.config.model.XrayHttpUpgrade
import com.lumvane.android.config.model.XrayGrpc
import com.lumvane.android.config.model.XrayXhttp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

object SingBoxJson {

    private fun JsonElement?.s(): String? = (this as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.str(k: String, d: String = ""): String = this[k].s() ?: d
    private fun JsonObject.int(k: String, d: Int = 0): Int = this[k].s()?.toDoubleOrNull()?.toInt() ?: d
    private fun JsonObject.bool(k: String, d: Boolean = false): Boolean = (this[k] as? JsonPrimitive)?.let { it.booleanOrNull ?: (it.contentOrNull == "1") } ?: d
    private fun JsonObject.obj(k: String): JsonObject? = this[k] as? JsonObject
    private fun JsonObject.strList(k: String): List<String> = when (val v = this[k]) {
        is JsonArray -> v.mapNotNull { it.s() }
        is JsonPrimitive -> v.contentOrNull?.let { listOf(it) } ?: emptyList()
        else -> emptyList()
    }

    private fun dial(o: JsonObject) = DialFields(
        reuseAddr = o.bool("reuse_addr"), connectTimeout = o.str("connect_timeout"), tcpFastOpen = o.bool("tcp_fast_open"),
        tcpMultiPath = o.bool("tcp_multi_path"), udpFragment = o.bool("udp_fragment"), bindInterface = o.str("bind_interface"),
        inet4BindAddress = o.str("inet4_bind_address"), inet6BindAddress = o.str("inet6_bind_address"),
    )

    internal fun tls(o: JsonObject?): TlsConfig {
        if (o == null || o.isEmpty()) return TlsConfig()
        val cert: List<String> = when (val c = o["certificate"]) {
            is JsonPrimitive -> c.contentOrNull?.split("\n")?.filter { it.isNotEmpty() } ?: emptyList()
            is JsonArray -> c.mapNotNull { it.s() }
            else -> emptyList()
        }
        val frag = o["fragment"]
        val tricks = o["tls_tricks"]
        val ech = o.obj("ech")
        val utls = o.obj("utls")
        val reality = o.obj("reality")
        return TlsConfig(
            enabled = o.bool("enabled"), disableSni = o.bool("disable_sni"), serverName = o.str("server_name"),
            insecure = o.bool("insecure"), alpn = o.strList("alpn"), minVersion = o.str("min_version"),
            maxVersion = o.str("max_version"), cipherSuites = o.strList("cipher_suites"),
            curvePreferences = o.strList("curve_preferences"), certificate = cert,
            certificatePath = o.str("certificate_path"),
            certificatePublicKeySha256 = o.strList("certificate_public_key_sha256"),
            clientCertificate = o.strList("client_certificate"), clientCertificatePath = o.str("client_certificate_path"),
            clientKey = o.strList("client_key"), clientKeyPath = o.str("client_key_path"),
            fragment = o.bool("fragment"), fragmentUnspecified = frag == null,
            fragmentFallbackDelay = o.str("fragment_fallback_delay"), recordFragment = o.bool("record_fragment"),
            tlsTricks = (tricks as? JsonObject)?.bool("mixedcase_sni") ?: false, tlsTricksUnspecified = tricks == null,
            spoof = o.str("spoof"), spoofMethod = o.str("spoof_method"),
            ech = if (ech == null) Ech() else Ech(ech.bool("enabled"), ech.strList("config"), ech.str("config_path"), ech.str("query_server_name")),
            utls = if (utls == null) UTls() else UTls(utls.bool("enabled"), utls.str("fingerprint")),
            reality = if (reality == null) Reality() else Reality(reality.bool("enabled"), reality.str("public_key"), reality.str("short_id")),
        )
    }

    internal fun transport(o: JsonObject?): TransportConfig {
        if (o == null || o.isEmpty()) return TransportConfig()
        val type = o.str("type")
        var host = o.str("host")
        var headers = emptyList<String>()
        (o["headers"] as? JsonObject)?.let { h ->
            val hh = LinkedHashMap(h)
            if (type == "ws") {
                hh["Host"]?.let { hv ->
                    host = when (hv) {
                        is JsonPrimitive -> hv.contentOrNull ?: host
                        is JsonArray -> hv.firstNotNullOfOrNull { it.s() } ?: host
                        else -> host
                    }
                    hh.remove("Host")
                }
            }
            headers = hh.entries.flatMap { listOf(it.key, it.value.s().orEmpty()) }
        }
        var path = o.str("path")
        var ed = o.int("max_early_data")
        var edName = o.str("early_data_header_name")
        if (type == "ws" && path.contains("?ed=")) {
            ed = path.substringAfter("?ed=").toIntOrNull() ?: ed
            path = path.substringBefore("?ed=")
            if (edName.isEmpty()) edName = "Sec-WebSocket-Protocol"
        }
        return TransportConfig(
            type = if (type == "tcp") "" else type, host = host, path = path, method = o.str("method"), headers = headers,
            idleTimeout = o.str("idle_timeout"), pingTimeout = o.str("ping_timeout"), maxEarlyData = ed,
            earlyDataHeaderName = edName, serviceName = o.str("service_name"),
        )
    }

    internal fun mux(o: JsonObject?): MultiplexConfig {
        if (o == null || o.isEmpty()) return MultiplexConfig()
        val br = o.obj("brutal")
        return MultiplexConfig(
            enabled = o.bool("enabled"), unspecified = !o.containsKey("enabled"), protocol = o.str("protocol"),
            maxConnections = o.int("max_connections"), minStreams = o.int("min_streams"), maxStreams = o.int("max_streams"),
            padding = o.bool("padding"),
            brutal = if (br == null) TcpBrutal() else TcpBrutal(br.bool("enabled"), br.int("up_mbps"), br.int("down_mbps")),
        )
    }

    private fun uot(o: JsonObject, key: String): Boolean = when (val v = o[key]) {
        is JsonObject -> v.bool("enabled")
        is JsonPrimitive -> o.bool(key)
        else -> false
    }

    fun parseOutbound(o: JsonObject): OutboundBean? {
        val type = o.str("type")
        val name = o.str("tag").ifEmpty { o.str("name") }
        val server = o.str("server")
        val port = o.int("server_port")
        val d = dial(o)
        return when (type) {
            "socks" -> {
                val v = o["version"]
                val version = if (v is JsonPrimitive && v.isString) v.content.take(1).toIntOrNull() ?: 5 else o.int("version", 5)
                SocksBean(name, server, port, d, o.str("username"), o.str("password"), version, o.bool("uot"))
            }
            "http" -> HttpBean(
                name, server, port, d, o.str("username"), o.str("password"), o.str("path"),
                (o["headers"] as? JsonObject)?.entries?.flatMap { listOf(it.key, it.value.s().orEmpty()) } ?: emptyList(),
                tls(o.obj("tls")),
            )
            "shadowsocks" -> ShadowsocksBean(
                name, server, port, d, o.str("method"), o.str("password"), o.str("plugin"), o.str("plugin_opts"),
                uot(o, "udp_over_tcp"), mux(o.obj("multiplex")),
            )
            "vmess" -> VmessBean(
                name, server, port, d, o.str("uuid"), o.str("security", "auto"),
                if (o.containsKey("alter_id")) o.int("alter_id") else o.int("alter-id"),
                o.bool("global_padding") || o.bool("global-padding"), o.bool("authenticated_length"),
                tls(o.obj("tls")), o.str("packet_encoding", "xudp"), transport(o.obj("transport")), mux(o.obj("multiplex")),
            )
            "vless" -> VlessBean(
                name, server, port, d, o.str("uuid"), o.str("flow"), tls(o.obj("tls")),
                o.str("packet_encoding", "xudp"), mux(o.obj("multiplex")), transport(o.obj("transport")),
            )
            "trojan" -> TrojanBean(name, server, port, d, o.str("password"), tls(o.obj("tls")), mux(o.obj("multiplex")), transport(o.obj("transport")))
            "anytls" -> {
                val def = AnyTlsBean()
                AnyTlsBean(
                    name, server, port, d, o.str("password"),
                    o.str("idle_session_check_interval", def.idleSessionCheckInterval),
                    o.str("idle_session_timeout", def.idleSessionTimeout), o.int("min_idle_session", def.minIdleSession),
                    tls(o.obj("tls")),
                )
            }
            "mieru" -> MieruBean(
                name, server, port, d, o.str("transport", "TCP"), o.str("username"), o.str("password"),
                o.str("multiplexing"), o.str("traffic_pattern"), o.strList("server_ports").joinToString(","),
            )
            "hysteria", "hysteria2" -> parseHysteria(o, name, server, port, d, type == "hysteria")
            "tuic" -> TuicBean(
                name, server, port, d, o.str("uuid"), o.str("password"), o.str("congestion_control"), o.str("udp_relay_mode"),
                o.bool("udp_over_stream"), o.bool("zero_rtt_handshake"), o.str("heartbeat"), tls(o.obj("tls")),
            )
            "juicity" -> JuicityBean(name, server, port, d, o.str("uuid"), o.str("password"), tls(o.obj("tls")))
            "trusttunnel" -> TrustTunnelBean(
                name, server, port, d, o.str("username"), o.str("password"),
                o.str("quic_congestion_control").ifEmpty { o.str("congestion_control") },
                o.bool("health_check"), o.bool("quic"), tls(o.obj("tls")),
            )
            "naive" -> NaiveBean(
                name, server, port, d, o.str("username"), o.str("password"),
                o.str("quic_congestion_control").ifEmpty { o.str("congestion_control") },
                o.bool("quic"), uot(o, "udp_over_tcp"), tls(o.obj("tls")),
            )
            "shadowtls" -> ShadowTlsBean(name, server, port, d, o.int("version", 1), o.str("password"), tls(o.obj("tls")))
            "ssh" -> SshBean(
                name, server, port, d, o.str("user"), o.str("password"), o.str("private_key"), o.str("private_key_path"),
                o.str("private_key_passphrase"), o.strList("host_key"), o.strList("host_key_algorithms"), o.str("client_version"),
            )
            "wireguard" -> parseWireGuard(o, name, d)
            else -> null
        }
    }

    private fun parseHysteria(o: JsonObject, name: String, server: String, port: Int, d: DialFields, v1: Boolean): HysteriaBean {
        var b = HysteriaBean(
            name = name, server = server, serverPort = port, dial = d, protocolVersion = if (v1) "1" else "2",
            serverPorts = LinkParser.normalizePorts(o.strList("server_ports")),
            hopInterval = o.str("hop_interval"), upMbps = o.int("up_mbps"), downMbps = o.int("down_mbps"),
            tls = tls(o.obj("tls")).copy(enabled = true),
        )
        if (v1) {
            b = b.copy(
                obfs = o.str("obfs"),
                recvWindowConn = o.int("connection_receive_window").takeIf { it > 0 } ?: o.int("recv_window_conn"),
                recvWindow = o.int("stream_receive_window").takeIf { it > 0 } ?: o.int("recv_window"),
                disableMtuDiscovery = o.bool("disable_path_mtu_discovery") || o.bool("disable_mtu_discovery"),
            )
            if (o.containsKey("auth")) b = b.copy(auth = o.str("auth"), authType = "BASE64")
            if (o.containsKey("auth_str")) b = b.copy(auth = o.str("auth_str"), authType = "STRING")
        } else {
            val ob = o.obj("obfs")
            if (ob != null) {
                b = b.copy(obfs = ob.str("password"))
                if (ob.str("type") == "gecko") b = b.copy(obfsType = "gecko", obfsMinPacketSize = ob.int("min_packet_size"), obfsMaxPacketSize = ob.int("max_packet_size"))
            }
            if (o.containsKey("obfsPassword")) b = b.copy(obfs = o.str("obfsPassword"))
            b = b.copy(password = o.str("password"), hopIntervalMax = o.str("hop_interval_max"), bbrProfile = o.str("bbr_profile"),
                disableChromeParrot = o.bool("disable_chrome_parrot"))
        }
        return b
    }

    private fun parseWireGuard(o: JsonObject, name: String, d: DialFields): WireGuardBean {
        val p = (o["peers"] as? JsonArray)?.firstOrNull() as? JsonObject
        fun rangeStr(k: String, a: JsonObject) = a.str(k)
        val peer = if (p == null) {
            WireGuardPeer(address = o.str("server"), port = o.int("server_port"), publicKey = o.str("peer_public_key"),
                preSharedKey = o.str("pre_shared_key"))
        } else WireGuardPeer(
            address = p.str("address"), port = p.int("port"), publicKey = p.str("public_key"),
            preSharedKey = p.str("pre_shared_key"),
            reserved = (p["reserved"] as? JsonArray)?.mapNotNull { it.s()?.toIntOrNull() } ?: emptyList(),
            persistentKeepalive = p.str("persistent_keepalive_interval"),
        )
        var w = WireGuardBean(
            name = name, dial = d, privateKey = o.str("private_key"), peer = peer,
            address = o.strList("address").map { if (it.contains('/')) it else if (it.contains(':')) "$it/128" else "$it/32" },
            mtu = o.int("mtu", 1420), system = o.bool("system"), workerCount = o.int("worker_count"), udpTimeout = o.str("udp_timeout"),
        )
        o.obj("amnezia_wg")?.let { a ->
            w = w.copy(
                enableAmnezia = true, jc = a.int("jc"), jmin = a.int("jmin"), jmax = a.int("jmax"),
                s1 = a.int("s1"), s2 = a.int("s2"), s3 = a.int("s3"), s4 = a.int("s4"),
                h1 = a.str("h1"), h2 = a.str("h2"), h3 = a.str("h3"), h4 = a.str("h4"),
                i1 = a.str("i1"), i2 = a.str("i2"), i3 = a.str("i3"), i4 = a.str("i4"), i5 = a.str("i5"),
                headerProtectionKey = a.str("header_protection_key"),
                contentPaddingAddition = rangeStr("content_padding_addition", a), rekeyAfterTime = rangeStr("rekey_after_time", a),
                rekeyTimeout = rangeStr("rekey_timeout", a), rejectAfterTime = rangeStr("reject_after_time", a),
                keepaliveTimeout = rangeStr("keepalive_timeout", a), maxHandshakeAttempts = rangeStr("max_handshake_attempts", a),
            )
        }
        return w
    }

    fun parseXrayVless(out: JsonObject): XrayVlessBean? {
        if (out.str("protocol") != "vless") return null
        val settings = out.obj("settings") ?: return null
        val address: String
        val port: Int
        val id: String
        val encryption: String
        val flow: String
        if (settings.containsKey("address") && !settings.containsKey("vnext")) {
            address = settings.str("address"); port = settings.int("port"); id = settings.str("id")
            encryption = settings.str("encryption", "none"); flow = settings.str("flow")
        } else {
            val first = (settings["vnext"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
            val user = (first["users"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
            address = first.str("address"); port = first.int("port"); id = user.str("id")
            encryption = user.str("encryption", "none"); flow = user.str("flow")
        }
        if (address.isEmpty() || id.isEmpty()) return null
        val mux = out.obj("mux")
        return XrayVlessBean(
            name = out.str("tag"), server = address, serverPort = port, uuid = id,
            encryption = encryption.ifEmpty { "none" }, flow = flow,
            streamSetting = out.obj("streamSettings")?.let { xrayStream(it) } ?: XrayStreamSetting(),
            multiplex = if (mux == null || mux.isEmpty()) XrayMultiplex()
            else XrayMultiplex(mux.bool("enabled"), !mux.containsKey("enabled"), mux.int("concurrency"), mux.int("xudpConcurrency", 16)),
        )
    }

    private fun xrayHeaders(o: JsonObject): List<String> =
        (o["headers"] as? JsonObject)?.entries?.flatMap { listOf(it.key, it.value.s().orEmpty()) } ?: emptyList()

    private fun splitEd(path: String): Pair<String, Int> =
        if (path.contains("?ed=")) path.substringBefore("?ed=") to (path.substringAfter("?ed=").toIntOrNull() ?: 0) else path to 0

    internal fun xrayStream(o: JsonObject): XrayStreamSetting {
        var network = o.str("network", "raw")
        if (network == "tcp") network = "raw"
        if (network !in XrayStreamSetting.NETWORKS) network = "raw"
        val security = o.str("security", "none")
        var s = XrayStreamSetting(network = network, security = security)
        if (security == "tls") o.obj("tlsSettings")?.let { t ->
            s = s.copy(tls = XrayTls(t.str("serverName"), t.str("pinnedPeerCertSha256"), t.str("verifyPeerCertByName"), t.strList("alpn"), t.str("fingerprint")))
        }
        if (security == "reality") o.obj("realitySettings")?.let { r ->
            s = s.copy(reality = XrayReality(r.str("serverName"), r.str("fingerprint"), r.str("password").ifEmpty { r.str("publicKey") }, r.str("shortId"), r.str("spiderX")))
        }
        when (network) {
            "xhttp" -> o.obj("xhttpSettings")?.let { x ->
                var xh = XrayXhttp(host = x.str("host"), path = x.str("path"), mode = x.str("mode", "auto"))
                val top = JsonObject(x.filterKeys { it !in setOf("host", "path", "mode", "extra") })
                if (top.isNotEmpty()) xh = applyXhttpExtra(xh, top)
                when (val ex = x["extra"]) {
                    is JsonObject -> if (ex.isNotEmpty()) xh = applyXhttpExtra(xh, ex)
                    is JsonPrimitive -> ex.contentOrNull?.let { t -> parseXhttpExtra(t, xh)?.let { xh = it } }
                    else -> {}
                }
                s = s.copy(xhttp = xh)
            }
            "ws" -> o.obj("wsSettings")?.let { w ->
                val (p, ed) = splitEd(w.str("path"))
                s = s.copy(ws = XrayWs(p, w.str("host"), ed, xrayHeaders(w), w.int("heartbeatPeriod")))
            }
            "httpupgrade" -> o.obj("httpupgradeSettings")?.let { w ->
                val (p, ed) = splitEd(w.str("path"))
                s = s.copy(httpUpgrade = XrayHttpUpgrade(p, ed, w.str("host"), xrayHeaders(w)))
            }
            "grpc" -> o.obj("grpcSettings")?.let { g ->
                s = s.copy(grpc = XrayGrpc(g.str("authority"), g.str("serviceName"), g.bool("multiMode")))
            }
        }
        return s
    }

    fun xrayOutboundAsCustom(out: JsonObject): CustomBean? {
        val protocol = out.str("protocol")
        if (protocol in setOf("freedom", "blackhole", "dns", "loopback") || protocol.isEmpty()) return null
        return CustomBean(name = out.str("tag"), subtype = CustomBean.XRAY_OUTBOUND, config = out.toString())
    }

    fun parseTypedObject(o: JsonObject): OutboundBean? {
        if (o.containsKey("protocol")) return parseXrayVless(o) ?: xrayOutboundAsCustom(o)
        if (o.str("type") == "custom") {
            return CustomBean(o.str("name"), o.str("subtype", CustomBean.OUTBOUND), o.str("config"))
        }
        return parseOutbound(o)
    }
}
