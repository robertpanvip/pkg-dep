package depslens.core.impact

import depslens.core.model.DependencyGraph
import depslens.core.parser.ProjectParser
import depslens.core.pm.PackageManager
import depslens.core.pm.PmKind
import java.io.File

/**
 * 影响分析：优先用包管理器自带的 dry-run JSON（npm 最准确、最轻量）；
 * yarn/pnpm 则生成一份“假设升级后的 lockfile”并复用语解析器做图 diff，
 * 保证与终端行为一致、不误报。
 */
class ImpactAnalyzer(private val pm: PackageManager) {

    fun analyze(projectDir: File, current: DependencyGraph, pkg: String, target: String): ImpactResult {
        return when (pm.kind) {
            PmKind.NPM -> NpmImpactParser.parse(pm.resolveDryRun(projectDir, pkg, target), pkg, target)
            else -> {
                val lockText = pm.resolveDryRunLockfile(projectDir, pkg, target)
                    ?: return ImpactResult.empty(pkg, target)
                val next = ProjectParser.buildGraph(projectDir, pm.kind, lockText)
                GraphDiff.diff(current, next, pkg, target)
            }
        }
    }
}
