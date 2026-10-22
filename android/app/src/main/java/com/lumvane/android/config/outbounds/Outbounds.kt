package com.lumvane.android.config.outbounds

import com.lumvane.android.config.common.BuildEnv
import com.lumvane.android.config.model.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

fun OutboundBean.isXray(): Boolean =
    this is XrayVlessBean || (this is CustomBean && subtype == CustomBean.XRAY_OUTBOUND)

fun OutboundBean.isXrayFullConfig(): Boolean = this is CustomBean && subtype == CustomBean.XRAY_FULL_CONFIG

fun OutboundBean.isEndpoint(): Boolean = this is WireGuardBean || this is TailscaleBean

fun buildSingBoxOutbound(bean: OutboundBean, env: BuildEnv): JsonObject? = when (bean) {
    is SocksBean -> bean.toSingBox(env)
    is HttpBean -> bean.toSingBox(env)
    is ShadowsocksBean -> bean.toSingBox(env)
    is VmessBean -> bean.toSingBox(env)
    is VlessBean -> bean.toSingBox(env)
    is TrojanBean -> bean.toSingBox(env)
    is XrayVlessBean -> bean.toSingBox(env)
    is HysteriaBean -> bean.toSingBox(env)
    is TuicBean -> bean.toSingBox(env)
    is JuicityBean -> bean.toSingBox(env)
    is TrustTunnelBean -> bean.toSingBox(env)
    is NaiveBean -> bean.toSingBox(env)
    is AnyTlsBean -> bean.toSingBox(env)
    is ShadowTlsBean -> bean.toSingBox(env)
    is MieruBean -> bean.toSingBox(env)
    is SshBean -> bean.toSingBox(env)
    is WireGuardBean -> bean.toSingBox(env)
    is TailscaleBean -> bean.toSingBox(env)
    is DirectBean -> bean.toSingBox(env)
    is CustomBean -> when (bean.subtype) {
        CustomBean.OUTBOUND -> bean.parsedConfig()
        CustomBean.XRAY_OUTBOUND -> JsonObject(mapOf("type" to JsonPrimitive("socks"), "server" to JsonPrimitive("127.0.0.1")))
        else -> null
    }
    is ChainBean, is AutoSelectorBean -> null
}

fun buildXrayOutbound(bean: OutboundBean, env: BuildEnv): JsonObject? = when {
    bean is XrayVlessBean -> bean.toXray(env)
    bean is CustomBean && bean.subtype == CustomBean.XRAY_OUTBOUND -> bean.parsedConfig()
    else -> null
}

private val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

fun isIpLiteral(host: String): Boolean {
    val h = host.trim().removePrefix("[").removeSuffix("]")
    return IPV4.matches(h) || (h.contains(':') && h.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '.' || it == '%' })
}

fun serverDomains(bean: OutboundBean): List<String> {
    val hosts: List<String> = when (bean) {
        is ServerBean -> listOf(bean.server)
        is WireGuardBean -> listOf(bean.peer.address)
        is TailscaleBean -> listOf(hostOf(bean.controlUrl))
        is CustomBean -> customHosts(bean)
        else -> emptyList()
    }
    return hosts.filter { it.isNotEmpty() && !isIpLiteral(it) }
}

private fun hostOf(url: String): String {
    val noScheme = url.substringAfter("://", url)
    return noScheme.substringBefore('/').substringBefore(':')
}

private fun customHosts(bean: CustomBean): List<String> {
    val obj = bean.parsedConfig() ?: return emptyList()
    return when (bean.subtype) {
        CustomBean.OUTBOUND -> listOfNotNull(obj["server"]?.asStringOrNull())
        CustomBean.XRAY_OUTBOUND -> xrayOutboundHosts(obj)
        CustomBean.XRAY_FULL_CONFIG ->
            (obj["outbounds"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject) }.flatMap { xrayOutboundHosts(it) }
        else -> emptyList()
    }
}

private fun xrayOutboundHosts(outbound: JsonObject): List<String> {
    val settings = outbound["settings"] as? JsonObject ?: return emptyList()
    val out = mutableListOf<String>()
    settings["address"]?.asStringOrNull()?.let(out::add)
    for (key in listOf("vnext", "servers")) {
        (settings[key] as? JsonArray)?.forEach { e ->
            (e as? JsonObject)?.get("address")?.asStringOrNull()?.let(out::add)
        }
    }
    return out
}

private fun JsonArray?.orEmpty(): List<JsonElement> = this?.toList() ?: emptyList()

private fun JsonElement.asStringOrNull(): String? = (this as? JsonPrimitive)?.contentOrNull

fun OutboundBean.describe(): String = "$typeName:${name.ifEmpty { endpoint?.first ?: "" }}"

