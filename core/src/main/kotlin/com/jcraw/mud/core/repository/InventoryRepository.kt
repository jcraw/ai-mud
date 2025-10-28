package com.jcraw.mud.core.repository

import com.jcraw.mud.core.InventoryComponent

/**
 * Repository interface for inventory persistence
 * Manages entity inventory state (items, equipped, gold, capacity)
 */
interface InventoryRepository {
    /**
     * Find inventory by entity ID
     * Returns null if entity has no inventory
     */
    fun findByEntityId(entityId: String): Result<InventoryComponent?>

    /**
     * Save complete inventory for an entity
     * Overwrites existing inventory if present
     */
    fun save(entityId: String, inventory: InventoryComponent): Result<Unit>

    /**
     * Delete inventory for an entity
     */
    fun delete(entityId: String): Result<Unit>

    /**
     * Update gold for an entity
     * Faster than loading and saving full inventory
     */
    fun updateGold(entityId: String, newGold: Int): Result<Unit>

    /**
     * Update capacity for an entity
     * Faster than loading and saving full inventory
     */
    fun updateCapacity(entityId: String, newCapacity: Double): Result<Unit>

    /**
     * Get all entities with inventories
     * Returns map of entityId -> InventoryComponent
     */
    fun findAll(): Result<Map<String, InventoryComponent>>
}
