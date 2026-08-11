plugins {
    id("buildsrc.convention.kotlin-jvm")
    // MUD-014: PIT pure-module mutation (not on kotlin-jvm — would mutate-the-world).
    id("buildsrc.convention.pitest-pure")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(project(":config"))
    implementation(libs.bundles.kotlinxEcosystem)
    testImplementation(kotlin("test"))
    testImplementation(libs.konsist)
    // MUD-015: Kotest property checks (pure hot paths only)
    testImplementation(libs.kotestProperty)
}