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
import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.core.SkillComponent
import com.jcraw.mud.core.SpacePropertiesComponent
import com.jcraw.mud.core.TreasureRoomComponent
import com.jcraw.mud.core.world.ExitData

/**
 * Travel for [ClientMovementHandlers] facade.
 */
object ClientMovementTravelHandlers {

    fun handleTravel(game: EngineGameClient, rawDirection: String) {
        val normalized = rawDirection.trim()
        if (normalized.isEmpty()) {
            game.emitEvent(GameEvent.System("Travel where?", GameEvent.MessageLevel.WARNING))
            return
        }
        Direction.fromString(normalized)?.let {
            ClientMovementMoveHandlers.handleMove(game, it)
            return
        }
        travelByExitName(game, normalized)
    }

    private fun travelByExitName(game: EngineGameClient, normalized: String) {
        val previousSpaceId = game.worldState.player.currentRoomId
        val previousTreasureRoom = game.worldState.getTreasureRoom(previousSpaceId)
        val playerSkills = game.skillManager.getSkillComponent(game.worldState.player.id)
        if (tryDirectExitMove(game, normalized, playerSkills)) return
        val space = game.worldState.getCurrentSpace()
        if (space == null) {
            game.emitEvent(GameEvent.System("You can't go that way.", GameEvent.MessageLevel.WARNING))
            return
        }
        travelResolvedExit(game, normalized, space, playerSkills, previousSpaceId, previousTreasureRoom)
    }

    private fun tryDirectExitMove(
        game: EngineGameClient,
        normalized: String,
        playerSkills: SkillComponent
    ): Boolean {
        val edgeMove = game.worldState.movePlayerByExit(normalized, playerSkills)
        if (edgeMove == null) return false
        game.worldState = edgeMove
        // Treasure room exit finalization disabled - players can return and swap anytime
        game.handlePlayerMovement(normalized, null)
        return true
    }

    private fun travelResolvedExit(
        game: EngineGameClient,
        normalized: String,
        space: SpacePropertiesComponent,
        playerSkills: SkillComponent,
        previousSpaceId: String,
        previousTreasureRoom: TreasureRoomComponent?
    ) {
        val resolvedExit = space.resolveExit(normalized, game.worldState.player, playerSkills)
        if (resolvedExit == null) {
            game.emitEvent(GameEvent.System("You can't go that way.", GameEvent.MessageLevel.WARNING))
            return
        }
        if (tryFallbackDirectionMove(game, resolvedExit, playerSkills, previousSpaceId, previousTreasureRoom)) {
            return
        }
        completeManualTravel(game, resolvedExit, previousSpaceId, previousTreasureRoom)
    }

    private fun tryFallbackDirectionMove(
        game: EngineGameClient,
        resolvedExit: ExitData,
        playerSkills: SkillComponent,
        previousSpaceId: String,
        previousTreasureRoom: TreasureRoomComponent?
    ): Boolean {
        val fallback = game.worldState.movePlayerByExit(resolvedExit.direction, playerSkills)
        if (fallback == null) return false
        game.worldState = fallback
        val treasureExitMessage = ClientMovementTreasureExit.finalizeTreasureRoomExit(
            game, previousSpaceId, previousTreasureRoom
        )
        game.handlePlayerMovement(resolvedExit.direction, treasureExitMessage)
        return true
    }

    private fun completeManualTravel(
        game: EngineGameClient,
        resolvedExit: ExitData,
        previousSpaceId: String,
        previousTreasureRoom: TreasureRoomComponent?
    ) {
        val targetNode = game.ensureGraphNodeLoaded(resolvedExit.targetId)
        if (targetNode == null) {
            game.emitEvent(GameEvent.System("That passage isn't available yet.", GameEvent.MessageLevel.WARNING))
            return
        }
        val targetSpace = game.loadSpace(resolvedExit.targetId)
            ?: game.worldState.getSpace(resolvedExit.targetId)
        if (targetSpace == null) {
            game.emitEvent(GameEvent.System("That passage feels incomplete.", GameEvent.MessageLevel.WARNING))
            return
        }
        applyManualTravelState(game, resolvedExit, targetNode, targetSpace, previousSpaceId, previousTreasureRoom)
    }

    private fun applyManualTravelState(
        game: EngineGameClient,
        resolvedExit: ExitData,
        targetNode: com.jcraw.mud.core.GraphNodeComponent,
        targetSpace: SpacePropertiesComponent,
        previousSpaceId: String,
        previousTreasureRoom: TreasureRoomComponent?
    ) {
        val updatedPlayer = game.worldState.player.moveToRoom(resolvedExit.targetId)
        game.worldState = game.worldState
            .updatePlayer(updatedPlayer)
            .updateSpace(resolvedExit.targetId, targetSpace)
            .updateGraphNode(resolvedExit.targetId, targetNode)
        val treasureExitMessage = ClientMovementTreasureExit.finalizeTreasureRoomExit(
            game, previousSpaceId, previousTreasureRoom
        )
        game.handlePlayerMovement(resolvedExit.direction, treasureExitMessage)
    }
}
