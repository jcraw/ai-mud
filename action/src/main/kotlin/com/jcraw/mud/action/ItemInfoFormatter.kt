package com.jcraw.mud.action

import com.jcraw.mud.core.ItemInstance
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.ItemType

/**
 * Formats item display info from [ItemInstance] and [ItemTemplate].
 * Shared by console and GUI inventory handlers (MUD-039).
 *
 * Example: `" [weapon, +10 damage, quality 7/10]"`
 */
object ItemInfoFormatter {

    private const val DEFAULT_QUALITY = 5
    private const val QUALITY_MAX = 10

    fun format(instance: ItemInstance, template: ItemTemplate): String {
        val parts = mutableListOf<String>()
        parts.add(template.type.name.lowercase())
        appendTypeParts(parts, instance, template)
        if (instance.quality != DEFAULT_QUALITY) {
            parts.add("quality ${instance.quality}/$QUALITY_MAX")
        }
        return if (parts.isEmpty()) "" else " [${parts.joinToString(", ")}]"
    }

    private fun appendTypeParts(
        parts: MutableList<String>,
        instance: ItemInstance,
        template: ItemTemplate
    ) {
        when (template.type) {
            ItemType.WEAPON -> appendWeaponParts(parts, instance, template)
            ItemType.ARMOR -> appendArmorParts(parts, instance, template)
            ItemType.CONSUMABLE -> appendConsumableParts(parts, instance, template)
            ItemType.TOOL -> appendToolParts(parts, instance)
            ItemType.RESOURCE -> appendResourceParts(parts, instance)
            else -> {}
        }
    }

    private fun appendWeaponParts(
        parts: MutableList<String>,
        instance: ItemInstance,
        template: ItemTemplate
    ) {
        val baseDamage = template.getPropertyInt("damage", 0)
        val damage = (baseDamage * instance.getQualityMultiplier()).toInt()
        if (damage > 0) parts.add("+$damage damage")
    }

    private fun appendArmorParts(
        parts: MutableList<String>,
        instance: ItemInstance,
        template: ItemTemplate
    ) {
        val baseDefense = template.getPropertyInt("defense", 0)
        val defense = (baseDefense * instance.getQualityMultiplier()).toInt()
        if (defense > 0) parts.add("+$defense defense")
    }

    private fun appendConsumableParts(
        parts: MutableList<String>,
        instance: ItemInstance,
        template: ItemTemplate
    ) {
        val healing = template.getPropertyInt("healing", 0)
        if (healing > 0) parts.add("heals $healing HP")
        if (instance.charges != null) parts.add("${instance.charges} charges")
    }

    private fun appendToolParts(parts: MutableList<String>, instance: ItemInstance) {
        if (instance.charges != null) parts.add("${instance.charges} charges")
    }

    private fun appendResourceParts(parts: MutableList<String>, instance: ItemInstance) {
        if (instance.quantity > 1) parts.add("x${instance.quantity}")
    }
}
