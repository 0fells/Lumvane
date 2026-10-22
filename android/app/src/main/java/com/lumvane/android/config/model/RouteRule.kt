package com.lumvane.android.config.model

import kotlinx.serialization.Serializable

object OutboundIds {
    const val PROXY = -1
    const val DIRECT = -2
    const val BLOCK = -3
    const val WARP_BYPASS = -5
}

object RuleType {
    const val CUSTOM = 0
    const val SIMPLE_ADDRESS_PROXY = 1
    const val SIMPLE_ADDRESS_BYPASS = 2
    const val SIMPLE_ADDRESS_BLOCK = 3
    const val SIMPLE_PROCESS_NAME_PROXY = 4
    const val SIMPLE_PROCESS_NAME_BYPASS = 5
    const val SIMPLE_PROCESS_NAME_BLOCK = 6
    const val SIMPLE_PROCESS_PATH_PROXY = 7
    const val SIMPLE_PROCESS_PATH_BYPASS = 8
    const val SIMPLE_PROCESS_PATH_BLOCK = 9
    const val SIMPLE_ADDRESS_WARP_BYPASS = 10
    const val SIMPLE_PROCESS_NAME_WARP_BYPASS = 11
    const val SIMPLE_PROCESS_PATH_WARP_BYPASS = 12
}

@Serializable
data class RouteRule(
    val name: String = "",
    val type: Int = RuleType.CUSTOM,
    val ipVersion: String = "",
    val network: String = "",
    val protocol: String = "",
    val inbound: List<String> = emptyList(),
    val domain: List<String> = emptyList(),
    val domainSuffix: List<String> = emptyList(),
    val domainKeyword: List<String> = emptyList(),
    val domainRegex: List<String> = emptyList(),
    val sourceIpCidr: List<String> = emptyList(),
    val sourceIpIsPrivate: Boolean = false,
    val ipCidr: List<String> = emptyList(),
    val ipIsPrivate: Boolean = false,
    val sourcePort: List<String> = emptyList(),
    val sourcePortRange: List<String> = emptyList(),
    val port: List<String> = emptyList(),
    val portRange: List<String> = emptyList(),
    val processName: List<String> = emptyList(),
    val processPath: List<String> = emptyList(),
    val processPathRegex: List<String> = emptyList(),
    val wifiSsid: List<String> = emptyList(),
    val wifiBssid: List<String> = emptyList(),
    val ruleSet: List<String> = emptyList(),
    val invert: Boolean = false,
    val outboundId: Int = OutboundIds.DIRECT,
    val action: String = "route",
    val rejectMethod: String = "",
    val noDrop: Boolean = false,
    val overrideAddress: String = "",
    val overridePort: String = "",
    val sniffers: List<String> = emptyList(),
    val sniffOverrideDest: Boolean = false,
    val strategy: String = "",
)
