package com.jcraw.mud.reasoning.combat

import com.jcraw.mud.core.*
import com.jcraw.mud.reasoning.skills.SkillModifierCalculator
import kotlin.random.Random

/**
 * Calculates damage for attacks using configurable formulas
 *
 * Formula: (baseDamage + skillMod + itemBonus) * qualityMult - resistReduction - armorDefense ± variance
 * - Base damage: Weapon/ability base value (e.g., sword = 10, fireball = 15)
 * - Skill mod: Primary skill level contributing to damage
 * - Item bonus: Weapon damage property from equipped weapon
 * - Quality mult: Quality 1-10 scales damage (0.8x to 2.0x)
 * - Resist reduction: Target's resistance skill reduces damage (resistSkillLevel / 2%)
 * - Armor defense: Target's armor defense property reduces damage
 * - Variance: ±20% random variation
 *
 * Design principles:
 * - Deterministic when seeded: Accepts Random instance for testing
 * - Minimum 1 damage: Never reduce below 1
 * - Configurable: Formulas can be tuned via parameters
 */
class DamageCalculator(
    private val skillModifierCalculator: SkillModifierCalculator = SkillModifierCalculator(),
    private val random: Random = Random.Default,
    private val damageVariance: Double = 0.2 // ±20%
) {

    companion object {
        /**
         * Base damage values for common weapon types
         * Used when weapon not specified or for fallback
         */
        private val WEAPON_BASE_DAMAGE = mapOf(
            "sword" to 10,
            "axe" to 12,
            "dagger" to 6,
            "bow" to 8,
            "staff" to 7,
            "fist" to 4, // Unarmed
            "spell" to 15 // Generic spell
        )

        /**
         * Damage type for common weapon types
         */
        private val WEAPON_DAMAGE_TYPE = mapOf(
            "sword" to DamageType.PHYSICAL,
            "axe" to DamageType.PHYSICAL,
            "dagger" to DamageType.PHYSICAL,
            "bow" to DamageType.PHYSICAL,
            "staff" to DamageType.PHYSICAL,
            "fist" to DamageType.PHYSICAL,
            "fire_spell" to DamageType.FIRE,
            "ice_spell" to DamageType.COLD,
            "lightning_spell" to DamageType.LIGHTNING,
            "poison_spell" to DamageType.POISON,
            "spell" to DamageType.MAGIC
        )

        /**
         * Resistance skill names for each damage type
         */
        private val DAMAGE_TYPE_RESISTANCE = mapOf(
            DamageType.PHYSICAL to "Physical Resistance",
            DamageType.FIRE to "Fire Resistance",
            DamageType.COLD to "Cold Resistance",
            DamageType.POISON to "Poison Resistance",
            DamageType.LIGHTNING to "Lightning Resistance",
            DamageType.MAGIC to "Magic Resistance"
        )
    }

    /**
     * Calculate damage for an attack
     *
     * @param context All information about the attack
     * @param worldState Current world state (for equipment lookups)
     * @param attackerEquipped Attacker's equipped items (for weapon damage)
     * @param defenderEquipped Defender's equipped items (for armor defense)
     * @param templates Map of item templates for property lookup
     * @return DamageResult with breakdown and final damage
     */
    fun calculateDamage(
        context: DamageContext,
        worldState: WorldState,
        attackerEquipped: Map<EquipSlot, ItemInstance> = emptyMap(),
        defenderEquipped: Map<EquipSlot, ItemInstance> = emptyMap(),
        templates: Map<String, ItemTemplate> = emptyMap()
    ): DamageResult {
        // 1. Determine base damage from weapon/action
        val (baseDamage, damageType) = getBaseDamageAndType(context.action)

        // 2. Calculate skill modifier (highest weighted skill contributes)
        val skillModifier = context.skillWeights.maxOfOrNull { weight ->
            (context.attackerSkills.getEffectiveLevel(weight.skill) * weight.weight).toInt()
        } ?: 0

        // 3. Get weapon damage from equipped weapon
        val weaponDamage = skillModifierCalculator.getWeaponDamage(attackerEquipped, templates)

        // 4. Get weapon quality multiplier
        val weaponInstance = attackerEquipped[EquipSlot.HANDS_BOTH]
            ?: attackerEquipped[EquipSlot.HANDS_MAIN]
        val qualityMultiplier = skillModifierCalculator.getQualityMultiplier(weaponInstance)

        // 5. Calculate base + item bonus before multiplier
        val baseWithWeapon = baseDamage + weaponDamage
        val itemBonus = (baseWithWeapon * (qualityMultiplier - 1.0)).toInt()

        // 6. Calculate resistance reduction
        val resistanceReduction = calculateResistanceReduction(
            damageType,
            context.defenderSkills
        )

        // 7. Get armor defense from equipped armor
        val armorDefense = skillModifierCalculator.getTotalArmorDefense(defenderEquipped, templates)

        // 8. Apply variance (±20% by default)
        val varianceAmount = applyVariance(baseWithWeapon + skillModifier + itemBonus)

        // 9. Calculate final damage: base + skill + item + variance - resistance - armor
        val rawDamage = baseWithWeapon + skillModifier + itemBonus + varianceAmount - resistanceReduction - armorDefense
        val finalDamage = rawDamage.coerceAtLeast(1) // Minimum 1 damage

        return DamageResult(
            baseDamage = baseDamage,
            skillModifier = skillModifier,
            itemBonus = weaponDamage + itemBonus,
            resistanceReduction = resistanceReduction,
            armorDefense = armorDefense,
            variance = varianceAmount,
            finalDamage = finalDamage,
            damageType = damageType
        )
    }

    /**
     * Determine base damage and damage type from action description
     * Uses keyword matching to identify weapon/spell type
     */
    private fun getBaseDamageAndType(action: String): Pair<Int, DamageType> {
        val actionLower = action.lowercase()

        // Check for specific weapon types
        for ((weaponType, baseDamage) in WEAPON_BASE_DAMAGE) {
            if (actionLower.contains(weaponType)) {
                val damageType = WEAPON_DAMAGE_TYPE[weaponType] ?: DamageType.PHYSICAL
                return baseDamage to damageType
            }
        }

        // Check for magic types
        when {
            actionLower.contains("fire") -> return 15 to DamageType.FIRE
            actionLower.contains("ice") || actionLower.contains("frost") || actionLower.contains("cold") ->
                return 14 to DamageType.COLD
            actionLower.contains("lightning") || actionLower.contains("shock") ->
                return 16 to DamageType.LIGHTNING
            actionLower.contains("poison") || actionLower.contains("venom") ->
                return 12 to DamageType.POISON
            actionLower.contains("magic") || actionLower.contains("spell") ->
                return 15 to DamageType.MAGIC
        }

        // Default: unarmed attack
        return WEAPON_BASE_DAMAGE["fist"]!! to DamageType.PHYSICAL
    }

    /**
     * Calculate damage reduction from resistance skills
     * Formula: resistSkillLevel / 2%
     * Example: Fire Resistance 20 → 10% reduction
     */
    private fun calculateResistanceReduction(
        damageType: DamageType,
        defenderSkills: SkillComponent?
    ): Int {
        if (defenderSkills == null) return 0

        val resistanceSkill = DAMAGE_TYPE_RESISTANCE[damageType] ?: return 0
        val resistanceLevel = defenderSkills.getEffectiveLevel(resistanceSkill)

        // Resistance reduces damage by level/2 percent
        // We'll apply this as a flat reduction for simplicity
        // More accurate: calculate as percentage of total damage, but that requires
        // knowing total damage first (circular dependency)
        // For V1, flat reduction is acceptable
        return resistanceLevel / 2
    }

    /**
     * Apply variance to damage (±20% by default)
     * Returns the variance amount to add/subtract
     */
    private fun applyVariance(baseDamage: Int): Int {
        if (damageVariance == 0.0) return 0

        val maxVariance = (baseDamage * damageVariance).toInt()
        // Random between -maxVariance and +maxVariance
        return random.nextInt(-maxVariance, maxVariance + 1)
    }
}
