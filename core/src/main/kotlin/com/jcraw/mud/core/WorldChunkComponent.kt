package com.jcraw.mud.core

import com.jcraw.mud.core.world.ChunkLevel
import kotlinx.serialization.Serializable

/**
 * World chunk component for hierarchical world representation
 * Each chunk represents a level in the hierarchy (WORLD → REGION → ZONE → SUBZONE → SPACE)
 * LLM integration happens at generation time, not in component
 */
@Serializable
data class WorldChunkComponent(
    val level: ChunkLevel,
    val parentId: String? = null,
    val children: List<String> = emptyList(),
    val lore: String = "",
    val biomeTheme: String = "",
    val sizeEstimate: Int = 1,
    val mobDensity: Double = 0.0,
    val difficultyLevel: Int = 1
) : Component {
    override val componentType: ComponentType
        get() = ComponentType.WORLD_CHUNK

    /**
     * Add child chunk ID
     * Immutable update
     */
    fun addChild(childId: String): WorldChunkComponent {
        require(level.canHaveChildren()) {
            "Cannot add children to SPACE level chunks"
        }
        return copy(children = children + childId)
    }

    /**
     * Remove child chunk ID
     */
    fun removeChild(childId: String): WorldChunkComponent =
        copy(children = children - childId)

    /**
     * Validate chunk hierarchy rules
     */
    fun validate(): Boolean {
        // SPACE level cannot have children
        if (level == ChunkLevel.SPACE && children.isNotEmpty()) {
            return false
        }

        // Non-WORLD chunks must have parent
        if (level != ChunkLevel.WORLD && parentId == null) {
            return false
        }

        // WORLD level cannot have parent
        if (level == ChunkLevel.WORLD && parentId != null) {
            return false
        }

        // Difficulty should scale with depth (not strictly enforced, but checked)
        // MobDensity should be in range 0-1
        if (mobDensity < 0.0 || mobDensity > 1.0) {
            return false
        }

        // Difficulty should be positive
        if (difficultyLevel < 1) {
            return false
        }

        // Size estimate should be positive
        if (sizeEstimate < 1) {
            return false
        }

        return true
    }

    /**
     * Create modified lore variant (to be called with LLM output from generator)
     * This method doesn't call LLM itself - generator provides the new lore
     */
    fun withInheritedLore(newLore: String): WorldChunkComponent =
        copy(lore = newLore)

    /**
     * Update theme (to be called with LLM output from generator)
     */
    fun withTheme(newTheme: String): WorldChunkComponent =
        copy(biomeTheme = newTheme)
}
