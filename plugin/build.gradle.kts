plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij")
}

kotlin {
    jvmToolchain(17)
}

// IntelliJ Platform Gradle Plugin 1.x：用 intellij {} 配置。
intellij {
    // 前端项目一般用 Ultimate（自带 NodeJS 支持）；社区版(IC)无 Node 检测。
    version.set("2023.2")
    type.set("IU")
    // 如需显式依赖 Node 能力，可加：plugins.set(listOf("JavaScript"))
}

dependencies {
    implementation(project(":core"))
    // 依赖图 SVG 由 Apache Batik（纯 Java）光栅化为 BufferedImage：无原生代码、无跨平台原生库、CI 无需 Rust。
    implementation("org.apache.xmlgraphics:batik-all:1.18")
}

// IDE 版本兼容：最低 251（2025.1）。untilBuild 设为空字符串，使插件对当前及未来 IDE 版本开放兼容
// （IntelliJ Gradle 插件默认会把 untilBuild 填成编译平台版本 232.*，反而把 WebStorm 2026.2/WS-262 挡在门外，故显式清空）。
tasks.patchPluginXml {
    sinceBuild.set("251")
    untilBuild.set("")
}

// 产物 zip 默认用子项目名 plugin，改为 pkg-dep（与仓库名一致）：pkg-dep-<version>.zip
tasks.buildPlugin {
    archiveBaseName.set("pkg-dep")
}

// 本地调试：./gradlew :plugin:runIde
tasks.runIde { }

// 无需原生构建步骤：依赖图 SVG 由 Apache Batik（纯 Java）光栅化，无 Rust / JNI / 跨平台原生库。
