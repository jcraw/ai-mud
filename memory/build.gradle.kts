plugins {
    id("buildsrc.convention.kotlin-jvm")
    // MUD-014: PIT pure-module mutation (not on kotlin-jvm — would mutate-the-world).
    id("buildsrc.convention.pitest-pure")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":llm"))
    implementation(libs.bundles.kotlinxEcosystem)
    implementation(libs.sqliteJdbc)
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}