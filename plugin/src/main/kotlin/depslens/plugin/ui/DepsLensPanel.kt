package depslens.plugin.ui

import com.intellij.ui.table.JBTable
import depslens.core.model.BumpLevel
import depslens.core.model.DependencyGraph
import depslens.core.model.PackageRef
import depslens.plugin.DepsLensProjectService
import java.awt.Color
import java.awt.Component
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

/**
 * 工具窗口主面板：顶级依赖树表（Package | Current | Latest | Type | Status）。
 * 列表为主；点击某行由 onRowSelected 触发影响预览 / 依赖图（P3/P4 接入）。
 */
class DepsLensPanel(private val service: DepsLensProjectService) : JPanel() {
    private val table = JBTable()
    private val model = DepsTableModel()
    private val statusLabel = JLabel("未加载")

    var onRowSelected: ((PackageRef) -> Unit)? = null
    var onUpgradeRequested: ((PackageRef) -> Unit)? = null
    var onOfflineToggled: ((Boolean) -> Unit)? = null

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        table.model = model
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        table.columnModel.getColumn(4).cellRenderer = StatusRenderer()
        table.selectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                val row = table.selectedRow
                if (row >= 0) model.rowRef(row)?.let { ref -> onRowSelected?.invoke(ref) }
            }
        }
        val toolbar = JPanel().apply {
            add(JButton("升级选中…").apply {
                addActionListener {
                    val row = table.selectedRow
                    if (row >= 0) this@DepsLensPanel.model.rowRef(row)?.let { onUpgradeRequested?.invoke(it) }
                }
            })
            add(JCheckBox("离线模式").apply {
                addActionListener { onOfflineToggled?.invoke(isSelected) }
            })
        }
        add(toolbar)
        add(JScrollPane(table))
        add(statusLabel)
    }

    /** 重新解析后由服务刷新监听调用。 */
    fun refresh() {
        showGraph(service.graph)
        refreshLatestView()
    }

    fun showGraph(graph: DependencyGraph?) {
        model.setGraph(graph, service)
        statusLabel.text = graph?.let { "顶级依赖 ${graph.directDependencies().size} 个" } ?: "未加载"
    }

    /** 最新版本刷新后重绘（着色更新）。 */
    fun refreshLatestView() = table.repaint()

    private class DepsTableModel : AbstractTableModel() {
        private val cols = arrayOf("Package", "Current", "Latest", "Type", "Status")
        private var rows: List<Row> = emptyList()
        private var svc: DepsLensProjectService? = null

        fun setGraph(graph: DependencyGraph?, service: DepsLensProjectService) {
            svc = service
            rows = graph?.directDependencies()
                ?.map { ref -> Row(ref, service.latestVersions[ref.name], service.bumpOf(ref)) }
                ?.sortedBy { it.ref.name } ?: emptyList()
            fireTableDataChanged()
        }

        fun rowRef(row: Int): PackageRef? = rows.getOrNull(row)?.ref

        override fun getRowCount() = rows.size
        override fun getColumnCount() = cols.size
        override fun getColumnName(c: Int) = cols[c]
        override fun getValueAt(r: Int, c: Int): Any = when (c) {
            0 -> rows[r].ref.name
            1 -> rows[r].ref.version
            2 -> rows[r].latest ?: "-"
            3 -> rows[r].ref.kind.name
            4 -> rows[r].bump.name
            else -> ""
        }

        private data class Row(val ref: PackageRef, val latest: String?, val bump: BumpLevel)
    }

    private class StatusRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, col: Int,
        ): Component {
            val c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col)
            if (!isSelected && c is JLabel) {
                val bump = runCatching { BumpLevel.valueOf(value.toString()) }.getOrDefault(BumpLevel.NONE)
                c.foreground = when (bump) {
                    BumpLevel.MAJOR -> Color(0xE24B4A.toInt())
                    BumpLevel.MINOR -> Color(0xEF9F27.toInt())
                    BumpLevel.PATCH -> Color(0x639922.toInt())
                    else -> c.foreground
                }
            }
            return c
        }
    }
}
