package com.lumvane.android.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@OptIn(ExperimentalSerializationApi::class)
@Serializable
enum class AutoMode {
    OFF,
    @JsonNames("FASTEST", "RESERVE") ON,
}

@Serializable
enum class AutoScope { ALL, GROUP, MANUAL }

@Serializable
enum class PerAppMode { OFF, ALLOW_LISTED, DENY_LISTED }

@Serializable
data class AppSettings(
    val logLevel: String = "info",
    val logFileLevel: String = "debug",
    val testLatencyUrl: String = "http://cp.cloudflare.com/",
    val urlTestTimeoutMs: Int = 3000,
    val testConcurrent: Int = 10,
    val disableTrafficStats: Boolean = false,
    val currentGroup: Int = DEFAULT_GROUP_ID,
    val muxProtocol: String = "smux",
    val muxPadding: Boolean = false,
    val muxConcurrency: Int = 8,
    val muxDefaultOn: Boolean = false,
    val fragmentImplementation: String = "built-in",
    val fragmentDefaultOn: Boolean = false,
    val fragmentSize: String = "10-100",
    val fragmentSleep: String = "2-5",
    val tlsTricksDefaultOn: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val language: String = "",
    val maxLogLine: Int = 200,
    val logAutoScroll: Boolean = true,
    val enableStats: Boolean = true,
    val trafficStatsRetentionDays: Int = 90,
    val disableTrafficAggregation: Boolean = false,
    val speedTestMode: Int = 0,
    val speedTestTimeoutMs: Int = 5000,
    val simpleDlUrl: String = "http://cachefly.cachefly.net/1mb.test",
    val skipDeleteConfirmation: Boolean = false,
    val showConfigSecurity: Boolean = false,
    val netUseProxy: Boolean = false,
    val netInsecure: Boolean = false,
    val userAgent: String = "",
    val subAutoUpdate: Int = -30,
    val subAutoUpdateLast: Long = 0,
    val subClear: Boolean = false,
    val subShowChangePopup: Boolean = true,
    val subSendHwid: Boolean = false,
    val subCustomHwidParams: String = "",
    val allowStoppingActiveProfile: Boolean = false,
    val skipCert: Boolean = false,
    val utlsFingerprint: String = "",
    val useMozillaCerts: Boolean = false,
    val rememberTun: Boolean = false,
    val rememberId: Int = NO_PROFILE_ID,
    val rememberEnable: Boolean = false,
    val currentRouteId: Int = DEFAULT_ROUTE_ID,
    val routeAutoUpdate: Int = -1440,
    val routeAutoUpdateLast: Long = 0,
    val remoteDns: String = "https://8.8.8.8/dns-query",
    val remoteDnsStrategy: String = "",
    val directDns: String = "localhost",
    val directDnsStrategy: String = "",
    val dnsCacheCapacity: Int = 65536,
    val dnsDisableCache: Boolean = false,
    val dnsDisableExpire: Boolean = false,
    val dnsOptimisticCache: Boolean = true,
    val dnsReverseMapping: Boolean = false,
    val enableDnsRouting: Boolean = true,
    val useDnsObject: Boolean = false,
    val dnsObject: String = "",
    val dnsFinalOut: String = "remote",
    val resolveDomainStrategy: String = "",
    val defaultDomainStrategy: String = "",
    val rulesetMirror: Int = 1,
    val customRouteGlobal: String = "{\"rules\": []}",
    val activeRouting: String = "Default",
    val adblockEnable: Boolean = false,
    val disableMixedInbound: Boolean = false,
    val inboundAddress: String = "127.0.0.1",
    val inboundSocksPort: Int = 2080,
    val randomInboundPort: Boolean = false,
    val customInbound: String = "{\"inbounds\": []}",
    val proxyScheme: String = "{ip}:{port}",
    val inboundAuth: Boolean = false,
    val inboundUser: String = "",
    val inboundPass: String = "",
    val allowLan: Boolean = false,
    val vpnMode: Boolean = true,
    val fakeDns: Boolean = false,
    val enableTunRouting: Boolean = false,
    val vpnImplementation: String = "system",
    val vpnStrictRoute: Boolean = false,
    val vpnAutoRedirect: Boolean = false,
    val vpnMtu: Int = 1500,
    val disablePrivateRangeBypass: Boolean = false,
    val vpnIpv6: Boolean = false,
    val vpnTunIpv4Cidr: String = "172.19.0.1/24",
    val vpnTunIpv6Cidr: String = "fdfe:dcba:9876::1/96",
    val perAppMode: PerAppMode = PerAppMode.OFF,
    val perAppPackages: List<String> = emptyList(),
    val startOnBoot: Boolean = false,
    val enableNtp: Boolean = false,
    val ntpServerAddress: String = "",
    val ntpServerPort: Int = 0,
    val ntpInterval: String = "",
    val ntpOutbound: String = "direct",
    val enableWarp: Boolean = false,
    val warpPrivateKey: String = "",
    val warpPublicKey: String = "",
    val warpIfcAddrs: List<String> = emptyList(),
    val warpEp: String = "",
    val warpReserved: List<String> = emptyList(),
    val enableDnsServer: Boolean = false,
    val dnsServerListenLan: Boolean = false,
    val dnsServerListenPort: Int = 53,
    val dnsV4Resp: String = "127.0.0.1",
    val dnsV6Resp: String = "::1",
    val dnsServerRules: List<String> = emptyList(),
    val enableRedirect: Boolean = false,
    val redirectListenAddress: String = "127.0.0.1",
    val redirectListenPort: Int = 443,
    val coreBoxClashApi: Int = -9090,
    val coreBoxClashListenAddr: String = "127.0.0.1",
    val coreBoxClashApiSecret: String = "",
    val coreBoxUnderlyingDns: String = "",
    val coreDnsInPort: Int = 5533,
    val xrayLogLevel: String = "warning",
    val xrayMuxConcurrency: Int = 8,
    val xrayMuxDefaultOn: Boolean = false,
    val xrayVlessPreference: Int = 0,
    val xrayGeoipUrl: String = "https://github.com/Loyalsoldier/v2ray-rules-dat/raw/release/geoip.dat",
    val xrayGeositeUrl: String = "https://github.com/Loyalsoldier/v2ray-rules-dat/raw/release/geosite.dat",
    val autoMode: AutoMode = AutoMode.OFF,
    val autoIntervalSec: Int = 60,
    val autoTestUrl: String = "",
    val autoToleranceMs: Int = 0,
    val autoScope: AutoScope = AutoScope.ALL,
    val autoProfileIds: List<Int> = emptyList(),
    val dialBindInterfaceHistory: List<String> = emptyList(),
    val dialInet4BindAddressHistory: List<String> = emptyList(),
    val dialInet6BindAddressHistory: List<String> = emptyList(),
    @Transient val extras: Map<String, String> = emptyMap(),
) {
    val mixedInboundEnabled: Boolean
        get() = !disableMixedInbound && (!vpnMode || inboundAuth || allowLan)

    companion object {
        const val NO_PROFILE_ID = -1
        const val DEFAULT_GROUP_ID = 1
        const val DEFAULT_ROUTE_ID = 1
    }
}

@OptIn(ExperimentalSerializationApi::class)
object SettingsCodec {
    val SECRET_KEYS = setOf("warpPrivateKey", "inboundPass", "coreBoxClashApiSecret")

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true; coerceInputValues = true }
    private val serializer = AppSettings.serializer()
    private val descriptor = serializer.descriptor
    private val names: List<String> = (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }
    private val nameSet = names.toSet()

    private fun isStringLike(index: Int): Boolean {
        val kind = descriptor.getElementDescriptor(index).kind
        return kind == PrimitiveKind.STRING || kind == SerialKind.ENUM
    }

    fun encode(settings: AppSettings): Map<String, String> {
        val obj = json.encodeToJsonElement(serializer, settings).jsonObject
        return obj.mapValues { (_, v) ->
            if (v is JsonPrimitive && v.isString) v.content else v.toString()
        }
    }

    fun encodeAll(settings: AppSettings): Map<String, String> = settings.extras + encode(settings)

    fun decode(rows: Map<String, String>): AppSettings {
        val parsed = LinkedHashMap<String, kotlinx.serialization.json.JsonElement>()
        names.forEachIndexed { i, n ->
            val raw = rows[n] ?: return@forEachIndexed
            val el = if (isStringLike(i)) JsonPrimitive(raw) else runCatching { json.parseToJsonElement(raw) }.getOrNull()
            if (el != null) parsed[n] = el
        }
        val extras = rows.filterKeys { it !in nameSet }
        val full = runCatching { json.decodeFromJsonElement(serializer, JsonObject(parsed)) }.getOrNull()
        val result = full ?: run {
            val good = parsed.filter { (k, v) ->
                runCatching { json.decodeFromJsonElement(serializer, JsonObject(mapOf(k to v))) }.isSuccess
            }
            json.decodeFromJsonElement(serializer, JsonObject(good))
        }
        return result.copy(extras = extras)
    }
}
