@file:Suppress("ReturnCount")

package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.core.Entity
import com.jcraw.mud.reasoning.QuestAction
import com.jcraw.mud.reasoning.inventory.EntityNameMatch
import com.jcraw.mud.reasoning.inventory.FloorItemTakeApply
import com.jcraw.mud.reasoning.inventory.FloorItemTakeBatch

/**
 * Floor take / take-all for [ItemHandlers] facade.
 */
object ItemTakeHandlers {

    fun handleTake(game: MudGame, target: String) {
        val spaceId = game.worldState.player.currentRoomId
        val treasureRoom = game.worldState.getTreasureRoom(spaceId)
        if (treasureRoom != null && !treasureRoom.hasBeenLooted) {
            TreasureRoomHandlers.handleTakeTreasure(game, target)
            return
        }
        val entities = game.worldState.getEntitiesInSpace(spaceId)
        val item = EntityNameMatch.findItem(entities, target)
        if (item == null) {
            reportMissingTakeTarget(entities, target)
            return
        }
        if (!item.isPickupable) {
            println("That's part of the environment and can't be taken.")
            return
        }
        applyFloorTake(game, spaceId, item)
    }

    fun handleTakeAll(game: MudGame) {
        val spaceId = game.worldState.player.currentRoomId
        val items = game.worldState.getEntitiesInSpace(spaceId)
            .filterIsInstance<Entity.Item>()
            .filter { it.isPickupable }
        if (items.isEmpty()) {
            println("There are no items to take here.")
            return
        }
        val batch = FloorItemTakeBatch.takeMany(game.worldState, spaceId, items) { item ->
            floorTakeTemplates(game.itemRepository, item)
        }
        game.worldState = batch.world
        batch.taken.forEach { taken -> println("You take the ${taken.itemName}.") }
        batch.failed.forEach { fail -> println(fail.message) }
        finishTakeAll(game, batch.taken.size, batch.taken.map { it.floorEntityId })
    }

    private fun finishTakeAll(game: MudGame, takenCount: Int, takenEntityIds: List<String>) {
        if (takenCount <= 0) return
        println("\nYou took $takenCount item${if (takenCount > 1) "s" else ""}.")
        takenEntityIds.forEach { entityId ->
            game.trackQuests(QuestAction.CollectedItem(entityId))
        }
    }

    private fun reportMissingTakeTarget(entities: List<Entity>, target: String) {
        if (EntityNameMatch.anyNameContains(entities, target)) {
            println("That's part of the environment and can't be taken.")
        } else {
            println("You don't see that here.")
        }
    }

    private fun applyFloorTake(game: MudGame, spaceId: String, item: Entity.Item) {
        val templates = floorTakeTemplates(game.itemRepository, item)
        when (val result = FloorItemTakeBatch.apply(game.worldState, spaceId, item, templates)) {
            is FloorItemTakeApply.Result.Success -> {
                game.worldState = result.world
                println("You take the ${result.itemName}.")
                game.trackQuests(QuestAction.CollectedItem(item.id))
            }
            is FloorItemTakeApply.Result.Failure -> println(result.message)
        }
    }

}
