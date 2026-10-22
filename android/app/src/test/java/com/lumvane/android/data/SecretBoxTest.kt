package com.lumvane.android.data

import com.lumvane.android.config.model.SocksBean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal object PlainCipher : SecretCipher {
    override fun encrypt(plain: String) = plain
    override fun decrypt(stored: String) = stored
}

internal class FakeCipher : SecretCipher {
    override fun encrypt(plain: String) = if (plain.isEmpty()) plain else SecretBox.PREFIX + plain.reversed() + "#ok"
    override fun decrypt(stored: String): String {
        if (!SecretBox.isEncrypted(stored)) return stored
        val body = stored.removePrefix(SecretBox.PREFIX)
        require(body.endsWith("#ok")) { "tampered" }
        return body.removeSuffix("#ok").reversed()
    }
}

internal class FakeGroupDao : GroupDao {
    val rows = MutableStateFlow<List<GroupEntity>>(emptyList())
    private var nextId = 1L
    override fun observeAll() = rows
    override suspend fun getAll() = rows.value
    override fun observe(id: Long) = rows.map { l -> l.firstOrNull { it.id == id } }
    override suspend fun get(id: Long) = rows.value.firstOrNull { it.id == id }
    override suspend fun nextSortOrder() = rows.value.size
    override suspend fun insert(group: GroupEntity): Long {
        val id = nextId++
        rows.value = rows.value + group.copy(id = id)
        return id
    }
    override suspend fun update(group: GroupEntity) { rows.value = rows.value.map { if (it.id == group.id) group else it } }
    override suspend fun delete(id: Long) { rows.value = rows.value.filter { it.id != id } }
    override suspend fun setSortOrder(id: Long, order: Int) {}
    override suspend fun replaceUrl(id: Long, old: String, new: String) {
        rows.value = rows.value.map { if (it.id == id && it.url == old) it.copy(url = new) else it }
    }
}

class SecretBoxTest {
    private val cipher = FakeCipher()

    @Test
    fun roundTripStoresCiphertextAndReturnsPlaintext() = runTest {
        val dao = FakeProfileDao()
        val repo = ProfilesRepository(dao, cipher)
        val id = repo.add(1, "n", SocksBean(server = "192.0.2.1", serverPort = 1080, password = "dummy-pass"))
        val stored = dao.rows.value.single().beanJson
        assertTrue(stored.startsWith(SecretBox.PREFIX))
        assertTrue("dummy-pass" !in stored)
        assertEquals("192.0.2.1:1080", repo.get(id)?.displayAddress)

        val groups = FakeGroupDao()
        val g = GroupsRepository(groups, cipher)
        val gid = g.create("sub", "https://example.com/sub?token=dummy")
        assertTrue(groups.rows.value.single().url.startsWith(SecretBox.PREFIX))
        assertEquals("https://example.com/sub?token=dummy", g.get(gid)?.url)
        assertEquals("https://example.com/sub?token=dummy", g.groups.first().single().url)
        g.update(g.get(gid)!!.copy(name = "renamed"))
        assertEquals("https://example.com/sub?token=dummy", g.getAll().single().url)
        assertEquals("", GroupsRepository(groups, cipher).also { it.create("manual") }.getAll()[1].url)
    }

    @Test
    fun legacyPlaintextPassesThrough() = runTest {
        val dao = FakeProfileDao()
        val plain = com.lumvane.android.config.model.BeanJson.encode(SocksBean(server = "192.0.2.2", serverPort = 1))
        dao.insert(ProfileEntity(groupId = 1, type = "socks", beanJson = plain))
        assertEquals("192.0.2.2:1", ProfilesRepository(dao, cipher).getByGroup(1).single().displayAddress)
        assertEquals("plain", cipher.decrypt("plain"))
        assertEquals("", cipher.encrypt(""))
    }

    @Test
    fun migrationEncryptsOnlyPlaintextRowsAndIsIdempotent() = runTest {
        val profiles = FakeProfileDao()
        val groups = FakeGroupDao()
        val plain = com.lumvane.android.config.model.BeanJson.encode(SocksBean(server = "192.0.2.3", serverPort = 2))
        val enc = cipher.encrypt(plain)
        profiles.insert(ProfileEntity(groupId = 1, type = "socks", beanJson = plain))
        profiles.insert(ProfileEntity(groupId = 1, type = "socks", beanJson = enc))
        groups.insert(GroupEntity(url = "https://example.com/a"))
        groups.insert(GroupEntity(url = ""))
        groups.insert(GroupEntity(url = cipher.encrypt("https://example.com/b")))

        SecretMigration(profiles, groups, cipher).run()
        assertEquals(listOf(enc, enc), profiles.rows.value.map { it.beanJson })
        assertEquals(
            listOf(cipher.encrypt("https://example.com/a"), "", cipher.encrypt("https://example.com/b")),
            groups.rows.value.map { it.url },
        )
        val snapshot = profiles.rows.value to groups.rows.value
        SecretMigration(profiles, groups, cipher).run()
        assertEquals(snapshot, profiles.rows.value to groups.rows.value)
        assertNotEquals(plain, profiles.rows.value[0].beanJson)
    }

    @Test
    fun tamperedCiphertextGivesNullProfileNotCrash() = runTest {
        val dao = FakeProfileDao()
        dao.insert(ProfileEntity(groupId = 1, type = "socks", beanJson = SecretBox.PREFIX + "garbage"))
        val repo = ProfilesRepository(dao, cipher)
        assertNull(repo.get(1))
        assertTrue(repo.getByGroup(1).isEmpty())
        assertFailsWith<IllegalArgumentException> { cipher.decrypt(SecretBox.PREFIX + "garbage") }

        val groups = FakeGroupDao()
        groups.insert(GroupEntity(url = SecretBox.PREFIX + "garbage"))
        assertEquals("", GroupsRepository(groups, cipher).getAll().single().url)
    }
}
