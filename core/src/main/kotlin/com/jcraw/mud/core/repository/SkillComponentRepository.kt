package com.jcraw.mud.core.repository

import com.jcraw.mud.core.SkillComponent

/**
 * Repository interface for SkillComponent persistence
 * Manages complete skill component state for entities
 */
interface SkillComponentRepository {
    /**
     * Save complete skill component for an entity
     * Overwrites existing component if present
     */
    fun save(entityId: String, component: SkillComponent): Result<Unit>

    /**
     * Load skill component for an entity
     * Returns null if entity has no skill component
     */
    fun load(entityId: String): Result<SkillComponent?>

    /**
     * Delete skill component for an entity
     */
    fun delete(entityId: String): Result<Unit>

    /**
     * Get all entities with skill components
     * Returns map of entityId -> SkillComponent
     */
    fun findAll(): Result<Map<String, SkillComponent>>
}
