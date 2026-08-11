package com.jcraw.mud.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.architecture.KoArchitectureCreator.assertArchitecture
import com.lemonappdev.konsist.api.architecture.Layer
import kotlin.test.Test

/**
 * Module package-boundary rules (MUD-011).
 *
 * Encodes the **current** Gradle `implementation(project(...))` graph as Konsist layers.
 * Production sources only. Utils has no main package root — no layer.
 *
 * Deliberate residual exceptions: see [ALLOWED_RESIDUALS] and docs/KONSIST.md.
 * Do not silently broaden [dependsOn] to hide new illegal edges.
 */
class ModuleBoundaryTest {

    @Test
    fun `module package layers match declared Gradle edges`() {
        // Layers = real package roots (not research/sample names)
        val config = Layer("config", "com.jcraw.mud.config..")
        val core = Layer("core", "com.jcraw.mud.core..")
        val llm = Layer("llm", "com.jcraw.sophia.llm..")
        val action = Layer("action", "com.jcraw.mud.action..")
        val memory = Layer("memory", "com.jcraw.mud.memory..")
        val perception = Layer("perception", "com.jcraw.mud.perception..")
        val reasoning = Layer("reasoning", "com.jcraw.mud.reasoning..")
        val app = Layer("app", "com.jcraw.app..")
        val client = Layer("client", "com.jcraw.mud.client..")
        val testbot = Layer("testbot", "com.jcraw.mud.testbot..")

        Konsist
            .scopeFromProduction()
            .assertArchitecture {
                // Leaves
                config.dependsOnNothing()

                // Mid layers (declared edges only; utils has no package layer)
                core.dependsOn(config)
                llm.dependsOn(config)
                action.dependsOn(core)
                memory.dependsOn(core, llm)
                perception.dependsOn(core, llm)
                reasoning.dependsOn(config, core, llm, memory)

                // App shells
                app.dependsOn(core, config, perception, reasoning, memory, action, llm)
                client.dependsOn(core, perception, reasoning, memory, llm, config, action)

                // testbot → app is unusual but real (Gradle)
                testbot.dependsOn(core, config, llm, perception, reasoning, action, memory, app)
            }
    }

    companion object {
        /**
         * Ticket-scoped residual allowlist for pre-existing illegal edges.
         * Empty when the graph is clean. Entries must cite a follow-up ticket:
         * `// allow: MUD-011 residual → MUD-XXX`
         *
         * Prefer fixing imports over growing this list. Never broaden dependsOn
         * instead of listing a residual here.
         */
        @Suppress("unused")
        val ALLOWED_RESIDUALS: Set<String> = emptySet()
    }
}
