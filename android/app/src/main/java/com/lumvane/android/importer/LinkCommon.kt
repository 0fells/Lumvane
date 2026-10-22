package com.lumvane.android.importer

import com.lumvane.android.config.model.DialFields
import com.lumvane.android.config.model.Ech
import com.lumvane.android.config.model.MultiplexConfig
import com.lumvane.android.config.model.Reality
import com.lumvane.android.config.model.TcpBrutal
import com.lumvane.android.config.model.TlsConfig
import com.lumvane.android.config.model.TransportConfig
import com.lumvane.android.config.model.UTls
import com.lumvane.android.config.model.XrayHttpUpgrade
import com.lumvane.android.config.model.XrayGrpc
import com.lumvane.android.config.model.XrayMultiplex
import com.lumvane.android.config.model.XrayReality
import com.lumvane.android.config.model.XrayStreamSetting
import com.lumvane.android.config.model.XrayTls
import com.lumvane.android.config.model.XrayWs
import com.lumvane.android.config.model.XrayXhttp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

internal class Q {
    val items = ArrayList<Pair<String, String>>()
    fun add(k: String, v: String) { items += k to v }
    fun addIf(cond: Boolean, k: String, v: String) { if (cond) items += k to v }
    fun addNotEmpty(k: String, v: String) { if (v.isNotEmpty()) items += k to v }
    fun addAll(other: List<Pair<String, String>>) { items += other }
    fun isEmpty() = items.isEmpty()
    fun encoded(): String = items.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }

    companion object {
        private val KEEP = setOf(',', '/', ':')
        fun enc(s: String): String {
            val sb = StringBuilder()
            for (ch in s) {
                if (ch in KEEP) sb.append(ch) else sb.append(UrlParts.encode(ch.toString()))
            }
            return sb.toString()
        }
    }
}

internal fun truthy(s: String?): Boolean = s != null && (s == "1" || s.equals("true", true))

internal fun parseDial(u: UrlParts): DialFields = DialFields(
    reuseAddr = u.get("reuse_addr") == "true",
    connectTimeout = u.get("connect_timeout", ""),
    tcpFastOpen = u.get("tcp_fast_open") == "true",
    tcpMultiPath = u.get("tcp_multi_path") == "true",
    udpFragment = u.get("udp_fragment") == "true",
    bindInterface = u.get("bind_interface", ""),
    inet4BindAddress = u.get("inet4_bind_address", ""),
    inet6BindAddress = u.get("inet6_bind_address", ""),
)

internal fun exportDial(d: DialFields, q: Q) {
    q.addIf(d.reuseAddr, "reuse_addr", "true")
    q.addNotEmpty("connect_timeout", d.connectTimeout)
    q.addIf(d.tcpFastOpen, "tcp_fast_open", "true")
    q.addIf(d.tcpMultiPath, "tcp_multi_path", "true")
    q.addIf(d.udpFragment, "udp_fragment", "true")
    q.addNotEmpty("bind_interface", d.bindInterface)
    q.addNotEmpty("inet4_bind_address", d.inet4BindAddress)
    q.addNotEmpty("inet6_bind_address", d.inet6BindAddress)
}

private fun csv(s: String?): List<String> = if (s.isNullOrEmpty()) emptyList() else s.split(",")

internal fun parseTls(u: UrlParts): TlsConfig {
    var enabled = false
    u.get("security")?.let {
        val sec = it.trim().lowercase()
        enabled = sec == "tls" || sec == "reality" || sec == "1" || sec == "true"
    }
    var serverName = ""
    u.get("sni")?.let { serverName = it }
    u.get("peer")?.let { serverName = it }
    var insecure = false
    for (k in listOf("allowInsecure", "allow_insecure", "insecure")) u.get(k)?.let { insecure = truthy(it) }
    val fragment = u.get("tls_fragment")
    val tricks = u.get("tls_tricks")
    if (serverName.isNotEmpty()) enabled = true
    val fp = u.get("fp").orEmpty()
    val pbk = u.get("pbk")
    val pins = csv(u.get("tls_certificate_public_key_sha256")).ifEmpty { csv(u.get("pinSHA256")) }
    return TlsConfig(
        enabled = enabled,
        disableSni = truthy(u.get("disable_sni")),
        serverName = serverName,
        insecure = insecure,
        alpn = csv(u.get("alpn")),
        minVersion = u.get("tls_min_version", ""),
        maxVersion = u.get("tls_max_version", ""),
        cipherSuites = csv(u.get("tls_cipher_suites")),
        curvePreferences = csv(u.get("tls_curve_preferences")),
        certificate = csv(u.get("tls_certificate")),
        certificatePath = u.get("tls_certificate_path", ""),
        certificatePublicKeySha256 = pins,
        clientCertificate = csv(u.get("tls_client_certificate")),
        clientCertificatePath = u.get("tls_client_certificate_path", ""),
        clientKey = csv(u.get("tls_client_key")),
        clientKeyPath = u.get("tls_client_key_path", ""),
        fragment = fragment == "true",
        fragmentUnspecified = fragment == null,
        fragmentFallbackDelay = u.get("tls_fragment_fallback_delay", ""),
        recordFragment = u.get("tls_record_fragment") == "true",
        tlsTricks = tricks == "true",
        tlsTricksUnspecified = tricks == null,
        spoof = u.get("tls_spoof", ""),
        spoofMethod = u.get("tls_spoof_method", ""),
        ech = Ech(
            enabled = u.get("ech_enabled") == "true",
            config = csv(u.get("ech_config")),
            configPath = u.get("ech_config_path", ""),
            serverName = u.get("ech_server_name", ""),
        ),
        utls = UTls(enabled = fp.isNotEmpty(), fingerprint = fp),
        reality = if (pbk != null) Reality(true, pbk, u.get("sid", "")) else Reality(),
    )
}

internal fun exportTls(t: TlsConfig, q: Q) {
    if (!t.enabled) return
    q.add("security", if (t.reality.enabled) "reality" else "tls")
    q.addIf(t.disableSni, "disable_sni", "true")
    q.addNotEmpty("sni", t.serverName)
    q.addIf(t.insecure, "allowInsecure", "true")
    q.addIf(t.alpn.isNotEmpty(), "alpn", t.alpn.joinToString(","))
    q.addNotEmpty("tls_min_version", t.minVersion)
    q.addNotEmpty("tls_max_version", t.maxVersion)
    q.addIf(t.cipherSuites.isNotEmpty(), "tls_cipher_suites", t.cipherSuites.joinToString(","))
    q.addIf(t.curvePreferences.isNotEmpty(), "tls_curve_preferences", t.curvePreferences.joinToString(","))
    q.addIf(t.certificate.isNotEmpty(), "tls_certificate", t.certificate.joinToString(","))
    q.addNotEmpty("tls_certificate_path", t.certificatePath)
    q.addIf(t.certificatePublicKeySha256.isNotEmpty(), "tls_certificate_public_key_sha256", t.certificatePublicKeySha256.joinToString(","))
    q.addIf(t.clientCertificate.isNotEmpty(), "tls_client_certificate", t.clientCertificate.joinToString(","))
    q.addNotEmpty("tls_client_certificate_path", t.clientCertificatePath)
    q.addIf(t.clientKey.isNotEmpty(), "tls_client_key", t.clientKey.joinToString(","))
    q.addNotEmpty("tls_client_key_path", t.clientKeyPath)
    q.addIf(!t.fragmentUnspecified, "tls_fragment", if (t.fragment) "true" else "false")
    q.addNotEmpty("tls_fragment_fallback_delay", t.fragmentFallbackDelay)
    q.addIf(t.recordFragment, "tls_record_fragment", "true")
    q.addIf(!t.tlsTricksUnspecified, "tls_tricks", if (t.tlsTricks) "true" else "false")
    if (t.spoof.isNotEmpty()) {
        q.add("tls_spoof", t.spoof)
        q.addNotEmpty("tls_spoof_method", t.spoofMethod)
    }
    if (t.ech.enabled) {
        q.add("ech_enabled", "true")
        q.addIf(t.ech.config.isNotEmpty(), "ech_config", t.ech.config.joinToString(","))
        q.addNotEmpty("ech_config_path", t.ech.configPath)
        q.addNotEmpty("ech_server_name", t.ech.serverName)
    }
    if (t.utls.enabled) q.addNotEmpty("fp", t.utls.fingerprint)
    if (t.reality.enabled) {
        q.add("pbk", t.reality.publicKey)
        q.add("sid", t.reality.shortId)
    }
}

private fun parseHeaders(raw: String?): List<String> {
    if (raw.isNullOrEmpty()) return emptyList()
    val l = raw.split("|")
    return if (l.size % 2 != 0) emptyList() else l
}

internal fun parseTransport(u: UrlParts): TransportConfig {
    var type = ""
    var method = ""
    u.get("type")?.let {
        type = if (it == "raw") "tcp" else it
        if ((type == "tcp" && u.get("headerType") == "http") || type == "h2") {
            type = "http"
            method = "GET"
        }
        if (type == "tcp") type = ""
    }
    u.get("method")?.let { method = it }
    return TransportConfig(
        type = type,
        host = u.get("host", ""),
        path = u.get("path", ""),
        method = method,
        headers = parseHeaders(u.get("headers")),
        idleTimeout = u.get("idle_timeout", ""),
        pingTimeout = u.get("ping_timeout", ""),
        maxEarlyData = u.get("max_early_data")?.toIntOrNull() ?: 0,
        earlyDataHeaderName = u.get("early_data_header_name", ""),
        serviceName = u.get("serviceName", ""),
    )
}

internal fun exportTransport(t: TransportConfig, q: Q) {
    if (t.type.isEmpty() || t.type == "tcp" || t.type == "raw") return
    q.add("type", t.type)
    q.addNotEmpty("host", t.host)
    q.addNotEmpty("path", t.path)
    q.addNotEmpty("method", t.method)
    q.addIf(t.headers.isNotEmpty(), "headers", t.headers.joinToString("|"))
    q.addNotEmpty("idle_timeout", t.idleTimeout)
    q.addNotEmpty("ping_timeout", t.pingTimeout)
    q.addIf(t.maxEarlyData > 0, "max_early_data", t.maxEarlyData.toString())
    q.addNotEmpty("early_data_header_name", t.earlyDataHeaderName)
    q.addNotEmpty("serviceName", t.serviceName)
}

internal fun parseMux(u: UrlParts): MultiplexConfig {
    val mux = u.get("mux")
    return MultiplexConfig(
        enabled = mux == "true",
        unspecified = mux == null,
        protocol = u.get("mux_protocol", ""),
        maxConnections = u.get("mux_max_connections")?.toIntOrNull() ?: 0,
        minStreams = u.get("mux_min_streams")?.toIntOrNull() ?: 0,
        maxStreams = u.get("mux_max_streams")?.toIntOrNull() ?: 0,
        padding = u.get("mux_padding") == "true",
        brutal = TcpBrutal(
            enabled = u.get("brutal_enabled") == "true",
            upMbps = u.get("brutal_up_mbps")?.toIntOrNull() ?: 0,
            downMbps = u.get("brutal_down_mbps")?.toIntOrNull() ?: 0,
        ),
    )
}

internal fun exportMux(m: MultiplexConfig, q: Q) {
    if (m.unspecified) return
    q.add("mux", if (m.enabled) "true" else "false")
    if (!m.enabled) return
    q.addNotEmpty("mux_protocol", m.protocol)
    q.addIf(m.maxConnections > 0, "mux_max_connections", m.maxConnections.toString())
    q.addIf(m.minStreams > 0, "mux_min_streams", m.minStreams.toString())
    q.addIf(m.maxStreams > 0, "mux_max_streams", m.maxStreams.toString())
    q.addIf(m.padding, "mux_padding", "true")
    if (m.brutal.enabled) {
        q.add("brutal_enabled", "true")
        q.addIf(m.brutal.upMbps > 0, "brutal_up_mbps", m.brutal.upMbps.toString())
        q.addIf(m.brutal.downMbps > 0, "brutal_down_mbps", m.brutal.downMbps.toString())
    }
}

internal fun parseXrayMux(u: UrlParts): XrayMultiplex {
    val mux = u.get("mux")
    return XrayMultiplex(
        enabled = truthy(mux),
        useDefault = mux == null,
        concurrency = u.get("mux_concurrency")?.toIntOrNull() ?: 0,
        xudpConcurrency = u.get("mux_xudp_concurrency")?.toIntOrNull() ?: 16,
    )
}

internal fun exportXrayMux(m: XrayMultiplex, q: Q) {
    if (m.useDefault) return
    q.add("mux", if (m.enabled) "true" else "false")
    if (m.enabled) {
        q.addIf(m.concurrency > 0, "mux_concurrency", m.concurrency.toString())
        q.addIf(m.xudpConcurrency > 0, "mux_xudp_concurrency", m.xudpConcurrency.toString())
    }
}

private val XRAY_NETWORKS = XrayStreamSetting.NETWORKS

internal fun parseXrayStream(u: UrlParts): XrayStreamSetting {
    var network = u.get("type")?.replace("tcp", "raw") ?: "raw"
    if (network !in XRAY_NETWORKS) network = "raw"
    val security = u.get("security") ?: "none"
    val sni = u.get("sni").orEmpty()
    val peer = u.get("peer")
    var tls = XrayTls()
    var reality = XrayReality()
    if (security == "tls") {
        tls = XrayTls(
            serverName = u.get("server_name") ?: peer ?: sni,
            pinnedPeerCertSha256 = u.get("pcs", ""),
            verifyPeerCertByName = u.get("vcn", ""),
            alpn = csv(u.get("alpn")),
            fingerprint = u.get("fp", ""),
        )
    } else if (security == "reality") {
        reality = XrayReality(
            serverName = peer ?: sni,
            fingerprint = u.get("fp", ""),
            password = u.get("pbk", ""),
            shortId = u.get("sid", ""),
            spiderX = u.get("spx", ""),
        )
    }
    var s = XrayStreamSetting(network = network, security = security, tls = tls, reality = reality)
    s = when (network) {
        "xhttp" -> s.copy(xhttp = parseXhttpLink(u))
        "ws" -> s.copy(ws = XrayWs(
            path = u.get("path", ""), host = u.get("host", ""), ed = u.get("ed")?.toIntOrNull() ?: 0,
            headers = parseHeaders(u.get("headers")), heartbeatPeriod = u.get("heartbeat_period")?.toIntOrNull() ?: 0,
        ))
        "httpupgrade" -> s.copy(httpUpgrade = XrayHttpUpgrade(
            path = u.get("path", ""), ed = u.get("ed")?.toIntOrNull() ?: 0, host = u.get("host", ""),
            headers = parseHeaders(u.get("headers")),
        ))
        "grpc" -> s.copy(grpc = XrayGrpc(
            authority = u.get("authority", ""), serviceName = u.get("serviceName", ""), multiMode = u.get("mode") == "multi",
        ))
        else -> s
    }
    return s
}

internal fun exportXrayStream(s: XrayStreamSetting, q: Q) {
    if (s.network.isNotEmpty()) q.add("type", s.network)
    if (s.security.isNotEmpty()) q.add("security", s.security)
    if (s.security == "tls") {
        q.add("sni", s.tls.serverName)
        q.addNotEmpty("pcs", s.tls.pinnedPeerCertSha256)
        q.addNotEmpty("vcn", s.tls.verifyPeerCertByName)
        q.addIf(s.tls.alpn.isNotEmpty(), "alpn", s.tls.alpn.joinToString(","))
        q.addNotEmpty("fp", s.tls.fingerprint)
    }
    if (s.security == "reality") {
        q.add("sni", s.reality.serverName)
        q.addNotEmpty("fp", s.reality.fingerprint)
        q.addNotEmpty("pbk", s.reality.password)
        q.addNotEmpty("sid", s.reality.shortId)
        q.addNotEmpty("spx", s.reality.spiderX)
    }
    when (s.network) {
        "xhttp" -> {
            val x = s.xhttp
            q.addNotEmpty("host", x.host)
            q.addNotEmpty("path", x.path)
            q.addNotEmpty("mode", x.mode)
            val extra = xhttpExtraJson(x)
            if (extra.isNotEmpty()) q.add("extra", extra.toString())
            q.addIf(x.headers.isNotEmpty(), "headers", x.headers.joinToString("|"))
        }
        "ws" -> {
            q.addNotEmpty("host", s.ws.host)
            q.addIf(s.ws.path.isNotEmpty() && s.ws.path != "/", "path", s.ws.path)
            q.addIf(s.ws.ed > 0, "ed", s.ws.ed.toString())
            q.addIf(s.ws.headers.isNotEmpty(), "headers", s.ws.headers.joinToString("|"))
            q.addIf(s.ws.heartbeatPeriod > 0, "heartbeat_period", s.ws.heartbeatPeriod.toString())
        }
        "httpupgrade" -> {
            q.addNotEmpty("host", s.httpUpgrade.host)
            q.addIf(s.httpUpgrade.path.isNotEmpty() && s.httpUpgrade.path != "/", "path", s.httpUpgrade.path)
            q.addIf(s.httpUpgrade.ed > 0, "ed", s.httpUpgrade.ed.toString())
            q.addIf(s.httpUpgrade.headers.isNotEmpty(), "headers", s.httpUpgrade.headers.joinToString("|"))
        }
        "grpc" -> {
            q.addNotEmpty("authority", s.grpc.authority)
            q.addNotEmpty("serviceName", s.grpc.serviceName)
            q.addIf(s.grpc.multiMode, "mode", "multi")
        }
    }
}

private val EXTRA_KEYS = setOf(
    "headers", "xPaddingBytes", "xPaddingObfsMode", "xPaddingKey", "xPaddingHeader", "xPaddingPlacement",
    "xPaddingMethod", "uplinkHTTPMethod", "sessionPlacement", "sessionKey", "sessionIDPlacement", "sessionIDKey",
    "sessionIDTable", "sessionIDLength", "seqPlacement", "seqKey", "uplinkDataPlacement", "uplinkDataKey",
    "uplinkChunkSize", "noGRPCHeader", "noSSEHeader", "scMaxEachPostBytes", "scMinPostsIntervalMs",
    "scMaxBufferedPosts", "scStreamUpServerSecs", "serverMaxHeaderBytes", "xmux", "downloadSettings",
)
private val XMUX_KEYS = setOf("maxConcurrency", "maxConnections", "cMaxReuseTimes", "hMaxRequestTimes", "hMaxReusableSecs", "hKeepAlivePeriod")

private fun JsonElement?.prim(): String? = (this as? JsonPrimitive)?.contentOrNull
private fun JsonElement?.boolVal(): Boolean? = (this as? JsonPrimitive)?.let { p ->
    p.booleanOrNull ?: p.contentOrNull?.let { c -> if (c.equals("true", true) || c == "1") true else if (c.equals("false", true) || c == "0") false else null }
}
private fun JsonElement?.longVal(): Long? = (this as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toLong()

private val strictJson = Json { ignoreUnknownKeys = true }

internal fun parseJsonObjectOrNull(text: String): JsonObject? =
    runCatching { strictJson.parseToJsonElement(text).jsonObject }.getOrNull()

internal fun parseXhttpExtra(text: String, into: XrayXhttp): XrayXhttp? {
    var obj = parseJsonObjectOrNull(text)
    if (obj == null || obj.isEmpty()) {
        obj = parseJsonObjectOrNull(text.replace('\'', '"').replace("True", "true").replace("False", "false"))
    }
    if (obj == null || obj.isEmpty()) return null
    return applyXhttpExtra(into, obj)
}

internal fun applyXhttpExtra(base: XrayXhttp, obj: JsonObject): XrayXhttp {
    var x = base
    val raw = LinkedHashMap(x.rawExtra)
    for ((k, v) in obj) {
        if (k !in EXTRA_KEYS) raw[k] = v
    }
    fun s(key: String): String? = obj[key].prim()
    fun keepRawIfComplex(key: String) { obj[key]?.let { if (it !is JsonPrimitive) raw[key] = it } }
    obj["headers"]?.let { h ->
        if (h is JsonObject) x = x.copy(headers = h.entries.flatMap { listOf(it.key, it.value.prim().orEmpty()) })
        else if (h is kotlinx.serialization.json.JsonArray) x = x.copy(headers = h.mapNotNull { it.prim() })
    }
    s("xPaddingBytes")?.let { x = x.copy(xPaddingBytes = it) }; keepRawIfComplex("xPaddingBytes")
    obj["xPaddingObfsMode"].boolVal()?.let { x = x.copy(xPaddingObfsMode = it) }
    s("xPaddingKey")?.let { x = x.copy(xPaddingKey = it) }
    s("xPaddingHeader")?.let { x = x.copy(xPaddingHeader = it) }
    s("xPaddingPlacement")?.let { x = x.copy(xPaddingPlacement = it) }
    s("xPaddingMethod")?.let { x = x.copy(xPaddingMethod = it) }
    s("uplinkHTTPMethod")?.let { x = x.copy(uplinkHttpMethod = it) }
    s("sessionPlacement")?.let { x = x.copy(sessionIdPlacement = it) }
    s("sessionIDPlacement")?.let { x = x.copy(sessionIdPlacement = it) }
    s("sessionKey")?.let { x = x.copy(sessionIdKey = it) }
    s("sessionIDKey")?.let { x = x.copy(sessionIdKey = it) }
    s("sessionIDTable")?.let { x = x.copy(sessionIdTable = it) }
    s("sessionIDLength")?.let { x = x.copy(sessionIdLength = it) }; keepRawIfComplex("sessionIDLength")
    s("seqPlacement")?.let { x = x.copy(seqPlacement = it) }
    s("seqKey")?.let { x = x.copy(seqKey = it) }
    s("uplinkDataPlacement")?.let { x = x.copy(uplinkDataPlacement = it) }
    s("uplinkDataKey")?.let { x = x.copy(uplinkDataKey = it) }
    s("uplinkChunkSize")?.let { x = x.copy(uplinkChunkSize = it) }; keepRawIfComplex("uplinkChunkSize")
    obj["noGRPCHeader"].boolVal()?.let { x = x.copy(noGrpcHeader = it) }
    obj["noSSEHeader"].boolVal()?.let { x = x.copy(noSseHeader = it) }
    s("scMaxEachPostBytes")?.let { x = x.copy(scMaxEachPostBytes = it) }; keepRawIfComplex("scMaxEachPostBytes")
    s("scMinPostsIntervalMs")?.let { x = x.copy(scMinPostsIntervalMs = it) }; keepRawIfComplex("scMinPostsIntervalMs")
    obj["scMaxBufferedPosts"].longVal()?.let { x = x.copy(scMaxBufferedPosts = it) }
    s("scStreamUpServerSecs")?.let { x = x.copy(scStreamUpServerSecs = it) }; keepRawIfComplex("scStreamUpServerSecs")
    obj["serverMaxHeaderBytes"].longVal()?.let { x = x.copy(serverMaxHeaderBytes = it.toInt()) }
    obj["downloadSettings"]?.let { ds ->
        x = when (ds) {
            is JsonObject -> x.copy(downloadSettings = ds.toString())
            is JsonPrimitive -> x.copy(downloadSettings = ds.contentOrNull.orEmpty())
            else -> x
        }
    }
    (obj["xmux"] as? JsonObject)?.let { xm ->
        val rawX = LinkedHashMap(x.rawXmux)
        for ((k, v) in xm) if (k !in XMUX_KEYS) rawX[k] = v
        xm["maxConcurrency"].prim()?.let { x = x.copy(maxConcurrency = it) }
        xm["maxConnections"].prim()?.let { x = x.copy(maxConnections = it) }
        xm["cMaxReuseTimes"].prim()?.let { x = x.copy(cMaxReuseTimes = it) }
        xm["hMaxRequestTimes"].prim()?.let { x = x.copy(hMaxRequestTimes = it) }
        xm["hMaxReusableSecs"].prim()?.let { x = x.copy(hMaxReusableSecs = it) }
        xm["hKeepAlivePeriod"].longVal()?.let { x = x.copy(hKeepAlivePeriod = it) }
        x = x.copy(rawXmux = JsonObject(rawX))
    }
    return x.copy(rawExtra = JsonObject(raw))
}

private fun parseXhttpLink(u: UrlParts): XrayXhttp {
    var x = XrayXhttp(host = u.get("host", ""), path = u.get("path", ""), mode = u.get("mode") ?: "auto")
    u.get("extra")?.let { e -> parseXhttpExtra(e, x)?.let { x = it } }
    u.get("headers")?.let { x = x.copy(headers = parseHeaders(it)) }
    u.get("x_padding_bytes")?.let { x = x.copy(xPaddingBytes = it) }
    u.get("no_grpc_header")?.let { x = x.copy(noGrpcHeader = truthy(it)) }
    u.get("sc_max_each_post_bytes")?.let { x = x.copy(scMaxEachPostBytes = it) }
    u.get("sc_min_posts_interval_ms")?.let { x = x.copy(scMinPostsIntervalMs = it) }
    u.get("max_concurrency")?.let { x = x.copy(maxConcurrency = it) }
    u.get("max_connections")?.let { x = x.copy(maxConnections = it) }
    u.get("max_reuse_times")?.let { x = x.copy(cMaxReuseTimes = it) }
    u.get("max_request_times")?.let { x = x.copy(hMaxRequestTimes = it) }
    u.get("max_reusable_secs")?.let { x = x.copy(hMaxReusableSecs = it) }
    u.get("keep_alive_period")?.toLongOrNull()?.let { x = x.copy(hKeepAlivePeriod = it) }
    return x
}

internal fun xhttpExtraJson(x: XrayXhttp): JsonObject {
    val e = LinkedHashMap<String, JsonElement>(x.rawExtra)
    fun str(k: String, v: String) { if (v.isNotEmpty()) e[k] = JsonPrimitive(v) }
    if (x.headers.isNotEmpty()) {
        e["headers"] = buildJsonObject { x.headers.chunked(2).forEach { if (it.size == 2) put(it[0], JsonPrimitive(it[1])) } }
    }
    str("xPaddingBytes", x.xPaddingBytes)
    if (x.xPaddingObfsMode) e["xPaddingObfsMode"] = JsonPrimitive(true)
    str("xPaddingKey", x.xPaddingKey)
    str("xPaddingHeader", x.xPaddingHeader)
    str("xPaddingPlacement", x.xPaddingPlacement)
    str("xPaddingMethod", x.xPaddingMethod)
    str("uplinkHTTPMethod", x.uplinkHttpMethod)
    str("sessionIDPlacement", x.sessionIdPlacement)
    str("sessionIDKey", x.sessionIdKey)
    str("sessionIDTable", x.sessionIdTable)
    str("sessionIDLength", x.sessionIdLength)
    str("seqPlacement", x.seqPlacement)
    str("seqKey", x.seqKey)
    str("uplinkDataPlacement", x.uplinkDataPlacement)
    str("uplinkDataKey", x.uplinkDataKey)
    str("uplinkChunkSize", x.uplinkChunkSize)
    if (x.noGrpcHeader) e["noGRPCHeader"] = JsonPrimitive(true)
    if (x.noSseHeader) e["noSSEHeader"] = JsonPrimitive(true)
    str("scMaxEachPostBytes", x.scMaxEachPostBytes)
    str("scMinPostsIntervalMs", x.scMinPostsIntervalMs)
    if (x.scMaxBufferedPosts != 0L) e["scMaxBufferedPosts"] = JsonPrimitive(x.scMaxBufferedPosts)
    str("scStreamUpServerSecs", x.scStreamUpServerSecs)
    if (x.serverMaxHeaderBytes != 0) e["serverMaxHeaderBytes"] = JsonPrimitive(x.serverMaxHeaderBytes)
    if (x.mode != "stream-one" && x.downloadSettings.isNotEmpty()) {
        parseJsonObjectOrNull(x.downloadSettings)?.let { e["downloadSettings"] = it }
    } else if (x.mode == "stream-one") {
        e.remove("downloadSettings")
    }
    val xm = LinkedHashMap<String, JsonElement>(x.rawXmux)
    fun xs(k: String, v: String) { if (v.isNotEmpty()) xm[k] = JsonPrimitive(v) }
    xs("maxConcurrency", x.maxConcurrency)
    xs("maxConnections", x.maxConnections)
    xs("cMaxReuseTimes", x.cMaxReuseTimes)
    xs("hMaxRequestTimes", x.hMaxRequestTimes)
    xs("hMaxReusableSecs", x.hMaxReusableSecs)
    if (x.hKeepAlivePeriod != 0L) xm["hKeepAlivePeriod"] = JsonPrimitive(x.hKeepAlivePeriod)
    if (xm.isNotEmpty()) e["xmux"] = JsonObject(xm)
    return JsonObject(e)
}
