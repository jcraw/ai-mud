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

package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.core.Direction
import com.jcraw.mud.core.Entity
import com.jcraw.mud.config.GameConfig

/**
 * Move / performMove for [MovementHandlers] facade.
 * Flee body lives in [MovementFleeHandlers].
 */
object MovementMoveHandlers {

    fun handleMove(game: MudGame, direction: Direction) {
        val currentSpaceId = game.worldState.player.currentRoomId
        val hostiles = getHostileEntitiesInRoom(game, currentSpaceId)
        if (hostiles.isNotEmpty()) {
            MovementFleeHandlers.handleFlee(game, direction, hostiles)
            return
        }
        performMove(game, direction)
    }

    fun performMove(game: MudGame, direction: Direction) {
        // V3: Graph-based navigation
        val previousSpaceId = game.worldState.player.currentRoomId
        @Suppress("UNUSED_VARIABLE")
        val previousTreasureRoom = game.worldState.getTreasureRoom(previousSpaceId)
        val playerSkills = game.skillManager.getSkillComponent(game.worldState.player.id)
        logMoveAttempt(game, direction, previousSpaceId)
        val newState = game.worldState.movePlayerV3(direction, playerSkills)
        if (newState == null) {
            logMoveFailure(game, direction, previousSpaceId)
            println("You can't go that way.")
            return
        }
        game.worldState = newState
        // Treasure room exit finalization disabled - players can return and swap anytime
        // val treasureExitMessage = finalizeTreasureRoomExit(game, previousSpaceId, previousTreasureRoom)
        MovementPostMoveHandlers.postMove(game, direction.displayName, null)
    }

    fun getHostileEntitiesInRoom(game: MudGame, spaceId: String): List<String> {
        val turnQueue = game.turnQueue ?: return emptyList()
        val entitiesInRoom = game.worldState.getEntitiesInSpace(spaceId)
        return entitiesInRoom
            .filterIsInstance<Entity.NPC>()
            .filter { npc -> turnQueue.contains(npc.id) }
            .map { it.id }
    }

    private fun logMoveAttempt(game: MudGame, direction: Direction, previousSpaceId: String) {
        if (!GameConfig.logLLMCalls) return
        val currentNode = game.worldState.graphNodes[previousSpaceId]
        println("[MOVE DEBUG] Attempting to move ${direction.displayName}")
        println("[MOVE DEBUG] Current space: $previousSpaceId")
        println("[MOVE DEBUG] Current node edges: ${currentNode?.neighbors?.map { "${it.direction} -> ${it.targetId}" }}")
        println("[MOVE DEBUG] Current node position: ${currentNode?.position}")
        println("[MOVE DEBUG] Target spaces in WorldState: ${game.worldState.spaces.keys.take(5)}")
    }

    private fun logMoveFailure(game: MudGame, direction: Direction, previousSpaceId: String) {
        if (!GameConfig.logLLMCalls) return
        val currentNode = game.worldState.graphNodes[previousSpaceId]
        val edge = currentNode?.neighbors?.find {
            it.direction.equals(direction.displayName, ignoreCase = true)
        }
        if (edge != null) {
            val targetExists = game.worldState.spaces.containsKey(edge.targetId)
            println("[MOVE DEBUG] Move failed! Edge found: ${edge.direction} -> ${edge.targetId}, Target space exists: $targetExists")
        } else {
            println("[MOVE DEBUG] Move failed! No matching edge found for direction: ${direction.displayName}")
        }
    }
}
