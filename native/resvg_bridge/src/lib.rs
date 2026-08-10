//! resvg_bridge —— DepsLens 的依赖图渲染后端。
//!
//! 通过 C-ABI 暴露 `svg_render_png_bytes`，把一段 UTF-8 SVG 文本光栅化成 PNG 字节，
//! 由 JVM 侧（JNA 5.14.0）调用。底层用 usvg 解析 SVG、resvg 渲染，
//! 字体库加载系统字体（Windows 上含 Segoe UI / Consolas 等），因此 SVG 中的
//! `font-family="sans-serif"` 能正确渲染出节点标签。

use std::os::raw::c_int;
use std::ptr;
use std::slice;

/// 把 SVG 文本渲染成 PNG 字节。
///
/// # 参数
/// * `svg`      —— SVG 内容的 UTF-8 字节指针（长度 `svg_len`）。
/// * `svg_len`  —— SVG 字节长度。
/// * `out`      —— 输出：成功时写入一块由本库分配、调用方须用 `svg_free_bytes` 释放的缓冲区指针。
/// * `out_len`  —— 输出：缓冲区字节长度。
///
/// # 返回值
/// `0` 成功；`-1` 参数非法；`-2` 渲染失败（SVG 解析错误或字体不可用等）。
///
/// # 安全
/// 调用方须保证 `svg` 指向至少 `svg_len` 字节的有效内存，且 `out`/`out_len` 非空。
#[no_mangle]
pub extern "C" fn svg_render_png_bytes(
    svg: *const u8,
    svg_len: c_int,
    out: *mut *mut u8,
    out_len: *mut u64,
) -> c_int {
    if svg.is_null() || out.is_null() || out_len.is_null() {
        return -1;
    }
    let svg_len = svg_len as usize;
    let svg_data = unsafe { slice::from_raw_parts(svg, svg_len) };
    match render_png(svg_data) {
        Ok(png) => {
            // 把 Vec<u8> 拆成原始指针交出去，所有权转移给调用方。
            let mut buf = png;
            let ptr = buf.as_mut_ptr();
            let len = buf.len();
            std::mem::forget(buf); // 不再由 Rust 自动释放
            unsafe {
                *out = ptr;
                *out_len = len as u64;
            }
            0
        }
        Err(_) => -2,
    }
}

/// 释放 [`svg_render_png_bytes`] 分配的缓冲区。
///
/// # 安全
/// `ptr` 必须来自一次成功的 [`svg_render_png_bytes`] 调用，`len` 必须是当时返回的 `out_len`。
#[no_mangle]
pub extern "C" fn svg_free_bytes(ptr: *mut u8, len: u64) {
    if ptr.is_null() {
        return;
    }
    unsafe {
        // 用相同的 len/cap 重建 Vec，drop 时归还内存。
        let _ = Vec::from_raw_parts(ptr, len as usize, len as usize);
    }
}

/// 内部渲染实现：SVG -> usvg 树 -> resvg 光栅化 -> PNG 编码。
fn render_png(svg_data: &[u8]) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
    use resvg::usvg;

    // 字体库：加载系统字体，并指定通用族映射，保证 SVG 文本可渲染。
    let mut fontdb = usvg::fontdb::Database::new();
    fontdb.load_system_fonts();
    #[cfg(target_os = "windows")]
    {
        fontdb.set_sans_serif_family("Segoe UI");
        fontdb.set_serif_family("Times New Roman");
        fontdb.set_monospace_family("Consolas");
    }
    #[cfg(not(target_os = "windows"))]
    {
        fontdb.set_sans_serif_family("DejaVu Sans");
        fontdb.set_serif_family("DejaVu Serif");
        fontdb.set_monospace_family("DejaVu Sans Mono");
    }

    let mut opt = usvg::Options::default();
    opt.fontdb = fontdb;

    let tree = usvg::Tree::from_data(svg_data, &opt)?;

    // 2x 超采样，保证在 HiDPI（Retina）屏幕上清晰；JVM 侧按逻辑尺寸缩放显示。
    let scale = 2.0_f32;
    let transform = usvg::Transform::from_scale(scale, scale);

    // 背景透明，深色底由 SVG 自身的 <rect> 提供。
    let pixmap = resvg::render(&tree, transform, None).ok_or("resvg render failed")?;
    let png = pixmap.encode_png()?;
    Ok(png)
}
