package com.lumvane.android.service

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ConnectionInfo(
    val id: String,
    val createdAt: Long = 0,
    val closedAt: Long = 0,
    val upload: Long = 0,
    val download: Long = 0,
    val network: String = "",
    val dest: String = "",
    val domain: String = "",
    val protocol: String = "",
    val outbound: String = "",
    val chain: List<String> = emptyList(),
    val rule: String = "",
    val uid: Int = -1,
    val packages: List<String> = emptyList(),
) {
    val owner: String?
        get() = packages.minOrNull() ?: uid.takeIf { it >= 0 }?.let { "uid:$it" }

    val host: String get() = domain.ifEmpty { dest }
}

@Serializable
data class ConnectionsSnapshot(val active: List<ConnectionInfo> = emptyList(), val closed: List<ConnectionInfo> = emptyList())

object ConnectionsParser {
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        namingStrategy = kotlinx.serialization.json.JsonNamingStrategy.SnakeCase
    }

    fun parse(raw: String): ConnectionsSnapshot? = runCatching { json.decodeFromString<ConnectionsSnapshot>(raw) }.getOrNull()
}

class AppTrafficAccumulator {
    private val counted = HashMap<String, LongArray>()
    private val pending = HashMap<String, LongArray>()

    fun add(snapshot: ConnectionsSnapshot) {
        val present = HashSet<String>()
        for (c in snapshot.active + snapshot.closed) {
            if (!present.add(c.id)) continue
            val seen = counted.getOrPut(c.id) { LongArray(2) }
            val up = (c.upload - seen[0]).coerceAtLeast(0)
            val down = (c.download - seen[1]).coerceAtLeast(0)
            seen[0] = maxOf(seen[0], c.upload)
            seen[1] = maxOf(seen[1], c.download)
            val owner = c.owner ?: continue
            if (up == 0L && down == 0L) continue
            val sum = pending.getOrPut(owner) { LongArray(2) }
            sum[0] += up
            sum[1] += down
        }
        counted.keys.retainAll(present)
    }

    fun drain(): Map<String, Pair<Long, Long>> {
        val out = pending.mapValues { (_, v) -> v[0] to v[1] }
        pending.clear()
        return out
    }

    fun reset() {
        counted.clear()
        pending.clear()
    }
}
