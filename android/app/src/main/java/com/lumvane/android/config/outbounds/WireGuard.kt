package com.lumvane.android.config.outbounds

import com.lumvane.android.config.common.*
import com.lumvane.android.config.model.WireGuardBean
import com.lumvane.android.config.model.WireGuardPeer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val RANGE = Regex("^\\d+-\\d+$")

private fun keepalive(value: String): JsonPrimitive? {
    if (value.isEmpty()) return null
    value.toIntOrNull()?.let { return if (it > 0) JsonPrimitive(it) else null }
    return if (RANGE.matches(value)) JsonPrimitive(value) else null
}

private fun peerJson(p: WireGuardPeer): JsonObject = buildJsonObject {
    putIfNotEmpty("address", p.address)
    putIfPositive("port", p.port)
    putIfNotEmpty("public_key", p.publicKey)
    putIfNotEmpty("pre_shared_key", p.preSharedKey)
    if (p.reserved.isNotEmpty()) put("reserved", ints(p.reserved))
    keepalive(p.persistentKeepalive)?.let { put("persistent_keepalive_interval", it) }
    put("allowed_ips", strings(listOf("0.0.0.0/0", "::/0")))
}

private fun WireGuardBean.amneziaJson(): JsonObject {
    if (!enableAmnezia) return JsonObject(emptyMap())
    return buildJsonObject {
        putIfPositive("jc", jc)
        putIfPositive("jmin", jmin)
        putIfPositive("jmax", jmax)
        putIfPositive("s1", s1)
        putIfPositive("s2", s2)
        putIfPositive("s3", s3)
        putIfPositive("s4", s4)
        putIfNotEmpty("h1", h1)
        putIfNotEmpty("h2", h2)
        putIfNotEmpty("h3", h3)
        putIfNotEmpty("h4", h4)
        putIfNotEmpty("i1", i1)
        putIfNotEmpty("i2", i2)
        putIfNotEmpty("i3", i3)
        putIfNotEmpty("i4", i4)
        putIfNotEmpty("i5", i5)
        putIfNotEmpty("header_protection_key", headerProtectionKey)
        putIfNotEmpty("content_padding_addition", contentPaddingAddition)
        putIfNotEmpty("rekey_after_time", rekeyAfterTime)
        putIfNotEmpty("rekey_timeout", rekeyTimeout)
        putIfNotEmpty("reject_after_time", rejectAfterTime)
        putIfNotEmpty("keepalive_timeout", keepaliveTimeout)
        putIfNotEmpty("max_handshake_attempts", maxHandshakeAttempts)
    }
}

fun WireGuardBean.toSingBox(@Suppress("UNUSED_PARAMETER") env: BuildEnv): JsonObject = buildJsonObject {
    put("type", "wireguard")
    for ((k, v) in dialJson(dial)) put(k, v)
    putIfNotEmpty("private_key", privateKey)
    putStrings("address", address)
    putIfPositive("mtu", mtu)
    putIfTrue("system", system)
    putIfPositive("workers", workerCount)
    putIfNotEmpty("udp_timeout", udpTimeout)
    putObjectIfNotEmpty("amnezia_wg", amneziaJson())
    val peerObj = peerJson(peer)
    if (peerObj.isNotEmpty()) put("peers", kotlinx.serialization.json.JsonArray(listOf(peerObj)))
}
