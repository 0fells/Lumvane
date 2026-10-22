package com.lumvane.android.sub

import com.lumvane.android.data.AppSettings
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.InputStream
import java.net.Proxy

class RuleSetCatalog(private val map: Map<String, String>) {
    val tags: Set<String> get() = map.keys
    val size get() = map.size
    fun urlFor(tag: String): String? = map[tag]
    operator fun contains(tag: String) = tag in map

    companion object {
        const val RESOURCE = "/com/lumvane/android/config/srslist.tsv"

        fun parse(input: InputStream): RuleSetCatalog {
            val m = LinkedHashMap<String, String>()
            input.bufferedReader().useLines { lines ->
                for (l in lines) {
                    val i = l.indexOf('\t')
                    if (i > 0) m[l.substring(0, i)] = l.substring(i + 1).trim()
                }
            }
            return RuleSetCatalog(m)
        }

        fun bundled(): RuleSetCatalog =
            RuleSetCatalog::class.java.getResourceAsStream(RESOURCE)?.use { parse(it) } ?: RuleSetCatalog(emptyMap())

        fun mirrorUrl(url: String, mirror: Int): String {
            if (mirror == 0) return url
            val m = Regex("^https://raw\\.githubusercontent\\.com/([^?#]*)").find(url) ?: return url
            val host = when (mirror) {
                2 -> "https://gcore.jsdelivr.net/gh"
                3 -> "https://quantil.jsdelivr.net/gh"
                4 -> "https://fastly.jsdelivr.net/gh"
                5 -> "https://cdn.jsdelivr.net/gh"
                else -> "https://testingcf.jsdelivr.net/gh"
            }
            val parts = m.groupValues[1].split('/').filter { it.isNotEmpty() }
            val sb = StringBuilder(host)
            parts.forEachIndexed { i, p -> if (i == 2) sb.append('@').append(p) else sb.append('/').append(p) }
            return sb.toString()
        }
    }
}

data class FileUpdate(val name: String, val ok: Boolean, val bytes: Long = 0, val error: String? = null)

class RuleSetUpdater(
    private val client: SubscriptionClient,
    private val catalog: RuleSetCatalog,
    private val dir: File,
) {
    fun fileFor(tag: String): File {
        require(SAFE_TAG.matches(tag)) { "invalid rule-set tag" }
        return File(dir, "$tag.srs")
    }

    fun missing(tags: Collection<String>) = tags.filter { it in catalog && !fileFor(it).isFile }

    suspend fun update(
        tags: Collection<String>,
        settings: AppSettings = AppSettings(),
        proxy: Proxy? = null,
        onlyMissing: Boolean = false,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): List<FileUpdate> {
        val out = ArrayList<FileUpdate>()
        val list = tags.distinct()
        for ((i, tag) in list.withIndex()) {
            val url = catalog.urlFor(tag)
            out += when {
                url == null -> FileUpdate(tag, false, error = "unknown rule-set")
                onlyMissing && fileFor(tag).isFile -> FileUpdate(tag, true, fileFor(tag).length())
                else -> try {
                    FileUpdate(tag, true, client.download(RuleSetCatalog.mirrorUrl(url, settings.rulesetMirror), fileFor(tag), settings, proxy, MAX_SRS_BYTES))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    FileUpdate(tag, false, error = e.message ?: e.javaClass.simpleName)
                }
            }
            onProgress(i + 1, list.size)
        }
        return out
    }

    suspend fun updateXrayGeo(settings: AppSettings, proxy: Proxy? = null): List<FileUpdate> =
        listOf("geoip.dat" to settings.xrayGeoipUrl, "geosite.dat" to settings.xrayGeositeUrl)
            .filter { it.second.isNotBlank() }
            .map { (name, url) ->
                try {
                    FileUpdate(name, true, client.download(url, File(dir, name), settings, proxy))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    FileUpdate(name, false, error = e.message ?: e.javaClass.simpleName)
                }
            }

    companion object {
        const val MAX_SRS_BYTES = 64L * 1024 * 1024
        private val SAFE_TAG = Regex("[A-Za-z0-9][A-Za-z0-9@._-]*")

        fun defaultDir(context: android.content.Context) = File(context.filesDir, "ruleset")
    }
}
