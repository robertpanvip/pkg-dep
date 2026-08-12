package depslens.plugin.gutter

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import depslens.core.model.BumpLevel
import depslens.core.model.DepKind
import depslens.core.model.PackageRef
import depslens.core.model.VersionRange
import depslens.plugin.DepsLensNotifier
import depslens.plugin.DepsLensProjectService
import depslens.plugin.ui.UpgradeDialog
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * package.json 入口：在 dependencies / devDependencies 下，按「本地是否已安装」决定图标：
 *  - 本地 node_modules 未安装该包 -> 黄色感叹号（#E0A800，对齐 IntelliJ 警告色），点击直接安装到 node_modules。
 *  - 已安装且有可升级版本 -> 绿色向上箭头（#59A869，对齐 npm scripts 的 Run 图标），点击打开升级对话框并预览影响。
 *  - 已安装且为最新 -> 不显示任何图标。
 *
 * 「是否有更新」需要异步拉 registry.latestMany，写入按项目隔离的缓存；缓存就绪后刷新该文件 gutter。
 * 缓存用哨兵值区分「未探测 / 探测过但无更新 / 具体版本」，避免离线/私有包导致反复网络请求与刷新死循环。
 * 「是否已安装」用同步文件系统探测（node_modules/<pkg> 是否存在，向上回溯到项目根以兼容 workspace hoist），开销极小。
 */
class PackageJsonUpgradeLineMarkerProvider : LineMarkerProvider {

    private val arrowIcon = IconLoader.getIcon("/icons/upgradeArrow.svg", javaClass)
    private val warnIcon = IconLoader.getIcon("/icons/installMissing.svg", javaClass)

    companion object {
        private val LATEST_KEY = Key.create<ConcurrentHashMap<String, String>>("DEPSLENS_LATEST_CACHE")
        private val FETCH_KEY = Key.create<AtomicBoolean>("DEPSLENS_FETCHING")
        private const val NO_VERSION = "__DEPSLENS_NO_VERSION__"
    }

    private fun latestMap(project: Project): ConcurrentHashMap<String, String> =
        project.getUserData(LATEST_KEY) ?: run {
            val m = ConcurrentHashMap<String, String>()
            project.putUserData(LATEST_KEY, m)
            m
        }

    private fun fetchingFlag(project: Project): AtomicBoolean =
        project.getUserData(FETCH_KEY) ?: run {
            val b = AtomicBoolean(false)
            project.putUserData(FETCH_KEY, b)
            b
        }

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element !is JsonProperty) return null
        val name = element.name ?: return null
        val sectionObject = element.parent as? JsonObject ?: return null
        val sectionProp = sectionObject.parent as? JsonProperty ?: return null
        val section = sectionProp.name ?: return null
        if (section != "dependencies" && section != "devDependencies") return null
        val file = element.containingFile as? JsonFile ?: return null
        if (file.name != "package.json") return null
        val literal = element.value as? JsonStringLiteral ?: return null
        val declaredRange = literal.value ?: return null
        val kind = if (section == "devDependencies") DepKind.DEV else DepKind.PROD

        val project = element.project
        val pkgDir = File(file.virtualFile?.parent?.path ?: return null)

        // 本地未安装 -> 黄色感叹号，点击安装
        if (!isInstalled(project, pkgDir, name)) {
            val handler = GutterIconNavigationHandler<PsiElement> { _, _ ->
                installMissing(project, name, declaredRange)
            }
            return LineMarkerInfo(
                element,
                element.textRange,
                warnIcon,
                { _: PsiElement -> "DepsLens：未安装 $name，点击安装到 node_modules" },
                handler,
                com.intellij.openapi.editor.markup.GutterIconRenderer.Alignment.LEFT,
            )
        }

        // 已安装：依据异步探测结果决定是否显示升级箭头
        val cached = latestMap(project)[name]
        if (cached == null) {
            scheduleFetch(project, file)
            return null
        }
        if (cached == NO_VERSION) return null
        if (!hasUpgrade(declaredRange, cached)) return null

        val handler = GutterIconNavigationHandler<PsiElement> { _, _ ->
            onUpgradeClick(project, name, declaredRange, kind)
        }
        return LineMarkerInfo(
            element,
            element.textRange,
            arrowIcon,
            { _: PsiElement -> "DepsLens：升级 $name → $cached" },
            handler,
            com.intellij.openapi.editor.markup.GutterIconRenderer.Alignment.LEFT,
        )
    }

    /** 本地 node_modules 是否已安装该包（向上回溯到项目根，兼容 workspace hoist）。 */
    private fun isInstalled(project: Project, pkgDir: File, name: String): Boolean {
        val base = File(project.basePath ?: return true)
        var cur: File? = pkgDir
        while (cur != null) {
            if (File(cur, "node_modules/$name").exists()) return true
            if (cur.absolutePath == base.absolutePath) return false
            cur = cur.parentFile
        }
        return false
    }

    /** 从版本范围串中抽取「基准版本号」（如 ^1.2.3 -> 1.2.3）；无法抽取则视为不可判定。 */
    private fun baseVersion(range: String): String? =
        Regex("""(\d+)\.(\d+)\.(\d+)""").find(range)?.value

    private fun hasUpgrade(declaredRange: String, latest: String): Boolean {
        val current = baseVersion(declaredRange) ?: return false
        return try {
            VersionRange.bumpLevel(current, latest) != BumpLevel.NONE
        } catch (_: Exception) {
            false
        }
    }

    private fun scheduleFetch(project: Project, file: JsonFile) {
        val map = latestMap(project)
        val names = collectDepNames(file).filter { !map.containsKey(it) }
        if (names.isEmpty()) return
        val flag = fetchingFlag(project)
        if (!flag.compareAndSet(false, true)) return
        val service = project.getService(DepsLensProjectService::class.java) ?: run {
            flag.set(false)
            return
        }
        service.scope().launch {
            try {
                val result = service.registry.latestMany(names)
                result.forEach { (k, v) -> map[k] = v ?: NO_VERSION }
            } catch (_: Exception) {
                // 网络/离线失败：保持不显示，等下次高亮重试
            } finally {
                // 保证每个请求过的包都有缓存条目，阻断反复网络请求与刷新死循环
                names.forEach { if (!map.containsKey(it)) map[it] = NO_VERSION }
                flag.set(false)
                ApplicationManager.getApplication().invokeLater {
                    DaemonCodeAnalyzer.getInstance(project).restart(file)
                }
            }
        }
    }

    private fun collectDepNames(file: JsonFile): List<String> {
        val names = mutableListOf<String>()
        file.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is JsonProperty) {
                    val obj = element.parent as? JsonObject
                    val sec = (obj?.parent as? JsonProperty)?.name
                    if (sec == "dependencies" || sec == "devDependencies") {
                        (element.value as? JsonStringLiteral)?.value?.let { names.add(element.name) }
                    }
                }
                super.visitElement(element)
            }
        })
        return names
    }

    /** 本地未安装：点击后用对应包管理器真实安装到 node_modules（spec 取 package.json 中的版本范围）。 */
    private fun installMissing(project: Project, name: String, spec: String) {
        val service = project.getService(DepsLensProjectService::class.java) ?: return
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project, "DepsLens：安装 $name", false,
        ) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    if (service.packageManager == null) service.detectAndParse()
                    val pm = service.packageManager
                        ?: throw IllegalStateException("未检测到包管理器（无 package-lock / yarn.lock / pnpm-lock）")
                    val base = File(project.basePath ?: throw IllegalStateException("项目路径不可用"))
                    pm.installDependency(base, name, spec)
                } catch (e: Exception) {
                    DepsLensNotifier.error(project, name, spec, e)
                    return
                }
                // 安装成功：重新解析与刷新（gutter 上黄色感叹号随即消失）
                try {
                    service.detectAndParse()
                } catch (_: Exception) { /* 解析失败不影响安装结果反馈 */ }
                DepsLensNotifier.installed(project, name, spec)
                service.scope().launch {
                    try {
                        service.refreshLatest()
                        service.notifyRefreshed()
                    } catch (_: Exception) { /* 刷新失败不影响安装结果反馈 */ }
                    ApplicationManager.getApplication().invokeLater {
                        DaemonCodeAnalyzer.getInstance(project).restart()
                    }
                }
            }
        })
    }

    private fun onUpgradeClick(project: Project, name: String, declaredRange: String, kind: DepKind) {
        val service = project.getService(DepsLensProjectService::class.java) ?: return
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project, "DepsLens：准备 $name 升级", false,
        ) {
            override fun run(indicator: ProgressIndicator) {
                // 首次点击（工具窗口未打开过）时确保已完成依赖图解析
                if (service.graph == null) service.detectAndParse()
                service.scope().launch {
                    val target = service.latestVersions[name] ?: service.registry.latest(name)
                    val resolved = service.graph
                        ?.directDependencies()
                        ?.firstOrNull { it.name == name }
                        ?.version ?: declaredRange
                    val ref = PackageRef(name, resolved, kind)
                    ApplicationManager.getApplication().invokeLater {
                        UpgradeDialog(project, service, ref, targetOverride = target, autoPreview = true).show()
                    }
                }
            }
        })
    }
}
