package com.jcraw.mud.core

/**
 * Configuration for mob respawn system.
 *
 * Defines respawn behavior including whether respawning is enabled globally
 * and difficulty-based scaling for respawn timers.
 */
data class RespawnConfig(
    /**
     * Whether respawn system is enabled globally.
     * If false, no mobs will respawn.
     */
    val enabled: Boolean = true,

    /**
     * Difficulty-based respawn timer scaling.
     * Maps difficulty ranges to respawn time in game turns.
     *
     * Default scaling:
     * - 1-10 (shallow): 300 turns (~5 min gameplay)
     * - 11-30 (mid): 500 turns (~8 min)
     * - 31-60 (deep): 1000 turns (~16 min)
     * - 61+ (boss zones): Long.MAX_VALUE (no respawn)
     */
    val difficultyScaling: Map<IntRange, Long> = mapOf(
        1..10 to 300L,
        11..30 to 500L,
        31..60 to 1000L,
        61..100 to Long.MAX_VALUE
    )
) {
    /**
     * Get respawn time for a given difficulty level.
     * Returns the respawn time in game turns, or Long.MAX_VALUE if no respawn.
     */
    fun getRespawnTime(difficulty: Int): Long {
        return difficultyScaling.entries
            .find { difficulty in it.key }
            ?.value
            ?: Long.MAX_VALUE  // Default: no respawn
    }
}
