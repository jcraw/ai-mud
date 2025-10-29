package com.jcraw.mud.core.world

import kotlinx.serialization.Serializable

/**
 * Hierarchy levels for world chunks
 * Depth property enables validation (SPACE is atomic, can't have children)
 */
@Serializable
enum class ChunkLevel(val depth: Int) {
    WORLD(0),
    REGION(1),
    ZONE(2),
    SUBZONE(3),
    SPACE(4);

    /**
     * Check if this level can have children
     */
    fun canHaveChildren(): Boolean = this != SPACE

    /**
     * Check if this level can be a parent of the given child level
     */
    fun canBeParentOf(childLevel: ChunkLevel): Boolean =
        canHaveChildren() && childLevel.depth == this.depth + 1
}
