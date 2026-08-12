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
import com.jcraw.mud.core.WorldState
import com.jcraw.mud.reasoning.QuestAction
import com.jcraw.mud.reasoning.inventory.FloorItemTakeApply

/**
 * Floor take / take-all for [ClientItemHandlers] facade.
 */
object ClientItemTakeHandlers {

    fun handleTake(game: EngineGameClient, target: String) {
        val spaceId = game.worldState.player.currentRoomId
        val treasureRoom = game.worldState.getTreasureRoom(spaceId)
        if (treasureRoom != null && !treasureRoom.hasBeenLooted) {
            ClientTreasureRoomHandlers.handleTakeTreasure(game, target)
            return
        }
        val entities = game.worldState.getEntitiesInSpace(spaceId)
        val item = findFloorItem(entities, target)
        if (item == null) {
            reportMissingTakeTarget(game, entities, target)
            return
        }
        if (!item.isPickupable) {
            game.emitEvent(
                GameEvent.System(
                    "That's part of the environment and can't be taken.",
                    GameEvent.MessageLevel.WARNING
                )
            )
            return
        }
        applyFloorTake(game, spaceId, item)
    }

    fun handleTakeAll(game: EngineGameClient) {
        val spaceId = game.worldState.player.currentRoomId
        val entities = game.worldState.getEntitiesInSpace(spaceId)
        val items = entities.filterIsInstance<Entity.Item>().filter { it.isPickupable }
        if (items.isEmpty()) {
            game.emitEvent(GameEvent.System("There are no items to take here.", GameEvent.MessageLevel.INFO))
            return
        }
        val (takenCount, takenEntityIds, currentState) = takeAllItems(game, spaceId, items)
        game.worldState = currentState
        finishTakeAll(game, spaceId, takenCount, takenEntityIds)
    }

    private fun finishTakeAll(
        game: EngineGameClient,
        spaceId: String,
        takenCount: Int,
        takenEntityIds: List<String>
    ) {
        if (takenCount <= 0) return
        game.emitEvent(
            GameEvent.Narrative("Picked up $takenCount ${if (takenCount == 1) "item" else "items"}.")
        )
        val player = game.worldState.player
        game.emitEvent(
            GameEvent.StatusUpdate(
                hp = player.health,
                maxHp = player.maxHealth,
                location = game.worldState.getSpace(spaceId)?.name ?: spaceId
            )
        )
        takenEntityIds.forEach { entityId ->
            game.trackQuests(QuestAction.CollectedItem(entityId))
        }
    }

    private fun findFloorItem(entities: List<Entity>, target: String): Entity.Item? =
        entities.filterIsInstance<Entity.Item>()
            .find { entity ->
                entity.name.lowercase().contains(target.lowercase()) ||
                    entity.id.lowercase().contains(target.lowercase())
            }

    private fun reportMissingTakeTarget(
        game: EngineGameClient,
        entities: List<Entity>,
        target: String
    ) {
        val isScenery = entities.any { it.name.lowercase().contains(target.lowercase()) }
        if (isScenery) {
            game.emitEvent(
                GameEvent.System(
                    "That's part of the environment and can't be taken.",
                    GameEvent.MessageLevel.WARNING
                )
            )
        } else {
            game.emitEvent(GameEvent.System("You don't see that here.", GameEvent.MessageLevel.WARNING))
        }
    }

    private fun applyFloorTake(game: EngineGameClient, spaceId: String, item: Entity.Item) {
        val templates = floorTakeTemplates(game.itemRepository, item)
        when (val result = FloorItemTakeApply.apply(
            world = game.worldState,
            player = game.worldState.player,
            spaceId = spaceId,
            floorItem = item,
            templates = templates
        )) {
            is FloorItemTakeApply.Result.Success -> onTakeSuccess(game, spaceId, item, result)
            is FloorItemTakeApply.Result.Failure -> {
                game.emitEvent(GameEvent.System(result.message, GameEvent.MessageLevel.WARNING))
            }
        }
    }

    private fun onTakeSuccess(
        game: EngineGameClient,
        spaceId: String,
        item: Entity.Item,
        result: FloorItemTakeApply.Result.Success
    ) {
        game.worldState = result.world
        game.emitEvent(GameEvent.Narrative("You take the ${result.itemName}."))
        val player = game.worldState.player
        game.emitEvent(
            GameEvent.StatusUpdate(
                hp = player.health,
                maxHp = player.maxHealth,
                location = game.worldState.getSpace(spaceId)?.name ?: spaceId
            )
        )
        game.trackQuests(QuestAction.CollectedItem(item.id))
    }

    private fun takeAllItems(
        game: EngineGameClient,
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
                    takenCount++
                    takenEntityIds.add(item.id)
                    game.emitEvent(GameEvent.Narrative("You take the ${outcome.itemName}."))
                }
                is TakeOne.Fail -> {
                    game.emitEvent(GameEvent.System(outcome.message, GameEvent.MessageLevel.WARNING))
                }
            }
        }
        return Triple(takenCount, takenEntityIds, currentState)
    }

    private sealed class TakeOne {
        data class Ok(val world: WorldState, val itemName: String) : TakeOne()
        data class Fail(val message: String) : TakeOne()
    }

    private fun tryTakeOne(
        game: EngineGameClient,
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
