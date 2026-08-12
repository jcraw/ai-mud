@file:Suppress(
    "ReturnCount",
    "MagicNumber",
    "MaxLineLength",
    "TooManyFunctions",
    "LongMethod",
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "LongParameterList"
)

package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.Direction
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.GameEvent

/**
 * Move / performMove for [ClientMovementHandlers] facade.
 * Flee body lives in [ClientMovementFleeHandlers].
 */
object ClientMovementMoveHandlers {

    fun handleMove(game: EngineGameClient, direction: Direction) {
        val currentSpaceId = game.worldState.player.currentRoomId
        val hostiles = getHostileEntitiesInRoom(game, currentSpaceId)
        if (hostiles.isNotEmpty()) {
            ClientMovementFleeHandlers.handleFlee(game, direction, hostiles)
            return
        }
        performMove(game, direction)
    }

    fun performMove(game: EngineGameClient, direction: Direction) {
        val previousSpaceId = game.worldState.player.currentRoomId
        @Suppress("UNUSED_VARIABLE")
        val previousTreasureRoom = game.worldState.getTreasureRoom(previousSpaceId)
        val playerSkills = game.skillManager.getSkillComponent(game.worldState.player.id)
        val newState = game.worldState.movePlayerV3(direction, playerSkills)
        if (newState == null) {
            game.emitEvent(GameEvent.System("You can't go that way.", GameEvent.MessageLevel.WARNING))
            return
        }
        game.worldState = newState
        // Treasure room exit finalization disabled - players can return and swap anytime
        // val treasureExitMessage = finalizeTreasureRoomExit(game, previousSpaceId, previousTreasureRoom)
        game.handlePlayerMovement(direction.displayName, null)
    }

    fun getHostileEntitiesInRoom(game: EngineGameClient, spaceId: String): List<String> {
        val turnQueue = game.turnQueue ?: return emptyList()
        val entitiesInRoom = game.worldState.getEntitiesInSpace(spaceId)
        return entitiesInRoom
            .filterIsInstance<Entity.NPC>()
            .filter { npc -> turnQueue.contains(npc.id) }
            .map { it.id }
    }
}
