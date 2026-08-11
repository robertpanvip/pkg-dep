plugins {
    kotlin("jvm") version "2.1.10" apply false
    id("org.jetbrains.intellij") version "2.16.0" apply false
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
