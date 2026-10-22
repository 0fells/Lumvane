package com.lumvane.android.config.outbounds

import com.lumvane.android.config.common.*
import com.lumvane.android.config.model.VmessBean
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun VmessBean.toSingBox(env: BuildEnv): JsonObject = buildJsonObject {
    outboundBase("vmess", server, serverPort, dial, tls, env)
    putIfNotEmpty("uuid", uuid)
    if (security != "auto" && security.isNotEmpty()) put("security", security)
    putIfPositive("alter_id", alterId)
    putIfTrue("global_padding", globalPadding)
    putIfTrue("authenticated_length", authenticatedLength)
    put("packet_encoding", packetEncoding)
    putObjectIfNotEmpty("tls", tlsJson(tls, env))
    putObjectIfNotEmpty("transport", transportJson(transport))
    putObjectIfNotEmpty("multiplex", multiplexJson(multiplex, env))
}
