package com.lumvane.android.config.outbounds

import com.lumvane.android.config.common.*
import com.lumvane.android.config.model.MieruBean
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun MieruBean.toSingBox(env: BuildEnv): JsonObject = buildJsonObject {
    outboundBase("mieru", server, serverPort, dial, null, env)
    put("transport", transport.ifEmpty { "TCP" })
    putIfNotEmpty("username", username)
    putIfNotEmpty("password", password)
    putIfNotEmpty("multiplexing", multiplexing)
    putIfNotEmpty("traffic_pattern", trafficPattern)
    val ports = serverPorts.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    if (ports.isNotEmpty()) put("server_ports", strings(ports))
}
