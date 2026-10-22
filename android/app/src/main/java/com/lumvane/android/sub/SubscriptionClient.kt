package com.lumvane.android.sub

import com.lumvane.android.data.AppSettings
import com.lumvane.android.importer.UrlParts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.Proxy
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class DeviceDetails(val hwid: String, val os: String = "Android", val osVersion: String = "", val model: String = "")

class SubscriptionException(message: String, cause: Throwable? = null) : IOException(message, cause)

data class FetchResult(
    val body: String,
    val userInfo: String,
    val updateIntervalHours: Int?,
    val title: String?,
) {
    val info get() = SubscriptionInfo.parse(userInfo)
}

class SubscriptionClient(
    private val base: OkHttpClient = defaultClient(),
    private val device: (() -> DeviceDetails)? = null,
) {
    private fun clientFor(settings: AppSettings, proxy: Proxy?): OkHttpClient {
        if (!settings.netInsecure && proxy == null) return base
        val b = base.newBuilder()
        if (proxy != null) b.proxy(proxy)
        if (settings.netInsecure) {
            val tm = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            val ctx = SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), SecureRandom()) }
            b.sslSocketFactory(ctx.socketFactory, tm).hostnameVerifier { _, _ -> true }
        }
        return b.build()
    }

    suspend fun fetch(url: String, settings: AppSettings = AppSettings(), proxy: Proxy? = null): FetchResult {
        val builder = try {
            Request.Builder().url(url)
        } catch (e: IllegalArgumentException) {
            throw SubscriptionException("invalid URL", e)
        }
        builder.header("User-Agent", userAgent(settings))
        if (settings.subSendHwid) device?.invoke()?.let { d -> hwidHeaders(settings, d).forEach { (k, v) -> builder.header(k, v) } }
        val resp = try {
            clientFor(settings, proxy).newCall(builder.build()).await()
        } catch (e: IOException) {
            throw SubscriptionException(e.message ?: "network error", e)
        }
        return withContext(Dispatchers.IO) {
            resp.use { r ->
                if (!r.isSuccessful) throw SubscriptionException("HTTP ${r.code}")
                val bytes = r.body?.byteStream()?.let { readLimited(it) } ?: ByteArray(0)
                FetchResult(
                    body = String(bytes, Charsets.UTF_8),
                    userInfo = r.header("Subscription-UserInfo").orEmpty(),
                    updateIntervalHours = r.header("Profile-Update-Interval")?.trim()?.toDoubleOrNull()?.toInt(),
                    title = r.header("Profile-Title")?.let { decodeTitle(it) },
                )
            }
        }
    }

    suspend fun download(url: String, dest: java.io.File, settings: AppSettings = AppSettings(), proxy: Proxy? = null, maxBytes: Long = 256L * 1024 * 1024): Long {
        val req = try {
            Request.Builder().url(url).header("User-Agent", userAgent(settings)).build()
        } catch (e: IllegalArgumentException) {
            throw SubscriptionException("invalid URL", e)
        }
        val resp = try {
            clientFor(settings, proxy).newCall(req).await()
        } catch (e: IOException) {
            throw SubscriptionException(e.message ?: "network error", e)
        }
        return withContext(Dispatchers.IO) {
            resp.use { r ->
                if (!r.isSuccessful) throw SubscriptionException("HTTP ${r.code}")
                val body = r.body ?: throw SubscriptionException("empty response")
                dest.parentFile?.mkdirs()
                val tmp = java.io.File(dest.parentFile, dest.name + ".tmp")
                var total = 0L
                try {
                    tmp.outputStream().use { out ->
                        body.byteStream().use { input ->
                            val buf = ByteArray(32 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                total += n
                                if (total > maxBytes) throw SubscriptionException("file too large")
                                out.write(buf, 0, n)
                            }
                        }
                    }
                    if (total == 0L) throw SubscriptionException("empty file")
                    if (dest.exists() && !dest.delete()) throw SubscriptionException("cannot replace ${dest.name}")
                    if (!tmp.renameTo(dest)) throw SubscriptionException("cannot move downloaded file")
                } finally {
                    tmp.delete()
                }
                total
            }
        }
    }

    private fun readLimited(input: java.io.InputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > MAX_BYTES) throw SubscriptionException("response too large")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    companion object {
        const val MAX_BYTES = 64L * 1024 * 1024
        const val DEFAULT_VERSION = "1.0.0"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .followSslRedirects(false)
            .addNetworkInterceptor { ch ->
                val r = ch.request()
                if (r.url.host == ch.call().request().url.host) return@addNetworkInterceptor ch.proceed(r)
                ch.proceed(r.newBuilder().apply { HWID_HEADERS.forEach { removeHeader(it) } }.build())
            }
            .build()

        private val HWID_HEADERS = listOf("x-hwid", "x-device-os", "x-ver-os", "x-device-model")

        fun userAgent(settings: AppSettings, version: String = DEFAULT_VERSION): String =
            settings.userAgent.ifBlank { "Lumvane/$version" }

        fun hwidHeaders(settings: AppSettings, d: DeviceDetails): Map<String, String> {
            val custom = HashMap<String, String>()
            for (pair in settings.subCustomHwidParams.split(',')) {
                val eq = pair.indexOf('=')
                if (eq <= 0) continue
                val k = pair.substring(0, eq).trim().lowercase()
                val v = pair.substring(eq + 1).trim()
                if (v.isNotEmpty() && '\n' !in v && '\r' !in v && v.length < 1000 && k in setOf("hwid", "os", "osversion", "model")) custom[k] = v
            }
            val out = LinkedHashMap<String, String>()
            (custom["hwid"] ?: d.hwid).takeIf { it.isNotEmpty() }?.let { out["x-hwid"] = it }
            (custom["os"] ?: d.os).takeIf { it.isNotEmpty() }?.let { out["x-device-os"] = it }
            (custom["osversion"] ?: d.osVersion).takeIf { it.isNotEmpty() }?.let { out["x-ver-os"] = it }
            (custom["model"] ?: d.model).takeIf { it.isNotEmpty() }?.let { out["x-device-model"] = it }
            return out
        }

        private fun decodeTitle(v: String): String {
            val t = v.trim()
            if (t.startsWith("base64:")) UrlParts.b64DecodeToString(t.removePrefix("base64:"))?.let { return it }
            return t
        }
    }
}

internal suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            cont.resume(response) { _, _, _ -> response.close() }
        }
    })
}
