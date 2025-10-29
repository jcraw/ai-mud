package com.jcraw.app.handlers

import com.jcraw.mud.core.*
import com.jcraw.mud.core.repository.ItemRepository
import com.jcraw.mud.perception.Intent
import com.jcraw.mud.reasoning.items.ItemUseHandler

/**
 * Handles multipurpose item use intents beyond simple consumption
 */

/**
 * Handle multipurpose item use intent
 *
 * TODO: Full integration pending InventoryComponent migration
 * Currently returns stub message with use possibilities
 */
fun handleUseItem(
    intent: Intent.UseItem,
    world: WorldState,
    player: PlayerState,
    itemUseHandler: ItemUseHandler,
    itemRepository: ItemRepository
): Pair<WorldState, String> {
    // TODO: Get player's InventoryComponent when fully integrated
    // TODO: Find item by name and get instance ID
    // For now, return placeholder message showing what's possible

    return world to """
        |Multipurpose item use system implemented but requires InventoryComponent integration.
        |
        |Item: ${intent.target}
        |Action: ${intent.action}
        |Target: ${intent.actionTarget ?: "none"}
        |
        |This feature will be fully functional once InventoryComponent is integrated into player state.
        |
        |Supported uses (based on item tags):
        |- Improvised weapon: Items with "blunt" or "sharp" tags (damage = weight * 0.5)
        |- Explosive: Items with "explosive" tag (configurable AoE damage and timer)
        |- Container: Items with "container" tag (capacity bonus)
        |- Flammable: Burn for light/distraction
        |- Fragile: Break to create noise/shards
        |- Liquid: Pour to create puddle/wet surface
        |
        |Examples:
        |- "bash goblin with pot" → Attack with improvised weapon
        |- "throw dynamite" → Explosive with 3-turn timer
        |- "burn cloth" → Create light source
    """.trimMargin()
}

/**
 * Show possible uses for an item
 */
fun handleExamineItemUses(
    itemName: String,
    itemRepository: ItemRepository,
    itemUseHandler: ItemUseHandler
): String {
    // Find item template by name
    val templates = itemRepository.findAll().getOrNull() ?: emptyList()
    val template = templates.find { it.name.equals(itemName, ignoreCase = true) }
        ?: return "Item not found: $itemName"

    val possibleUses = itemUseHandler.getPossibleUses(template)

    return if (possibleUses.isEmpty()) {
        "${template.name} has no special uses beyond normal consumption/equipment."
    } else {
        """
        |${template.name} can be used for:
        |${possibleUses.joinToString("\n") { "- $it" }}
        """.trimMargin()
    }
}
