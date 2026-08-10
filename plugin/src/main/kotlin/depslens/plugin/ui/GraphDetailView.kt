package depslens.plugin.ui

import com.intellij.ui.jcef.JBCefBrowser
import depslens.core.model.DependencyGraph
import depslens.core.model.PackageRef
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * 依赖关系图：JCEF 加载内嵌的、无外部 CDN 的力导向图，渲染某节点的 N-hop 邻居。
 * 数据来自 DependencyGraph.neighborhood；中心节点高亮。
 */
class GraphDetailView : JPanel() {
    private val browser = JBCefBrowser()

    init {
        layout = BorderLayout()
        add(browser.component, BorderLayout.CENTER)
    }

    fun showRef(graph: DependencyGraph?, ref: PackageRef) {
        if (graph == null) {
            browser.loadHTML("<html><body style='color:#ccc'>无依赖图</body></html>")
            return
        }
        val ids = graph.neighborhood(ref.id, hops = 2)
        val nodes = ids.map { id ->
            val n = graph.node(id)
            mapOf(
                "id" to id,
                "label" to (n?.name ?: id),
                "center" to (id == ref.id).toString(),
            )
        }
        val links = mutableListOf<Map<String, String>>()
        ids.forEach { id ->
            graph.dependenciesOf(id).forEach { d ->
                if (d.id in ids) links.add(mapOf("source" to id, "target" to d.id))
            }
        }
        val dataJson = """{"nodes":${nodes.toJson()},"links":${links.toJson()}}"""
        browser.loadHTML(buildHtml(dataJson))
    }

    private fun buildHtml(dataJson: String): String = """
        <!doctype html><html><head><meta charset="utf-8"><style>
        html,body{margin:0;height:100%;background:#1e1e1e;color:#ddd;font-family:sans-serif;overflow:hidden}
        canvas{display:block}#tip{position:absolute;left:8px;top:8px;font-size:12px;opacity:.7}
        </style></head><body>
        <div id="tip">依赖关系图（N-hop 邻居）</div><canvas id="c"></canvas>
        <script>
        const DATA = $dataJson;
        const cv = document.getElementById('c'); const ctx = cv.getContext('2d');
        let W,H; function resize(){W=cv.width=innerWidth;H=cv.height=innerHeight;} resize(); addEventListener('resize',resize);
        const nodes = DATA.nodes.map(n => ({id:n.id,label:n.label,center:n.center==='true',x:Math.random()*W,y:Math.random()*H,vx:0,vy:0}));
        const ix={}; nodes.forEach((n,i)=>ix[n.id]=i);
        const links = DATA.links.map(l=>({s:ix[l.source],t:ix[l.target]}));
        function tick(){
          for(let i=0;i<nodes.length;i++){const a=nodes[i];
            for(let j=i+1;j<nodes.length;j++){const b=nodes[j];
              let dx=a.x-b.x, dy=a.y-b.y, d2=dx*dx+dy*dy+0.01;
              let f=140/d2; a.vx+=dx*f; a.vy+=dy*f; b.vx-=dx*f; b.vy-=dy*f;
            }
            a.vx+=(W/2-a.x)*0.002; a.vy+=(H/2-a.y)*0.002;
          }
          links.forEach(l=>{const a=nodes[l.s],b=nodes[l.t];
            let dx=b.x-a.x, dy=b.y-a.y, d=Math.sqrt(dx*dx+dy*dy+0.01);
            let f=(d-90)*0.01; a.vx+=dx/d*f; a.vy+=dy/d*f; b.vx-=dx/d*f; b.vy-=dy/d*f;
          });
          nodes.forEach(n=>{n.x+=n.vx; n.y+=n.vy; n.vx*=0.85; n.vy*=0.85;
            n.x=Math.max(10,Math.min(W-10,n.x)); n.y=Math.max(10,Math.min(H-10,n.y));});
          ctx.clearRect(0,0,W,H);
          links.forEach(l=>{const a=nodes[l.s],b=nodes[l.t];ctx.strokeStyle='#555';ctx.beginPath();ctx.moveTo(a.x,a.y);ctx.lineTo(b.x,b.y);ctx.stroke();});
          nodes.forEach(n=>{ctx.fillStyle=n.center?'#4a9eff':'#7d8590';ctx.beginPath();ctx.arc(n.x,n.y,n.center?9:6,0,7);ctx.fill();
            ctx.fillStyle='#ccc';ctx.font='11px sans-serif';ctx.fillText(n.label,n.x+10,n.y+3);});
          requestAnimationFrame(tick);
        }
        tick();
        </script></body></html>
    """.trimIndent()

    private fun List<Map<String, String>>.toJson(): String =
        "[" + joinToString(",") { m ->
            "{" + m.entries.joinToString(",") { "\"${it.key}\":\"${it.value.escape()}\"" } + "}"
        } + "]"

    private fun String.escape(): String =
        replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
}
