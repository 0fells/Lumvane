package com.lumvane.android.config.outbounds

import com.lumvane.android.config.common.*
import com.lumvane.android.config.model.TuicBean
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun TuicBean.toSingBox(env: BuildEnv): JsonObject = buildJsonObject {
    val tlsCfg = tls.copy(enabled = true)
    outboundBase("tuic", server, serverPort, dial, tlsCfg, env)
    putIfNotEmpty("uuid", uuid)
    putIfNotEmpty("password", password)
    putIfNotEmpty("congestion_control", congestionControl)
    if (udpRelayMode.isNotEmpty() && !udpOverStream) put("udp_relay_mode", udpRelayMode)
    putIfTrue("udp_over_stream", udpOverStream)
    putIfTrue("zero_rtt_handshake", zeroRttHandshake)
    putIfNotEmpty("heartbeat", heartbeat)
    put("tls", tlsJson(tlsCfg, env, utlsSupported = false))
}
