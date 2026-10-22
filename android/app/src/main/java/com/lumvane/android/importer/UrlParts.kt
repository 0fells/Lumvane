package com.lumvane.android.importer

import java.io.ByteArrayOutputStream
import java.util.Base64 as JBase64

class UrlParts(
    val scheme: String,
    val user: String,
    val password: String,
    val hasPassword: Boolean,
    val host: String,
    val port: Int,
    val path: String,
    val query: List<Pair<String, String>>,
    val fragment: String,
    val rawQuery: String,
    val portText: String = "",
) {
    fun has(key: String) = query.any { it.first == key }
    fun get(key: String): String? = query.lastOrNull { it.first == key }?.second
    fun get(key: String, default: String): String = get(key) ?: default
    fun all(key: String): List<String> = query.filter { it.first == key }.map { it.second }
    fun bool(key: String): Boolean? = get(key)?.let { it.equals("true", true) || it == "1" }

    companion object {
        fun parse(link: String): UrlParts? {
            val schemeEnd = link.indexOf("://")
            if (schemeEnd <= 0) return null
            val scheme = link.substring(0, schemeEnd).lowercase()
            var rest = link.substring(schemeEnd + 3)
            var fragment = ""
            rest.indexOf('#').let { if (it >= 0) { fragment = percentDecode(rest.substring(it + 1)); rest = rest.substring(0, it) } }
            var rawQuery = ""
            rest.indexOf('?').let { if (it >= 0) { rawQuery = rest.substring(it + 1); rest = rest.substring(0, it) } }
            var path = ""
            val slash = rest.indexOf('/')
            var authority = rest
            if (slash >= 0) { path = rest.substring(slash); authority = rest.substring(0, slash) }

            var user = ""
            var password = ""
            var hasPassword = false
            val at = authority.lastIndexOf('@')
            var hostPort = authority
            if (at >= 0) {
                val ui = authority.substring(0, at)
                hostPort = authority.substring(at + 1)
                val colon = ui.indexOf(':')
                if (colon >= 0) {
                    user = percentDecode(ui.substring(0, colon))
                    password = percentDecode(ui.substring(colon + 1))
                    hasPassword = true
                } else {
                    user = percentDecode(ui)
                }
            }
            var host: String
            var port = 0
            var portText = ""
            if (hostPort.startsWith("[")) {
                val close = hostPort.indexOf(']')
                if (close < 0) return null
                host = hostPort.substring(1, close)
                val after = hostPort.substring(close + 1)
                if (after.startsWith(":")) portText = after.substring(1)
                if (after.startsWith(":")) port = after.substring(1).toIntOrNull()?.takeIf { it in 1..65535 } ?: 0
            } else {
                val colon = hostPort.lastIndexOf(':')
                if (colon >= 0) {
                    host = hostPort.substring(0, colon)
                    portText = hostPort.substring(colon + 1)
                    port = hostPort.substring(colon + 1).toIntOrNull()?.takeIf { it in 1..65535 } ?: 0
                } else {
                    host = hostPort
                }
            }
            host = percentDecode(host)
            val query = if (rawQuery.isEmpty()) emptyList() else rawQuery.split('&').filter { it.isNotEmpty() }.map {
                val eq = it.indexOf('=')
                if (eq < 0) percentDecode(it) to "" else percentDecode(it.substring(0, eq)) to percentDecode(it.substring(eq + 1))
            }
            return UrlParts(scheme, user, password, hasPassword, host, port, path, query, fragment, rawQuery, portText)
        }

        fun percentDecode(s: String): String {
            if (!s.contains('%')) return s
            val out = ByteArrayOutputStream(s.length)
            var i = 0
            val bytes = s.toByteArray(Charsets.UTF_8)
            while (i < bytes.size) {
                val b = bytes[i].toInt()
                if (b == '%'.code && i + 2 < bytes.size && hex(bytes[i + 1].toInt()) >= 0 && hex(bytes[i + 2].toInt()) >= 0) {
                    out.write(hex(bytes[i + 1].toInt()) * 16 + hex(bytes[i + 2].toInt()))
                    i += 3
                } else {
                    out.write(b)
                    i++
                }
            }
            return out.toString(Charsets.UTF_8.name())
        }

        private fun hex(c: Int): Int = when (c) {
            in '0'.code..'9'.code -> c - '0'.code
            in 'a'.code..'f'.code -> c - 'a'.code + 10
            in 'A'.code..'F'.code -> c - 'A'.code + 10
            else -> -1
        }

        private val UNRESERVED = (('a'..'z') + ('A'..'Z') + ('0'..'9') + listOf('-', '.', '_', '~')).toSet()

        fun encode(s: String): String {
            val sb = StringBuilder()
            for (b in s.toByteArray(Charsets.UTF_8)) {
                val c = (b.toInt() and 0xff).toChar()
                if (c in UNRESERVED) sb.append(c) else sb.append('%').append("%02X".format(b.toInt() and 0xff))
            }
            return sb.toString()
        }

        fun b64Decode(input: String): ByteArray? {
            val s = input.trim().replace("\r", "").replace("\n", "")
            if (s.isEmpty()) return null
            if (s.any { !(it.isLetterOrDigit() && it.code < 128) && it != '+' && it != '/' && it != '-' && it != '_' && it != '=' }) return null
            val normalized = s.trimEnd('=').replace('-', '+').replace('_', '/')
            if (normalized.length % 4 == 1) return null
            return try {
                JBase64.getDecoder().decode(normalized + "=".repeat((4 - normalized.length % 4) % 4))
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        fun b64DecodeToString(input: String): String? = b64Decode(input)?.let { String(it, Charsets.UTF_8) }

        fun b64UrlEncode(bytes: ByteArray): String = JBase64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        fun b64Encode(bytes: ByteArray): String = JBase64.getEncoder().encodeToString(bytes)
    }
}
