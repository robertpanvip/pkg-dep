package depslens.core.impact

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 解析 npm `install --dry-run --json` 的报告（added / changed / removed）。
 * changed 项尽量取 previousVersion，缺失时回退 from。
 */
object NpmImpactParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(report: String, pkg: String, target: String): ImpactResult {
        val root = runCatching { json.parseToJsonElement(report).jsonObject }.getOrNull() ?: return ImpactResult.empty(pkg, target)

        val changed = mutableListOf<ChangedDep>()
        root["changed"]?.jsonArray?.forEach {
            val o = it.jsonObject
            val name = o["name"]?.jsonPrimitive?.content ?: return@forEach
            val to = o["version"]?.jsonPrimitive?.content ?: return@forEach
            val from = o["previousVersion"]?.jsonPrimitive?.content ?: o["from"]?.jsonPrimitive?.content ?: "?"
            changed.add(ChangedDep(name, from, to))
        }
        val added = mapIds(root["added"]?.jsonArray)
        val removed = mapIds(root["removed"]?.jsonArray)
        return ImpactResult(pkg, target, changed, added, removed)
    }

    private fun mapIds(arr: kotlinx.serialization.json.JsonArray?): List<String> =
        arr?.mapNotNull { o ->
            val obj = (o as? JsonObject) ?: return@mapNotNull null
            val n = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val v = obj["version"]?.jsonPrimitive?.content ?: return@mapNotNull null
            "$n@$v"
        } ?: emptyList()
}
