package depslens.core.exec

import java.io.File

/** 命令执行结果。 */
data class CommandResult(val exitCode: Int, val stdout: String, val stderr: String)

/**
 * 命令执行抽象。plugin 模块注入 IntelliJ 检测到的 Node/npm 环境，
 * core 不直接 spawn 裸进程，便于测试时替换为 Fake。
 */
interface CommandExecutor {
    fun exec(command: List<String>, workingDir: File, env: Map<String, String> = emptyMap()): CommandResult
}

/** 真实实现：用检测到的 node/npm 路径执行。plugin 模块提供并注入。 */
class ProcessCommandExecutor : CommandExecutor {
    override fun exec(command: List<String>, workingDir: File, env: Map<String, String>): CommandResult {
        val pb = ProcessBuilder(command)
            .directory(workingDir)
            .redirectErrorStream(false)
        pb.environment().putAll(env)
        val proc = pb.start()
        val stdout = proc.inputStream.bufferedReader().readText()
        val stderr = proc.errorStream.bufferedReader().readText()
        val code = proc.waitFor()
        return CommandResult(code, stdout, stderr)
    }
}
