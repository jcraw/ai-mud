@file:Suppress("ReturnCount")

package com.jcraw.app

import com.jcraw.mud.reasoning.death.PlayerRespawnService

/**
 * Player death and respawn prompt flow for [MudGame]. Pure extract.
 */
object MudGameDeathRespawn {

    sealed interface RespawnState {
        data class AwaitingConfirmation(val pending: PlayerRespawnService.PendingRespawn) : RespawnState
        data class AwaitingName(val pending: PlayerRespawnService.PendingRespawn) : RespawnState
    }

    /**
     * Handle player death with permadeath mechanics:
     * - Persist corpse with player's items at death location
     * - Prompt player to continue with a brand new character
     * - Respawn at starting location once player provides a new name
     */
    fun handlePlayerDeath(game: MudGame) {
        if (game.respawnState != null) return

        val pending = game.playerRespawnService.createPendingRespawn(
            worldState = game.worldState,
            playerId = game.worldState.player.id,
            spawnSpaceIdOverride = game.worldState.gameProperties["starting_space"]
        ).getOrElse { error ->
            println("\nFailed to process permadeath: ${error.message}")
            game.running = false
            return
        }

        game.respawnState = RespawnState.AwaitingConfirmation(pending)

        println()
        println(pending.deathResult.narration)
        println("\nContinue as new character (Y/N)?")
    }

    fun handleRespawnInput(game: MudGame, input: String) {
        val state = game.respawnState ?: return
        when (state) {
            is RespawnState.AwaitingConfirmation -> handleConfirmation(game, state, input)
            is RespawnState.AwaitingName -> handleName(game, state, input)
        }
    }

    private fun handleConfirmation(
        game: MudGame,
        state: RespawnState.AwaitingConfirmation,
        input: String
    ) {
        when (input.lowercase()) {
            "y", "yes" -> {
                game.respawnState = RespawnState.AwaitingName(state.pending)
                println("Name your new character:")
            }
            "n", "no" -> {
                println("You accept your fate. Game over.")
                game.respawnState = null
                game.running = false
            }
            else -> println("Please answer Y or N.")
        }
    }

    private fun handleName(
        game: MudGame,
        state: RespawnState.AwaitingName,
        input: String
    ) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            println("Name cannot be blank.")
            return
        }

        val outcome = game.playerRespawnService.completeRespawn(game.worldState, state.pending, trimmed)
        outcome.onFailure { error ->
            println("Failed to respawn: ${error.message}")
        }.onSuccess { result ->
            game.worldState = result.worldState
            game.respawnState = null
            game.lastConversationNpcId = null
            println(result.respawnMessage)
            game.describeCurrentRoom()
        }
    }
}
