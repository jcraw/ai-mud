@file:Suppress("ReturnCount")

package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.reasoning.QuestAction
import com.jcraw.mud.reasoning.inventory.EntityNameMatch
import com.jcraw.mud.reasoning.inventory.FloorItemTakeApply
import com.jcraw.mud.reasoning.inventory.FloorItemTakeBatch

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
        val item = EntityNameMatch.findItem(entities, target)
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
        applyTakeAll(game, spaceId, items)
    }

    private fun applyTakeAll(game: EngineGameClient, spaceId: String, items: List<Entity.Item>) {
        val batch = FloorItemTakeBatch.takeMany(game.worldState, spaceId, items) { item ->
            floorTakeTemplates(game.itemRepository, item)
        }
        game.worldState = batch.world
        batch.taken.forEach { taken ->
            game.emitEvent(GameEvent.Narrative("You take the ${taken.itemName}."))
        }
        batch.failed.forEach { fail ->
            game.emitEvent(GameEvent.System(fail.message, GameEvent.MessageLevel.WARNING))
        }
        finishTakeAll(game, spaceId, batch.taken.size, batch.taken.map { it.floorEntityId })
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

    private fun reportMissingTakeTarget(
        game: EngineGameClient,
        entities: List<Entity>,
        target: String
    ) {
        if (EntityNameMatch.anyNameContains(entities, target)) {
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
        when (val result = FloorItemTakeBatch.apply(game.worldState, spaceId, item, templates)) {
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

}
