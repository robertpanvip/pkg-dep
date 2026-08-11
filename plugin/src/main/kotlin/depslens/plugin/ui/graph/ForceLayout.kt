package depslens.plugin.ui.graph

import java.awt.Point

/**
 * 轻量力导向布局（Fruchterman–Reingold 变体），在 Kotlin 端把依赖图算成坐标。
 *
 * 平台 SVGLoader 只负责把 SVG 光栅化，布局必须在 JVM 侧完成（没有 D3）。这里用确定性随机种子，
 * 保证同一子图每次渲染位置稳定，便于对比。
 */
object ForceLayout {
    data class Edge(val a: String, val b: String)

    fun compute(
        ids: Set<String>,
        edges: List<Edge>,
        width: Int = 960,
        height: Int = 680,
    ): Map<String, Point> {
        if (ids.isEmpty()) return emptyMap()

        val rnd = java.util.Random(ids.size * 2654435761L + 7L)
        val pos = ids.associateWith {
            Point(
                (20 + rnd.nextDouble() * (width - 40)).toInt(),
                (20 + rnd.nextDouble() * (height - 40)).toInt(),
            )
        }.toMutableMap()
        val disp = ids.associateWith { Point(0, 0) }.toMutableMap()

        // 理想边长 ~ 区域平均可用空间
        val k = kotlin.math.sqrt((width * height).toDouble() / ids.size.coerceAtLeast(1))
        var temp = width / 8.0
        val list = ids.toList()

        repeat(400) {
            // 重置位移
            for (id in ids) disp[id] = Point(0, 0)

            // 节点间斥力
            for (i in list.indices) {
                for (j in i + 1 until list.size) {
                    val a = list[i]
                    val b = list[j]
                    val pa = pos[a]!!
                    val pb = pos[b]!!
                    var dx = (pa.x - pb.x).toDouble()
                    var dy = (pa.y - pb.y).toDouble()
                    var d2 = dx * dx + dy * dy
                    if (d2 < 0.01) {
                        d2 = 0.01
                        dx = rnd.nextDouble()
                        dy = rnd.nextDouble()
                    }
                    val d = kotlin.math.sqrt(d2)
                    val rep = (k * k) / d
                    val fx = (dx / d * rep).toInt()
                    val fy = (dy / d * rep).toInt()
                    val da = disp[a]!!
                    val db = disp[b]!!
                    disp[a] = Point(da.x + fx, da.y + fy)
                    disp[b] = Point(db.x - fx, db.y - fy)
                }
            }

            // 边引力
            for (e in edges) {
                val a = e.a
                val b = e.b
                if (a !in pos || b !in pos) continue
                val pa = pos[a]!!
                val pb = pos[b]!!
                val dx = (pa.x - pb.x).toDouble()
                val dy = (pa.y - pb.y).toDouble()
                val d = kotlin.math.sqrt(dx * dx + dy * dy + 0.01)
                val att = (d * d) / k
                val fx = (dx / d * att).toInt()
                val fy = (dy / d * att).toInt()
                val da = disp[a]!!
                val db = disp[b]!!
                disp[a] = Point(da.x - fx, da.y - fy)
                disp[b] = Point(db.x + fx, db.y + fy)
            }

            // 向中心轻微聚拢，避免飘出画布
            for (id in ids) {
                val p = pos[id]!!
                val d = disp[id]!!
                disp[id] = Point(
                    d.x + ((width / 2 - p.x) * 0.02).toInt(),
                    d.y + ((height / 2 - p.y) * 0.02).toInt(),
                )
            }

            // 按温度限幅后落点
            for (id in ids) {
                val p = pos[id]!!
                val d = disp[id]!!
                val len = kotlin.math.sqrt(d.x * d.x + d.y * d.y + 0.01)
                val lim = kotlin.math.min(len, temp)
                val nx = (p.x + d.x / len * lim).toInt().coerceIn(24, width - 24)
                val ny = (p.y + d.y / len * lim).toInt().coerceIn(24, height - 24)
                pos[id] = Point(nx, ny)
            }
            temp *= 0.99
        }

        return pos
    }
}
