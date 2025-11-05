package com.jcraw.mud.core

import com.jcraw.mud.core.world.EdgeData
import com.jcraw.mud.core.world.NodeType
import kotlinx.serialization.Serializable

/**
 * Graph node component for pre-generated world topology
 * Defines connectivity structure before content generation (SpaceProperties)
 * V3 feature: Graph-based navigation with typed nodes and dynamic edges
 */
@Serializable
data class GraphNodeComponent(
    /**
     * Unique node ID (typically matches Space entity ID)
     */
    val id: String,

    /**
     * 2D grid position for layouts that support it (null for organic layouts)
     * Used by Grid and BSP algorithms, optional for FloodFill
     */
    val position: Pair<Int, Int>? = null,

    /**
     * Structural type of this node (HUB, LINEAR, BRANCHING, etc.)
     * Determines generation hints and gameplay role
     */
    val type: NodeType,

    /**
     * List of edges to neighboring nodes
     * Order matters for cardinal directions (N/S/E/W first)
     * EdgeData contains targetId, direction, hidden flag, conditions
     */
    val neighbors: List<EdgeData> = emptyList(),

    /**
     * ID of chunk this node belongs to (typically SUBZONE level)
     */
    val chunkId: String = ""
) : Component {
    override val componentType: ComponentType
        get() = ComponentType.GRAPH_NODE

    /**
     * Add edge to another node
     * Immutable update - returns new component
     */
    fun addEdge(edge: EdgeData): GraphNodeComponent {
        require(!neighbors.any { it.targetId == edge.targetId && it.direction == edge.direction }) {
            "Edge to ${edge.targetId} via ${edge.direction} already exists"
        }
        return copy(neighbors = neighbors + edge)
    }

    /**
     * Remove edge to target node by target ID
     * Immutable update
     */
    fun removeEdge(targetId: String): GraphNodeComponent {
        require(neighbors.any { it.targetId == targetId }) {
            "Edge to $targetId not found"
        }
        val filtered = neighbors.filter { it.targetId != targetId }
        return copy(neighbors = filtered)
    }

    /**
     * Remove specific edge by direction
     * Useful when multiple edges exist to same target
     */
    fun removeEdgeByDirection(direction: String): GraphNodeComponent {
        require(neighbors.any { it.direction == direction }) {
            "Edge in direction $direction not found"
        }
        val filtered = neighbors.filter { it.direction != direction }
        return copy(neighbors = filtered)
    }

    /**
     * Get edge by direction (case-insensitive)
     * Returns null if not found
     */
    fun getEdge(direction: String): EdgeData? {
        return neighbors.find { it.direction.equals(direction, ignoreCase = true) }
    }

    /**
     * Get all visible edges (not hidden or already revealed)
     * Used for exit display
     */
    fun getVisibleEdges(revealedExits: Set<String>): List<EdgeData> {
        return neighbors.filter { edge ->
            !edge.hidden || revealedExits.contains(edge.edgeId(id))
        }
    }

    /**
     * Get all hidden edges not yet revealed
     * Used for Perception checks
     */
    fun getHiddenEdges(revealedExits: Set<String>): List<EdgeData> {
        return neighbors.filter { edge ->
            edge.hidden && !revealedExits.contains(edge.edgeId(id))
        }
    }

    /**
     * Calculate node degree (number of edges)
     * Used for validation and node type assignment
     */
    fun degree(): Int = neighbors.size

    /**
     * Check if this is a dead-end node (degree 1)
     */
    fun isDeadEnd(): Boolean = degree() == 1

    /**
     * Check if this is a hub node (high connectivity)
     */
    fun isHub(): Boolean = type == NodeType.Hub || degree() >= 4

    /**
     * Check if this is a frontier node (boundary expansion point)
     */
    fun isFrontier(): Boolean = type == NodeType.Frontier

    /**
     * Validate component constraints
     */
    fun validate(): Boolean {
        // ID must not be empty
        if (id.isBlank()) return false

        // Dead-end nodes should have degree 1
        if (type == NodeType.DeadEnd && degree() != 1) return false

        // Linear nodes should have degree 2
        if (type == NodeType.Linear && degree() != 2) return false

        // Hub nodes should have high degree
        if (type == NodeType.Hub && degree() < 3) return false

        // No duplicate edges (same target + direction)
        val edgeKeys = neighbors.map { "${it.targetId}:${it.direction}" }
        if (edgeKeys.size != edgeKeys.toSet().size) return false

        // Chunk ID should not be empty
        if (chunkId.isBlank()) return false

        return true
    }
}
