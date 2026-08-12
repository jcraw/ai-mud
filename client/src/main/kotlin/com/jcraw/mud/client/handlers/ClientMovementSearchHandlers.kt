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
import com.jcraw.mud.core.Difficulty
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.core.GraphNodeComponent
import com.jcraw.mud.core.SkillCheckResult
import com.jcraw.mud.core.StatType

/**
 * Search for [ClientMovementHandlers] facade.
 * Success narrative body in [ClientMovementSearchSuccess].
 */
object ClientMovementSearchHandlers {

    fun handleSearch(game: EngineGameClient, target: String?) {
        // V3-only: Use space-based navigation
        val space = game.worldState.getCurrentSpace()
        val node = game.worldState.getCurrentGraphNode()
        if (space == null || node == null) {
            game.emitEvent(GameEvent.System("Error: No current space", GameEvent.MessageLevel.ERROR))
            return
        }
        val searchMessage = "You search the area carefully${if (target != null) ", focusing on the $target" else ""}..."
        val result = game.skillCheckResolver.checkPlayer(
            game.worldState.player,
            StatType.WISDOM,
            Difficulty.MEDIUM
        )
        val narrative = buildSearchNarrative(game, node, searchMessage, result)
        game.emitEvent(GameEvent.Narrative(narrative))
    }

    private fun buildSearchNarrative(
        game: EngineGameClient,
        node: GraphNodeComponent,
        searchMessage: String,
        result: SkillCheckResult
    ): String = buildString {
        appendLine(searchMessage)
        appendLine()
        appendLine("Rolling Perception check...")
        appendLine("d20 roll: ${result.roll} + WIS modifier: ${result.modifier} = ${result.total} vs DC ${result.dc}")
        appendLine()
        appendCritNotes(result)
        if (result.success) {
            appendLine("✅ Success!")
            appendLine()
            ClientMovementSearchSuccess.appendSuccess(this, game, node)
        } else {
            appendLine("❌ Failure!")
            appendLine("You don't find anything of interest.")
        }
    }

    private fun StringBuilder.appendCritNotes(result: SkillCheckResult) {
        if (result.isCriticalSuccess) {
            appendLine("🎲 CRITICAL SUCCESS! (Natural 20)")
        } else if (result.isCriticalFailure) {
            appendLine("💀 CRITICAL FAILURE! (Natural 1)")
        }
    }
}

/**
 * Client search success narrative fragment.
 */
internal object ClientMovementSearchSuccess {

    fun appendSuccess(
        builder: StringBuilder,
        game: EngineGameClient,
        node: GraphNodeComponent
    ) {
        val entities = game.worldState.getEntitiesInSpace(game.worldState.player.currentRoomId)
        val hiddenItems = entities.filterIsInstance<Entity.Item>().filter { !it.isPickupable }
        val pickupableItems = entities.filterIsInstance<Entity.Item>().filter { it.isPickupable }
        var foundSomething = false
        if (appendPickupable(builder, pickupableItems)) foundSomething = true
        if (appendHiddenFeatures(builder, hiddenItems)) foundSomething = true
        if (revealHiddenExits(builder, game, node)) foundSomething = true
        if (!foundSomething) {
            builder.appendLine("You don't find anything hidden here.")
        }
    }

    private fun appendPickupable(builder: StringBuilder, items: List<Entity.Item>): Boolean {
        if (items.isEmpty()) return false
        builder.appendLine("You find the following items:")
        items.forEach { item ->
            builder.appendLine("  - ${item.name}: ${item.description}")
        }
        return true
    }

    private fun appendHiddenFeatures(builder: StringBuilder, items: List<Entity.Item>): Boolean {
        if (items.isEmpty()) return false
        builder.appendLine()
        builder.appendLine("You also notice some interesting features:")
        items.forEach { item ->
            builder.appendLine("  - ${item.name}: ${item.description}")
        }
        return true
    }

    private fun revealHiddenExits(
        builder: StringBuilder,
        game: EngineGameClient,
        node: GraphNodeComponent
    ): Boolean {
        val hiddenExits = node.neighbors.filter { edge ->
            val edgeId = edge.edgeId(node.id)
            edge.hidden && !game.worldState.player.hasRevealedExit(edgeId)
        }
        if (hiddenExits.isEmpty()) return false
        val firstExit = hiddenExits.first()
        val edgeId = firstExit.edgeId(node.id)
        val updatedPlayer = game.worldState.player.revealExit(edgeId)
        game.worldState = game.worldState.updatePlayer(updatedPlayer)
        builder.appendLine()
        builder.appendLine("Hidden exits:")
        hiddenExits.forEach { exit ->
            builder.appendLine("  - ${exit.direction} (now marked on your map)")
        }
        return true
    }
}
