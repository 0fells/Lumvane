package com.lumvane.android.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings")
    fun observeAll(): Flow<List<SettingEntity>>

    @Query("SELECT * FROM settings")
    suspend fun getAll(): List<SettingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<SettingEntity>)

    @Query("DELETE FROM settings WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("UPDATE settings SET value = :new WHERE `key` = :key AND value = :old")
    suspend fun replaceValue(key: String, old: String, new: String)
}

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups ORDER BY sortOrder, id")
    suspend fun getAll(): List<GroupEntity>

    @Query("SELECT * FROM groups WHERE id = :id")
    fun observe(id: Long): Flow<GroupEntity?>

    @Query("SELECT * FROM groups WHERE id = :id")
    suspend fun get(id: Long): GroupEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM groups")
    suspend fun nextSortOrder(): Int

    @Insert
    suspend fun insert(group: GroupEntity): Long

    @Update
    suspend fun update(group: GroupEntity)

    @Query("DELETE FROM groups WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE groups SET sortOrder = :order WHERE id = :id")
    suspend fun setSortOrder(id: Long, order: Int)

    @Query("UPDATE groups SET url = :new WHERE id = :id AND url = :old")
    suspend fun replaceUrl(id: Long, old: String, new: String)
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY groupId, sortOrder, id")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE groupId = :groupId ORDER BY sortOrder, id")
    fun observeByGroup(groupId: Long): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE groupId = :groupId ORDER BY sortOrder, id")
    suspend fun getByGroup(groupId: Long): List<ProfileEntity>

    @Query("SELECT * FROM profiles WHERE id = :id")
    fun observe(id: Long): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun get(id: Long): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<ProfileEntity>

    @Query("SELECT COUNT(*) FROM profiles WHERE groupId = :groupId")
    fun observeCount(groupId: Long): Flow<Int>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM profiles WHERE groupId = :groupId")
    suspend fun nextSortOrder(groupId: Long): Int

    @Insert
    suspend fun insert(profile: ProfileEntity): Long

    @Insert
    suspend fun insertAll(profiles: List<ProfileEntity>): List<Long>

    @Update
    suspend fun update(profile: ProfileEntity)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM profiles WHERE id IN (:ids)")
    suspend fun deleteAll(ids: List<Long>)

    @Query("DELETE FROM profiles WHERE groupId = :groupId")
    suspend fun deleteByGroup(groupId: Long)

    @Query("SELECT * FROM profiles")
    suspend fun getAllRaw(): List<ProfileEntity>

    @Query("UPDATE profiles SET beanJson = :new WHERE id = :id AND beanJson = :old")
    suspend fun replaceBeanJson(id: Long, old: String, new: String)

    @Query("UPDATE profiles SET sortOrder = :order WHERE id = :id")
    suspend fun setSortOrder(id: Long, order: Int)

    @Query("UPDATE profiles SET groupId = :groupId, sortOrder = :order WHERE id = :id")
    suspend fun move(id: Long, groupId: Long, order: Int)

    @Query("UPDATE profiles SET latency = :latency, latencyAt = :at WHERE id = :id")
    suspend fun setLatency(id: Long, latency: Int, at: Long)

    @Query("UPDATE profiles SET dlSpeed = :dl, ulSpeed = :ul, testCountry = :country, ipOut = :ip WHERE id = :id")
    suspend fun setTestResults(id: Long, dl: String, ul: String, country: String, ip: String)

    @Query("UPDATE profiles SET latency = 0, latencyAt = 0, dlSpeed = '', ulSpeed = '', testCountry = '', ipOut = '' WHERE groupId = :groupId")
    suspend fun clearTestResults(groupId: Long)

    @Query("UPDATE profiles SET trafficDl = trafficDl + :dl, trafficUp = trafficUp + :up WHERE id = :id")
    suspend fun addTraffic(id: Long, dl: Long, up: Long)

    @Query("UPDATE profiles SET trafficDl = 0, trafficUp = 0 WHERE id = :id")
    suspend fun resetTraffic(id: Long)
}

@Dao
interface RouteDao {
    @Query("SELECT * FROM route_profiles ORDER BY id")
    fun observeProfiles(): Flow<List<RouteProfileEntity>>

    @Query("SELECT * FROM route_profiles ORDER BY id")
    suspend fun getProfiles(): List<RouteProfileEntity>

    @Query("SELECT * FROM route_profiles WHERE id = :id")
    suspend fun getProfile(id: Long): RouteProfileEntity?

    @Query("SELECT * FROM route_profiles WHERE id = :id")
    fun observeProfile(id: Long): Flow<RouteProfileEntity?>

    @Insert
    suspend fun insertProfile(profile: RouteProfileEntity): Long

    @Update
    suspend fun updateProfile(profile: RouteProfileEntity)

    @Query("DELETE FROM route_profiles WHERE id = :id")
    suspend fun deleteProfile(id: Long)

    @Query("SELECT * FROM route_rules WHERE routeProfileId = :profileId ORDER BY ruleOrder")
    fun observeRules(profileId: Long): Flow<List<RouteRuleEntity>>

    @Query("SELECT * FROM route_rules WHERE routeProfileId = :profileId ORDER BY ruleOrder")
    suspend fun getRules(profileId: Long): List<RouteRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<RouteRuleEntity>)

    @Query("DELETE FROM route_rules WHERE routeProfileId = :profileId")
    suspend fun deleteRules(profileId: Long)

    @Transaction
    suspend fun replaceRules(profileId: Long, rules: List<RouteRuleEntity>) {
        deleteRules(profileId)
        insertRules(rules)
    }
}

@Dao
interface TrafficDao {
    @Query(
        "INSERT INTO config_traffic (tier, bucketStart, profileId, up, down) VALUES (:tier, :bucket, :profileId, :up, :down) " +
            "ON CONFLICT(tier, bucketStart, profileId) DO UPDATE SET up = up + excluded.up, down = down + excluded.down",
    )
    suspend fun addConfigTraffic(tier: Int, bucket: Long, profileId: Long, up: Long, down: Long)

    @Query(
        "INSERT INTO app_traffic (tier, bucketStart, processName, up, down) VALUES (:tier, :bucket, :processName, :up, :down) " +
            "ON CONFLICT(tier, bucketStart, processName) DO UPDATE SET up = up + excluded.up, down = down + excluded.down",
    )
    suspend fun addAppTraffic(tier: Int, bucket: Long, processName: String, up: Long, down: Long)

    @Query(
        "INSERT INTO config_traffic (tier, bucketStart, profileId, up, down) " +
            "SELECT 1, (bucketStart / 3600) * 3600, profileId, SUM(up), SUM(down) FROM config_traffic " +
            "WHERE tier = 0 AND bucketStart < :olderThan GROUP BY 2, 3 " +
            "ON CONFLICT(tier, bucketStart, profileId) DO UPDATE SET up = up + excluded.up, down = down + excluded.down",
    )
    suspend fun rollupConfig(olderThan: Long)

    @Query("DELETE FROM config_traffic WHERE tier = 0 AND bucketStart < :olderThan")
    suspend fun dropMinuteConfig(olderThan: Long)

    @Query(
        "INSERT INTO app_traffic (tier, bucketStart, processName, up, down) " +
            "SELECT 1, (bucketStart / 3600) * 3600, processName, SUM(up), SUM(down) FROM app_traffic " +
            "WHERE tier = 0 AND bucketStart < :olderThan GROUP BY 2, 3 " +
            "ON CONFLICT(tier, bucketStart, processName) DO UPDATE SET up = up + excluded.up, down = down + excluded.down",
    )
    suspend fun rollupApp(olderThan: Long)

    @Query("DELETE FROM app_traffic WHERE tier = 0 AND bucketStart < :olderThan")
    suspend fun dropMinuteApp(olderThan: Long)

    @Transaction
    suspend fun rollupMinuteToHour(olderThan: Long) {
        rollupConfig(olderThan)
        dropMinuteConfig(olderThan)
        rollupApp(olderThan)
        dropMinuteApp(olderThan)
    }

    @Query("DELETE FROM config_traffic WHERE tier = 1 AND bucketStart < :olderThan")
    suspend fun pruneConfigHour(olderThan: Long)

    @Query("DELETE FROM app_traffic WHERE tier = 1 AND bucketStart < :olderThan")
    suspend fun pruneAppHour(olderThan: Long)

    @Query("DELETE FROM config_traffic WHERE profileId = :profileId")
    suspend fun deleteConfigTraffic(profileId: Long)

    @Query(
        "SELECT profileId, SUM(up) AS up, SUM(down) AS down FROM config_traffic " +
            "WHERE bucketStart >= :from AND bucketStart < :to GROUP BY profileId",
    )
    suspend fun queryConfigUsage(from: Long, to: Long): List<ConfigUsage>

    @Query(
        "SELECT processName, SUM(up) AS up, SUM(down) AS down FROM app_traffic " +
            "WHERE bucketStart >= :from AND bucketStart < :to GROUP BY processName",
    )
    suspend fun queryAppUsage(from: Long, to: Long): List<AppUsage>

    @Query(
        "SELECT ((bucketStart + :utcOffset) / :bucketSecs) * :bucketSecs - :utcOffset AS bucketStart, " +
            "SUM(up) AS up, SUM(down) AS down FROM config_traffic " +
            "WHERE bucketStart >= :from AND bucketStart < :to GROUP BY 1 ORDER BY 1",
    )
    suspend fun queryConfigSeries(from: Long, to: Long, bucketSecs: Long, utcOffset: Long): List<TrafficSeriesPoint>

    @Query(
        "SELECT ((bucketStart + :utcOffset) / :bucketSecs) * :bucketSecs - :utcOffset AS bucketStart, " +
            "SUM(up) AS up, SUM(down) AS down FROM app_traffic " +
            "WHERE bucketStart >= :from AND bucketStart < :to GROUP BY 1 ORDER BY 1",
    )
    suspend fun queryAppSeries(from: Long, to: Long, bucketSecs: Long, utcOffset: Long): List<TrafficSeriesPoint>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConfigMeta(meta: ConfigMetaEntity)

    @Query("SELECT * FROM config_meta WHERE profileId = :profileId")
    suspend fun getConfigMeta(profileId: Long): ConfigMetaEntity?

    @Query("SELECT * FROM config_meta")
    suspend fun getAllConfigMeta(): List<ConfigMetaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppMeta(meta: AppMetaEntity)

    @Query("SELECT * FROM app_meta WHERE processName = :processName")
    suspend fun getAppMeta(processName: String): AppMetaEntity?

    @Query("SELECT * FROM app_meta")
    suspend fun getAllAppMeta(): List<AppMetaEntity>
}
