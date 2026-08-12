package depslens.core.pm

import depslens.core.exec.CommandExecutor
import depslens.core.model.DependencyGraph
import depslens.core.parser.ProjectParser
import depslens.core.registry.RegistryClient
import java.io.File

/**
 * npm 实现。
 * - parse: 直接解析 package.json + package-lock.json（v1/v2/v3）。
 * - resolveDryRun: `npm install pkg@ver --package-lock-only --dry-run --json`。
 * - resolveDryRunLockfile: 临时目录跑真实解析拿新 package-lock.json（兜底）。
 * - applyUpgrade: 去掉 --dry-run 真实写回（不下载 tarball，仅更新 lockfile）。
 */
class NpmPackageManager(
    private val executor: CommandExecutor,
    private val registry: RegistryClient,
) : PackageManager {

    override val kind = PmKind.NPM

    override fun parse(projectDir: File): DependencyGraph = ProjectParser.buildGraph(projectDir, PmKind.NPM)

    override suspend fun latestVersion(name: String): String? = registry.latest(name)

    override fun resolveDryRun(projectDir: File, pkg: String, version: String): String {
        val res = executor.exec(
            listOf("npm", "install", "$pkg@$version", "--package-lock-only", "--dry-run", "--json"),
            projectDir,
        )
        return res.stdout
    }

    override fun resolveDryRunLockfile(projectDir: File, pkg: String, version: String): String? =
        LockfileProbe.probe(
            executor,
            projectDir,
            listOf("npm", "install", "$pkg@$version", "--package-lock-only"),
            "package-lock.json",
        )

    override fun applyUpgrade(projectDir: File, pkg: String, version: String) {
        // 真实安装：写回 package.json + package-lock.json 并下载到 node_modules
        executor.exec(
            listOf("npm", "install", "$pkg@$version"),
            projectDir,
        )
    }

    override fun installDependency(projectDir: File, pkg: String, spec: String) {
        executor.exec(listOf("npm", "install", "$pkg@$spec"), projectDir)
    }
}
