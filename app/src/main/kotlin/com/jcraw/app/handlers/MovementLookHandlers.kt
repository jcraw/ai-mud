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
import kotlinx.coroutines.runBlocking

/**
 * Look for [MovementHandlers] facade.
 */
object MovementLookHandlers {

    fun handleLook(game: MudGame, target: String?) {
        if (target == null) {
            // Look at room - describeCurrentRoom already shows all entities including items
            game.describeCurrentRoom()
            return
        }
        lookAtTarget(game, target)
    }

    private fun lookAtTarget(game: MudGame, target: String) {
        // V3: Use space-based entity system
        val spaceId = game.worldState.player.currentRoomId
        val entities = game.worldState.getEntitiesInSpace(spaceId)
        val entity = entities.find { e ->
            e.name.lowercase().contains(target.lowercase()) ||
                e.id.lowercase().contains(target.lowercase())
        }
        if (entity != null) {
            println(entity.description)
            return
        }
        describeSceneryOrMiss(game, target, spaceId)
    }

    private fun describeSceneryOrMiss(game: MudGame, target: String, spaceId: String) {
        val space = game.worldState.getCurrentSpace()
        if (space == null) {
            println("You don't see that here.")
            return
        }
        val roomDescription = game.generateRoomDescription(space, spaceId)
        val sceneryDescription = runBlocking {
            game.sceneryGenerator.describeScenery(target, space, roomDescription)
        }
        if (sceneryDescription != null) {
            println(sceneryDescription)
        } else {
            println("You don't see that here.")
        }
    }
}
