package com.lumvane.android.config.common

import java.math.BigInteger
import java.net.InetAddress

val TUN_BYPASSABLE_PRIVATE_RANGES = listOf("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "169.254.0.0/16", "224.0.0.0/4")

private class Prefix(val bytes: Int, val addr: BigInteger, val bits: Int) {
    val hostBits get() = bytes * 8 - bits
    val first: BigInteger get() = addr.shiftRight(hostBits).shiftLeft(hostBits)
    val last: BigInteger get() = first.add(BigInteger.ONE.shiftLeft(hostBits)).subtract(BigInteger.ONE)
}

private val V4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

private fun parsePrefix(text: String): Prefix? {
    val host = text.substringBefore('/').trim()
    val isV4 = V4.matches(host)
    val isV6 = !isV4 && host.contains(':') && host.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '.' }
    if (!isV4 && !isV6) return null
    val bytes = runCatching { InetAddress.getByName(host).address }.getOrNull() ?: return null
    val max = bytes.size * 8
    val bits = if (text.contains('/')) text.substringAfter('/').trim().toIntOrNull() ?: return null else max
    if (bits < 0 || bits > max) return null
    return Prefix(bytes.size, BigInteger(1, bytes), bits)
}

fun prefixesOverlap(lhs: String, rhs: String): Boolean {
    val a = parsePrefix(lhs) ?: return false
    val b = parsePrefix(rhs) ?: return false
    if (a.bytes != b.bytes) return false
    return a.first <= b.last && b.first <= a.last
}
