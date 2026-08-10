package depslens.core.registry

import java.io.File

/**
 * .npmrc 解析：默认 registry + 作用域私有源（@scope:registry=...）。
 * 用于把 RegistryClient 查询与包管理器命令路由到正确的源。
 */
data class Npmrc(val defaultRegistry: String, val scoped: Map<String, String>) {
    fun registryFor(scope: String?): String =
        if (scope != null) scoped[scope] ?: defaultRegistry else defaultRegistry
}

object NpmrcParser {
    fun parse(file: File): Npmrc {
        var def = "https://registry.npmjs.org"
        val scoped = mutableMapOf<String, String>()
        if (file.exists()) {
            file.forEachLine { raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEachLine
                val eq = line.indexOf('=')
                if (eq < 0) return@forEachLine
                val key = line.substring(0, eq).trim()
                val value = line.substring(eq + 1).trim()
                if (key == "registry") def = value
                else if (key.endsWith(":registry")) scoped[key.removeSuffix(":registry")] = value
            }
        }
        return Npmrc(def, scoped)
    }

    /** 从包名提取作用域（@scope/name -> @scope）。 */
    fun scopeOf(name: String): String? = name.substringBefore('/').takeIf { it.startsWith("@") }
}
