plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":llm"))
    implementation(libs.bundles.kotlinxEcosystem)
    implementation(libs.sqliteJdbc)
    testImplementation(kotlin("test"))
}