package com.jcraw.mud.core.repository

import com.jcraw.mud.core.TradingComponent

/**
 * Repository interface for trading persistence
 * Manages merchant stock, gold, and pricing
 */
interface TradingRepository {
    /**
     * Find trading component by entity ID
     * Returns null if entity is not a merchant
     */
    fun findByEntityId(entityId: String): Result<TradingComponent?>

    /**
     * Save complete trading component for an entity
     * Overwrites existing trading data if present
     */
    fun save(entityId: String, trading: TradingComponent): Result<Unit>

    /**
     * Delete trading data for an entity
     */
    fun delete(entityId: String): Result<Unit>

    /**
     * Update merchant gold for an entity
     * Faster than loading and saving full trading component
     */
    fun updateGold(entityId: String, newGold: Int): Result<Unit>

    /**
     * Update stock for an entity
     * Faster than loading and saving full trading component
     */
    fun updateStock(entityId: String, trading: TradingComponent): Result<Unit>

    /**
     * Get all entities with trading components
     * Returns map of entityId -> TradingComponent
     */
    fun findAll(): Result<Map<String, TradingComponent>>
}
