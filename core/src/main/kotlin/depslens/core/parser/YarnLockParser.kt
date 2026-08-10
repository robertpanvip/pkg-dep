package depslens.core.parser

import depslens.core.model.DependencyGraph
import depslens.core.model.DepKind
import depslens.core.model.PackageRef
import depslens.core.model.VersionRange

/**
 * 解析 yarn 的经典 yarn.lock（v1）。
 * 每个 "name@range" 块解析为 (name, version)，块内 dependencies 给出依赖边。
 * 注：yarn berry（v2+，lockfile 含 __metadata / resolution 字段）格式不同，
 * 建议改用 `yarn list --json` 或后续单独解析；此处覆盖最常见的 v1。
 */
object YarnLockParser {
    fun parse(text: String, manifest: Manifest?): DependencyGraph {
        val graph = DependencyGraph(rootProject = manifest?.name ?: "project")
        val blocks = parseBlocks(text)
        val nodeFor = mutableMapOf<Pair<String, String>, PackageRef>()
        val resolvers = mutableMapOf<String, MutableList<Pair<String, String>>>()

        for (b in blocks) {
            val ref = PackageRef(b.name, b.version, DepKind.PROD)
            graph.addNode(ref)
            nodeFor[b.name to b.version] = ref
            for (key in b.keys) resolvers.getOrPut(key.name) { mutableListOf() }.add(key.range to b.version)
        }
        for (b in blocks) {
            val from = nodeFor[b.name to b.version] ?: continue
            b.deps.forEach { (depName, _) ->
                val candidates = resolvers[depName] ?: return@forEach
                val targetVer = candidates.firstOrNull { VersionRange(it.first, depName).satisfies(it.second) }?.second
                    ?: candidates.firstOrNull()?.second
                if (targetVer != null) nodeFor[depName to targetVer]?.let { graph.addEdge(from, it) }
            }
        }
        applyManifest(graph, manifest)
        return graph
    }

    private fun applyManifest(graph: DependencyGraph, manifest: Manifest?) {
        manifest ?: return
        manifest.directNames.forEach { graph.markDirect(it) }
        (manifest.prodRanges + manifest.devRanges).forEach { (n, r) -> graph.declareRange(n, VersionRange(r, n)) }
    }

    private data class Key(val name: String, val range: String)
    private data class Block(val keys: List<Key>, val name: String, val version: String, val deps: Map<String, String>)

    private fun parseBlocks(text: String): List<Block> {
        val lines = text.lines()
        val blocks = mutableListOf<Block>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            val isKeyLine = trimmed.endsWith(":") && trimmed.contains("@") && !line.startsWith(" ") && !trimmed.startsWith("#")
            if (!isKeyLine) { i++; continue }

            val keys = trimmed.removeSuffix(":")
                .split(",").map { it.trim().removeSurrounding("\"") }
                .mapNotNull { parseKey(it) }

            var version = ""
            val deps = mutableMapOf<String, String>()
            var j = i + 1
            var inDeps = false
            while (j < lines.size) {
                val l = lines[j]
                if (!l.startsWith(" ")) break
                val t = l.trim()
                when {
                    t.startsWith("version") -> version = t.substringAfter('"').substringBefore('"')
                    t == "dependencies:" || t == "optionalDependencies:" -> inDeps = true
                    inDeps -> {
                        if (t.matches(Regex("\"?[\\w@./-]+\"?\\s+\".+\""))) {
                            val parts = t.split(Regex("\\s+"))
                            deps[parts[0].removeSurrounding("\"")] = parts[1].removeSurrounding("\"")
                        } else inDeps = false
                    }
                }
                j++
            }
            val name = keys.firstOrNull()?.name
            if (name != null && version.isNotEmpty()) blocks.add(Block(keys, name, version, deps))
            i = j
        }
        return blocks
    }

    private fun parseKey(s: String): Key? {
        val at = s.lastIndexOf('@')
        if (at < 0) return null
        return Key(s.substring(0, at), s.substring(at + 1))
    }
}
