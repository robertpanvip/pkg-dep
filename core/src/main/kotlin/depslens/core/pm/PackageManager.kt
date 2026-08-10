package depslens.core.pm

import depslens.core.exec.CommandExecutor
import depslens.core.model.DependencyGraph
import depslens.core.registry.RegistryClient
import java.io.File

/** 包管理器种类，对应三种实现。 */
enum class PmKind { NPM, YARN, PNPM }

/**
 * 统一包管理器抽象。npm / yarn / pnpm 各实现一套，
 * 差异只在 lockfile 格式与升级命令；上层 UI 与影响分析完全面向接口。
 */
interface PackageManager {
    val kind: PmKind

    /** 从 lockfile + package.json 构建已解析依赖图。 */
    fun parse(projectDir: File): DependencyGraph

    /** 查询某包最新版本（dist-tag latest）。 */
    suspend fun latestVersion(name: String): String?

    /** 升级 pkg@version 的 dry-run 解析，返回影响集合原始输出（JSON 文本）。 */
    fun resolveDryRun(projectDir: File, pkg: String, version: String): String

    /** 生成“假设升级后”的 lockfile 文本（yarn/pnpm 影响分析用；npm 作为兜底）。 */
    fun resolveDryRunLockfile(projectDir: File, pkg: String, version: String): String?

    /** 真实升级并写回 lockfile（异步 + 可取消由调用方保证）。 */
    fun applyUpgrade(projectDir: File, pkg: String, version: String)
}
