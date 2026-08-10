package depslens.core.model

import com.vdurmont.semver4j.Requirement
import com.vdurmont.semver4j.Semver

/** 升级幅度分级，用于工具窗口着色（MAJOR=红 / MINOR=橙 / PATCH=绿）。 */
enum class BumpLevel { MAJOR, MINOR, PATCH, NONE }

/**
 * package.json 中声明的版本范围，如 "^1.2.0"、"~2.0"、">=3.0 <4"。
 * 解析与比较委托给 semver4j，这里仅做轻量包装。
 */
data class VersionRange(
    val raw: String,
    val dependencyName: String,
) {
    private val requirement: Requirement? by lazy {
        runCatching { Requirement.buildNPM(raw) }.getOrNull()
    }

    /** 给定版本是否满足此范围。 */
    fun satisfies(version: String): Boolean {
        val semver = runCatching { Semver(version) }.getOrNull() ?: return false
        return requirement?.isSatisfiedBy(semver) ?: false
    }

    companion object {
        /** current -> latest 的升级幅度（按 semver 主/次/补丁分级）。 */
        fun bumpLevel(current: String, latest: String): BumpLevel {
            val c = runCatching { Semver(current) }.getOrNull() ?: return BumpLevel.NONE
            val l = runCatching { Semver(latest) }.getOrNull() ?: return BumpLevel.NONE
            return when {
                l.major > c.major -> BumpLevel.MAJOR
                l.major == c.major && l.minor > c.minor -> BumpLevel.MINOR
                l.major == c.major && l.minor == c.minor && l.patch > c.patch -> BumpLevel.PATCH
                else -> BumpLevel.NONE
            }
        }
    }
}
