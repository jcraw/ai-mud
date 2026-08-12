@file:Suppress("MagicNumber", "MaxLineLength")

package com.jcraw.app.handlers

import com.jcraw.mud.core.ItemInstance
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.ItemType

/**
 * Format item display info from ItemInstance and ItemTemplate.
 * Returns a string like " [weapon, +10 damage, quality 7/10]"
 */
internal fun formatItemInfo(instance: ItemInstance, template: ItemTemplate): String {
    val parts = mutableListOf<String>()
    parts.add(template.type.name.lowercase())
    appendTypeParts(parts, instance, template)
    if (instance.quality != 5) {
        parts.add("quality ${instance.quality}/10")
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
