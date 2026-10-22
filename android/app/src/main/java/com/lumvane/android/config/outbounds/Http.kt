package com.lumvane.android.config.outbounds

import com.lumvane.android.config.common.*
import com.lumvane.android.config.model.HttpBean
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

fun HttpBean.toSingBox(env: BuildEnv): JsonObject = buildJsonObject {
    outboundBase("http", server, serverPort, dial, tls, env)
    putIfNotEmpty("username", username)
    putIfNotEmpty("password", password)
    putIfNotEmpty("path", path)
    putObjectIfNotEmpty("headers", headersObject(headers))
    putObjectIfNotEmpty("tls", tlsJson(tls, env))
}
