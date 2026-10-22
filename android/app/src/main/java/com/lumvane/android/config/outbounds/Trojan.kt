package com.lumvane.android.config.outbounds

import com.lumvane.android.config.common.*
import com.lumvane.android.config.model.TrojanBean
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

fun TrojanBean.toSingBox(env: BuildEnv): JsonObject = buildJsonObject {
    outboundBase("trojan", server, serverPort, dial, tls, env)
    putIfNotEmpty("password", password)
    putObjectIfNotEmpty("tls", tlsJson(tls, env))
    putObjectIfNotEmpty("transport", transportJson(transport))
    putObjectIfNotEmpty("multiplex", multiplexJson(multiplex, env))
}
