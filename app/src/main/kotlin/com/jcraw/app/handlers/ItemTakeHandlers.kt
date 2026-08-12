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
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.WorldState
import com.jcraw.mud.reasoning.QuestAction
import com.jcraw.mud.reasoning.inventory.FloorItemTakeApply

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
        val item = findFloorItem(game, spaceId, target)
        if (item == null) {
            reportMissingTakeTarget(game, spaceId, target)
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
        val (takenCount, takenEntityIds, currentState) = takeAllItems(game, spaceId, items)
        game.worldState = currentState
        finishTakeAll(game, takenCount, takenEntityIds)
    }

    private fun finishTakeAll(game: MudGame, takenCount: Int, takenEntityIds: List<String>) {
        if (takenCount <= 0) return
        println("\nYou took $takenCount item${if (takenCount > 1) "s" else ""}.")
        takenEntityIds.forEach { entityId ->
            game.trackQuests(QuestAction.CollectedItem(entityId))
        }
    }

    private fun findFloorItem(game: MudGame, spaceId: String, target: String): Entity.Item? =
        game.worldState.getEntitiesInSpace(spaceId)
            .filterIsInstance<Entity.Item>()
            .find { entity ->
                entity.name.lowercase().contains(target.lowercase()) ||
                    entity.id.lowercase().contains(target.lowercase())
            }

    private fun reportMissingTakeTarget(game: MudGame, spaceId: String, target: String) {
        val entities = game.worldState.getEntitiesInSpace(spaceId)
        val isScenery = entities.any { it.name.lowercase().contains(target.lowercase()) }
        if (isScenery) {
            println("That's part of the environment and can't be taken.")
        } else {
            println("You don't see that here.")
        }
    }

    private fun applyFloorTake(game: MudGame, spaceId: String, item: Entity.Item) {
        val templates = floorTakeTemplates(game.itemRepository, item)
        when (val result = FloorItemTakeApply.apply(
            world = game.worldState,
            player = game.worldState.player,
            spaceId = spaceId,
            floorItem = item,
            templates = templates
        )) {
            is FloorItemTakeApply.Result.Success -> {
                game.worldState = result.world
                println("You take the ${result.itemName}.")
                game.trackQuests(QuestAction.CollectedItem(item.id))
            }
            is FloorItemTakeApply.Result.Failure -> {
                println(result.message)
            }
        }
    }

    private fun takeAllItems(
        game: MudGame,
        spaceId: String,
        items: List<Entity.Item>
    ): Triple<Int, List<String>, WorldState> {
        var takenCount = 0
        var currentState = game.worldState
        val takenEntityIds = mutableListOf<String>()
        items.forEach { item ->
            when (val outcome = tryTakeOne(game, currentState, spaceId, item)) {
                is TakeOne.Ok -> {
                    currentState = outcome.world
                    println("You take the ${outcome.itemName}.")
                    takenCount++
                    takenEntityIds.add(item.id)
                }
                is TakeOne.Fail -> println(outcome.message)
            }
        }
        return Triple(takenCount, takenEntityIds, currentState)
    }

    private sealed class TakeOne {
        data class Ok(val world: WorldState, val itemName: String) : TakeOne()
        data class Fail(val message: String) : TakeOne()
    }

    private fun tryTakeOne(
        game: MudGame,
        world: WorldState,
        spaceId: String,
        item: Entity.Item
    ): TakeOne {
        val templates = floorTakeTemplates(game.itemRepository, item)
        return when (val result = FloorItemTakeApply.apply(
            world = world,
            player = world.player,
            spaceId = spaceId,
            floorItem = item,
            templates = templates
        )) {
            is FloorItemTakeApply.Result.Success -> TakeOne.Ok(result.world, result.itemName)
            is FloorItemTakeApply.Result.Failure -> TakeOne.Fail(result.message)
        }
    }
}
