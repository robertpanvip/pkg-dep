package depslens.plugin.ui

import com.intellij.ui.components.JBScrollPane
import depslens.core.model.DependencyGraph
import depslens.core.model.PackageRef
import depslens.plugin.svg.SvgRasterizer
import depslens.plugin.ui.graph.ForceLayout
import depslens.plugin.ui.graph.SvgGraphRenderer
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Graphics
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * 依赖关系图：选中节点后，Kotlin 端跑力导布局生成 SVG，再用平台自带的 SVGLoader 光栅化成
 * BufferedImage，由 Swing 静态显示（无 JCEF、无原生库）。数据来自
 * DependencyGraph.neighborhood；中心节点高亮。
 */
class GraphDetailView : JPanel() {
    private val scroll = JBScrollPane()
    private val imagePanel = SvgImagePanel()

    init {
        layout = BorderLayout()
        scroll.setViewportView(imagePanel)
        add(scroll, BorderLayout.CENTER)
    }

    fun showRef(graph: DependencyGraph?, ref: PackageRef) {
        if (graph == null) {
            imagePanel.show(null, "无依赖图")
            return
        }
        val ids = graph.neighborhood(ref.id, hops = 2)
        val edges = ids.flatMap { id ->
            graph.dependenciesOf(id)
                .filter { it.id in ids }
                .map { ForceLayout.Edge(id, it.id) }
        }
        val layout = ForceLayout.compute(ids, edges)
        val svg = SvgGraphRenderer.render(layout, graph, ref, ids)
        val img = SvgRasterizer.render(svg)
        if (img == null) {
            imagePanel.show(null, "依赖图渲染失败（SVG 光栅化异常）")
            return
        }
        imagePanel.show(img, null)
    }
}

/** 在逻辑尺寸画布上绘制平台 SVGLoader 产出的高分辨率位图（按 IDE UI 缩放自动清晰）。 */
class SvgImagePanel : JComponent() {
    private var image: BufferedImage? = null
    private var message: String? = null
    private val logicalW = 960
    private val logicalH = 680

    init {
        preferredSize = Dimension(logicalW, logicalH)
    }

    fun show(img: BufferedImage?, msg: String?) {
        image = img
        message = msg
        revalidate()
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val img = image
        if (img != null) {
            // Graphics 上下文已按 IDE 的 UI 缩放（Retina 为 2x），
            // 把 2x 超采样的 PNG 缩绘到逻辑尺寸即清晰。
            g.drawImage(img, 0, 0, logicalW, logicalH, null)
        } else {
            g.color = java.awt.Color(0xcc, 0xcc, 0xcc)
            g.font = g.font.deriveFont(13f)
            g.drawString(message ?: "", 12, 26)
        }
    }
}
