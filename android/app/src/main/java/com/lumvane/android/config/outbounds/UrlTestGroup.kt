package com.lumvane.android.config.outbounds

import com.lumvane.android.config.common.strings
import com.lumvane.android.data.AppSettings
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object AutoTags {
    private val INGRESS = Regex("^auto-(\\d+)-0$")

    fun prefix(profileId: Int) = "auto-$profileId"

    fun profileId(tag: String): Int? = INGRESS.matchEntire(tag)?.groupValues?.get(1)?.toIntOrNull()
}

const val AUTO_MIN_INTERVAL_SEC = 10
const val AUTO_MAX_INTERVAL_SEC = 3600
const val AUTO_MAX_TOLERANCE_MS = 30_000
const val AUTO_DEFAULT_TOLERANCE_MS = 75

fun AppSettings.autoIntervalSecEffective(): Int = autoIntervalSec.coerceIn(AUTO_MIN_INTERVAL_SEC, AUTO_MAX_INTERVAL_SEC)

fun AppSettings.autoToleranceEffective(): Int =
    (if (autoToleranceMs > 0) autoToleranceMs else AUTO_DEFAULT_TOLERANCE_MS).coerceIn(1, AUTO_MAX_TOLERANCE_MS)

fun buildUrlTestGroup(settings: AppSettings, groupTag: String, memberTags: List<String>, preferFirst: Boolean = true): JsonObject {
    val interval = settings.autoIntervalSecEffective()
    return buildJsonObject {
        put("type", "urltest")
        put("tag", groupTag)
        put("outbounds", strings(memberTags))
        put("url", settings.autoTestUrl.ifEmpty { settings.testLatencyUrl })
        put("interval", "${interval}s")
        put("tolerance", settings.autoToleranceEffective())
        if (preferFirst) put("prefer_first", true)
        put("idle_timeout", "${maxOf(1800, interval * 3)}s")
        put("interrupt_exist_connections", true)
    }
}
