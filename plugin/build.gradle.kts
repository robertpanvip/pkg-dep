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
    // 协程由 IntelliJ Platform 自带（kotlinx-coroutines-core），无需显式声明；
    // EDT 调度改用 ApplicationManager.invokeLater，避免引入 kotlinx-coroutines-swing 的版本错配。
    implementation(project(":core"))
}

tasks.patchPluginXml {
    sinceBuild.set("232")
    untilBuild.set("241.*")
}

// 本地调试：./gradlew :plugin:runIde
tasks.runIde { }
