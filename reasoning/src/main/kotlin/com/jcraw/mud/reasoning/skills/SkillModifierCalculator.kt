package com.jcraw.mud.reasoning.skills

import com.jcraw.mud.core.*

/**
 * Calculates skill modifiers from equipped items
 *
 * Equipped items can provide bonuses to specific skills via properties:
 * - "sword_fighting_bonus" = "2" → +2 to Sword Fighting skill
 * - "archery_bonus" = "3" → +3 to Archery skill
 * - "perception_bonus" = "1" → +1 to Perception skill
 * - etc.
 *
 * These bonuses are added to skill rolls during combat, skill checks, and other actions
 *
 * Design principles:
 * - Explicit dependencies: Takes all inputs as parameters
 * - Flexible property naming: Uses pattern matching for skill bonuses
 * - Additive bonuses: Multiple items with same bonus stack
 */
class SkillModifierCalculator {

    companion object {
        /**
         * Property suffix for skill bonuses
         * e.g., "sword_fighting_bonus" → bonus for "Sword Fighting" skill
         */
        const val BONUS_SUFFIX = "_bonus"
    }

    /**
     * Calculate total skill bonus from all equipped items
     *
     * @param skillName Name of skill to check (e.g., "Sword Fighting")
     * @param equipped Map of equipped items by slot
     * @param templates Map of template ID to ItemTemplate for property lookup
     * @return Total bonus to skill (sum of all equipped item bonuses)
     */
    fun calculateSkillBonus(
        skillName: String,
        equipped: Map<EquipSlot, ItemInstance>,
        templates: Map<String, ItemTemplate>
    ): Int {
        // Convert skill name to property key format
        // "Sword Fighting" → "sword_fighting_bonus"
        val propertyKey = skillNameToPropertyKey(skillName)

        // Sum all bonuses from equipped items
        return equipped.values.sumOf { instance ->
            val template = templates[instance.templateId] ?: return@sumOf 0
            template.getPropertyInt(propertyKey, 0)
        }
    }

    /**
     * Calculate all skill bonuses from equipped items
     * Returns a map of skill name → total bonus
     *
     * @param equipped Map of equipped items by slot
     * @param templates Map of template ID to ItemTemplate for property lookup
     * @return Map of skill name to bonus amount (only skills with bonuses > 0)
     */
    fun calculateAllSkillBonuses(
        equipped: Map<EquipSlot, ItemInstance>,
        templates: Map<String, ItemTemplate>
    ): Map<String, Int> {
        val bonuses = mutableMapOf<String, Int>()

        // Scan all equipped items for skill bonus properties
        equipped.values.forEach { instance ->
            val template = templates[instance.templateId] ?: return@forEach

            // Find all properties ending with "_bonus"
            template.properties.forEach { (key, value) ->
                if (key.endsWith(BONUS_SUFFIX)) {
                    val bonus = value.toIntOrNull() ?: 0
                    if (bonus > 0) {
                        // Convert property key back to skill name
                        // "sword_fighting_bonus" → "Sword Fighting"
                        val skillName = propertyKeyToSkillName(key)
                        bonuses[skillName] = (bonuses[skillName] ?: 0) + bonus
                    }
                }
            }
        }

        return bonuses
    }

    /**
     * Get weapon damage bonus from equipped weapon
     *
     * @param equipped Map of equipped items by slot
     * @param templates Map of template ID to ItemTemplate for property lookup
     * @return Weapon damage value, or 0 if no weapon equipped
     */
    fun getWeaponDamage(
        equipped: Map<EquipSlot, ItemInstance>,
        templates: Map<String, ItemTemplate>
    ): Int {
        // Check main hand, two-handed, then off-hand
        val weaponInstance = equipped[EquipSlot.HANDS_BOTH]
            ?: equipped[EquipSlot.HANDS_MAIN]
            ?: equipped[EquipSlot.HANDS_OFF]
            ?: return 0

        val template = templates[weaponInstance.templateId] ?: return 0
        return template.getPropertyInt("damage", 0)
    }

    /**
     * Get weapon damage multiplier from item quality
     * Quality 1-10 scales damage: quality 1 = 0.8x, quality 10 = 2.0x
     *
     * @param weaponInstance ItemInstance of equipped weapon
     * @return Damage multiplier based on quality
     */
    fun getQualityMultiplier(weaponInstance: ItemInstance?): Double {
        if (weaponInstance == null) return 1.0
        // Quality 1-10 → multiplier 0.8-2.0 (linear scaling)
        // Formula: 0.8 + (quality - 1) * 0.133
        val quality = weaponInstance.quality.coerceIn(1, 10)
        return 0.8 + (quality - 1) * 0.133
    }

    /**
     * Get total armor defense from all equipped armor
     *
     * @param equipped Map of equipped items by slot
     * @param templates Map of template ID to ItemTemplate for property lookup
     * @return Total defense value from all armor
     */
    fun getTotalArmorDefense(
        equipped: Map<EquipSlot, ItemInstance>,
        templates: Map<String, ItemTemplate>
    ): Int {
        // Sum defense from all armor pieces
        return equipped.values.sumOf { instance ->
            val template = templates[instance.templateId] ?: return@sumOf 0
            template.getPropertyInt("defense", 0)
        }
    }

    /**
     * Convert skill name to property key format
     * "Sword Fighting" → "sword_fighting_bonus"
     */
    private fun skillNameToPropertyKey(skillName: String): String {
        return skillName.lowercase().replace(" ", "_") + BONUS_SUFFIX
    }

    /**
     * Convert property key to skill name format
     * "sword_fighting_bonus" → "Sword Fighting"
     */
    private fun propertyKeyToSkillName(propertyKey: String): String {
        val baseName = propertyKey.removeSuffix(BONUS_SUFFIX)
        return baseName.split("_").joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercaseChar() }
        }
    }
}
