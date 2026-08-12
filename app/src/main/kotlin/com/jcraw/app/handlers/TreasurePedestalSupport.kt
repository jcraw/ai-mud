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
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.PedestalState
import com.jcraw.mud.core.TreasureRoomComponent

/**
 * Template / name / barrier / stats helpers for treasure room handlers (MUD-034l).
 */
internal object TreasurePedestalSupport {

    fun buildItemTemplatesMap(game: MudGame, treasureRoom: TreasureRoomComponent): Map<String, ItemTemplate> {
        val templates = mutableMapOf<String, ItemTemplate>()
        treasureRoom.pedestals.forEach { pedestal ->
            val result = game.itemRepository.findTemplateById(pedestal.itemTemplateId)
            result.getOrNull()?.let { templates[it.id] = it }
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
