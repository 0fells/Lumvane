package com.lumvane.android.data

import com.lumvane.android.config.model.BeanJson
import com.lumvane.android.config.model.HysteriaBean
import com.lumvane.android.config.model.VlessBean
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeSettingsDao : SettingsDao {
    val rows = MutableStateFlow<Map<String, String>>(emptyMap())
    var writes = 0
    override fun observeAll(): Flow<List<SettingEntity>> = rows.map { m -> m.map { SettingEntity(it.key, it.value) } }
    override suspend fun getAll() = rows.value.map { SettingEntity(it.key, it.value) }
    override suspend fun upsertAll(rows: List<SettingEntity>) {
        writes += rows.size
        this.rows.value = this.rows.value + rows.associate { it.key to it.value }
    }
    override suspend fun delete(key: String) { rows.value = rows.value - key }
    override suspend fun replaceValue(key: String, old: String, new: String) {
        if (rows.value[key] == old) rows.value = rows.value + (key to new)
    }
}

class SettingsTest {
    @Test
    fun defaultsMatchOriginal() {
        val s = SettingsCodec.decode(emptyMap())
        assertEquals(2080, s.inboundSocksPort)
        assertEquals("https://8.8.8.8/dns-query", s.remoteDns)
        assertEquals("http://cp.cloudflare.com/", s.testLatencyUrl)
        assertEquals(-1440, s.routeAutoUpdate)
        assertEquals(1500, s.vpnMtu)
    }

    @Test
    fun codecRoundTrip() {
        val s = AppSettings(
            inboundSocksPort = 7890, allowLan = true, theme = ThemeMode.DARK, language = "ru",
            remoteDns = "tls://1.1.1.1", vpnIpv6 = true, perAppMode = PerAppMode.DENY_LISTED,
            perAppPackages = listOf("a.b", "c.d"), customInbound = "{\"x\":\"y\"}", subAutoUpdateLast = 1_700_000_000_000L,
        )
        val rows = SettingsCodec.encode(s)
        assertEquals("7890", rows["inboundSocksPort"])
        assertEquals("DARK", rows["theme"])
        assertEquals("{\"x\":\"y\"}", rows["customInbound"])
        assertEquals(s, SettingsCodec.decode(rows))
    }

    @Test
    fun unknownKeysPreservedAndBadValuesIgnored() {
        val s = SettingsCodec.decode(mapOf("legacy_key" to "v", "inboundSocksPort" to "oops", "vpnMtu" to "1400"))
        assertEquals(mapOf("legacy_key" to "v"), s.extras)
        assertEquals(2080, s.inboundSocksPort)
        assertEquals(1400, s.vpnMtu)
        assertTrue("legacy_key" in SettingsCodec.encodeAll(s))
    }

    @Test
    fun repositoryWritesOnlyChanges() = runTest {
        val dao = FakeSettingsDao()
        val repo = SettingsRepository(dao, PlainCipher)
        repo.update { it.copy(inboundSocksPort = 9999) }
        val after = dao.writes
        repo.update { it.copy(inboundSocksPort = 9999) }
        assertEquals(after, dao.writes)
        assertEquals(9999, repo.get().inboundSocksPort)
        repo.setExtra("custom", "1")
        assertEquals("1", repo.settings.first().extras["custom"])
    }

    @Test
    fun profileEntityMapping() {
        val p = Profile(
            id = 5, groupId = 1, name = "n", bean = HysteriaBean(server = "h", serverPort = 443, protocolVersion = "2"),
            latency = 120, trafficDl = 10,
        )
        val e = p.toEntity(1, 2, PlainCipher)
        assertEquals("hysteria", e.type)
        assertEquals(p, e.toProfile(PlainCipher))
        assertEquals("h:443", p.displayAddress)
        assertNull(e.copy(beanJson = "{bad").toProfile(PlainCipher))
        assertNotNull(BeanJson.decodeOrNull(e.beanJson))
        assertEquals("", Profile(groupId = 1, name = "x", bean = VlessBean()).displayAddress)
    }

    @Test
    fun secretSettingsAreEncryptedAtRest() = runTest {
        val dao = FakeSettingsDao()
        val repo = SettingsRepository(dao, FakeCipher())
        repo.update { it.copy(inboundPass = "dummy-pass", inboundUser = "user") }
        assertTrue(SecretBox.isEncrypted(dao.rows.value.getValue("inboundPass")))
        assertEquals("user", dao.rows.value.getValue("inboundUser"))
        assertEquals("dummy-pass", repo.get().inboundPass)
        dao.rows.value = dao.rows.value + ("warpPrivateKey" to "legacy-key")
        assertEquals("legacy-key", repo.get().warpPrivateKey)
        SecretMigration(FakeProfileDao(), FakeGroupDao(), FakeCipher(), dao).run()
        assertTrue(SecretBox.isEncrypted(dao.rows.value.getValue("warpPrivateKey")))
        assertEquals("legacy-key", repo.get().warpPrivateKey)
    }
}
