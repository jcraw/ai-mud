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
import com.jcraw.mud.core.InventoryComponent
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.ItemType
import com.jcraw.mud.core.PlayerState

/**
 * Inventory display for [ItemHandlers] facade.
 */
object ItemInventoryHandlers {

    fun handleInventory(game: MudGame) {
        println("Inventory:")
        val player = game.worldState.player
        val invComp = player.inventoryComponent
        println("DEBUG: inventoryComponent is ${if (invComp != null) "NOT NULL (V2)" else "NULL (legacy)"}")
        if (invComp != null) {
            println("DEBUG: V2 inventory has ${invComp.items.size} items")
            displayV2Inventory(game, invComp)
        } else {
            displayLegacyInventory(player)
        }
    }

    private fun displayV2Inventory(game: MudGame, invComp: InventoryComponent) {
        println("  Gold: ${invComp.gold}")
        val templates = loadInventoryTemplates(game, invComp)
        val currentWeight = invComp.currentWeight(templates)
        val capacity = invComp.capacityWeight
        println("  Weight: ${"%.1f".format(currentWeight)}kg / ${"%.1f".format(capacity)}kg")
        displayEquipped(invComp, templates)
        displayCarrying(invComp, templates)
    }

    private fun loadInventoryTemplates(
        game: MudGame,
        invComp: InventoryComponent
    ): Map<String, ItemTemplate> {
        val templates = mutableMapOf<String, ItemTemplate>()
        invComp.items.forEach { instance ->
            val result = game.itemRepository.findTemplateById(instance.templateId)
            result.getOrNull()?.let { templates[it.id] = it }
        }
        return templates
    }

    private fun displayEquipped(
        invComp: InventoryComponent,
        templates: Map<String, ItemTemplate>
    ) {
        if (invComp.equipped.isNotEmpty()) {
            println("\n  Equipped:")
            invComp.equipped.forEach { (slot, instance) ->
                val template = templates[instance.templateId]
                if (template != null) {
                    val info = formatItemInfo(instance, template)
                    println("    $slot: ${template.name}$info")
                }
            }
        } else {
            println("\n  Equipped: (nothing)")
        }
    }

    private fun displayCarrying(
        invComp: InventoryComponent,
        templates: Map<String, ItemTemplate>
    ) {
        val unequippedItems = invComp.items.filter { item ->
            !invComp.equipped.values.any { it.id == item.id }
        }
        if (unequippedItems.isEmpty()) {
            println("  Carrying: (nothing)")
        } else {
            println("  Carrying:")
            unequippedItems.forEach { instance ->
                val template = templates[instance.templateId]
                if (template != null) {
                    val info = formatItemInfo(instance, template)
                    println("    - ${template.name}$info")
                }
            }
        }
    }

    private fun displayLegacyInventory(player: PlayerState) {
        println("  Gold: ${player.gold}")
        displayLegacyEquipped(player)
        displayLegacyCarrying(player)
    }

    private fun displayLegacyEquipped(player: PlayerState) {
        if (player.equippedWeapon != null) {
            println("  Equipped Weapon: ${player.equippedWeapon!!.name} (+${player.equippedWeapon!!.damageBonus} damage)")
        } else {
            println("  Equipped Weapon: (none)")
        }
        if (player.equippedArmor != null) {
            println("  Equipped Armor: ${player.equippedArmor!!.name} (+${player.equippedArmor!!.defenseBonus} defense)")
        } else {
            println("  Equipped Armor: (none)")
        }
    }

    private fun displayLegacyCarrying(player: PlayerState) {
        if (player.inventory.isEmpty()) {
            println("  Carrying: (nothing)")
            return
        }
        println("  Carrying:")
        player.inventory.forEach { item ->
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
