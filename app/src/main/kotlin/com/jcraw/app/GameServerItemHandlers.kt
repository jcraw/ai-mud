@file:Suppress("TooManyFunctions", "LongParameterList", "WildcardImport", "UnusedParameter")

package com.jcraw.app

import com.jcraw.mud.core.*
import com.jcraw.mud.reasoning.QuestAction
import com.jcraw.mud.reasoning.inventory.FloorItemDropApply
import com.jcraw.mud.reasoning.inventory.GiveItemApply
import com.jcraw.mud.reasoning.inventory.UseConsumableApply

/**
 * Drop/give/equip/use handlers for [GameServer]. Pure extract.
 */
object GameServerItemHandlers {

    fun handleDrop(
        server: GameServer,
        playerId: PlayerId,
        playerState: PlayerState,
        itemId: String
    ): Triple<String, WorldState, GameEvent?> {
        val spaceId = playerState.currentRoomId
        val templates = buildFloorDropTemplates(server, playerState)
        return when (
            val result = FloorItemDropApply.apply(
                world = server.worldState,
                player = playerState,
                spaceId = spaceId,
                target = itemId,
                templates = templates
            )
        ) {
            is FloorItemDropApply.Result.Success -> dropSuccess(playerId, playerState, spaceId, result)
            is FloorItemDropApply.Result.Failure ->
                Triple(result.message, server.worldState, null)
        }
    }

    private fun dropSuccess(
        playerId: PlayerId,
        playerState: PlayerState,
        spaceId: SpaceId,
        result: FloorItemDropApply.Result.Success
    ): Triple<String, WorldState, GameEvent?> {
        val event = GameEvent.GenericAction(
            playerId = playerId,
            playerName = playerState.name,
            actionDescription = "drops ${result.itemName}",
            roomId = spaceId,
            excludePlayer = playerId
        )
        return Triple("You drop the ${result.itemName}.", result.world, event)
    }

    fun buildFloorDropTemplates(server: GameServer, player: PlayerState): Map<String, ItemTemplate> {
        val repo = server.itemRepository ?: return emptyMap()
        val templates = mutableMapOf<String, ItemTemplate>()
        repo.findAllTemplates().getOrNull()?.let { templates.putAll(it) }
        if (templates.isEmpty()) {
            player.inventoryComponent.items.forEach { instance ->
                repo.findTemplateById(instance.templateId).getOrNull()?.let {
                    templates[it.id] = it
                }
            }
        }
        return templates
    }

    fun handleGive(
        server: GameServer,
        playerId: PlayerId,
        playerState: PlayerState,
        itemTarget: String,
        npcTarget: String
    ): Triple<String, WorldState, GameEvent?> {
        val spaceId = playerState.currentRoomId
        val entities = server.worldState.getEntitiesInSpace(spaceId)
        val npc = entities.filterIsInstance<Entity.NPC>().find { entity ->
            entity.name.lowercase().contains(npcTarget.lowercase()) ||
                entity.id.lowercase().contains(npcTarget.lowercase())
        }
        if (npc == null) {
            return Triple("There's no one here by that name.", server.worldState, null)
        }
        return giveToNpc(server, playerId, playerState, spaceId, itemTarget, npc)
    }

    private fun giveToNpc(
        server: GameServer,
        playerId: PlayerId,
        playerState: PlayerState,
        spaceId: SpaceId,
        itemTarget: String,
        npc: Entity.NPC
    ): Triple<String, WorldState, GameEvent?> {
        val templates = buildFloorDropTemplates(server, playerState)
        return when (
            val result = GiveItemApply.apply(
                world = server.worldState,
                player = playerState,
                target = itemTarget,
                templates = templates
            )
        ) {
            is GiveItemApply.Result.Success ->
                completeGive(server, playerId, playerState, spaceId, npc, result)
            is GiveItemApply.Result.Failure ->
                Triple(result.message, server.worldState, null)
        }
    }

    private fun completeGive(
        server: GameServer,
        playerId: PlayerId,
        playerState: PlayerState,
        spaceId: SpaceId,
        npc: Entity.NPC,
        result: GiveItemApply.Result.Success
    ): Triple<String, WorldState, GameEvent?> {
        val givenPlayer = result.world.getPlayer(playerId) ?: playerState
        val quest = GameServerQuestSupport.trackQuests(
            server, givenPlayer, QuestAction.DeliveredItem(result.instanceId, npc.id)
        )
        // Merge inventory remove + quest updates (trackQuests base world may be pre-give)
        val finalWorld = quest.updatedWorld.updatePlayer(quest.updatedPlayer)
        val event = giveEvent(playerId, playerState, spaceId, result.itemName, npc.name)
        return Triple(
            "You give the ${result.itemName} to ${npc.name}." + quest.notifications,
            finalWorld,
            event
        )
    }

    private fun giveEvent(
        playerId: PlayerId,
        playerState: PlayerState,
        spaceId: SpaceId,
        itemName: String,
        npcName: String
    ) = GameEvent.GenericAction(
        playerId = playerId,
        playerName = playerState.name,
        actionDescription = "gives $itemName to $npcName",
        roomId = spaceId,
        excludePlayer = playerId
    )

    fun handleEquip(
        server: GameServer,
        playerId: PlayerId,
        playerState: PlayerState,
        itemId: String
    ): Triple<String, WorldState, GameEvent?> {
        val inv = playerState.inventoryComponent
        val query = itemId.lowercase()
        val templates = buildFloorDropTemplates(server, playerState)
        val instance = findEquipInstance(inv, templates, query, itemId)
        val template = instance?.let {
            templates[it.templateId]
                ?: server.itemRepository?.findTemplateById(it.templateId)?.getOrNull()
        }
        return equipResult(server, playerState, inv, instance, template)
    }

    private fun findEquipInstance(
        inv: InventoryComponent,
        templates: Map<String, ItemTemplate>,
        query: String,
        itemId: String
    ) = inv.items.find { instance ->
        val template = templates[instance.templateId]
        (template?.name?.lowercase()?.contains(query) == true) ||
            instance.templateId.lowercase().contains(query) ||
            instance.id.equals(query, ignoreCase = true) ||
            (template?.name?.equals(itemId, ignoreCase = true) == true)
    }

    private fun equipResult(
        server: GameServer,
        playerState: PlayerState,
        inv: InventoryComponent,
        instance: ItemInstance?,
        template: ItemTemplate?
    ): Triple<String, WorldState, GameEvent?> {
        val slot = template?.equipSlot
        val updated = if (instance != null && slot != null) inv.equip(instance, slot) else null
        return when {
            instance == null -> Triple("You don't have that item.", server.worldState, null)
            template == null -> Triple("Error: Item template not found", server.worldState, null)
            slot == null -> Triple("You can't equip that.", server.worldState, null)
            updated == null -> Triple("Error: Could not equip item", server.worldState, null)
            else -> {
                val player = playerState.copy(inventoryComponent = updated)
                Triple("You equip the ${template.name}.", server.worldState.updatePlayer(player), null)
            }
        }
    }

    fun handleUse(
        server: GameServer,
        playerId: PlayerId,
        playerState: PlayerState,
        itemId: String
    ): Triple<String, WorldState, GameEvent?> {
        val templates = buildFloorDropTemplates(server, playerState)
        return when (val result = UseConsumableApply.apply(playerState, itemId, templates)) {
            is UseConsumableApply.Result.Success -> useSuccess(server, result)
            is UseConsumableApply.Result.Failure ->
                Triple(result.message, server.worldState, null)
        }
    }

    private fun useSuccess(
        server: GameServer,
        result: UseConsumableApply.Result.Success
    ): Triple<String, WorldState, GameEvent?> {
        val message = if (result.healedAmount > 0) {
            "You consume the ${result.itemName} and restore ${result.healedAmount} HP.\n" +
                "Current health: ${result.player.health}/${result.player.maxHealth}"
        } else {
            "You consume the ${result.itemName}, but you're already at full health."
        }
        return Triple(message, server.worldState.updatePlayer(result.player), null)
    }
}
