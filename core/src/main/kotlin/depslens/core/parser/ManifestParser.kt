package depslens.core.parser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * package.json 的声明信息：依赖范围、直接依赖名、workspaces 配置。
 * 与具体包管理器无关（npm/yarn/pnpm 共用同一份 package.json）。
 */
data class Manifest(
    val name: String?,
    val prodRanges: Map<String, String>,   // dependencies
    val devRanges: Map<String, String>,    // devDependencies
    val peerRanges: Map<String, String>,   // peerDependencies
    val workspaces: List<String>,          // 包 globular 列表
) {
    val directNames: Set<String> get() = (prodRanges.keys + devRanges.keys).toSet()
    fun rangeOf(name: String): String? = prodRanges[name] ?: devRanges[name]
}

object ManifestParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(file: File): Manifest = parseText(file.readText())
    fun parse(text: String): Manifest = parseText(text)

    private fun parseText(text: String): Manifest {
        val root = json.parseToJsonElement(text).jsonObject
        return Manifest(
            name = root["name"]?.jsonPrimitive?.content,
            prodRanges = root["dependencies"].asObjectOrEmpty(),
            devRanges = root["devDependencies"].asObjectOrEmpty(),
            peerRanges = root["peerDependencies"].asObjectOrEmpty(),
            workspaces = parseWorkspaces(root["workspaces"]),
        )
    }

    private fun parseWorkspaces(node: JsonElement?): List<String> = when (node) {
        is JsonObject -> node["packages"].asArrayOrEmpty().mapNotNull { it.jsonPrimitive.content }
        is JsonArray -> node.mapNotNull { it.jsonPrimitive.content }
        else -> emptyList()
    }

    private fun JsonElement?.asObjectOrEmpty(): Map<String, String> =
        if (this is JsonObject) mapValues { it.value.jsonPrimitive.content } else emptyMap()

    private fun JsonElement?.asArrayOrEmpty(): JsonArray =
        if (this is JsonArray) this else JsonArray(emptyList())
}
