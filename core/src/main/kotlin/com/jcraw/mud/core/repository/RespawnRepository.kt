package com.jcraw.mud.core.repository

import com.jcraw.mud.core.RespawnComponent

/**
 * Repository interface for respawn component persistence
 * Manages mob respawn timers and regeneration state
 */
interface RespawnRepository {
    /**
     * Save or update a respawn component
     * Overwrites existing component if entityId matches
     *
     * @param component The respawn component to save
     * @param entityId The entity ID this component belongs to
     * @param spaceId The space ID where the entity respawns
     */
    fun save(component: RespawnComponent, entityId: String, spaceId: String): Result<Unit>

    /**
     * Find respawn component by entity ID
     * Returns null if not found
     */
    fun findByEntityId(entityId: String): Result<Pair<RespawnComponent, String>?>

    /**
     * Find all respawn components in a specific space
     * Returns list of (entityId, spaceId, component) tuples
     * Useful for processing all mobs in a space for respawn checks
     */
    fun findBySpaceId(spaceId: String): Result<List<Triple<String, String, RespawnComponent>>>

    /**
     * Find all respawn components that are ready to respawn
     * Returns list of (entityId, spaceId, component) tuples
     *
     * @param currentTime Current game time (in turns)
     */
    fun findReadyToRespawn(currentTime: Long): Result<List<Triple<String, String, RespawnComponent>>>

    /**
     * Update lastKilled timestamp for an entity
     * Optimized single-field update (avoids full object serialization)
     *
     * @param entityId Entity that was killed
     * @param gameTime Game time when entity was killed
     */
    fun markKilled(entityId: String, gameTime: Long): Result<Unit>

    /**
     * Reset respawn timer after entity has respawned
     * Sets lastKilled back to 0
     *
     * @param entityId Entity that has respawned
     */
    fun resetTimer(entityId: String): Result<Unit>

    /**
     * Delete respawn component by entity ID
     */
    fun delete(entityId: String): Result<Unit>

    /**
     * Get all respawn components
     * Returns map of entityId -> (spaceId, component)
     */
    fun getAll(): Result<Map<String, Pair<String, RespawnComponent>>>
}
