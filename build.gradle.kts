plugins {
    kotlin("jvm") version "2.1.10" apply false
    // 注意：2025.1 + Kotlin 2.0+ 需用「IntelliJ Platform Gradle Plugin」(org.jetbrains.intellij.platform)，
    // 旧的 org.jetbrains.intellij 在 1.17.4 即停止，没有 2.x；2.x 是另一个插件 id。
    id("org.jetbrains.intellij.platform") version "2.18.1" apply false
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
