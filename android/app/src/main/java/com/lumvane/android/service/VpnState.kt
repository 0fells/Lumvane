package com.lumvane.android.service

sealed interface VpnState {
    data object Stopped : VpnState
    data object Starting : VpnState
    data object Started : VpnState
    data object Stopping : VpnState
    data class Error(val message: String) : VpnState
}

data class TrafficStats(
    val totalUp: Long = 0,
    val totalDown: Long = 0,
    val upSpeed: Long = 0,
    val downSpeed: Long = 0,
)

data class LogLine(val level: Int, val message: String)

data class VpnStartOptions(
    val xrayConfig: String = "",
    val xrayLazyStart: Boolean = false,
    val xrayIdleSeconds: Int = 0,
    val xrayFullConfigsJson: String = "",
    val xrayFullIdleSeconds: Int = 0,
    val xrayOutboundDnsAddress: String = "",
    val xrayOutboundDnsStrategy: String = "",
)

data class VpnSettings(
    val allowBypass: Boolean = false,
    val sessionName: String = "Lumvane",
)
