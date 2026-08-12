@file:Suppress(
    "ReturnCount",
    "MagicNumber",
    "MaxLineLength",
    "TooManyFunctions",
    "LongMethod",
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "LongParameterList",
    "UnusedParameter",
    "TooGenericExceptionCaught",
    "TooGenericExceptionThrown",
    "SwallowedException",
    "WildcardImport",
    "MayBeConst",
    "ImplicitDefaultLocale",
    "ForbiddenComment",
    "UnusedPrivateProperty",
)

package com.jcraw.mud.reasoning.world

import com.jcraw.mud.config.GameConfig
import com.jcraw.mud.core.*
import com.jcraw.sophia.llm.LLMClient

/**
 * Spawns entities based on theme, mob density, and difficulty.
 * Uses LLM for dynamic mob variety or fallback rules when LLM unavailable.
 *
 * Thin facade — combat/LLM/fallback extracted (MUD-034g).
 */
open class MobSpawner(
    private val llmClient: LLMClient? = null,
    private val lootTableGenerator: LootTableGenerator? = null
) {
    /**
     * Spawn entities for a space.
     * Count determined by MobSpawnTuning (avg 1-3 per standard room).
     * Uses LLM for variety or deterministic fallback.
     */
    open suspend fun spawnEntities(
        theme: String,
        mobDensity: Double,
        difficulty: Int,
        spaceSize: Int = 10
    ): List<Entity.NPC> {
        // Check if mob generation is disabled
        if (!GameConfig.enableMobGeneration) return emptyList()

        val mobCount = MobSpawnTuning.desiredMobCount(mobDensity, spaceSize)
        if (mobCount == 0) return emptyList()

        return if (llmClient != null) {
            MobSpawnerLlm.spawnEntitiesWithLLM(llmClient, theme, mobCount, difficulty)
        } else {
            MobSpawnerFallback.spawnEntitiesFallback(theme, mobCount, difficulty)
        }
    }

    /**
     * Respawn entities for a space.
     * Clears existing entities and generates fresh list.
     * Used on game restart for murder-hobo viable gameplay.
     */
    suspend fun respawn(
        theme: String,
        mobDensity: Double,
        difficulty: Int,
        spaceSize: Int = 10
    ): List<Entity.NPC> {
        return spawnEntities(theme, mobDensity, difficulty, spaceSize)
    }

    /**
     * Spawn entities with respawn tracking.
     * Generates entities and registers them for timer-based respawning.
     *
     * @param theme Biome theme (e.g., "dark forest", "volcanic")
     * @param mobDensity Mob density (0.0-1.0)
     * @param difficulty Difficulty level (1-100)
     * @param spaceId Space where entities will spawn
     * @param respawnChecker RespawnChecker for registration
     * @param spaceSize Space size for mob count calculation
     * @return Pair of (spawned entities, registration results)
     */
    suspend fun spawnWithRespawn(
        theme: String,
        mobDensity: Double,
        difficulty: Int,
        spaceId: String,
        respawnChecker: RespawnChecker,
        spaceSize: Int = 10
    ): Result<List<Entity.NPC>> {
        // Spawn entities using existing logic
        val entities = spawnEntities(theme, mobDensity, difficulty, spaceSize)

        // Register each entity for respawn (non-fatal - log failures but continue)
        entities.forEach { entity ->
            respawnChecker.registerRespawn(
                entity = entity,
                spaceId = spaceId,
                respawnTurns = 0L // Use config-based scaling
            ).onFailure { error ->
                // Log warning but don't fail the whole operation
                println("[MOB SPAWN DEBUG] Warning: Failed to register respawn for ${entity.name}: ${error.message}")
            }
        }

        return Result.success(entities)
    }
}
