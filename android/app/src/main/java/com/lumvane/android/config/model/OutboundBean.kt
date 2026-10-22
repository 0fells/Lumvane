package com.lumvane.android.config.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

@Serializable
sealed interface OutboundBean {
    val name: String
}

sealed interface ServerBean : OutboundBean {
    val server: String
    val serverPort: Int
    val dial: DialFields
}

sealed interface TlsBean : ServerBean {
    val tls: TlsConfig
}

sealed interface MuxBean : ServerBean {
    val multiplex: MultiplexConfig
}

sealed interface TransportBean : ServerBean {
    val transport: TransportConfig
}

@Serializable
@SerialName("socks")
data class SocksBean(
    override val name: String = "",
    override val server: String = "",
    override val serverPort: Int = 0,
    override val dial: DialFields = DialFields(),
    val username: String = "",
    val password: String = "",
    val version: Int = 5,
    val uot: Boolean = false,
) : ServerBean

@Serializable
@SerialName("http")
data class HttpBean(
    override val name: String = "",
    override val server: String = "",
    override val serverPort: Int = 0,
    override val dial: DialFields = DialFields(),
    val username: String = "",
    val password: String = "",
    val path: String = "",
    val headers: List<String> = emptyList(),
    override val tls: TlsConfig = TlsConfig(),
) : TlsBean

@Serializable
@SerialName("shadowsocks")
data class ShadowsocksBean(
    override val name: String = "",
    override val server: String = "",
    override val serverPort: Int = 0,
    override val dial: DialFields = DialFields(),
    val method: String = "",
    val password: String = "",
    val plugin: String = "",
    val pluginOpts: String = "",
    val uot: Boolean = false,
    override val multiplex: MultiplexConfig = MultiplexConfig(),
) : MuxBean {
    companion object {
        val METHODS = listOf(
            "2022-blake3-aes-128-gcm", "2022-blake3-aes-256-gcm", "2022-blake3-chacha20-poly1305", "none",
            "aes-128-gcm", "aes-192-gcm", "aes-256-gcm", "chacha20-ietf-poly1305", "xchacha20-ietf-poly1305",
            "aes-128-ctr", "aes-192-ctr", "aes-256-ctr", "aes-128-cfb", "aes-192-cfb", "aes-256-cfb",
            "rc4-md5", "chacha20-ietf", "xchacha20",
        )
    }
}

@Serializable
@SerialName("vmess")
data class VmessBean(
    override val name: String = "",
    override val server: String = "",
    override val serverPort: Int = 0,
    override val dial: DialFields = DialFields(),
    val uuid: String = "",
    val security: String = "auto",
    val alterId: Int = 0,
    val globalPadding: Boolean = false,
    val authenticatedLength: Boolean = false,
    override val tls: TlsConfig = TlsConfig(),
    val packetEncoding: String = "xudp",
    override val transport: TransportConfig = TransportConfig(),
    override val multiplex: MultiplexConfig = MultiplexConfig(),
) : TlsBean, MuxBean, TransportBean

@Serializable
@SerialName("vless")
data class VlessBean(
    override val name: String = "",
    override val server: String = "",
    override val serverPort: Int = 0,
    override val dial: DialFields = DialFields(),
    val uuid: String = "",
    val flow: String = "",
    override val tls: TlsConfig = TlsConfig(),
    val packetEncoding: String = "xudp",
    override val multiplex: MultiplexConfig = MultiplexConfig(),
    override val transport: TransportConfig = TransportConfig(),
) : TlsBean, MuxBean, TransportBean

@Serializable
@SerialName("trojan")
data class TrojanBean(
    override val name: String = "",
    override val server: String = "",
    override val serverPort: Int = 0,
    override val dial: DialFields = DialFields(),
    val password: String = "",
    override val tls: TlsConfig = TlsConfig(),
    override val multiplex: MultiplexConfig = MultiplexConfig(),
    override val transport: TransportConfig = TransportConfig(),
) : TlsBean, MuxBean, TransportBean

@Serializable
@SerialName("xrayvless")
data class XrayVlessBean(
    override val name: String = "",
    override val server: String = "",
    override val serverPort: Int = 0,
    override val dial: DialFields = DialFields(),
    val uuid: String = "",
    val encryption: String = "none",
    val flow: String = "",
    val streamSetting: XrayStreamSetting = XrayStreamSetting(),
    val multiplex: XrayMultiplex = XrayMultiplex(),
) : ServerBean

@Serializable
@SerialName("hysteria")
data class HysteriaBean(
    override val name: String = "",
    override val server: String = "",
    override val serverPort: Int = 0,
    override val dial: DialFields = DialFields(),
    val protocolVersion: String = "1",
    val serverPorts: List<String> = emptyList(),
    val hopInterval: String = "",
    val upMbps: Int = 0,
    val downMbps: Int = 0,
    val obfs: String = "",
    val authType: String = "",
    val auth: String = "",
    val recvWindowConn: Int = 0,
    val recvWindow: Int = 0,
    val disableMtuDiscovery: Boolean = false,
    val password: String = "",
    val hopIntervalMax: String = "",
    val obfsType: String = "",
    val obfsMinPacketSize: Int = 0,
    val obfsMaxPacketSize: Int = 0,
    val bbrProfile: String = "",
    val disableChromeParrot: Boolean = false,
    override val tls: TlsConfig = TlsConfig(),
) : TlsBean {
    val isV2: Boolean get() = protocolVersion == "2"

    companion object {
        val OBFS_TYPES = listOf("salamander", "gecko")
        val BBR_PROFILES = listOf("", "conservative", "standard", "aggressive")
    }
}

@Serializable
@SerialName("tuic")
data class TuicBean(
    override val name: String = "",
    override val server: String = "",
    override val serverPort: Int = 0,
    override val dial: DialFields = DialFields(),
    val uuid: String = "",
    val password: String = "",
    val congestionControl: String = "",
    val udpRelayMode: String = "",
    val udpOverStream: Boolean = false,
    val zeroRttHandshake: Boolean = false,
    val heartbeat: String = "",
    override val tls: TlsConfig = TlsConfig(),
) : TlsBean

@Serializable
@SerialName("juicity")
data class JuicityBean(
    override val name: String = "",
    override val server: String = "",
    override val serverPort: Int = 0,
    override val dial: DialFields = DialFields(),
    val uuid: String = "",
    val password: String = "",
    override val tls: TlsConfig = TlsConfig(),
) : TlsBean

@Serializable
@SerialName("trusttunnel")
data class TrustTunnelBean(
    override val name: String = "",
    override val server: String = "",
    override val serverPort: Int = 0,
    override val dial: DialFields = DialFields(),
    val username: String = "",
    val password: String = "",
    val congestionControl: String = "",
    val healthCheck: Boolean = false,
    val quic: Boolean = false,
    override val tls: TlsConfig = TlsConfig(),
) : TlsBean

@Serializable
@SerialName("naive")
data class NaiveBean(
    override val name: String = "",
    override val server: String = "",
    override val serverPort: Int = 0,
    override val dial: DialFields = DialFields(),
    val username: String = "",
    val password: String = "",
    val congestionControl: String = "",
    val quic: Boolean = false,
    val uot: Boolean = false,
    override val tls: TlsConfig = TlsConfig(),
) : TlsBean

@Serializable
@SerialName("anytls")
data class AnyTlsBean(
    override val name: String = "",
    override val server: String = "",
    override val serverPort: Int = 0,
    override val dial: DialFields = DialFields(),
    val password: String = "",
    val idleSessionCheckInterval: String = "30s",
    val idleSessionTimeout: String = "30s",
    val minIdleSession: Int = 5,
    override val tls: TlsConfig = TlsConfig(),
) : TlsBean

@Serializable
@SerialName("shadowtls")
data class ShadowTlsBean(
    override val name: String = "",
    override val server: String = "",
    override val serverPort: Int = 0,
    override val dial: DialFields = DialFields(),
    val version: Int = 1,
    val password: String = "",
    override val tls: TlsConfig = TlsConfig(),
) : TlsBean

@Serializable
@SerialName("mieru")
data class MieruBean(
    override val name: String = "",
    override val server: String = "",
    override val serverPort: Int = 0,
    override val dial: DialFields = DialFields(),
    val transport: String = "TCP",
    val username: String = "",
    val password: String = "",
    val multiplexing: String = "",
    val trafficPattern: String = "",
    val serverPorts: String = "",
) : ServerBean

@Serializable
@SerialName("ssh")
data class SshBean(
    override val name: String = "",
    override val server: String = "",
    override val serverPort: Int = 0,
    override val dial: DialFields = DialFields(),
    val user: String = "",
    val password: String = "",
    val privateKey: String = "",
    val privateKeyPath: String = "",
    val privateKeyPassphrase: String = "",
    val hostKey: List<String> = emptyList(),
    val hostKeyAlgorithms: List<String> = emptyList(),
    val clientVersion: String = "",
) : ServerBean

@Serializable
data class WireGuardPeer(
    val address: String = "",
    val port: Int = 0,
    val publicKey: String = "",
    val preSharedKey: String = "",
    val reserved: List<Int> = emptyList(),
    val persistentKeepalive: String = "",
)

@Serializable
@SerialName("wireguard")
data class WireGuardBean(
    override val name: String = "",
    val dial: DialFields = DialFields(),
    val privateKey: String = "",
    val peer: WireGuardPeer = WireGuardPeer(),
    val address: List<String> = emptyList(),
    val mtu: Int = 1420,
    val system: Boolean = false,
    val workerCount: Int = 0,
    val udpTimeout: String = "",
    val enableAmnezia: Boolean = false,
    val jc: Int = 0,
    val jmin: Int = 0,
    val jmax: Int = 0,
    val s1: Int = 0,
    val s2: Int = 0,
    val s3: Int = 0,
    val s4: Int = 0,
    val h1: String = "",
    val h2: String = "",
    val h3: String = "",
    val h4: String = "",
    val i1: String = "",
    val i2: String = "",
    val i3: String = "",
    val i4: String = "",
    val i5: String = "",
    val headerProtectionKey: String = "",
    val contentPaddingAddition: String = "",
    val rekeyAfterTime: String = "",
    val rekeyTimeout: String = "",
    val rejectAfterTime: String = "",
    val keepaliveTimeout: String = "",
    val maxHandshakeAttempts: String = "",
) : OutboundBean

@Serializable
@SerialName("tailscale")
data class TailscaleBean(
    override val name: String = "",
    val dial: DialFields = DialFields(),
    val stateDirectory: String = "\$HOME/.tailscale",
    val authKey: String = "",
    val controlUrl: String = "https://controlplane.tailscale.com",
    val ephemeral: Boolean = false,
    val hostname: String = "",
    val acceptRoutes: Boolean = false,
    val exitNode: String = "",
    val exitNodeAllowLanAccess: Boolean = false,
    val advertiseRoutes: List<String> = emptyList(),
    val advertiseExitNode: Boolean = false,
    val globalDns: Boolean = false,
) : OutboundBean

@Serializable
@SerialName("direct")
data class DirectBean(
    override val name: String = "",
    val dial: DialFields = DialFields(),
) : OutboundBean

@Serializable
@SerialName("chain")
data class ChainBean(
    override val name: String = "",
    val list: List<Int> = emptyList(),
) : OutboundBean

@Serializable
@SerialName("custom")
data class CustomBean(
    override val name: String = "",
    val subtype: String = OUTBOUND,
    val config: String = "",
) : OutboundBean {
    companion object {
        const val OUTBOUND = "outbound"
        const val FULL_CONFIG = "fullconfig"
        const val XRAY_OUTBOUND = "xrayoutbound"
        const val XRAY_FULL_CONFIG = "xrayfullconfig"
    }
}

@Serializable
@SerialName("autoselector")
data class AutoSelectorBean(
    override val name: String = "",
    val gid: Int = -1,
    val nameFilter: String = "",
    val countryFilter: String = "",
    val excludeUnavailable: Boolean = true,
    val poolCap: Int = 1000,
    val buildLimit: Int = 300,
    val resultValidityMins: Int = 1440,
    val testUrl: String = "",
    val connectivityUrl: String = "",
    val intervalSec: Int = 300,
    val benchIntervalSec: Int = 600,
    val watchIntervalSec: Int = 15,
    val activeSize: Int = 8,
    val sampling: Int = 10,
    val toleranceMs: Int = 300,
    val maxRttMs: Int = 0,
    val expected: Int = 3,
    val dialRetries: Int = 2,
    val interruptOnSwitch: Boolean = true,
    val balance: Boolean = false,
    val balanceMode: String = "rotate",
    val balanceIntervalSec: Int = 30,
    val pool: List<Int> = emptyList(),
    val poolRankedAt: Long = 0,
    val pinnedId: Int = -1,
    val lastBuilt: List<Int> = emptyList(),
    val lastBuiltAt: Long = 0,
    val history: JsonArray = JsonArray(emptyList<JsonElement>()),
) : OutboundBean

val OutboundBean.typeName: String
    get() = when (this) {
        is SocksBean -> "socks"
        is HttpBean -> "http"
        is ShadowsocksBean -> "shadowsocks"
        is VmessBean -> "vmess"
        is VlessBean -> "vless"
        is TrojanBean -> "trojan"
        is XrayVlessBean -> "xrayvless"
        is HysteriaBean -> "hysteria"
        is TuicBean -> "tuic"
        is JuicityBean -> "juicity"
        is TrustTunnelBean -> "trusttunnel"
        is NaiveBean -> "naive"
        is AnyTlsBean -> "anytls"
        is ShadowTlsBean -> "shadowtls"
        is MieruBean -> "mieru"
        is SshBean -> "ssh"
        is WireGuardBean -> "wireguard"
        is TailscaleBean -> "tailscale"
        is DirectBean -> "direct"
        is ChainBean -> "chain"
        is CustomBean -> "custom"
        is AutoSelectorBean -> "autoselector"
    }

val OutboundBean.endpoint: Pair<String, Int>?
    get() = when (this) {
        is ServerBean -> server to serverPort
        is WireGuardBean -> peer.address to peer.port
        else -> null
    }
