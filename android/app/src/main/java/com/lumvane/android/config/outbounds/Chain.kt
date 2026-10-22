package com.lumvane.android.config.outbounds

import com.lumvane.android.config.model.ChainBean
import com.lumvane.android.config.model.CustomBean
import com.lumvane.android.config.model.OutboundBean

fun chainScanError(hops: List<OutboundBean>): String? {
    var xrayFull = 0
    var xrayFullIdx = -1
    var xrayHops = 0
    var transitions = 0
    var inXray = false
    hops.forEachIndexed { idx, hop ->
        if (hop is ChainBean) return "Chain in Chain is not allowed"
        if (hop is com.lumvane.android.config.model.AutoSelectorBean) {
            return "An auto selector cannot be used as a hop; it is not a fixed server"
        }
        if (hop is CustomBean && hop.subtype == CustomBean.FULL_CONFIG) {
            return "Custom full config profiles cannot be used in a chain; only custom outbound profiles are chainable"
        }
        val xray = hop.isXray()
        if (xray != inXray) transitions++
        inXray = xray
        if (hop.isXrayFullConfig()) {
            xrayFull++
            xrayFullIdx = idx
        }
        if (xray) xrayHops++
    }
    if (xrayFull > 1) return "Only one custom Xray full config profile is allowed in a chain"
    if (xrayFull > 0 && xrayHops > 0) {
        return "Custom Xray full config cannot be combined with other Xray hops in a chain (only one Xray instance is supported at a time)"
    }
    if (xrayFull == 1 && hops.size > 1 && xrayFullIdx != hops.size - 1) {
        return "Custom Xray full config can only be the final hop in a chain (top of the chain editor)"
    }
    if (transitions > 2) {
        return "Too many core transitions, the valid sequence is: (optional sing-box chain)->(optional xray chain)->(optional sing-box chain)"
    }
    return null
}
