package depslens.plugin.svg

import com.intellij.ui.svg.loadSvg
import java.awt.image.BufferedImage

/**
 * 依赖图 SVG 光栅化：直接调用 IntelliJ 平台底层的 SVG 渲染引擎
 * `com.intellij.ui.svg.loadSvg`（SVGLoader 内部委托的 JSVG 实现），
 * 不打包任何第三方 SVG 库（无 batik-all / resvg / Rust / JNI）。
 *
 * compile SDK 已对齐到 2025.1（sinceBuild=251），无需兼容旧版（旧版 Batik 时代没有
 * com.intellij.ui.svg 包），因此这里直接用底层 loadSvg 静态调用，去掉了反射兼容层。
 * loadSvg 被 @ApiStatus.Internal 标记，Plugin Verifier 会给出一条 warning，但不影响产物与运行。
 *
 * 失败返回 null，由调用方降级显示提示。
 */
object SvgRasterizer {
    private const val SCALE = 2f

    fun render(svg: String): BufferedImage? = runCatching {
        val bytes = svg.toByteArray(Charsets.UTF_8)
        // loadSvg(data: ByteArray, scale: Float) 是底层 JSVG 实现暴露的稳定过载，返回 BufferedImage。
        loadSvg(bytes, SCALE) as? BufferedImage
    }.getOrNull()
}
