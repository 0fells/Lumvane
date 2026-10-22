package com.lumvane.android.service

import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Network
import android.net.VpnService
import android.os.Build
import androidx.core.app.ServiceCompat
import com.lumvane.android.R
import com.lumvane.android.ui.AppGraph
import com.lumvane.android.ui.UiWiring
import com.lumvane.lumcore.StartOptions
import com.lumvane.lumcore.Lumcore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

class LumVpnService : VpnService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lifecycle = Mutex()
    private var statsJob: Job? = null
    private var platform: BoxPlatform? = null
    private val recordLock = Mutex()
    private var recordedUp = 0L
    private var recordedDown = 0L
    private val appTraffic = AppTrafficAccumulator()
    private var connectionsPolledAt = 0L
    private var statsProfileId = -1L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                VpnPrefs(this).wantRunning = false
                enterForeground(getString(R.string.vpn_stopping))
                scope.launch { shutdown(stopSelf = true) }
            }
            else -> {
                enterForeground(getString(R.string.vpn_connecting))
                val fresh = intent?.getBooleanExtra(EXTRA_FRESH, false) == true
                scope.launch { restart(fresh) }
            }
        }
        return START_NOT_STICKY
    }

    private fun enterForeground(text: String) {
        val n = Notifications.build(this, text)
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(this, Notifications.NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifications.NOTIFICATION_ID, n)
        }
    }

    private suspend fun rebuildLast() {
        val prefs = VpnPrefs(this)
        val id = prefs.profileId
        if (id < 0) return
        UiWiring.install(this)
        AppGraph.configProvider.buildForProfile(id)
            .onSuccess { prefs.saveLast(it.configJson, it.options) }
            .onFailure { VpnController.appLog("Config rebuild failed, using the saved one: " + it.message.orEmpty()) }
    }

    private suspend fun restart(fresh: Boolean): Unit = lifecycle.withLock {
        if (!fresh) rebuildLast()
        val saved = VpnPrefs(this).loadLast()
        recordTraffic()
        stopCore()
        if (saved == null) {
            VpnController.setState(VpnState.Error("No saved profile"))
            finish()
            return@withLock
        }
        VpnController.setState(VpnState.Starting)
        try {
            val prefs = VpnPrefs(this)
            val p = BoxPlatform(this, VpnSettings(allowBypass = prefs.allowBypass), ::onUnderlying, ::onNetworkChanged)
            platform = p
            Lumcore.init(filesDir.absolutePath, cacheDir.absolutePath, p, VpnController.logListener)
            val (config, opts) = saved
            val so = StartOptions().apply {
                coreConfig = config
                xrayConfig = opts.xrayConfig
                xrayLazyStart = opts.xrayLazyStart
                xrayIdleSeconds = opts.xrayIdleSeconds
                xrayFullConfigsJSON = opts.xrayFullConfigsJson
                xrayFullIdleSeconds = opts.xrayFullIdleSeconds
                xrayOutboundDNSAddress = opts.xrayOutboundDnsAddress
                xrayOutboundDNSStrategy = opts.xrayOutboundDnsStrategy
            }
            Lumcore.start(so)
            VpnController.setTraffic(TrafficStats())
            recordedUp = 0
            recordedDown = 0
            appTraffic.reset()
            connectionsPolledAt = 0
            statsProfileId = prefs.profileId
            VpnController.setAutoRunning(hasUrlTestGroup(config))
            VpnController.setState(VpnState.Started)
            startStats()
        } catch (t: Throwable) {
            runCatching { Lumcore.stop() }
            platform?.closeDefaultInterfaceMonitor()
            platform?.closeTun()
            platform = null
            VpnController.setState(VpnState.Error(t.message ?: t.javaClass.simpleName))
            finish()
        }
    }

    private suspend fun shutdown(stopSelf: Boolean): Unit = lifecycle.withLock {
        recordTraffic()
        if (VpnController.state.value !is VpnState.Error) VpnController.setState(VpnState.Stopping)
        stopCore()
        if (VpnController.state.value !is VpnState.Error) VpnController.setState(VpnState.Stopped)
        if (stopSelf) finish()
    }

    private fun stopCore() {
        statsJob?.cancel()
        statsJob = null
        VpnController.setAutoStatus(null)
        VpnController.setAutoRunning(false)
        lastAutoNow = ""
        runCatching { Lumcore.stop() }
        platform?.closeDefaultInterfaceMonitor()
        platform?.closeTun()
        platform = null
    }

    private fun finish() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun onNetworkChanged() {
        if (VpnController.autoStatus.value == null) return
        scope.launch {
            delay(1500)
            VpnController.appLog("Auto: network changed, re-checking profiles")
            VpnController.recheckAuto()
            delay(4000)
            pollAuto()
        }
    }

    private val profileNames = HashMap<Int, String>()
    private var lastAutoNow = ""

    private val autoLock = Mutex()

    private suspend fun refreshNames() {
        runCatching {
            AppGraph.repos(this).profiles.observeAll().first().forEach { profileNames[it.id.toInt()] = it.name.ifEmpty { it.displayAddress } }
        }
    }

    private suspend fun pollAuto(): Unit = autoLock.withLock {
        val raw = runCatching { Lumcore.queryUrlTestGroups() }.getOrNull()
        if (raw == null || raw == "[]") { VpnController.setAutoStatus(null); return@withLock }
        var st = AutoStatusParser.parse(raw) { profileNames[it] }
        if (st != null && st.members.any { it.profileId != null && it.profileId !in profileNames }) {
            refreshNames()
            st = AutoStatusParser.parse(raw) { profileNames[it] }
        }
        if (st != null && st.nowTag.isNotEmpty() && st.nowTag != lastAutoNow) {
            val ms = if (st.nowDelayMs >= 0) " (${st.nowDelayMs} ms)" else ""
            VpnController.appLog(if (lastAutoNow.isEmpty()) "Auto: active profile ${st.nowName}$ms" else "Auto: switched to ${st.nowName}$ms")
            autoSwitchAlert(st, lastAutoNow)?.let { Notifications.notifyAutoSwitch(this, it) }
            lastAutoNow = st.nowTag
        }
        VpnController.setAutoStatus(st)
    }

    private fun autoSwitchAlert(st: AutoStatus, prevTag: String): String? {
        val main = st.members.firstOrNull { it.profileId != null && it.profileId.toLong() == statsProfileId } ?: return null
        return when {
            st.nowTag == main.tag -> if (prevTag.isEmpty()) null else getString(R.string.auto_alert_back, main.name)
            prevTag.isEmpty() || prevTag == main.tag -> getString(R.string.auto_alert_left, main.name, st.nowName)
            else -> getString(R.string.auto_alert_switched, st.nowName)
        }
    }

    private fun onUnderlying(n: Network?) {
        setUnderlyingNetworks(n?.let { arrayOf(it) })
    }

    private fun pollConnections() = synchronized(appTraffic) {
        val since = if (connectionsPolledAt == 0L) 0L else connectionsPolledAt - CONNECTIONS_SLACK_MS
        connectionsPolledAt = System.currentTimeMillis()
        val raw = runCatching { Lumcore.queryConnections(since) }.getOrNull() ?: return@synchronized
        ConnectionsParser.parse(raw)?.let { appTraffic.add(it) }
    }

    private suspend fun recordTraffic() = recordLock.withLock {
        if (VpnController.state.value !is VpnState.Started) return@withLock
        pollConnections()
        val apps = synchronized(appTraffic) { appTraffic.drain() }
        if (apps.isNotEmpty()) runCatching {
            val traffic = AppGraph.repos(this).traffic
            for ((owner, bytes) in apps) {
                traffic.touchAppMeta(owner, "")
                traffic.addAppTraffic(owner, bytes.first, bytes.second)
            }
        }
        val id = statsProfileId
        if (id < 0) return@withLock
        val up = runCatching { Lumcore.totalUp() }.getOrDefault(0)
        val down = runCatching { Lumcore.totalDown() }.getOrDefault(0)
        val dUp = up - recordedUp
        val dDown = down - recordedDown
        if (dUp < 0 || dDown < 0) { recordedUp = up; recordedDown = down; return@withLock }
        if (dUp == 0L && dDown == 0L) return@withLock
        recordedUp = up
        recordedDown = down
        runCatching {
            val repos = AppGraph.repos(this)
            repos.profiles.get(id)?.let { p ->
                repos.traffic.touchConfigMeta(p, repos.groups.get(p.groupId)?.name.orEmpty())
            }
            repos.traffic.addConfigTraffic(id, dUp, dDown)
            repos.traffic.maintain(repos.settings.get().trafficStatsRetentionDays)
        }
    }

    private fun startStats() {
        statsJob = scope.launch {
            var lastUp = Lumcore.totalUp()
            var lastDown = Lumcore.totalDown()
            val nm = getSystemService(NotificationManager::class.java)
            var lastRecord = System.currentTimeMillis()
            var tick = 0
            while (isActive) {
                delay(1000)
                if (tick % 3 == 0) pollAuto()
                if (tick % CONNECTIONS_POLL_TICKS == 0) pollConnections()
                tick++
                val up = Lumcore.totalUp()
                val down = Lumcore.totalDown()
                val t = TrafficStats(up, down, (up - lastUp).coerceAtLeast(0), (down - lastDown).coerceAtLeast(0))
                lastUp = up
                lastDown = down
                VpnController.setTraffic(t)
                if (System.currentTimeMillis() - lastRecord >= RECORD_INTERVAL_MS) {
                    lastRecord = System.currentTimeMillis()
                    recordTraffic()
                }
                val speeds = "↑ ${formatSpeed(t.upSpeed)}  ↓ ${formatSpeed(t.downSpeed)}"
                val text = VpnController.autoStatus.value?.nowName?.takeIf { it.isNotEmpty() }?.let { "$it  $speeds" } ?: speeds
                runCatching { nm.notify(Notifications.NOTIFICATION_ID, Notifications.build(this@LumVpnService, text)) }
            }
        }
    }

    override fun onRevoke() {
        VpnPrefs(this).wantRunning = false
        scope.launch { shutdown(stopSelf = true) }
    }

    override fun onDestroy() {
        statsJob?.cancel()
        runCatching { Lumcore.stop() }
        platform?.closeDefaultInterfaceMonitor()
        platform?.closeTun()
        val s = VpnController.state.value
        if (s !is VpnState.Error) VpnController.setState(VpnState.Stopped)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val RECORD_INTERVAL_MS = 60_000L

        private const val CONNECTIONS_POLL_TICKS = 5
        private const val CONNECTIONS_SLACK_MS = 2_000L
        const val ACTION_START = "com.lumvane.android.action.START"
        const val ACTION_STOP = "com.lumvane.android.action.STOP"
        const val ACTION_RECONNECT = "com.lumvane.android.action.RECONNECT"

        const val EXTRA_FRESH = "com.lumvane.android.extra.FRESH"
    }
}

internal fun hasUrlTestGroup(config: String): Boolean = runCatching {
    val obs = Json.parseToJsonElement(config).jsonObject["outbounds"] as? JsonArray ?: return false
    obs.any { ((it as? JsonObject)?.get("type") as? JsonPrimitive)?.contentOrNull == "urltest" }
}.getOrDefault(false)
