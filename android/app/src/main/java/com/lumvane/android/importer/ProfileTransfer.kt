package com.lumvane.android.importer

import com.lumvane.android.config.model.AutoSelectorBean
import com.lumvane.android.config.model.BeanJson
import com.lumvane.android.config.model.ChainBean
import com.lumvane.android.config.model.OutboundBean
import com.lumvane.android.data.GroupEntity
import com.lumvane.android.data.Profile
import com.lumvane.android.data.Repositories
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class TransferProfile(val name: String = "", val bean: OutboundBean, val id: Long = 0)

@Serializable
data class TransferGroup(
    val name: String = "",
    val url: String = "",
    val info: String = "",
    val archive: Boolean = false,
    val skipAutoUpdate: Boolean = false,
    val autoClearUnavailable: Boolean = false,
    val profiles: List<TransferProfile> = emptyList(),
    val id: Long = 0,
)

@Serializable
data class TransferFile(
    val format: String = FORMAT,
    val version: Int = 2,
    val groups: List<TransferGroup> = emptyList(),
) {
    companion object { const val FORMAT = "lumvane-android" }
}

data class TransferStats(val groups: Int, val profiles: Int, val skipped: Int)

fun remapReferences(bean: OutboundBean, profiles: Map<Long, Long>, groups: Map<Long, Long>): OutboundBean {
    fun ids(l: List<Int>) = l.mapNotNull { profiles[it.toLong()]?.toInt() }
    return when (bean) {
        is ChainBean -> ids(bean.list).let { l -> bean.copy(list = if (l.size == bean.list.size) l else emptyList()) }
        is AutoSelectorBean -> bean.copy(
            gid = groups[bean.gid.toLong()]?.toInt() ?: -1,
            pool = ids(bean.pool),
            pinnedId = profiles[bean.pinnedId.toLong()]?.toInt() ?: -1,
            lastBuilt = ids(bean.lastBuilt),
        )
        else -> bean
    }
}

fun isReferenceBean(b: OutboundBean) = b is ChainBean || b is AutoSelectorBean

class ProfileTransfer(private val repos: Repositories) {

    suspend fun exportGroups(groupIds: List<Long>? = null): String {
        val groups = repos.groups.getAll().filter { groupIds == null || it.id in groupIds }
        val out = groups.map { g -> toTransfer(g, repos.profiles.getByGroup(g.id).map { it.toTransfer() }) }
        return encode(TransferFile(groups = withLinked(out)))
    }

    suspend fun exportProfiles(profileIds: List<Long>, groupName: String = "Export"): String {
        val items = profileIds.mapNotNull { repos.profiles.get(it) }.map { it.toTransfer() }
        return encode(TransferFile(groups = withLinked(listOf(TransferGroup(name = groupName, profiles = items)))))
    }

    private fun Profile.toTransfer() = TransferProfile(name, bean, id)

    private suspend fun withLinked(groups: List<TransferGroup>): List<TransferGroup> {
        val have = groups.flatMap { g -> g.profiles.map { it.id } }.toMutableSet()
        val linked = ArrayList<TransferProfile>()
        var pending = groups.flatMap { it.profiles }
        while (pending.isNotEmpty()) {
            val next = ArrayList<TransferProfile>()
            for (p in pending) {
                for (ref in referencedProfileIds(p.bean)) {
                    if (!have.add(ref)) continue
                    val prof = repos.profiles.get(ref) ?: continue
                    val tp = prof.toTransfer()
                    linked += tp
                    next += tp
                }
            }
            pending = next
        }
        return if (linked.isEmpty()) groups else groups + TransferGroup(name = LINKED_GROUP, profiles = linked)
    }

    suspend fun import(json: String): TransferStats {
        val file = decode(json) ?: return TransferStats(0, 0, 0)
        return repos.transaction { importFile(file) }
    }

    private suspend fun importFile(file: TransferFile): TransferStats {
        var profiles = 0
        val profileMap = HashMap<Long, Long>()
        val groupMap = HashMap<Long, Long>()
        val needRemap = ArrayList<Pair<Long, OutboundBean>>()
        for (g in file.groups) {
            val gid = repos.groups.create(g.name.ifEmpty { "Imported" }, g.url)
            if (g.id != 0L) groupMap[g.id] = gid
            repos.groups.get(gid)?.let {
                repos.groups.update(
                    it.copy(info = g.info, archive = g.archive, skipAutoUpdate = g.skipAutoUpdate, autoClearUnavailable = g.autoClearUnavailable),
                )
            }
            val ids = repos.profiles.addAll(gid, g.profiles.map { it.name.ifEmpty { displayName(it.bean) } to it.bean })
            g.profiles.forEachIndexed { i, p ->
                val newId = ids.getOrNull(i) ?: return@forEachIndexed
                if (p.id != 0L) profileMap[p.id] = newId
                if (isReferenceBean(p.bean)) needRemap += newId to p.bean
            }
            profiles += g.profiles.size
        }
        for ((newId, bean) in needRemap) repos.profiles.updateBean(newId, bean = remapReferences(bean, profileMap, groupMap))
        return TransferStats(file.groups.size, profiles, 0)
    }

    companion object {
        const val LINKED_GROUP = "Linked"
        private val pretty = Json(from = BeanJson.json) { prettyPrint = true }

        fun referencedProfileIds(b: OutboundBean): List<Long> = when (b) {
            is ChainBean -> b.list.map { it.toLong() }
            is AutoSelectorBean -> (b.pool + b.pinnedId + b.lastBuilt).filter { it > 0 }.map { it.toLong() }
            else -> emptyList()
        }

        fun encode(file: TransferFile): String = pretty.encodeToString(file)

        fun decode(text: String): TransferFile? {
            val f = runCatching { BeanJson.json.decodeFromString(TransferFile.serializer(), text) }.getOrNull() ?: return null
            return if (f.format == TransferFile.FORMAT) f else null
        }

        private fun toTransfer(g: GroupEntity, profiles: List<TransferProfile>) = TransferGroup(
            name = g.name, url = g.url, info = g.info, archive = g.archive, skipAutoUpdate = g.skipAutoUpdate,
            autoClearUnavailable = g.autoClearUnavailable, profiles = profiles, id = g.id,
        )
    }
}
