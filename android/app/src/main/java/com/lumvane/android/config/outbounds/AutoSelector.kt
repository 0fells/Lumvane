package com.lumvane.android.config.outbounds

import com.lumvane.android.config.common.BuildEnv
import com.lumvane.android.config.common.putIfNotEmpty
import com.lumvane.android.config.common.strings
import com.lumvane.android.config.model.AutoSelectorBean
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun AutoSelectorBean.toSingBox(
    env: BuildEnv,
    groupTag: String,
    memberTags: List<String>,
    pinnedTag: String? = null,
): JsonObject = buildJsonObject {
    val s = env.settings
    put("type", "auto-selector")
    put("tag", groupTag)
    put("outbounds", strings(memberTags))
    put("url", testUrl.ifEmpty { s.testLatencyUrl })
    put("interval", "${intervalSec}s")
    put("bench_interval", "${benchIntervalSec}s")
    put("watch_interval", "${watchIntervalSec}s")
    put("active_size", activeSize)
    put("sampling", sampling)
    put("tolerance", toleranceMs)
    put("expected", expected)
    put("dial_retries", dialRetries)
    put("interrupt_exist_connections", interruptOnSwitch)
    if (!pinnedTag.isNullOrEmpty()) put("pinned", pinnedTag)
    if (maxRttMs > 0) put("max_rtt", "${maxRttMs}ms")
    put("connectivity_url", connectivityUrl.ifEmpty { s.testLatencyUrl })
    if (balance) {
        put("balance", true)
        putIfNotEmpty("balance_mode", balanceMode)
        put("balance_interval", "${balanceIntervalSec}s")
    }
}
