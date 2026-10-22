package com.lumvane.android.ui.editor

import androidx.annotation.StringRes
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
import com.lumvane.android.config.model.TlsConfig
import com.lumvane.android.config.model.TrojanBean
import com.lumvane.android.config.model.TrustTunnelBean
import com.lumvane.android.config.model.TuicBean
import com.lumvane.android.config.model.VlessBean
import com.lumvane.android.config.model.VmessBean
import com.lumvane.android.config.model.WireGuardBean
import com.lumvane.android.config.model.XrayVlessBean
import com.lumvane.android.config.model.typeName

data class BeanType(val key: String, @StringRes val label: Int, val create: () -> OutboundBean)

private fun tlsOn() = TlsConfig(enabled = true)

val BEAN_TYPES: List<BeanType> = listOf(
    BeanType("vless", R.string.type_vless) { VlessBean(tls = tlsOn()) },
    BeanType("vmess", R.string.type_vmess) { VmessBean() },
    BeanType("trojan", R.string.type_trojan) { TrojanBean(tls = tlsOn()) },
    BeanType("shadowsocks", R.string.type_shadowsocks) { ShadowsocksBean(method = "2022-blake3-aes-128-gcm") },
    BeanType("xrayvless", R.string.type_xrayvless) { XrayVlessBean() },
    BeanType("hysteria", R.string.type_hysteria) { HysteriaBean(protocolVersion = "2", tls = tlsOn()) },
    BeanType("tuic", R.string.type_tuic) { TuicBean(tls = tlsOn()) },
    BeanType("juicity", R.string.type_juicity) { JuicityBean(tls = tlsOn()) },
    BeanType("trusttunnel", R.string.type_trusttunnel) { TrustTunnelBean(tls = tlsOn()) },
    BeanType("naive", R.string.type_naive) { NaiveBean(tls = tlsOn()) },
    BeanType("anytls", R.string.type_anytls) { AnyTlsBean(tls = tlsOn()) },
    BeanType("shadowtls", R.string.type_shadowtls) { ShadowTlsBean(version = 3, tls = tlsOn()) },
    BeanType("mieru", R.string.type_mieru) { MieruBean() },
    BeanType("ssh", R.string.type_ssh) { SshBean() },
    BeanType("wireguard", R.string.type_wireguard) { WireGuardBean() },
    BeanType("tailscale", R.string.type_tailscale) { TailscaleBean() },
    BeanType("socks", R.string.type_socks) { SocksBean() },
    BeanType("http", R.string.type_http) { HttpBean() },
    BeanType("direct", R.string.type_direct) { DirectBean() },
    BeanType("chain", R.string.type_chain) { ChainBean() },
    BeanType("custom", R.string.type_custom) { CustomBean() },
    BeanType("autoselector", R.string.type_autoselector) { AutoSelectorBean() },
)

fun beanTypeOf(bean: OutboundBean): BeanType? = BEAN_TYPES.firstOrNull { it.key == bean.typeName }

@StringRes
fun beanTypeLabel(typeKey: String): Int = BEAN_TYPES.firstOrNull { it.key == typeKey }?.label ?: R.string.type_unknown

@StringRes
fun beanTypeLabel(bean: OutboundBean): Int = when {
    bean is HysteriaBean && bean.protocolVersion == "2" -> R.string.type_hysteria_v2
    bean is HysteriaBean -> R.string.type_hysteria_v1
    else -> beanTypeLabel(bean.typeName)
}
