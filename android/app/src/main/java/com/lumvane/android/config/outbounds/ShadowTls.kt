package com.lumvane.android.config.outbounds

import com.lumvane.android.config.common.*
import com.lumvane.android.config.model.ShadowTlsBean
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun ShadowTlsBean.toSingBox(env: BuildEnv): JsonObject = buildJsonObject {
    val tlsCfg = tls.copy(enabled = true)
    outboundBase("shadowtls", server, serverPort, dial, tlsCfg, env)
    put("version", version)
    if (version > 1) putIfNotEmpty("password", password)
    put("tls", tlsJson(tlsCfg, env))
}
