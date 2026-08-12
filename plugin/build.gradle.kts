plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij.platform")
}

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
    // IntelliJ Platform Gradle Plugin 所需的 JetBrains / Marketplace 等仓库
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation(project(":core"))

    intellijPlatform {
        // 前端项目用 Ultimate（自带 NodeJS 支持）；社区版(IC)无 Node 检测。
        // intellijIdeaUltimate 为遗留辅助函数，适用于 2025.3 之前的版本（2025.1 可用）。
        intellijIdeaUltimate("2025.1")

        // JSON 是 2024.3 起独立的 bundled 插件：<depends>com.intellij.modules.json</depends>
        // 只管运行时，编译期必须显式声明，否则 com.intellij.json.psi.* 无法解析。
        bundledPlugin("com.intellij.modules.json")

        // instrumentationTools() 在 2.x 已移除，默认由 javaCompiler() 处理字节码插桩；
        // pluginVerifier() 默认已添加（verifyPlugin 任务可用），无需显式声明。
    }
}

// IDE 版本兼容：最低 251（2025.1），与编译平台一致。
// untilBuild = provider { null } 表示开放上限，可装在当前及未来 IDE 上。
intellijPlatform {
    pluginConfiguration {
        version = providers.gradleProperty("version").getOrElse("0.1.0")
        ideaVersion {
            sinceBuild = "251"
            untilBuild = provider { null }
        }
    }
}

// 产物 zip：pkg-dep-<version>.zip（默认用子项目名 plugin）
tasks {
    buildPlugin {
        archiveBaseName = "pkg-dep"
    }
    // 本地调试：./gradlew :plugin:runIde
    runIde {}
}
