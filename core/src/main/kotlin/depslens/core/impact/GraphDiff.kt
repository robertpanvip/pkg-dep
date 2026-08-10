package depslens.core.impact

import depslens.core.model.DependencyGraph

/**
 * 把“升级前”的图与“升级后”的图做结构 diff，得到受影响集合。
 * 用于 yarn/pnpm 等不易直接解析 dry-run JSON 的场景（先生成新 lockfile 再解析）。
 */
object GraphDiff {
    fun diff(current: DependencyGraph, next: DependencyGraph, pkg: String, target: String): ImpactResult {
        val changed = mutableListOf<ChangedDep>()
        val added = mutableListOf<String>()
        val removed = mutableListOf<String>()

        val curByName = current.allNodes().groupBy { it.name }
        val nextByName = next.allNodes().groupBy { it.name }

        for ((name, nextNodes) in nextByName) {
            val curNodes = curByName[name]
            if (curNodes == null) {
                nextNodes.forEach { added.add(it.id) }
                continue
            }
            val curVers = curNodes.map { it.version }.toSet()
            nextNodes.forEach { nn ->
                if (nn.version !in curVers) {
                    val from = curNodes.firstOrNull()?.version ?: "?"
                    changed.add(ChangedDep(name, from, nn.version))
                }
            }
        }
        for ((name, curNodes) in curByName) {
            if (name !in nextByName) curNodes.forEach { removed.add(it.id) }
        }
        return ImpactResult(pkg, target, changed, added, removed)
    }
}
