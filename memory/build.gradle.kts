plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":llm"))
    implementation(libs.bundles.kotlinxEcosystem)
    testImplementation(kotlin("test"))
}