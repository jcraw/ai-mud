package com.jcraw.mud.reasoning.inventory

import com.jcraw.mud.core.ItemInstance
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.WorldState

/**
 * Pure apply for give → V2 [InventoryComponent] remove only.
 * Shared by console, GUI, and multi-user handlers.
 *
 * Does not write V1 inventory/equip fields. Does not transfer item into NPC storage
 * (current give strips player + quest [DeliveredItem] tracking in the handler).
 */
object GiveItemApply {

    sealed class Result {
        data class Success(
            val world: WorldState,
            val itemName: String,
            val templateId: String,
            val instanceId: String
        ) : Result()

        data class Failure(val message: String) : Result()
    }

    /**
     * Remove an inventory item matching [target] from the player's V2 inventory.
     *
     * Resolve order: exact instance id → template name contains → templateId contains.
     * Success: [InventoryComponent.removeItem] + [PlayerState.updateInventory].
     */
    fun apply(
        world: WorldState,
        player: PlayerState,
        target: String,
        templates: Map<String, ItemTemplate>
    ): Result {
        val instance = resolveInstance(player, target, templates)
        val newInv = instance?.let { player.inventoryComponent.removeItem(it.id) }

        return if (instance == null || newInv == null) {
            Result.Failure("You don't have that item.")
        } else {
            val template = templates[instance.templateId]
            val itemName = template?.name ?: instance.templateId
            Result.Success(
                world = world.updatePlayer(player.updateInventory(newInv)),
                itemName = itemName,
                templateId = instance.templateId,
                instanceId = instance.id
            )
        }
    }

    /**
     * Prefer exact instance id; else template name contains target; else templateId contains.
     */
    internal fun resolveInstance(
        player: PlayerState,
        target: String,
        templates: Map<String, ItemTemplate>
    ): ItemInstance? {
        val query = target.lowercase().trim()
        val items = player.inventoryComponent.items
        if (query.isEmpty() || items.isEmpty()) return null

        return items.find { it.id.equals(query, ignoreCase = true) }
            ?: items.find { instance ->
                templates[instance.templateId]?.name?.lowercase()?.contains(query) == true
            }
            ?: items.find { it.templateId.lowercase().contains(query) }
    }
}
