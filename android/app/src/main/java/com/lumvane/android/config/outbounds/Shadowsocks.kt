package com.lumvane.android.config.outbounds

import com.lumvane.android.config.common.*
import com.lumvane.android.config.model.ShadowsocksBean
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

fun ShadowsocksBean.toSingBox(env: BuildEnv): JsonObject = buildJsonObject {
    val pluginName = if (plugin.contains(";")) plugin.substringBefore(";") else plugin
    val opts = if (plugin.contains(";")) plugin.substringAfter(";") else pluginOpts
    outboundBase("shadowsocks", server, serverPort, dial, null, env)
    putIfNotEmpty("method", method)
    putIfNotEmpty("password", password)
    putIfNotEmpty("plugin", pluginName)
    putIfNotEmpty("plugin_opts", opts)
    putIfTrue("udp_over_tcp", uot)
    putObjectIfNotEmpty("multiplex", multiplexJson(multiplex, env))
}
