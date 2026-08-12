@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList")

package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.PlayerState

/**
 * Healing fountain interaction fragment for interact cluster.
 */
object SkillQuestInteractFountain {

    fun handleFountainInteraction(game: MudGame, fountain: Entity.Feature) {
        if (!isInSafeZone(game)) {
            println("\nThe fountain's magic lies dormant outside the safety of town.")
            return
        }
        val player = game.worldState.player
        if (player.health >= player.maxHealth) {
            printAlreadyFull(fountain)
            return
        }
        restoreFullHealth(game, player)
    }

    private fun isInSafeZone(game: MudGame): Boolean {
        val spaceId = game.worldState.player.currentRoomId
        return game.worldState.spaces[spaceId]?.isSafeZone == true
    }

    private fun printAlreadyFull(fountain: Entity.Feature) {
        println("\nYou drink from the ${fountain.name}. The water is cool and refreshing,")
        println("though you are already at full health.")
    }

    private fun restoreFullHealth(game: MudGame, player: PlayerState) {
        val healed = player.maxHealth - player.health
        val newPlayer = player.copy(health = player.maxHealth)
        game.worldState = game.worldState.updatePlayer(newPlayer)
        println("\nYou cup the luminescent water and drink deeply.")
        println("Warmth spreads through your body, mending wounds and easing pain.")
        println("HP fully restored: ${newPlayer.health}/${newPlayer.maxHealth} (+$healed)")
    }
}
