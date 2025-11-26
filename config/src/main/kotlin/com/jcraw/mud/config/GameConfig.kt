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
    var logLLMCalls: Boolean = true

    /**
     * Multiplier for skill experience (XP) gain rate.
     *
     * ## Keywords
     * skill progression, xp multiplier, experience rate, skill testing, fast leveling
     *
     * ## What This Affects
     * All skill XP gains from any source (successful and failed attempts)
     *
     * ## Examples
     * - `1.0f` - Normal progression (default)
     * - `10.0f` - 10x faster for testing
     * - `100.0f` - 100x faster for rapid testing
     * - `0.5f` - Half speed for challenge runs
     *
     * Default: 1.0f (normal progression)
     */
    var skillXpMultiplier: Float = 10.0f

    /**
     * Base chance percentage for lucky skill progression.
     *
     * ## Keywords
     * lucky progression, chance-based leveling, skill progression, instant level-up
     *
     * ## Formula
     * Actual chance = floor(baseLuckyChance / sqrt(targetLevel + 1))
     *
     * ## Examples (baseLuckyChance = 15)
     * - Level 0→1: 15% chance
     * - Level 1→2: 11% chance
     * - Level 10→11: 5% chance
     * - Level 99→100: 1.5% chance
     *
     * Default: 15
     */
    var baseLuckyChance: Int = 15

    /**
     * Enable/disable lucky progression (chance-based level-ups).
     *
     * ## Keywords
     * lucky progression, disable lucky, xp-only progression
     *
     * ## What This Affects
     * When enabled: Skills can level up via lucky chance OR XP accumulation
     * When disabled: All progression uses XP-only path
     *
     * Default: true
     */
    var enableLuckyProgression: Boolean = true

    /**
     * Enable lucky progression for NPCs.
     *
     * ## Keywords
     * npc progression, npc leveling, disable npc lucky
     *
     * ## Rationale
     * NPCs have preset skill levels. Random level-ups during combat
     * could make encounters unpredictable and difficult to balance.
     *
     * Default: false
     */
    var enableNPCLuckyProgression: Boolean = false
}
