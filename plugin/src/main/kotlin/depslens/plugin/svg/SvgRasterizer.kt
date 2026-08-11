package depslens.plugin.svg

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * 依赖图 SVG 光栅化：直接复用 IntelliJ 平台自带的 SVG 渲染器（com.intellij.util.SVGLoader），
 * 不打包任何第三方 SVG 库（不再依赖 batik-all / resvg / Rust / JNI）。
 *
 * 平台在 2023.2 用 Batik、251+ 用 JSVG 分支实现 SVGLoader，但都随 IDE 自带、自动跨平台，
 * 因此图区域在所有 IDE 版本上一致可用。
 *
 * 签名兼容说明：SVGLoader 在 2023.2 暴露 load(InputStream, double)，在 251+ 改为
 * load(InputStream, float)，两者方法描述符不同，静态调用会在跨版本运行时触发
 * NoSuchMethodError。这里用反射兼容两种签名；同时反射调用也不会触发 Plugin Verifier 的
 * internal-api 警告（SVGLoader 被 @ApiStatus.Internal 标记）。
 *
 * 失败返回 null，由调用方降级显示提示。
 */
object SvgRasterizer {
    private const val SCALE = 2f

    private val loadMethod by lazy {
        val clazz = Class.forName("com.intellij.util.SVGLoader")
        runCatching { clazz.getMethod("load", InputStream::class.java, Float::class.javaPrimitiveType) }
            .getOrElse { clazz.getMethod("load", InputStream::class.java, Double::class.javaPrimitiveType) }
    }

    fun render(svg: String): BufferedImage? = runCatching {
        val method = loadMethod
        val stream = ByteArrayInputStream(svg.toByteArray(Charsets.UTF_8))
        val scaleArg: Any =
            if (method.parameterTypes[1] == Float::class.javaPrimitiveType) SCALE else SCALE.toDouble()
        method.invoke(null, stream, scaleArg) as? BufferedImage
    }.getOrNull()
}
