package com.jcraw.mud.core.world

import kotlinx.serialization.Serializable

/**
 * Terrain types with mechanical effects on movement and combat
 * Easier to extend with new types in future (e.g., WATER, LAVA)
 */
@Serializable
enum class TerrainType(
    val timeCostMultiplier: Double,
    val damageRisk: Int
) {
    /**
     * Normal terrain - no penalties
     */
    NORMAL(timeCostMultiplier = 1.0, damageRisk = 0),

    /**
     * Difficult terrain - slower movement, requires skill check to avoid damage
     */
    DIFFICULT(timeCostMultiplier = 2.0, damageRisk = 6),

    /**
     * Impassable terrain - cannot move through
     */
    IMPASSABLE(timeCostMultiplier = 0.0, damageRisk = 0);

    /**
     * Check if terrain allows passage
     */
    fun isPassable(): Boolean = this != IMPASSABLE
}
