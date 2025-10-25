package com.jcraw.mud.core.repository

import com.jcraw.mud.core.CombatComponent
import com.jcraw.mud.core.CombatEvent
import com.jcraw.mud.core.StatusEffect

/**
 * Repository interface for combat system persistence
 * Manages combat components, status effects, events, and corpses
 */
interface CombatRepository {
    /**
     * Find combat component by entity ID
     * Returns null if entity has no combat component
     */
    fun findByEntityId(entityId: String): Result<CombatComponent?>

    /**
     * Save complete combat component for an entity
     * Overwrites existing component if present
     */
    fun save(entityId: String, component: CombatComponent): Result<Unit>

    /**
     * Delete combat component for an entity
     */
    fun delete(entityId: String): Result<Unit>

    /**
     * Update HP for an entity
     * Faster than loading and saving full component
     */
    fun updateHp(entityId: String, newHp: Int): Result<Unit>

    /**
     * Apply status effect to an entity
     * Handles stacking and replacement logic
     */
    fun applyEffect(entityId: String, effect: StatusEffect): Result<Unit>

    /**
     * Remove status effect from an entity
     */
    fun removeEffect(entityId: String, effectId: Int): Result<Unit>

    /**
     * Find all entities with hostile disposition in a room
     * Returns entity IDs of threats
     */
    fun findActiveThreats(roomId: String): Result<List<String>>

    /**
     * Find all entities with combat components in a room
     * Returns entity IDs
     */
    fun findCombatantsInRoom(roomId: String): Result<List<String>>

    /**
     * Log combat event
     */
    fun logEvent(event: CombatEvent): Result<Unit>

    /**
     * Get event history for an entity
     * Returns most recent events up to limit
     */
    fun getEventHistory(entityId: String, limit: Int = 50): Result<List<CombatEvent>>

    /**
     * Get all entities with combat components
     * Returns map of entityId -> CombatComponent
     */
    fun findAll(): Result<Map<String, CombatComponent>>
}
