package com.jcraw.mud.client

import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.QuestStatus
import com.jcraw.mud.reasoning.QuestAction
import com.jcraw.mud.reasoning.death.PlayerRespawnService

/**
 * Quest tracking, max-HP sync, and death helpers for [EngineGameClient]. Pure extract.
 */
object ClientQuestDeathSupport {

    suspend fun syncPlayerMaxHp(game: EngineGameClient) {
        val player = game.worldState.player
        val skillComponent = game.skillManager.getSkillComponent(player.id)
        val correctMaxHp = player.calculateMaxHp(skillComponent)
        if (player.maxHealth != correctMaxHp) {
            game.worldState = game.worldState.updatePlayer(player.updateMaxHp(correctMaxHp))
            game.emitEvent(
                GameEvent.System(
                    "Your maximum health has changed: ${player.maxHealth} → $correctMaxHp HP",
                    GameEvent.MessageLevel.INFO
                )
            )
        }
    }

    fun trackQuests(game: EngineGameClient, action: QuestAction) {
        val (updatedPlayer, updatedWorld) = game.questTracker.updateQuestsAfterAction(
            game.worldState.player,
            game.worldState,
            action
        )
        if (updatedPlayer == game.worldState.player) return
        emitQuestProgressEvents(game, updatedPlayer)
        game.worldState = updatedWorld.updatePlayer(updatedPlayer)
    }

    private fun emitQuestProgressEvents(game: EngineGameClient, updatedPlayer: PlayerState) {
        updatedPlayer.activeQuests.forEach { quest ->
            val oldQuest = game.worldState.player.getQuest(quest.id) ?: return@forEach
            quest.objectives.zip(oldQuest.objectives).forEach { (newObj, oldObj) ->
                if (newObj.isCompleted && !oldObj.isCompleted) {
                    game.emitEvent(
                        GameEvent.Quest("\n✓ Quest objective completed: ${newObj.description}")
                    )
                }
            }
            if (quest.status == QuestStatus.COMPLETED && oldQuest.status == QuestStatus.ACTIVE) {
                game.emitEvent(
                    GameEvent.Quest(
                        "\n🎉 Quest completed: ${quest.title}\n" +
                            "Use 'claim ${quest.id}' to collect your reward!"
                    )
                )
            }
        }
    }

    fun handlePlayerDeath(game: EngineGameClient) {
        if (game.respawnState != null) return
        val pending = game.playerRespawnService.createPendingRespawn(
            worldState = game.worldState,
            playerId = game.worldState.player.id,
            spawnSpaceIdOverride = game.worldState.gameProperties["starting_space"]
        ).getOrElse { error ->
            game.emitEvent(
                GameEvent.System(
                    "Failed to process permadeath: ${error.message}",
                    GameEvent.MessageLevel.ERROR
                )
            )
            game.running = false
            return
        }
        game.respawnState = RespawnState.AwaitingConfirmation(pending)
        game.emitEvent(GameEvent.Combat("\n${pending.deathResult.narration}"))
        game.emitEvent(
            GameEvent.System("Continue as new character? (Y/N)", GameEvent.MessageLevel.INFO)
        )
    }

    fun handleRespawnInput(game: EngineGameClient, rawInput: String) {
        val state = game.respawnState ?: return
        val input = rawInput.trim()
        when (state) {
            is RespawnState.AwaitingConfirmation -> handleRespawnConfirmation(game, state, input)
            is RespawnState.AwaitingName -> handleRespawnName(game, state, input)
        }
    }

    private fun handleRespawnConfirmation(
        game: EngineGameClient,
        state: RespawnState.AwaitingConfirmation,
        input: String
    ) {
        when (input.lowercase()) {
            "y", "yes" -> {
                game.respawnState = RespawnState.AwaitingName(state.pending)
                game.emitEvent(
                    GameEvent.System("Name your new character:", GameEvent.MessageLevel.INFO)
                )
            }
            "n", "no" -> {
                game.emitEvent(
                    GameEvent.System("You accept your fate. Game over.", GameEvent.MessageLevel.INFO)
                )
                game.respawnState = null
                game.running = false
            }
            else -> game.emitEvent(
                GameEvent.System("Please answer Y or N.", GameEvent.MessageLevel.WARNING)
            )
        }
    }

    private fun handleRespawnName(
        game: EngineGameClient,
        state: RespawnState.AwaitingName,
        input: String
    ) {
        if (input.isBlank()) {
            game.emitEvent(
                GameEvent.System("Name cannot be blank.", GameEvent.MessageLevel.WARNING)
            )
            return
        }
        game.playerRespawnService.completeRespawn(game.worldState, state.pending, input)
            .onFailure { error ->
                game.emitEvent(
                    GameEvent.System(
                        "Failed to respawn: ${error.message}",
                        GameEvent.MessageLevel.ERROR
                    )
                )
            }
            .onSuccess { result -> applyRespawnSuccess(game, result) }
    }

    private fun applyRespawnSuccess(
        game: EngineGameClient,
        result: PlayerRespawnService.RespawnOutcome
    ) {
        game.worldState = result.worldState
        game.respawnState = null
        game.lastConversationNpcId = null
        game.emitEvent(GameEvent.System(result.respawnMessage))
        game.emitEvent(
            GameEvent.StatusUpdate(
                hp = game.worldState.player.health,
                maxHp = game.worldState.player.maxHealth,
                location = game.worldState.player.currentRoomId
            )
        )
        game.describeCurrentRoom()
    }

    sealed interface RespawnState {
        data class AwaitingConfirmation(
            val pending: PlayerRespawnService.PendingRespawn
        ) : RespawnState
        data class AwaitingName(
            val pending: PlayerRespawnService.PendingRespawn
        ) : RespawnState
    }
}
