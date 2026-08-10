package depslens.core.parser

import depslens.core.model.DependencyGraph
import depslens.core.model.DepKind
import depslens.core.model.PackageRef
import depslens.core.model.VersionRange

/**
 * 逐行解析 pnpm-lock.yaml（无第三方依赖，避免 kaml API 漂移）。
 * 处理 packages 段：key 形如 "/name/version" 或 "/name/version_peer@x"，
 * 依赖边连到顶层 hoist 版本（/name/version）。
 */
object PnpmLockParser {
    fun parse(text: String, manifest: Manifest?): DependencyGraph {
        val graph = DependencyGraph(rootProject = manifest?.name ?: "project")
        val lines = text.lines()
        val nodeByKey = mutableMapOf<String, PackageRef>()
        val byNameTop = mutableMapOf<String, String>()
        val depsByKey = mutableMapOf<String, Map<String, String>>()

        var i = 0
        while (i < lines.size && !lines[i].trim().startsWith("packages:")) i++
        i++

        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank()) { i++; continue }
            val indent = line.indexOfFirst { !it.isWhitespace() }
            if (indent != 2) break
            val trimmed = line.trim()
            if (!trimmed.endsWith(":")) { i++; continue }
            val key = trimmed.removeSuffix(":").trim()
            if (!key.startsWith("/")) { i++; continue }
            val (name, version) = splitPkgKey(key)
            if (name == null) { i++; continue }

            val ref = PackageRef(name, version, DepKind.PROD)
            graph.addNode(ref)
            nodeByKey[key] = ref
            if (key == "/$name/$version") byNameTop[name] = version

            i++
            val deps = mutableMapOf<String, String>()
            while (i < lines.size) {
                val l2 = lines[i]
                val ind2 = l2.indexOfFirst { !it.isWhitespace() }
                if (ind2 <= indent) break
                val t2 = l2.trim()
                if (t2.endsWith(":")) { i++; continue }
                val idx = t2.indexOf(':')
                if (idx > 0) {
                    val dn = t2.substring(0, idx).trim().removeSurrounding("\"")
                    val rng = t2.substring(idx + 1).trim().removeSurrounding("\"")
                    deps[dn] = rng
                }
                i++
            }
            depsByKey[key] = deps
        }

        for ((key, deps) in depsByKey) {
            val from = nodeByKey[key] ?: continue
            deps.forEach { (dn, _) ->
                val tv = byNameTop[dn] ?: return@forEach
                graph.node("$dn@$tv")?.let { graph.addEdge(from, it) }
            }
        }

        manifest?.let { m ->
            m.directNames.forEach { graph.markDirect(it) }
            (m.prodRanges + m.devRanges).forEach { (n, r) -> graph.declareRange(n, VersionRange(r, n)) }
        }
        return graph
    }

    private fun splitPkgKey(key: String): Pair<String?, String> {
        if (!key.startsWith("/")) return null to ""
        val body = key.removePrefix("/")
        val firstSlash = body.indexOf('/')
        if (firstSlash < 0) return null to ""
        val name = body.substring(0, firstSlash)
        val rest = body.substring(firstSlash + 1)
        val version = rest.split("_").first()
        return name to version
    }
}
