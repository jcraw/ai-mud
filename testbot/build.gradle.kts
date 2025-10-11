plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
    application
}

application {
    mainClass.set("com.jcraw.mud.testbot.TestBotMainKt")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":llm"))
    implementation(project(":perception"))
    implementation(project(":reasoning"))
    implementation(project(":action"))
    implementation(project(":memory"))
    implementation(libs.bundles.kotlinxEcosystem)

    testImplementation(kotlin("test"))
}
