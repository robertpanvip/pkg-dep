package depslens.core.pm

import depslens.core.exec.CommandExecutor
import java.io.File
import java.nio.file.Files

/**
 * 在临时目录复制 manifest + lockfile，跑一次真实解析以拿到“假设升级后”的 lockfile 文本。
 * 用于 yarn/pnpm（及 npm 兜底）的影响分析——复用各自 lockfile 解析器做图 diff。
 */
object LockfileProbe {
    fun probe(
        executor: CommandExecutor,
        projectDir: File,
        command: List<String>,
        lockFileName: String,
    ): String? {
        val tmp = Files.createTempDirectory("deps-lens-probe").toFile()
        try {
            File(projectDir, "package.json").copyTo(File(tmp, "package.json"), overwrite = true)
            val existing = File(projectDir, lockFileName)
            if (existing.exists()) existing.copyTo(File(tmp, lockFileName), overwrite = true)
            val res = executor.exec(command, tmp)
            if (res.exitCode != 0) return null
            return File(tmp, lockFileName).takeIf { it.exists() }?.readText()
        } finally {
            tmp.deleteRecursively()
        }
    }
}
