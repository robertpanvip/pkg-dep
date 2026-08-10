package depslens.core.model

/**
 * 一个具体解析出的包版本节点。
 *
 * @param name   包名，如 "react"
 * @param version 已解析版本，如 "18.2.0"
 * @param kind   在 lockfile 中的来源类型
 */
data class PackageRef(
    val name: String,
    val version: String,
    val kind: DepKind = DepKind.PROD,
) {
    /** 图内唯一键：name@version */
    val id: String get() = "$name@$version"
}

enum class DepKind { PROD, DEV, PEER, OPTIONAL }
