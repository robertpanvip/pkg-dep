//! resvg_bridge —— DepsLens 的依赖图渲染后端。
//!
//! 通过 JNI 暴露 `Java_depslens_plugin_resvg_ResvgBridge_renderNative`，把一段 UTF-8
//! SVG 文本光栅化成 PNG 字节（`jbyteArray`），由 JVM 侧直接 `System.load` 后调用，
//! 不再依赖 JNA（IntelliJ Gradle 插件会把含 `com/sun/jna` 的 jar 当“平台已提供”剔除）。
//! 底层用 usvg 解析 SVG、resvg 渲染，字体库加载系统字体（Windows 上含 Segoe UI /
//! Consolas 等），因此 SVG 中的 `font-family="sans-serif"` 能正确渲染出节点标签。
//!
//! 同时保留 C-ABI 的 `svg_render_png_bytes` / `svg_free_bytes` 作为可替代的调用约定。

use std::os::raw::c_int;
use std::slice;

use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::jbyteArray;

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

/// JNI 入口：对应 Java 侧 `depslens.plugin.resvg.ResvgBridge.renderNative(String)`。
///
/// 直接返回 `jbyteArray`（PNG 字节），由 JVM 的 GC 负责回收，无需调用方手动释放。
/// 任一失败返回 `null`（JVM 侧据此返回 `null` 并展示错误）。
///
/// # 安全
/// 这是 JNI 约定的 `extern "system"` 导出函数，由 JVM 在 `System.load` 后通过符号名调用。
#[no_mangle]
pub extern "system" fn Java_depslens_plugin_resvg_ResvgBridge_renderNative(
    mut env: JNIEnv,
    _class: JClass,
    svg: JString,
) -> jbyteArray {
    let s = match env.get_string(&svg) {
        Ok(j) => j.to_string_lossy().into_owned(),
        Err(_) => return std::ptr::null_mut(),
    };
    match render_png(s.as_bytes()) {
        Ok(png) => match env.byte_array_from_slice(&png) {
            Ok(arr) => arr.into_raw(),
            Err(_) => std::ptr::null_mut(),
        },
        Err(_) => std::ptr::null_mut(),
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
    opt.fontdb = std::sync::Arc::new(fontdb);

    let tree = usvg::Tree::from_data(svg_data, &opt)?;

    // 2x 超采样，保证在 HiDPI（Retina）屏幕上清晰；JVM 侧按逻辑尺寸缩放显示。
    let scale = 2.0_f32;
    let transform = usvg::Transform::from_scale(scale, scale);

    // 背景透明，深色底由 SVG 自身的 <rect> 提供。
    let size = tree.size();
    let w = (size.width() * scale) as u32;
    let h = (size.height() * scale) as u32;
    let mut buf = vec![0u8; (w * h * 4) as usize];
    let mut pixmap = resvg::tiny_skia::PixmapMut::from_bytes(&mut buf, w, h)
        .ok_or("无法创建像素缓冲")?;
    resvg::render(&tree, transform, &mut pixmap);
    let png = pixmap.as_ref().encode_png()?;
    Ok(png)
}

#[cfg(test)]
mod tests {
    use super::render_png;

    #[test]
    fn smoke_render() {
        let svg = r##"<svg xmlns="http://www.w3.org/2000/svg" width="220" height="140">
            <rect width="220" height="140" fill="#1e1e1e"/>
            <circle cx="50" cy="70" r="18" fill="#4a9eff"/>
            <text x="78" y="75" fill="#ccc" font-family="sans-serif" font-size="14">react</text>
            <line x1="50" y1="70" x2="170" y2="70" stroke="#555" stroke-width="1"/>
            <circle cx="170" cy="70" r="12" fill="#7d8590"/>
        </svg>"##;
        let png = render_png(svg.as_bytes()).expect("render failed");
        assert!(png.len() > 100, "png too small");
        std::fs::write("target/render_test.png", &png).unwrap();
    }
}
