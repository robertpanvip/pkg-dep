package depslens.core.pm

import depslens.core.exec.CommandExecutor
import depslens.core.model.DependencyGraph
import depslens.core.parser.ProjectParser
import depslens.core.registry.RegistryClient
import java.io.File

/**
 * yarn 实现。
 * - parse: 直接解析 package.json + yarn.lock（v1）。
 * - resolveDryRun: `yarn up pkg@ver --json`（yarn 升级输出即含变更）。
 * - resolveDryRunLockfile: 临时目录 `yarn up` 拿新 yarn.lock，复用语解析器做 diff。
 * - applyUpgrade: `yarn up pkg@ver`。
 */
class YarnPackageManager(
    private val executor: CommandExecutor,
    private val registry: RegistryClient,
) : PackageManager {

    override val kind = PmKind.YARN

    override fun parse(projectDir: File): DependencyGraph = ProjectParser.buildGraph(projectDir, PmKind.YARN)

    override suspend fun latestVersion(name: String): String? = registry.latest(name)

    override fun resolveDryRun(projectDir: File, pkg: String, version: String): String {
        val res = executor.exec(listOf("yarn", "up", "$pkg@$version", "--json"), projectDir)
        return res.stdout
    }

    override fun resolveDryRunLockfile(projectDir: File, pkg: String, version: String): String? =
        LockfileProbe.probe(executor, projectDir, listOf("yarn", "up", "$pkg@$version"), "yarn.lock")

    override fun applyUpgrade(projectDir: File, pkg: String, version: String) {
        executor.exec(listOf("yarn", "up", "$pkg@$version"), projectDir)
    }
}
