package com.jcraw.mud.reasoning.inventory

import com.jcraw.mud.core.ItemInstance
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.ItemType
import com.jcraw.mud.core.PlayerState

/**
 * Pure apply for consumable use → V2 [InventoryComponent] remove + heal.
 * Shared by console, GUI, and multi-user handlers.
 *
 * Never calls V1 consumable mutators / never writes V1 inventory list.
 */
object UseConsumableApply {

    sealed class Result {
        data class Success(
            val player: PlayerState,
            val itemName: String,
            val healedAmount: Int,
            val instanceId: String
        ) : Result()

        data class Failure(val message: String) : Result()
    }

    /**
     * Resolve [target] from V2 inventory and consume one unit if consumable.
     *
     * Resolve order: exact instance id → template name contains → templateId contains.
     * Heal amount from template property `healing` (default 0). Fail closed if no match
     * or non-consumable (weapon gets equip hint).
     */
    fun apply(
        player: PlayerState,
        target: String,
        templates: Map<String, ItemTemplate>
    ): Result {
        val instance = resolveInstance(player, target, templates)
        val template = instance?.let { templates[it.templateId] }
        val itemName = template?.name ?: instance?.templateId

        return when {
            instance == null -> Result.Failure("You don't have that in your inventory.")
            template == null || itemName == null -> Result.Failure("You can't use that.")
            template.type == ItemType.WEAPON ->
                Result.Failure("Try 'equip $itemName' to equip this weapon.")
            template.type != ItemType.CONSUMABLE ->
                Result.Failure("You're not sure how to use that.")
            else -> consume(player, instance, template, itemName)
        }
    }

    private fun consume(
        player: PlayerState,
        instance: ItemInstance,
        template: ItemTemplate,
        itemName: String
    ): Result {
        val healAmount = template.getPropertyInt("healing", 0)
        val newInv = if (instance.quantity > 1) {
            player.inventoryComponent.removeQuantity(instance.id, 1)
        } else {
            player.inventoryComponent.removeItem(instance.id)
        }

        return if (newInv == null) {
            Result.Failure("You don't have that in your inventory.")
        } else {
            val oldHealth = player.health
            val updated = player.updateInventory(newInv).heal(healAmount)
            Result.Success(
                player = updated,
                itemName = itemName,
                healedAmount = updated.health - oldHealth,
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
