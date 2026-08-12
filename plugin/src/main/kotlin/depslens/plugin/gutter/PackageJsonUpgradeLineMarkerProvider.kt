package depslens.plugin.gutter

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.PsiElement
import depslens.core.model.DepKind
import depslens.core.model.PackageRef
import depslens.plugin.DepsLensProjectService
import depslens.plugin.ui.UpgradeDialog

/**
 * package.json 入口：在 dependencies / devDependencies 下每个依赖行左侧，
 * 放置一个「升级箭头」gutter 图标（仿 npm scripts 的 gutter 位置）。
 * 点击后自动解析项目（首次）+ 取最新版本，打开升级对话框并立即预览影响。
 */
class PackageJsonUpgradeLineMarkerProvider : LineMarkerProvider {

    private val icon = IconLoader.getIcon("/icons/upgradeArrow.svg", javaClass)

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element !is JsonProperty) return null
        val name = element.name ?: return null
        // 仅匹配直接挂在 dependencies / devDependencies 对象下的属性
        val sectionObject = element.parent as? JsonObject ?: return null
        val sectionProp = sectionObject.parent as? JsonProperty ?: return null
        val section = sectionProp.name ?: return null
        if (section != "dependencies" && section != "devDependencies") return null
        // 限定为 package.json 文件
        val file = element.containingFile as? JsonFile ?: return null
        if (file.name != "package.json") return null
        val literal = element.value as? JsonStringLiteral ?: return null
        val declaredRange = literal.value ?: return null
        val kind = if (section == "devDependencies") DepKind.DEV else DepKind.PROD

        val project = element.project
        val handler = GutterIconNavigationHandler<PsiElement> { _, _ ->
            onUpgradeClick(project, name, declaredRange, kind)
        }
        return LineMarkerInfo(
            element = element,
            range = element.textRange,
            icon = icon,
            tooltipProvider = { _ -> "DepsLens：升级 $name 并预览影响" },
            navHandler = handler,
            alignment = com.intellij.codeInsight.daemon.GutterIconRenderer.Alignment.LEFT,
            altIcon = null,
        )
    }

    private fun onUpgradeClick(project: Project, name: String, declaredRange: String, kind: DepKind) {
        val service = project.getService(DepsLensProjectService::class.java) ?: return
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project, "DepsLens：准备 $name 升级", false,
        ) {
            override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
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
