package com.lumvane.android.service

import org.json.JSONArray
import org.json.JSONObject

data class TunSpec(
    val inet4Address: List<String>,
    val inet6Address: List<String>,
    val dnsServers: List<String>,
    val mtu: Int,
    val inet4Routes: List<String>,
    val inet6Routes: List<String>,
    val includePackage: List<String>,
    val excludePackage: List<String>,
    val httpProxy: HttpProxy?,
) {
    data class HttpProxy(val server: String, val port: Int, val bypassDomain: List<String>)

    companion object {
        fun parse(json: String): TunSpec {
            val o = JSONObject(json)
            val hp = o.optJSONObject("http_proxy")?.let {
                HttpProxy(it.optString("server"), it.optInt("port"), it.strings("bypass_domain"))
            }
            return TunSpec(
                inet4Address = o.strings("inet4_address"),
                inet6Address = o.strings("inet6_address"),
                dnsServers = o.strings("dns_servers"),
                mtu = o.optInt("mtu", 9000),
                inet4Routes = o.strings("inet4_route_range").ifEmpty { o.strings("inet4_route_address") },
                inet6Routes = o.strings("inet6_route_range").ifEmpty { o.strings("inet6_route_address") },
                includePackage = o.strings("include_package"),
                excludePackage = o.strings("exclude_package"),
                httpProxy = hp,
            )
        }

        private fun JSONObject.strings(key: String): List<String> = optJSONArray(key).toList()

        private fun JSONArray?.toList(): List<String> =
            if (this == null) emptyList() else List(length()) { getString(it) }
    }
}

fun splitCidr(cidr: String): Pair<String, Int>? {
    val i = cidr.lastIndexOf('/')
    if (i <= 0) return null
    val prefix = cidr.substring(i + 1).toIntOrNull() ?: return null
    return cidr.substring(0, i) to prefix
}

fun formatSpeed(bytesPerSec: Long): String = formatBytes(bytesPerSec) + "/s"

fun formatBytes(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = bytes.coerceAtLeast(0).toDouble()
    var u = 0
    while (v >= 1024 && u < units.size - 1) {
        v /= 1024
        u++
    }
    return if (u == 0) "${v.toLong()} B" else String.format(java.util.Locale.US, "%.1f %s", v, units[u])
}
