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
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.ItemType
import com.jcraw.mud.core.PlayerState

/**
 * Inventory display for [ClientItemHandlers] facade.
 */
object ClientItemInventoryHandlers {

    fun handleInventory(game: EngineGameClient) {
        val player = game.worldState.player
        val invComp = player.inventoryComponent
        val text = buildString {
            appendLine("Inventory:")
            appendLine()
            if (invComp != null) {
                appendV2Inventory(game, invComp)
            } else {
                appendLegacyInventory(player)
            }
        }
        game.emitEvent(GameEvent.Narrative(text))
    }

    private fun StringBuilder.appendV2Inventory(game: EngineGameClient, invComp: InventoryComponent) {
        appendLine("  Gold: ${invComp.gold}")
        val templates = loadTemplates(game, invComp)
        val currentWeight = invComp.currentWeight(templates)
        val capacity = invComp.capacityWeight
        appendLine("  Weight: ${"%.1f".format(currentWeight)}kg / ${"%.1f".format(capacity)}kg")
        appendEquipped(invComp, templates)
        appendCarrying(invComp, templates)
    }

    private fun loadTemplates(
        game: EngineGameClient,
        invComp: InventoryComponent
    ): Map<String, ItemTemplate> {
        val templates = mutableMapOf<String, ItemTemplate>()
        invComp.items.forEach { instance ->
            val result = game.itemRepository.findTemplateById(instance.templateId)
            result.getOrNull()?.let { templates[it.id] = it }
        }
        return templates
    }

    private fun StringBuilder.appendEquipped(
        invComp: InventoryComponent,
        templates: Map<String, ItemTemplate>
    ) {
        if (invComp.equipped.isNotEmpty()) {
            appendLine()
            appendLine("  Equipped:")
            invComp.equipped.forEach { (slot, instance) ->
                val template = templates[instance.templateId]
                if (template != null) {
                    val info = formatItemInfo(instance, template)
                    appendLine("    $slot: ${template.name}$info")
                } else {
                    appendLine("    $slot: ${instance.templateId} (template missing)")
                }
            }
        } else {
            appendLine()
            appendLine("  Equipped: (nothing)")
        }
    }

    private fun StringBuilder.appendCarrying(
        invComp: InventoryComponent,
        templates: Map<String, ItemTemplate>
    ) {
        val unequippedItems = invComp.items.filter { item ->
            !invComp.equipped.values.any { it.id == item.id }
        }
        if (unequippedItems.isEmpty()) {
            appendLine("  Carrying: (nothing)")
        } else {
            appendLine("  Carrying:")
            unequippedItems.forEach { instance ->
                val template = templates[instance.templateId]
                if (template != null) {
                    val info = formatItemInfo(instance, template)
                    appendLine("    - ${template.name}$info")
                } else {
                    appendLine("    - ${instance.templateId} (template missing)")
                }
            }
        }
    }

    private fun StringBuilder.appendLegacyInventory(player: PlayerState) {
        appendLegacyEquipped(player)
        appendLegacyCarrying(player)
    }

    private fun StringBuilder.appendLegacyEquipped(player: PlayerState) {
        if (player.equippedWeapon != null) {
            appendLine("  Equipped Weapon: ${player.equippedWeapon!!.name} (+${player.equippedWeapon!!.damageBonus} damage)")
        } else {
            appendLine("  Equipped Weapon: (none)")
        }
        if (player.equippedArmor != null) {
            appendLine("  Equipped Armor: ${player.equippedArmor!!.name} (+${player.equippedArmor!!.defenseBonus} defense)")
        } else {
            appendLine("  Equipped Armor: (none)")
        }
    }

    private fun StringBuilder.appendLegacyCarrying(player: PlayerState) {
        if (player.inventory.isEmpty()) {
            appendLine("  Carrying: (nothing)")
            return
        }
        appendLine("  Carrying:")
        player.inventory.forEach { item ->
            val extra = when (item.itemType) {
                ItemType.WEAPON -> " [weapon, +${item.damageBonus} damage]"
                ItemType.ARMOR -> " [armor, +${item.defenseBonus} defense]"
                ItemType.CONSUMABLE -> " [heals ${item.healAmount} HP]"
                else -> ""
            }
            appendLine("    - ${item.name}$extra")
        }
    }
}
