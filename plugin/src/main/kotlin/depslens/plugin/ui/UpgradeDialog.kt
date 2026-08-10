package depslens.plugin.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import depslens.core.impact.ImpactAnalyzer
import depslens.core.impact.ImpactResult
import depslens.core.model.PackageRef
import depslens.plugin.DepsLensProjectService
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.io.File
import javax.swing.*

/**
 * 升级对话框：输入目标版本 -> 预览影响（ImpactAnalyzer）-> 确认后异步写回 lockfile。
 * 写回在后台协程执行，不阻塞 UI；完成后通知服务刷新监听者。
 */
class UpgradeDialog(
    private val project: Project,
    private val service: DepsLensProjectService,
    private val ref: PackageRef,
) : DialogWrapper(project) {

    private val targetField = JTextField(service.latestVersions[ref.name] ?: "", 20)
    private val previewList = JBList<String>()
    private val analyzer = service.impactAnalyzer()

    init {
        title = "升级 ${ref.name}"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(8, 8))
        val top = JPanel().apply {
            add(JLabel("目标版本:"))
            add(targetField)
            add(JButton("预览影响").apply { addActionListener { preview() } })
        }
        panel.add(top, BorderLayout.NORTH)
        panel.add(JBScrollPane(previewList), BorderLayout.CENTER)
        panel.preferredSize = java.awt.Dimension(460, 320)
        return panel
    }

    private fun preview() {
        val target = targetField.text.trim()
        if (target.isEmpty() || analyzer == null) return
        val base = File(project.basePath ?: return)
        val graph = service.graph ?: return
        service.scope().launch {
            val result = analyzer.analyze(base, graph, ref.name, target)
            ApplicationManager.getApplication().invokeLater { renderPreview(result) }
        }
    }

    private fun renderPreview(result: ImpactResult) {
        val lines = mutableListOf<String>()
        lines.add("升级 ${ref.name} -> ${result.target}")
        lines.add("变更 ${result.changed.size}：")
        result.changed.forEach { lines.add("  ${it.name}: ${it.from} -> ${it.to}") }
        lines.add("新增 ${result.added.size}：")
        result.added.forEach { lines.add("  $it") }
        lines.add("移除 ${result.removed.size}：")
        result.removed.forEach { lines.add("  $it") }
        if (result.isEmpty) lines.add("无影响（该版本等价或可满足当前约束）")
        previewList.setListData(lines.toTypedArray())
    }

    override fun doOKAction() {
        val target = targetField.text.trim()
        if (target.isEmpty()) { super.doOKAction(); return }
        val base = File(project.basePath ?: return)
        val pm = service.packageManager ?: return
        // 异步写回 lockfile（不阻塞 UI），完成后重新解析并通知刷新
        service.scope().launch {
            pm.applyUpgrade(base, ref.name, target)
            service.detectAndParse()
            service.refreshLatest()
            service.notifyRefreshed()
        }
        super.doOKAction()
    }
}
