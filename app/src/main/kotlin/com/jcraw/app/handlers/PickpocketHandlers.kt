package com.jcraw.app.handlers

import com.jcraw.mud.core.*
import com.jcraw.mud.core.repository.ItemRepository
import com.jcraw.mud.perception.Intent
import com.jcraw.mud.reasoning.pickpocket.PickpocketHandler

/**
 * Handles pickpocketing intents - stealing from or placing items on NPCs
 */

/**
 * Handle pickpocket intent
 *
 * TODO: Full integration pending InventoryComponent migration
 * Currently returns stub message with skill check simulation
 */
fun handlePickpocket(
    intent: Intent.Pickpocket,
    world: WorldState,
    player: PlayerState,
    pickpocketHandler: PickpocketHandler,
    itemRepository: ItemRepository
): Pair<WorldState, String> {
    val currentRoom = world.rooms[player.currentRoomId]
        ?: return world to "You are nowhere!"

    // Find target NPC in room
    val targetNpc = currentRoom.entities.filterIsInstance<Entity.NPC>()
        .find { it.name.equals(intent.npcTarget, ignoreCase = true) }
        ?: return world to "You don't see ${intent.npcTarget} here."

    // TODO: Get player's InventoryComponent and SkillComponent when fully integrated
    // TODO: Get all item templates for lookups
    // For now, return placeholder message

    return world to """
        |Pickpocketing system implemented but requires InventoryComponent integration.
        |
        |Action: ${intent.action}
        |Target: ${targetNpc.name}
        |Item: ${intent.itemTarget ?: "gold"}
        |
        |This feature will be fully functional once InventoryComponent is integrated into player state.
        |The skill check would use your Stealth or Agility vs ${targetNpc.name}'s Perception.
        |
        |On success: You ${if (intent.action == "steal") "steal" else "place"} the item undetected
        |On failure: Disposition drops by -20 to -50, and ${targetNpc.name} gains Wariness status (+20 Perception for 10 turns)
    """.trimMargin()
}
