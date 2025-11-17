plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(project(":config"))
    implementation(libs.bundles.kotlinxEcosystem)
    testImplementation(kotlin("test"))
}