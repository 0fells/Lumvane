package com.lumvane.android.importer

import com.lumvane.android.data.Repositories

data class ImportOutcome(val added: Int, val skipped: Int, val ids: List<Long>)

object ImportInput {
    fun isSubscriptionUrl(text: String): Boolean {
        val t = text.trim()
        return !t.contains('\n') && (t.startsWith("http://", true) || t.startsWith("https://", true)) &&
            UrlParts.parse(t)?.host?.isNotEmpty() == true
    }

    fun groupNameForUrl(url: String): String = UrlParts.parse(url.trim())?.host?.ifEmpty { null } ?: url.trim()
}

class ProfileImporter(private val repos: Repositories) {
    suspend fun importText(text: String, groupId: Long, options: ParseOptions = ParseOptions()): ImportOutcome {
        val result = ContentParser.parse(text, options)
        if (result.isEmpty) return ImportOutcome(0, result.skipped, emptyList())
        val ids = repos.profiles.addAll(groupId, result.profiles.map { it.name to it.bean })
        return ImportOutcome(ids.size, result.skipped, ids)
    }
}
