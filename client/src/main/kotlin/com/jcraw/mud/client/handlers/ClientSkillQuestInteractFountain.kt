@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList")

package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.core.PlayerState

/**
 * Healing fountain interaction fragment for client interact cluster.
 */
object ClientSkillQuestInteractFountain {

    fun handleFountainInteraction(game: EngineGameClient, fountain: Entity.Feature) {
        if (!isInSafeZone(game)) {
            game.emitEvent(GameEvent.System(
                "The fountain's magic lies dormant outside the safety of town.",
                GameEvent.MessageLevel.INFO
            ))
            return
        }
        val player = game.worldState.player
        if (player.health >= player.maxHealth) {
            emitAlreadyFull(game, fountain)
            return
        }
        restoreFullHealth(game, player)
    }

    private fun isInSafeZone(game: EngineGameClient): Boolean {
        val spaceId = game.worldState.player.currentRoomId
        return game.worldState.spaces[spaceId]?.isSafeZone == true
    }

    private fun emitAlreadyFull(game: EngineGameClient, fountain: Entity.Feature) {
        game.emitEvent(GameEvent.Narrative(
            "You drink from the ${fountain.name}. The water is cool and refreshing,\nthough you are already at full health."
        ))
    }

    private fun restoreFullHealth(game: EngineGameClient, player: PlayerState) {
        val healed = player.maxHealth - player.health
        val newPlayer = player.copy(health = player.maxHealth)
        game.worldState = game.worldState.updatePlayer(newPlayer)
        val output = buildString {
            appendLine("You cup the luminescent water and drink deeply.")
            appendLine("Warmth spreads through your body, mending wounds and easing pain.")
            appendLine("HP fully restored: ${newPlayer.health}/${newPlayer.maxHealth} (+$healed)")
        }
        game.emitEvent(GameEvent.Narrative(output))
    }
}
