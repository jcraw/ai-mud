package com.jcraw.mud.core

/**
 * Configuration for mob (monster) generation and spawning.
 *
 * This controls whether hostile and friendly NPCs spawn via the MobSpawner system.
 *
 * ## What This Affects
 * - ✅ Regular monsters (hostile NPCs spawned in dungeons)
 * - ✅ Friendly NPCs (non-hostile creatures spawned in areas)
 * - ❌ Merchants (always spawn - gameplay critical)
 * - ❌ Bosses (always spawn - gameplay critical)
 *
 * ## Usage
 * To disable all mob spawning:
 * ```kotlin
 * MobGenerationConfig.enabled = false
 * ```
 *
 * To re-enable:
 * ```kotlin
 * MobGenerationConfig.enabled = true
 * ```
 *
 * ## Search Keywords
 * disable mobs, turn off monsters, turn off enemies, no hostile NPCs,
 * empty dungeon, mob spawning toggle, enable mob generation
 *
 * ## Implementation Notes
 * This affects:
 * - Initial world/chunk generation (MobSpawner.spawnEntities)
 * - Game load respawns (RespawnManager)
 * - Timer-based respawns (SpacePopulator with respawn)
 *
 * Structured as an object for simplicity, but designed to be extensible.
 * Can be converted to a data class with granular controls if needed:
 * - enableInitialSpawns
 * - enableLoadRespawns
 * - enableTimerRespawns
 */
object MobGenerationConfig {
    /**
     * Master toggle for all mob generation.
     * When false, no monsters spawn (merchants and bosses still spawn).
     * Default: true
     */
    var enabled: Boolean = true
}
