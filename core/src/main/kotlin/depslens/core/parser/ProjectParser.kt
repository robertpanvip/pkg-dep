package depslens.core.parser

import depslens.core.model.DependencyGraph
import depslens.core.pm.PmKind
import java.io.File

/**
 * 组合 manifest + lockfile 构建依赖图。PackageManager.parse 直接委托这里。
 * 另提供 lockTextOverride 重载，供影响分析传入“假设升级后”的 lockfile 文本。
 */
object ProjectParser {
    fun buildGraph(projectDir: File, kind: PmKind): DependencyGraph {
        val manifest = readManifest(projectDir)
        val lockText = readLock(projectDir, kind)
        return parseLock(kind, lockText, manifest, projectDir.name)
    }

    fun buildGraph(projectDir: File, kind: PmKind, lockTextOverride: String): DependencyGraph {
        val manifest = readManifest(projectDir)
        return parseLock(kind, lockTextOverride, manifest, projectDir.name)
    }

    private fun readManifest(projectDir: File): Manifest? {
        val f = File(projectDir, "package.json")
        return if (f.exists()) ManifestParser.parse(f) else null
    }

    private fun readLock(projectDir: File, kind: PmKind): String? = when (kind) {
        PmKind.NPM -> File(projectDir, "package-lock.json").takeIf { it.exists() }?.readText()
        PmKind.YARN -> File(projectDir, "yarn.lock").takeIf { it.exists() }?.readText()
        PmKind.PNPM -> File(projectDir, "pnpm-lock.yaml").takeIf { it.exists() }?.readText()
    }

    private fun parseLock(kind: PmKind, lockText: String?, manifest: Manifest?, name: String): DependencyGraph {
        if (lockText == null) return DependencyGraph(name)
        return when (kind) {
            PmKind.NPM -> NpmLockfileParser.parse(lockText, manifest)
            PmKind.YARN -> YarnLockParser.parse(lockText, manifest)
            PmKind.PNPM -> PnpmLockParser.parse(lockText, manifest)
        }
    }
}
