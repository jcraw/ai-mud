plugins {
    // The Kotlin DSL plugin provides a convenient way to develop convention plugins.
    // Convention plugins are located in `src/main/kotlin`, with the file extension `.gradle.kts`,
    // and are applied in the project's `build.gradle.kts` files as required.
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Add a dependency on the Kotlin Gradle plugin, so that convention plugins can apply it.
    implementation(libs.kotlinGradlePlugin)
    // Detekt static analysis (MUD-010) — applied from kotlin-jvm convention.
    implementation(libs.detektGradlePlugin)
    // PIT mutation (MUD-014) — pure modules only via pitest-pure convention (not kotlin-jvm).
    implementation(libs.pitestGradlePlugin)
}
