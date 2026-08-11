package depslens.plugin.resvg

import com.intellij.openapi.util.SystemInfo
import java.io.File

/**
 * 加载 resvg_bridge 原生库，并把 SVG 字符串渲染成 PNG 字节。
 *
 * 原生库按平台放在 classpath 资源 `/native/<os>/` 下：
 *   windows-x64/resvg_bridge.dll
 *   linux-x64/libresvg_bridge.so
 *   darwin-arm64/libresvg_bridge.dylib
 * 运行时从 jar 资源提取到临时文件，`System.load` 后由 JVM 通过 JNI 调用
 * [renderNative]（对应 Rust 导出的 `Java_depslens_plugin_resvg_ResvgBridge_renderNative`）。
 *
 * 注意：这里不使用 JNA。IntelliJ Gradle 插件会把含有 `com/sun/jna` 的 jar 当作
 * “平台已提供”而从编译/运行 classpath 中剔除，导致 JNA 无法使用；直接用 JNI 更稳。
 *
 * 线程安全：加载仅发生一次（双重检查锁）。渲染本身无共享可变状态，可并发调用。
 */
object ResvgBridge {
    @Volatile
    private var loaded = false

    /** 渲染 SVG 为 PNG 字节；任何失败（缺原生库、SVG 非法等）返回 null。 */
    fun render(svg: String): ByteArray? {
        if (!ensureLoaded()) return null
        return runCatching { renderNative(svg) }.getOrNull()
    }

    @Synchronized
    private fun ensureLoaded(): Boolean {
        if (loaded) return true
        val resPath = "/native/${osDir()}/${libName()}"
        val stream = ResvgBridge::class.java.getResourceAsStream(resPath) ?: return false
        val tmp = File.createTempFile("resvg_bridge", libExt()).apply { deleteOnExit() }
        stream.use { input -> tmp.outputStream().use { out -> input.copyTo(out) } }
        val ok = runCatching { System.load(tmp.absolutePath) }
            .onFailure { it.printStackTrace() }
            .isSuccess
        if (ok) loaded = true
        return ok
    }

    /** JNI 入口：传 SVG 文本，返回 PNG 字节（失败返回 null）。 */
    external fun renderNative(svg: String): ByteArray?

    private fun osDir(): String = when {
        SystemInfo.isWindows -> "windows-x64"
        SystemInfo.isMac -> "darwin-arm64"
        SystemInfo.isLinux -> "linux-x64"
        else -> "linux-x64"
    }

    private fun libExt(): String = when {
        SystemInfo.isWindows -> ".dll"
        SystemInfo.isMac -> ".dylib"
        else -> ".so"
    }

    private fun libName(): String = "resvg_bridge" + libExt()
}
