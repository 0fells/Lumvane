package com.lumvane.android.config.outbounds

import com.lumvane.android.config.common.*
import com.lumvane.android.config.model.JuicityBean
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun JuicityBean.toSingBox(env: BuildEnv): JsonObject = buildJsonObject {
    val tlsCfg = tls.copy(enabled = true)
    outboundBase("juicity", server, serverPort, dial, tlsCfg, env)
    putIfNotEmpty("uuid", uuid)
    putIfNotEmpty("password", password)
    put("tls", tlsJson(tlsCfg, env, utlsSupported = false))
}
