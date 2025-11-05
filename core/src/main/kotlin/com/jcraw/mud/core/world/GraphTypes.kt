package com.jcraw.mud.core.world

import kotlinx.serialization.Serializable

/**
 * Type of graph node defining its structural role in the world
 * Sealed class enables exhaustive when statements and future extension with type-specific data
 */
@Serializable
sealed class NodeType {
    /**
     * Hub nodes - Safe zones, towns, major connection points
     * Typically have high connectivity (4+ edges)
     */
    @Serializable
    object Hub : NodeType() {
        override fun toString(): String = "Hub"
    }

    /**
     * Linear nodes - Corridor chains, straight paths
     * Typically have 2 edges (in and out)
     */
    @Serializable
    object Linear : NodeType() {
        override fun toString(): String = "Linear"
    }

    /**
     * Branching nodes - Choice points, loops, intersections
     * Typically have 3+ edges creating exploration choices
     */
    @Serializable
    object Branching : NodeType() {
        override fun toString(): String = "Branching"
    }

    /**
     * Dead-end nodes - Treasure rooms, traps, single-exit chambers
     * Typically have 1 edge (dead end)
     */
    @Serializable
    object DeadEnd : NodeType() {
        override fun toString(): String = "DeadEnd"
    }

    /**
     * Boss nodes - Boss rooms, major encounters
     * Placed at strategic graph positions (farthest from entry)
     */
    @Serializable
    object Boss : NodeType() {
        override fun toString(): String = "Boss"
    }

    /**
     * Frontier nodes - Expandable boundary nodes for infinite exploration
     * When traversed, trigger cascade to new chunks
     */
    @Serializable
    object Frontier : NodeType() {
        override fun toString(): String = "Frontier"
    }

    /**
     * Questable nodes - Designated for future quest system integration
     * Can be used for gated content or quest objectives
     */
    @Serializable
    object Questable : NodeType() {
        override fun toString(): String = "Questable"
    }
}

/**
 * Edge data connecting graph nodes
 * Stores direction, target, visibility, and access conditions
 * Separates graph structure from world content (SpaceProperties)
 */
@Serializable
data class EdgeData(
    /**
     * Target node ID this edge leads to
     */
    val targetId: String,

    /**
     * Direction label (e.g., "north", "up", "hidden passage")
     */
    val direction: String,

    /**
     * Whether this edge is hidden and requires Perception to reveal
     * Hidden edges are not shown in exit lists until player reveals them
     */
    val hidden: Boolean = false,

    /**
     * Access conditions (skill checks, item requirements)
     * Player must meet all conditions to traverse this edge
     */
    val conditions: List<Condition> = emptyList()
) {
    /**
     * Generate unique ID for this edge (for tracking revealed exits)
     * Format: "{sourceId}->{targetId}"
     */
    fun edgeId(fromId: String): String = "$fromId->$targetId"
}
