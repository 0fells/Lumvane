package com.lumvane.android.config.outbounds

import com.lumvane.android.config.common.*
import com.lumvane.android.config.model.TailscaleBean
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun TailscaleBean.toSingBox(env: BuildEnv): JsonObject = buildJsonObject {
    put("type", "tailscale")
    for ((k, v) in dialJson(dial)) put(k, v)
    val state = if (env.dataDir.isNotEmpty()) stateDirectory.replace("\$HOME", env.dataDir) else stateDirectory
    putIfNotEmpty("state_directory", state)
    putIfNotEmpty("auth_key", authKey)
    putIfNotEmpty("control_url", controlUrl)
    putIfTrue("ephemeral", ephemeral)
    putIfNotEmpty("hostname", hostname)
    putIfTrue("accept_routes", acceptRoutes)
    putIfNotEmpty("exit_node", exitNode)
    putIfTrue("exit_node_allow_lan_access", exitNodeAllowLanAccess)
    putStrings("advertise_routes", advertiseRoutes)
    putIfTrue("advertise_exit_node", advertiseExitNode)
}
