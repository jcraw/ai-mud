package com.jcraw.mud.core.repository

import com.jcraw.mud.core.Entity

/**
 * Repository for persisting entity data associated with spaces.
 * Currently focused on NPC entities but extensible for additional types.
 */
interface SpaceEntityRepository {
    /**
     * Persist or update an entity.
     */
    fun save(entity: Entity): Result<Unit>

    /**
     * Persist or update a batch of entities atomically.
     */
    fun saveAll(entities: List<Entity>): Result<Unit>

    /**
     * Retrieve entity by identifier.
     */
    fun findById(id: String): Result<Entity?>

    /**
     * Remove entity from persistence.
     */
    fun delete(id: String): Result<Unit>
}
