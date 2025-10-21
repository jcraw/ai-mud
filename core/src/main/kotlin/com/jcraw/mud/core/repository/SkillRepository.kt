package com.jcraw.mud.core.repository

import com.jcraw.mud.core.SkillEvent
import com.jcraw.mud.core.SkillState

/**
 * Repository interface for individual skill persistence
 * Manages skill data in denormalized table for fast queries
 */
interface SkillRepository {
    /**
     * Find skill state by entity ID and skill name
     * Returns null if skill doesn't exist
     */
    fun findByEntityAndSkill(entityId: String, skillName: String): Result<SkillState?>

    /**
     * Find all skills for an entity
     * Returns map of skill name -> SkillState
     */
    fun findByEntityId(entityId: String): Result<Map<String, SkillState>>

    /**
     * Find all skills with a specific tag
     * Returns map of (entityId, skillName) -> SkillState
     */
    fun findByTag(tag: String): Result<Map<Pair<String, String>, SkillState>>

    /**
     * Save or update a skill's state
     */
    fun save(entityId: String, skillName: String, skillState: SkillState): Result<Unit>

    /**
     * Update skill XP (convenience method for common operation)
     */
    fun updateXp(entityId: String, skillName: String, newXp: Long, newLevel: Int): Result<Unit>

    /**
     * Mark skill as unlocked
     */
    fun unlockSkill(entityId: String, skillName: String): Result<Unit>

    /**
     * Delete a skill (rarely used, mainly for testing)
     */
    fun delete(entityId: String, skillName: String): Result<Unit>

    /**
     * Delete all skills for an entity
     */
    fun deleteAllForEntity(entityId: String): Result<Unit>

    /**
     * Log a skill event to event history
     */
    fun logEvent(event: SkillEvent): Result<Unit>

    /**
     * Get event history for entity and skill
     * Returns events sorted by timestamp (most recent first)
     */
    fun getEventHistory(
        entityId: String,
        skillName: String? = null,
        limit: Int = 50
    ): Result<List<SkillEvent>>
}
