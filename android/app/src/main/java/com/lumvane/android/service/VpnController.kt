package com.lumvane.android.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.lumvane.lumcore.LogListener
import com.lumvane.lumcore.Lumcore
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object VpnController {
    private val _state = MutableStateFlow<VpnState>(VpnState.Stopped)
    val state: StateFlow<VpnState> = _state.asStateFlow()

    private val _traffic = MutableStateFlow(TrafficStats())
    val traffic: StateFlow<TrafficStats> = _traffic.asStateFlow()

    private val _logs = MutableSharedFlow<LogLine>(extraBufferCapacity = 1024, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val logs: SharedFlow<LogLine> = _logs.asSharedFlow()

    private val _autoStatus = MutableStateFlow<AutoStatus?>(null)

    val autoStatus: StateFlow<AutoStatus?> = _autoStatus.asStateFlow()

    private val _autoRunning = MutableStateFlow(false)

    val autoRunning: StateFlow<Boolean> = _autoRunning.asStateFlow()

    internal val logListener = LogListener { level, message -> _logs.tryEmit(LogLine(level, message)) }

    fun start(
        context: Context,
        configJson: String,
        options: VpnStartOptions = VpnStartOptions(),
        settings: VpnSettings? = null,
        profileId: Long = -1,
    ) {
        val prefs = VpnPrefs(context)
        prefs.profileId = profileId
        settings?.let { prefs.allowBypass = it.allowBypass }
        prefs.saveLast(configJson, options)
        prefs.wantRunning = true
        send(context, LumVpnService.ACTION_START, fresh = true)
    }

    fun startLast(context: Context): Boolean {
        val prefs = VpnPrefs(context)
        if (prefs.loadLast() == null) return false
        prefs.wantRunning = true
        send(context, LumVpnService.ACTION_START)
        return true
    }

    fun stop(context: Context) {
        VpnPrefs(context).wantRunning = false
        if (_state.value is VpnState.Stopped || _state.value is VpnState.Error) {
            _state.value = VpnState.Stopped
            return
        }
        context.startService(Intent(context, LumVpnService::class.java).setAction(LumVpnService.ACTION_STOP))
    }

    fun reconnect(context: Context) {
        send(context, LumVpnService.ACTION_RECONNECT)
    }

    fun isRunning(): Boolean = runCatching { Lumcore.isRunning() }.getOrDefault(false)

    fun recentLogs(): String = runCatching { Lumcore.recentLogs() }.getOrDefault("")

    fun connections(): List<ConnectionInfo> =
        runCatching { Lumcore.queryConnections(-1) }.getOrNull()
            ?.let { ConnectionsParser.parse(it)?.active }.orEmpty()
            .sortedByDescending { it.createdAt }

    private fun send(context: Context, action: String, fresh: Boolean = false) {
        ContextCompat.startForegroundService(
            context, Intent(context, LumVpnService::class.java).setAction(action).putExtra(LumVpnService.EXTRA_FRESH, fresh),
        )
    }

    internal fun setAutoStatus(s: AutoStatus?) {
        _autoStatus.value = s
    }

    internal fun setAutoRunning(v: Boolean) {
        _autoRunning.value = v
    }

    internal fun appLog(message: String) {
        _logs.tryEmit(LogLine(4, message))
    }

    fun recheckAuto(): Boolean = runCatching { Lumcore.urlTestRecheck("") }.isSuccess

    internal fun setState(s: VpnState) {
        _state.value = s
    }

    internal fun setTraffic(t: TrafficStats) {
        _traffic.value = t
    }
}
