package depslens.core.parser

import java.io.File

/**
 * 检测 monorepo workspaces 的各包目录（含根项目）。
 * 支持：pnpm-workspace.yaml、package.json 的 workspaces 字段、lerna.json 的 packages 字段。
 * 返回的所有目录都应包含 package.json，用于合并依赖图 / 标记内部包。
 */
object Workspaces {
    fun detect(projectDir: File): List<File> {
        val dirs = mutableListOf(projectDir)
        // pnpm
        val pnpmWs = File(projectDir, "pnpm-workspace.yaml")
        if (pnpmWs.exists()) dirs += globDirs(projectDir, parseYamlList(pnpmWs.readText(), "packages"))
        // npm/yarn
        val pkg = File(projectDir, "package.json")
        if (pkg.exists()) {
            val m = runCatching { ManifestParser.parse(pkg) }.getOrNull()
            dirs += m?.workspaces?.flatMap { globDirs(projectDir, listOf(it)) } ?: emptyList()
        }
        // lerna
        val lerna = File(projectDir, "lerna.json")
        if (lerna.exists()) dirs += globDirs(projectDir, parseYamlList(lerna.readText(), "packages"))

        return dirs.distinctBy { it.absolutePath }.filter { File(it, "package.json").exists() }
    }

    private fun globDirs(root: File, globs: List<String>): List<File> {
        if (globs.isEmpty()) return emptyList()
        val results = mutableListOf<File>()
        // 简单 glob：仅支持 **/* 与 * 的单段展开（足够常见 workspaces 配置）
        for (glob in globs) {
            val base = glob.trim().trimEnd('/').removeSuffix("/*").removeSuffix("/**")
            val dir = File(root, base)
            if (dir.isDirectory) {
                dir.listFiles { f -> f.isDirectory }?.forEach { results.add(it) }
            }
        }
        return results
    }

    private fun parseYamlList(text: String, key: String): List<String> {
        val lines = text.lines()
        val out = mutableListOf<String>()
        var inBlock = false
        for (line in lines) {
            val t = line.trim()
            if (!inBlock) {
                if (t == "$key:" || t.startsWith("$key:")) inBlock = true
                continue
            }
            if (!t.startsWith("- ")) break
            out.add(t.removePrefix("- ").trim().removeSurrounding("\""))
        }
        return out
    }
}
