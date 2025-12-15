package com.jcraw.app.handlers

import com.jcraw.mud.core.*
import com.jcraw.mud.core.repository.ItemRepository
import com.jcraw.mud.perception.Intent
import com.jcraw.mud.reasoning.items.ItemUseHandler

/**
 * Handles multipurpose item use intents beyond simple consumption
 * Integrates ItemUseHandler (reasoning) with game state management
 */

/**
 * Handle multipurpose item use intent
 *
 * Supports creative item uses based on tags:
 * - Improvised weapon: Items with "blunt" or "sharp" tags
 * - Explosive: Items with "explosive" tag
 * - Container: Items with "container" tag
 * - Environmental: flammable, fragile, liquid tags
 */
fun handleUseItem(
    intent: Intent.UseItem,
    world: WorldState,
    player: PlayerState,
    itemUseHandler: ItemUseHandler,
    itemRepository: ItemRepository
): Pair<WorldState, String> {
    // Get player's InventoryComponent
    val inventory = player.inventoryComponent
    if (inventory == null) {
        return world to "You don't have an inventory."
    }

    // Find item by name in inventory
    val itemInstance = inventory.items.find { instance ->
        val template = itemRepository.findTemplateById(instance.templateId).getOrNull()
        template != null && (
            template.name.lowercase().contains(intent.target.lowercase()) ||
            instance.templateId.lowercase().contains(intent.target.lowercase())
        )
    }

    if (itemInstance == null) {
        return world to "You don't have that item."
    }

    // Determine action from intent
    val action = intent.action ?: "use"

    // Use ItemUseHandler to determine appropriate use
    val result = itemUseHandler.determineUse(
        instanceId = itemInstance.id,
        action = action,
        inventory = inventory
    )

    return when (result) {
        is ItemUseHandler.ItemUseResult.ImprovisedWeapon -> {
            // Return message about weapon capability - actual attack handled by combat system
            world to buildString {
                append("You ready the ${result.itemName} as an improvised weapon.\n")
                append("Damage bonus: +${result.damageBonus}\n")
                append("Tags: ${result.itemTags.joinToString(", ")}\n")
                append("\nUse 'attack <target> with ${result.itemName}' to strike.")
            }
        }

        is ItemUseHandler.ItemUseResult.ExplosiveUse -> {
            // For now, just describe the explosive capability
            // Full implementation would add a timed effect to the turn queue
            world to buildString {
                append("You arm the ${result.itemName}!\n")
                append("AoE Damage: ${result.aoeDamage}\n")
                append("Timer: ${result.timer} turns\n")
                append("\n(Explosive mechanics not yet implemented in combat system)")
            }
        }

        is ItemUseHandler.ItemUseResult.ContainerUse -> {
            // Apply capacity bonus to player inventory
            val updatedInventory = inventory.augmentCapacity(result.capacityBonus)
            val updatedPlayer = player.copy(inventoryComponent = updatedInventory)
            val newWorld = world.updatePlayer(updatedPlayer)

            newWorld to buildString {
                append("You equip the ${result.itemName} as a container.\n")
                append("Carrying capacity increased by +${result.capacityBonus}kg.\n")
                append("New capacity: ${updatedInventory.capacityWeight}kg")
            }
        }

        is ItemUseHandler.ItemUseResult.EnvironmentalUse -> {
            // Describe the environmental effect
            world to buildString {
                append("You use the ${result.itemName}.\n")
                append(result.effect)
            }
        }

        is ItemUseHandler.ItemUseResult.Failure -> {
            world to result.reason
        }
    }
}

/**
 * Show possible uses for an item based on its tags
 */
fun handleExamineItemUses(
    itemName: String,
    itemRepository: ItemRepository,
    itemUseHandler: ItemUseHandler,
    inventory: InventoryComponent?
): String {
    if (inventory == null) {
        return "You don't have an inventory."
    }

    // Find item by name in inventory
    val itemInstance = inventory.items.find { instance ->
        val template = itemRepository.findTemplateById(instance.templateId).getOrNull()
        template != null && (
            template.name.lowercase().contains(itemName.lowercase()) ||
            instance.templateId.lowercase().contains(itemName.lowercase())
        )
    }

    if (itemInstance == null) {
        return "You don't have that item."
    }

    // Get template
    val template = itemRepository.findTemplateById(itemInstance.templateId).getOrNull()
        ?: return "Item template not found."

    // Get possible uses
    val possibleUses = itemUseHandler.getPossibleUses(template)

    return if (possibleUses.isEmpty()) {
        "The ${template.name} doesn't have any special uses."
    } else {
        buildString {
            append("Possible uses for ${template.name}:\n")
            possibleUses.forEach { use ->
                append("  - $use\n")
            }
        }
    }
}
