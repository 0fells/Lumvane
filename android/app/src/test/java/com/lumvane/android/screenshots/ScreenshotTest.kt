package com.lumvane.android.screenshots

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.lumvane.android.R
import com.lumvane.android.config.RoutePresets
import com.lumvane.android.config.model.HysteriaBean
import com.lumvane.android.config.model.OutboundBean
import com.lumvane.android.config.model.ShadowsocksBean
import com.lumvane.android.config.model.TlsConfig
import com.lumvane.android.config.model.TransportConfig
import com.lumvane.android.config.model.TrojanBean
import com.lumvane.android.config.model.TuicBean
import com.lumvane.android.config.model.VlessBean
import com.lumvane.android.data.AppDatabase
import com.lumvane.android.data.PlainCipher
import com.lumvane.android.data.Repositories
import com.lumvane.android.data.SecretBox
import com.lumvane.android.data.ThemeMode
import com.lumvane.android.service.TrafficStats
import com.lumvane.android.service.VpnController
import com.lumvane.android.service.VpnState
import com.lumvane.android.ui.AppGraph
import com.lumvane.android.ui.LumvaneApp
import com.lumvane.android.ui.theme.LumvaneTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val outDir = File(System.getProperty("lumvane.screenshots.dir") ?: "build/screenshots")
    private var db: AppDatabase? = null

    @After
    fun tearDown() {
        VpnController.setState(VpnState.Stopped)
        VpnController.setTraffic(TrafficStats())
        db?.close()
    }

    @Test
    @Config(qualifiers = "ru-w411dp-h891dp-xxhdpi")
    fun russian() = shoot(SampleData.russian, "")

    @Test
    @Config(qualifiers = "en-w411dp-h891dp-xxhdpi")
    fun english() = shoot(SampleData.english, "-en")

    @Test
    @Config(qualifiers = "ru-w411dp-h891dp-night-xxhdpi")
    fun russianDark() = shoot(SampleData.russian, "-dark", dark = true, screens = listOf(Screen.Home))

    @Test
    @Config(qualifiers = "en-w411dp-h891dp-night-xxhdpi")
    fun englishDark() = shoot(SampleData.english, "-dark-en", dark = true, screens = listOf(Screen.Home))

    private enum class Screen(val file: String, val tab: Int, val waitFor: (SampleData) -> String?) {
        Home("home", R.string.tab_home, { it.profiles.first().first }),
        Profiles("profiles", R.string.tab_profiles, { it.profiles.last().first }),
        Routes("routes", R.string.tab_routes, { it.routeName }),
        Settings("settings", R.string.tab_settings, { null }),
    }

    private fun shoot(data: SampleData, suffix: String, dark: Boolean = false, screens: List<Screen> = Screen.entries) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        seed(context, data, dark)
        VpnController.setState(VpnState.Started)
        VpnController.setTraffic(data.traffic)
        compose.setContent {
            LumvaneTheme(darkTheme = dark, dynamicColor = false) { LumvaneApp() }
        }
        outDir.mkdirs()
        for (screen in screens) {
            if (screen != Screen.Home) {
                compose.onNode(hasText(compose.activity.getString(screen.tab)) and hasClickAction()).performClick()
            }
            screen.waitFor(data)?.let { text ->
                compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
            }
            compose.waitForIdle()
            compose.onRoot().captureRoboImage(File(outDir, "android-${screen.file}$suffix.png").path)
        }
    }

    private fun seed(context: Context, data: SampleData, dark: Boolean) = runBlocking {
        SecretBox.migrationStarted.set(true)
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(AppDatabase.SeedCallback)
            .allowMainThreadQueries()
            .build()
        db = database
        val repos = Repositories(database, PlainCipher)
        AppGraph.useRepositories(repos)

        repos.groups.get(1)?.let { repos.groups.update(it.copy(name = data.groupName)) }
        val ids = repos.profiles.addAll(1, data.profiles)
        data.latencies.zip(ids).forEach { (ms, id) -> repos.profiles.setLatency(id, ms) }

        val preset = RoutePresets.byName("Bypass Russia")!!
        val routeId = repos.routes.createProfile(data.routeName, preset.defaultOutboundId)
        repos.routes.saveRules(routeId, preset.rules)
        repos.routes.getProfile(1)?.let { repos.routes.updateProfile(it.copy(name = data.secondRouteName)) }

        repos.settings.update {
            it.copy(
                rememberId = ids.first().toInt(),
                currentRouteId = routeId.toInt(),
                theme = if (dark) ThemeMode.DARK else ThemeMode.LIGHT,
            )
        }
    }
}

private class SampleData(
    val groupName: String,
    val routeName: String,
    val secondRouteName: String,
    val profiles: List<Pair<String, OutboundBean>>,
    val latencies: List<Int>,
    val traffic: TrafficStats,
) {
    companion object {
        private const val UUID = "6f1c2a4e-3b7d-4e9a-9c51-0d8f2b6a7e13"

        private fun beans(): List<OutboundBean> = listOf(
            VlessBean(server = "nl.example.com", serverPort = 443, uuid = UUID, flow = "xtls-rprx-vision",
                tls = TlsConfig(enabled = true, serverName = "nl.example.com")),
            HysteriaBean(server = "de.example.com", serverPort = 8443, protocolVersion = "2", password = "sample"),
            TrojanBean(server = "fi.example.com", serverPort = 443, password = "sample",
                tls = TlsConfig(enabled = true, serverName = "fi.example.com"), transport = TransportConfig(type = "ws", path = "/ws")),
            TuicBean(server = "se.example.com", serverPort = 443, uuid = UUID, password = "sample", congestionControl = "bbr"),
            ShadowsocksBean(server = "pl.example.com", serverPort = 8388, method = "2022-blake3-aes-128-gcm", password = "c2FtcGxlLXNhbXBsZS0xMjM="),
        )

        private val traffic = TrafficStats(
            totalUp = 52_428_800, totalDown = 1_395_864_371, upSpeed = 126_976, downSpeed = 2_516_582,
        )

        val russian = SampleData(
            groupName = "Основные",
            routeName = "Россия напрямую",
            secondRouteName = "Всё через прокси",
            profiles = listOf("Нидерланды", "Германия", "Финляндия", "Швеция", "Польша").zip(beans()),
            latencies = listOf(48, 63, 71, 94, 0),
            traffic = traffic,
        )

        val english = SampleData(
            groupName = "Main",
            routeName = "Bypass Russia",
            secondRouteName = "Everything via proxy",
            profiles = listOf("Netherlands", "Germany", "Finland", "Sweden", "Poland").zip(beans()),
            latencies = listOf(48, 63, 71, 94, 0),
            traffic = traffic,
        )
    }
}
