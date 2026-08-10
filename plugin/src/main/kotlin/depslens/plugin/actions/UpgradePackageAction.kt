package depslens.plugin.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import depslens.plugin.DepsLensProjectService
import depslens.plugin.ui.UpgradeDialog

/**
 * 全局升级入口：输入包名，找到依赖图中的对应节点，打开升级对话框。
 * 面板中选中某行后也可直接打开（见 DepsLensPanel.onUpgradeRequested）。
 */
class UpgradePackageAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = project.getService(DepsLensProjectService::class.java)
        val name = Messages.showInputDialog(project, "包名:", "DepsLens 升级", Messages.getQuestionIcon()) ?: return
        val ref = service.graph?.allNodes()?.firstOrNull { it.name == name }
        if (ref == null) {
            Messages.showInfoMessage("未在依赖图中找到 $name", "DepsLens")
            return
        }
        UpgradeDialog(project, service, ref).show()
    }
}
