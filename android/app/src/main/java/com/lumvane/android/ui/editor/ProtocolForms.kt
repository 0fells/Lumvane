package com.lumvane.android.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lumvane.android.R
import com.lumvane.android.config.model.AnyTlsBean
import com.lumvane.android.config.model.AutoSelectorBean
import com.lumvane.android.config.model.ChainBean
import com.lumvane.android.config.model.CustomBean
import com.lumvane.android.config.model.DirectBean
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
import com.lumvane.android.config.model.TailscaleBean
import com.lumvane.android.config.model.TrojanBean
import com.lumvane.android.config.model.TrustTunnelBean
import com.lumvane.android.config.model.TuicBean
import com.lumvane.android.config.model.VlessBean
import com.lumvane.android.config.model.VmessBean
import com.lumvane.android.config.model.WireGuardBean
import com.lumvane.android.config.model.XrayVlessBean
import com.lumvane.android.data.GroupEntity
import com.lumvane.android.data.Profile
import com.lumvane.android.ui.components.DropdownField
import com.lumvane.lumcore.Lumcore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val NL = '\n'

data class EditorEnv(
    val profiles: List<Profile> = emptyList(),
    val groups: List<GroupEntity> = emptyList(),
    val selfId: Long = 0,
)

@Composable
fun BeanForm(bean: OutboundBean, env: EditorEnv, onChange: (OutboundBean) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (bean) {
            is SocksBean -> SocksForm(bean, onChange)
            is HttpBean -> HttpForm(bean, onChange)
            is ShadowsocksBean -> ShadowsocksForm(bean, onChange)
            is VmessBean -> VmessForm(bean, onChange)
            is VlessBean -> VlessForm(bean, onChange)
            is TrojanBean -> TrojanForm(bean, onChange)
            is XrayVlessBean -> XrayVlessForm(bean, onChange)
            is HysteriaBean -> HysteriaForm(bean, onChange)
            is TuicBean -> TuicForm(bean, onChange)
            is JuicityBean -> JuicityForm(bean, onChange)
            is TrustTunnelBean -> TrustTunnelForm(bean, onChange)
            is NaiveBean -> NaiveForm(bean, onChange)
            is AnyTlsBean -> AnyTlsForm(bean, onChange)
            is ShadowTlsBean -> ShadowTlsForm(bean, onChange)
            is MieruBean -> MieruForm(bean, onChange)
            is SshBean -> SshForm(bean, onChange)
            is WireGuardBean -> WireGuardForm(bean, onChange)
            is TailscaleBean -> TailscaleForm(bean, onChange)
            is DirectBean -> DialSection(bean.dial) { onChange(bean.copy(dial = it)) }
            is ChainBean -> ChainForm(bean, env, onChange)
            is CustomBean -> CustomForm(bean, onChange)
            is AutoSelectorBean -> AutoSelectorForm(bean, env, onChange)
        }
    }
}

@Composable
private fun SocksForm(b: SocksBean, onChange: (OutboundBean) -> Unit) {
    FormSection(stringResource(R.string.sec_server)) {
        ServerFields(b.server, b.serverPort, { onChange(b.copy(server = it)) }, { onChange(b.copy(serverPort = it)) })
        TextF(stringResource(R.string.f_username), b.username, { onChange(b.copy(username = it)) })
        TextF(stringResource(R.string.f_password), b.password, { onChange(b.copy(password = it)) }, secret = true)
        ChoiceF(stringResource(R.string.f_socks_version), b.version.toString(), listOf("4", "5"), { onChange(b.copy(version = it.toInt())) })
        SwitchF(stringResource(R.string.f_uot), b.uot, { onChange(b.copy(uot = it)) })
    }
    DialSection(b.dial) { onChange(b.copy(dial = it)) }
}

@Composable
private fun HttpForm(b: HttpBean, onChange: (OutboundBean) -> Unit) {
    FormSection(stringResource(R.string.sec_server)) {
        ServerFields(b.server, b.serverPort, { onChange(b.copy(server = it)) }, { onChange(b.copy(serverPort = it)) })
        TextF(stringResource(R.string.f_username), b.username, { onChange(b.copy(username = it)) })
        TextF(stringResource(R.string.f_password), b.password, { onChange(b.copy(password = it)) }, secret = true)
        TextF(stringResource(R.string.f_path), b.path, { onChange(b.copy(path = it)) })
        ListF(stringResource(R.string.f_headers), b.headers, { onChange(b.copy(headers = it)) }, separator = NL, minLines = 2)
    }
    TlsSection(b.tls, { onChange(b.copy(tls = it)) })
    DialSection(b.dial) { onChange(b.copy(dial = it)) }
}

@Composable
private fun ShadowsocksForm(b: ShadowsocksBean, onChange: (OutboundBean) -> Unit) {
    FormSection(stringResource(R.string.sec_server)) {
        ServerFields(b.server, b.serverPort, { onChange(b.copy(server = it)) }, { onChange(b.copy(serverPort = it)) })
        ChoiceF(stringResource(R.string.f_method), b.method, ShadowsocksBean.METHODS, { onChange(b.copy(method = it)) })
        TextF(stringResource(R.string.f_password), b.password, { onChange(b.copy(password = it)) }, secret = true)
        TextF(stringResource(R.string.f_ss_plugin), b.plugin, { onChange(b.copy(plugin = it)) })
        TextF(stringResource(R.string.f_ss_plugin_opts), b.pluginOpts, { onChange(b.copy(pluginOpts = it)) })
        SwitchF(stringResource(R.string.f_uot), b.uot, { onChange(b.copy(uot = it)) })
    }
    MultiplexSection(b.multiplex) { onChange(b.copy(multiplex = it)) }
    DialSection(b.dial) { onChange(b.copy(dial = it)) }
}

@Composable
private fun VmessForm(b: VmessBean, onChange: (OutboundBean) -> Unit) {
    FormSection(stringResource(R.string.sec_server)) {
        ServerFields(b.server, b.serverPort, { onChange(b.copy(server = it)) }, { onChange(b.copy(serverPort = it)) })
        TextF(stringResource(R.string.f_uuid), b.uuid, { onChange(b.copy(uuid = it)) })
        ChoiceF(
            stringResource(R.string.f_vmess_security), b.security,
            listOf("auto", "none", "zero", "aes-128-gcm", "chacha20-poly1305"), { onChange(b.copy(security = it)) },
        )
        IntF(stringResource(R.string.f_alter_id), b.alterId, { onChange(b.copy(alterId = it)) })
        SwitchF(stringResource(R.string.f_global_padding), b.globalPadding, { onChange(b.copy(globalPadding = it)) })
        SwitchF(stringResource(R.string.f_authenticated_length), b.authenticatedLength, { onChange(b.copy(authenticatedLength = it)) })
        ChoiceF(stringResource(R.string.f_packet_encoding), b.packetEncoding, listOf("", "packetaddr", "xudp"), { onChange(b.copy(packetEncoding = it)) })
    }
    TlsSection(b.tls, { onChange(b.copy(tls = it)) }, allowReality = true)
    TransportSection(b.transport) { onChange(b.copy(transport = it)) }
    MultiplexSection(b.multiplex) { onChange(b.copy(multiplex = it)) }
    DialSection(b.dial) { onChange(b.copy(dial = it)) }
}

@Composable
private fun VlessForm(b: VlessBean, onChange: (OutboundBean) -> Unit) {
    FormSection(stringResource(R.string.sec_server)) {
        ServerFields(b.server, b.serverPort, { onChange(b.copy(server = it)) }, { onChange(b.copy(serverPort = it)) })
        TextF(stringResource(R.string.f_uuid), b.uuid, { onChange(b.copy(uuid = it)) })
        ChoiceF(stringResource(R.string.f_flow), b.flow, listOf("", "xtls-rprx-vision"), { onChange(b.copy(flow = it)) })
        ChoiceF(stringResource(R.string.f_packet_encoding), b.packetEncoding, listOf("", "packetaddr", "xudp"), { onChange(b.copy(packetEncoding = it)) })
    }
    TlsSection(b.tls, { onChange(b.copy(tls = it)) }, allowReality = true)
    TransportSection(b.transport) { onChange(b.copy(transport = it)) }
    MultiplexSection(b.multiplex) { onChange(b.copy(multiplex = it)) }
    DialSection(b.dial) { onChange(b.copy(dial = it)) }
}

@Composable
private fun TrojanForm(b: TrojanBean, onChange: (OutboundBean) -> Unit) {
    FormSection(stringResource(R.string.sec_server)) {
        ServerFields(b.server, b.serverPort, { onChange(b.copy(server = it)) }, { onChange(b.copy(serverPort = it)) })
        TextF(stringResource(R.string.f_password), b.password, { onChange(b.copy(password = it)) }, secret = true)
    }
    TlsSection(b.tls, { onChange(b.copy(tls = it)) }, allowReality = true)
    TransportSection(b.transport) { onChange(b.copy(transport = it)) }
    MultiplexSection(b.multiplex) { onChange(b.copy(multiplex = it)) }
    DialSection(b.dial) { onChange(b.copy(dial = it)) }
}

@Composable
private fun XrayVlessForm(b: XrayVlessBean, onChange: (OutboundBean) -> Unit) {
    FormSection(stringResource(R.string.sec_server)) {
        ServerFields(b.server, b.serverPort, { onChange(b.copy(server = it)) }, { onChange(b.copy(serverPort = it)) })
        TextF(stringResource(R.string.f_uuid), b.uuid, { onChange(b.copy(uuid = it)) })
        TextF(stringResource(R.string.f_encryption), b.encryption, { onChange(b.copy(encryption = it)) })
        ChoiceF(stringResource(R.string.f_flow), b.flow, listOf("", "xtls-rprx-vision", "xtls-rprx-vision-udp443"), { onChange(b.copy(flow = it)) })
    }
    XrayStreamSection(b.streamSetting) { onChange(b.copy(streamSetting = it)) }
    XrayMultiplexSection(b.multiplex) { onChange(b.copy(multiplex = it)) }
    DialSection(b.dial) { onChange(b.copy(dial = it)) }
}

@Composable
private fun HysteriaForm(b: HysteriaBean, onChange: (OutboundBean) -> Unit) {
    FormSection(stringResource(R.string.sec_server)) {
        ChoiceF(stringResource(R.string.f_hy_version), b.protocolVersion, listOf("1", "2"), { onChange(b.copy(protocolVersion = it)) }, display = { "Hysteria $it" })
        ServerFields(b.server, b.serverPort, { onChange(b.copy(server = it)) }, { onChange(b.copy(serverPort = it)) })
        if (b.isV2) {
            TextF(stringResource(R.string.f_password), b.password, { onChange(b.copy(password = it)) }, secret = true)
        } else {
            ChoiceF(stringResource(R.string.f_hy_auth_type), b.authType, listOf("", "base64", "string"), { onChange(b.copy(authType = it)) })
            TextF(stringResource(R.string.f_hy_auth), b.auth, { onChange(b.copy(auth = it)) }, secret = true)
        }
        IntF(stringResource(R.string.f_up_mbps), b.upMbps, { onChange(b.copy(upMbps = it)) })
        IntF(stringResource(R.string.f_down_mbps), b.downMbps, { onChange(b.copy(downMbps = it)) })
    }
    FormSection(stringResource(R.string.sec_hy_ports)) {
        ListF(stringResource(R.string.f_hy_server_ports), b.serverPorts, { onChange(b.copy(serverPorts = it)) })
        TextF(stringResource(R.string.f_hy_hop_interval), b.hopInterval, { onChange(b.copy(hopInterval = it)) })
        if (b.isV2) TextF(stringResource(R.string.f_hy_hop_interval_max), b.hopIntervalMax, { onChange(b.copy(hopIntervalMax = it)) })
    }
    FormSection(stringResource(R.string.sec_hy_obfs)) {
        if (b.isV2) {
            DropdownField(
                stringResource(R.string.f_hy_obfs_type), b.obfsType,
                listOf("" to "salamander", "gecko" to "gecko"), { onChange(b.copy(obfsType = it)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        TextF(stringResource(R.string.f_hy_obfs_password), b.obfs, { onChange(b.copy(obfs = it)) }, secret = true)
        if (b.isV2 && b.obfsType == "gecko") {
            IntF(stringResource(R.string.f_hy_obfs_min), b.obfsMinPacketSize, { onChange(b.copy(obfsMinPacketSize = it)) })
            IntF(stringResource(R.string.f_hy_obfs_max), b.obfsMaxPacketSize, { onChange(b.copy(obfsMaxPacketSize = it)) })
        }
    }
    FormSection(stringResource(R.string.sec_advanced), collapsible = true) {
        if (b.isV2) {
            ChoiceF(stringResource(R.string.f_hy_bbr), b.bbrProfile, HysteriaBean.BBR_PROFILES, { onChange(b.copy(bbrProfile = it)) })
            SwitchF(stringResource(R.string.f_hy_no_chrome_parrot), b.disableChromeParrot, { onChange(b.copy(disableChromeParrot = it)) })
        } else {
            IntF(stringResource(R.string.f_hy_recv_window_conn), b.recvWindowConn, { onChange(b.copy(recvWindowConn = it)) })
            IntF(stringResource(R.string.f_hy_recv_window), b.recvWindow, { onChange(b.copy(recvWindow = it)) })
            SwitchF(stringResource(R.string.f_hy_disable_mtu), b.disableMtuDiscovery, { onChange(b.copy(disableMtuDiscovery = it)) })
        }
    }
    TlsSection(b.tls, { onChange(b.copy(tls = it)) }, allowToggle = false, allowUtls = false)
    DialSection(b.dial) { onChange(b.copy(dial = it)) }
}

@Composable
private fun TuicForm(b: TuicBean, onChange: (OutboundBean) -> Unit) {
    FormSection(stringResource(R.string.sec_server)) {
        ServerFields(b.server, b.serverPort, { onChange(b.copy(server = it)) }, { onChange(b.copy(serverPort = it)) })
        TextF(stringResource(R.string.f_uuid), b.uuid, { onChange(b.copy(uuid = it)) })
        TextF(stringResource(R.string.f_password), b.password, { onChange(b.copy(password = it)) }, secret = true)
        ChoiceF(stringResource(R.string.f_congestion), b.congestionControl, listOf("", "cubic", "new_reno", "bbr"), { onChange(b.copy(congestionControl = it)) })
        ChoiceF(stringResource(R.string.f_udp_relay_mode), b.udpRelayMode, listOf("", "native", "quic"), { onChange(b.copy(udpRelayMode = it)) })
        SwitchF(stringResource(R.string.f_udp_over_stream), b.udpOverStream, { onChange(b.copy(udpOverStream = it)) })
        SwitchF(stringResource(R.string.f_zero_rtt), b.zeroRttHandshake, { onChange(b.copy(zeroRttHandshake = it)) })
        TextF(stringResource(R.string.f_heartbeat), b.heartbeat, { onChange(b.copy(heartbeat = it)) })
    }
    TlsSection(b.tls, { onChange(b.copy(tls = it)) }, allowToggle = false, allowUtls = false)
    DialSection(b.dial) { onChange(b.copy(dial = it)) }
}

@Composable
private fun JuicityForm(b: JuicityBean, onChange: (OutboundBean) -> Unit) {
    FormSection(stringResource(R.string.sec_server)) {
        ServerFields(b.server, b.serverPort, { onChange(b.copy(server = it)) }, { onChange(b.copy(serverPort = it)) })
        TextF(stringResource(R.string.f_uuid), b.uuid, { onChange(b.copy(uuid = it)) })
        TextF(stringResource(R.string.f_password), b.password, { onChange(b.copy(password = it)) }, secret = true)
    }
    TlsSection(b.tls, { onChange(b.copy(tls = it)) }, allowToggle = false, allowUtls = false)
    DialSection(b.dial) { onChange(b.copy(dial = it)) }
}

@Composable
private fun TrustTunnelForm(b: TrustTunnelBean, onChange: (OutboundBean) -> Unit) {
    FormSection(stringResource(R.string.sec_server)) {
        ServerFields(b.server, b.serverPort, { onChange(b.copy(server = it)) }, { onChange(b.copy(serverPort = it)) })
        TextF(stringResource(R.string.f_username), b.username, { onChange(b.copy(username = it)) })
        TextF(stringResource(R.string.f_password), b.password, { onChange(b.copy(password = it)) }, secret = true)
        SwitchF(stringResource(R.string.f_quic), b.quic, { onChange(b.copy(quic = it)) })
        if (b.quic) {
            ChoiceF(stringResource(R.string.f_congestion), b.congestionControl, listOf("", "bbr", "bbr2", "cubic", "reno"), { onChange(b.copy(congestionControl = it)) })
        }
        SwitchF(stringResource(R.string.f_health_check), b.healthCheck, { onChange(b.copy(healthCheck = it)) })
    }
    TlsSection(b.tls, { onChange(b.copy(tls = it)) })
    DialSection(b.dial) { onChange(b.copy(dial = it)) }
}

@Composable
private fun NaiveForm(b: NaiveBean, onChange: (OutboundBean) -> Unit) {
    FormSection(stringResource(R.string.sec_server)) {
        ServerFields(b.server, b.serverPort, { onChange(b.copy(server = it)) }, { onChange(b.copy(serverPort = it)) })
        TextF(stringResource(R.string.f_username), b.username, { onChange(b.copy(username = it)) })
        TextF(stringResource(R.string.f_password), b.password, { onChange(b.copy(password = it)) }, secret = true)
        SwitchF(stringResource(R.string.f_quic), b.quic, { onChange(b.copy(quic = it)) })
        if (b.quic) {
            ChoiceF(stringResource(R.string.f_congestion), b.congestionControl, listOf("", "bbr", "bbr2", "cubic", "reno"), { onChange(b.copy(congestionControl = it)) })
        }
        SwitchF(stringResource(R.string.f_uot), b.uot, { onChange(b.copy(uot = it)) })
    }
    TlsSection(b.tls, { onChange(b.copy(tls = it)) })
    DialSection(b.dial) { onChange(b.copy(dial = it)) }
}

@Composable
private fun AnyTlsForm(b: AnyTlsBean, onChange: (OutboundBean) -> Unit) {
    FormSection(stringResource(R.string.sec_server)) {
        ServerFields(b.server, b.serverPort, { onChange(b.copy(server = it)) }, { onChange(b.copy(serverPort = it)) })
        TextF(stringResource(R.string.f_password), b.password, { onChange(b.copy(password = it)) }, secret = true)
        TextF(stringResource(R.string.f_anytls_check_interval), b.idleSessionCheckInterval, { onChange(b.copy(idleSessionCheckInterval = it)) })
        TextF(stringResource(R.string.f_anytls_idle_timeout), b.idleSessionTimeout, { onChange(b.copy(idleSessionTimeout = it)) })
        IntF(stringResource(R.string.f_anytls_min_idle), b.minIdleSession, { onChange(b.copy(minIdleSession = it)) })
    }
    TlsSection(b.tls, { onChange(b.copy(tls = it)) }, allowReality = false)
    DialSection(b.dial) { onChange(b.copy(dial = it)) }
}

@Composable
private fun ShadowTlsForm(b: ShadowTlsBean, onChange: (OutboundBean) -> Unit) {
    FormSection(stringResource(R.string.sec_server)) {
        ServerFields(b.server, b.serverPort, { onChange(b.copy(server = it)) }, { onChange(b.copy(serverPort = it)) })
        ChoiceF(stringResource(R.string.f_shadowtls_version), b.version.toString(), listOf("1", "2", "3"), { onChange(b.copy(version = it.toInt())) })
        if (b.version >= 2) TextF(stringResource(R.string.f_password), b.password, { onChange(b.copy(password = it)) }, secret = true)
    }
    TlsSection(b.tls, { onChange(b.copy(tls = it)) })
    DialSection(b.dial) { onChange(b.copy(dial = it)) }
}

@Composable
private fun MieruForm(b: MieruBean, onChange: (OutboundBean) -> Unit) {
    FormSection(stringResource(R.string.sec_server)) {
        ServerFields(b.server, b.serverPort, { onChange(b.copy(server = it)) }, { onChange(b.copy(serverPort = it)) })
        TextF(stringResource(R.string.f_mieru_ports), b.serverPorts, { onChange(b.copy(serverPorts = it)) })
        ChoiceF(stringResource(R.string.f_mieru_transport), b.transport, listOf("TCP", "UDP"), { onChange(b.copy(transport = it)) })
        TextF(stringResource(R.string.f_username), b.username, { onChange(b.copy(username = it)) })
        TextF(stringResource(R.string.f_password), b.password, { onChange(b.copy(password = it)) }, secret = true)
        ChoiceF(
            stringResource(R.string.f_mieru_multiplexing), b.multiplexing,
            listOf("", "MULTIPLEXING_OFF", "MULTIPLEXING_LOW", "MULTIPLEXING_MIDDLE", "MULTIPLEXING_HIGH"),
            { onChange(b.copy(multiplexing = it)) },
        )
        TextF(stringResource(R.string.f_mieru_traffic_pattern), b.trafficPattern, { onChange(b.copy(trafficPattern = it)) })
    }
    DialSection(b.dial) { onChange(b.copy(dial = it)) }
}

@Composable
private fun SshForm(b: SshBean, onChange: (OutboundBean) -> Unit) {
    FormSection(stringResource(R.string.sec_server)) {
        ServerFields(b.server, b.serverPort, { onChange(b.copy(server = it)) }, { onChange(b.copy(serverPort = it)) })
        TextF(stringResource(R.string.f_username), b.user, { onChange(b.copy(user = it)) })
        TextF(stringResource(R.string.f_password), b.password, { onChange(b.copy(password = it)) }, secret = true)
        TextF(stringResource(R.string.f_ssh_private_key), b.privateKey, { onChange(b.copy(privateKey = it)) }, singleLine = false, minLines = 4)
        TextF(stringResource(R.string.f_ssh_private_key_path), b.privateKeyPath, { onChange(b.copy(privateKeyPath = it)) })
        TextF(stringResource(R.string.f_ssh_passphrase), b.privateKeyPassphrase, { onChange(b.copy(privateKeyPassphrase = it)) }, secret = true)
        ListF(stringResource(R.string.f_ssh_host_key), b.hostKey, { onChange(b.copy(hostKey = it)) }, separator = NL, minLines = 2)
        ListF(stringResource(R.string.f_ssh_host_key_algorithms), b.hostKeyAlgorithms, { onChange(b.copy(hostKeyAlgorithms = it)) })
        TextF(stringResource(R.string.f_ssh_client_version), b.clientVersion, { onChange(b.copy(clientVersion = it)) })
    }
    DialSection(b.dial) { onChange(b.copy(dial = it)) }
}

@Composable
private fun WireGuardForm(b: WireGuardBean, onChange: (OutboundBean) -> Unit) {
    val scope = rememberCoroutineScope()
    var publicKey by remember { mutableStateOf("") }
    val peer = b.peer
    FormSection(stringResource(R.string.sec_wg_interface)) {
        TextF(stringResource(R.string.f_wg_private_key), b.privateKey, { onChange(b.copy(privateKey = it)) }, secret = true)
        Button(onClick = {
            scope.launch {
                val pair = withContext(Dispatchers.IO) { runCatching { Lumcore.genWgKeyPair() }.getOrNull() }
                val parts = pair?.split(NL)
                if (parts != null && parts.size == 2) {
                    publicKey = parts[1]
                    onChange(b.copy(privateKey = parts[0]))
                }
            }
        }) { Text(stringResource(R.string.action_wg_generate)) }
        if (publicKey.isNotEmpty()) {
            Text(stringResource(R.string.wg_public_key_is, publicKey), style = MaterialTheme.typography.bodySmall)
        }
        ListF(stringResource(R.string.f_wg_address), b.address, { onChange(b.copy(address = it)) })
        IntF(stringResource(R.string.f_wg_mtu), b.mtu, { onChange(b.copy(mtu = it)) })
        SwitchF(stringResource(R.string.f_wg_system), b.system, { onChange(b.copy(system = it)) })
        IntF(stringResource(R.string.f_wg_workers), b.workerCount, { onChange(b.copy(workerCount = it)) })
        TextF(stringResource(R.string.f_wg_udp_timeout), b.udpTimeout, { onChange(b.copy(udpTimeout = it)) })
    }
    FormSection(stringResource(R.string.sec_wg_peer)) {
        ServerFields(peer.address, peer.port, { onChange(b.copy(peer = peer.copy(address = it))) }, { onChange(b.copy(peer = peer.copy(port = it))) })
        TextF(stringResource(R.string.f_wg_peer_public_key), peer.publicKey, { onChange(b.copy(peer = peer.copy(publicKey = it))) })
        TextF(stringResource(R.string.f_wg_psk), peer.preSharedKey, { onChange(b.copy(peer = peer.copy(preSharedKey = it))) }, secret = true)
        ListF(
            stringResource(R.string.f_wg_reserved), peer.reserved.map { it.toString() },
            { l -> onChange(b.copy(peer = peer.copy(reserved = l.mapNotNull { it.toIntOrNull() }))) },
        )
        TextF(stringResource(R.string.f_wg_keepalive), peer.persistentKeepalive, { onChange(b.copy(peer = peer.copy(persistentKeepalive = it))) })
    }
    FormSection(stringResource(R.string.sec_amnezia), collapsible = true, initiallyExpanded = b.enableAmnezia) {
        SwitchF(stringResource(R.string.f_awg_enable), b.enableAmnezia, { onChange(b.copy(enableAmnezia = it)) })
        if (b.enableAmnezia) {
            IntF(stringResource(R.string.f_awg_jc), b.jc, { onChange(b.copy(jc = it)) })
            IntF(stringResource(R.string.f_awg_jmin), b.jmin, { onChange(b.copy(jmin = it)) })
            IntF(stringResource(R.string.f_awg_jmax), b.jmax, { onChange(b.copy(jmax = it)) })
            IntF(stringResource(R.string.f_awg_s1), b.s1, { onChange(b.copy(s1 = it)) })
            IntF(stringResource(R.string.f_awg_s2), b.s2, { onChange(b.copy(s2 = it)) })
            IntF(stringResource(R.string.f_awg_s3), b.s3, { onChange(b.copy(s3 = it)) })
            IntF(stringResource(R.string.f_awg_s4), b.s4, { onChange(b.copy(s4 = it)) })
            TextF(stringResource(R.string.f_awg_h1), b.h1, { onChange(b.copy(h1 = it)) })
            TextF(stringResource(R.string.f_awg_h2), b.h2, { onChange(b.copy(h2 = it)) })
            TextF(stringResource(R.string.f_awg_h3), b.h3, { onChange(b.copy(h3 = it)) })
            TextF(stringResource(R.string.f_awg_h4), b.h4, { onChange(b.copy(h4 = it)) })
            TextF(stringResource(R.string.f_awg_i1), b.i1, { onChange(b.copy(i1 = it)) })
            TextF(stringResource(R.string.f_awg_i2), b.i2, { onChange(b.copy(i2 = it)) })
            TextF(stringResource(R.string.f_awg_i3), b.i3, { onChange(b.copy(i3 = it)) })
            TextF(stringResource(R.string.f_awg_i4), b.i4, { onChange(b.copy(i4 = it)) })
            TextF(stringResource(R.string.f_awg_i5), b.i5, { onChange(b.copy(i5 = it)) })
        }
    }
    if (b.enableAmnezia) {
        FormSection(stringResource(R.string.sec_awg3), collapsible = true) {
            TextF(stringResource(R.string.f_awg_header_protection_key), b.headerProtectionKey, { onChange(b.copy(headerProtectionKey = it)) })
            TextF(stringResource(R.string.f_awg_content_padding), b.contentPaddingAddition, { onChange(b.copy(contentPaddingAddition = it)) })
            TextF(stringResource(R.string.f_awg_rekey_after_time), b.rekeyAfterTime, { onChange(b.copy(rekeyAfterTime = it)) })
            TextF(stringResource(R.string.f_awg_rekey_timeout), b.rekeyTimeout, { onChange(b.copy(rekeyTimeout = it)) })
            TextF(stringResource(R.string.f_awg_reject_after_time), b.rejectAfterTime, { onChange(b.copy(rejectAfterTime = it)) })
            TextF(stringResource(R.string.f_awg_keepalive_timeout), b.keepaliveTimeout, { onChange(b.copy(keepaliveTimeout = it)) })
            TextF(stringResource(R.string.f_awg_max_handshake_attempts), b.maxHandshakeAttempts, { onChange(b.copy(maxHandshakeAttempts = it)) })
        }
    }
    DialSection(b.dial) { onChange(b.copy(dial = it)) }
}

@Composable
private fun TailscaleForm(b: TailscaleBean, onChange: (OutboundBean) -> Unit) {
    FormSection(stringResource(R.string.type_tailscale)) {
        TextF(stringResource(R.string.f_ts_state_dir), b.stateDirectory, { onChange(b.copy(stateDirectory = it)) })
        TextF(stringResource(R.string.f_ts_auth_key), b.authKey, { onChange(b.copy(authKey = it)) }, secret = true)
        TextF(stringResource(R.string.f_ts_control_url), b.controlUrl, { onChange(b.copy(controlUrl = it)) })
        TextF(stringResource(R.string.f_ts_hostname), b.hostname, { onChange(b.copy(hostname = it)) })
        SwitchF(stringResource(R.string.f_ts_ephemeral), b.ephemeral, { onChange(b.copy(ephemeral = it)) })
        SwitchF(stringResource(R.string.f_ts_accept_routes), b.acceptRoutes, { onChange(b.copy(acceptRoutes = it)) })
        TextF(stringResource(R.string.f_ts_exit_node), b.exitNode, { onChange(b.copy(exitNode = it)) })
        SwitchF(stringResource(R.string.f_ts_exit_lan), b.exitNodeAllowLanAccess, { onChange(b.copy(exitNodeAllowLanAccess = it)) })
        ListF(stringResource(R.string.f_ts_advertise_routes), b.advertiseRoutes, { onChange(b.copy(advertiseRoutes = it)) })
        SwitchF(stringResource(R.string.f_ts_advertise_exit), b.advertiseExitNode, { onChange(b.copy(advertiseExitNode = it)) })
        SwitchF(stringResource(R.string.f_ts_global_dns), b.globalDns, { onChange(b.copy(globalDns = it)) })
    }
    DialSection(b.dial) { onChange(b.copy(dial = it)) }
}

@Composable
private fun ChainForm(b: ChainBean, env: EditorEnv, onChange: (OutboundBean) -> Unit) {
    val byId = env.profiles.associateBy { it.id.toInt() }
    FormSection(stringResource(R.string.sec_chain)) {
        Text(stringResource(R.string.chain_hint), style = MaterialTheme.typography.bodySmall)
        b.list.forEachIndexed { index, id ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}. ${byId[id]?.name ?: stringResource(R.string.chain_missing, id)}", Modifier.weight(1f))
                IconButton(enabled = index > 0, onClick = {
                    val l = b.list.toMutableList(); l.add(index - 1, l.removeAt(index)); onChange(b.copy(list = l))
                }) { Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.action_move_up)) }
                IconButton(enabled = index < b.list.lastIndex, onClick = {
                    val l = b.list.toMutableList(); l.add(index + 1, l.removeAt(index)); onChange(b.copy(list = l))
                }) { Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.action_move_down)) }
                IconButton(onClick = { onChange(b.copy(list = b.list.filterIndexed { i, _ -> i != index })) }) {
                    Icon(Icons.Filled.Close, stringResource(R.string.action_delete))
                }
            }
        }
        val candidates = env.profiles.filter { it.id != env.selfId && it.bean !is ChainBean }
        DropdownField(
            label = stringResource(R.string.chain_add),
            value = "",
            options = candidates.map { it.id.toString() to it.name },
            onSelect = { onChange(b.copy(list = b.list + it.toInt())) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CustomForm(b: CustomBean, onChange: (OutboundBean) -> Unit) {
    FormSection(stringResource(R.string.type_custom)) {
        ChoiceF(
            stringResource(R.string.f_custom_subtype), b.subtype,
            listOf(CustomBean.OUTBOUND, CustomBean.FULL_CONFIG, CustomBean.XRAY_OUTBOUND, CustomBean.XRAY_FULL_CONFIG),
            { onChange(b.copy(subtype = it)) },
        )
        TextF(stringResource(R.string.f_custom_json), b.config, { onChange(b.copy(config = it)) }, singleLine = false, minLines = 10)
    }
}

@Composable
private fun AutoSelectorForm(b: AutoSelectorBean, env: EditorEnv, onChange: (OutboundBean) -> Unit) {
    FormSection(stringResource(R.string.sec_autoselector)) {
        DropdownField(
            stringResource(R.string.f_as_group), b.gid.toString(),
            env.groups.map { it.id.toString() to it.name }, { onChange(b.copy(gid = it.toInt())) },
            modifier = Modifier.fillMaxWidth(),
        )
        TextF(stringResource(R.string.f_as_name_filter), b.nameFilter, { onChange(b.copy(nameFilter = it)) })
        TextF(stringResource(R.string.f_as_country_filter), b.countryFilter, { onChange(b.copy(countryFilter = it)) })
        SwitchF(stringResource(R.string.f_as_exclude_unavailable), b.excludeUnavailable, { onChange(b.copy(excludeUnavailable = it)) })
        TextF(stringResource(R.string.f_as_test_url), b.testUrl, { onChange(b.copy(testUrl = it)) })
        TextF(stringResource(R.string.f_as_connectivity_url), b.connectivityUrl, { onChange(b.copy(connectivityUrl = it)) })
        IntF(stringResource(R.string.f_as_interval), b.intervalSec, { onChange(b.copy(intervalSec = it)) })
        IntF(stringResource(R.string.f_as_active_size), b.activeSize, { onChange(b.copy(activeSize = it)) })
        IntF(stringResource(R.string.f_as_tolerance), b.toleranceMs, { onChange(b.copy(toleranceMs = it)) })
        IntF(stringResource(R.string.f_as_max_rtt), b.maxRttMs, { onChange(b.copy(maxRttMs = it)) })
        SwitchF(stringResource(R.string.f_as_interrupt), b.interruptOnSwitch, { onChange(b.copy(interruptOnSwitch = it)) })
        SwitchF(stringResource(R.string.f_as_balance), b.balance, { onChange(b.copy(balance = it)) })
        if (b.balance) {
            ChoiceF(stringResource(R.string.f_as_balance_mode), b.balanceMode, listOf("rotate", "connection"), { onChange(b.copy(balanceMode = it)) })
            IntF(stringResource(R.string.f_as_balance_interval), b.balanceIntervalSec, { onChange(b.copy(balanceIntervalSec = it)) })
        }
    }
    FormSection(stringResource(R.string.sec_advanced), collapsible = true) {
        IntF(stringResource(R.string.f_as_pool_cap), b.poolCap, { onChange(b.copy(poolCap = it)) })
        IntF(stringResource(R.string.f_as_build_limit), b.buildLimit, { onChange(b.copy(buildLimit = it)) })
        IntF(stringResource(R.string.f_as_validity), b.resultValidityMins, { onChange(b.copy(resultValidityMins = it)) })
        IntF(stringResource(R.string.f_as_bench_interval), b.benchIntervalSec, { onChange(b.copy(benchIntervalSec = it)) })
        IntF(stringResource(R.string.f_as_watch_interval), b.watchIntervalSec, { onChange(b.copy(watchIntervalSec = it)) })
        IntF(stringResource(R.string.f_as_sampling), b.sampling, { onChange(b.copy(sampling = it)) })
        IntF(stringResource(R.string.f_as_expected), b.expected, { onChange(b.copy(expected = it)) })
        IntF(stringResource(R.string.f_as_dial_retries), b.dialRetries, { onChange(b.copy(dialRetries = it)) })
    }
}
