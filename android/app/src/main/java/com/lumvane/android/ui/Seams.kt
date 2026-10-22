package com.lumvane.android.ui

import android.content.Context
import com.lumvane.android.config.model.OutboundBean
import com.lumvane.android.data.AppDatabase
import com.lumvane.android.data.Profile
import com.lumvane.android.data.Repositories
import com.lumvane.android.service.VpnSettings
import com.lumvane.android.service.VpnStartOptions
import com.lumvane.lumcore.TestOptions
import com.lumvane.lumcore.Lumcore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

class NotConnectedException(what: String) : UnsupportedOperationException("$what is not connected yet")

data class ConfigBundle(
    val configJson: String,
    val options: VpnStartOptions = VpnStartOptions(),
    val vpnSettings: VpnSettings? = null,
)

interface ConfigProvider {
    suspend fun buildForProfile(profileId: Long): Result<ConfigBundle>
}

data class ImportedProfile(val name: String, val bean: OutboundBean)

data class ImportResult(
    val profiles: List<ImportedProfile> = emptyList(),
    val subscriptionUrls: List<String> = emptyList(),
    val failed: Int = 0,
)

interface ProfileImporter {
    suspend fun parse(text: String): ImportResult

    fun toShareLink(name: String, bean: OutboundBean): String?
}

interface SubscriptionUpdater {
    suspend fun update(groupId: Long): Result<Int>
}

interface RuleSetUpdater {
    suspend fun updateAll(): Result<Unit>
}

interface RemoteRoutes {
    suspend fun update(routeId: Long): Result<List<String>>
}

interface LatencyTester {
    suspend fun test(
        profiles: List<Profile>,
        url: String,
        timeoutMs: Int,
        concurrency: Int,
        onResult: suspend (Long, Int) -> Unit,
    )

    suspend fun testRunning(url: String, timeoutMs: Int): Int
}

object DefaultLatencyTester : LatencyTester {
    override suspend fun test(
        profiles: List<Profile>, url: String, timeoutMs: Int, concurrency: Int,
        onResult: suspend (Long, Int) -> Unit,
    ) {
        throw NotConnectedException("Latency test")
    }

    override suspend fun testRunning(url: String, timeoutMs: Int): Int = withContext(Dispatchers.IO) {
        val o = TestOptions().apply {
            current = true
            useDefaultOutbound = true
            this.url = url
            this.timeoutMs = timeoutMs
        }
        val arr = JSONArray(Lumcore.urlTest(o))
        if (arr.length() == 0) return@withContext -1
        val r = arr.getJSONObject(0)
        if (r.optString("error").isNotEmpty()) -1 else r.optInt("latency_ms", -1)
    }
}

private object NotConnectedConfigProvider : ConfigProvider {
    override suspend fun buildForProfile(profileId: Long) =
        Result.failure<ConfigBundle>(NotConnectedException("Config generator"))
}

private object NotConnectedImporter : ProfileImporter {
    override suspend fun parse(text: String) = throw NotConnectedException("Importer")
    override fun toShareLink(name: String, bean: OutboundBean): String? = null
}

private object NotConnectedSubscriptions : SubscriptionUpdater {
    override suspend fun update(groupId: Long) = Result.failure<Int>(NotConnectedException("Subscription updater"))
}

private object NotConnectedRuleSets : RuleSetUpdater {
    override suspend fun updateAll() = Result.failure<Unit>(NotConnectedException("Rule-set updater"))
}

private object NotConnectedRemoteRoutes : RemoteRoutes {
    override suspend fun update(routeId: Long) = Result.failure<List<String>>(NotConnectedException("Remote routes"))
}

object AppGraph {
    @Volatile private var repositories: Repositories? = null

    fun repos(context: Context): Repositories =
        repositories ?: synchronized(this) {
            repositories ?: Repositories(AppDatabase.getInstance(context.applicationContext)).also { repositories = it }
        }

    internal fun useRepositories(repos: Repositories) {
        repositories = repos
    }

    @Volatile var configProvider: ConfigProvider = NotConnectedConfigProvider
    @Volatile var importer: ProfileImporter = NotConnectedImporter
    @Volatile var subscriptions: SubscriptionUpdater = NotConnectedSubscriptions
    @Volatile var ruleSets: RuleSetUpdater = NotConnectedRuleSets
    @Volatile var remoteRoutes: RemoteRoutes = NotConnectedRemoteRoutes
    @Volatile var latency: LatencyTester = DefaultLatencyTester
}
