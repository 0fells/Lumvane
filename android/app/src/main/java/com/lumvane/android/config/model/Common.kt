package com.lumvane.android.config.model

import kotlinx.serialization.Serializable

@Serializable
data class DialFields(
    val reuseAddr: Boolean = false,
    val connectTimeout: String = "",
    val tcpFastOpen: Boolean = false,
    val tcpMultiPath: Boolean = false,
    val udpFragment: Boolean = false,
    val bindInterface: String = "",
    val inet4BindAddress: String = "",
    val inet6BindAddress: String = "",
)

@Serializable
data class UTls(
    val enabled: Boolean = false,
    val fingerprint: String = "",
)

@Serializable
data class Ech(
    val enabled: Boolean = false,
    val config: List<String> = emptyList(),
    val configPath: String = "",
    val serverName: String = "",
)

@Serializable
data class Reality(
    val enabled: Boolean = false,
    val publicKey: String = "",
    val shortId: String = "",
)

@Serializable
data class TlsConfig(
    val enabled: Boolean = false,
    val disableSni: Boolean = false,
    val serverName: String = "",
    val insecure: Boolean = false,
    val alpn: List<String> = emptyList(),
    val minVersion: String = "",
    val maxVersion: String = "",
    val cipherSuites: List<String> = emptyList(),
    val curvePreferences: List<String> = emptyList(),
    val certificate: List<String> = emptyList(),
    val certificatePath: String = "",
    val certificatePublicKeySha256: List<String> = emptyList(),
    val clientCertificate: List<String> = emptyList(),
    val clientCertificatePath: String = "",
    val clientKey: List<String> = emptyList(),
    val clientKeyPath: String = "",
    val fragment: Boolean = false,
    val fragmentUnspecified: Boolean = true,
    val fragmentFallbackDelay: String = "",
    val recordFragment: Boolean = false,
    val tlsTricks: Boolean = false,
    val tlsTricksUnspecified: Boolean = true,
    val spoof: String = "",
    val spoofMethod: String = "",
    val ech: Ech = Ech(),
    val utls: UTls = UTls(),
    val reality: Reality = Reality(),
) {
    companion object {
        val FINGERPRINTS = listOf("", "chrome", "firefox", "edge", "safari", "360", "qq", "ios", "android", "random", "randomized")
        val TLS_SPOOF_METHODS = listOf("", "wrong-sequence", "wrong-checksum", "wrong-ack", "wrong-md5", "wrong-timestamp")
    }
}

@Serializable
data class TcpBrutal(
    val enabled: Boolean = false,
    val upMbps: Int = 0,
    val downMbps: Int = 0,
)

@Serializable
data class MultiplexConfig(
    val enabled: Boolean = false,
    val unspecified: Boolean = true,
    val protocol: String = "",
    val maxConnections: Int = 0,
    val minStreams: Int = 0,
    val maxStreams: Int = 0,
    val padding: Boolean = false,
    val brutal: TcpBrutal = TcpBrutal(),
) {
    companion object {
        val PROTOCOLS = listOf("smux", "yamux", "h2mux")
    }
}

@Serializable
data class TransportConfig(
    val type: String = "",
    val host: String = "",
    val path: String = "",
    val method: String = "",
    val headers: List<String> = emptyList(),
    val idleTimeout: String = "",
    val pingTimeout: String = "",
    val maxEarlyData: Int = 0,
    val earlyDataHeaderName: String = "",
    val serviceName: String = "",
)
