plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // JSON 解析（lockfile / registry 响应）；kotlinx-serialization 平台不自带，需打包。
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    // kotlinx-coroutines-core 由 IntelliJ 平台在运行时提供，无需打包进插件：
    // 用 compileOnly 仅供编译，test 仍需要 testImplementation 才能跑。
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    // 轻量 semver（范围判断 + 主/次/补丁分级）；平台不自带，需打包。
    implementation("com.vdurmont:semver4j:3.1.0")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
