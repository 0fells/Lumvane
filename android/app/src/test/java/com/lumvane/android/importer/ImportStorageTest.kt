package com.lumvane.android.importer

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.lumvane.android.config.model.ChainBean
import com.lumvane.android.config.model.TrojanBean
import com.lumvane.android.config.model.VlessBean
import com.lumvane.android.data.AppDatabase
import com.lumvane.android.data.PlainCipher
import com.lumvane.android.data.Repositories
import com.lumvane.android.data.SecretBox
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportStorageTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var repos: Repositories

    @Before
    fun setUp() {
        SecretBox.migrationStarted.set(true)
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .addCallback(AppDatabase.SeedCallback)
            .allowMainThreadQueries()
            .build()
        repos = Repositories(db, PlainCipher)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun exportImportRoundTripRemapsChains() = runBlocking {
        val gid = repos.groups.create("Source", "https://example.com/sub")
        val (a, b) = repos.profiles.addAll(
            gid,
            listOf(
                "a" to VlessBean(server = "192.0.2.1", serverPort = 443, uuid = "00000000-0000-4000-8000-000000000001"),
                "b" to TrojanBean(server = "192.0.2.2", serverPort = 443, password = "pw"),
            ),
        )
        repos.profiles.add(gid, "chain", ChainBean("chain", listOf(a.toInt(), b.toInt())))
        val transfer = ProfileTransfer(repos)
        val json = transfer.exportGroups(listOf(gid))

        val stats = transfer.import(json)
        assertEquals(3, stats.profiles)
        val imported = repos.groups.getAll().filter { it.name == "Source" && it.id != gid }
        assertEquals(1, imported.size, "import must create a new group, not merge")
        assertEquals("https://example.com/sub", imported.single().url)
        val profiles = repos.profiles.getByGroup(imported.single().id)
        assertEquals(listOf("a", "b", "chain"), profiles.map { it.name })
        val chain = assertIs<ChainBean>(profiles.last().bean)
        assertEquals(profiles.take(2).map { it.id.toInt() }, chain.list)
        assertTrue(chain.list.none { it == a.toInt() || it == b.toInt() }, "chain still points at the source profiles")
    }

    @Test
    fun importRejectsForeignFiles() = runBlocking {
        val before = repos.groups.getAll().size
        assertEquals(0, ProfileTransfer(repos).import("""{"format":"other","groups":[{"name":"x"}]}""").groups)
        assertEquals(0, ProfileTransfer(repos).import("not json").groups)
        assertEquals(before, repos.groups.getAll().size)
    }

    @Test
    fun windowsDatabaseImport() = runBlocking {
        val file = tmp.newFile("lumvane.db")
        file.delete()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { w ->
            w.execSQL(
                "CREATE TABLE groups (id INTEGER PRIMARY KEY, archive INTEGER, skip_auto_update INTEGER, name TEXT, url TEXT, " +
                    "info TEXT, sub_last_update INTEGER, profiles_json TEXT, auto_clear_unavailable INTEGER)",
            )
            w.execSQL("CREATE TABLE profiles (id INTEGER PRIMARY KEY, type TEXT, name TEXT, gid INTEGER, outbound_json TEXT)")
            w.execSQL("INSERT INTO groups VALUES (7, 0, 1, 'Desk', '', '', 0, '[12,11,13]', 0)")
            w.execSQL(
                "INSERT INTO profiles VALUES (11, 'vless', 'v', 7, " +
                    "'{\"type\":\"vless\",\"tag\":\"v\",\"server\":\"192.0.2.11\",\"server_port\":443,\"uuid\":\"00000000-0000-4000-8000-000000000002\"}')",
            )
            w.execSQL(
                "INSERT INTO profiles VALUES (12, 'trojan', 't', 7, " +
                    "'{\"type\":\"trojan\",\"tag\":\"t\",\"server\":\"192.0.2.12\",\"server_port\":443,\"password\":\"pw\"}')",
            )
            w.execSQL("INSERT INTO profiles VALUES (13, 'chain', 'c', 7, '{\"name\":\"c\",\"type\":\"chain\",\"list\":[12,11]}')")
            w.execSQL("INSERT INTO profiles VALUES (14, 'nonsense', 'n', 7, '{}')")
            w.execSQL("INSERT INTO profiles VALUES (15, 'trojan', 'orphan', 99, '{\"type\":\"trojan\",\"tag\":\"o\",\"server\":\"192.0.2.15\",\"server_port\":443,\"password\":\"pw\"}')")
        }

        val stats = WindowsDbImport.import(repos, file.path)
        assertEquals(4, stats.profiles)
        assertEquals(1, stats.skipped)

        val desk = repos.groups.getAll().single { it.name == "Desk" }
        assertTrue(desk.skipAutoUpdate)
        val profiles = repos.profiles.getByGroup(desk.id)
        assertEquals(listOf("t", "v", "c"), profiles.map { it.name }, "desktop display order is kept")
        val chain = assertIs<ChainBean>(profiles.last().bean)
        assertEquals(listOf(profiles[0].id.toInt(), profiles[1].id.toInt()), chain.list)
        assertNotEquals(listOf(12, 11), chain.list)

        val orphans = repos.groups.getAll().single { it.name == "Imported" }
        assertEquals(listOf("orphan"), repos.profiles.getByGroup(orphans.id).map { it.name })
    }
}
