@file:Suppress("TooManyFunctions")

package com.jcraw.mud.reasoning.treasureroom

import com.jcraw.mud.core.ItemInstance
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.PedestalState
import com.jcraw.mud.core.TreasureRoomComponent

/**
 * Pedestal name/barrier/stats helpers. Shared by console and GUI (MUD-039).
 * Template lookup stays in the handler wrapper (client extra getItemTemplate fallback).
 */
object TreasurePedestalSupport {

    fun buildItemTemplatesMap(
        treasureRoom: TreasureRoomComponent,
        lookup: (String) -> ItemTemplate?
    ): Map<String, ItemTemplate> {
        val templates = mutableMapOf<String, ItemTemplate>()
        treasureRoom.pedestals.forEach { pedestal ->
            lookup(pedestal.itemTemplateId)?.let { templates[it.id] = it }
        }
        return templates
    }

    fun findItemTemplateByName(
        nameQuery: String,
        templates: Map<String, ItemTemplate>,
        treasureRoom: TreasureRoomComponent
    ): String? {
        return treasureRoom.pedestals
            .firstOrNull { pedestal ->
                val template = templates[pedestal.itemTemplateId]
                template?.name?.lowercase()?.contains(nameQuery.lowercase()) == true
            }
            ?.itemTemplateId
    }

    fun getAvailableItemNames(
        treasureRoom: TreasureRoomComponent,
        templates: Map<String, ItemTemplate>
    ): List<String> {
        return treasureRoom.pedestals
            .filter { it.state == PedestalState.AVAILABLE }
            .mapNotNull { pedestal -> templates[pedestal.itemTemplateId]?.name }
    }

    fun getPedestalDescription(treasureRoom: TreasureRoomComponent, itemTemplateId: String): String {
        return treasureRoom.getPedestal(itemTemplateId)?.themeDescription ?: "pedestal"
    }

    fun getBarrierTypeForBiome(biomeTheme: String): String {
        return when (biomeTheme.lowercase()) {
            "ancient_abyss", "ancient_ruins" -> "shimmering arcane barriers"
            "magma_cave", "magma_caves" -> "walls of molten energy"
            "frozen_depths", "ice_cavern" -> "frozen barriers of solid ice"
            "bone_crypt", "bone_crypts" -> "cages of blackened bone"
            else -> "magical barriers"
        }
    }

    fun extractItemStats(template: ItemTemplate): List<String> {
        val stats = mutableListOf<String>()
        appendSkillBonuses(stats, template)
        appendCombatStats(stats, template)
        return stats
    }

    fun findInventoryItem(
        items: List<ItemInstance>,
        templates: Map<String, ItemTemplate>,
        query: String
    ): ItemInstance? {
        return items.find { instance ->
            val template = templates[instance.templateId]
            template?.name?.lowercase()?.contains(query.lowercase()) == true ||
                instance.templateId.lowercase().contains(query.lowercase())
        }
    }

    fun availableItemsLine(treasureRoom: TreasureRoomComponent, templates: Map<String, ItemTemplate>): String {
        val names = getAvailableItemNames(treasureRoom, templates).joinToString(", ")
        return "That item is not on any pedestal in this room.\nAvailable items: $names"
    }

    private fun appendSkillBonuses(stats: MutableList<String>, template: ItemTemplate) {
        addBonus(stats, template, "skill_bonus_strength", "STR")
        addBonus(stats, template, "skill_bonus_agility", "AGI")
        addBonus(stats, template, "skill_bonus_endurance", "END")
        addBonus(stats, template, "skill_bonus_magic", "MAG")
        addBonus(stats, template, "skill_bonus_wisdom", "WIS")
        addBonus(stats, template, "skill_bonus_charisma", "CHA")
        addBonus(stats, template, "skill_bonus_perception", "PER")
    }

    private fun addBonus(
        stats: MutableList<String>,
        template: ItemTemplate,
        key: String,
        label: String
    ) {
        template.properties[key]?.toIntOrNull()?.let { bonus ->
            if (bonus > 0) stats.add("$label +$bonus")
        }
    }

    private fun appendCombatStats(stats: MutableList<String>, template: ItemTemplate) {
        template.properties["damage"]?.toIntOrNull()?.let { damage ->
            if (damage > 0) stats.add(0, "${damage} dmg")
        }
        template.properties["defense"]?.toIntOrNull()?.let { defense ->
            if (defense > 0) stats.add(0, "${defense} def")
        }
    }
}
