package depslens.core.model

/**
 * 已解析依赖图：节点是 package@version，边是依赖关系。
 * 同时保留“声明范围”（来自 package.json）以便计算升级幅度与顶级依赖。
 */
class DependencyGraph(
    val rootProject: String,
    private val nodes: MutableMap<String, PackageRef> = mutableMapOf(),
    private val outEdges: MutableMap<String, MutableSet<String>> = mutableMapOf(),
    private val inEdges: MutableMap<String, MutableSet<String>> = mutableMapOf(),
    private val declaredRanges: MutableMap<String, VersionRange> = mutableMapOf(),
    private val directNames: MutableSet<String> = mutableSetOf(),
) {
    fun addNode(ref: PackageRef) { nodes[ref.id] = ref }
    fun addEdge(from: PackageRef, to: PackageRef) {
        outEdges.getOrPut(from.id) { mutableSetOf() }.add(to.id)
        inEdges.getOrPut(to.id) { mutableSetOf() }.add(from.id)
    }
    fun declareRange(name: String, range: VersionRange) { declaredRanges[name] = range }
    fun markDirect(name: String) { directNames.add(name) }

    fun node(id: String): PackageRef? = nodes[id]
    fun allNodes(): Collection<PackageRef> = nodes.values
    fun directDependencies(): List<PackageRef> = nodes.values.filter { it.name in directNames }
    fun rangeOf(name: String): VersionRange? = declaredRanges[name]

    fun dependenciesOf(id: String): List<PackageRef> = outEdges[id].orEmpty().mapNotNull { nodes[it] }
    fun dependentsOf(id: String): List<PackageRef> = inEdges[id].orEmpty().mapNotNull { nodes[it] }

    fun allEdges(): List<Pair<PackageRef, PackageRef>> =
        outEdges.flatMap { (fromId, toSet) ->
            toSet.mapNotNull { toId -> node(fromId)?.let { f -> node(toId)?.let { t -> f to t } } }
        }
    val directNamesView: Set<String> get() = directNames
    fun allRanges(): Map<String, VersionRange> = declaredRanges

    /** 合并另一个图（workspaces 多包场景）。 */
    fun mergeFrom(other: DependencyGraph) {
        other.allNodes().forEach { addNode(it) }
        other.allEdges().forEach { (f, t) -> addEdge(f, t) }
        other.directNamesView.forEach { markDirect(it) }
        other.allRanges().forEach { (n, r) -> declareRange(n, r) }
    }

    /**
     * N-hop 子图：返回以 center 为中心的上下 N 层邻居节点 id 集合。
     * 用于“点击展开依赖关系图”时只渲染局部，避免一次性画整棵树。
     */
    fun neighborhood(centerId: String, hops: Int): Set<String> {
        val visited = mutableSetOf(centerId)
        var frontier = setOf(centerId)
        repeat(hops) {
            val next = mutableSetOf<String>()
            frontier.forEach { id ->
                outEdges[id].orEmpty().forEach { if (it !in visited) { visited.add(it); next.add(it) } }
                inEdges[id].orEmpty().forEach { if (it !in visited) { visited.add(it); next.add(it) } }
            }
            frontier = next
        }
        return visited
    }
}
