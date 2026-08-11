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
    // resvg_bridge 通过 JNI 直接调用（System.load + external fun），不依赖 JNA：
    // IntelliJ Gradle 插件会把含有 com/sun/jna 的 jar 当作“平台已提供”而剔除出编译/运行
    // classpath，导致 JNA 无法使用。JNI 方式无任何额外 Java 依赖。
}

tasks.patchPluginXml {
    sinceBuild.set("232")
    untilBuild.set("241.*")
}

// 本地调试：./gradlew :plugin:runIde
tasks.runIde { }

// ---- resvg_bridge 原生库构建（可选） ----
// 默认：使用已提交到 plugin/src/main/resources/native/<os>/ 的预编译库，无需 cargo。
// 仅当你显式传入 -PresvgBuild 且本机有 cargo 时，才重新编译并把产物拷入资源目录。
val nativeOsDir = when {
    System.getProperty("os.name").contains("Windows", ignoreCase = true) -> "windows-x64"
    System.getProperty("os.name").contains("Mac", ignoreCase = true) -> "darwin-arm64"
    else -> "linux-x64"
}
val resvgBuildRequested = providers.gradleProperty("resvgBuild").isPresent

val cargoPath = System.getenv("CARGO_HOME")?.let { "$it/bin/cargo" } ?: "cargo"

tasks.register("buildResvgBridge", Exec::class) {
    group = "native"
    description = "用 cargo 编译 resvg_bridge cdylib（需 -PresvgBuild 与 cargo）"
    workingDir = file("../native/resvg_bridge")
    commandLine(cargoPath, "build", "--release")
    onlyIf { resvgBuildRequested }
}

tasks.register("syncResvgLib", Copy::class) {
    group = "native"
    description = "把 cargo 编译出的原生库拷入插件资源目录"
    dependsOn("buildResvgBridge")
    from("../native/resvg_bridge/target/release") {
        include("resvg_bridge.dll", "libresvg_bridge.so", "libresvg_bridge.dylib")
    }
    into("src/main/resources/native/$nativeOsDir")
    onlyIf { resvgBuildRequested }
}

tasks.named("processResources") { dependsOn("syncResvgLib") }
