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
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.core.SpacePropertiesComponent
import kotlinx.coroutines.runBlocking

/**
 * Look for [ClientMovementHandlers] facade.
 */
object ClientMovementLookHandlers {

    fun handleLook(game: EngineGameClient, target: String?) {
        // V3-only: Use space-based navigation
        val space = game.worldState.getCurrentSpace()
        if (space == null) {
            game.emitEvent(GameEvent.System("Error: No current space", GameEvent.MessageLevel.ERROR))
            return
        }
        if (target == null) {
            lookAtRoom(game)
            return
        }
        lookAtTarget(game, space, target)
    }

    private fun lookAtRoom(game: EngineGameClient) {
        game.describeCurrentRoom()
        val groundItems = game.worldState.getEntitiesInSpace(game.worldState.player.currentRoomId)
            .filterIsInstance<Entity.Item>()
            .filter { it.isPickupable }
        if (groundItems.isNotEmpty()) {
            val itemsList = buildString {
                appendLine()
                appendLine("Items on the ground:")
                groundItems.forEach { item ->
                    appendLine("  - ${item.name}")
                }
            }
            game.emitEvent(GameEvent.Narrative(itemsList))
        } else {
            game.emitEvent(GameEvent.Narrative("\nYou don't see any items here."))
        }
    }

    private fun lookAtTarget(game: EngineGameClient, space: SpacePropertiesComponent, target: String) {
        val lower = target.lowercase()
        if (lookEntity(game, lower)) return
        if (lookInventory(game, lower)) return
        if (lookEquippedWeapon(game, lower)) return
        if (lookEquippedArmor(game, lower)) return
        lookScenery(game, space, target)
    }

    private fun lookEntity(game: EngineGameClient, lower: String): Boolean {
        val entities = game.worldState.getEntitiesInSpace(game.worldState.player.currentRoomId)
        val entity = entities.find { e ->
            e.name.lowercase().contains(lower) || e.id.lowercase().contains(lower)
        } ?: return false
        game.emitEvent(GameEvent.Narrative(entity.description))
        return true
    }

    private fun lookInventory(game: EngineGameClient, lower: String): Boolean {
        val item = game.worldState.player.inventory.find { invItem ->
            invItem.name.lowercase().contains(lower) || invItem.id.lowercase().contains(lower)
        } ?: return false
        game.emitEvent(GameEvent.Narrative(item.description))
        return true
    }

    private fun lookEquippedWeapon(game: EngineGameClient, lower: String): Boolean {
        val equippedWeapon = game.worldState.player.equippedWeapon ?: return false
        if (!equippedWeapon.name.lowercase().contains(lower) &&
            !equippedWeapon.id.lowercase().contains(lower)
        ) {
            return false
        }
        game.emitEvent(GameEvent.Narrative(equippedWeapon.description + " (equipped)"))
        return true
    }

    private fun lookEquippedArmor(game: EngineGameClient, lower: String): Boolean {
        val equippedArmor = game.worldState.player.equippedArmor ?: return false
        if (!equippedArmor.name.lowercase().contains(lower) &&
            !equippedArmor.id.lowercase().contains(lower)
        ) {
            return false
        }
        game.emitEvent(GameEvent.Narrative(equippedArmor.description + " (equipped)"))
        return true
    }

    private fun lookScenery(
        game: EngineGameClient,
        space: SpacePropertiesComponent,
        target: String
    ) {
        val sceneryDescription = runBlocking {
            game.sceneryGenerator.describeScenery(target, space, space.description)
        }
        if (sceneryDescription != null) {
            game.emitEvent(GameEvent.Narrative(sceneryDescription))
        } else {
            game.emitEvent(GameEvent.System("You don't see that here.", GameEvent.MessageLevel.INFO))
        }
    }
}
