package depslens.plugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import depslens.core.impact.ImpactResult
import depslens.core.model.PackageRef
import depslens.plugin.ui.DepsLensPanel
import depslens.plugin.ui.GraphDetailView
import depslens.plugin.ui.ImpactPanel
import depslens.plugin.ui.UpgradeDialog
import kotlinx.coroutines.launch
import java.io.File

/**
 * 工具窗口工厂：右侧停靠，承载顶级依赖列表 + 影响预览 + 依赖图。
 */
class DepsLensToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val service = project.getService(DepsLensProjectService::class.java)
        val panel = DepsLensPanel(service)
        val impactPanel = ImpactPanel()
        val graphView = GraphDetailView()

        /**
         * 选中某包即预览“升级到最新版”的影响集合（changed/added/removed），
         * 直接落入 Impact 标签页，满足“升级某包会动到哪些包”的核心需求。
         * 离线模式跳过（无网无法跑 dry-run）。
         */
        fun showImpactPreview(ref: PackageRef) {
            val target = service.latestVersions[ref.name]
            if (target == null) { impactPanel.showMessage("未获取到 ${ref.name} 的最新版本"); return }
            if (service.offline) { impactPanel.showMessage("离线模式：无法预览 ${ref.name} 的影响"); return }
            impactPanel.showMessage("正在分析 ${ref.name} -> $target 的影响…")
            service.scope().launch {
                val base = File(project.basePath ?: return@launch)
                val graph = service.graph ?: return@launch
                val result = runCatching {
                    service.impactAnalyzer()?.analyze(base, graph, ref.name, target)
                }.getOrNull() ?: ImpactResult.empty(ref.name, target)
                ApplicationManager.getApplication().invokeLater { impactPanel.render(result) }
            }
        }

        // 点击某行：右侧展示依赖子图 + 影响预览（针对最新版本，后台计算）
        panel.onRowSelected = { ref ->
            graphView.showRef(service.graph, ref)
            showImpactPreview(ref)
        }
        panel.onUpgradeRequested = { ref -> UpgradeDialog(project, service, ref).show() }
        // 离线模式切换：重建 registry（offline 标志），重解析并回退到本地缓存
        panel.onOfflineToggled = { on ->
            service.offline = on
            service.detectAndParse()
            service.scope().launch {
                service.refreshLatest()
                ApplicationManager.getApplication().invokeLater {
                    panel.showGraph(service.graph)
                    panel.refreshLatestView()
                }
            }
        }
        service.addRefreshListener { panel.refresh() }

        val container = com.intellij.ui.components.JBTabbedPane().apply {
            addTab("Dependencies", panel)
            addTab("Impact", impactPanel)
            addTab("Graph", graphView)
        }
        val content = ContentFactory.getInstance().createContent(container, "", false)
        toolWindow.contentManager.addContent(content)

        service.detectAndParse()
        panel.showGraph(service.graph)

        service.scope().launch {
            service.refreshLatest()
            ApplicationManager.getApplication().invokeLater {
                panel.showGraph(service.graph)
                panel.refreshLatestView()
            }
        }
    }
}
