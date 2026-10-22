package com.lumvane.android.importer

import com.lumvane.android.config.model.AnyTlsBean
import com.lumvane.android.config.model.DialFields
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
import com.lumvane.android.config.model.TlsConfig
import com.lumvane.android.config.model.TrojanBean
import com.lumvane.android.config.model.TrustTunnelBean
import com.lumvane.android.config.model.TuicBean
import com.lumvane.android.config.model.VlessBean
import com.lumvane.android.config.model.VmessBean
import com.lumvane.android.config.model.WireGuardBean
import com.lumvane.android.config.model.WireGuardPeer
import com.lumvane.android.config.model.XrayVlessBean
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class ParseOptions(val xrayVlessPreference: Int = 1)

object LinkParser {
    val SCHEMES = listOf(
        "socks5", "socks4", "socks4a", "socks", "http", "https", "ss", "vmess", "vless", "trojan", "anytls",
        "mierus", "mieru", "hysteria", "hysteria2", "hy2", "tuic", "juicity", "tt", "shadowtls", "wg", "ssh",
        "naive+https", "naive+quic", "json", "lumvane",
    )

    fun looksLikeLink(s: String): Boolean {
        val i = s.indexOf("://")
        return i > 0 && s.substring(0, i).lowercase() in SCHEMES
    }

    fun parse(raw: String, options: ParseOptions = ParseOptions()): OutboundBean? {
        val s = raw.trim()
        val lower = s.lowercase()
        return try {
            when {
                lower.startsWith("json://") -> parseJsonLink(s)
                lower.startsWith("lumvane://add/") -> parseDeepLink(s)
                lower.startsWith("socks5://") || lower.startsWith("socks4://") || lower.startsWith("socks4a://") ||
                    lower.startsWith("socks://") -> parseSocks(s)
                lower.startsWith("http://") || lower.startsWith("https://") -> parseHttp(s)
                lower.startsWith("ss://") -> parseShadowsocks(s)
                lower.startsWith("vmess://") -> parseVmess(s)
                lower.startsWith("vless://") -> if (useXrayVless(s, options)) parseXrayVless(s) else parseVless(s)
                lower.startsWith("trojan://") -> parseTrojan(s)
                lower.startsWith("anytls://") -> parseAnyTls(s)
                lower.startsWith("mierus://") || lower.startsWith("mieru://") -> parseMieru(s)
                lower.startsWith("hysteria://") || lower.startsWith("hysteria2://") || lower.startsWith("hy2://") -> parseHysteria(s)
                lower.startsWith("tuic://") -> parseTuic(s)
                lower.startsWith("juicity://") -> parseJuicity(s)
                lower.startsWith("tt://") -> parseTrustTunnel(s)
                lower.startsWith("shadowtls://") -> parseShadowTls(s)
                lower.startsWith("wg://") -> parseWireGuardLink(s)
                lower.startsWith("ssh://") -> parseSsh(s)
                lower.startsWith("naive+https://") || lower.startsWith("naive+quic://") -> parseNaive(s)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseJsonLink(s: String): OutboundBean? {
        val frag = s.substringAfter('#', "")
        val text = UrlParts.b64DecodeToString(frag) ?: return null
        return SingBoxJson.parseTypedObject(parseJsonObjectOrNull(text) ?: return null)
    }

    private fun parseDeepLink(s: String): OutboundBean? {
        val payload = s.substring("lumvane://add/".length).substringBefore('#').substringBefore('?').trim('/')
        val text = UrlParts.b64DecodeToString(UrlParts.percentDecode(payload)) ?: return null
        return SingBoxJson.parseTypedObject(parseJsonObjectOrNull(text) ?: return null)
    }

    private fun useXrayVless(link: String, o: ParseOptions): Boolean {
        val u = UrlParts.parse(link) ?: return false
        val enc = u.get("encryption") ?: ""
        return o.xrayVlessPreference == 2 ||
            u.get("type") == "xhttp" ||
            (u.get("security") == "reality" && o.xrayVlessPreference == 1) ||
            (enc != "none" && enc != "") ||
            (u.get("extra") ?: "") != ""
    }

    internal fun b64Text(s: String): String? {
        val bytes = UrlParts.b64Decode(s) ?: return null
        val text = String(bytes, Charsets.UTF_8)
        if (!text.toByteArray(Charsets.UTF_8).contentEquals(bytes)) return null
        if (text.isEmpty() || text.any { it.code < 0x20 && it != '\t' && it != '\n' && it != '\r' }) return null
        return text
    }

    private fun dial(u: UrlParts): DialFields = parseDial(u)

    private fun parseSocks(s: String): SocksBean? {
        val u = UrlParts.parse(s) ?: return null
        val version = u.get("version")?.toIntOrNull() ?: when (u.scheme) {
            "socks4", "socks4a" -> 4
            else -> 5
        }
        var user = u.user
        var pass = u.password
        if (user.isNotEmpty() && pass.isEmpty()) {
            b64Text(user)?.let { d ->
                user = d.substringBefore(':')
                pass = if (d.contains(':')) d.substringAfter(':') else d
            }
        }
        if (u.host.isEmpty()) return null
        return SocksBean(
            name = u.fragment, server = u.host, serverPort = if (u.port == 0) 1080 else u.port, dial = dial(u),
            username = user, password = pass, version = version, uot = u.get("uot").let { it == "true" || (it?.toIntOrNull() ?: 0) > 0 },
        )
    }

    private fun parseHttp(s: String): HttpBean? {
        val u = UrlParts.parse(s) ?: return null
        var user = u.user
        var pass = u.password
        if (pass.isEmpty() && user.isNotEmpty()) {
            b64Text(user)?.let { d ->
                user = d.substringBefore(':')
                pass = if (d.contains(':')) d.substringAfter(':') else d
            }
        }
        if (user.isEmpty() && pass.isNotEmpty()) user = pass
        var tls = TlsConfig()
        if (u.scheme == "https" || u.get("security") == "tls") tls = parseTls(u).copy(enabled = true)
        if (u.host.isEmpty()) return null
        return HttpBean(
            name = u.fragment, server = u.host, serverPort = if (u.port == 0) 443 else u.port, dial = dial(u),
            username = user, password = pass, path = u.get("path", ""),
            headers = u.get("headers")?.split(",") ?: emptyList(), tls = tls,
        )
    }

    private fun parseShadowsocks(link: String): ShadowsocksBean? {
        var work = link
        val beforeFrag = link.substringBefore('#')
        val u: UrlParts
        if (beforeFrag.contains('@')) {
            u = UrlParts.parse(work) ?: return null
        } else {
            val body = beforeFrag.substringAfter("://")
            val q = body.indexOf('?')
            val b64 = if (q >= 0) body.substring(0, q) else body
            val decoded = b64Text(b64.trimEnd('/')) ?: return null
            work = "ss://" + decoded + (if (q >= 0) body.substring(q) else "") +
                (if (link.contains('#')) "#" + link.substringAfter('#') else "")
            u = UrlParts.parse(work) ?: return null
        }
        val method: String
        val password: String
        if (!u.hasPassword || u.password.isEmpty()) {
            val mp = b64Text(u.user) ?: return null
            method = mp.substringBefore(':')
            password = if (mp.contains(':')) mp.substringAfter(':') else mp
        } else {
            method = u.user
            password = u.password
        }
        var plugin = (u.get("plugin") ?: "").replace("simple-obfs;", "obfs-local;")
        var opts = if (plugin.contains(';')) plugin.substringAfter(';') else ""
        plugin = plugin.substringBefore(';')
        u.get("plugin-opts")?.let { opts = it }
        val uot = u.get("uot").let { it == "true" || (it?.toIntOrNull() ?: 0) > 0 }
        if (u.host.isEmpty() || method.isEmpty() || password.isEmpty()) return null
        return ShadowsocksBean(
            name = u.fragment, server = u.host, serverPort = u.port, dial = dial(u),
            method = method, password = password, plugin = plugin, pluginOpts = opts, uot = uot, multiplex = parseMux(u),
        )
    }

    private fun parseVmess(link: String): VmessBean? {
        val noFrag = link.substringBefore('#')
        val frag = if (link.contains('#')) UrlParts.percentDecode(link.substringAfter('#')) else ""
        val body = noFrag.substring("vmess://".length)
        if (!body.contains('@')) {
            val json = b64Text(body.trim())?.let { parseJsonObjectOrNull(it) }
            if (json != null && json.isNotEmpty()) return vmessFromBase64Json(json, frag)
        }
        val u = UrlParts.parse(link) ?: return null
        val uuid = u.user
        if (uuid.isEmpty() || u.host.isEmpty()) return null
        val pe = u.get("packetEncoding") ?: "xudp"
        return VmessBean(
            name = u.fragment, server = u.host, serverPort = if (u.port == 0) 443 else u.port, dial = dial(u),
            uuid = uuid, security = u.get("encryption").let { if (it.isNullOrEmpty()) "auto" else it },
            alterId = u.get("alterId")?.toIntOrNull() ?: 0,
            globalPadding = u.get("globalPadding") == "true",
            authenticatedLength = u.get("authenticatedLength") == "true",
            tls = parseTls(u), packetEncoding = if (pe in listOf("", "packetaddr", "xudp")) pe else "",
            transport = parseTransport(u), multiplex = parseMux(u),
        )
    }

    private fun JsonObject.str(k: String): String = (this[k] as? JsonPrimitive)?.contentOrNull.orEmpty()
    private fun JsonObject.int(k: String): Int = (this[k] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toInt() ?: 0

    private fun vmessFromBase64Json(o: JsonObject, fragName: String): VmessBean? {
        val uuid = o.str("id")
        val server = o.str("add")
        if (uuid.isEmpty() || server.isEmpty()) return null
        var net = o.str("net")
        if (net == "h2") net = "http"
        if (o.str("type") == "http") net = "http"
        if (net == "tcp" || net == "raw") net = ""
        var path = o.str("path")
        var service = ""
        if (net == "grpc") { service = path; path = "" }
        val tlsOn = o.str("tls").let { it == "tls" || it == "reality" }
        val sni = o.str("sni")
        val tls = TlsConfig(
            enabled = tlsOn,
            serverName = if (tlsOn) sni else "",
            alpn = o.str("alpn").takeIf { it.isNotEmpty() }?.split(",") ?: emptyList(),
            insecure = o.str("allowInsecure").let { it == "1" || it == "true" },
            utls = com.lumvane.android.config.model.UTls(o.str("fp").isNotEmpty(), o.str("fp")),
        )
        val scy = o.str("scy")
        return VmessBean(
            name = o.str("ps").ifEmpty { fragName }, server = server, serverPort = o.int("port").let { if (it == 0) 443 else it },
            uuid = uuid, alterId = o.int("aid"), security = scy.ifEmpty { "auto" },
            tls = tls,
            transport = com.lumvane.android.config.model.TransportConfig(
                type = net, host = o.str("host"), path = path, serviceName = service,
                method = if (net == "http") "GET" else "",
            ),
        )
    }

    private fun parseVless(s: String): VlessBean? {
        val u = UrlParts.parse(s) ?: return null
        if (u.user.isEmpty() || u.host.isEmpty()) return null
        var tls = parseTls(u)
        if (tls.serverName.isNotEmpty()) tls = tls.copy(enabled = true)
        val pe = u.get("packetEncoding").let { if (it.isNullOrEmpty()) "xudp" else it }
        return VlessBean(
            name = u.fragment, server = u.host, serverPort = if (u.port == 0) 443 else u.port, dial = dial(u),
            uuid = u.user, flow = u.get("flow", ""), tls = tls,
            packetEncoding = if (pe in listOf("", "packetaddr", "xudp")) pe else "",
            multiplex = parseMux(u), transport = parseTransport(u),
        )
    }

    private fun parseXrayVless(s: String): XrayVlessBean? {
        val u = UrlParts.parse(s) ?: return null
        if (u.user.isEmpty() || u.host.isEmpty()) return null
        return XrayVlessBean(
            name = u.fragment, server = u.host, serverPort = if (u.port == 0) 443 else u.port, dial = dial(u),
            uuid = u.user, encryption = u.get("encryption").let { if (it.isNullOrEmpty()) "none" else it },
            flow = u.get("flow", ""), streamSetting = parseXrayStream(u), multiplex = parseXrayMux(u),
        )
    }

    private fun parseTrojan(s: String): TrojanBean? {
        val u = UrlParts.parse(s) ?: return null
        if (u.host.isEmpty()) return null
        var tls = parseTls(u)
        if (!u.has("security") && !tls.enabled) tls = tls.copy(enabled = true)
        return TrojanBean(
            name = u.fragment, server = u.host, serverPort = if (u.port == 0) 443 else u.port, dial = dial(u),
            password = u.user + if (u.hasPassword) ":" + u.password else "",
            tls = tls, multiplex = parseMux(u), transport = parseTransport(u),
        )
    }

    private fun parseAnyTls(s: String): AnyTlsBean? {
        val u = UrlParts.parse(s) ?: return null
        if (u.host.isEmpty()) return null
        val d = AnyTlsBean()
        return AnyTlsBean(
            name = u.fragment, server = u.host, serverPort = if (u.port == 0) 443 else u.port, dial = dial(u),
            password = u.user + if (u.hasPassword) ":" + u.password else "",
            idleSessionCheckInterval = u.get("idle_session_check_interval") ?: d.idleSessionCheckInterval,
            idleSessionTimeout = u.get("idle_session_timeout") ?: d.idleSessionTimeout,
            minIdleSession = u.get("min_idle_session")?.toIntOrNull() ?: d.minIdleSession,
            tls = parseTls(u).copy(enabled = true),
        )
    }

    internal fun normalizePorts(parts: List<String>): List<String> = parts.map { it.trim() }.filter { it.isNotEmpty() }.map { v ->
        val r = v.split(':', '-')
        if (r.size == 2) "${r[0]}:${r[1]}" else "$v:$v"
    }

    private fun parseHysteria(s: String): HysteriaBean? {
        val u = UrlParts.parse(s) ?: return null
        if (u.host.isEmpty()) return null
        var port = u.port
        var ports = emptyList<String>()
        if (port == 0 && u.portText.isNotEmpty() && Regex("^[\\d,\\-]+$").matches(u.portText)) {
            ports = normalizePorts(u.portText.split(","))
        }
        var b = HysteriaBean(name = u.fragment, server = u.host, serverPort = port, dial = dial(u))
        if (u.scheme == "hysteria") {
            b = b.copy(protocolVersion = "1")
            u.get("obfsParam")?.let { b = b.copy(obfs = it) }
            u.get("auth")?.let { b = b.copy(auth = it, authType = "STRING") }
            u.get("recv_window_conn")?.toIntOrNull()?.let { b = b.copy(recvWindowConn = it) }
            u.get("recv_window")?.toIntOrNull()?.let { b = b.copy(recvWindow = it) }
            u.get("disable_mtu_discovery")?.let { b = b.copy(disableMtuDiscovery = it == "true") }
        } else {
            b = b.copy(protocolVersion = "2", password = u.user + if (u.hasPassword) ":" + u.password else "")
            u.get("obfs-password")?.let { b = b.copy(obfs = it) }
            if (u.get("obfs") == "gecko") b = b.copy(obfsType = "gecko")
            u.get("obfs-min-packet-size")?.toIntOrNull()?.let { b = b.copy(obfsMinPacketSize = it) }
            u.get("obfs-max-packet-size")?.toIntOrNull()?.let { b = b.copy(obfsMaxPacketSize = it) }
            u.get("hop-interval-max")?.let { b = b.copy(hopIntervalMax = it) }
            u.get("hop_interval_max")?.let { b = b.copy(hopIntervalMax = it) }
            u.get("bbr_profile")?.let { b = b.copy(bbrProfile = it) }
            u.get("disable_chrome_parrot")?.let { b = b.copy(disableChromeParrot = truthy(it)) }
        }
        u.get("upmbps")?.toIntOrNull()?.let { b = b.copy(upMbps = it) }
        u.get("downmbps")?.toIntOrNull()?.let { b = b.copy(downMbps = it) }
        u.get("hop-interval")?.let { b = b.copy(hopInterval = it) }
        u.get("hop_interval")?.let { b = b.copy(hopInterval = it) }
        u.get("mport")?.let { ports = normalizePorts(it.split(",")) }
        b = b.copy(serverPorts = ports, tls = parseTls(u).copy(enabled = true))
        if (b.serverPort == 0 && b.serverPorts.isEmpty()) b = b.copy(serverPort = 443)
        return b
    }

    private fun parseTuic(s: String): TuicBean? {
        val u = UrlParts.parse(s) ?: return null
        if (u.user.isEmpty() || u.host.isEmpty()) return null
        return TuicBean(
            name = u.fragment, server = u.host, serverPort = if (u.port == 0) 443 else u.port, dial = dial(u),
            uuid = u.user, password = u.password,
            congestionControl = u.get("congestion_control", ""), udpRelayMode = u.get("udp_relay_mode", ""),
            udpOverStream = u.get("udp_over_stream") == "true", zeroRttHandshake = u.get("zero_rtt_handshake") == "true",
            heartbeat = u.get("heartbeat", ""), tls = parseTls(u).copy(enabled = true),
        )
    }

    private fun parseJuicity(s: String): JuicityBean? {
        val u = UrlParts.parse(s) ?: return null
        if (u.user.isEmpty() || u.host.isEmpty()) return null
        return JuicityBean(
            name = u.fragment, server = u.host, serverPort = if (u.port == 0) 443 else u.port, dial = dial(u),
            uuid = u.user, password = u.password, tls = parseTls(u).copy(enabled = true),
        )
    }

    private fun parseTrustTunnel(s: String): TrustTunnelBean? {
        val u = UrlParts.parse(s) ?: return null
        if (u.user.isEmpty() || u.password.isEmpty() || u.host.isEmpty()) return null
        val cc = u.get("congestion_control")
        return TrustTunnelBean(
            name = u.fragment, server = u.host, serverPort = if (u.port == 0) 443 else u.port, dial = dial(u),
            username = u.user, password = u.password, healthCheck = u.get("health_check") == "true",
            quic = cc != null, congestionControl = cc ?: "", tls = parseTls(u).copy(enabled = true),
        )
    }

    private fun parseShadowTls(s: String): ShadowTlsBean? {
        val u = UrlParts.parse(s) ?: return null
        if (u.host.isEmpty()) return null
        return ShadowTlsBean(
            name = u.fragment, server = u.host, serverPort = if (u.port == 0) 443 else u.port, dial = dial(u),
            version = u.get("version")?.toIntOrNull() ?: 1, password = u.password, tls = parseTls(u).copy(enabled = true),
        )
    }

    private fun parseNaive(s: String): NaiveBean? {
        val u = UrlParts.parse(s) ?: return null
        if (u.host.isEmpty()) return null
        val quic = u.scheme == "naive+quic"
        return NaiveBean(
            name = u.fragment, server = u.host, serverPort = if (u.port == 0) 443 else u.port, dial = dial(u),
            username = u.user, password = u.password,
            uot = u.get("uot").let { it == "true" || (it?.toIntOrNull() ?: 0) > 0 },
            quic = quic, congestionControl = if (quic) u.get("congestion_control", "") else "",
            tls = parseTls(u).copy(enabled = true),
        )
    }

    private fun parseSsh(s: String): SshBean? {
        val u = UrlParts.parse(s) ?: return null
        if (u.host.isEmpty()) return null
        fun b64s(v: String?): String = v?.let { UrlParts.b64DecodeToString(it) }.orEmpty()
        fun list(v: String?): List<String> = v?.takeIf { it.isNotEmpty() }?.split("-")?.map { b64s(it) }?.filter { it.isNotEmpty() } ?: emptyList()
        return SshBean(
            name = u.fragment, server = u.host, serverPort = if (u.port == 0) 22 else u.port, dial = dial(u),
            user = u.get("user") ?: u.user, password = u.get("password") ?: u.password,
            privateKey = b64s(u.get("private_key")),
            privateKeyPath = u.get("private_key_path", ""),
            privateKeyPassphrase = u.get("private_key_passphrase", ""),
            hostKey = list(u.get("host_key")), hostKeyAlgorithms = list(u.get("host_key_algorithms")),
            clientVersion = u.get("client_version", ""),
        )
    }

    private fun parseMieru(s: String): MieruBean? {
        val u = UrlParts.parse(s) ?: return null
        if (u.host.isEmpty() || u.query.isEmpty()) return null
        val protocols = u.all("protocol")
        var single = 0
        val ranges = ArrayList<String>()
        for (p0 in u.all("port")) {
            val p = p0.trim()
            if (p.isEmpty()) continue
            if (p.contains('-')) ranges += p
            else if (single == 0) single = p.toIntOrNull() ?: 0
            else ranges += "$p-$p"
        }
        return MieruBean(
            name = u.fragment, server = u.host, serverPort = single, dial = dial(u),
            transport = protocols.firstOrNull()?.uppercase() ?: "TCP",
            username = u.user, password = u.password, multiplexing = u.get("multiplexing", ""),
            trafficPattern = u.get("traffic-pattern", ""), serverPorts = ranges.joinToString(","),
        )
    }

    private fun fixAddress(list: List<String>): List<String> = list.map { it.trim() }.filter { it.isNotEmpty() }.map {
        if (it.contains('/')) it else if (it.contains(':')) "$it/128" else "$it/32"
    }

    private fun parseWireGuardLink(s: String): WireGuardBean? {
        val u = UrlParts.parse(s) ?: return null
        var w = WireGuardBean(name = u.fragment, dial = dial(u), privateKey = u.get("private_key", ""))
        val reserved = u.get("reserved")?.split("-")?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
        w = w.copy(
            peer = WireGuardPeer(
                address = u.host, port = u.port,
                publicKey = u.get("public_key") ?: u.get("peer_public_key") ?: "",
                preSharedKey = u.get("pre_shared_key", ""), reserved = reserved,
                persistentKeepalive = u.get("persistent_keepalive_interval", ""),
            ),
            address = fixAddress(u.get("local_address")?.split("-") ?: emptyList()),
        )
        u.get("mtu")?.toIntOrNull()?.let { w = w.copy(mtu = it) }
        u.get("use_system_interface")?.let { w = w.copy(system = it == "true") }
        u.get("workers")?.toIntOrNull()?.let { w = w.copy(workerCount = it) }
        u.get("udp_timeout")?.let { w = w.copy(udpTimeout = it) }
        if (u.get("enable_amnezia") == "true") w = w.copy(enableAmnezia = true)
        for ((k, v) in u.query) w = applyAmnezia(w, k.lowercase(), v)
        if (w.privateKey.isEmpty() || w.peer.publicKey.isEmpty() || w.peer.address.isEmpty()) return null
        return w
    }

    private fun applyAmnezia(w: WireGuardBean, key: String, v: String): WireGuardBean {
        fun i() = v.toIntOrNull() ?: 0
        val r = when (key) {
            "jc" -> w.copy(jc = i())
            "jmin" -> w.copy(jmin = i())
            "jmax" -> w.copy(jmax = i())
            "s1" -> w.copy(s1 = i())
            "s2" -> w.copy(s2 = i())
            "s3" -> w.copy(s3 = i())
            "s4" -> w.copy(s4 = i())
            "h1" -> w.copy(h1 = v)
            "h2" -> w.copy(h2 = v)
            "h3" -> w.copy(h3 = v)
            "h4" -> w.copy(h4 = v)
            "i1" -> w.copy(i1 = v)
            "i2" -> w.copy(i2 = v)
            "i3" -> w.copy(i3 = v)
            "i4" -> w.copy(i4 = v)
            "i5" -> w.copy(i5 = v)
            "header_protection_key", "headerprotectionkey" -> w.copy(headerProtectionKey = v)
            "content_padding_addition", "contentpaddingaddition" -> w.copy(contentPaddingAddition = v)
            "rekey_after_time", "rekeyaftertime" -> w.copy(rekeyAfterTime = v)
            "rekey_timeout", "rekeytimeout" -> w.copy(rekeyTimeout = v)
            "reject_after_time", "rejectaftertime" -> w.copy(rejectAfterTime = v)
            "keepalive_timeout", "keepalivetimeout" -> w.copy(keepaliveTimeout = v)
            "max_handshake_attempts", "maxhandshakeattempts" -> w.copy(maxHandshakeAttempts = v)
            else -> return w
        }
        return r.copy(enableAmnezia = true)
    }

    fun parseWireGuardConf(text: String, nameHint: String = ""): WireGuardBean? {
        if (!text.contains("[Interface]") || !text.contains("[Peer]")) return null
        var w = WireGuardBean(name = nameHint)
        for (line in text.lines()) {
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("#") || t.startsWith("[") || !t.contains('=')) continue
            val key = t.substringBefore('=').trim()
            val value = t.substringAfter('=').trim()
            when (key) {
                "PrivateKey" -> w = w.copy(privateKey = value)
                "Address" -> w = w.copy(address = value.replace(" ", "").split(",").filter { it.isNotEmpty() })
                "MTU" -> w = w.copy(mtu = value.toIntOrNull() ?: w.mtu)
                "PublicKey" -> w = w.copy(peer = w.peer.copy(publicKey = value))
                "PresharedKey" -> w = w.copy(peer = w.peer.copy(preSharedKey = value))
                "PersistentKeepalive" -> w = w.copy(peer = w.peer.copy(persistentKeepalive = value))
                "Reserved" -> w = w.copy(peer = w.peer.copy(reserved = value.split(',', '-').mapNotNull { it.trim().toIntOrNull() }))
                "Endpoint" -> {
                    val colon = value.lastIndexOf(':')
                    if (colon > 0) {
                        var host = value.substring(0, colon).trim()
                        if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length - 1)
                        w = w.copy(peer = w.peer.copy(address = host, port = value.substring(colon + 1).trim().toIntOrNull() ?: 0))
                    }
                }
                else -> w = applyAmnezia(w, key.lowercase(), value)
            }
        }
        w = w.copy(address = fixAddress(w.address))
        if (w.privateKey.isEmpty() || w.peer.publicKey.isEmpty()) return null
        return w
    }
}
