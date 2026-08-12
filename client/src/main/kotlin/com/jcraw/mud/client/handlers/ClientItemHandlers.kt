package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.*
import com.jcraw.mud.core.repository.ItemRepository
import com.jcraw.mud.reasoning.QuestAction
import com.jcraw.mud.reasoning.inventory.FloorItemDropApply
import com.jcraw.mud.reasoning.inventory.FloorItemTakeApply
import com.jcraw.mud.reasoning.inventory.GiveItemApply
import com.jcraw.mud.reasoning.inventory.UseConsumableApply

/**
 * Handles inventory and item interactions in the GUI client.
 */
object ClientItemHandlers {

    fun handleInventory(game: EngineGameClient) {
        val player = game.worldState.player
        val invComp = player.inventoryComponent

        val text = buildString {
            appendLine("Inventory:")
            appendLine()

            // V2 Inventory System
            if (invComp != null) {
                // Show gold
                appendLine("  Gold: ${invComp.gold}")

                // Show weight capacity
                val templates = mutableMapOf<String, ItemTemplate>()
                invComp.items.forEach { instance ->
                    val result = game.itemRepository.findTemplateById(instance.templateId)
                    result.getOrNull()?.let { templates[it.id] = it }
                }
                val currentWeight = invComp.currentWeight(templates)
                val capacity = invComp.capacityWeight
                appendLine("  Weight: ${"%.1f".format(currentWeight)}kg / ${"%.1f".format(capacity)}kg")

                // Show equipped items
                if (invComp.equipped.isNotEmpty()) {
                    appendLine()
                    appendLine("  Equipped:")
                    invComp.equipped.forEach { (slot, instance) ->
                        val template = templates[instance.templateId]
                        if (template != null) {
                            val info = formatItemInfo(instance, template)
                            appendLine("    $slot: ${template.name}$info")
                        } else {
                            // Template missing: show templateId so inventory is never silently empty
                            appendLine("    $slot: ${instance.templateId} (template missing)")
                        }
                    }
                } else {
                    appendLine()
                    appendLine("  Equipped: (nothing)")
                }

                // Show inventory items (exclude equipped)
                val unequippedItems = invComp.items.filter { item ->
                    !invComp.equipped.values.any { it.id == item.id }
                }

                if (unequippedItems.isEmpty()) {
                    appendLine("  Carrying: (nothing)")
                } else {
                    appendLine("  Carrying:")
                    unequippedItems.forEach { instance ->
                        val template = templates[instance.templateId]
                        if (template != null) {
                            val info = formatItemInfo(instance, template)
                            appendLine("    - ${template.name}$info")
                        } else {
                            // Template missing: show templateId so inventory is never silently empty
                            appendLine("    - ${instance.templateId} (template missing)")
                        }
                    }
                }
            } else {
                // Legacy Inventory System (fallback)
                if (player.equippedWeapon != null) {
                    appendLine("  Equipped Weapon: ${player.equippedWeapon!!.name} (+${player.equippedWeapon!!.damageBonus} damage)")
                } else {
                    appendLine("  Equipped Weapon: (none)")
                }

                if (player.equippedArmor != null) {
                    appendLine("  Equipped Armor: ${player.equippedArmor!!.name} (+${player.equippedArmor!!.defenseBonus} defense)")
                } else {
                    appendLine("  Equipped Armor: (none)")
                }

                if (player.inventory.isEmpty()) {
                    appendLine("  Carrying: (nothing)")
                } else {
                    appendLine("  Carrying:")
                    player.inventory.forEach { item ->
                        val extra = when (item.itemType) {
                            ItemType.WEAPON -> " [weapon, +${item.damageBonus} damage]"
                            ItemType.ARMOR -> " [armor, +${item.defenseBonus} defense]"
                            ItemType.CONSUMABLE -> " [heals ${item.healAmount} HP]"
                            else -> ""
                        }
                        appendLine("    - ${item.name}$extra")
                    }
                }
            }
        }

        game.emitEvent(GameEvent.Narrative(text))
    }

    /**
     * Format item display info from ItemInstance and ItemTemplate
     * Returns a string like " [weapon, +10 damage, quality 7/10]"
     */
    private fun formatItemInfo(instance: ItemInstance, template: ItemTemplate): String {
        val parts = mutableListOf<String>()

        // Add type
        parts.add(template.type.name.lowercase())

        // Add relevant properties based on type
        when (template.type) {
            ItemType.WEAPON -> {
                val baseDamage = template.getPropertyInt("damage", 0)
                val damage = (baseDamage * instance.getQualityMultiplier()).toInt()
                if (damage > 0) parts.add("+$damage damage")
            }
            ItemType.ARMOR -> {
                val baseDefense = template.getPropertyInt("defense", 0)
                val defense = (baseDefense * instance.getQualityMultiplier()).toInt()
                if (defense > 0) parts.add("+$defense defense")
            }
            ItemType.CONSUMABLE -> {
                val healing = template.getPropertyInt("healing", 0)
                if (healing > 0) parts.add("heals $healing HP")
                if (instance.charges != null) parts.add("${instance.charges} charges")
            }
            ItemType.TOOL -> {
                if (instance.charges != null) parts.add("${instance.charges} charges")
            }
            ItemType.RESOURCE -> {
                if (instance.quantity > 1) parts.add("x${instance.quantity}")
            }
            else -> {}
        }

        // Add quality if not average
        if (instance.quality != 5) {
            parts.add("quality ${instance.quality}/10")
        }

        return if (parts.isEmpty()) "" else " [${parts.joinToString(", ")}]"
    }

    fun handleTake(game: EngineGameClient, target: String) {
        // Check if player is in a treasure room first
        val spaceId = game.worldState.player.currentRoomId
        val treasureRoom = game.worldState.getTreasureRoom(spaceId)

        if (treasureRoom != null && !treasureRoom.hasBeenLooted) {
            // Delegate to treasure room handler
            ClientTreasureRoomHandlers.handleTakeTreasure(game, target)
            return
        }

        val entities = game.worldState.getEntitiesInSpace(spaceId)

        val item = entities.filterIsInstance<Entity.Item>()
            .find { entity ->
                entity.name.lowercase().contains(target.lowercase()) ||
                entity.id.lowercase().contains(target.lowercase())
            }

        if (item == null) {
            // Not an item - check if it's scenery (entity)
            val isScenery = entities.any { it.name.lowercase().contains(target.lowercase()) }
            if (isScenery) {
                game.emitEvent(GameEvent.System("That's part of the environment and can't be taken.", GameEvent.MessageLevel.WARNING))
            } else {
                game.emitEvent(GameEvent.System("You don't see that here.", GameEvent.MessageLevel.WARNING))
            }
            return
        }

        if (!item.isPickupable) {
            game.emitEvent(GameEvent.System("That's part of the environment and can't be taken.", GameEvent.MessageLevel.WARNING))
            return
        }

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
                game.emitEvent(GameEvent.Narrative("You take the ${result.itemName}."))
                // Explicit StatusUpdate so ViewModel refreshes playerState (inventory HUD)
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
            is FloorItemTakeApply.Result.Failure -> {
                game.emitEvent(GameEvent.System(result.message, GameEvent.MessageLevel.WARNING))
            }
        }
    }

    fun handleTakeAll(game: EngineGameClient) {
        val spaceId = game.worldState.player.currentRoomId
        val entities = game.worldState.getEntitiesInSpace(spaceId)

        // Find all pickupable items in the space
        val items = entities.filterIsInstance<Entity.Item>().filter { it.isPickupable }

        if (items.isEmpty()) {
            game.emitEvent(GameEvent.System("There are no items to take here.", GameEvent.MessageLevel.INFO))
            return
        }

        var takenCount = 0
        var currentState = game.worldState
        val takenEntityIds = mutableListOf<String>()

        items.forEach { item ->
            val player = currentState.player
            val templates = floorTakeTemplates(game.itemRepository, item)
            when (val result = FloorItemTakeApply.apply(
                world = currentState,
                player = player,
                spaceId = spaceId,
                floorItem = item,
                templates = templates
            )) {
                is FloorItemTakeApply.Result.Success -> {
                    currentState = result.world
                    takenCount++
                    takenEntityIds.add(item.id)
                    game.emitEvent(GameEvent.Narrative("You take the ${result.itemName}."))
                }
                is FloorItemTakeApply.Result.Failure -> {
                    game.emitEvent(GameEvent.System(result.message, GameEvent.MessageLevel.WARNING))
                }
            }
        }

        game.worldState = currentState

        if (takenCount > 0) {
            game.emitEvent(GameEvent.Narrative("Picked up $takenCount ${if (takenCount == 1) "item" else "items"}."))
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
    }


    fun handleDrop(game: EngineGameClient, target: String) {
        val spaceId = game.worldState.player.currentRoomId
        val player = game.worldState.player
        val templates = floorDropTemplates(game.itemRepository, player)

        when (val result = FloorItemDropApply.apply(
            world = game.worldState,
            player = player,
            spaceId = spaceId,
            target = target,
            templates = templates
        )) {
            is FloorItemDropApply.Result.Success -> {
                game.worldState = result.world
                game.emitEvent(GameEvent.Narrative("You drop the ${result.itemName}."))
            }
            is FloorItemDropApply.Result.Failure -> {
                game.emitEvent(GameEvent.System(result.message, GameEvent.MessageLevel.WARNING))
            }
        }
    }

    fun handleGive(game: EngineGameClient, itemTarget: String, npcTarget: String) {
        val spaceId = game.worldState.player.currentRoomId
        val entities = game.worldState.getEntitiesInSpace(spaceId)
        val player = game.worldState.player

        // Find the NPC in the space
        val npc = entities.filterIsInstance<Entity.NPC>()
            .find { entity ->
                entity.name.lowercase().contains(npcTarget.lowercase()) ||
                entity.id.lowercase().contains(npcTarget.lowercase())
            }

        if (npc == null) {
            game.emitEvent(GameEvent.System("There's no one here by that name.", GameEvent.MessageLevel.WARNING))
            return
        }

        val templates = floorDropTemplates(game.itemRepository, player)
        when (val result = GiveItemApply.apply(
            world = game.worldState,
            player = player,
            target = itemTarget,
            templates = templates
        )) {
            is GiveItemApply.Result.Success -> {
                game.worldState = result.world
                game.emitEvent(GameEvent.Narrative("You give the ${result.itemName} to ${npc.name}."))
                game.trackQuests(QuestAction.DeliveredItem(result.instanceId, npc.id))
            }
            is GiveItemApply.Result.Failure -> {
                game.emitEvent(GameEvent.System(result.message, GameEvent.MessageLevel.WARNING))
            }
        }
    }

    fun handleEquip(game: EngineGameClient, target: String) {
        val player = game.worldState.player
        val invComp = player.inventoryComponent
        val query = target.lowercase()

        // V2 only — resolve from inventoryComponent (no V1 equip field mutators)
        val itemInstance = invComp.items.find { instance ->
            val template = game.itemRepository.findTemplateById(instance.templateId).getOrNull()
            (template?.name?.lowercase()?.contains(query) == true) ||
                instance.templateId.lowercase().contains(query) ||
                instance.id.lowercase().contains(query)
        }

        if (itemInstance == null) {
            game.emitEvent(GameEvent.System("You don't have that in your inventory.", GameEvent.MessageLevel.WARNING))
            return
        }

        val template = game.itemRepository.findTemplateById(itemInstance.templateId).getOrNull()
        if (template == null) {
            game.emitEvent(
                GameEvent.System(
                    "Item template missing for '${itemInstance.templateId}' — cannot equip.",
                    GameEvent.MessageLevel.ERROR
                )
            )
            return
        }

        val equipSlot = template.equipSlot
        if (equipSlot == null) {
            game.emitEvent(GameEvent.System("You can't equip that.", GameEvent.MessageLevel.WARNING))
            return
        }

        val updatedInventory = invComp.equip(itemInstance, equipSlot)
        if (updatedInventory == null) {
            game.emitEvent(GameEvent.System("Error: Could not equip item", GameEvent.MessageLevel.ERROR))
            return
        }
        game.worldState = game.worldState.updatePlayer(player.copy(inventoryComponent = updatedInventory))

        game.emitEvent(GameEvent.Narrative("You equip the ${template.name}."))
    }

    fun handleUse(game: EngineGameClient, target: String) {
        val player = game.worldState.player
        val templates = floorDropTemplates(game.itemRepository, player)

        when (val result = UseConsumableApply.apply(player, target, templates)) {
            is UseConsumableApply.Result.Success -> {
                game.worldState = game.worldState.updatePlayer(result.player)
                if (result.healedAmount > 0) {
                    game.emitEvent(
                        GameEvent.Narrative(
                            "You consume the ${result.itemName} and restore ${result.healedAmount} HP.\n" +
                                "Current health: ${result.player.health}/${result.player.maxHealth}"
                        )
                    )
                    game.emitEvent(
                        GameEvent.StatusUpdate(
                            hp = result.player.health,
                            maxHp = result.player.maxHealth
                        )
                    )
                } else {
                    game.emitEvent(
                        GameEvent.Narrative(
                            "You consume the ${result.itemName}, but you're already at full health."
                        )
                    )
                }
            }
            is UseConsumableApply.Result.Failure -> {
                val level = if (result.message.startsWith("Try 'equip")) {
                    GameEvent.MessageLevel.INFO
                } else if (result.message.contains("not sure", ignoreCase = true)) {
                    GameEvent.MessageLevel.INFO
                } else {
                    GameEvent.MessageLevel.WARNING
                }
                game.emitEvent(GameEvent.System(result.message, level))
            }
        }
    }
}

/** Templates for floor take: prefer property templateId, then full catalog for name-match. */
internal fun floorTakeTemplates(
    itemRepository: ItemRepository,
    item: Entity.Item
): Map<String, ItemTemplate> {
    val templates = mutableMapOf<String, ItemTemplate>()
    item.properties["templateId"]?.let { tid ->
        itemRepository.findTemplateById(tid).getOrNull()?.let { templates[it.id] = it }
    }
    if (templates.isEmpty()) {
        itemRepository.findAllTemplates().getOrNull()?.let { templates.putAll(it) }
    }
    return templates
}

/** Templates for floor drop: full catalog for name-match; fallback per inventory templateId. */
internal fun floorDropTemplates(
    itemRepository: ItemRepository,
    player: PlayerState
): Map<String, ItemTemplate> {
    val templates = mutableMapOf<String, ItemTemplate>()
    itemRepository.findAllTemplates().getOrNull()?.let { templates.putAll(it) }
    if (templates.isEmpty()) {
        player.inventoryComponent.items.forEach { instance ->
            itemRepository.findTemplateById(instance.templateId).getOrNull()?.let {
                templates[it.id] = it
            }
        }
    }
    return templates
}
