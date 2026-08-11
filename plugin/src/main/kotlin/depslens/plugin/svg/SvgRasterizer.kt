package depslens.plugin.svg

import org.apache.batik.transcoder.TranscoderInput
import org.apache.batik.transcoder.TranscoderOutput
import org.apache.batik.transcoder.image.ImageTranscoder
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream

/**
 * 把依赖图的 SVG 字符串光栅化为 BufferedImage（纯 Java，使用 Apache Batik）。
 *
 * 替代原来的 Rust resvg + JNI 方案：无原生代码、无跨平台原生库、CI 无需 Rust 工具链。
 * 输出尺寸按 SCALE 超采样，由 ResvgImagePanel 缩绘到逻辑尺寸，保证在 Retina 上清晰。
 *
 * 失败（Batik 缺失或 SVG 非法）返回 null，由调用方降级显示提示。
 */
object SvgRasterizer {
    private const val WIDTH = 960
    private const val HEIGHT = 680
    private const val SCALE = 2

    fun render(svg: String): BufferedImage? = runCatching {
        val holder = arrayOfNulls<BufferedImage>(1)
        val transcoder = object : ImageTranscoder() {
            override fun createImage(w: Int, h: Int): BufferedImage =
                BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)

            override fun writeImage(image: BufferedImage, output: TranscoderOutput?) {
                holder[0] = image
            }
        }
        transcoder.addTranscodingHint(ImageTranscoder.KEY_WIDTH, (WIDTH * SCALE).toFloat())
        transcoder.addTranscodingHint(ImageTranscoder.KEY_HEIGHT, (HEIGHT * SCALE).toFloat())
        val input = TranscoderInput(ByteArrayInputStream(svg.toByteArray(Charsets.UTF_8)))
        transcoder.transcode(input, TranscoderOutput(null))
        holder[0]
    }.getOrNull()
}
