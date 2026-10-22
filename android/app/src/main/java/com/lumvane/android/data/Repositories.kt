package com.lumvane.android.data

import com.lumvane.android.config.model.BeanJson
import com.lumvane.android.config.model.OutboundBean
import com.lumvane.android.config.model.RouteRule
import com.lumvane.android.config.model.endpoint
import com.lumvane.android.config.model.typeName
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private fun nowSecs(): Long = System.currentTimeMillis() / 1000

class SettingsRepository(private val dao: SettingsDao, private val cipher: SecretCipher = SecretBox.default) {
    private fun plainRows(rows: List<SettingEntity>): Map<String, String> = rows.associate { r ->
        r.key to if (r.key in SettingsCodec.SECRET_KEYS && r.value.isNotEmpty()) SecretBox.decryptOrNull(cipher, r.value, "setting ${r.key}") ?: "" else r.value
    }

    val settings: Flow<AppSettings> = dao.observeAll()
        .map { rows -> SettingsCodec.decode(plainRows(rows)) }
        .distinctUntilChanged()

    suspend fun get(): AppSettings = SettingsCodec.decode(plainRows(dao.getAll()))

    suspend fun update(transform: (AppSettings) -> AppSettings): AppSettings {
        val oldRows = plainRows(dao.getAll())
        val old = SettingsCodec.decode(oldRows)
        val new = transform(old)
        val newRows = SettingsCodec.encodeAll(new)
        val changed = newRows.filter { (k, v) -> oldRows[k] != v }.map { (k, v) ->
            SettingEntity(k, if (k in SettingsCodec.SECRET_KEYS && v.isNotEmpty()) cipher.encrypt(v) else v)
        }
        if (changed.isNotEmpty()) dao.upsertAll(changed)
        return new
    }

    suspend fun setExtra(key: String, value: String) = update { it.copy(extras = it.extras + (key to value)) }
}

class GroupsRepository(private val dao: GroupDao, private val cipher: SecretCipher) {
    private fun GroupEntity.decrypted(): GroupEntity =
        if (url.isEmpty()) this else copy(url = SecretBox.decryptOrNull(cipher, url, "group $id url") ?: "")

    val groups: Flow<List<GroupEntity>> = dao.observeAll().map { l -> l.map { it.decrypted() } }.flowOn(Dispatchers.Default)

    fun observe(id: Long): Flow<GroupEntity?> = dao.observe(id).map { it?.decrypted() }.flowOn(Dispatchers.Default)
    suspend fun get(id: Long): GroupEntity? = dao.get(id)?.decrypted()
    suspend fun getAll(): List<GroupEntity> = dao.getAll().map { it.decrypted() }

    suspend fun create(name: String, url: String = ""): Long {
        val now = nowSecs()
        return dao.insert(GroupEntity(name = name, url = cipher.encrypt(url), sortOrder = dao.nextSortOrder(), createdAt = now, updatedAt = now))
    }

    suspend fun update(group: GroupEntity) {
        val url = if (group.url.isEmpty()) {
            dao.get(group.id)?.url?.takeIf { SecretBox.isEncrypted(it) && SecretBox.decryptOrNull(cipher, it, "group ${group.id} url") == null } ?: ""
        } else cipher.encrypt(group.url)
        dao.update(group.copy(url = url, updatedAt = nowSecs()))
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun reorder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> dao.setSortOrder(id, index) }
    }
}

data class Profile(
    val id: Long = 0,
    val groupId: Long,
    val name: String,
    val bean: OutboundBean,
    val sortOrder: Int = 0,
    val latency: Int = 0,
    val latencyAt: Long = 0,
    val dlSpeed: String = "",
    val ulSpeed: String = "",
    val testCountry: String = "",
    val ipOut: String = "",
    val trafficDl: Long = 0,
    val trafficUp: Long = 0,
) {
    val type: String get() = bean.typeName

    val displayAddress: String
        get() = bean.endpoint?.let { (h, p) -> if (h.isEmpty()) "" else if (p > 0) "$h:$p" else h } ?: ""
}

internal fun ProfileEntity.toProfile(cipher: SecretCipher): Profile? {
    val json = SecretBox.decryptOrNull(cipher, beanJson, "profile $id") ?: return null
    val bean = BeanJson.decodeOrNull(json) ?: return null
    return Profile(
        id = id, groupId = groupId, name = name, bean = bean, sortOrder = sortOrder,
        latency = latency, latencyAt = latencyAt, dlSpeed = dlSpeed, ulSpeed = ulSpeed,
        testCountry = testCountry, ipOut = ipOut, trafficDl = trafficDl, trafficUp = trafficUp,
    )
}

internal fun Profile.toEntity(createdAt: Long, updatedAt: Long, cipher: SecretCipher): ProfileEntity = ProfileEntity(
    id = id, groupId = groupId, type = type, name = name, sortOrder = sortOrder,
    beanJson = cipher.encrypt(BeanJson.encode(bean)), latency = latency, latencyAt = latencyAt,
    dlSpeed = dlSpeed, ulSpeed = ulSpeed, testCountry = testCountry, ipOut = ipOut,
    trafficDl = trafficDl, trafficUp = trafficUp, createdAt = createdAt, updatedAt = updatedAt,
)

fun moveId(ids: List<Long>, id: Long, delta: Int): List<Long> {
    val from = ids.indexOf(id)
    if (from < 0) return ids
    val to = (from + delta).coerceIn(0, ids.lastIndex)
    if (to == from) return ids
    return ids.toMutableList().also { it.add(to, it.removeAt(from)) }
}

class ProfilesRepository(private val dao: ProfileDao, private val cipher: SecretCipher) {
    fun observeAll(): Flow<List<Profile>> = dao.observeAll().map { l -> l.mapNotNull { it.toProfile(cipher) } }.flowOn(Dispatchers.Default)
    fun observeByGroup(groupId: Long): Flow<List<Profile>> =
        dao.observeByGroup(groupId).map { l -> l.mapNotNull { it.toProfile(cipher) } }.flowOn(Dispatchers.Default)

    fun observe(id: Long): Flow<Profile?> = dao.observe(id).map { it?.toProfile(cipher) }.flowOn(Dispatchers.Default)
    fun observeCount(groupId: Long): Flow<Int> = dao.observeCount(groupId)
    suspend fun get(id: Long): Profile? = dao.get(id)?.toProfile(cipher)
    suspend fun getByGroup(groupId: Long): List<Profile> = dao.getByGroup(groupId).mapNotNull { it.toProfile(cipher) }

    suspend fun add(groupId: Long, name: String, bean: OutboundBean): Long {
        val now = nowSecs()
        val p = Profile(groupId = groupId, name = name, bean = bean, sortOrder = dao.nextSortOrder(groupId))
        return dao.insert(p.toEntity(now, now, cipher))
    }

    suspend fun addAll(groupId: Long, items: List<Pair<String, OutboundBean>>): List<Long> {
        if (items.isEmpty()) return emptyList()
        val now = nowSecs()
        val start = dao.nextSortOrder(groupId)
        val entities = items.mapIndexed { i, (name, bean) ->
            Profile(groupId = groupId, name = name, bean = bean, sortOrder = start + i).toEntity(now, now, cipher)
        }
        return dao.insertAll(entities)
    }

    suspend fun update(profile: Profile) {
        val existing = dao.get(profile.id) ?: return
        dao.update(profile.toEntity(existing.createdAt, nowSecs(), cipher))
    }

    suspend fun updateBean(id: Long, name: String? = null, bean: OutboundBean) {
        val existing = dao.get(id) ?: return
        dao.update(
            existing.copy(
                type = bean.typeName, beanJson = cipher.encrypt(BeanJson.encode(bean)),
                name = name ?: existing.name, updatedAt = nowSecs(),
            ),
        )
    }

    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun deleteAll(ids: List<Long>) { if (ids.isNotEmpty()) dao.deleteAll(ids) }

    suspend fun moveToGroup(id: Long, groupId: Long) = dao.move(id, groupId, dao.nextSortOrder(groupId))

    suspend fun reorder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> dao.setSortOrder(id, index) }
    }

    suspend fun moveBy(id: Long, delta: Int): Boolean {
        val groupId = dao.get(id)?.groupId ?: return false
        val ids = dao.getByGroup(groupId).map { it.id }
        val moved = moveId(ids, id, delta)
        if (moved == ids) return false
        reorder(moved)
        return true
    }

    suspend fun setLatency(id: Long, latencyMs: Int, at: Long = nowSecs()) = dao.setLatency(id, latencyMs, at)

    suspend fun setTestResults(id: Long, dlSpeed: String, ulSpeed: String, country: String, ipOut: String) =
        dao.setTestResults(id, dlSpeed, ulSpeed, country, ipOut)

    suspend fun clearTestResults(groupId: Long) = dao.clearTestResults(groupId)
    suspend fun addTraffic(id: Long, downBytes: Long, upBytes: Long) = dao.addTraffic(id, downBytes, upBytes)
    suspend fun resetTraffic(id: Long) = dao.resetTraffic(id)
}

class RoutesRepository(private val dao: RouteDao) {
    private val rulesSerializer = RouteRule.serializer()

    val profiles: Flow<List<RouteProfileEntity>> = dao.observeProfiles()
    fun observeProfile(id: Long): Flow<RouteProfileEntity?> = dao.observeProfile(id)
    suspend fun getProfile(id: Long): RouteProfileEntity? = dao.getProfile(id)
    suspend fun allProfiles(): List<RouteProfileEntity> = dao.getProfiles()

    suspend fun createProfile(name: String, defaultOutboundId: Int = -1): Long {
        val now = nowSecs()
        return dao.insertProfile(
            RouteProfileEntity(name = name, defaultOutboundId = defaultOutboundId, createdAt = now, updatedAt = now),
        )
    }

    suspend fun updateProfile(profile: RouteProfileEntity) = dao.updateProfile(profile.copy(updatedAt = nowSecs()))

    suspend fun deleteProfile(id: Long) = dao.deleteProfile(id)

    fun observeRules(profileId: Long): Flow<List<RouteRule>> =
        dao.observeRules(profileId).map { rows -> rows.mapNotNull { decode(it) } }

    suspend fun getRules(profileId: Long): List<RouteRule> = dao.getRules(profileId).mapNotNull { decode(it) }

    suspend fun saveRules(profileId: Long, rules: List<RouteRule>) {
        val rows = rules.mapIndexed { i, r ->
            RouteRuleEntity(
                routeProfileId = profileId, ruleOrder = i, name = r.name, type = r.type,
                outboundId = r.outboundId, action = r.action,
                ruleJson = BeanJson.json.encodeToString(rulesSerializer, r),
            )
        }
        dao.replaceRules(profileId, rows)
    }

    private fun decode(row: RouteRuleEntity): RouteRule? =
        runCatching { BeanJson.json.decodeFromString(rulesSerializer, row.ruleJson) }.getOrNull()
}

class TrafficStatsRepository(private val dao: TrafficDao) {
    companion object {
        const val TIER_MINUTE = 0
        const val TIER_HOUR = 1
        const val MINUTE_WINDOW_SECS = 48L * 3600
    }

    suspend fun addConfigTraffic(profileId: Long, up: Long, down: Long, atSecs: Long = nowSecs()) =
        dao.addConfigTraffic(TIER_MINUTE, atSecs / 60 * 60, profileId, up, down)

    suspend fun addAppTraffic(processName: String, up: Long, down: Long, atSecs: Long = nowSecs()) =
        dao.addAppTraffic(TIER_MINUTE, atSecs / 60 * 60, processName, up, down)

    suspend fun maintain(retentionDays: Int, nowSecs: Long = nowSecs()) {
        dao.rollupMinuteToHour(nowSecs - MINUTE_WINDOW_SECS)
        val cutoff = nowSecs - retentionDays.coerceAtLeast(1) * 86_400L
        dao.pruneConfigHour(cutoff)
        dao.pruneAppHour(cutoff)
    }

    suspend fun configUsage(fromSecs: Long, toSecs: Long): List<ConfigUsage> = dao.queryConfigUsage(fromSecs, toSecs)
    suspend fun appUsage(fromSecs: Long, toSecs: Long): List<AppUsage> = dao.queryAppUsage(fromSecs, toSecs)

    suspend fun configSeries(fromSecs: Long, toSecs: Long, bucketSecs: Long, utcOffsetSecs: Long = 0) =
        dao.queryConfigSeries(fromSecs, toSecs, bucketSecs, utcOffsetSecs)

    suspend fun appSeries(fromSecs: Long, toSecs: Long, bucketSecs: Long, utcOffsetSecs: Long = 0) =
        dao.queryAppSeries(fromSecs, toSecs, bucketSecs, utcOffsetSecs)

    suspend fun touchConfigMeta(profile: Profile, groupName: String, atSecs: Long = nowSecs()) {
        val first = dao.getConfigMeta(profile.id)?.firstSeen ?: atSecs
        dao.upsertConfigMeta(
            ConfigMetaEntity(profile.id, profile.name, groupName, profile.type, profile.displayAddress, first, atSecs),
        )
    }

    suspend fun touchAppMeta(processName: String, lastPath: String, atSecs: Long = nowSecs()) {
        val first = dao.getAppMeta(processName)?.firstSeen ?: atSecs
        dao.upsertAppMeta(AppMetaEntity(processName, lastPath, first, atSecs))
    }

    suspend fun allConfigMeta() = dao.getAllConfigMeta()
    suspend fun allAppMeta() = dao.getAllAppMeta()
}

class Repositories(private val db: AppDatabase, cipher: SecretCipher = SecretBox.default) {
    suspend fun <T> transaction(block: suspend () -> T): T = db.withTransaction(block)

    val settings = SettingsRepository(db.settingsDao(), cipher)
    val groups = GroupsRepository(db.groupDao(), cipher)
    val profiles = ProfilesRepository(db.profileDao(), cipher)
    val routes = RoutesRepository(db.routeDao())
    val traffic = TrafficStatsRepository(db.trafficDao())

    init {
        if (SecretBox.migrationStarted.compareAndSet(false, true)) CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (SecretMigration(db.profileDao(), db.groupDao(), cipher, db.settingsDao()).run()) {
                    val sql = db.openHelper.writableDatabase
                    sql.query("PRAGMA wal_checkpoint(TRUNCATE)").close()
                    sql.execSQL("VACUUM")
                    sql.query("PRAGMA wal_checkpoint(TRUNCATE)").close()
                }
            } catch (e: Exception) {
                SecretBox.warn("secret migration failed: ${e.javaClass.simpleName}")
            }
        }
    }
}
