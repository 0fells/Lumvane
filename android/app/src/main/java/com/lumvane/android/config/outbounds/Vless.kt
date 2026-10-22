package com.lumvane.android.config.outbounds

import com.lumvane.android.config.common.*
import com.lumvane.android.config.model.VlessBean
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun VlessBean.toSingBox(env: BuildEnv): JsonObject = buildJsonObject {
    outboundBase("vless", server, serverPort, dial, tls, env)
    putIfNotEmpty("uuid", uuid)
    putIfNotEmpty("flow", flow)
    put("packet_encoding", packetEncoding)
    putObjectIfNotEmpty("tls", tlsJson(tls, env))
    putObjectIfNotEmpty("transport", transportJson(transport))
    putObjectIfNotEmpty("multiplex", multiplexJson(multiplex, env))
}
