package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.app.times
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.ItemType
import com.jcraw.mud.core.ItemInstance
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.reasoning.QuestAction

/**
 * Handlers for item interactions: inventory, take, drop, equip, use
 */
object ItemHandlers {

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

    fun handleInventory(game: MudGame) {
        println("Inventory:")

        // Show equipped items
        if (game.worldState.player.equippedWeapon != null) {
            println("  Equipped Weapon: ${game.worldState.player.equippedWeapon!!.name} (+${game.worldState.player.equippedWeapon!!.damageBonus} damage)")
        } else {
            println("  Equipped Weapon: (none)")
        }

        if (game.worldState.player.equippedArmor != null) {
            println("  Equipped Armor: ${game.worldState.player.equippedArmor!!.name} (+${game.worldState.player.equippedArmor!!.defenseBonus} defense)")
        } else {
            println("  Equipped Armor: (none)")
        }

        // Show inventory items
        if (game.worldState.player.inventory.isEmpty()) {
            println("  Carrying: (nothing)")
        } else {
            println("  Carrying:")
            game.worldState.player.inventory.forEach { item ->
                val extra = when (item.itemType) {
                    ItemType.WEAPON -> " [weapon, +${item.damageBonus} damage]"
                    ItemType.ARMOR -> " [armor, +${item.defenseBonus} defense]"
                    ItemType.CONSUMABLE -> " [heals ${item.healAmount} HP]"
                    else -> ""
                }
                println("    - ${item.name}$extra")
            }
        }
    }

    fun handleTake(game: MudGame, target: String) {
        val room = game.worldState.getCurrentRoom() ?: return

        // Find the item in the room
        val item = room.entities.filterIsInstance<Entity.Item>()
            .find { entity ->
                entity.name.lowercase().contains(target.lowercase()) ||
                entity.id.lowercase().contains(target.lowercase())
            }

        if (item == null) {
            // Not an item - check if it's scenery (room trait or entity)
            val isScenery = room.traits.any { it.lowercase().contains(target.lowercase()) } ||
                           room.entities.any { it.name.lowercase().contains(target.lowercase()) }
            if (isScenery) {
                println("That's part of the environment and can't be taken.")
            } else {
                println("You don't see that here.")
            }
            return
        }

        if (!item.isPickupable) {
            println("That's part of the environment and can't be taken.")
            return
        }

        // Remove item from room and add to inventory
        val newState = game.worldState
            .removeEntityFromRoom(room.id, item.id)
            ?.updatePlayer(game.worldState.player.addToInventory(item))

        if (newState != null) {
            game.worldState = newState
            println("You take the ${item.name}.")

            // Track item collection for quests
            game.trackQuests(QuestAction.CollectedItem(item.id))
        } else {
            println("Something went wrong.")
        }
    }

    fun handleTakeAll(game: MudGame) {
        val room = game.worldState.getCurrentRoom() ?: return

        // Find all pickupable items in the room
        val items = room.entities.filterIsInstance<Entity.Item>().filter { it.isPickupable }

        if (items.isEmpty()) {
            println("There are no items to take here.")
            return
        }

        var takenCount = 0
        var currentState = game.worldState

        items.forEach { item ->
            val newState = currentState
                .removeEntityFromRoom(room.id, item.id)
                ?.updatePlayer(currentState.player.addToInventory(item))

            if (newState != null) {
                currentState = newState
                println("You take the ${item.name}.")
                takenCount++
            }
        }

        game.worldState = currentState

        if (takenCount > 0) {
            println("\nYou took $takenCount item${if (takenCount > 1) "s" else ""}.")

            // Track item collection for quests
            items.forEach { item ->
                game.trackQuests(QuestAction.CollectedItem(item.id))
            }
        }
    }

    fun handleDrop(game: MudGame, target: String) {
        val room = game.worldState.getCurrentRoom() ?: return

        // Find the item in inventory
        var item = game.worldState.player.inventory.find { invItem ->
            invItem.name.lowercase().contains(target.lowercase()) ||
            invItem.id.lowercase().contains(target.lowercase())
        }

        // Check if item is equipped weapon
        var isEquippedWeapon = false
        if (item == null && game.worldState.player.equippedWeapon != null) {
            if (game.worldState.player.equippedWeapon!!.name.lowercase().contains(target.lowercase()) ||
                game.worldState.player.equippedWeapon!!.id.lowercase().contains(target.lowercase())) {
                item = game.worldState.player.equippedWeapon
                isEquippedWeapon = true
            }
        }

        // Check if item is equipped armor
        var isEquippedArmor = false
        if (item == null && game.worldState.player.equippedArmor != null) {
            if (game.worldState.player.equippedArmor!!.name.lowercase().contains(target.lowercase()) ||
                game.worldState.player.equippedArmor!!.id.lowercase().contains(target.lowercase())) {
                item = game.worldState.player.equippedArmor
                isEquippedArmor = true
            }
        }

        if (item == null) {
            println("You don't have that.")
            return
        }

        // Unequip if needed and add to room
        val updatedPlayer = when {
            isEquippedWeapon -> game.worldState.player.copy(equippedWeapon = null)
            isEquippedArmor -> game.worldState.player.copy(equippedArmor = null)
            else -> game.worldState.player.removeFromInventory(item.id)
        }

        val newState = game.worldState
            .updatePlayer(updatedPlayer)
            .addEntityToRoom(room.id, item)

        if (newState != null) {
            game.worldState = newState
            println("You drop the ${item.name}.")
        } else {
            println("Something went wrong.")
        }
    }

    fun handleGive(game: MudGame, itemTarget: String, npcTarget: String) {
        val room = game.worldState.getCurrentRoom() ?: return

        // Find the item in inventory
        val item = game.worldState.player.inventory.find { invItem ->
            invItem.name.lowercase().contains(itemTarget.lowercase()) ||
            invItem.id.lowercase().contains(itemTarget.lowercase())
        }

        if (item == null) {
            println("You don't have that item.")
            return
        }

        // Find the NPC in the room
        val npc = room.entities.filterIsInstance<Entity.NPC>()
            .find { entity ->
                entity.name.lowercase().contains(npcTarget.lowercase()) ||
                entity.id.lowercase().contains(npcTarget.lowercase())
            }

        if (npc == null) {
            println("There's no one here by that name.")
            return
        }

        // Remove item from inventory
        val updatedPlayer = game.worldState.player.removeFromInventory(item.id)
        game.worldState = game.worldState.updatePlayer(updatedPlayer)

        println("You give the ${item.name} to ${npc.name}.")

        // Track delivery for quests
        game.trackQuests(QuestAction.DeliveredItem(item.id, npc.id))
    }

    fun handleEquip(game: MudGame, target: String) {
        // Find the item in inventory
        val item = game.worldState.player.inventory.find { invItem ->
            invItem.name.lowercase().contains(target.lowercase()) ||
            invItem.id.lowercase().contains(target.lowercase())
        }

        if (item == null) {
            println("You don't have that in your inventory.")
            return
        }

        when (item.itemType) {
            ItemType.WEAPON -> {
                val oldWeapon = game.worldState.player.equippedWeapon
                game.worldState = game.worldState.updatePlayer(game.worldState.player.equipWeapon(item))

                if (oldWeapon != null) {
                    println("You unequip the ${oldWeapon.name} and equip the ${item.name} (+${item.damageBonus} damage).")
                } else {
                    println("You equip the ${item.name} (+${item.damageBonus} damage).")
                }
            }
            ItemType.ARMOR -> {
                val oldArmor = game.worldState.player.equippedArmor
                game.worldState = game.worldState.updatePlayer(game.worldState.player.equipArmor(item))

                if (oldArmor != null) {
                    println("You unequip the ${oldArmor.name} and equip the ${item.name} (+${item.defenseBonus} defense).")
                } else {
                    println("You equip the ${item.name} (+${item.defenseBonus} defense).")
                }
            }
            else -> {
                println("You can't equip that.")
            }
        }
    }

    fun handleUse(game: MudGame, target: String) {
        // Find the item in inventory
        val item = game.worldState.player.inventory.find { invItem ->
            invItem.name.lowercase().contains(target.lowercase()) ||
            invItem.id.lowercase().contains(target.lowercase())
        }

        if (item == null) {
            println("You don't have that in your inventory.")
            return
        }

        if (!item.isUsable) {
            println("You can't use that.")
            return
        }

        when (item.itemType) {
            ItemType.CONSUMABLE -> {
                val oldHealth = game.worldState.player.health

                // Consume the item and heal
                game.worldState = game.worldState.updatePlayer(game.worldState.player.useConsumable(item))
                val healedAmount = game.worldState.player.health - oldHealth

                if (healedAmount > 0) {
                    println("\nYou consume the ${item.name} and restore $healedAmount HP.")
                    println("Current health: ${game.worldState.player.health}/${game.worldState.player.maxHealth}")
                } else {
                    println("\nYou consume the ${item.name}, but you're already at full health.")
                }

                // V2 combat: Using items takes game time, so NPCs in turn queue may act
                // This happens naturally through the turn queue system
            }
            ItemType.WEAPON -> {
                println("Try 'equip ${item.name}' to equip this weapon.")
            }
            else -> {
                println("You're not sure how to use that.")
            }
        }
    }

    fun handleLoot(game: MudGame, corpseTarget: String, itemTarget: String?) {
        val room = game.worldState.getCurrentRoom() ?: return

        // Find the corpse in the room
        val corpse = room.entities.filterIsInstance<Entity.Corpse>()
            .find { entity ->
                entity.name.lowercase().contains(corpseTarget.lowercase()) ||
                entity.id.lowercase().contains(corpseTarget.lowercase())
            }

        if (corpse == null) {
            println("There's no corpse here by that name.")
            return
        }

        // If no item target specified, list contents
        if (itemTarget == null) {
            if (corpse.contents.isEmpty() && corpse.goldAmount == 0) {
                println("The corpse is empty.")
            } else {
                println("${corpse.name} contains:")
                corpse.contents.forEach { instance ->
                    val templateResult = game.itemRepository.findTemplateById(instance.templateId)
                    templateResult.onSuccess { template ->
                        if (template != null) {
                            val extra = formatItemInfo(instance, template)
                            println("  - ${template.name}$extra")
                        } else {
                            println("  - Unknown item (${instance.id})")
                        }
                    }.onFailure {
                        println("  - Unknown item (${instance.id})")
                    }
                }
                if (corpse.goldAmount > 0) {
                    println("  - ${corpse.goldAmount} gold")
                }
            }
            return
        }

        // Special case: looting gold
        if (itemTarget.lowercase() == "gold" || itemTarget.lowercase() == "coins") {
            if (corpse.goldAmount > 0) {
                val updatedCorpse = corpse.removeGold(corpse.goldAmount)
                // TODO: Add gold to player inventory when InventoryComponent is integrated
                val newState = game.worldState
                    .removeEntityFromRoom(room.id, corpse.id)
                    ?.addEntityToRoom(room.id, updatedCorpse)

                if (newState != null) {
                    game.worldState = newState
                    println("You take ${corpse.goldAmount} gold from ${corpse.name}.")
                } else {
                    println("Something went wrong.")
                }
            } else {
                println("There's no gold in the corpse.")
            }
            return
        }

        // Find the item in the corpse by matching against template names
        val matchingItem = corpse.contents.find { instance ->
            val templateResult = game.itemRepository.findTemplateById(instance.templateId)
            templateResult.getOrNull()?.let { template ->
                template.name.lowercase().contains(itemTarget.lowercase()) ||
                instance.id.lowercase().contains(itemTarget.lowercase())
            } ?: false
        }

        if (matchingItem == null) {
            println("That item isn't in the corpse.")
            return
        }

        // Get template for display
        val templateResult = game.itemRepository.findTemplateById(matchingItem.templateId)
        val templateName = templateResult.getOrNull()?.name ?: "item"

        // Remove item from corpse
        // TODO: Add item to player inventory when InventoryComponent is integrated
        val updatedCorpse = corpse.removeItem(matchingItem.id)

        val newState = game.worldState
            .removeEntityFromRoom(room.id, corpse.id)
            ?.addEntityToRoom(room.id, updatedCorpse)

        if (newState != null) {
            game.worldState = newState
            println("You take the $templateName from ${corpse.name}.")

            // Track item collection for quests
            game.trackQuests(QuestAction.CollectedItem(matchingItem.id))
        } else {
            println("Something went wrong.")
        }
    }

    fun handleLootAll(game: MudGame, corpseTarget: String) {
        val room = game.worldState.getCurrentRoom() ?: return

        // Find the corpse in the room
        val corpse = room.entities.filterIsInstance<Entity.Corpse>()
            .find { entity ->
                entity.name.lowercase().contains(corpseTarget.lowercase()) ||
                entity.id.lowercase().contains(corpseTarget.lowercase())
            }

        if (corpse == null) {
            println("There's no corpse here by that name.")
            return
        }

        if (corpse.contents.isEmpty() && corpse.goldAmount == 0) {
            println("The corpse is empty.")
            return
        }

        var lootedCount = 0
        var currentCorpse: Entity.Corpse = corpse

        // Loot all items
        // TODO: Add items to player inventory when InventoryComponent is integrated
        corpse.contents.forEach { instance ->
            val templateResult = game.itemRepository.findTemplateById(instance.templateId)
            val templateName = templateResult.getOrNull()?.name ?: "item"

            currentCorpse = currentCorpse.removeItem(instance.id)
            println("You take the $templateName.")
            lootedCount++

            // Track item collection for quests
            game.trackQuests(QuestAction.CollectedItem(instance.id))
        }

        // Loot gold
        if (corpse.goldAmount > 0) {
            println("You take ${corpse.goldAmount} gold.")
            currentCorpse = currentCorpse.removeGold(corpse.goldAmount)
            // TODO: Add gold to player inventory when InventoryComponent is integrated
        }

        val newState = game.worldState
            .removeEntityFromRoom(room.id, corpse.id)
            ?.addEntityToRoom(room.id, currentCorpse)

        if (newState != null) {
            game.worldState = newState

            val summary = buildString {
                append("\nYou looted ")
                if (lootedCount > 0) {
                    append("$lootedCount item${if (lootedCount > 1) "s" else ""}")
                }
                if (lootedCount > 0 && corpse.goldAmount > 0) {
                    append(" and ")
                }
                if (corpse.goldAmount > 0) {
                    append("${corpse.goldAmount} gold")
                }
                append(" from ${corpse.name}.")
            }
            println(summary)
        } else {
            println("Something went wrong.")
        }
    }
}
