package com.jcraw.mud.core.repository

import com.jcraw.mud.core.ItemInstance
import com.jcraw.mud.core.SpacePropertiesComponent

/**
 * Repository interface for space properties persistence
 * Manages detailed space data (descriptions, exits, content, state flags)
 */
interface SpacePropertiesRepository {
    /**
     * Save or update space properties
     * Overwrites existing properties if chunkId matches
     */
    fun save(properties: SpacePropertiesComponent, chunkId: String): Result<Unit>

    /**
     * Find space properties by chunk ID
     * Returns null if not found
     */
    fun findByChunkId(chunkId: String): Result<SpacePropertiesComponent?>

    /**
     * Update description only (optimized for single field)
     * Avoids full object serialization
     */
    fun updateDescription(chunkId: String, description: String): Result<Unit>

    /**
     * Update state flags only (optimized for single field)
     * Replaces existing flags with new map
     */
    fun updateFlags(chunkId: String, flags: Map<String, Boolean>): Result<Unit>

    /**
     * Add items to itemsDropped list (optimized append)
     * Useful for incremental updates (e.g., player drops item)
     */
    fun addItems(chunkId: String, items: List<ItemInstance>): Result<Unit>

    /**
     * Delete space properties by chunk ID
     */
    fun delete(chunkId: String): Result<Unit>
}
