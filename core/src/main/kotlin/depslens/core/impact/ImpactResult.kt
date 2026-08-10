package depslens.core.impact

/** 单个被改变的依赖：从 from 版本变到 to 版本。 */
data class ChangedDep(val name: String, val from: String, val to: String)

/**
 * 升级 pkg -> target 的影响集合。
 * @param changed 版本发生变化的依赖（name, from, to）
 * @param added   新增的包（id：name@version）
 * @param removed 被移除的包（id）
 */
data class ImpactResult(
    val pkg: String,
    val target: String,
    val changed: List<ChangedDep>,
    val added: List<String>,
    val removed: List<String>,
) {
    val isEmpty: Boolean get() = changed.isEmpty() && added.isEmpty() && removed.isEmpty()
    companion object {
        fun empty(pkg: String, target: String) = ImpactResult(pkg, target, emptyList(), emptyList(), emptyList())
    }
}
