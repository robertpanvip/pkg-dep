package depslens.plugin.resvg

import com.sun.jna.Library
import com.sun.jna.Pointer
import com.sun.jna.PointerByReference
import com.sun.jna.ptr.LongByReference

/**
 * resvg_bridge 的 C-ABI 映射（对应 native/resvg_bridge/src/lib.rs）。
 *
 * - `svg_render_png_bytes`：把 SVG UTF-8 字节光栅化成 PNG；成功时通过 `out` 返回一个
 *   由本库分配、调用方须用 `svg_free_bytes` 释放的缓冲区指针，`outLen` 为其长度。
 * - 长度统一用 64 位（JNA 的 `LongByReference` 对应 C `uint64_t*`），避免 Windows 上
 *   `long` 为 32 位导致 `size_t` 错位。
 */
internal interface ResvgLib : Library {
    fun svg_render_png_bytes(
        svg: ByteArray,
        svgLen: Int,
        out: PointerByReference,
        outLen: LongByReference,
    ): Int

    fun svg_free_bytes(ptr: Pointer, len: Long)
}
