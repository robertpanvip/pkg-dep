plugins {
    kotlin("jvm") version "1.9.24" apply false
    id("org.jetbrains.intellij") version "1.17.4" apply false
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
