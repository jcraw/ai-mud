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

package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.core.InventoryComponent
import com.jcraw.mud.core.ItemInstance
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.PlayerState

/**
 * Equip for [ClientItemHandlers] facade.
 */
object ClientItemEquipHandlers {

    fun handleEquip(game: EngineGameClient, target: String) {
        val player = game.worldState.player
        val invComp = player.inventoryComponent
        val itemInstance = findEquippableInstance(game, invComp.items, target.lowercase())
        if (itemInstance == null) {
            game.emitEvent(
                GameEvent.System("You don't have that in your inventory.", GameEvent.MessageLevel.WARNING)
            )
            return
        }
        val template = game.itemRepository.findTemplateById(itemInstance.templateId).getOrNull()
        if (template == null) {
            game.emitEvent(
                GameEvent.System(
                    "Item template missing for '${itemInstance.templateId}' — cannot equip.",
                    GameEvent.MessageLevel.ERROR
                )
            )
            return
        }
        applyEquip(game, player, invComp, itemInstance, template)
    }

    private fun applyEquip(
        game: EngineGameClient,
        player: PlayerState,
        invComp: InventoryComponent,
        itemInstance: ItemInstance,
        template: ItemTemplate
    ) {
        val equipSlot = template.equipSlot
        if (equipSlot == null) {
            game.emitEvent(GameEvent.System("You can't equip that.", GameEvent.MessageLevel.WARNING))
            return
        }
        val updatedInventory = invComp.equip(itemInstance, equipSlot)
        if (updatedInventory == null) {
            game.emitEvent(GameEvent.System("Error: Could not equip item", GameEvent.MessageLevel.ERROR))
            return
        }
        game.worldState = game.worldState.updatePlayer(player.copy(inventoryComponent = updatedInventory))
        game.emitEvent(GameEvent.Narrative("You equip the ${template.name}."))
    }

    private fun findEquippableInstance(
        game: EngineGameClient,
        items: List<ItemInstance>,
        query: String
    ): ItemInstance? =
        items.find { instance ->
            val template = game.itemRepository.findTemplateById(instance.templateId).getOrNull()
            (template?.name?.lowercase()?.contains(query) == true) ||
                instance.templateId.lowercase().contains(query) ||
                instance.id.lowercase().contains(query)
        }
}
