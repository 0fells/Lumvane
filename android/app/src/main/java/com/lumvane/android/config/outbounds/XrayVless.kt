package com.lumvane.android.config.outbounds

import com.lumvane.android.config.common.*
import com.lumvane.android.config.model.XrayMultiplex
import com.lumvane.android.config.model.XrayStreamSetting
import com.lumvane.android.config.model.XrayVlessBean
import com.lumvane.android.config.model.XrayXhttp
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun XrayVlessBean.toSingBox(@Suppress("UNUSED_PARAMETER") env: BuildEnv): JsonObject = buildJsonObject {
    put("type", "socks")
    put("server", "127.0.0.1")
}

fun XrayVlessBean.toXray(env: BuildEnv): JsonObject = buildJsonObject {
    put("protocol", "vless")
    put("settings", buildJsonObject {
        put("address", server)
        put("port", serverPort)
        put("id", uuid)
        put("encryption", encryption)
        if (flow.isNotEmpty() && flow != "none") put("flow", flow)
    })
    putObjectIfNotEmpty("streamSettings", xrayStreamJson(streamSetting, env))
    putObjectIfNotEmpty("mux", xrayMuxJson(multiplex, env))
}

internal fun xrayMuxJson(m: XrayMultiplex, env: BuildEnv): JsonObject {
    val s = env.settings
    val enabled = if (m.useDefault) s.xrayMuxDefaultOn else m.enabled
    if (!enabled) return JsonObject(emptyMap())
    return buildJsonObject {
        put("enabled", true)
        val own = !m.useDefault
        val concurrency = if (own && m.concurrency > 0) m.concurrency else s.xrayMuxConcurrency
        if (concurrency > 0) put("concurrency", concurrency)
        if (m.xudpConcurrency > 0) put("xudpConcurrency", m.xudpConcurrency)
    }
}

internal fun xrayStreamJson(st: XrayStreamSetting, env: BuildEnv): JsonObject {
    val globalFp = env.settings.utlsFingerprint
    return buildJsonObject {
        put("network", st.network)
        put("security", st.security)
        when (st.security) {
            "tls" -> put("tlsSettings", buildJsonObject {
                put("serverName", st.tls.serverName)
                putIfNotEmpty("pinnedPeerCertSha256", st.tls.pinnedPeerCertSha256)
                putIfNotEmpty("verifyPeerCertByName", st.tls.verifyPeerCertByName)
                putStrings("alpn", st.tls.alpn)
                val fp = st.tls.fingerprint.ifEmpty { globalFp }
                putIfNotEmpty("fingerprint", fp)
            })
            "reality" -> put("realitySettings", buildJsonObject {
                put("serverName", st.reality.serverName)
                val fp = st.reality.fingerprint.ifEmpty { globalFp }
                putIfNotEmpty("fingerprint", fp)
                putIfNotEmpty("password", st.reality.password)
                putIfNotEmpty("shortId", st.reality.shortId)
                putIfNotEmpty("spiderX", st.reality.spiderX)
            })
        }
        when (st.network) {
            "xhttp" -> put("xhttpSettings", xhttpJson(st.xhttp))
            "ws" -> put("wsSettings", buildJsonObject {
                if (st.ws.path.isNotEmpty()) {
                    put("path", if (st.ws.ed > 0) st.ws.path + "?ed=" + st.ws.ed else st.ws.path)
                }
                putIfNotEmpty("host", st.ws.host)
                putObjectIfNotEmpty("headers", headersObject(st.ws.headers))
                putIfPositive("heartbeatPeriod", st.ws.heartbeatPeriod)
            })
            "httpupgrade" -> put("httpupgradeSettings", buildJsonObject {
                if (st.httpUpgrade.path.isNotEmpty()) {
                    put("path", if (st.httpUpgrade.ed > 0) st.httpUpgrade.path + "?ed=" + st.httpUpgrade.ed else st.httpUpgrade.path)
                }
                putIfNotEmpty("host", st.httpUpgrade.host)
                putObjectIfNotEmpty("headers", headersObject(st.httpUpgrade.headers))
            })
            "grpc" -> put("grpcSettings", buildJsonObject {
                putIfNotEmpty("authority", st.grpc.authority)
                putIfNotEmpty("serviceName", st.grpc.serviceName)
                putIfTrue("multiMode", st.grpc.multiMode)
            })
        }
    }
}

internal fun xhttpJson(x: XrayXhttp): JsonObject = buildJsonObject {
    putIfNotEmpty("host", x.host)
    putIfNotEmpty("path", x.path)
    putIfNotEmpty("mode", x.mode)

    val extra = LinkedHashMap<String, JsonElement>(x.rawExtra)
    fun str(key: String, v: String) { if (v.isEmpty()) extra.remove(key) else extra[key] = JsonPrimitive(v) }
    fun bool(key: String, v: Boolean) { if (v) extra[key] = JsonPrimitive(true) else extra.remove(key) }
    fun num(key: String, v: Long) { if (v != 0L) extra[key] = JsonPrimitive(v) else extra.remove(key) }

    val headers = headersObject(x.headers)
    if (headers.isNotEmpty()) extra["headers"] = headers else extra.remove("headers")
    str("xPaddingBytes", x.xPaddingBytes)
    bool("xPaddingObfsMode", x.xPaddingObfsMode)
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
    bool("noGRPCHeader", x.noGrpcHeader)
    bool("noSSEHeader", x.noSseHeader)
    str("scMaxEachPostBytes", x.scMaxEachPostBytes)
    str("scMinPostsIntervalMs", x.scMinPostsIntervalMs)
    num("scMaxBufferedPosts", x.scMaxBufferedPosts)
    str("scStreamUpServerSecs", x.scStreamUpServerSecs)
    num("serverMaxHeaderBytes", x.serverMaxHeaderBytes.toLong())
    if (x.mode == "stream-one") {
        extra.remove("downloadSettings")
    } else {
        val ds = if (x.downloadSettings.isNotEmpty()) parseObjectOrNull(x.downloadSettings) else null
        if (ds != null && ds.isNotEmpty()) extra["downloadSettings"] = ds else extra.remove("downloadSettings")
    }

    val xmux = LinkedHashMap<String, JsonElement>(x.rawXmux)
    fun xs(key: String, v: String) { if (v.isEmpty()) xmux.remove(key) else xmux[key] = JsonPrimitive(v) }
    xs("maxConcurrency", x.maxConcurrency)
    xs("maxConnections", x.maxConnections)
    xs("cMaxReuseTimes", x.cMaxReuseTimes)
    xs("hMaxRequestTimes", x.hMaxRequestTimes)
    xs("hMaxReusableSecs", x.hMaxReusableSecs)
    if (x.hKeepAlivePeriod != 0L) xmux["hKeepAlivePeriod"] = JsonPrimitive(x.hKeepAlivePeriod) else xmux.remove("hKeepAlivePeriod")
    if (xmux.isNotEmpty()) extra["xmux"] = JsonObject(xmux) else extra.remove("xmux")

    if (extra.isNotEmpty()) put("extra", JsonObject(extra))
}
