// Thin PIT convention for pure-ish modules only (MUD-014).
// Apply from :core / :perception / :memory — never hang on kotlin-jvm (would mutate-the-world).
package buildsrc.convention

import info.solidsoft.gradle.pitest.PitestPluginExtension

plugins {
    id("info.solidsoft.pitest")
}

val targetPackage = when (project.name) {
    "core" -> "com.jcraw.mud.core.*"
    "perception" -> "com.jcraw.mud.perception.*"
    "memory" -> "com.jcraw.mud.memory.*"
    else -> error(
        "buildsrc.convention.pitest-pure is only for :core, :perception, :memory " +
            "(got :${project.name})"
    )
}

// Catalog pin for junit5 plugin version (libs.versions.toml pitestJunit5).
// Hardcoded here to avoid version-catalog reach into convention body; keep in sync with catalog.
val junit5PluginVer = "1.2.2"

extensions.configure<PitestPluginExtension>("pitest") {
    targetClasses.set(setOf(targetPackage))
    // STRONGER mutator group (plan §3) — not default full noisy set alone.
    mutators.set(setOf("STRONGER"))
    // Report-only at Gradle; soft/hard threshold policy lives in tools/verify_mud.sh + docs/PIT.md.
    mutationThreshold.set(0)
    // JUnit Platform tests (kotlin-test → junit5).
    junit5PluginVersion.set(junit5PluginVer)
    // Stable report path for score parse (no timestamp subdirs).
    timestampedReports.set(false)
    outputFormats.set(setOf("XML", "HTML"))
    // Avoid failing the task when a module yields zero eligible mutants (edge config).
    // 0 mutations is still treated as fail by verify_mud score parse when lane ran.
    failWhenNoMutations.set(true)
    // Speed multi-module nightly; memory SQLite thrash still multi-minute either way.
    threads.set(Runtime.getRuntime().availableProcessors().coerceAtLeast(1))
}
