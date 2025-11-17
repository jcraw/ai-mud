plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(project(":config"))
    implementation(project(":core"))
    implementation(project(":llm"))
    implementation(project(":memory"))
    implementation(libs.bundles.kotlinxEcosystem)
    testImplementation(kotlin("test"))
}