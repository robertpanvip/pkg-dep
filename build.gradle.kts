plugins {
    kotlin("jvm") version "2.1.10" apply false
    // 注意：2025.1 + Kotlin 2.0+ 需用「IntelliJ Platform Gradle Plugin」(org.jetbrains.intellij.platform)，
    // 旧的 org.jetbrains.intellij 在 1.17.4 即停止，没有 2.x；2.x 是另一个插件 id。
    // 版本钉在 2.11.0：这是最后一个仍支持 Gradle 8.x 的版本；2.12.0+ 强制要求 Gradle 9.0.0。
    // 本项目 wrapper 是 Gradle 8.13，故不升 2.12+（否则要连 Gradle/Kotlin 一起升，风险更大）。
    id("org.jetbrains.intellij.platform") version "2.11.0" apply false
}

allprojects {
    group = "depslens"
    // 默认 0.1.0；release 工作流通过 -Pversion=<tag> 覆盖，使产物版本与 tag 一致
    version = providers.gradleProperty("version").getOrElse("0.1.0")
}

subprojects {
    repositories {
        mavenCentral()
    }
}
