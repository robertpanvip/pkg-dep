package depslens.core

import depslens.core.parser.ManifestParser
import depslens.core.parser.NpmLockfileParser
import depslens.core.parser.YarnLockParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ParsersTest {

    private val manifestJson = """
        {
          "name": "demo",
          "dependencies": { "react": "^18.2.0" },
          "devDependencies": { "react-dom": "^18.2.0" }
        }
    """.trimIndent()

    private val npmV3Lock = """
        {
          "name": "demo",
          "lockfileVersion": 3,
          "packages": {
            "": { "dependencies": { "react": "^18.2.0" }, "devDependencies": { "react-dom": "^18.2.0" } },
            "node_modules/react": { "version": "18.2.0", "dependencies": { "loose-envify": "^1.1.0" } },
            "node_modules/react-dom": { "version": "18.2.0", "dev": true, "dependencies": { "react": "18.2.0", "scheduler": "^0.23.0" } },
            "node_modules/scheduler": { "version": "0.23.0" }
          }
        }
    """.trimIndent()

    private val yarnLock = """
        "react@^18.2.0":
          version "18.2.0"
          dependencies:
            loose-envify "^1.1.0"

        "react-dom@^18.2.0":
          version "18.2.0"
          dependencies:
            react "18.2.0"
            scheduler "^0.23.0"

        "scheduler@^0.23.0":
          version "0.23.0"
    """.trimIndent()

    @Test
    fun `npm v3 lockfile builds expected graph`() {
        val manifest = ManifestParser.parse(manifestJson)
        val graph = NpmLockfileParser.parse(npmV3Lock, manifest)

        assertEquals(3, graph.allNodes().size) // react, react-dom, scheduler
        assertTrue(graph.directDependencies().any { it.name == "react" })
        assertTrue(graph.directDependencies().any { it.name == "react-dom" })

        val dom = graph.allNodes().first { it.name == "react-dom" }
        assertTrue(graph.dependenciesOf(dom.id).any { it.name == "react" })
        assertTrue(graph.dependenciesOf(dom.id).any { it.name == "scheduler" })
    }

    @Test
    fun `yarn lockfile builds expected graph`() {
        val manifest = ManifestParser.parse(manifestJson)
        val graph = YarnLockParser.parse(yarnLock, manifest)

        assertEquals(3, graph.allNodes().size)
        val dom = graph.allNodes().first { it.name == "react-dom" }
        assertTrue(graph.dependenciesOf(dom.id).any { it.name == "react" })
        assertTrue(graph.dependenciesOf(dom.id).any { it.name == "scheduler" })
    }
}
