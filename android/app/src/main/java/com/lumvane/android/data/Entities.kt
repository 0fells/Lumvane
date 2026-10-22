package com.lumvane.android.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val url: String = "",
    val info: String = "",
    val archive: Boolean = false,
    val skipAutoUpdate: Boolean = false,
    val autoClearUnavailable: Boolean = false,
    val subLastUpdate: Long = 0,
    val frontProxyId: Long = -1,
    val landingProxyId: Long = -1,
    val sortOrder: Int = 0,
    val testSortBy: Int = 0,
    val trafficSortBy: Int = 0,
    val typeSortBy: Int = 0,
    val testItemsToShow: Int = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Entity(
    tableName = "profiles",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("groupId"), Index("name")],
)
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val type: String,
    val name: String = "",
    val sortOrder: Int = 0,
    val beanJson: String,
    val latency: Int = 0,
    val latencyAt: Long = 0,
    val dlSpeed: String = "",
    val ulSpeed: String = "",
    val testCountry: String = "",
    val ipOut: String = "",
    val trafficDl: Long = 0,
    val trafficUp: Long = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Entity(tableName = "route_profiles")
data class RouteProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val defaultOutboundId: Int = -1,
    val isRaw: Boolean = false,
    val rawRoute: String = "",
    val preventModifications: Boolean = false,
    val isRemote: Boolean = false,
    val remoteUrl: String = "",
    val autoUpdate: Boolean = false,
    val remoteLastUpdate: Long = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Entity(
    tableName = "route_rules",
    primaryKeys = ["routeProfileId", "ruleOrder"],
    foreignKeys = [
        ForeignKey(
            entity = RouteProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["routeProfileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class RouteRuleEntity(
    val routeProfileId: Long,
    val ruleOrder: Int,
    val name: String = "",
    val type: Int = 0,
    val outboundId: Int = -2,
    val action: String = "route",
    val ruleJson: String,
)

@Entity(tableName = "config_traffic", primaryKeys = ["tier", "bucketStart", "profileId"])
data class ConfigTrafficEntity(
    val tier: Int,
    val bucketStart: Long,
    val profileId: Long,
    val up: Long = 0,
    val down: Long = 0,
)

@Entity(tableName = "app_traffic", primaryKeys = ["tier", "bucketStart", "processName"])
data class AppTrafficEntity(
    val tier: Int,
    val bucketStart: Long,
    val processName: String,
    val up: Long = 0,
    val down: Long = 0,
)

@Entity(tableName = "config_meta")
data class ConfigMetaEntity(
    @PrimaryKey val profileId: Long,
    val name: String = "",
    val groupName: String = "",
    val type: String = "",
    val serverAddress: String = "",
    val firstSeen: Long = 0,
    val lastSeen: Long = 0,
)

@Entity(tableName = "app_meta")
data class AppMetaEntity(
    @PrimaryKey val processName: String,
    val lastPath: String = "",
    val firstSeen: Long = 0,
    val lastSeen: Long = 0,
)

data class ConfigUsage(
    @ColumnInfo(name = "profileId") val profileId: Long,
    val up: Long,
    val down: Long,
)

data class AppUsage(
    @ColumnInfo(name = "processName") val processName: String,
    val up: Long,
    val down: Long,
)

data class TrafficSeriesPoint(
    val bucketStart: Long,
    val up: Long,
    val down: Long,
)
