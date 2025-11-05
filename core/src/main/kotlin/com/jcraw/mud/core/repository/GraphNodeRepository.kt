package com.jcraw.mud.core.repository

import com.jcraw.mud.core.GraphNodeComponent
import com.jcraw.mud.core.world.EdgeData

/**
 * Repository interface for graph node persistence
 * Manages V3 graph-based world topology
 */
interface GraphNodeRepository {
    /**
     * Save a graph node
     * Overwrites existing node if ID matches
     */
    fun save(node: GraphNodeComponent): Result<Unit>

    /**
     * Find node by ID
     * Returns null if node not found
     */
    fun findById(id: String): Result<GraphNodeComponent?>

    /**
     * Find all nodes in a specific chunk
     * Returns empty list if no nodes found
     */
    fun findByChunk(chunkId: String): Result<List<GraphNodeComponent>>

    /**
     * Update an existing node
     * Fails if node doesn't exist
     */
    fun update(node: GraphNodeComponent): Result<Unit>

    /**
     * Delete node by ID
     * Note: Should cascade delete edges referencing this node
     */
    fun delete(id: String): Result<Unit>

    /**
     * Add edge to a node
     * Updates the node's neighbors list
     * Immutable update - uses node.addEdge() internally
     */
    fun addEdge(fromId: String, edge: EdgeData): Result<Unit>

    /**
     * Remove edge from a node
     * Updates the node's neighbors list
     * Immutable update - uses node.removeEdge() internally
     */
    fun removeEdge(fromId: String, targetId: String): Result<Unit>

    /**
     * Get all nodes
     * Returns map of nodeId -> GraphNodeComponent
     */
    fun getAll(): Result<Map<String, GraphNodeComponent>>
}
