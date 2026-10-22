package com.lumvane.android.config.outbounds

import com.lumvane.android.config.common.*
import com.lumvane.android.config.model.DirectBean
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun DirectBean.toSingBox(@Suppress("UNUSED_PARAMETER") env: BuildEnv): JsonObject = buildJsonObject {
    put("type", "direct")
    for ((k, v) in dialJson(dial)) put(k, v)
}
