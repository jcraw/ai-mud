package com.jcraw.app.handlers

import com.jcraw.mud.core.*
import com.jcraw.mud.reasoning.boss.VictoryChecker
import com.jcraw.mud.reasoning.boss.VictoryResult

/**
 * Handles victory condition checking and victory state management
 * Checks when player returns to town with Abyss Heart
 */
class VictoryHandlers(
    private val victoryChecker: VictoryChecker = VictoryChecker()
) {
    /**
     * Check victory condition when player enters a space
     * Should be called on Intent.Travel completion
     *
     * @param worldState Current world state
     * @param playerId Player to check
     * @param currentSpace Space player just entered
     * @return VictoryResponse indicating if victory occurred
     */
    fun checkVictory(
        worldState: WorldState,
        playerId: PlayerId,
        currentSpace: SpacePropertiesComponent
    ): VictoryResponse {
        val player = worldState.getPlayer(playerId) ?: return VictoryResponse.NotYet

        when (val result = victoryChecker.checkVictoryCondition(player, currentSpace)) {
            is VictoryResult.Won -> {
                return VictoryResponse.Victory(
                    narration = result.narration,
                    playerName = player.name,
                    finalStats = getFinalStats(player)
                )
            }
            is VictoryResult.NotYet -> {
                return VictoryResponse.NotYet
            }
        }
    }

    /**
     * Get final player stats for victory screen
     */
    private fun getFinalStats(player: PlayerState): Map<String, String> {
        return mapOf(
            "Name" to player.name,
            "Final HP" to "${player.health}/${player.maxHealth}",
            "Gold" to player.gold.toString(),
            "Experience" to player.experiencePoints.toString(),
            "Quests Completed" to player.completedQuests.size.toString(),
            "Inventory Items" to player.inventory.size.toString()
        )
    }

    /**
     * Generate restart prompt for player
     */
    fun getRestartPrompt(): String {
        return """

            Would you like to:
            1. Continue exploring (the world persists)
            2. Start a new game
            3. Quit

            Enter your choice (1-3):
        """.trimIndent()
    }

    /**
     * Handle victory state persistence
     * Marks victory flag in world properties
     *
     * @param worldState Current world state
     * @return Updated world state with victory flag set
     */
    fun markVictory(worldState: WorldState): WorldState {
        val updatedProperties = worldState.gameProperties + ("victory_achieved" to "true")
        return worldState.copy(gameProperties = updatedProperties)
    }

    /**
     * Check if victory has been achieved (from saved state)
     *
     * @param worldState World state to check
     * @return true if victory flag is set
     */
    fun hasVictory(worldState: WorldState): Boolean {
        return worldState.gameProperties["victory_achieved"] == "true"
    }
}

/**
 * Victory check response
 */
sealed class VictoryResponse {
    /**
     * Victory achieved
     */
    data class Victory(
        val narration: String,
        val playerName: String,
        val finalStats: Map<String, String>
    ) : VictoryResponse()

    /**
     * Victory not yet achieved
     */
    data object NotYet : VictoryResponse()
}
