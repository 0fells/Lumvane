package com.lumvane.android.service

import android.content.Context
import com.lumvane.android.data.SecretBox
import org.json.JSONObject
import java.io.File

class VpnPrefs(context: Context) {
    private val appContext = context.applicationContext
    private val sp get() = appContext.getSharedPreferences("vpn_service", Context.MODE_PRIVATE)
    private val file get() = File(appContext.filesDir, "last_vpn_start.json")

    var autoStartOnBoot: Boolean
        get() = sp.getBoolean("auto_start", false)
        set(v) = sp.edit().putBoolean("auto_start", v).apply()

    var allowBypass: Boolean
        get() = sp.getBoolean("allow_bypass", false)
        set(v) = sp.edit().putBoolean("allow_bypass", v).apply()

    var wantRunning: Boolean
        get() = sp.getBoolean("want_running", false)
        set(v) = sp.edit().putBoolean("want_running", v).apply()

    var profileId: Long
        get() = sp.getLong("profile_id", -1)
        set(v) = sp.edit().putLong("profile_id", v).apply()

    fun saveLast(config: String, options: VpnStartOptions) {
        val o = JSONObject()
            .put("config", config)
            .put("xray", options.xrayConfig)
            .put("lazy", options.xrayLazyStart)
            .put("idle", options.xrayIdleSeconds)
            .put("full", options.xrayFullConfigsJson)
            .put("fullIdle", options.xrayFullIdleSeconds)
            .put("dnsAddr", options.xrayOutboundDnsAddress)
            .put("dnsStrat", options.xrayOutboundDnsStrategy)
        file.writeText(SecretBox.default.encrypt(o.toString()))
    }

    fun loadLast(): Pair<String, VpnStartOptions>? = runCatching {
        if (!file.exists()) return null
        val o = JSONObject(SecretBox.default.decrypt(file.readText()))
        o.getString("config") to VpnStartOptions(
            xrayConfig = o.optString("xray"),
            xrayLazyStart = o.optBoolean("lazy"),
            xrayIdleSeconds = o.optInt("idle"),
            xrayFullConfigsJson = o.optString("full"),
            xrayFullIdleSeconds = o.optInt("fullIdle"),
            xrayOutboundDnsAddress = o.optString("dnsAddr"),
            xrayOutboundDnsStrategy = o.optString("dnsStrat"),
        )
    }.getOrNull()
}
