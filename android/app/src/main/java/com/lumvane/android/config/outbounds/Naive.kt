package com.lumvane.android.config.outbounds

import com.lumvane.android.config.common.*
import com.lumvane.android.config.model.NaiveBean
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun NaiveBean.toSingBox(env: BuildEnv): JsonObject = buildJsonObject {
    val tlsCfg = tls.copy(enabled = true)
    outboundBase("naive", server, serverPort, dial, tlsCfg, env)
    putIfNotEmpty("username", username)
    putIfNotEmpty("password", password)
    putIfTrue("udp_over_tcp", uot)
    if (quic) {
        put("quic", true)
        putIfNotEmpty("quic_congestion_control", congestionControl)
    }
    put("tls", tlsJson(tlsCfg, env, utlsSupported = false))
}
