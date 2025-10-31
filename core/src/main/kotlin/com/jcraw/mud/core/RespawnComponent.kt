package com.jcraw.mud.core

import kotlinx.serialization.Serializable

/**
 * Respawn component for entities that respawn after death
 * Tracks per-entity respawn timers and regeneration state
 */
@Serializable
data class RespawnComponent(
    val respawnTurns: Long = 500L,      // Turns until respawn
    val lastKilled: Long = 0L,          // gameTime when killed
    val originalEntityId: String,       // Template for regeneration
    override val componentType: ComponentType = ComponentType.RESPAWN
) : Component {
    /**
     * Check if enough time has elapsed for respawn
     * @param currentTime Current game time (in turns)
     * @return true if entity should respawn
     */
    fun shouldRespawn(currentTime: Long): Boolean {
        return lastKilled > 0L && (currentTime - lastKilled) >= respawnTurns
    }

    /**
     * Mark entity as killed at specific time
     * @param gameTime Current game time when entity was killed
     * @return Updated component with lastKilled set
     */
    fun markKilled(gameTime: Long): RespawnComponent {
        return copy(lastKilled = gameTime)
    }

    /**
     * Reset respawn timer after entity has respawned
     * @return Updated component with lastKilled reset to 0
     */
    fun resetTimer(): RespawnComponent {
        return copy(lastKilled = 0L)
    }

    /**
     * Calculate turns remaining until respawn
     * @param currentTime Current game time
     * @return Turns remaining (0 if ready to respawn, negative if not killed yet)
     */
    fun turnsUntilRespawn(currentTime: Long): Long {
        if (lastKilled == 0L) return -1L
        val elapsed = currentTime - lastKilled
        return (respawnTurns - elapsed).coerceAtLeast(0L)
    }
}
