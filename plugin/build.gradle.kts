plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij")
}

kotlin {
    jvmToolchain(17)
}

// IntelliJ Platform Gradle Plugin 2.x：用 intellij {} 配置目标平台。
intellij {
    // 前端项目一般用 Ultimate（自带 NodeJS 支持）；社区版(IC)无 Node 检测。
    version = "2025.1"
    type = "IU"
}

dependencies {
    implementation(project(":core"))
    // 依赖图 SVG 直接调用 IntelliJ 平台底层的 SVG 渲染引擎（com.intellij.ui.svg.loadSvg，
    // 即 SVGLoader 内部委托的 JSVG 实现），不打包任何第三方 SVG 库，无原生代码、无跨平台原生库。
}

// IDE 版本兼容：最低 251（2025.1），与编译平台一致（251 起平台强制 Kotlin 2.0+ 与 Gradle 插件 2.x）。
// untilBuild 留空表示对更高版本开放兼容（243+ 起 Gradle 插件会忽略 until-build，插件可装在当前及未来 IDE 上）。
patchPluginXml {
    sinceBuild = "251"
    untilBuild = ""
}

// 产物 zip 默认用子项目名 plugin，改为 pkg-dep（与仓库名一致）：pkg-dep-<version>.zip
buildPlugin {
    archiveBaseName = "pkg-dep"
}

// 本地调试：./gradlew :plugin:runIde
runIde { }
