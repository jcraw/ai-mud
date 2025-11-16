package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.*
import com.jcraw.mud.reasoning.QuestAction

/**
 * Handles inventory and item interactions in the GUI client.
 */
object ClientItemHandlers {

    fun handleInventory(game: EngineGameClient) {
        val text = buildString {
            appendLine("Inventory:")
            appendLine()

            if (game.worldState.player.equippedWeapon != null) {
                appendLine("  Equipped Weapon: ${game.worldState.player.equippedWeapon!!.name} (+${game.worldState.player.equippedWeapon!!.damageBonus} damage)")
            } else {
                appendLine("  Equipped Weapon: (none)")
            }

            if (game.worldState.player.equippedArmor != null) {
                appendLine("  Equipped Armor: ${game.worldState.player.equippedArmor!!.name} (+${game.worldState.player.equippedArmor!!.defenseBonus} defense)")
            } else {
                appendLine("  Equipped Armor: (none)")
            }

            if (game.worldState.player.inventory.isEmpty()) {
                appendLine("  Carrying: (nothing)")
            } else {
                appendLine("  Carrying:")
                game.worldState.player.inventory.forEach { item ->
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

        game.emitEvent(GameEvent.Narrative(text))
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

        val newState = game.worldState
            .removeEntityFromSpace(spaceId, item.id)
            ?.updatePlayer(game.worldState.player.addToInventory(item))

        if (newState != null) {
            game.worldState = newState
            game.emitEvent(GameEvent.Narrative("You take the ${item.name}."))

            // Track item collection for quests
            game.trackQuests(QuestAction.CollectedItem(item.id))
        } else {
            game.emitEvent(GameEvent.System("Something went wrong.", GameEvent.MessageLevel.ERROR))
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

        items.forEach { item ->
            val newState = currentState
                .removeEntityFromSpace(spaceId, item.id)
                ?.updatePlayer(currentState.player.addToInventory(item))

            if (newState != null) {
                currentState = newState
                takenCount++
                game.emitEvent(GameEvent.Narrative("You take the ${item.name}."))
            }
        }

        game.worldState = currentState

        if (takenCount > 0) {
            game.emitEvent(GameEvent.Narrative("Picked up $takenCount ${if (takenCount == 1) "item" else "items"}."))

            // Track item collection for quests
            items.forEach { item ->
                game.trackQuests(QuestAction.CollectedItem(item.id))
            }
        }
    }

    fun handleDrop(game: EngineGameClient, target: String) {
        val spaceId = game.worldState.player.currentRoomId

        val item = game.worldState.player.inventory.find { invItem ->
            invItem.name.lowercase().contains(target.lowercase()) ||
            invItem.id.lowercase().contains(target.lowercase())
        }

        if (item == null) {
            game.emitEvent(GameEvent.System("You don't have that.", GameEvent.MessageLevel.WARNING))
            return
        }

        val newState = game.worldState
            .updatePlayer(game.worldState.player.removeFromInventory(item.id))
            .addEntityToSpace(spaceId, item)

        if (newState != null) {
            game.worldState = newState
            game.emitEvent(GameEvent.Narrative("You drop the ${item.name}."))
        } else {
            game.emitEvent(GameEvent.System("Something went wrong.", GameEvent.MessageLevel.ERROR))
        }
    }

    fun handleGive(game: EngineGameClient, itemTarget: String, npcTarget: String) {
        val spaceId = game.worldState.player.currentRoomId
        val entities = game.worldState.getEntitiesInSpace(spaceId)

        // Find the item in inventory
        val item = game.worldState.player.inventory.find { invItem ->
            invItem.name.lowercase().contains(itemTarget.lowercase()) ||
            invItem.id.lowercase().contains(itemTarget.lowercase())
        }

        if (item == null) {
            game.emitEvent(GameEvent.System("You don't have that item.", GameEvent.MessageLevel.WARNING))
            return
        }

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

        // Remove item from inventory
        val updatedPlayer = game.worldState.player.removeFromInventory(item.id)
        game.worldState = game.worldState.updatePlayer(updatedPlayer)

        game.emitEvent(GameEvent.Narrative("You give the ${item.name} to ${npc.name}."))

        // Track delivery for quests
        game.trackQuests(QuestAction.DeliveredItem(item.id, npc.id))
    }

    fun handleEquip(game: EngineGameClient, target: String) {
        val item = game.worldState.player.inventory.find { invItem ->
            invItem.name.lowercase().contains(target.lowercase()) ||
            invItem.id.lowercase().contains(target.lowercase())
        }

        if (item == null) {
            game.emitEvent(GameEvent.System("You don't have that in your inventory.", GameEvent.MessageLevel.WARNING))
            return
        }

        when (item.itemType) {
            ItemType.WEAPON -> {
                val oldWeapon = game.worldState.player.equippedWeapon
                game.worldState = game.worldState.updatePlayer(game.worldState.player.equipWeapon(item))

                if (oldWeapon != null) {
                    game.emitEvent(GameEvent.Narrative("You unequip the ${oldWeapon.name} and equip the ${item.name} (+${item.damageBonus} damage)."))
                } else {
                    game.emitEvent(GameEvent.Narrative("You equip the ${item.name} (+${item.damageBonus} damage)."))
                }
            }
            ItemType.ARMOR -> {
                val oldArmor = game.worldState.player.equippedArmor
                game.worldState = game.worldState.updatePlayer(game.worldState.player.equipArmor(item))

                if (oldArmor != null) {
                    game.emitEvent(GameEvent.Narrative("You unequip the ${oldArmor.name} and equip the ${item.name} (+${item.defenseBonus} defense)."))
                } else {
                    game.emitEvent(GameEvent.Narrative("You equip the ${item.name} (+${item.defenseBonus} defense)."))
                }
            }
            else -> {
                game.emitEvent(GameEvent.System("You can't equip that.", GameEvent.MessageLevel.WARNING))
            }
        }
    }

    fun handleUse(game: EngineGameClient, target: String) {
        val item = game.worldState.player.inventory.find { invItem ->
            invItem.name.lowercase().contains(target.lowercase()) ||
            invItem.id.lowercase().contains(target.lowercase())
        }

        if (item == null) {
            game.emitEvent(GameEvent.System("You don't have that in your inventory.", GameEvent.MessageLevel.WARNING))
            return
        }

        if (!item.isUsable) {
            game.emitEvent(GameEvent.System("You can't use that.", GameEvent.MessageLevel.WARNING))
            return
        }

        when (item.itemType) {
            ItemType.CONSUMABLE -> {
                val oldHealth = game.worldState.player.health
                game.worldState = game.worldState.updatePlayer(game.worldState.player.useConsumable(item))
                val healedAmount = game.worldState.player.health - oldHealth

                if (healedAmount > 0) {
                    game.emitEvent(GameEvent.Narrative("You consume the ${item.name} and restore $healedAmount HP.\nCurrent health: ${game.worldState.player.health}/${game.worldState.player.maxHealth}"))

                    // Update status
                    game.emitEvent(GameEvent.StatusUpdate(
                        hp = game.worldState.player.health,
                        maxHp = game.worldState.player.maxHealth
                    ))
                } else {
                    game.emitEvent(GameEvent.Narrative("You consume the ${item.name}, but you're already at full health."))
                }
            }
            ItemType.WEAPON -> {
                game.emitEvent(GameEvent.System("Try 'equip ${item.name}' to equip this weapon.", GameEvent.MessageLevel.INFO))
            }
            else -> {
                game.emitEvent(GameEvent.System("You're not sure how to use that.", GameEvent.MessageLevel.INFO))
            }
        }
    }
}
