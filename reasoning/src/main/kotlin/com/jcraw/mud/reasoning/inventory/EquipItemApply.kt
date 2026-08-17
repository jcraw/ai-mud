package com.jcraw.mud.reasoning.inventory

import com.jcraw.mud.core.EquipSlot
import com.jcraw.mud.core.InventoryComponent
import com.jcraw.mud.core.ItemInstance
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.PlayerState

/**
 * Pure apply for equip → V2 [InventoryComponent.equip].
 * Shared by console, GUI, and multi-user handlers.
 *
 * Resolve order matches [UseConsumableApply]: instance id → name contains → templateId.
 * Never writes V1 inventory/equip fields.
 */
object EquipItemApply {

    sealed class Result {
        data class Success(
            val player: PlayerState,
            val itemName: String,
            val slot: EquipSlot,
            val instanceId: String
        ) : Result()

        data class Failure(val message: String) : Result()
    }

    fun apply(
        player: PlayerState,
        target: String,
        templates: Map<String, ItemTemplate>
    ): Result {
        val instance = UseConsumableApply.resolveInstance(player, target, templates)
        val template = instance?.let { templates[it.templateId] }
        return equipOrFail(player, instance, template)
    }

    private fun equipOrFail(
        player: PlayerState,
        instance: ItemInstance?,
        template: ItemTemplate?
    ): Result {
        val slot = template?.equipSlot
        val equipped = if (instance != null && slot != null) {
            player.inventoryComponent.equip(instance, slot)
        } else {
            null
        }
        return classify(player, instance, template, slot, equipped)
    }

    private fun classify(
        player: PlayerState,
        instance: ItemInstance?,
        template: ItemTemplate?,
        slot: EquipSlot?,
        equipped: InventoryComponent?
    ): Result = when {
        instance == null -> Result.Failure("You don't have that in your inventory.")
        template == null -> Result.Failure("Error: Item template not found")
        slot == null -> Result.Failure("You can't equip that.")
        equipped == null -> Result.Failure("Error: Could not equip item")
        else -> Result.Success(
            player = player.updateInventory(equipped),
            itemName = template.name,
            slot = slot,
            instanceId = instance.id
        )
    }
}
