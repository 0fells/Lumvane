package com.lumvane.android.config

import com.lumvane.android.config.outbounds.autoIntervalSecEffective
import com.lumvane.android.config.outbounds.parsedConfig
import com.lumvane.android.config.model.CustomBean
import com.lumvane.android.config.model.OutboundBean
import com.lumvane.android.data.AppSettings
import com.lumvane.android.data.AutoMode
import com.lumvane.android.data.AutoScope
import com.lumvane.android.config.model.AutoSelectorBean
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.net.InetAddress
import java.net.ServerSocket
import java.security.SecureRandom

fun interface ProfileResolver {
    fun bean(id: Int): OutboundBean?
}

class ConfigBuildException(message: String) : Exception(message)

data class XrayStartParams(
    val xrayConfig: String,
    val fullConfigs: List<String> = emptyList(),
    val outboundDnsAddress: String = "",
    val outboundDnsStrategy: String = "",
    val lazyStart: Boolean = false,
    val idleSeconds: Int = 0,
    val fullIdleSeconds: Int = 0,
)

data class GeneratedConfig(
    val singBoxJson: String,
    val xray: XrayStartParams?,
    val tunIpv4Cidr: String = "",
    val warnings: List<String> = emptyList(),
)

class ConfigOptions(
    val dataDir: String = "",
    val systemDnsServers: List<String> = emptyList(),
    val allocatePort: () -> Int = ::freeLoopbackPort,
    val randomSecret: () -> String = ::secureRandomString,
    val fallbackLocalDns: String = "1.1.1.1",
)

fun freeLoopbackPort(): Int =
    ServerSocket(0, 0, InetAddress.getByName("127.0.0.1")).use { it.localPort }

fun secureRandomString(length: Int = 32): String {
    val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    val rnd = SecureRandom()
    return buildString(length) { repeat(length) { append(alphabet[rnd.nextInt(alphabet.length)]) } }
}

class ConfigBuilder(
    private val resolver: ProfileResolver,
    private val options: ConfigOptions = ConfigOptions(),
) {
    @Throws(ConfigBuildException::class)
    fun build(
        profileId: Int,
        bean: OutboundBean,
        settings: AppSettings,
        route: RouteInput = RouteInput(),
        landingProxyId: Int = -1,
        frontProxyId: Int = -1,
    ): GeneratedConfig {
        if (bean is CustomBean && bean.subtype == CustomBean.FULL_CONFIG) {
            val cfg = bean.parsedConfig()
                ?: throw ConfigBuildException("Custom full config is not valid JSON")
            return GeneratedConfig(JSON.encodeToString(JsonObject.serializer(), cfg), null)
        }
        val session = BuildSession(resolver, options, settings, route, profileId, bean, landingProxyId, frontProxyId)
        return session.run()
    }

    @Throws(ConfigBuildException::class)
    fun buildAuto(req: AutoRequest, settings: AppSettings, route: RouteInput = RouteInput()): GeneratedConfig {
        if (req.mode == AutoMode.OFF) throw ConfigBuildException("Auto mode is off")
        val marker = AutoSelectorBean(name = "auto", intervalSec = settings.autoIntervalSecEffective())
        return BuildSession(resolver, options, settings, route, -1, marker, -1, -1, req).run()
    }

    private companion object {
        val JSON = Json { prettyPrint = false }
    }
}

class AutoRequest(val mode: AutoMode, val candidateIds: List<Int>, val preferredId: Int = -1)

data class AutoCandidate(val id: Int, val groupId: Int)

fun selectAutoCandidates(settings: AppSettings, all: List<AutoCandidate>, preferredGroupId: Int): List<Int> =
    when (settings.autoScope) {
        AutoScope.ALL -> all.map { it.id }
        AutoScope.GROUP -> all.filter { it.groupId == preferredGroupId }.map { it.id }
        AutoScope.MANUAL -> { val known = all.mapTo(HashSet()) { it.id }; settings.autoProfileIds.filter { it in known } }
    }
