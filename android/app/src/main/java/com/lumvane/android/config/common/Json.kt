package com.lumvane.android.config.common

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun strings(items: Iterable<String>): JsonArray = JsonArray(items.map { JsonPrimitive(it) })

fun ints(items: Iterable<Int>): JsonArray = JsonArray(items.map { JsonPrimitive(it) })

fun JsonObjectBuilder.putIfNotEmpty(key: String, value: String) {
    if (value.isNotEmpty()) put(key, value)
}

fun JsonObjectBuilder.putIfPositive(key: String, value: Int) {
    if (value > 0) put(key, value)
}

fun JsonObjectBuilder.putIfTrue(key: String, value: Boolean) {
    if (value) put(key, true)
}

fun JsonObjectBuilder.putStrings(key: String, items: List<String>) {
    if (items.isNotEmpty()) put(key, strings(items))
}

fun JsonObjectBuilder.putObjectIfNotEmpty(key: String, value: JsonObject) {
    if (value.isNotEmpty()) put(key, value)
}

fun JsonObjectBuilder.merge(other: JsonObject) {
    for ((k, v) in other) put(k, v)
}

fun JsonObject.with(key: String, value: JsonElement): JsonObject =
    JsonObject(LinkedHashMap(this).also { it[key] = value })

fun JsonObject.with(key: String, value: String): JsonObject = with(key, JsonPrimitive(value))

fun JsonObject.without(key: String): JsonObject = JsonObject(LinkedHashMap(this).also { it.remove(key) })

fun parseObjectOrNull(text: String): JsonObject? =
    runCatching { kotlinx.serialization.json.Json.parseToJsonElement(text) as? JsonObject }.getOrNull()

fun jsonObject(block: JsonObjectBuilder.() -> Unit): JsonObject = buildJsonObject(block)

fun headersObject(items: List<String>): JsonObject {
    if (items.isEmpty()) return JsonObject(emptyMap())
    val colonStyle = items.first().contains(':')
    return buildJsonObject {
        if (colonStyle) {
            for (item in items) {
                val i = item.indexOf(':')
                if (i <= 0) continue
                put(item.substring(0, i).trim(), item.substring(i + 1).trim())
            }
        } else if (items.size % 2 == 0) {
            for (i in items.indices step 2) put(items[i], items[i + 1])
        }
    }
}
