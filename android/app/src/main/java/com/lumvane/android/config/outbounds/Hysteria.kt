package com.lumvane.android.config.outbounds

import com.lumvane.android.config.common.*
import com.lumvane.android.config.model.HysteriaBean
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun hysteriaPorts(ports: List<String>): List<String> =
    ports.flatMap { it.split(",") }.map { it.trim() }.filter { it.isNotEmpty() }.map { v ->
        val range = v.split(":", "-")
        if (range.size == 2) "${range[0]}:${range[1]}" else "$v:$v"
    }

internal fun hopDuration(v: String): String =
    v.trim().let { if (it.isNotEmpty() && it.all(Char::isDigit)) it + "s" else it }

fun HysteriaBean.toSingBox(env: BuildEnv): JsonObject = buildJsonObject {
    val tlsCfg = tls.copy(enabled = true)
    outboundBase(if (isV2) "hysteria2" else "hysteria", server, serverPort, dial, tlsCfg, env)
    val hop = hysteriaPorts(serverPorts)
    if (hop.isNotEmpty()) put("server_ports", strings(hop))
    if (hopInterval.isNotEmpty()) put("hop_interval", hopDuration(hopInterval))
    putIfPositive("up_mbps", upMbps)
    putIfPositive("down_mbps", downMbps)
    if (!isV2) {
        putIfNotEmpty("obfs", obfs)
        if (auth.isNotEmpty()) {
            if (authType == "BASE64") put("auth", auth)
            if (authType == "STRING") put("auth_str", auth)
        }
        putIfPositive("connection_receive_window", recvWindowConn)
        putIfPositive("stream_receive_window", recvWindow)
        putIfTrue("disable_path_mtu_discovery", disableMtuDiscovery)
    } else {
        if (obfs.isNotEmpty()) {
            val gecko = obfsType == "gecko"
            put("obfs", buildJsonObject {
                put("type", if (gecko) "gecko" else "salamander")
                put("password", obfs)
                if (gecko) {
                    putIfPositive("min_packet_size", obfsMinPacketSize)
                    putIfPositive("max_packet_size", obfsMaxPacketSize)
                }
            })
        }
        putIfNotEmpty("password", password)
        if (hopIntervalMax.isNotEmpty()) put("hop_interval_max", hopDuration(hopIntervalMax))
        putIfNotEmpty("bbr_profile", bbrProfile)
        putIfTrue("disable_chrome_parrot", disableChromeParrot)
    }
    put("tls", tlsJson(tlsCfg, env, utlsSupported = false))
}
