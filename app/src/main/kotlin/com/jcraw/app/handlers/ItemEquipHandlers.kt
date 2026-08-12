@file:Suppress(
    "ReturnCount",
    "MagicNumber",
    "MaxLineLength",
    "TooManyFunctions",
    "LongMethod",
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "LongParameterList"
)

package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.core.ItemInstance
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.PlayerState

/**
 * Equip for [ItemHandlers] facade.
 */
object ItemEquipHandlers {

    fun handleEquip(game: MudGame, target: String) {
        val player = game.worldState.player
        val invComp = player.inventoryComponent
        val itemInstance = findEquippableInstance(game, invComp.items, target.lowercase())
        if (itemInstance == null) {
            println("You don't have that in your inventory.")
            return
        }
        val template = game.itemRepository.findTemplateById(itemInstance.templateId).getOrNull()
        if (template == null) {
            println("Error: Item template not found")
            return
        }
        applyEquip(game, player, invComp, itemInstance, template)
    }

    private fun applyEquip(
        game: MudGame,
        player: PlayerState,
        invComp: com.jcraw.mud.core.InventoryComponent,
        itemInstance: ItemInstance,
        template: ItemTemplate
    ) {
        val equipSlot = template.equipSlot
        if (equipSlot == null) {
            println("You can't equip that.")
            return
        }
        val updatedInventory = invComp.equip(itemInstance, equipSlot)
        if (updatedInventory == null) {
            println("Error: Could not equip item")
            return
        }
        game.worldState = game.worldState.updatePlayer(player.copy(inventoryComponent = updatedInventory))
        println("You equip the ${template.name}.")
    }

    private fun findEquippableInstance(
        game: MudGame,
        items: List<ItemInstance>,
        query: String
    ): ItemInstance? =
        items.find { instance ->
            val template = game.itemRepository.findTemplateById(instance.templateId).getOrNull()
            template != null && (
                template.name.lowercase().contains(query) ||
                    instance.templateId.lowercase().contains(query) ||
                    instance.id.lowercase().contains(query)
                )
        }
}
