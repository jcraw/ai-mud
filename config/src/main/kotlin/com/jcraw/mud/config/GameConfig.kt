package com.jcraw.mud.config

/**
 * Unified configuration for debugging and testing.
 *
 * This object provides a central location for all debug and test configuration values.
 * Any module can import this to access or modify config flags.
 *
 * ## Usage
 * ```kotlin
 * // Disable mob spawning for testing
 * GameConfig.enableMobGeneration = false
 *
 * // Enable LLM call logging for debugging
 * GameConfig.logLLMCalls = true
 * ```
 *
 * ## Search Keywords
 * config, configuration, debug, testing, toggle, flags, settings
 */
object GameConfig {
    /**
     * Master toggle for mob (monster) generation and spawning.
     *
     * ## What This Affects
     * - ✅ Regular monsters (hostile NPCs spawned in dungeons)
     * - ✅ Friendly NPCs (non-hostile creatures spawned in areas)
     * - ❌ Merchants (always spawn - gameplay critical)
     * - ❌ Bosses (always spawn - gameplay critical)
     *
     * ## Implementation Notes
     * This affects:
     * - Initial world/chunk generation (MobSpawner.spawnEntities)
     * - Game load respawns (RespawnManager)
     * - Timer-based respawns (SpacePopulator with respawn)
     *
     * Default: true
     */
    var enableMobGeneration: Boolean = true

    /**
     * Toggle for logging LLM API calls.
     *
     * When enabled, logs all LLM requests and responses for debugging.
     * Useful for tracking API usage, debugging prompts, and understanding LLM behavior.
     *
     * Default: false
     */
    var logLLMCalls: Boolean = false
}
