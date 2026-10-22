package com.lumvane.android.config.outbounds

import com.lumvane.android.config.common.*
import com.lumvane.android.config.model.SocksBean
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun SocksBean.toSingBox(env: BuildEnv): JsonObject = buildJsonObject {
    outboundBase("socks", server, serverPort, dial, null, env)
    putIfNotEmpty("username", username)
    putIfNotEmpty("password", password)
    if (version == 4) put("version", "4")
    putIfTrue("uot", uot)
}
