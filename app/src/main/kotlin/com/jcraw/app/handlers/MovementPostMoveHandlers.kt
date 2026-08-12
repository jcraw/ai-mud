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
import com.jcraw.mud.core.GraphNodeComponent
import com.jcraw.mud.core.SpacePropertiesComponent
import com.jcraw.mud.reasoning.QuestAction
import kotlinx.coroutines.runBlocking

/**
 * Post-move side effects for [MovementHandlers] facade (app-only).
 * Order: lazy-fill → populate → frontier → quests → describe.
 */
object MovementPostMoveHandlers {

    fun postMove(game: MudGame, movementLabel: String, treasureExitMessage: String? = null) {
        println("You move $movementLabel.")
        treasureExitMessage?.let { println(it) }

        val currentSpace = game.worldState.getCurrentSpace()
        val currentNode = game.worldState.getCurrentGraphNode()
        val spaceId = game.worldState.player.currentRoomId

        lazyFillIfEmpty(game, spaceId, currentSpace, currentNode)

        if (currentSpace != null && currentNode != null) {
            MovementPostMovePopulate.populateSpaceIfNeeded(game, spaceId, currentSpace, currentNode)
        }

        MovementPostMoveFrontier.tryExpandFrontier(game)

        val currentSpaceId = game.worldState.player.currentRoomId
        game.trackQuests(QuestAction.VisitedRoom(currentSpaceId))

        game.describeCurrentRoom()
    }

    private fun lazyFillIfEmpty(
        game: MudGame,
        spaceId: String,
        currentSpace: SpacePropertiesComponent?,
        currentNode: GraphNodeComponent?
    ) {
        if (currentSpace == null || currentNode == null || currentSpace.description.isNotEmpty()) {
            return
        }
        val chunk = game.worldState.getChunk(currentNode.chunkId)
        if (chunk == null || game.worldGenerator == null) return
        runBlocking {
            val result = game.worldGenerator?.fillSpaceContent(currentSpace, currentNode, chunk)
            result?.onSuccess { filledSpace ->
                game.worldState = game.worldState.updateSpace(spaceId, filledSpace)
            }?.onFailure {
                println("(Content generation unavailable)")
            }
        }
    }
}
