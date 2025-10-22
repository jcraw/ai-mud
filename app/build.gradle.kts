import java.util.Properties
import java.io.FileInputStream

plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")

    // Apply the Application plugin to add support for building an executable JVM application.
    application
}

// Read API key from local.properties
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    FileInputStream(localPropertiesFile).use { localProperties.load(it) }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":perception"))
    implementation(project(":reasoning"))
    implementation(project(":memory"))
    implementation(project(":action"))
    implementation(project(":llm"))
    implementation(project(":utils"))
    implementation(libs.bundles.kotlinxEcosystem)
    testImplementation(kotlin("test"))
    testImplementation(project(":testbot"))
}

application {
    // Define the Fully Qualified Name for the application main class
    // (Note that Kotlin compiles `App.kt` to a class with FQN `com.example.app.AppKt`.)
    mainClass = "com.jcraw.app.AppKt"

    // Pass OpenAI API key from local.properties to the application
    applicationDefaultJvmArgs = listOf(
        "-Dopenai.api.key=${localProperties.getProperty("openai.api.key", "")}"
    )
}
