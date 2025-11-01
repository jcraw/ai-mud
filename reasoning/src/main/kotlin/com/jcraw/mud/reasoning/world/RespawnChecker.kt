package com.jcraw.mud.reasoning.world

import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.RespawnComponent
import com.jcraw.mud.core.RespawnConfig
import com.jcraw.mud.core.repository.RespawnRepository

/**
 * Handles mob respawn logic using timer-based RespawnComponent system.
 *
 * Responsibilities:
 * - Check respawns on space entry
 * - Register entities for respawn tracking
 * - Mark entity deaths
 * - Regenerate respawned mobs
 */
class RespawnChecker(
    private val respawnRepository: RespawnRepository,
    private val mobSpawner: MobSpawner,
    private val config: RespawnConfig = RespawnConfig()
) {
    /**
     * Check which mobs should respawn in a space and regenerate them.
     *
     * @param spaceId Space to check for respawns
     * @param currentTime Current game time (in turns)
     * @return List of respawned entities
     */
    suspend fun checkRespawns(spaceId: String, currentTime: Long): Result<List<Entity.NPC>> {
        if (!config.enabled) {
            return Result.success(emptyList())
        }

        return respawnRepository.findBySpaceId(spaceId)
            .mapCatching { components ->
                val respawnedEntities = mutableListOf<Entity.NPC>()

                for ((entityId, _, component) in components) {
                    if (component.shouldRespawn(currentTime)) {
                        // Respawn the entity
                        val respawnedEntity = regenerateEntity(
                            originalEntityId = component.originalEntityId,
                            newEntityId = entityId
                        ).getOrNull()

                        if (respawnedEntity != null) {
                            respawnedEntities.add(respawnedEntity)

                            // Reset the respawn timer
                            respawnRepository.resetTimer(entityId)
                                .onFailure { e ->
                                    // Log but don't fail the respawn
                                    println("Warning: Failed to reset timer for $entityId: ${e.message}")
                                }
                        }
                    }
                }

                respawnedEntities
            }
    }

    /**
     * Register an entity for respawn tracking.
     *
     * @param entity Entity to track for respawn
     * @param spaceId Space where entity should respawn
     * @param respawnTurns Turns until respawn (0 = use config-based scaling)
     */
    fun registerRespawn(
        entity: Entity.NPC,
        spaceId: String,
        respawnTurns: Long = 0L
    ): Result<Unit> {
        if (!config.enabled) {
            return Result.success(Unit)
        }

        // Use provided respawnTurns or calculate from entity difficulty
        val finalRespawnTurns = if (respawnTurns > 0L) {
            respawnTurns
        } else {
            // Estimate difficulty from entity health (rough heuristic)
            val estimatedDifficulty = (entity.health / 10).coerceIn(1, 100)
            config.getRespawnTime(estimatedDifficulty)
        }

        // Don't register if respawn time is effectively infinite
        if (finalRespawnTurns == Long.MAX_VALUE) {
            return Result.success(Unit)
        }

        val component = RespawnComponent(
            respawnTurns = finalRespawnTurns,
            lastKilled = 0L,
            originalEntityId = entity.id
        )

        return respawnRepository.save(
            component = component,
            entityId = entity.id,
            spaceId = spaceId
        )
    }

    /**
     * Mark an entity as killed at a specific game time.
     *
     * @param entityId Entity that was killed
     * @param gameTime Game time when entity was killed
     */
    fun markDeath(entityId: String, gameTime: Long): Result<Unit> {
        if (!config.enabled) {
            return Result.success(Unit)
        }

        return respawnRepository.markKilled(entityId, gameTime)
    }

    /**
     * Regenerate an entity from its template.
     *
     * For now, this creates a new entity with the same ID.
     * In the future, this could load from a template repository.
     *
     * @param originalEntityId ID of the original entity (template)
     * @param newEntityId ID to assign to the respawned entity
     */
    private suspend fun regenerateEntity(
        originalEntityId: String,
        newEntityId: String
    ): Result<Entity.NPC> {
        // For now, we can't easily regenerate the exact same entity
        // because we don't store entity templates.
        // This is a limitation we'll address in a future iteration.
        //
        // For now, return a placeholder entity.
        // In a real implementation, we would:
        // 1. Store entity templates in a separate table
        // 2. Load template by originalEntityId
        // 3. Create new entity from template with newEntityId

        return Result.failure(
            NotImplementedError("Entity regeneration requires template storage system")
        )
    }

    /**
     * Get all entities that are ready to respawn across all spaces.
     *
     * @param currentTime Current game time
     * @return Map of spaceId -> list of entities ready to respawn
     */
    suspend fun getReadyToRespawn(currentTime: Long): Result<Map<String, List<String>>> {
        if (!config.enabled) {
            return Result.success(emptyMap())
        }

        return respawnRepository.findReadyToRespawn(currentTime)
            .mapCatching { components ->
                components
                    .groupBy({ it.second }, { it.first })
            }
    }

    /**
     * Delete respawn tracking for an entity.
     * Used when entity is permanently removed (e.g., boss defeated).
     *
     * @param entityId Entity to remove from respawn tracking
     */
    fun removeFromRespawn(entityId: String): Result<Unit> {
        return respawnRepository.delete(entityId)
    }
}
