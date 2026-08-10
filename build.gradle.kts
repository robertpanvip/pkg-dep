plugins {
    kotlin("jvm") version "1.9.24" apply false
    id("org.jetbrains.intellij") version "1.17.4" apply false
}

allprojects {
    group = "depslens"
    version = "0.1.0"
}

subprojects {
    repositories {
        mavenCentral()
    }
}
