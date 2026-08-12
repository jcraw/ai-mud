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
import com.jcraw.mud.core.Difficulty
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.GraphNodeComponent
import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.SkillCheckResult
import com.jcraw.mud.core.StatType

/**
 * Search for [MovementHandlers] facade.
 * Success body in [MovementSearchSuccess].
 */
object MovementSearchHandlers {

    fun handleSearch(game: MudGame, target: String?) {
        // V3: Check if using graph-based world with hidden exits
        val currentNode = game.worldState.getCurrentGraphNode()
        val player = game.worldState.player
        val hasV3Graph = currentNode != null

        println("\nYou search the area carefully${if (target != null) ", focusing on the $target" else ""}...")

        val result = game.skillCheckResolver.checkPlayer(
            player,
            StatType.WISDOM,
            Difficulty.MEDIUM  // DC 15 for finding hidden items/exits
        )
        printSearchRoll(result)

        if (result.success) {
            println("\n✅ Success!")
            MovementSearchSuccess.onSearchSuccess(game, player, currentNode, hasV3Graph)
        } else {
            println("\n❌ Failure!")
            println("You don't find anything of interest.")
        }
    }

    private fun printSearchRoll(result: SkillCheckResult) {
        println("\nRolling Perception check...")
        println("d20 roll: ${result.roll} + WIS modifier: ${result.modifier} = ${result.total} vs DC ${result.dc}")
        if (result.isCriticalSuccess) {
            println("\n🎲 CRITICAL SUCCESS! (Natural 20)")
        } else if (result.isCriticalFailure) {
            println("\n💀 CRITICAL FAILURE! (Natural 1)")
        }
    }
}

/**
 * Search success path fragment (hidden exits + items).
 */
internal object MovementSearchSuccess {

    fun onSearchSuccess(
        game: MudGame,
        player: PlayerState,
        currentNode: GraphNodeComponent?,
        hasV3Graph: Boolean
    ) {
        var foundSomething = false
        if (hasV3Graph && currentNode != null) {
            if (revealFirstHiddenExit(game, player, currentNode)) {
                foundSomething = true
            }
        }
        if (reportFoundItems(game)) {
            foundSomething = true
        }
        if (!foundSomething) {
            println("You don't find anything hidden here.")
        }
    }

    private fun revealFirstHiddenExit(
        game: MudGame,
        player: PlayerState,
        currentNode: GraphNodeComponent
    ): Boolean {
        val hiddenExits = currentNode.neighbors.filter { edge ->
            val edgeId = edge.edgeId(currentNode.id)
            edge.hidden && !player.hasRevealedExit(edgeId)
        }
        if (hiddenExits.isEmpty()) return false
        val revealedExit = hiddenExits.first()
        val edgeId = revealedExit.edgeId(currentNode.id)
        game.worldState = game.worldState.updatePlayer(player.revealExit(edgeId))
        println("\n🔍 You discover a hidden exit: ${revealedExit.direction}!")
        return true
    }

    private fun reportFoundItems(game: MudGame): Boolean {
        val spaceId = game.worldState.player.currentRoomId
        val entities = game.worldState.getEntitiesInSpace(spaceId)
        val hiddenItems = entities.filterIsInstance<Entity.Item>().filter { !it.isPickupable }
        val pickupableItems = entities.filterIsInstance<Entity.Item>().filter { it.isPickupable }
        var found = false
        if (pickupableItems.isNotEmpty()) {
            println("\nYou find the following items:")
            pickupableItems.forEach { item ->
                println("  - ${item.name}: ${item.description}")
            }
            found = true
        }
        if (hiddenItems.isNotEmpty()) {
            println("\nYou also notice some interesting features:")
            hiddenItems.forEach { item ->
                println("  - ${item.name}: ${item.description}")
            }
            found = true
        }
        return found
    }
}
