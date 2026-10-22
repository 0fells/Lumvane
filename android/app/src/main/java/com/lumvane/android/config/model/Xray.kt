package com.lumvane.android.config.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class XrayMultiplex(
    val enabled: Boolean = false,
    val useDefault: Boolean = true,
    val concurrency: Int = 0,
    val xudpConcurrency: Int = 16,
)

@Serializable
data class XrayTls(
    val serverName: String = "",
    val pinnedPeerCertSha256: String = "",
    val verifyPeerCertByName: String = "",
    val alpn: List<String> = emptyList(),
    val fingerprint: String = "",
)

@Serializable
data class XrayReality(
    val serverName: String = "",
    val fingerprint: String = "",
    val password: String = "",
    val shortId: String = "",
    val spiderX: String = "",
)

@Serializable
data class XrayXhttp(
    val host: String = "",
    val path: String = "",
    val mode: String = "auto",
    val rawExtra: JsonObject = JsonObject(emptyMap()),
    val headers: List<String> = emptyList(),
    val xPaddingBytes: String = "",
    val xPaddingObfsMode: Boolean = false,
    val xPaddingKey: String = "",
    val xPaddingHeader: String = "",
    val xPaddingPlacement: String = "",
    val xPaddingMethod: String = "",
    val uplinkHttpMethod: String = "",
    val sessionIdPlacement: String = "",
    val sessionIdKey: String = "",
    val sessionIdTable: String = "",
    val sessionIdLength: String = "",
    val seqPlacement: String = "",
    val seqKey: String = "",
    val uplinkDataPlacement: String = "",
    val uplinkDataKey: String = "",
    val uplinkChunkSize: String = "",
    val noGrpcHeader: Boolean = false,
    val noSseHeader: Boolean = false,
    val scMaxEachPostBytes: String = "",
    val scMinPostsIntervalMs: String = "",
    val scMaxBufferedPosts: Long = 0,
    val scStreamUpServerSecs: String = "",
    val serverMaxHeaderBytes: Int = 0,
    val rawXmux: JsonObject = JsonObject(emptyMap()),
    val maxConcurrency: String = "",
    val maxConnections: String = "",
    val cMaxReuseTimes: String = "",
    val hMaxRequestTimes: String = "",
    val hMaxReusableSecs: String = "",
    val hKeepAlivePeriod: Long = 0,
    val downloadSettings: String = "",
)

@Serializable
data class XrayWs(
    val path: String = "",
    val host: String = "",
    val ed: Int = 0,
    val headers: List<String> = emptyList(),
    val heartbeatPeriod: Int = 0,
)

@Serializable
data class XrayHttpUpgrade(
    val path: String = "",
    val ed: Int = 0,
    val host: String = "",
    val headers: List<String> = emptyList(),
)

@Serializable
data class XrayGrpc(
    val authority: String = "",
    val serviceName: String = "",
    val multiMode: Boolean = false,
)

@Serializable
data class XrayStreamSetting(
    val network: String = "raw",
    val security: String = "none",
    val tls: XrayTls = XrayTls(),
    val reality: XrayReality = XrayReality(),
    val xhttp: XrayXhttp = XrayXhttp(),
    val ws: XrayWs = XrayWs(),
    val httpUpgrade: XrayHttpUpgrade = XrayHttpUpgrade(),
    val grpc: XrayGrpc = XrayGrpc(),
) {
    companion object {
        val NETWORKS = listOf("raw", "xhttp", "ws", "httpupgrade", "grpc")
        val XHTTP_MODES = listOf("auto", "packet-up", "stream-up", "stream-one")
    }
}
