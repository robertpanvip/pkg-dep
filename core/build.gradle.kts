plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // JSON 解析（lockfile / registry 响应）
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    // 并行查询 registry
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    // 轻量 semver（范围判断 + 主/次/补丁分级）
    implementation("com.vdurmont:semver4j:3.1.0")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
