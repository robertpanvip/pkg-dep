package depslens.plugin.ui.graph

import depslens.core.model.DependencyGraph
import depslens.core.model.PackageRef
import java.awt.Point

/**
 * 把力导布局结果渲染成暗色主题的 SVG 字符串，交给平台 SVGLoader 光栅化成位图。
 *
 * - 边：灰色连线。
 * - 节点：中心节点蓝色高亮、顶级依赖灰色、间接依赖更暗；圆 + 标签。
 * - 深色底由 <rect> 提供（SVGLoader 背景透明）。
 */
object SvgGraphRenderer {
    private const val WIDTH = 960
    private const val HEIGHT = 680

    fun render(
        layout: Map<String, Point>,
        graph: DependencyGraph,
        ref: PackageRef,
        ids: Set<String>,
    ): String {
        val directIds = graph.directDependencies().map { it.id }.toSet()
        val sb = StringBuilder()
        sb.append("""<svg xmlns="http://www.w3.org/2000/svg" width="$WIDTH" height="$HEIGHT" viewBox="0 0 $WIDTH $HEIGHT">""")
        sb.append("""<rect width="$WIDTH" height="$HEIGHT" fill="#1e1e1e"/>""")

        // 边
        for (id in ids) {
            val from = layout[id] ?: continue
            graph.dependenciesOf(id).forEach { d ->
                if (d.id !in ids) return@forEach
                val to = layout[d.id] ?: return@forEach
                sb.append("""<line x1="${from.x}" y1="${from.y}" x2="${to.x}" y2="${to.y}" stroke="#555" stroke-width="1"/>""")
            }
        }

        // 节点
        for (id in ids) {
            val p = layout[id] ?: continue
            val n = graph.node(id)
            val isCenter = id == ref.id
            val fill = when {
                isCenter -> "#4a9eff"
                id in directIds -> "#7d8590"
                else -> "#5a6270"
            }
            val r = if (isCenter) 10 else 7
            sb.append("""<circle cx="${p.x}" cy="${p.y}" r="$r" fill="$fill"/>""")
            val label = (n?.name ?: id).escapeXml()
            sb.append("""<text x="${p.x + r + 3}" y="${p.y + 4}" fill="#ccc" font-family="sans-serif" font-size="12">$label</text>""")
        }

        sb.append("</svg>")
        return sb.toString()
    }

    private fun String.escapeXml(): String =
        replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
