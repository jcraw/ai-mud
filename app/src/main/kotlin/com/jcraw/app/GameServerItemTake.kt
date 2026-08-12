@file:Suppress("TooManyFunctions", "LongParameterList", "WildcardImport", "UnusedParameter")

package com.jcraw.app

import com.jcraw.mud.core.*
import com.jcraw.mud.reasoning.QuestAction
import com.jcraw.mud.reasoning.inventory.FloorItemTakeApply

/**
 * Floor take handlers for [GameServer]. Pure extract.
 */
object GameServerItemTake {

    fun handleTake(
        server: GameServer,
        playerId: PlayerId,
        playerState: PlayerState,
        itemId: String
    ): Triple<String, WorldState, GameEvent?> {
        val spaceId = playerState.currentRoomId
        val entities = server.worldState.getEntitiesInSpace(spaceId)
        val item = findFloorItem(entities, itemId)
        return when {
            item != null && item.isPickupable ->
                takePickupable(server, playerId, playerState, spaceId, item)
            item != null ->
                Triple("That's part of the environment and can't be taken.", server.worldState, null)
            else -> takeSceneryOrMissing(server, entities, itemId)
        }
    }

    private fun findFloorItem(entities: List<Entity>, itemId: String): Entity.Item? =
        entities.filterIsInstance<Entity.Item>().find {
            it.name.equals(itemId, ignoreCase = true) ||
                it.name.lowercase().contains(itemId.lowercase()) ||
                it.id.lowercase().contains(itemId.lowercase())
        }

    private fun takePickupable(
        server: GameServer,
        playerId: PlayerId,
        playerState: PlayerState,
        spaceId: SpaceId,
        item: Entity.Item
    ): Triple<String, WorldState, GameEvent?> {
        val templates = buildFloorTakeTemplates(server, item)
        return when (
            val result = FloorItemTakeApply.apply(
                world = server.worldState,
                player = playerState,
                spaceId = spaceId,
                floorItem = item,
                templates = templates
            )
        ) {
            is FloorItemTakeApply.Result.Success ->
                completeTakeSuccess(server, playerId, playerState, spaceId, item, result)
            is FloorItemTakeApply.Result.Failure ->
                Triple(result.message, server.worldState, null)
        }
    }

    private fun completeTakeSuccess(
        server: GameServer,
        playerId: PlayerId,
        playerState: PlayerState,
        spaceId: SpaceId,
        item: Entity.Item,
        result: FloorItemTakeApply.Result.Success
    ): Triple<String, WorldState, GameEvent?> {
        // Point member world at take result so trackQuests does not drop V2 inventory
        server.worldState = result.world
        val updated = server.worldState.getPlayer(playerId) ?: server.worldState.player
        val quest = GameServerQuestSupport.trackQuests(
            server, updated, QuestAction.CollectedItem(item.id)
        )
        val event = takeEvent(playerId, playerState, spaceId, result.itemName)
        return Triple(
            "You take the ${result.itemName}." + quest.notifications,
            quest.updatedWorld,
            event
        )
    }

    private fun takeEvent(
        playerId: PlayerId,
        playerState: PlayerState,
        spaceId: SpaceId,
        itemName: String
    ) = GameEvent.GenericAction(
        playerId = playerId,
        playerName = playerState.name,
        actionDescription = "picks up $itemName",
        roomId = spaceId,
        excludePlayer = playerId
    )

    private fun takeSceneryOrMissing(
        server: GameServer,
        entities: List<Entity>,
        itemId: String
    ): Triple<String, WorldState, GameEvent?> {
        val isScenery = entities.any { it.name.lowercase().contains(itemId.lowercase()) }
        return if (isScenery) {
            Triple("That's part of the environment and can't be taken.", server.worldState, null)
        } else {
            Triple("You don't see that here.", server.worldState, null)
        }
    }

    fun handleTakeAll(
        server: GameServer,
        playerId: PlayerId,
        playerState: PlayerState
    ): Triple<String, WorldState, GameEvent?> {
        val spaceId = playerState.currentRoomId
        val items = server.worldState.getEntitiesInSpace(spaceId)
            .filterIsInstance<Entity.Item>().filter { it.isPickupable }
        if (items.isEmpty()) {
            return Triple("There are no items to take here.", server.worldState, null)
        }
        return takeAllItems(server, playerId, playerState, spaceId, items)
    }

    private fun takeAllItems(
        server: GameServer,
        playerId: PlayerId,
        playerState: PlayerState,
        spaceId: SpaceId,
        items: List<Entity.Item>
    ): Triple<String, WorldState, GameEvent?> {
        var player = playerState
        var world = server.worldState
        val taken = mutableListOf<String>()
        var questNotes = ""
        val messages = mutableListOf<String>()
        items.forEach { item ->
            val step = takeOneInAll(server, playerId, player, world, spaceId, item)
            player = step.player
            world = step.world
            questNotes += step.questNotes
            messages += step.messages
            taken += step.takenNames
        }
        return finalizeTakeAll(
            server, playerId, playerState, spaceId, taken, messages, questNotes, world
        )
    }

    private data class TakeOneStep(
        val player: PlayerState,
        val world: WorldState,
        val messages: List<String>,
        val takenNames: List<String>,
        val questNotes: String
    )

    private fun takeOneInAll(
        server: GameServer,
        playerId: PlayerId,
        currentPlayer: PlayerState,
        currentWorld: WorldState,
        spaceId: SpaceId,
        item: Entity.Item
    ): TakeOneStep {
        val templates = buildFloorTakeTemplates(server, item)
        return when (
            val result = FloorItemTakeApply.apply(
                world = currentWorld,
                player = currentPlayer,
                spaceId = spaceId,
                floorItem = item,
                templates = templates
            )
        ) {
            is FloorItemTakeApply.Result.Success ->
                takeOneSuccess(server, playerId, item, result)
            is FloorItemTakeApply.Result.Failure -> TakeOneStep(
                player = currentPlayer,
                world = currentWorld,
                messages = listOf(result.message),
                takenNames = emptyList(),
                questNotes = ""
            )
        }
    }

    private fun takeOneSuccess(
        server: GameServer,
        playerId: PlayerId,
        item: Entity.Item,
        result: FloorItemTakeApply.Result.Success
    ): TakeOneStep {
        var world = result.world
        var player = world.getPlayer(playerId) ?: world.player
        // Keep trackQuests on post-take world (V2 inventory + entity removal)
        server.worldState = world
        val quest = GameServerQuestSupport.trackQuests(
            server, player, QuestAction.CollectedItem(item.id)
        )
        player = quest.updatedPlayer
        world = quest.updatedWorld
        server.worldState = world
        return TakeOneStep(
            player = player,
            world = world,
            messages = listOf("You take the ${result.itemName}."),
            takenNames = listOf(result.itemName),
            questNotes = quest.notifications
        )
    }

    private fun finalizeTakeAll(
        server: GameServer,
        playerId: PlayerId,
        playerState: PlayerState,
        spaceId: SpaceId,
        takenItems: List<String>,
        messages: List<String>,
        questNotes: String,
        currentWorld: WorldState
    ): Triple<String, WorldState, GameEvent?> {
        if (takenItems.isEmpty()) {
            return Triple(
                messages.joinToString("\n").ifBlank { "You couldn't take any items." },
                server.worldState,
                null
            )
        }
        val message = takeAllMessage(takenItems, messages, questNotes)
        val event = GameEvent.GenericAction(
            playerId = playerId,
            playerName = playerState.name,
            actionDescription = "picks up all items",
            roomId = spaceId,
            excludePlayer = playerId
        )
        return Triple(message, currentWorld, event)
    }

    private fun takeAllMessage(
        takenItems: List<String>,
        messages: List<String>,
        questNotes: String
    ): String = buildString {
        messages.forEach { appendLine(it) }
        append("\nYou took ${takenItems.size} item${if (takenItems.size > 1) "s" else ""}.")
        append(questNotes)
    }

    fun buildFloorTakeTemplates(server: GameServer, item: Entity.Item): Map<String, ItemTemplate> {
        val repo = server.itemRepository ?: return emptyMap()
        val templates = mutableMapOf<String, ItemTemplate>()
        item.properties["templateId"]?.let { tid ->
            repo.findTemplateById(tid).getOrNull()?.let { templates[it.id] = it }
        }
        if (templates.isEmpty()) {
            repo.findAllTemplates().getOrNull()?.let { templates.putAll(it) }
        }
        return templates
    }
}
