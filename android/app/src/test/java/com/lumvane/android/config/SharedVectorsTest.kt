package com.lumvane.android.config

import com.lumvane.android.data.AppSettings
import com.lumvane.android.importer.LinkParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class SharedVectorsTest {
    private fun vectorsFile(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val f = File(dir, "testdata/share-links.json")
            if (f.isFile) return f
            dir = dir.parentFile
        }
        fail("testdata/share-links.json not found above ${File("").absolutePath}")
    }

    private fun build(link: String): GeneratedConfig {
        val bean = assertNotNull(LinkParser.parse(link), "link not parsed: $link")
        var port = 41000
        val builder = ConfigBuilder(
            { null },
            ConfigOptions(allocatePort = { ++port }, randomSecret = { "SECRET" }, systemDnsServers = listOf("192.0.2.53")),
        )
        return builder.build(100, bean, AppSettings(), RouteInput(), -1, -1)
    }

    private fun proxy(c: GeneratedConfig): JsonObject =
        Json.parseToJsonElement(c.singBoxJson).jsonObject["outbounds"]!!.jsonArray.map { it.jsonObject }
            .first { it["tag"]?.jsonPrimitive?.content == "proxy" }

    private fun same(expected: JsonElement, actual: JsonElement?): Boolean = when (expected) {
        is JsonObject -> actual is JsonObject && expected.all { (k, v) -> same(v, actual[k]) }
        is JsonArray -> actual is JsonArray && actual.size == expected.size && expected.indices.all { same(expected[it], actual[it]) }
        is JsonPrimitive -> actual is JsonPrimitive && (
            actual.content == expected.content && actual.isString == expected.isString ||
                !expected.isString && !actual.isString && actual.content.toDoubleOrNull() == expected.content.toDoubleOrNull()
            )
    }

    private fun mismatches(expected: JsonObject, actual: JsonObject, path: String = ""): List<String> =
        expected.flatMap { (k, v) ->
            val p = if (path.isEmpty()) k else "$path.$k"
            val a = actual[k]
            when {
                v is JsonObject && a is JsonObject -> mismatches(v, a, p)
                same(v, a) -> emptyList()
                else -> listOf("$p: want $v, got $a")
            }
        }

    private fun lookup(root: JsonObject, dotted: String): JsonElement? =
        dotted.split('.').fold(root as JsonElement?) { e, k -> (e as? JsonObject)?.get(k) }

    @Test
    fun shareLinksBuildTheExpectedOutbounds() {
        val vectors = Json.parseToJsonElement(vectorsFile().readText()).jsonArray
        assertTrue(vectors.isNotEmpty())
        val failures = vectors.map { it.jsonObject }.flatMap { v ->
            val name = v["name"]!!.jsonPrimitive.content
            val config = runCatching { build(v["link"]!!.jsonPrimitive.content) }.getOrElse { return@flatMap listOf("$name: $it") }
            val wantXray = v["engine"]?.jsonPrimitive?.content == "xray"
            if (wantXray != (config.xray != null)) return@flatMap listOf("$name: Xray used = ${config.xray != null}, want $wantXray")
            if (wantXray) return@flatMap emptyList()
            val out = proxy(config)
            mismatches(v["expect"]!!.jsonObject, out).map { "$name: $it" } +
                (v["absent"]?.jsonArray ?: JsonArray(emptyList())).map { it.jsonPrimitive.content }
                    .filter { lookup(out, it) != null }.map { "$name: $it must be absent" }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }
}
