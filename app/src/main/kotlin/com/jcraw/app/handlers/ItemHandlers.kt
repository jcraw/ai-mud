package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.app.times
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.ItemType
import com.jcraw.mud.reasoning.QuestAction

/**
 * Handlers for item interactions: inventory, take, drop, equip, use
 */
object ItemHandlers {

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
                val inCombat = game.worldState.player.isInCombat()

                // Consume the item and heal
                game.worldState = game.worldState.updatePlayer(game.worldState.player.useConsumable(item))
                val healedAmount = game.worldState.player.health - oldHealth

                if (healedAmount > 0) {
                    println("\nYou consume the ${item.name} and restore $healedAmount HP.")
                    println("Current health: ${game.worldState.player.health}/${game.worldState.player.maxHealth}")
                } else {
                    println("\nYou consume the ${item.name}, but you're already at full health.")
                }

                // If in combat, the NPC gets a free attack (using an item consumes your turn)
                if (inCombat) {
                    val combat = game.worldState.player.activeCombat!!
                    val room = game.worldState.getCurrentRoom() ?: return
                    val npc = room.entities.filterIsInstance<Entity.NPC>()
                        .find { it.id == combat.combatantNpcId }

                    if (npc != null) {
                        // Calculate NPC damage
                        val npcDamage = calculateNpcDamage(game, npc)
                        val afterNpcAttack = combat.applyNpcDamage(npcDamage)

                        println("\nThe enemy strikes back for $npcDamage damage while you drink!")

                        // Check if player died
                        if (afterNpcAttack.playerHealth <= 0) {
                            game.worldState = game.worldState.updatePlayer(game.worldState.player.endCombat())
                            println("\nYou have been defeated! Game over.")
                            println("\nPress any key to play again...")
                            readLine()  // Wait for any input

                            // Restart the game
                            game.worldState = game.initialWorldState
                            println("\n" + "=" * 60)
                            println("  Restarting Adventure...")
                            println("=" * 60)
                            game.printWelcome()
                            game.describeCurrentRoom()
                        } else {
                            // Update combat state with new health
                            val updatedPlayer = game.worldState.player
                                .updateCombat(afterNpcAttack)
                                .copy(health = afterNpcAttack.playerHealth)
                            game.worldState = game.worldState.updatePlayer(updatedPlayer)
                            game.describeCurrentRoom()  // Show updated combat status
                        }
                    }
                }
            }
            ItemType.WEAPON -> {
                println("Try 'equip ${item.name}' to equip this weapon.")
            }
            else -> {
                println("You're not sure how to use that.")
            }
        }
    }

    /**
     * Calculate damage dealt by NPC attack (helper for potion use during combat).
     * Base damage + STR modifier - player armor defense.
     */
    private fun calculateNpcDamage(game: MudGame, npc: Entity.NPC): Int {
        // Base damage 3-12 + STR modifier - armor defense
        val baseDamage = kotlin.random.Random.nextInt(3, 13)
        val strModifier = npc.stats.strModifier()
        val armorDefense = game.worldState.player.getArmorDefenseBonus()
        return (baseDamage + strModifier - armorDefense).coerceAtLeast(1)
    }
}
