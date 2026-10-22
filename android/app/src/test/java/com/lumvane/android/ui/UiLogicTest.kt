package com.lumvane.android.ui

import com.lumvane.android.config.model.BeanJson
import com.lumvane.android.config.model.OutboundBean
import com.lumvane.android.config.model.typeName
import com.lumvane.android.ui.editor.BEAN_TYPES
import com.lumvane.android.ui.routes.UiRoutePresets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UiLogicTest {
    @Test
    fun beanTypeRegistryCoversAllTypesAndRoundTrips() {
        assertEquals(22, BEAN_TYPES.size)
        assertEquals(22, BEAN_TYPES.map { it.key }.toSet().size)
        for (t in BEAN_TYPES) {
            val bean = t.create()
            assertEquals(t.key, bean.typeName)
            assertEquals(bean, BeanJson.decode(BeanJson.encode(bean)))
        }
    }

    @Test
    fun builtinPresetsAreAvailable() {
        assertTrue(UiRoutePresets.names.isNotEmpty())
        for (n in UiRoutePresets.names) assertTrue(UiRoutePresets.byName(n)!!.rules.isNotEmpty())
    }

    @Test
    fun ruleSetSplitJoinKeepsTags() {
        val tags = listOf("geosite-ru", "geoip-ru", "custom-set")
        val (gs, gi, other) = UiRoutePresets.splitRuleSets(tags)
        assertEquals(listOf("ru"), gs)
        assertEquals(listOf("ru"), gi)
        assertEquals(listOf("custom-set"), other)
        assertEquals(tags, UiRoutePresets.joinRuleSets(gs, gi, other))
    }

    @Test
    fun testConfigDropsTunInbound() {
        val cfg = """{"inbounds":[{"type":"tun"},{"type":"mixed","tag":"in"}],"route":{"auto_detect_interface":true,"final":"proxy"},"outbounds":[]}"""
        val stopped = Json.parseToJsonElement(prepareTestConfig(cfg, keepAutoDetectInterface = false)).jsonObject
        assertEquals(1, stopped["inbounds"]!!.jsonArray.size)
        assertFalse(stopped["route"]!!.jsonObject.containsKey("auto_detect_interface"))
        assertEquals("proxy", stopped["route"]!!.jsonObject["final"].toString().trim('"'))
        val running = Json.parseToJsonElement(prepareTestConfig(cfg, keepAutoDetectInterface = true)).jsonObject
        assertTrue(running["route"]!!.jsonObject.containsKey("auto_detect_interface"))
        assertTrue(running["outbounds"] is JsonArray)
    }

    @Test
    fun listSplitting() {
        assertEquals(listOf("a", "b"), splitList("a, b ,"))
        assertEquals(listOf("x", "y"), splitList("x\ny\n", '\n'))
    }

    @Test
    fun ansiColorsAreStripped() {
        val log = "[38;5;44m1748158963[0m \u001B[36mINFO\u001B[0m [36mINFO[0m router: x[1]"
        assertEquals("1748158963 INFO INFO router: x[1]", com.lumvane.android.ui.log.stripAnsi(log))
        assertEquals("plain", com.lumvane.android.ui.log.stripAnsi("plain"))
    }

    @Test
    fun beanTypeKeysMatchRegistry() {
        val b: OutboundBean = BEAN_TYPES.first { it.key == "hysteria" }.create()
        assertNotNull(b)
        assertEquals("hysteria", b.typeName)
    }

    @Test
    fun coreVersionHidesUnknownPlaceholder() {
        assertEquals("go1.27.0 android/arm64", com.lumvane.android.ui.settings.formatCoreVersion("unknown (go1.27.0 android/arm64)"))
        assertEquals("1.12.0 (go1.27.0 android/arm64)", com.lumvane.android.ui.settings.formatCoreVersion("1.12.0 (go1.27.0 android/arm64)"))
    }
}
