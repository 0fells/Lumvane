package com.lumvane.android.config.common

import com.lumvane.android.config.model.DialFields
import com.lumvane.android.config.model.MultiplexConfig
import com.lumvane.android.config.model.TlsConfig
import com.lumvane.android.config.model.TransportConfig
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val EMPTY = JsonObject(emptyMap())

fun dialJson(d: DialFields): JsonObject = buildJsonObject {
    putIfTrue("reuse_addr", d.reuseAddr)
    putIfNotEmpty("connect_timeout", d.connectTimeout)
    putIfTrue("tcp_fast_open", d.tcpFastOpen)
    putIfTrue("tcp_multi_path", d.tcpMultiPath)
    putIfTrue("udp_fragment", d.udpFragment)
    putIfNotEmpty("bind_interface", d.bindInterface)
    putIfNotEmpty("inet4_bind_address", d.inet4BindAddress)
    putIfNotEmpty("inet6_bind_address", d.inet6BindAddress)
}

fun fragmentEffectivelyOn(t: TlsConfig, env: BuildEnv): Boolean =
    t.fragment || (t.fragmentUnspecified && env.settings.fragmentDefaultOn)

fun tlsTricksEffectivelyOn(t: TlsConfig, env: BuildEnv): Boolean =
    t.tlsTricks || (t.tlsTricksUnspecified && env.settings.tlsTricksDefaultOn)

fun tlsJson(t: TlsConfig, env: BuildEnv, utlsSupported: Boolean = true): JsonObject {
    if (!t.enabled) return EMPTY
    val s = env.settings
    return buildJsonObject {
        put("enabled", true)
        putIfTrue("disable_sni", t.disableSni)
        putIfNotEmpty("server_name", t.serverName)
        putIfTrue("insecure", t.insecure)
        putStrings("alpn", t.alpn)
        putIfNotEmpty("min_version", t.minVersion)
        putIfNotEmpty("max_version", t.maxVersion)
        putStrings("cipher_suites", t.cipherSuites)
        putStrings("curve_preferences", t.curvePreferences)
        putStrings("certificate", t.certificate)
        putIfNotEmpty("certificate_path", t.certificatePath)
        putStrings("certificate_public_key_sha256", t.certificatePublicKeySha256)
        putStrings("client_certificate", t.clientCertificate)
        putIfNotEmpty("client_certificate_path", t.clientCertificatePath)
        putStrings("client_key", t.clientKey)
        putIfNotEmpty("client_key_path", t.clientKeyPath)
        if (fragmentEffectivelyOn(t, env) && s.fragmentImplementation != "custom") {
            put("fragment", true)
            putIfNotEmpty("fragment_fallback_delay", t.fragmentFallbackDelay)
        }
        putIfTrue("record_fragment", t.recordFragment)
        if (tlsTricksEffectivelyOn(t, env)) put("tls_tricks", buildJsonObject { put("mixedcase_sni", true) })
        if (t.ech.enabled) {
            put("ech", buildJsonObject {
                put("enabled", true)
                putStrings("config", t.ech.config)
                putIfNotEmpty("config_path", t.ech.configPath)
                putIfNotEmpty("query_server_name", t.ech.serverName)
            })
        }
        val utls = if (utlsSupported) utlsJson(t, env) else EMPTY
        if (utls.isNotEmpty()) {
            put("utls", utls)
        } else if (t.reality.enabled) {
            put("utls", buildJsonObject { put("enabled", true); put("fingerprint", "random") })
        }
        if (t.reality.enabled || t.reality.publicKey.isNotEmpty()) {
            put("reality", buildJsonObject {
                put("enabled", true)
                put("public_key", t.reality.publicKey)
                putIfNotEmpty("short_id", t.reality.shortId)
            })
        }
    }
}

private fun utlsJson(t: TlsConfig, env: BuildEnv): JsonObject {
    val own = t.utls
    if (own.enabled && own.fingerprint.isNotEmpty()) {
        return buildJsonObject { put("enabled", true); put("fingerprint", own.fingerprint) }
    }
    val global = env.settings.utlsFingerprint
    if (global.isNotEmpty()) {
        return buildJsonObject { put("enabled", true); put("fingerprint", global) }
    }
    return if (own.enabled) buildJsonObject { put("enabled", true) } else EMPTY
}

fun multiplexJson(m: MultiplexConfig, env: BuildEnv): JsonObject {
    val s = env.settings
    val enabled = if (m.unspecified) s.muxDefaultOn else m.enabled
    if (!enabled) return EMPTY
    val own = !m.unspecified
    return buildJsonObject {
        put("enabled", true)
        val protocol = if (own) m.protocol else ""
        put("protocol", protocol.ifEmpty { s.muxProtocol })
        val maxConn = if (own) m.maxConnections else 0
        val minStreams = if (own) m.minStreams else 0
        val maxStreams = if (own) m.maxStreams else 0
        putIfPositive("max_connections", maxConn)
        putIfPositive("min_streams", minStreams)
        if (maxConn == 0 && minStreams == 0 && maxStreams == 0) put("max_streams", s.muxConcurrency)
        else putIfPositive("max_streams", maxStreams)
        if (s.muxPadding || (own && m.padding)) put("padding", true)
        if (own && m.brutal.enabled) {
            put("brutal", buildJsonObject {
                put("enabled", true)
                put("up_mbps", if (m.brutal.upMbps <= 0) 1 else m.brutal.upMbps)
                put("down_mbps", if (m.brutal.downMbps <= 0) 1 else m.brutal.downMbps)
            })
        }
    }
}

fun transportJson(t: TransportConfig): JsonObject {
    if (t.type.isEmpty() || t.type == "tcp" || t.type == "raw") return EMPTY
    return buildJsonObject {
        put("type", t.type)
        if (t.path.contains("?ed=")) {
            put("path", t.path.substringBefore("?ed="))
            put("max_early_data", t.path.substringAfter("?ed=").toIntOrNull() ?: 0)
            put("early_data_header_name", "Sec-WebSocket-Protocol")
        } else {
            putIfNotEmpty("path", t.path)
            putIfPositive("max_early_data", t.maxEarlyData)
            putIfNotEmpty("early_data_header_name", t.earlyDataHeaderName)
        }
        putIfNotEmpty("method", t.method)
        val headers = headersObject(t.headers)
        val merged = if (t.host.isNotEmpty() && t.type == "ws") {
            JsonObject(LinkedHashMap(headers).also { it["Host"] = JsonPrimitive(t.host) })
        } else headers
        if (merged.isNotEmpty()) put("headers", merged)
        if (t.host.isNotEmpty() && (t.type == "http" || t.type == "httpupgrade")) put("host", t.host)
        putIfNotEmpty("idle_timeout", t.idleTimeout)
        putIfNotEmpty("ping_timeout", t.pingTimeout)
        putIfNotEmpty("service_name", t.serviceName)
    }
}

fun JsonObjectBuilder.outboundBase(
    type: String,
    server: String,
    port: Int,
    dial: DialFields,
    tls: TlsConfig?,
    env: BuildEnv,
) {
    put("type", type)
    putIfNotEmpty("server", server)
    putIfPositive("server_port", port)
    val s = env.settings
    val customFragment = tls != null && tls.enabled && fragmentEffectivelyOn(tls, env) && s.fragmentImplementation == "custom"
    for ((k, v) in dialJson(dial)) {
        if (customFragment && k == "tcp_fast_open") continue
        put(k, v)
    }
    if (customFragment) {
        put("tls_fragment", buildJsonObject {
            put("enabled", true)
            put("size", s.fragmentSize.ifEmpty { "10-100" })
            put("sleep", s.fragmentSleep.ifEmpty { "2-5" })
        })
    }
}
