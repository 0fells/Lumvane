package com.lumvane.android.importer

import com.lumvane.android.config.model.AnyTlsBean
import com.lumvane.android.config.model.HttpBean
import com.lumvane.android.config.model.HysteriaBean
import com.lumvane.android.config.model.JuicityBean
import com.lumvane.android.config.model.MieruBean
import com.lumvane.android.config.model.NaiveBean
import com.lumvane.android.config.model.OutboundBean
import com.lumvane.android.config.model.ShadowTlsBean
import com.lumvane.android.config.model.ShadowsocksBean
import com.lumvane.android.config.model.SocksBean
import com.lumvane.android.config.model.SshBean
import com.lumvane.android.config.model.TrojanBean
import com.lumvane.android.config.model.TrustTunnelBean
import com.lumvane.android.config.model.TuicBean
import com.lumvane.android.config.model.VlessBean
import com.lumvane.android.config.model.VmessBean
import com.lumvane.android.config.model.WireGuardBean
import com.lumvane.android.config.model.XrayVlessBean

object LinkExporter {
    fun export(bean: OutboundBean, nameOverride: String? = null): String? {
        val name = nameOverride ?: bean.name
        return when (bean) {
            is VlessBean -> link("vless", bean.uuid, null, bean.server, bean.serverPort, name) { q ->
                q.add("encryption", "none")
                q.addNotEmpty("flow", bean.flow)
                exportTls(bean.tls, q); exportTransport(bean.transport, q); exportMux(bean.multiplex, q)
                q.add("packetEncoding", bean.packetEncoding.ifEmpty { "none" })
            }
            is XrayVlessBean -> link("vless", bean.uuid, null, bean.server, bean.serverPort, name) { q ->
                q.add("encryption", bean.encryption)
                if (bean.flow.isNotEmpty() && bean.flow != "none") q.add("flow", bean.flow)
                exportXrayStream(bean.streamSetting, q); exportXrayMux(bean.multiplex, q)
            }
            is VmessBean -> link("vmess", bean.uuid, null, bean.server, bean.serverPort, name) { q ->
                if (bean.security != "auto") q.add("encryption", bean.security)
                exportTls(bean.tls, q); exportTransport(bean.transport, q); exportMux(bean.multiplex, q)
                q.addIf(bean.alterId > 0, "alterId", bean.alterId.toString())
                q.addIf(bean.globalPadding, "globalPadding", "true")
                q.addIf(bean.authenticatedLength, "authenticatedLength", "true")
                q.add("packetEncoding", bean.packetEncoding.ifEmpty { "none" })
            }
            is TrojanBean -> link("trojan", bean.password, null, bean.server, bean.serverPort, name) { q ->
                if (bean.tls.enabled) exportTls(bean.tls, q) else q.add("security", "none")
                exportTransport(bean.transport, q); exportMux(bean.multiplex, q)
            }
            is ShadowsocksBean -> {
                val user: String
                val pw: String?
                if (bean.method.startsWith("2022-")) { user = bean.method; pw = bean.password }
                else { user = UrlParts.b64UrlEncode("${bean.method}:${bean.password}".toByteArray()); pw = null }
                link("ss", user, pw, bean.server, bean.serverPort, name, rawUser = pw == null) { q ->
                    if (bean.plugin.isNotEmpty()) {
                        q.add("plugin", bean.plugin + if (bean.pluginOpts.isNotEmpty()) ";" + bean.pluginOpts else "")
                    }
                    q.addIf(bean.uot, "uot", "1")
                    exportMux(bean.multiplex, q); exportDial(bean.dial, q)
                }
            }
            is HysteriaBean -> exportHysteria(bean, name)
            is TuicBean -> link("tuic", bean.uuid, bean.password.takeIf { it.isNotEmpty() }, bean.server, bean.serverPort, name) { q ->
                q.addNotEmpty("congestion_control", bean.congestionControl)
                q.addIf(bean.udpRelayMode.isNotEmpty() && !bean.udpOverStream, "udp_relay_mode", bean.udpRelayMode)
                q.addIf(bean.udpOverStream, "udp_over_stream", "true")
                q.addIf(bean.zeroRttHandshake, "zero_rtt_handshake", "true")
                q.addNotEmpty("heartbeat", bean.heartbeat)
                exportTls(bean.tls, q); exportDial(bean.dial, q)
            }
            is AnyTlsBean -> link("anytls", bean.password, null, bean.server, bean.serverPort, name) { q ->
                q.addNotEmpty("idle_session_check_interval", bean.idleSessionCheckInterval)
                q.addNotEmpty("idle_session_timeout", bean.idleSessionTimeout)
                q.addIf(bean.minIdleSession > 0, "min_idle_session", bean.minIdleSession.toString())
                exportTls(bean.tls, q); exportDial(bean.dial, q)
            }
            is SocksBean -> link(if (bean.version == 4) "socks4" else "socks5", bean.username.takeIf { it.isNotEmpty() },
                bean.password.takeIf { it.isNotEmpty() }, bean.server, bean.serverPort, name) { q ->
                q.addIf(bean.uot, "uot", "1"); exportDial(bean.dial, q)
            }
            is HttpBean -> link(if (bean.tls.enabled) "https" else "http", bean.username.takeIf { it.isNotEmpty() },
                bean.password.takeIf { it.isNotEmpty() }, bean.server, bean.serverPort, name) { q ->
                q.addNotEmpty("path", bean.path)
                q.addIf(bean.headers.isNotEmpty(), "headers", bean.headers.joinToString(","))
                exportTls(bean.tls, q); exportDial(bean.dial, q)
            }
            is JuicityBean -> link("juicity", bean.uuid, bean.password.takeIf { it.isNotEmpty() }, bean.server, bean.serverPort, name) { q ->
                exportTls(bean.tls, q); exportDial(bean.dial, q)
            }
            is TrustTunnelBean -> link("tt", bean.username, bean.password, bean.server, bean.serverPort, name) { q ->
                q.addIf(bean.healthCheck, "health_check", "true")
                q.addIf(bean.quic && bean.congestionControl.isNotEmpty(), "congestion_control", bean.congestionControl)
                exportTls(bean.tls, q); exportDial(bean.dial, q)
            }
            is ShadowTlsBean -> link("shadowtls", null, bean.password.takeIf { bean.version > 1 && it.isNotEmpty() }, bean.server, bean.serverPort, name) { q ->
                q.add("version", bean.version.toString()); exportTls(bean.tls, q); exportDial(bean.dial, q)
            }
            is NaiveBean -> link(if (bean.quic) "naive+quic" else "naive+https", bean.username.takeIf { it.isNotEmpty() },
                bean.password.takeIf { it.isNotEmpty() }, bean.server, bean.serverPort, name) { q ->
                q.addIf(bean.uot, "uot", "1")
                q.addIf(bean.quic && bean.congestionControl.isNotEmpty(), "congestion_control", bean.congestionControl)
                exportTls(bean.tls, q); exportDial(bean.dial, q)
            }
            is SshBean -> link("ssh", null, null, bean.server, bean.serverPort, name) { q ->
                q.addNotEmpty("user", bean.user)
                q.addNotEmpty("password", bean.password)
                q.addIf(bean.privateKey.isNotEmpty(), "private_key", UrlParts.b64Encode(bean.privateKey.toByteArray()).trimEnd('='))
                q.addNotEmpty("private_key_path", bean.privateKeyPath)
                q.addNotEmpty("private_key_passphrase", bean.privateKeyPassphrase)
                q.addIf(bean.hostKey.isNotEmpty(), "host_key", bean.hostKey.joinToString("-") { UrlParts.b64Encode(it.toByteArray()).trimEnd('=') })
                q.addIf(bean.hostKeyAlgorithms.isNotEmpty(), "host_key_algorithms", bean.hostKeyAlgorithms.joinToString("-") { UrlParts.b64Encode(it.toByteArray()).trimEnd('=') })
                q.addNotEmpty("client_version", bean.clientVersion)
                exportDial(bean.dial, q)
            }
            is MieruBean -> {
                val q = Q()
                q.add("profile", "default")
                val proto = bean.transport.ifEmpty { "TCP" }.uppercase()
                if (bean.serverPort > 0) { q.add("port", bean.serverPort.toString()); q.add("protocol", proto) }
                bean.serverPorts.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { q.add("port", it); q.add("protocol", proto) }
                q.addNotEmpty("multiplexing", bean.multiplexing)
                q.addNotEmpty("traffic-pattern", bean.trafficPattern)
                exportDial(bean.dial, q)
                build("mierus", bean.username.takeIf { it.isNotEmpty() }, bean.password.takeIf { it.isNotEmpty() }, bean.server, 0, name, q)
            }
            is WireGuardBean -> exportWireGuard(bean, name)
            else -> null
        }
    }

    private fun link(
        scheme: String, user: String?, password: String?, host: String, port: Int, name: String,
        rawUser: Boolean = false, fill: (Q) -> Unit,
    ): String {
        val q = Q()
        fill(q)
        return build(scheme, user, password, host, port, name, q, rawUser)
    }

    private fun build(
        scheme: String, user: String?, password: String?, host: String, port: Int, name: String, q: Q, rawUser: Boolean = false,
        portText: String? = null,
    ): String {
        val sb = StringBuilder(scheme).append("://")
        if (user != null || password != null) {
            if (user != null) sb.append(if (rawUser) user else UrlParts.encode(user))
            if (password != null) sb.append(':').append(UrlParts.encode(password))
            sb.append('@')
        }
        sb.append(if (host.contains(':')) "[$host]" else host)
        if (portText != null) sb.append(':').append(portText) else if (port > 0) sb.append(':').append(port)
        if (!q.isEmpty()) sb.append("/?").append(q.encoded())
        if (name.isNotEmpty()) sb.append('#').append(UrlParts.encode(name))
        return sb.toString()
    }

    private fun exportHysteria(b: HysteriaBean, name: String): String {
        val q = Q()
        val v1 = b.protocolVersion == "1"
        var user: String? = null
        var pw: String? = null
        if (v1) {
            q.addIf(b.obfs.isNotEmpty(), "obfsParam", b.obfs)
            q.addIf(b.authType == "STRING" && b.auth.isNotEmpty(), "auth", b.auth)
            q.addIf(b.recvWindowConn > 0, "recv_window_conn", b.recvWindowConn.toString())
            q.addIf(b.recvWindow > 0, "recv_window", b.recvWindow.toString())
            q.addIf(b.disableMtuDiscovery, "disable_mtu_discovery", "true")
        } else {
            if (b.password.contains(':')) { user = b.password.substringBefore(':'); pw = b.password.substringAfter(':') } else user = b.password
            if (b.obfs.isNotEmpty()) {
                q.add("obfs", if (b.obfsType == "gecko") "gecko" else "salamander")
                q.add("obfs-password", b.obfs)
                if (b.obfsType == "gecko") {
                    q.addIf(b.obfsMinPacketSize > 0, "obfs-min-packet-size", b.obfsMinPacketSize.toString())
                    q.addIf(b.obfsMaxPacketSize > 0, "obfs-max-packet-size", b.obfsMaxPacketSize.toString())
                }
            }
            q.addNotEmpty("hop_interval_max", b.hopIntervalMax)
            q.addNotEmpty("bbr_profile", b.bbrProfile)
            q.addIf(b.disableChromeParrot, "disable_chrome_parrot", "true")
        }
        q.addIf(b.upMbps > 0, "upmbps", b.upMbps.toString())
        q.addIf(b.downMbps > 0, "downmbps", b.downMbps.toString())
        q.addNotEmpty("hop_interval", b.hopInterval)
        exportTls(b.tls, q); exportDial(b.dial, q)
        val scheme = if (v1) "hysteria" else "hysteria2"
        val ranges = b.serverPorts.map { it.replace(':', '-') }
        if (ranges.isEmpty() || b.serverPort > 0) {
            if (ranges.isNotEmpty()) q.add("mport", ranges.joinToString(","))
            return build(scheme, user, pw, b.server, b.serverPort, name, q)
        }
        return build(scheme, user, pw, b.server, 0, name, q, portText = ranges.joinToString(","))
    }

    private fun exportWireGuard(w: WireGuardBean, name: String): String {
        val q = Q()
        q.addNotEmpty("private_key", w.privateKey)
        q.addIf(w.address.isNotEmpty(), "local_address", w.address.joinToString("-"))
        q.addIf(w.mtu > 0 && w.mtu != 1420, "mtu", w.mtu.toString())
        q.addIf(w.system, "use_system_interface", "true")
        q.addIf(w.workerCount > 0, "workers", w.workerCount.toString())
        q.addNotEmpty("udp_timeout", w.udpTimeout)
        if (w.enableAmnezia) {
            q.add("enable_amnezia", "true")
            q.addIf(w.jc > 0, "jc", w.jc.toString()); q.addIf(w.jmin > 0, "jmin", w.jmin.toString()); q.addIf(w.jmax > 0, "jmax", w.jmax.toString())
            q.addIf(w.s1 > 0, "s1", w.s1.toString()); q.addIf(w.s2 > 0, "s2", w.s2.toString())
            q.addIf(w.s3 > 0, "s3", w.s3.toString()); q.addIf(w.s4 > 0, "s4", w.s4.toString())
            q.addNotEmpty("h1", w.h1); q.addNotEmpty("h2", w.h2); q.addNotEmpty("h3", w.h3); q.addNotEmpty("h4", w.h4)
            q.addNotEmpty("i1", w.i1); q.addNotEmpty("i2", w.i2); q.addNotEmpty("i3", w.i3); q.addNotEmpty("i4", w.i4); q.addNotEmpty("i5", w.i5)
            q.addNotEmpty("header_protection_key", w.headerProtectionKey)
            q.addNotEmpty("content_padding_addition", w.contentPaddingAddition)
            q.addNotEmpty("rekey_after_time", w.rekeyAfterTime)
            q.addNotEmpty("rekey_timeout", w.rekeyTimeout)
            q.addNotEmpty("reject_after_time", w.rejectAfterTime)
            q.addNotEmpty("keepalive_timeout", w.keepaliveTimeout)
            q.addNotEmpty("max_handshake_attempts", w.maxHandshakeAttempts)
        }
        exportDial(w.dial, q)
        q.addNotEmpty("public_key", w.peer.publicKey)
        q.addNotEmpty("pre_shared_key", w.peer.preSharedKey)
        q.addIf(w.peer.reserved.isNotEmpty(), "reserved", w.peer.reserved.joinToString("-"))
        q.addNotEmpty("persistent_keepalive_interval", w.peer.persistentKeepalive)
        return build("wg", null, null, w.peer.address, w.peer.port, name, q)
    }

    fun exportWireGuardConf(w: WireGuardBean): String = buildString {
        appendLine("[Interface]")
        appendLine("PrivateKey = ${w.privateKey}")
        if (w.address.isNotEmpty()) appendLine("Address = ${w.address.joinToString(", ")}")
        if (w.mtu > 0 && w.mtu != 1420) appendLine("MTU = ${w.mtu}")
        if (w.enableAmnezia) {
            fun n(k: String, v: Int) { if (v > 0) appendLine("$k = $v") }
            fun t(k: String, v: String) { if (v.isNotEmpty()) appendLine("$k = $v") }
            n("Jc", w.jc); n("Jmin", w.jmin); n("Jmax", w.jmax); n("S1", w.s1); n("S2", w.s2); n("S3", w.s3); n("S4", w.s4)
            t("H1", w.h1); t("H2", w.h2); t("H3", w.h3); t("H4", w.h4)
            t("I1", w.i1); t("I2", w.i2); t("I3", w.i3); t("I4", w.i4); t("I5", w.i5)
            t("HeaderProtectionKey", w.headerProtectionKey); t("ContentPaddingAddition", w.contentPaddingAddition)
            t("RekeyAfterTime", w.rekeyAfterTime); t("RekeyTimeout", w.rekeyTimeout); t("RejectAfterTime", w.rejectAfterTime)
            t("KeepaliveTimeout", w.keepaliveTimeout); t("MaxHandshakeAttempts", w.maxHandshakeAttempts)
        }
        appendLine()
        appendLine("[Peer]")
        appendLine("PublicKey = ${w.peer.publicKey}")
        if (w.peer.preSharedKey.isNotEmpty()) appendLine("PresharedKey = ${w.peer.preSharedKey}")
        appendLine("AllowedIPs = 0.0.0.0/0, ::/0")
        val host = if (w.peer.address.contains(':')) "[${w.peer.address}]" else w.peer.address
        appendLine("Endpoint = $host:${w.peer.port}")
        if (w.peer.persistentKeepalive.isNotEmpty()) appendLine("PersistentKeepalive = ${w.peer.persistentKeepalive}")
        if (w.peer.reserved.isNotEmpty()) appendLine("Reserved = ${w.peer.reserved.joinToString(", ")}")
    }
}
