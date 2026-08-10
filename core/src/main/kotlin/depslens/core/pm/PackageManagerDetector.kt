package depslens.core.pm

import java.io.File

/**
 * 按 lockfile 存在性推断当前项目使用的包管理器。
 * 优先级：pnpm-lock.yaml > yarn.lock > package-lock.json / package.json。
 */
object PackageManagerDetector {
    fun detect(projectDir: File): PmKind? {
        if (File(projectDir, "pnpm-lock.yaml").exists()) return PmKind.PNPM
        if (File(projectDir, "yarn.lock").exists()) return PmKind.YARN
        if (File(projectDir, "package-lock.json").exists() || File(projectDir, "package.json").exists()) return PmKind.NPM
        return null
    }
}
