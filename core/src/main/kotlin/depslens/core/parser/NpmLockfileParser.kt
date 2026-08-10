package depslens.core.parser

import depslens.core.model.DependencyGraph
import depslens.core.model.DepKind
import depslens.core.model.PackageRef
import depslens.core.model.VersionRange
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 解析 npm 的 package-lock.json。
 * - v2/v3：用 "packages" 映射（key 为 node_modules 路径，含顶层 hoist 与嵌套）。
 * - v1：用 "dependencies" 递归树。
 * 边连接采用“顶层 hoist 版本”近似（可视化足够；精确嵌套解析见注释）。
 */
object NpmLockfileParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(lockfileText: String, manifest: Manifest?): DependencyGraph {
        val root = json.parseToJsonElement(lockfileText).jsonObject
        val graph = DependencyGraph(rootProject = manifest?.name ?: "project")
        val version = (root["lockfileVersion"]?.jsonPrimitive?.content ?: "1").first().digitToIntOrNull() ?: 1
        if (version >= 2) parseV2(root, graph) else parseV1(root, graph)
        applyManifest(graph, manifest)
        return graph
    }

    private fun parseV2(root: JsonObject, graph: DependencyGraph) {
        val packages = root["packages"]?.jsonObject ?: return
        val byNameTop = mutableMapOf<String, String>()
        val entries = mutableListOf<Entry>()

        for ((pathKey, value) in packages) {
            if (pathKey.isEmpty()) continue
            val obj = value.jsonObject
            val version = obj["version"]?.jsonPrimitive?.content ?: continue
            val name = nameFromPath(pathKey) ?: continue
            val kind = when {
                obj["peer"]?.jsonPrimitive?.content == "true" -> DepKind.PEER
                obj["dev"]?.jsonPrimitive?.content == "true" -> DepKind.DEV
                else -> DepKind.PROD
            }
            graph.addNode(PackageRef(name, version, kind))
            if (pathKey == "node_modules/$name") byNameTop[name] = version
            entries.add(Entry(name, version, obj["dependencies"]?.jsonObject))
        }

        for (e in entries) {
            val from = PackageRef(e.name, e.version)
            e.deps?.forEach { (depName, _) ->
                val tv = byNameTop[depName] ?: return@forEach
                graph.node("$depName@$tv")?.let { graph.addEdge(from, it) }
            }
        }
    }

    private fun parseV1(root: JsonObject, graph: DependencyGraph) {
        val deps = root["dependencies"]?.jsonObject ?: return
        fun walk(node: JsonObject, parent: PackageRef?) {
            for ((name, value) in node) {
                val obj = value.jsonObject
                val version = obj["version"]?.jsonPrimitive?.content ?: continue
                val kind = if (obj["dev"]?.jsonPrimitive?.content == "true") DepKind.DEV else DepKind.PROD
                val ref = PackageRef(name, version, kind)
                graph.addNode(ref)
                if (parent != null) graph.addEdge(parent, ref)
                obj["dependencies"]?.jsonObject?.let { walk(it, ref) }
            }
        }
        walk(deps, null)
    }

    private fun applyManifest(graph: DependencyGraph, manifest: Manifest?) {
        manifest ?: return
        manifest.directNames.forEach { graph.markDirect(it) }
        (manifest.prodRanges + manifest.devRanges).forEach { (n, r) -> graph.declareRange(n, VersionRange(r, n)) }
    }

    private fun nameFromPath(pathKey: String): String? {
        val idx = pathKey.lastIndexOf("node_modules/")
        if (idx < 0) return null
        return pathKey.substring(idx + "node_modules/".length)
    }

    private data class Entry(val name: String, val version: String, val deps: JsonObject?)
}
