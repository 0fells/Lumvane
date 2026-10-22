package com.lumvane.android.sub

import com.lumvane.android.config.model.AutoSelectorBean
import com.lumvane.android.config.model.ChainBean
import com.lumvane.android.config.model.endpoint
import com.lumvane.android.config.model.typeName
import com.lumvane.android.data.AppSettings
import com.lumvane.android.data.Profile
import com.lumvane.android.data.Repositories
import com.lumvane.android.importer.ContentParser
import com.lumvane.android.importer.ImportInput
import com.lumvane.android.importer.ImportedProfile
import com.lumvane.android.importer.ParseOptions
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.Proxy

sealed interface Slot {
    data class Existing(val profileId: Long) : Slot
    data class New(val index: Int) : Slot
}

class DiffPlan(
    val update: List<Pair<Long, ImportedProfile>>,
    val add: List<ImportedProfile>,
    val delete: List<Long>,
    val order: List<Slot>,
    val kept: List<Long>,
)

object SubscriptionDiff {
    fun isSticky(p: Profile) = p.bean is ChainBean || p.bean is AutoSelectorBean

    private fun exactKey(name: String, json: String) = "$name\u0000$json"

    private fun identity1(p: Profile) = p.bean.typeName + "|" + p.name
    private fun identity1(i: ImportedProfile) = i.bean.typeName + "|" + i.name
    private fun identity2(type: String, ep: Pair<String, Int>?) = type + "|" + (ep?.first ?: "") + "|" + (ep?.second ?: 0)

    fun compute(old: List<Profile>, new: List<ImportedProfile>, protectedIds: Set<Long> = emptySet(), clear: Boolean = false): DiffPlan {
        val sticky = old.withIndex().filter { isSticky(it.value) }
        val owned = old.filter { !isSticky(it) }

        if (clear) {
            val kept = owned.filter { it.id in protectedIds }
            val delete = owned.filter { it.id !in protectedIds }.map { it.id }
            val order = ArrayList<Slot>()
            new.indices.forEach { order += Slot.New(it) }
            insertPreserved(order, old, sticky.map { it.value.id } + kept.map { it.id })
            return DiffPlan(emptyList(), new, delete, order, kept.map { it.id })
        }

        val matched = arrayOfNulls<Profile>(new.size)
        val changed = BooleanArray(new.size)
        val free = LinkedHashSet(owned.map { it.id })
        val byId = owned.associateBy { it.id }

        fun matchBy(key: (ImportedProfile) -> String, oldKey: (Profile) -> String, markChanged: Boolean) {
            val pool = HashMap<String, ArrayDeque<Long>>()
            for (id in free) pool.getOrPut(oldKey(byId.getValue(id))) { ArrayDeque() }.addLast(id)
            for (i in new.indices) {
                if (matched[i] != null) continue
                val q = pool[key(new[i])] ?: continue
                val id = q.removeFirstOrNull() ?: continue
                matched[i] = byId.getValue(id)
                changed[i] = markChanged
                free.remove(id)
            }
        }
        matchBy({ exactKey(it.name, com.lumvane.android.config.model.BeanJson.encode(it.bean)) },
            { exactKey(it.name, com.lumvane.android.config.model.BeanJson.encode(it.bean)) }, false)
        matchBy({ identity1(it) }, { identity1(it) }, true)
        matchBy({ identity2(it.bean.typeName, it.bean.endpoint) }, { identity2(it.bean.typeName, it.bean.endpoint) }, true)

        val update = ArrayList<Pair<Long, ImportedProfile>>()
        val add = ArrayList<ImportedProfile>()
        val order = ArrayList<Slot>()
        for (i in new.indices) {
            val m = matched[i]
            if (m == null) {
                order += Slot.New(add.size)
                add += new[i]
            } else {
                order += Slot.Existing(m.id)
                if (changed[i]) update += m.id to new[i]
            }
        }
        val kept = free.filter { it in protectedIds }
        val delete = free.filter { it !in protectedIds }
        insertPreserved(order, old, sticky.map { it.value.id } + kept)
        return DiffPlan(update, add, delete, order, kept)
    }

    private fun insertPreserved(order: MutableList<Slot>, old: List<Profile>, ids: List<Long>) {
        val positions = ids.mapNotNull { id -> old.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { it to id } }.sortedBy { it.first }
        for ((pos, id) in positions) order.add(minOf(pos, order.size), Slot.Existing(id))
    }
}

internal inline fun <T> catching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

data class UpdateResult(
    val added: List<String>,
    val updated: List<String>,
    val removed: List<String>,
    val kept: List<String>,
    val info: String,
    val updateIntervalHours: Int?,
) {
    val unchanged get() = added.isEmpty() && updated.isEmpty() && removed.isEmpty()

    fun changeText(): String {
        if (unchanged && kept.isEmpty()) return "Nothing"
        val sb = StringBuilder()
        fun section(prefix: String, l: List<String>) {
            if (l.size >= 1000) sb.append("$prefix ${l.size} profiles\n") else l.forEach { sb.append("$prefix $it\n") }
        }
        section("[+]", added); section("[~]", updated); section("[-]", removed); section("[=]", kept)
        return sb.toString().trimEnd()
    }
}

class SubscriptionUpdater(
    private val repos: Repositories,
    private val client: SubscriptionClient = SubscriptionClient(),
    private val parseOptions: (AppSettings) -> ParseOptions = { ParseOptions(it.xrayVlessPreference) },
    private val now: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private val lock = Mutex()

    suspend fun updateGroup(
        groupId: Long,
        settings: AppSettings,
        proxy: Proxy? = null,
        protectedProfileIds: Set<Long> = emptySet(),
    ): Result<UpdateResult> = lock.withLock {
        catching {
            val group = repos.groups.get(groupId) ?: throw SubscriptionException("group not found")
            if (group.archive) throw SubscriptionException("group is archived")
            if (group.url.isBlank()) throw SubscriptionException("group has no subscription URL")
            val fetched = client.fetch(group.url.trim(), settings, proxy)
            val parsed = ContentParser.parse(fetched.body, parseOptions(settings))
            if (parsed.isEmpty) throw SubscriptionException("no profiles found in the subscription")

            val old = repos.profiles.getByGroup(groupId)
            val plan = SubscriptionDiff.compute(old, parsed.profiles, protectedProfileIds, settings.subClear)
            val byId = old.associateBy { it.id }

            plan.update.forEach { (id, p) -> repos.profiles.updateBean(id, p.name, p.bean) }
            repos.profiles.deleteAll(plan.delete)
            val newIds = repos.profiles.addAll(groupId, plan.add.map { it.name to it.bean })
            val finalIds = plan.order.map { slot ->
                when (slot) {
                    is Slot.Existing -> slot.profileId
                    is Slot.New -> newIds[slot.index]
                }
            }
            repos.profiles.reorder(finalIds)

            repos.groups.get(groupId)?.let {
                repos.groups.update(it.copy(info = fetched.userInfo, subLastUpdate = now()))
            }
            UpdateResult(
                added = plan.add.map { it.name },
                updated = plan.update.map { byId[it.first]?.name ?: it.second.name },
                removed = plan.delete.map { byId[it]?.name.orEmpty() },
                kept = plan.kept.map { byId[it]?.name.orEmpty() },
                info = fetched.userInfo,
                updateIntervalHours = fetched.updateIntervalHours,
            )
        }
    }

    suspend fun addSubscription(url: String, settings: AppSettings, name: String? = null, proxy: Proxy? = null): Result<Pair<Long, UpdateResult>> {
        val gid = repos.groups.create(name ?: ImportInput.groupNameForUrl(url), url.trim())
        return updateGroup(gid, settings, proxy).map { gid to it }
    }

    suspend fun updateAll(settings: AppSettings, onlyAllowed: Boolean, proxy: Proxy? = null, protectedProfileIds: Set<Long> = emptySet()): Map<Long, Result<UpdateResult>> {
        val out = LinkedHashMap<Long, Result<UpdateResult>>()
        for (g in repos.groups.getAll()) {
            if (g.url.isBlank() || g.archive || (onlyAllowed && g.skipAutoUpdate)) continue
            out[g.id] = updateGroup(g.id, settings, proxy, protectedProfileIds)
        }
        return out
    }
}
