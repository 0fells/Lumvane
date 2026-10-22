package com.lumvane.android.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SettingEntity::class,
        GroupEntity::class,
        ProfileEntity::class,
        RouteProfileEntity::class,
        RouteRuleEntity::class,
        ConfigTrafficEntity::class,
        AppTrafficEntity::class,
        ConfigMetaEntity::class,
        AppMetaEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
    abstract fun groupDao(): GroupDao
    abstract fun profileDao(): ProfileDao
    abstract fun routeDao(): RouteDao
    abstract fun trafficDao(): TrafficDao

    companion object {
        const val FILE_NAME = "lumvane.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, FILE_NAME)
                .addCallback(SeedCallback)
                .build()

        val SeedCallback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                seed(db)
            }
        }

        fun seed(db: SupportSQLiteDatabase) {
            val now = System.currentTimeMillis() / 1000
            db.execSQL(
                "INSERT INTO groups (id, name, url, info, archive, skipAutoUpdate, autoClearUnavailable, subLastUpdate, " +
                    "frontProxyId, landingProxyId, sortOrder, testSortBy, trafficSortBy, typeSortBy, testItemsToShow, " +
                    "createdAt, updatedAt) VALUES (1, 'Default', '', '', 0, 0, 0, 0, -1, -1, 0, 0, 0, 0, 0, $now, $now)",
            )
            db.execSQL(
                "INSERT INTO route_profiles (id, name, defaultOutboundId, isRaw, rawRoute, preventModifications, " +
                    "isRemote, remoteUrl, autoUpdate, remoteLastUpdate, createdAt, updatedAt) " +
                    "VALUES (1, 'Default', -1, 0, '', 0, 0, '', 0, 0, $now, $now)",
            )
        }
    }
}
