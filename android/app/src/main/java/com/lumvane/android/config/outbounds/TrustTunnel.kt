package com.lumvane.android.config.outbounds

import com.lumvane.android.config.common.*
import com.lumvane.android.config.model.TrustTunnelBean
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun TrustTunnelBean.toSingBox(env: BuildEnv): JsonObject = buildJsonObject {
    val tlsCfg = tls.copy(enabled = true)
    outboundBase("trusttunnel", server, serverPort, dial, tlsCfg, env)
    putIfNotEmpty("username", username)
    putIfNotEmpty("password", password)
    putIfTrue("health_check", healthCheck)
    if (quic) {
        put("quic", true)
        putIfNotEmpty("quic_congestion_control", congestionControl)
    }
    put("tls", tlsJson(tlsCfg, env))
}
