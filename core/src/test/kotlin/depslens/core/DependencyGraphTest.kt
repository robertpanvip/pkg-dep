package depslens.core

import depslens.core.model.DependencyGraph
import depslens.core.model.DepKind
import depslens.core.model.PackageRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DependencyGraphTest {

    @Test
    fun `dependents and dependencies are tracked`() {
        val g = DependencyGraph("demo")
        val react = PackageRef("react", "18.2.0", DepKind.PROD)
        val dom = PackageRef("react-dom", "18.2.0", DepKind.PROD)
        g.addNode(react)
        g.addNode(dom)
        g.addEdge(dom, react)
        g.markDirect("react")
        g.markDirect("react-dom")

        assertEquals(listOf(react), g.dependenciesOf(dom.id))
        assertEquals(listOf(dom), g.dependentsOf(react.id))
        assertEquals(2, g.directDependencies().size)
    }

    @Test
    fun `neighborhood returns N-hop neighbors`() {
        val g = DependencyGraph("demo")
        val a = PackageRef("a", "1.0.0")
        val b = PackageRef("b", "1.0.0")
        val c = PackageRef("c", "1.0.0")
        g.addNode(a)
        g.addNode(b)
        g.addNode(c)
        g.addEdge(a, b)
        g.addEdge(b, c)

        val ids = g.neighborhood(a.id, hops = 2)
        assertTrue(ids.contains(a.id) && ids.contains(b.id) && ids.contains(c.id))
    }
}
