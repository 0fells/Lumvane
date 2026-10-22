package com.lumvane.android.config.outbounds

import com.lumvane.android.config.common.*
import com.lumvane.android.config.model.AnyTlsBean
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun AnyTlsBean.toSingBox(env: BuildEnv): JsonObject = buildJsonObject {
    val tlsCfg = tls.copy(enabled = true)
    outboundBase("anytls", server, serverPort, dial, tlsCfg, env)
    putIfNotEmpty("password", password)
    putIfNotEmpty("idle_session_check_interval", idleSessionCheckInterval)
    putIfNotEmpty("idle_session_timeout", idleSessionTimeout)
    putIfPositive("min_idle_session", minIdleSession)
    put("tls", tlsJson(tlsCfg, env))
}
