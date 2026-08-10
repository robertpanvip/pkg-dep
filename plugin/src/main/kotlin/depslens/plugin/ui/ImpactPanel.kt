package depslens.plugin.ui

import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import depslens.core.impact.ImpactResult
import javax.swing.*

/**
 * 升级影响预览面板：列出 changed / added / removed。
 */
class ImpactPanel : JPanel() {
    private val list = JBList<String>()

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        add(JBScrollPane(list))
    }

    fun render(result: ImpactResult) {
        val lines = mutableListOf<String>()
        lines.add("升级 ${result.pkg} -> ${result.target}")
        lines.add("变更 ${result.changed.size}：")
        result.changed.forEach { lines.add("  ${it.name}: ${it.from} -> ${it.to}") }
        lines.add("新增 ${result.added.size}：")
        result.added.forEach { lines.add("  $it") }
        lines.add("移除 ${result.removed.size}：")
        result.removed.forEach { lines.add("  $it") }
        if (result.isEmpty) lines.add("无影响（该版本等价或可满足当前约束）")
        list.setListData(lines.toTypedArray())
    }

    /** 单行提示（分析中 / 离线 / 未取到最新版本等过渡态）。 */
    fun showMessage(text: String) = list.setListData(arrayOf(text))
}
