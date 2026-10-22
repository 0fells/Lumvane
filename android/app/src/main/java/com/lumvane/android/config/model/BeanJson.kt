package com.lumvane.android.config.model

import kotlinx.serialization.json.Json

object BeanJson {
    val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        coerceInputValues = true
        classDiscriminator = "type"
    }

    fun encode(bean: OutboundBean): String = json.encodeToString(OutboundBean.serializer(), bean)

    fun decode(text: String): OutboundBean = json.decodeFromString(OutboundBean.serializer(), text)

    fun decodeOrNull(text: String): OutboundBean? = runCatching { decode(text) }.getOrNull()
}
