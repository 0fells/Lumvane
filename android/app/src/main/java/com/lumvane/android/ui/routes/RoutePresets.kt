package com.lumvane.android.ui.routes

import com.lumvane.android.config.RouteInput
import com.lumvane.android.config.RoutePresets as BuiltinPresets

object UiRoutePresets {
    val names: List<String> get() = BuiltinPresets.names

    fun byName(name: String): RouteInput? = BuiltinPresets.byName(name)

    fun splitRuleSets(tags: List<String>): Triple<List<String>, List<String>, List<String>> {
        val geosite = tags.filter { it.startsWith("geosite-") }.map { it.removePrefix("geosite-") }
        val geoip = tags.filter { it.startsWith("geoip-") }.map { it.removePrefix("geoip-") }
        val other = tags.filter { !it.startsWith("geosite-") && !it.startsWith("geoip-") }
        return Triple(geosite, geoip, other)
    }

    fun joinRuleSets(geosite: List<String>, geoip: List<String>, other: List<String>): List<String> =
        geosite.map { "geosite-$it" } + geoip.map { "geoip-$it" } + other
}
