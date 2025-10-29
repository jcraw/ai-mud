package com.jcraw.mud.reasoning.skills

import com.jcraw.mud.core.*

/**
 * Calculates inventory carrying capacity based on Strength skill and augments
 *
 * Formula: Base = Strength level * 5kg, augments = bags + perks + spells
 * - Base capacity scales with Strength skill progression
 * - Bags/containers add flat bonuses (e.g., Leather Bag +10kg)
 * - Perks can add percentage bonuses (e.g., "Pack Mule" +20%)
 * - Spells/buffs can provide temporary bonuses
 *
 * Design principles:
 * - Explicit dependencies: Takes all inputs as parameters
 * - Deterministic: Same inputs always produce same output
 * - Testable: Pure calculation with no side effects
 */
class CapacityCalculator {

    companion object {
        /**
         * Base capacity multiplier per Strength level
         * Example: Strength 10 → 50kg, Strength 20 → 100kg
         */
        const val STRENGTH_MULTIPLIER = 5.0

        /**
         * Minimum capacity (even with Strength 0)
         */
        const val MINIMUM_CAPACITY = 10.0
    }

    /**
     * Calculate total carrying capacity
     *
     * @param strengthLevel Current Strength skill level
     * @param bagBonuses List of flat bonuses from bags/containers (in kg)
     * @param perkMultipliers List of perk multipliers (e.g., 0.2 for +20%)
     * @return Total capacity in kg
     */
    fun calculateCapacity(
        strengthLevel: Int,
        bagBonuses: List<Double> = emptyList(),
        perkMultipliers: List<Double> = emptyList()
    ): Double {
        // 1. Calculate base capacity from Strength
        val baseCapacity = strengthLevel * STRENGTH_MULTIPLIER

        // 2. Sum all bag bonuses
        val totalBagBonus = bagBonuses.sum()

        // 3. Calculate perk multiplier (additive)
        val perkMultiplier = perkMultipliers.sum()

        // 4. Apply perks as percentage of base
        val perkBonus = baseCapacity * perkMultiplier

        // 5. Total = base + bags + perk bonus
        val totalCapacity = baseCapacity + totalBagBonus + perkBonus

        // 6. Enforce minimum capacity
        return totalCapacity.coerceAtLeast(MINIMUM_CAPACITY)
    }

    /**
     * Calculate capacity from equipped items with container property
     *
     * @param equipped Map of equipped items
     * @param templates Map of template ID to ItemTemplate for property lookup
     * @return Total bag bonuses in kg
     */
    fun calculateBagBonuses(
        equipped: Map<EquipSlot, ItemInstance>,
        templates: Map<String, ItemTemplate>
    ): List<Double> {
        return equipped.values.mapNotNull { instance ->
            val template = templates[instance.templateId] ?: return@mapNotNull null
            val capacityBonus = template.getPropertyDouble("capacity", 0.0)
            if (capacityBonus > 0.0) capacityBonus else null
        }
    }

    /**
     * Calculate perk multipliers from active perks
     * This method should be called with perks that affect capacity
     *
     * @param activePerks List of active perk names
     * @return List of multipliers (e.g., [0.2] for "Pack Mule" +20%)
     */
    fun calculatePerkMultipliers(activePerks: List<String>): List<Double> {
        return activePerks.mapNotNull { perkName ->
            when (perkName) {
                "Pack Mule" -> 0.2 // +20%
                "Strong Back" -> 0.15 // +15%
                "Hoarder" -> 0.25 // +25%
                else -> null
            }
        }
    }

    /**
     * Convenience method to calculate full capacity from components
     *
     * @param skillComponent SkillComponent with Strength skill
     * @param inventoryComponent InventoryComponent with equipped items
     * @param templates Map of templates for property lookup
     * @param activePerks List of active perk names affecting capacity
     * @return Total capacity in kg
     */
    fun calculateFullCapacity(
        skillComponent: SkillComponent,
        inventoryComponent: InventoryComponent,
        templates: Map<String, ItemTemplate>,
        activePerks: List<String> = emptyList()
    ): Double {
        val strengthLevel = skillComponent.getEffectiveLevel("Strength")
        val bagBonuses = calculateBagBonuses(inventoryComponent.equipped, templates)
        val perkMultipliers = calculatePerkMultipliers(activePerks)

        return calculateCapacity(strengthLevel, bagBonuses, perkMultipliers)
    }
}
