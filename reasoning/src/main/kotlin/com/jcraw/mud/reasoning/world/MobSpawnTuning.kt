package com.jcraw.mud.reasoning.world

import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Centralized mob spawn scaling so rooms average 1-3 hostiles per spec.
 * Converts chunk-level density (0-1) + rough space size into an actual count.
 */
object MobSpawnTuning {
    private const val BASE_MAX_MOBS = 3
    private const val DEFAULT_SPACE_SIZE = 10.0
    private const val MAX_MOBS_CAP = 5
    private const val MIN_EFFECTIVE_DENSITY = 0.1

    /**
     * Calculate desired mob count while keeping per-room populations manageable.
     *
     * @param mobDensity Configured density (0-1)
     * @param spaceSize Relative space size hint (defaults to ~10 tiles)
     */
    fun desiredMobCount(mobDensity: Double, spaceSize: Int): Int {
        if (spaceSize <= 0) return 0
        val normalizedDensity = mobDensity.coerceIn(0.0, 1.0)
        if (normalizedDensity < MIN_EFFECTIVE_DENSITY) return 0

        val normalizedSize = spaceSize.coerceAtLeast(1)
        val sizeFactor = (normalizedSize / DEFAULT_SPACE_SIZE).coerceIn(0.5, 2.0)
        val maxMobs = (BASE_MAX_MOBS * sizeFactor)
            .roundToInt()
            .coerceIn(1, MAX_MOBS_CAP)

        val scaled = normalizedDensity * maxMobs
        return ceil(scaled)
            .toInt()
            .coerceIn(1, maxMobs)
    }
}
