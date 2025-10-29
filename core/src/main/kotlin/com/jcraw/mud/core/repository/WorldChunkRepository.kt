package com.jcraw.mud.core.repository

import com.jcraw.mud.core.WorldChunkComponent

/**
 * Repository interface for world chunk persistence
 * Manages hierarchical world structure (WORLD/REGION/ZONE/SUBZONE/SPACE)
 */
interface WorldChunkRepository {
    /**
     * Save or update a world chunk
     * Overwrites existing chunk if ID matches
     */
    fun save(chunk: WorldChunkComponent, id: String): Result<Unit>

    /**
     * Find chunk by ID
     * Returns null if chunk not found
     */
    fun findById(id: String): Result<WorldChunkComponent?>

    /**
     * Find all chunks with given parent ID
     * Returns empty list if no children found
     */
    fun findByParent(parentId: String): Result<List<Pair<String, WorldChunkComponent>>>

    /**
     * Find adjacent chunk in specified direction (spatial query)
     * Uses LLM-based spatial reasoning for "which chunk is north/south/etc"
     * Returns null if no adjacent chunk found
     */
    fun findAdjacent(currentId: String, direction: String): Result<WorldChunkComponent?>

    /**
     * Delete chunk by ID
     * Note: Will fail if chunk has children (foreign key constraint)
     */
    fun delete(id: String): Result<Unit>

    /**
     * Get all chunks
     * Returns map of chunkId -> WorldChunkComponent
     */
    fun getAll(): Result<Map<String, WorldChunkComponent>>
}
