package com.lumvane.android.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lumvane.android.R
import com.lumvane.android.config.model.DialFields
import com.lumvane.android.config.model.MultiplexConfig
import com.lumvane.android.config.model.TlsConfig
import com.lumvane.android.config.model.TransportConfig
import com.lumvane.android.config.model.XrayMultiplex
import com.lumvane.android.config.model.XrayStreamSetting
import com.lumvane.android.config.model.XrayXhttp

private const val NL = '\n'

@Composable
fun ServerFields(server: String, port: Int, onServer: (String) -> Unit, onPort: (Int) -> Unit) {
    TextF(stringResource(R.string.f_server), server, onServer)
    IntF(stringResource(R.string.f_port), port, onPort)
}

@Composable
fun DialSection(dial: DialFields, onChange: (DialFields) -> Unit) {
    FormSection(stringResource(R.string.sec_dial), collapsible = true) {
        TextF(stringResource(R.string.f_connect_timeout), dial.connectTimeout, { onChange(dial.copy(connectTimeout = it)) })
        SwitchF(stringResource(R.string.f_tcp_fast_open), dial.tcpFastOpen, { onChange(dial.copy(tcpFastOpen = it)) })
        SwitchF(stringResource(R.string.f_tcp_multipath), dial.tcpMultiPath, { onChange(dial.copy(tcpMultiPath = it)) })
        SwitchF(stringResource(R.string.f_udp_fragment), dial.udpFragment, { onChange(dial.copy(udpFragment = it)) })
        SwitchF(stringResource(R.string.f_reuse_addr), dial.reuseAddr, { onChange(dial.copy(reuseAddr = it)) })
        TextF(stringResource(R.string.f_bind_interface), dial.bindInterface, { onChange(dial.copy(bindInterface = it)) })
        TextF(stringResource(R.string.f_bind_v4), dial.inet4BindAddress, { onChange(dial.copy(inet4BindAddress = it)) })
        TextF(stringResource(R.string.f_bind_v6), dial.inet6BindAddress, { onChange(dial.copy(inet6BindAddress = it)) })
    }
}

@Composable
fun TlsSection(
    tls: TlsConfig,
    onChange: (TlsConfig) -> Unit,
    allowToggle: Boolean = true,
    allowReality: Boolean = false,
    allowUtls: Boolean = true,
) {
    FormSection(stringResource(R.string.sec_tls)) {
        if (allowToggle) SwitchF(stringResource(R.string.f_tls_enabled), tls.enabled, { onChange(tls.copy(enabled = it)) })
        if (!allowToggle || tls.enabled) {
            TextF(stringResource(R.string.f_sni), tls.serverName, { onChange(tls.copy(serverName = it)) })
            SwitchF(stringResource(R.string.f_insecure), tls.insecure, { onChange(tls.copy(insecure = it)) })
            ListF(stringResource(R.string.f_alpn), tls.alpn, { onChange(tls.copy(alpn = it)) })
            if (allowUtls) {
                SwitchF(stringResource(R.string.f_utls), tls.utls.enabled, { onChange(tls.copy(utls = tls.utls.copy(enabled = it))) })
                if (tls.utls.enabled) {
                    ChoiceF(
                        stringResource(R.string.f_fingerprint), tls.utls.fingerprint, TlsConfig.FINGERPRINTS,
                        { onChange(tls.copy(utls = tls.utls.copy(fingerprint = it))) },
                    )
                }
            }
            if (allowReality) {
                SwitchF(stringResource(R.string.f_reality), tls.reality.enabled, { onChange(tls.copy(reality = tls.reality.copy(enabled = it))) })
                if (tls.reality.enabled) {
                    TextF(stringResource(R.string.f_reality_public_key), tls.reality.publicKey, { onChange(tls.copy(reality = tls.reality.copy(publicKey = it))) })
                    TextF(stringResource(R.string.f_reality_short_id), tls.reality.shortId, { onChange(tls.copy(reality = tls.reality.copy(shortId = it))) })
                }
            }
        }
    }
    if (!allowToggle || tls.enabled) {
        FormSection(stringResource(R.string.sec_tls_advanced), collapsible = true) {
            SwitchF(stringResource(R.string.f_disable_sni), tls.disableSni, { onChange(tls.copy(disableSni = it)) })
            val versions = listOf("", "1.0", "1.1", "1.2", "1.3")
            ChoiceF(stringResource(R.string.f_tls_min), tls.minVersion, versions, { onChange(tls.copy(minVersion = it)) })
            ChoiceF(stringResource(R.string.f_tls_max), tls.maxVersion, versions, { onChange(tls.copy(maxVersion = it)) })
            ListF(stringResource(R.string.f_cipher_suites), tls.cipherSuites, { onChange(tls.copy(cipherSuites = it)) })
            ListF(stringResource(R.string.f_curves), tls.curvePreferences, { onChange(tls.copy(curvePreferences = it)) })
            ListF(stringResource(R.string.f_cert_pin), tls.certificatePublicKeySha256, { onChange(tls.copy(certificatePublicKeySha256 = it)) })
            ListF(stringResource(R.string.f_certificate), tls.certificate, { onChange(tls.copy(certificate = it)) }, separator = NL, minLines = 3)
            TextF(stringResource(R.string.f_certificate_path), tls.certificatePath, { onChange(tls.copy(certificatePath = it)) })
            ListF(stringResource(R.string.f_client_certificate), tls.clientCertificate, { onChange(tls.copy(clientCertificate = it)) }, separator = NL, minLines = 3)
            TextF(stringResource(R.string.f_client_certificate_path), tls.clientCertificatePath, { onChange(tls.copy(clientCertificatePath = it)) })
            ListF(stringResource(R.string.f_client_key), tls.clientKey, { onChange(tls.copy(clientKey = it)) }, separator = NL, minLines = 3)
            TextF(stringResource(R.string.f_client_key_path), tls.clientKeyPath, { onChange(tls.copy(clientKeyPath = it)) })
            SwitchF(stringResource(R.string.f_ech), tls.ech.enabled, { onChange(tls.copy(ech = tls.ech.copy(enabled = it))) })
            if (tls.ech.enabled) {
                ListF(stringResource(R.string.f_ech_config), tls.ech.config, { onChange(tls.copy(ech = tls.ech.copy(config = it))) }, separator = NL, minLines = 2)
                TextF(stringResource(R.string.f_ech_config_path), tls.ech.configPath, { onChange(tls.copy(ech = tls.ech.copy(configPath = it))) })
            }
            TriStateF(stringResource(R.string.f_tls_fragment), tls.fragmentUnspecified, tls.fragment) { u, e ->
                onChange(tls.copy(fragmentUnspecified = u, fragment = e))
            }
            if (!tls.fragmentUnspecified && tls.fragment) {
                TextF(stringResource(R.string.f_fragment_delay), tls.fragmentFallbackDelay, { onChange(tls.copy(fragmentFallbackDelay = it)) })
            }
            SwitchF(stringResource(R.string.f_record_fragment), tls.recordFragment, { onChange(tls.copy(recordFragment = it)) })
            TriStateF(stringResource(R.string.f_tls_tricks), tls.tlsTricksUnspecified, tls.tlsTricks) { u, e ->
                onChange(tls.copy(tlsTricksUnspecified = u, tlsTricks = e))
            }
            if (!tls.tlsTricksUnspecified && tls.tlsTricks) {
                TextF(stringResource(R.string.f_spoof), tls.spoof, { onChange(tls.copy(spoof = it)) })
                ChoiceF(stringResource(R.string.f_spoof_method), tls.spoofMethod, TlsConfig.TLS_SPOOF_METHODS, { onChange(tls.copy(spoofMethod = it)) })
            }
        }
    }
}

@Composable
fun TransportSection(t: TransportConfig, onChange: (TransportConfig) -> Unit) {
    FormSection(stringResource(R.string.sec_transport)) {
        ChoiceF(
            stringResource(R.string.f_transport_type), t.type, listOf("", "ws", "grpc", "http", "httpupgrade", "quic"),
            { onChange(t.copy(type = it)) },
        )
        when (t.type) {
            "ws" -> {
                TextF(stringResource(R.string.f_host), t.host, { onChange(t.copy(host = it)) })
                TextF(stringResource(R.string.f_path), t.path, { onChange(t.copy(path = it)) })
                ListF(stringResource(R.string.f_headers), t.headers, { onChange(t.copy(headers = it)) }, separator = NL, minLines = 2)
                IntF(stringResource(R.string.f_max_early_data), t.maxEarlyData, { onChange(t.copy(maxEarlyData = it)) })
                TextF(stringResource(R.string.f_early_data_header), t.earlyDataHeaderName, { onChange(t.copy(earlyDataHeaderName = it)) })
            }
            "grpc" -> {
                TextF(stringResource(R.string.f_service_name), t.serviceName, { onChange(t.copy(serviceName = it)) })
                TextF(stringResource(R.string.f_idle_timeout), t.idleTimeout, { onChange(t.copy(idleTimeout = it)) })
                TextF(stringResource(R.string.f_ping_timeout), t.pingTimeout, { onChange(t.copy(pingTimeout = it)) })
            }
            "http" -> {
                TextF(stringResource(R.string.f_host), t.host, { onChange(t.copy(host = it)) })
                TextF(stringResource(R.string.f_path), t.path, { onChange(t.copy(path = it)) })
                TextF(stringResource(R.string.f_method), t.method, { onChange(t.copy(method = it)) })
                ListF(stringResource(R.string.f_headers), t.headers, { onChange(t.copy(headers = it)) }, separator = NL, minLines = 2)
                TextF(stringResource(R.string.f_idle_timeout), t.idleTimeout, { onChange(t.copy(idleTimeout = it)) })
                TextF(stringResource(R.string.f_ping_timeout), t.pingTimeout, { onChange(t.copy(pingTimeout = it)) })
            }
            "httpupgrade" -> {
                TextF(stringResource(R.string.f_host), t.host, { onChange(t.copy(host = it)) })
                TextF(stringResource(R.string.f_path), t.path, { onChange(t.copy(path = it)) })
                ListF(stringResource(R.string.f_headers), t.headers, { onChange(t.copy(headers = it)) }, separator = NL, minLines = 2)
            }
        }
    }
}

@Composable
fun MultiplexSection(m: MultiplexConfig, onChange: (MultiplexConfig) -> Unit) {
    FormSection(stringResource(R.string.sec_multiplex), collapsible = true) {
        TriStateF(stringResource(R.string.f_multiplex), m.unspecified, m.enabled) { u, e ->
            onChange(m.copy(unspecified = u, enabled = e))
        }
        if (!m.unspecified && m.enabled) {
            ChoiceF(stringResource(R.string.f_mux_protocol), m.protocol, listOf("") + MultiplexConfig.PROTOCOLS, { onChange(m.copy(protocol = it)) })
            IntF(stringResource(R.string.f_mux_max_connections), m.maxConnections, { onChange(m.copy(maxConnections = it)) })
            IntF(stringResource(R.string.f_mux_min_streams), m.minStreams, { onChange(m.copy(minStreams = it)) })
            IntF(stringResource(R.string.f_mux_max_streams), m.maxStreams, { onChange(m.copy(maxStreams = it)) })
            SwitchF(stringResource(R.string.f_mux_padding), m.padding, { onChange(m.copy(padding = it)) })
            SwitchF(stringResource(R.string.f_tcp_brutal), m.brutal.enabled, { onChange(m.copy(brutal = m.brutal.copy(enabled = it))) })
            if (m.brutal.enabled) {
                IntF(stringResource(R.string.f_up_mbps), m.brutal.upMbps, { onChange(m.copy(brutal = m.brutal.copy(upMbps = it))) })
                IntF(stringResource(R.string.f_down_mbps), m.brutal.downMbps, { onChange(m.copy(brutal = m.brutal.copy(downMbps = it))) })
            }
        }
    }
}

@Composable
fun XrayMultiplexSection(m: XrayMultiplex, onChange: (XrayMultiplex) -> Unit) {
    FormSection(stringResource(R.string.sec_multiplex), collapsible = true) {
        SwitchF(stringResource(R.string.f_xray_mux_default), m.useDefault, { onChange(m.copy(useDefault = it)) })
        if (!m.useDefault) {
            SwitchF(stringResource(R.string.f_multiplex), m.enabled, { onChange(m.copy(enabled = it)) })
            if (m.enabled) {
                IntF(stringResource(R.string.f_xray_mux_concurrency), m.concurrency, { onChange(m.copy(concurrency = it)) })
                IntF(stringResource(R.string.f_xray_xudp_concurrency), m.xudpConcurrency, { onChange(m.copy(xudpConcurrency = it)) })
            }
        }
    }
}

@Composable
fun XrayStreamSection(s: XrayStreamSetting, onChange: (XrayStreamSetting) -> Unit) {
    FormSection(stringResource(R.string.sec_xray_stream)) {
        ChoiceF(stringResource(R.string.f_network), s.network, XrayStreamSetting.NETWORKS, { onChange(s.copy(network = it)) })
        when (s.network) {
            "xhttp" -> {
                val x = s.xhttp
                val up = { v: XrayXhttp -> onChange(s.copy(xhttp = v)) }
                TextF(stringResource(R.string.f_host), x.host, { up(x.copy(host = it)) })
                TextF(stringResource(R.string.f_path), x.path, { up(x.copy(path = it)) })
                ChoiceF(stringResource(R.string.f_xhttp_mode), x.mode, XrayStreamSetting.XHTTP_MODES, { up(x.copy(mode = it)) })
                ListF(stringResource(R.string.f_headers), x.headers, { up(x.copy(headers = it)) }, separator = NL, minLines = 2)
                TextF(stringResource(R.string.f_xhttp_padding), x.xPaddingBytes, { up(x.copy(xPaddingBytes = it)) })
                TextF(stringResource(R.string.f_xhttp_uplink_method), x.uplinkHttpMethod, { up(x.copy(uplinkHttpMethod = it)) })
                SwitchF(stringResource(R.string.f_xhttp_no_grpc_header), x.noGrpcHeader, { up(x.copy(noGrpcHeader = it)) })
                SwitchF(stringResource(R.string.f_xhttp_no_sse_header), x.noSseHeader, { up(x.copy(noSseHeader = it)) })
                TextF(stringResource(R.string.f_xhttp_max_each_post), x.scMaxEachPostBytes, { up(x.copy(scMaxEachPostBytes = it)) })
                TextF(stringResource(R.string.f_xhttp_min_posts_interval), x.scMinPostsIntervalMs, { up(x.copy(scMinPostsIntervalMs = it)) })
                TextF(stringResource(R.string.f_xmux_max_concurrency), x.maxConcurrency, { up(x.copy(maxConcurrency = it)) })
                TextF(stringResource(R.string.f_xmux_max_connections), x.maxConnections, { up(x.copy(maxConnections = it)) })
                TextF(stringResource(R.string.f_xmux_reuse_times), x.cMaxReuseTimes, { up(x.copy(cMaxReuseTimes = it)) })
                TextF(stringResource(R.string.f_xhttp_download_settings), x.downloadSettings, { up(x.copy(downloadSettings = it)) }, singleLine = false, minLines = 2)
            }
            "ws" -> {
                val w = s.ws
                TextF(stringResource(R.string.f_host), w.host, { onChange(s.copy(ws = w.copy(host = it))) })
                TextF(stringResource(R.string.f_path), w.path, { onChange(s.copy(ws = w.copy(path = it))) })
                IntF(stringResource(R.string.f_max_early_data), w.ed, { onChange(s.copy(ws = w.copy(ed = it))) })
                IntF(stringResource(R.string.f_ws_heartbeat), w.heartbeatPeriod, { onChange(s.copy(ws = w.copy(heartbeatPeriod = it))) })
                ListF(stringResource(R.string.f_headers), w.headers, { onChange(s.copy(ws = w.copy(headers = it))) }, separator = NL, minLines = 2)
            }
            "httpupgrade" -> {
                val h = s.httpUpgrade
                TextF(stringResource(R.string.f_host), h.host, { onChange(s.copy(httpUpgrade = h.copy(host = it))) })
                TextF(stringResource(R.string.f_path), h.path, { onChange(s.copy(httpUpgrade = h.copy(path = it))) })
                IntF(stringResource(R.string.f_max_early_data), h.ed, { onChange(s.copy(httpUpgrade = h.copy(ed = it))) })
                ListF(stringResource(R.string.f_headers), h.headers, { onChange(s.copy(httpUpgrade = h.copy(headers = it))) }, separator = NL, minLines = 2)
            }
            "grpc" -> {
                val g = s.grpc
                TextF(stringResource(R.string.f_service_name), g.serviceName, { onChange(s.copy(grpc = g.copy(serviceName = it))) })
                TextF(stringResource(R.string.f_grpc_authority), g.authority, { onChange(s.copy(grpc = g.copy(authority = it))) })
                SwitchF(stringResource(R.string.f_grpc_multi_mode), g.multiMode, { onChange(s.copy(grpc = g.copy(multiMode = it))) })
            }
        }
        ChoiceF(
            stringResource(R.string.f_security), s.security, listOf("none", "tls", "reality"),
            { onChange(s.copy(security = it)) },
        )
        when (s.security) {
            "tls" -> {
                val t = s.tls
                TextF(stringResource(R.string.f_sni), t.serverName, { onChange(s.copy(tls = t.copy(serverName = it))) })
                ListF(stringResource(R.string.f_alpn), t.alpn, { onChange(s.copy(tls = t.copy(alpn = it))) })
                ChoiceF(stringResource(R.string.f_fingerprint), t.fingerprint, TlsConfig.FINGERPRINTS, { onChange(s.copy(tls = t.copy(fingerprint = it))) })
                TextF(stringResource(R.string.f_xray_pinned_cert), t.pinnedPeerCertSha256, { onChange(s.copy(tls = t.copy(pinnedPeerCertSha256 = it))) })
                TextF(stringResource(R.string.f_xray_verify_by_name), t.verifyPeerCertByName, { onChange(s.copy(tls = t.copy(verifyPeerCertByName = it))) })
            }
            "reality" -> {
                val r = s.reality
                TextF(stringResource(R.string.f_sni), r.serverName, { onChange(s.copy(reality = r.copy(serverName = it))) })
                ChoiceF(stringResource(R.string.f_fingerprint), r.fingerprint, TlsConfig.FINGERPRINTS, { onChange(s.copy(reality = r.copy(fingerprint = it))) })
                TextF(stringResource(R.string.f_reality_public_key), r.password, { onChange(s.copy(reality = r.copy(password = it))) })
                TextF(stringResource(R.string.f_reality_short_id), r.shortId, { onChange(s.copy(reality = r.copy(shortId = it))) })
                TextF(stringResource(R.string.f_reality_spiderx), r.spiderX, { onChange(s.copy(reality = r.copy(spiderX = it))) })
            }
        }
    }
}
