package com.lumvane.android.ui

import android.content.Context
import android.net.ConnectivityManager
import com.lumvane.android.config.AutoCandidate
import com.lumvane.android.config.AutoRequest
import com.lumvane.android.config.ConfigBuilder
import com.lumvane.android.config.selectAutoCandidates
import com.lumvane.android.data.AutoMode
import com.lumvane.android.config.ConfigOptions
import com.lumvane.android.config.GeneratedConfig
import com.lumvane.android.config.RouteInput
import com.lumvane.android.config.model.OutboundBean
import com.lumvane.android.data.Profile
import com.lumvane.android.importer.ContentParser
import com.lumvane.android.importer.ImportInput
import com.lumvane.android.importer.LinkExporter
import com.lumvane.android.importer.ParseOptions
import com.lumvane.android.service.VpnController
import com.lumvane.android.service.VpnStartOptions
import com.lumvane.android.sub.RemoteRouteUpdater
import com.lumvane.android.sub.RuleSetCatalog
import com.lumvane.android.sub.SubscriptionClient
import com.lumvane.android.sub.deviceDetails
import com.lumvane.android.sub.downloadProxy
import com.lumvane.lumcore.TestOptions
import com.lumvane.lumcore.Lumcore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONArray
import java.io.File
import com.lumvane.android.sub.RuleSetUpdater as CoreRuleSetUpdater
import com.lumvane.android.sub.SubscriptionUpdater as CoreSubscriptionUpdater

object UiWiring {
    @Volatile private var installed = false

    fun install(context: Context) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            val app = context.applicationContext
            val provider = CoreConfigProvider(app)
            AppGraph.configProvider = provider
            AppGraph.importer = CoreProfileImporter(app)
            AppGraph.subscriptions = CoreSubscriptions(app)
            AppGraph.ruleSets = CoreRuleSets(app)
            AppGraph.remoteRoutes = CoreRemoteRoutes(app)
            AppGraph.latency = CoreLatencyTester(app, provider)
            installed = true
        }
    }
}

class CoreConfigProvider(private val app: Context) : ConfigProvider {
    private val repos get() = AppGraph.repos(app)

    suspend fun generate(profileId: Long): GeneratedConfig = withContext(Dispatchers.IO) {
        val profile = repos.profiles.get(profileId) ?: throw IllegalStateException("profile $profileId not found")
        val settings = repos.settings.get()
        val group = repos.groups.get(profile.groupId)
        val beans = repos.profiles.observeAll().first().associate { it.id.toInt() to it.bean }

        val routeProfile = repos.routes.getProfile(settings.currentRouteId.toLong())
        val route = if (routeProfile == null) RouteInput() else RouteInput(
            defaultOutboundId = routeProfile.defaultOutboundId,
            rules = repos.routes.getRules(routeProfile.id),
            isRaw = routeProfile.isRaw,
            rawRoute = routeProfile.rawRoute,
            preventModifications = routeProfile.preventModifications,
        )
        val options = ConfigOptions(dataDir = app.filesDir.absolutePath, systemDnsServers = systemDns())
        ConfigBuilder({ id -> beans[id] }, options).build(
            profileId = profile.id.toInt(),
            bean = profile.bean,
            settings = settings,
            route = route,
            landingProxyId = group?.landingProxyId?.toInt() ?: -1,
            frontProxyId = group?.frontProxyId?.toInt() ?: -1,
        )
    }

    suspend fun generateAuto(preferredId: Long): GeneratedConfig = withContext(Dispatchers.IO) {
        val settings = repos.settings.get()
        val profiles = repos.profiles.observeAll().first()
        val beans = profiles.associate { it.id.toInt() to it.bean }
        val preferred = profiles.firstOrNull { it.id == preferredId }
        val ids = selectAutoCandidates(settings, profiles.map { AutoCandidate(it.id.toInt(), it.groupId.toInt()) }, preferred?.groupId?.toInt() ?: settings.currentGroup)
        val routeProfile = repos.routes.getProfile(settings.currentRouteId.toLong())
        val route = if (routeProfile == null) RouteInput() else RouteInput(
            defaultOutboundId = routeProfile.defaultOutboundId,
            rules = repos.routes.getRules(routeProfile.id),
            isRaw = routeProfile.isRaw,
            rawRoute = routeProfile.rawRoute,
            preventModifications = routeProfile.preventModifications,
        )
        val options = ConfigOptions(dataDir = app.filesDir.absolutePath, systemDnsServers = systemDns())
        ConfigBuilder({ id -> beans[id] }, options).buildAuto(AutoRequest(settings.autoMode, ids, preferredId.toInt()), settings, route)
    }

    override suspend fun buildForProfile(profileId: Long): Result<ConfigBundle> = try {
        val g = if (repos.settings.get().autoMode != AutoMode.OFF) generateAuto(profileId) else generate(profileId)
        g.warnings.forEach(VpnController::appLog)
        val x = g.xray
        val options = if (x == null) VpnStartOptions() else VpnStartOptions(
            xrayConfig = x.xrayConfig,
            xrayLazyStart = x.lazyStart,
            xrayIdleSeconds = x.idleSeconds,
            xrayFullConfigsJson = if (x.fullConfigs.isEmpty()) "" else JsonArray(x.fullConfigs.map { JsonPrimitive(it) }).toString(),
            xrayFullIdleSeconds = x.fullIdleSeconds,
            xrayOutboundDnsAddress = x.outboundDnsAddress,
            xrayOutboundDnsStrategy = x.outboundDnsStrategy,
        )
        Result.success(ConfigBundle(g.singBoxJson, options))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }

    private fun systemDns(): List<String> = runCatching {
        val cm = app.getSystemService(ConnectivityManager::class.java)
        cm.getLinkProperties(cm.activeNetwork)?.dnsServers?.mapNotNull { it.hostAddress }.orEmpty()
    }.getOrDefault(emptyList())
}

class CoreProfileImporter(private val app: Context) : ProfileImporter {
    override suspend fun parse(text: String): ImportResult {
        if (ImportInput.isSubscriptionUrl(text)) return ImportResult(subscriptionUrls = listOf(text.trim()))
        val settings = AppGraph.repos(app).settings.get()
        val r = withContext(Dispatchers.Default) { ContentParser.parse(text, ParseOptions(settings.xrayVlessPreference)) }
        return ImportResult(r.profiles.map { ImportedProfile(it.name, it.bean) }, emptyList(), r.skipped)
    }

    override fun toShareLink(name: String, bean: OutboundBean): String? = LinkExporter.export(bean, name)
}

class CoreSubscriptions(private val app: Context) : SubscriptionUpdater {
    private val updater by lazy {
        CoreSubscriptionUpdater(AppGraph.repos(app), SubscriptionClient(device = { deviceDetails(app) }))
    }

    override suspend fun update(groupId: Long): Result<Int> = try {
        val settings = AppGraph.repos(app).settings.get()
        updater.updateGroup(groupId, settings, downloadProxy(settings))
            .map { it.added.size + it.updated.size + it.kept.size }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
}

class CoreRemoteRoutes(private val app: Context) : RemoteRoutes {
    override suspend fun update(routeId: Long): Result<List<String>> = try {
        val repos = AppGraph.repos(app)
        val settings = repos.settings.get()
        val client = SubscriptionClient(device = { deviceDetails(app) })
        val proxy = downloadProxy(settings)
        RemoteRouteUpdater(repos) { url -> client.fetch(url, settings, proxy).body }.update(routeId).map { it.missingOutbounds }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
}

class CoreRuleSets(private val app: Context) : RuleSetUpdater {
    override suspend fun updateAll(): Result<Unit> = try {
        val repos = AppGraph.repos(app)
        val settings = repos.settings.get()
        val catalog = RuleSetCatalog.bundled()
        val used = repos.routes.profiles.first().flatMap { repos.routes.getRules(it.id) }
            .flatMap { it.ruleSet }.filter { it in catalog }.distinct()
        val updater = CoreRuleSetUpdater(
            SubscriptionClient(device = { deviceDetails(app) }), catalog, File(app.filesDir, "ruleset").also { it.mkdirs() },
        )
        val proxy = downloadProxy(settings)
        val results = updater.update(used, settings, proxy) + updater.updateXrayGeo(settings, proxy)
        val failed = results.filter { !it.ok }
        if (results.isNotEmpty() && failed.size == results.size) Result.failure(IllegalStateException(failed.first().error ?: failed.first().name))
        else Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
}

class CoreLatencyTester(private val app: Context, private val provider: CoreConfigProvider) : LatencyTester {
    override suspend fun testRunning(url: String, timeoutMs: Int): Int = DefaultLatencyTester.testRunning(url, timeoutMs)

    override suspend fun test(
        profiles: List<Profile>, url: String, timeoutMs: Int, concurrency: Int,
        onResult: suspend (Long, Int) -> Unit,
    ) {
        val running = VpnController.state.value is com.lumvane.android.service.VpnState.Started
        if (!running) {
            Lumcore.init(app.filesDir.absolutePath, app.cacheDir.absolutePath, null, VpnController.logListener)
        }
        val gate = Semaphore(concurrency.coerceIn(1, 32))
        try {
            coroutineScope {
                profiles.map { p ->
                    async(Dispatchers.IO) {
                        gate.withPermit {
                            val ms = try {
                                testOne(p, url, timeoutMs, running)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Throwable) {
                                -1
                            }
                            onResult(p.id, ms)
                        }
                    }
                }.awaitAll()
            }
        } catch (e: CancellationException) {
            runCatching { Lumcore.stopTests() }
            throw e
        }
    }

    private suspend fun testOne(p: Profile, url: String, timeoutMs: Int, running: Boolean): Int {
        val g = provider.generate(p.id)
        val o = TestOptions().apply {
            current = false
            coreConfig = prepareTestConfig(g.singBoxJson, keepAutoDetectInterface = running)
            g.xray?.let { x ->
                xrayConfig = x.xrayConfig
                if (x.fullConfigs.isNotEmpty()) xrayFullConfigsJSON = JsonArray(x.fullConfigs.map { JsonPrimitive(it) }).toString()
            }
            useDefaultOutbound = true
            this.url = url
            this.timeoutMs = timeoutMs
            this.concurrency = 1
        }
        val arr = JSONArray(Lumcore.urlTest(o))
        if (arr.length() == 0) return -1
        val r = arr.getJSONObject(0)
        return if (r.optString("error").isNotEmpty()) -1 else r.optInt("latency_ms", -1)
    }
}

fun prepareTestConfig(config: String, keepAutoDetectInterface: Boolean): String {
    val root = Json.parseToJsonElement(config).jsonObject
    val inbounds = (root["inbounds"] as? JsonArray)?.filter {
        (it as? JsonObject)?.get("type")?.jsonPrimitive?.contentOrNull != "tun"
    }
    val route = (root["route"] as? JsonObject)?.let { r ->
        if (keepAutoDetectInterface) r else JsonObject(r - "auto_detect_interface")
    }
    val out = LinkedHashMap(root)
    if (inbounds != null) out["inbounds"] = JsonArray(inbounds)
    if (route != null) out["route"] = route
    return JsonObject(out).toString()
}
