// The code in this file is a convention plugin - a Gradle mechanism for sharing reusable build logic.
// `buildSrc` is a Gradle-recognized directory and every plugin there will be easily available in the rest of the build.
package buildsrc.convention

import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    // Apply the Kotlin JVM plugin to add support for Kotlin in JVM projects.
    kotlin("jvm")
    // Static analysis gate (MUD-010): new smells hard-fail; legacy soft via shared baseline.
    id("io.gitlab.arturbosch.detekt")
}

kotlin {
    // Use a specific Java version to make it easier to work in different environments.
    jvmToolchain(17)
}

// Detekt: default rule set, no type-resolution on fast path, shared root baseline.
// Config + baseline live under repo root config/detekt/ (see docs/DETEKT.md).
extensions.configure<DetektExtension>("detekt") {
    buildUponDefaultConfig = true
    allRules = false
    parallel = true
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    // Shared root baseline by default. -PdetektModuleBaseline=true writes per-module
    // files under config/detekt/module-baselines/ for merge into baseline.xml (regen).
    baseline = if (project.hasProperty("detektModuleBaseline")) {
        rootProject.file("config/detekt/module-baselines/${project.name}.xml")
    } else {
        rootProject.file("config/detekt/baseline.xml")
    }
    // Main sources only (default plugin source sets); keep default/fast honest without type-res.
    source.setFrom(
        files(
            "src/main/java",
            "src/main/kotlin",
        )
    )
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "17"
    // Exclude build/generated noise if present (Compose client etc.).
    exclude("**/build/**")
    exclude("**/generated/**")
    reports {
        html.required.set(false)
        xml.required.set(false)
        txt.required.set(false)
        sarif.required.set(false)
        md.required.set(false)
    }
}

tasks.withType<Test>().configureEach {
    // JUnit Platform: default excludes @Tag("quarantine") so green lanes stay honest.
    // Override: -Pmud.quarantineOnly=true → run only quarantine-tagged tests (debt lane).
    // Optional: -Pmud.includeQuarantine=true → run all tests including quarantine.
    val quarantineOnly = project.findProperty("mud.quarantineOnly")?.toString() == "true"
    val includeQuarantine = project.findProperty("mud.includeQuarantine")?.toString() == "true"
    useJUnitPlatform {
        when {
            quarantineOnly -> includeTags("quarantine")
            includeQuarantine -> { /* no tag filter */ }
            else -> excludeTags("quarantine")
        }
    }

    // Log information about all test results, not only the failed ones.
    testLogging {
        events(
            TestLogEvent.FAILED,
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED,
            TestLogEvent.STANDARD_OUT,
            TestLogEvent.STANDARD_ERROR
        )
        showStandardStreams = true
    }
}
