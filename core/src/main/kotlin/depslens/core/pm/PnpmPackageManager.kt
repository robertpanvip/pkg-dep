package depslens.core.pm

import depslens.core.exec.CommandExecutor
import depslens.core.model.DependencyGraph
import depslens.core.parser.ProjectParser
import depslens.core.registry.RegistryClient
import java.io.File

/**
 * pnpm 实现。
 * - parse: 直接解析 package.json + pnpm-lock.yaml。
 * - resolveDryRun: `pnpm up pkg@ver --dry-run --json`。
 * - resolveDryRunLockfile: 临时目录 `pnpm up` 拿新 pnpm-lock.yaml，复用语解析器做 diff。
 * - applyUpgrade: `pnpm up pkg@ver`。
 */
class PnpmPackageManager(
    private val executor: CommandExecutor,
    private val registry: RegistryClient,
) : PackageManager {

    override val kind = PmKind.PNPM

    override fun parse(projectDir: File): DependencyGraph = ProjectParser.buildGraph(projectDir, PmKind.PNPM)

    override suspend fun latestVersion(name: String): String? = registry.latest(name)

    override fun resolveDryRun(projectDir: File, pkg: String, version: String): String {
        val res = executor.exec(listOf("pnpm", "up", "$pkg@$version", "--dry-run", "--json"), projectDir)
        return res.stdout
    }

    override fun resolveDryRunLockfile(projectDir: File, pkg: String, version: String): String? =
        LockfileProbe.probe(executor, projectDir, listOf("pnpm", "up", "$pkg@$version"), "pnpm-lock.yaml")

    override fun applyUpgrade(projectDir: File, pkg: String, version: String) {
        executor.exec(listOf("pnpm", "up", "$pkg@$version"), projectDir)
    }
}
