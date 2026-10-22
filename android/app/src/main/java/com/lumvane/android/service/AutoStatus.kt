package com.lumvane.android.service

import com.lumvane.android.config.outbounds.AutoTags
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class AutoMember(val tag: String, val profileId: Int?, val name: String, val delayMs: Int)

data class AutoStatus(val groupTag: String, val nowTag: String, val nowProfileId: Int?, val nowName: String, val nowDelayMs: Int, val members: List<AutoMember>) {
    val aliveCount get() = members.count { it.delayMs >= 0 }
}

object AutoStatusParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String, nameOf: (Int) -> String?): AutoStatus? {
        val groups = runCatching { json.parseToJsonElement(raw).jsonArray }.getOrNull() ?: return null
        val g = groups.firstOrNull()?.jsonObject ?: return null
        val members = (g["members"] as? JsonArray).orEmpty().map { e ->
            val o = e.jsonObject
            val tag = o["tag"]!!.jsonPrimitive.content
            val id = AutoTags.profileId(tag)
            val alive = o["alive"]?.jsonPrimitive?.content == "true"
            AutoMember(tag, id, id?.let(nameOf) ?: tag, if (alive) o["delay_ms"]?.jsonPrimitive?.int ?: 0 else -1)
        }
        val now = g["now"]?.jsonPrimitive?.content.orEmpty()
        val cur = members.firstOrNull { it.tag == now }
        return AutoStatus(g["tag"]!!.jsonPrimitive.content, now, cur?.profileId, cur?.name ?: now, cur?.delayMs ?: -1, members)
    }

    private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this?.toList() ?: emptyList()
}
