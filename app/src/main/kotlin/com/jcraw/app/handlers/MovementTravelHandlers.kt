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
import com.jcraw.mud.core.SkillComponent
import com.jcraw.mud.core.SpacePropertiesComponent
import com.jcraw.mud.core.TreasureRoomComponent
import com.jcraw.mud.core.world.ExitData

/**
 * Travel (non-cardinal exits) for [MovementHandlers] facade.
 */
object MovementTravelHandlers {

    fun handleTravel(game: MudGame, rawDirection: String) {
        val normalized = rawDirection.trim()
        if (normalized.isEmpty()) {
            println("Travel where?")
            return
        }
        val cardinal = Direction.fromString(normalized)
        if (cardinal != null) {
            MovementMoveHandlers.handleMove(game, cardinal)
            return
        }
        travelByExitName(game, normalized)
    }

    private fun travelByExitName(game: MudGame, normalized: String) {
        val previousSpaceId = game.worldState.player.currentRoomId
        val previousTreasureRoom = game.worldState.getTreasureRoom(previousSpaceId)
        val playerSkills = game.skillManager.getSkillComponent(game.worldState.player.id)
        if (tryDirectExitMove(game, normalized, playerSkills)) return
        val space = game.worldState.getCurrentSpace()
        if (space == null) {
            println("You can't go that way.")
            return
        }
        travelResolvedExit(game, normalized, space, playerSkills, previousSpaceId, previousTreasureRoom)
    }

    private fun tryDirectExitMove(
        game: MudGame,
        normalized: String,
        playerSkills: SkillComponent
    ): Boolean {
        val directMove = game.worldState.movePlayerByExit(normalized, playerSkills)
        if (directMove == null) return false
        game.worldState = directMove
        // Treasure room exit finalization disabled - players can return and swap anytime
        MovementPostMoveHandlers.postMove(game, normalized, null)
        return true
    }

    private fun travelResolvedExit(
        game: MudGame,
        normalized: String,
        space: SpacePropertiesComponent,
        playerSkills: SkillComponent,
        previousSpaceId: String,
        previousTreasureRoom: TreasureRoomComponent?
    ) {
        val resolvedExit = space.resolveExit(normalized, game.worldState.player, playerSkills)
        if (resolvedExit == null) {
            println("You can't go that way.")
            return
        }
        if (tryFallbackDirectionMove(game, resolvedExit, playerSkills, previousSpaceId, previousTreasureRoom)) {
            return
        }
        completeFormedPassage(game, resolvedExit, previousSpaceId, previousTreasureRoom)
    }

    private fun tryFallbackDirectionMove(
        game: MudGame,
        resolvedExit: ExitData,
        playerSkills: SkillComponent,
        previousSpaceId: String,
        previousTreasureRoom: TreasureRoomComponent?
    ): Boolean {
        val fallback = game.worldState.movePlayerByExit(resolvedExit.direction, playerSkills)
        if (fallback == null) return false
        game.worldState = fallback
        val treasureExitMessage = MovementTreasureExit.finalizeTreasureRoomExit(
            game, previousSpaceId, previousTreasureRoom
        )
        MovementPostMoveHandlers.postMove(game, resolvedExit.direction, treasureExitMessage)
        return true
    }

    private fun completeFormedPassage(
        game: MudGame,
        resolvedExit: ExitData,
        previousSpaceId: String,
        previousTreasureRoom: TreasureRoomComponent?
    ) {
        val targetSpace = game.worldState.getSpace(resolvedExit.targetId)
        if (targetSpace == null) {
            println("That passage hasn't fully formed yet.")
            return
        }
        val updatedPlayer = game.worldState.player.moveToRoom(resolvedExit.targetId)
        game.worldState = game.worldState.updatePlayer(updatedPlayer)
        val treasureExitMessage = MovementTreasureExit.finalizeTreasureRoomExit(
            game, previousSpaceId, previousTreasureRoom
        )
        MovementPostMoveHandlers.postMove(game, resolvedExit.direction, treasureExitMessage)
    }
}
