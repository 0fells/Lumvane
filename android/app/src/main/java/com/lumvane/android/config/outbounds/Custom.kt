package com.lumvane.android.config.outbounds

import com.lumvane.android.config.common.parseObjectOrNull
import com.lumvane.android.config.model.CustomBean
import kotlinx.serialization.json.JsonObject

fun CustomBean.parsedConfig(): JsonObject? = parseObjectOrNull(config)?.takeIf { it.isNotEmpty() }

val CustomBean.isXrayOutbound: Boolean get() = subtype == CustomBean.XRAY_OUTBOUND
val CustomBean.isXrayFullConfig: Boolean get() = subtype == CustomBean.XRAY_FULL_CONFIG
val CustomBean.isSingBoxFullConfig: Boolean get() = subtype == CustomBean.FULL_CONFIG
