package depslens.plugin

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import depslens.core.exec.ProcessCommandExecutor
import depslens.core.impact.ImpactAnalyzer
import depslens.core.model.BumpLevel
import depslens.core.model.DependencyGraph
import depslens.core.model.PackageRef
import depslens.core.model.VersionRange
import depslens.core.pm.NpmPackageManager
import depslens.core.pm.PackageManager
import depslens.core.pm.PackageManagerDetector
import depslens.core.pm.PmKind
import depslens.core.pm.PnpmPackageManager
import depslens.core.pm.YarnPackageManager
import depslens.core.parser.Workspaces
import depslens.core.registry.NpmrcParser
import depslens.core.registry.RegistryClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File

/**
 * 项目级服务：检测包管理器、解析依赖图、缓存最新版本、提供影响分析。
 * 命令执行默认用进程（P5 可改为注入 IntelliJ 检测的 Node 路径）。
 */
@Service(Service.Level.PROJECT)
class DepsLensProjectService(private val project: Project) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val executor = ProcessCommandExecutor()
    var registry: RegistryClient = RegistryClient()
    var offline: Boolean = false

    var packageManager: PackageManager? = null
        private set
    var graph: DependencyGraph? = null
        private set
    val latestVersions = mutableMapOf<String, String?>()

    private val refreshListeners = mutableListOf<() -> Unit>()

    /** 注册刷新监听（依赖图/最新版本变化后通知 UI 重绘）。 */
    fun addRefreshListener(listener: () -> Unit) { refreshListeners.add(listener) }

    /** 在 EDT 上通知所有监听者刷新（升级写回后调用）。 */
    fun notifyRefreshed() {
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
            refreshListeners.forEach { it() }
        }
    }

    /** 检测并解析当前项目的依赖图（应在后台线程调用）。 */
    fun detectAndParse() {
        val base = File(project.basePath ?: return)
        val kind = PackageManagerDetector.detect(base) ?: return
        val npmrc = NpmrcParser.parse(File(base, ".npmrc"))
        registry = RegistryClient(npmrc = npmrc, offline = offline, cacheDir = cacheDir(base))
        packageManager = when (kind) {
            PmKind.NPM -> NpmPackageManager(executor, registry)
            PmKind.YARN -> YarnPackageManager(executor, registry)
            PmKind.PNPM -> PnpmPackageManager(executor, registry)
        }
        val graph = packageManager!!.parse(base)
        // workspaces：合并各子包依赖图（monorepo 内部包也纳入）
        Workspaces.detect(base).forEach { dir ->
            if (dir.absolutePath != base.absolutePath) graph.mergeFrom(packageManager!!.parse(dir))
        }
        this.graph = graph
    }

    private fun cacheDir(base: File): File = File(base, ".deps-lens-cache").apply { mkdirs() }

    /** 并行查询所有顶级依赖的最新版本（网络，挂起）。 */
    suspend fun refreshLatest(): Map<String, String?> {
        val g = graph ?: return emptyMap()
        val result = registry.latestMany(g.directDependencies().map { it.name })
        latestVersions.clear()
        latestVersions.putAll(result)
        return result
    }

    fun bumpOf(ref: PackageRef): BumpLevel {
        val latest = latestVersions[ref.name] ?: return BumpLevel.NONE
        return VersionRange.bumpLevel(ref.version, latest)
    }

    fun impactAnalyzer(): ImpactAnalyzer? = packageManager?.let { ImpactAnalyzer(it) }

    fun scope(): CoroutineScope = scope

    fun dispose() = scope.cancel()
}
