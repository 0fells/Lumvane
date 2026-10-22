package com.lumvane.android.importer

import android.database.sqlite.SQLiteDatabase
import com.lumvane.android.config.model.AutoSelectorBean
import com.lumvane.android.config.model.ChainBean
import com.lumvane.android.config.model.CustomBean
import com.lumvane.android.config.model.OutboundBean
import com.lumvane.android.data.Repositories
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

data class WinGroup(
    val id: Long,
    val name: String,
    val url: String,
    val info: String,
    val archive: Boolean,
    val skipAutoUpdate: Boolean,
    val autoClearUnavailable: Boolean,
    val subLastUpdate: Long,
    val profileIds: List<Long>,
)

data class WinProfile(val id: Long, val type: String, val name: String, val gid: Long, val outboundJson: String)

class WinDbConversion(val groups: List<Pair<WinGroup, List<ImportedProfile>>>, val skipped: Map<String, Int>) {
    val profileCount get() = groups.sumOf { it.second.size }
}

object WindowsDbImport {

    private fun JsonObject.int(key: String, def: Int) = (this[key] as? JsonPrimitive)?.intOrNull ?: def
    private fun JsonObject.bool(key: String, def: Boolean) = (this[key] as? JsonPrimitive)?.booleanOrNull ?: def
    private fun JsonObject.str(key: String, def: String = "") = (this[key] as? JsonPrimitive)?.contentOrNull ?: def
    private fun JsonObject.long(key: String) = (this[key] as? JsonPrimitive)?.longOrNull ?: 0L
    private fun JsonObject.ints(key: String) =
        (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.intOrNull }.orEmpty()

    internal fun parseChain(o: JsonObject) = ChainBean(o.str("name"), o.ints("list"))

    internal fun parseAutoSelector(o: JsonObject): AutoSelectorBean {
        val d = AutoSelectorBean()
        return AutoSelectorBean(
            name = o.str("name"), gid = o.int("gid", -1), nameFilter = o.str("name_filter"),
            countryFilter = o.str("country_filter"), excludeUnavailable = o.bool("exclude_unavailable", d.excludeUnavailable),
            poolCap = o.int("pool_cap", d.poolCap), buildLimit = o.int("build_limit", d.buildLimit),
            resultValidityMins = o.int("result_validity_mins", d.resultValidityMins),
            testUrl = o.str("test_url"), connectivityUrl = o.str("connectivity_url"),
            intervalSec = o.int("interval_sec", d.intervalSec), benchIntervalSec = o.int("bench_interval_sec", d.benchIntervalSec),
            watchIntervalSec = o.int("watch_interval_sec", d.watchIntervalSec), activeSize = o.int("active_size", d.activeSize),
            sampling = o.int("sampling", d.sampling), toleranceMs = o.int("tolerance_ms", d.toleranceMs),
            maxRttMs = o.int("max_rtt_ms", d.maxRttMs), expected = o.int("expected", d.expected),
            dialRetries = o.int("dial_retries", d.dialRetries), interruptOnSwitch = o.bool("interrupt_on_switch", d.interruptOnSwitch),
            balance = o.bool("balance", d.balance), balanceMode = o.str("balance_mode", d.balanceMode),
            balanceIntervalSec = o.int("balance_interval_sec", d.balanceIntervalSec),
            pool = o.ints("pool"), poolRankedAt = o.long("pool_ranked_at"),
            pinnedId = o.int("pinned_id", -1), lastBuilt = o.ints("last_built"), lastBuiltAt = o.long("last_built_at"),
            history = o["history"] as? JsonArray ?: JsonArray(emptyList()),
        )
    }

    fun convert(groups: List<WinGroup>, profiles: List<WinProfile>): WinDbConversion {
        val skipped = HashMap<String, Int>()
        fun bean(p: WinProfile): OutboundBean? {
            val obj = parseJsonObjectOrNull(p.outboundJson)
            val b: OutboundBean? = when (p.type) {
                "chain" -> obj?.let { parseChain(it) }
                "autoselector" -> obj?.let { parseAutoSelector(it) }
                "custom" -> obj?.let { SingBoxJson.parseTypedObject(it) as? CustomBean }
                "xrayvless" -> obj?.let { SingBoxJson.parseXrayVless(it) }
                else -> obj?.let { SingBoxJson.parseOutbound(it) }
            }
            if (b == null) skipped.merge(p.type, 1, Int::plus)
            return b
        }

        fun imported(p: WinProfile): ImportedProfile? = bean(p)?.let { ImportedProfile(p.name.ifEmpty { displayName(it) }, it, p.id) }

        val byGroup = profiles.groupBy { it.gid }
        val result = ArrayList<Pair<WinGroup, List<ImportedProfile>>>()
        for (g in groups) {
            val rows = byGroup[g.id].orEmpty()
            val byId = rows.associateBy { it.id }
            val ordered = g.profileIds.mapNotNull { byId[it] } + rows.filter { it.id !in g.profileIds }.sortedBy { it.id }
            result += g to ordered.mapNotNull { imported(it) }
        }
        val known = groups.map { it.id }.toSet()
        val orphans = profiles.filter { it.gid !in known }.sortedBy { it.id }.mapNotNull { imported(it) }
        if (orphans.isNotEmpty()) result += WinGroup(-1, "Imported", "", "", false, false, false, 0, emptyList()) to orphans
        return WinDbConversion(result, skipped)
    }

    fun read(dbPath: String): Pair<List<WinGroup>, List<WinProfile>> {
        val db = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val groups = ArrayList<WinGroup>()
            db.rawQuery(
                "SELECT id, archive, skip_auto_update, name, url, info, sub_last_update, profiles_json, auto_clear_unavailable FROM groups",
                null,
            ).use { c ->
                while (c.moveToNext()) {
                    val ids = runCatching {
                        (Json.parseToJsonElement(c.getString(7) ?: "[]") as JsonArray)
                            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.toLongOrNull() }
                    }.getOrDefault(emptyList())
                    groups += WinGroup(
                        c.getLong(0), c.getString(3).orEmpty(), c.getString(4).orEmpty(), c.getString(5).orEmpty(),
                        c.getInt(1) != 0, c.getInt(2) != 0, c.getInt(8) != 0, c.getLong(6), ids,
                    )
                }
            }
            val profiles = ArrayList<WinProfile>()
            db.rawQuery("SELECT id, type, name, gid, outbound_json FROM profiles ORDER BY id", null).use { c ->
                while (c.moveToNext()) {
                    profiles += WinProfile(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getLong(3), c.getString(4).orEmpty())
                }
            }
            return groups to profiles
        } finally {
            db.close()
        }
    }

    suspend fun import(repos: Repositories, dbPath: String): TransferStats {
        val (g, p) = read(dbPath)
        val conv = convert(g, p)
        return repos.transaction { store(repos, conv) }
    }

    private suspend fun store(repos: Repositories, conv: WinDbConversion): TransferStats {
        var count = 0
        val profileMap = HashMap<Long, Long>()
        val groupMap = HashMap<Long, Long>()
        val meta = ArrayList<Pair<Long, OutboundBean>>()
        for ((wg, items) in conv.groups) {
            val gid = repos.groups.create(wg.name.ifEmpty { "Imported" }, wg.url)
            repos.groups.get(gid)?.let {
                repos.groups.update(
                    it.copy(
                        info = wg.info, archive = wg.archive, skipAutoUpdate = wg.skipAutoUpdate,
                        autoClearUnavailable = wg.autoClearUnavailable, subLastUpdate = wg.subLastUpdate,
                    ),
                )
            }
            if (wg.id >= 0) groupMap[wg.id] = gid
            val ids = repos.profiles.addAll(gid, items.map { it.name to it.bean })
            items.forEachIndexed { i, ip ->
                val newId = ids.getOrNull(i) ?: return@forEachIndexed
                if (ip.sourceId != 0L) profileMap[ip.sourceId] = newId
                if (isReferenceBean(ip.bean)) meta += newId to ip.bean
            }
            count += items.size
        }
        for ((newId, bean) in meta) repos.profiles.updateBean(newId, bean = remapReferences(bean, profileMap, groupMap))
        return TransferStats(conv.groups.size, count, conv.skipped.values.sum())
    }
}
