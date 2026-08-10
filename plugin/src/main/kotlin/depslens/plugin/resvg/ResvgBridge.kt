package depslens.plugin.resvg

import com.intellij.util.SystemInfo
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.PointerByReference
import com.sun.jna.ptr.LongByReference
import java.io.File

/**
 * 加载 resvg_bridge 原生库，并把 SVG 字符串渲染成 PNG 字节。
 *
 * 原生库按平台放在 classpath 资源 `/native/<os>/` 下：
 *   windows-x64/resvg_bridge.dll
 *   linux-x64/libresvg_bridge.so
 *   darwin-arm64/libresvg_bridge.dylib
 * 运行时从 jar 资源提取到临时文件，`System.load` 后再用 JNA 绑定。
 *
 * 线程安全：加载仅发生一次（双重检查锁）。渲染本身无共享可变状态，可并发调用。
 */
object ResvgBridge {
    @Volatile
    private var lib: ResvgLib? = null

    /** 渲染 SVG 为 PNG 字节；任何失败（缺原生库、SVG 非法等）返回 null。 */
    fun render(svg: String): ByteArray? {
        val l = ensureLoaded() ?: return null
        val bytes = svg.toByteArray(Charsets.UTF_8)
        val out = PointerByReference()
        val outLen = LongByReference()
        val rc = runCatching { l.svg_render_png_bytes(bytes, bytes.size, out, outLen) }
            .getOrElse { return null }
        if (rc != 0) return null
        val ptr: Pointer = out.value ?: return null
        val len = outLen.value.toInt()
        return try {
            ptr.getByteArray(0, len)
        } finally {
            runCatching { l.svg_free_bytes(ptr, outLen.value) }
        }
    }

    @Synchronized
    private fun ensureLoaded(): ResvgLib? {
        lib?.let { return it }
        val loaded = runCatching {
            val resPath = "/native/${osDir()}/${libName()}"
            val stream = ResvgBridge::class.java.getResourceAsStream(resPath)
                ?: throw IllegalStateException("找不到原生库资源: $resPath")
            val tmp = File.createTempFile("resvg_bridge", libExt()).apply { deleteOnExit() }
            stream.use { input -> tmp.outputStream().use { out -> input.copyTo(out) } }
            System.load(tmp.absolutePath)
            Native.load("resvg_bridge", ResvgLib::class.java)
        }.onFailure { it.printStackTrace() }.getOrNull()
        lib = loaded
        return loaded
    }

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

    private fun libName(): String = "resvg_bridge$libExt()"
}
