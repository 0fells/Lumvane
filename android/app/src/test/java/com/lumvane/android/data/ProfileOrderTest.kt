package com.lumvane.android.data

import com.lumvane.android.config.model.SocksBean
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class FakeProfileDao : ProfileDao {
    val rows = MutableStateFlow<List<ProfileEntity>>(emptyList())
    private var nextId = 1L

    private fun sorted(groupId: Long) = rows.value.filter { it.groupId == groupId }.sortedWith(compareBy({ it.sortOrder }, { it.id }))

    override fun observeAll(): Flow<List<ProfileEntity>> = rows
    override fun observeByGroup(groupId: Long): Flow<List<ProfileEntity>> = rows.map { _ -> sorted(groupId) }
    override suspend fun getByGroup(groupId: Long) = sorted(groupId)
    override fun observe(id: Long): Flow<ProfileEntity?> = rows.map { l -> l.firstOrNull { it.id == id } }
    override suspend fun get(id: Long) = rows.value.firstOrNull { it.id == id }
    override suspend fun getByIds(ids: List<Long>) = rows.value.filter { it.id in ids }
    override fun observeCount(groupId: Long): Flow<Int> = rows.map { l -> l.count { it.groupId == groupId } }
    override suspend fun nextSortOrder(groupId: Long) = (rows.value.filter { it.groupId == groupId }.maxOfOrNull { it.sortOrder } ?: -1) + 1
    override suspend fun insert(profile: ProfileEntity): Long {
        val id = nextId++
        rows.value = rows.value + profile.copy(id = id)
        return id
    }
    override suspend fun insertAll(profiles: List<ProfileEntity>) = profiles.map { insert(it) }
    override suspend fun update(profile: ProfileEntity) { rows.value = rows.value.map { if (it.id == profile.id) profile else it } }
    override suspend fun delete(id: Long) { rows.value = rows.value.filter { it.id != id } }
    override suspend fun deleteAll(ids: List<Long>) { rows.value = rows.value.filter { it.id !in ids } }
    override suspend fun deleteByGroup(groupId: Long) { rows.value = rows.value.filter { it.groupId != groupId } }
    override suspend fun setSortOrder(id: Long, order: Int) { rows.value = rows.value.map { if (it.id == id) it.copy(sortOrder = order) else it } }
    override suspend fun move(id: Long, groupId: Long, order: Int) {
        rows.value = rows.value.map { if (it.id == id) it.copy(groupId = groupId, sortOrder = order) else it }
    }
    override suspend fun setLatency(id: Long, latency: Int, at: Long) {}
    override suspend fun setTestResults(id: Long, dl: String, ul: String, country: String, ip: String) {}
    override suspend fun clearTestResults(groupId: Long) {}
    override suspend fun addTraffic(id: Long, dl: Long, up: Long) {}
    override suspend fun resetTraffic(id: Long) {}
    override suspend fun getAllRaw() = rows.value
    override suspend fun replaceBeanJson(id: Long, old: String, new: String) {
        rows.value = rows.value.map { if (it.id == id && it.beanJson == old) it.copy(beanJson = new) else it }
    }
}

class ProfileOrderTest {
    private suspend fun ProfilesRepository.names(group: Long) = getByGroup(group).map { it.name }

    @Test
    fun moveIdClampsAndShifts() {
        val ids = listOf(1L, 2L, 3L, 4L)
        assertEquals(listOf(2L, 1L, 3L, 4L), moveId(ids, 1, 1))
        assertEquals(listOf(1L, 2L, 4L, 3L), moveId(ids, 4, -1))
        assertEquals(listOf(4L, 1L, 2L, 3L), moveId(ids, 4, -10))
        assertEquals(ids, moveId(ids, 1, -1))
        assertEquals(ids, moveId(ids, 99, 1))
    }

    @Test
    fun newProfilesGoToTheEndAndOrderPersists() = runTest {
        val repo = ProfilesRepository(FakeProfileDao(), FakeCipher())
        repo.add(1, "a", SocksBean())
        repo.add(1, "b", SocksBean())
        repo.add(2, "x", SocksBean())
        repo.add(1, "c", SocksBean())
        assertEquals(listOf("a", "b", "c"), repo.names(1))

        val ids = repo.getByGroup(1).map { it.id }
        repo.reorder(listOf(ids[2], ids[0], ids[1]))
        assertEquals(listOf("c", "a", "b"), repo.names(1))
        repo.add(1, "d", SocksBean())
        assertEquals(listOf("c", "a", "b", "d"), repo.names(1))
        assertEquals(listOf("x"), repo.names(2))
    }

    @Test
    fun moveByShiftsInsideGroupOnly() = runTest {
        val repo = ProfilesRepository(FakeProfileDao(), FakeCipher())
        repo.addAll(1, listOf("a", "b", "c").map { it to SocksBean() })
        repo.add(2, "x", SocksBean())
        val ids = repo.getByGroup(1).map { it.id }

        assertTrue(repo.moveBy(ids[0], 1))
        assertEquals(listOf("b", "a", "c"), repo.names(1))
        assertTrue(repo.moveBy(ids[2], -2))
        assertEquals(listOf("c", "b", "a"), repo.names(1))
        assertFalse(repo.moveBy(ids[2], -1))
        assertEquals(listOf("x"), repo.names(2))
    }
}
