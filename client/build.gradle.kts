import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0"
}

dependencies {
    implementation(project(":core"))
    implementation(project(":perception"))
    implementation(project(":reasoning"))
    implementation(project(":memory"))
    implementation(project(":llm"))
    implementation(project(":utils"))

    implementation(project(":action"))
    implementation(libs.bundles.kotlinxEcosystem)

    // Compose Desktop
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

compose.desktop {
    application {
        mainClass = "com.jcraw.mud.client.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "AI-MUD Client"
            packageVersion = "1.0.0"
        }
    }
}
