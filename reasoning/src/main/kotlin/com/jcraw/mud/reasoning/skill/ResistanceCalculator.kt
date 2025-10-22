package com.jcraw.mud.reasoning.skill

import com.jcraw.mud.core.repository.SkillComponentRepository

/**
 * Damage reduction result
 */
data class DamageReductionResult(
    val originalDamage: Int,
    val reducedDamage: Int,
    val reductionAmount: Int,
    val reductionPercentage: Float,
    val resistanceLevel: Int,
    val damageType: String
) {
    val narrative: String
        get() = if (reductionAmount > 0) {
            "Your $damageType Resistance (L$resistanceLevel) reduces damage by $reductionAmount (${"%.1f".format(reductionPercentage * 100)}%)"
        } else {
            "No resistance to $damageType damage"
        }
}

/**
 * Calculates damage reduction from resistance skills
 *
 * Reduction formula: damage * (resistanceLevel / 2) / 100
 * Example: Fire Resistance L20 = 10% damage reduction from fire
 * Example: Fire Resistance L50 = 25% damage reduction from fire
 * Example: Fire Resistance L100 = 50% damage reduction from fire (max practical resistance)
 */
class ResistanceCalculator(
    private val componentRepo: SkillComponentRepository
) {

    /**
     * Calculate damage reduction for a specific damage type
     * - Looks for "[DamageType] Resistance" skill (e.g., "Fire Resistance")
     * - Reduction % = resistanceLevel / 2
     * - Returns reduced damage and details
     */
    fun calculateReduction(
        entityId: String,
        damageType: String,
        damage: Int
    ): Result<DamageReductionResult> {
        return runCatching {
            require(damage >= 0) { "Damage must be non-negative" }

            // Get component
            val component = componentRepo.load(entityId).getOrNull()

            // Get resistance skill (e.g., "Fire Resistance")
            val resistanceSkillName = "$damageType Resistance"
            val resistanceLevel = component?.getEffectiveLevel(resistanceSkillName) ?: 0

            if (resistanceLevel <= 0) {
                // No resistance
                return Result.success(
                    DamageReductionResult(
                        originalDamage = damage,
                        reducedDamage = damage,
                        reductionAmount = 0,
                        reductionPercentage = 0f,
                        resistanceLevel = 0,
                        damageType = damageType
                    )
                )
            }

            // Calculate reduction
            // Formula: reductionPercentage = resistanceLevel / 2 / 100
            // Example: L20 = 10%, L50 = 25%, L100 = 50%
            val reductionPercentage = (resistanceLevel.toFloat() / 2f) / 100f
            val reductionAmount = (damage * reductionPercentage).toInt()
            val reducedDamage = (damage - reductionAmount).coerceAtLeast(0)

            DamageReductionResult(
                originalDamage = damage,
                reducedDamage = reducedDamage,
                reductionAmount = reductionAmount,
                reductionPercentage = reductionPercentage,
                resistanceLevel = resistanceLevel,
                damageType = damageType
            )
        }
    }

    /**
     * Calculate combined resistance from multiple damage types
     * Useful for attacks that deal multiple damage types (e.g., "Fire and Poison")
     *
     * Each damage type is reduced independently, then summed
     */
    fun calculateMultiTypeReduction(
        entityId: String,
        damageByType: Map<String, Int>
    ): Result<List<DamageReductionResult>> {
        return runCatching {
            damageByType.map { (damageType, damage) ->
                calculateReduction(entityId, damageType, damage).getOrThrow()
            }
        }
    }

    /**
     * Get total reduced damage from multi-type attack
     */
    fun getTotalReducedDamage(
        entityId: String,
        damageByType: Map<String, Int>
    ): Result<Int> {
        return runCatching {
            val reductions = calculateMultiTypeReduction(entityId, damageByType).getOrThrow()
            reductions.sumOf { it.reducedDamage }
        }
    }

    /**
     * Get all resistance levels for an entity
     * Returns map of damage type to resistance level
     */
    fun getAllResistances(entityId: String): Result<Map<String, Int>> {
        return runCatching {
            val component = componentRepo.load(entityId).getOrNull()

            // Find all skills with "Resistance" in the name
            component?.skills
                ?.filterKeys { it.endsWith(" Resistance") }
                ?.filterValues { it.unlocked }
                ?.mapKeys { (skillName, _) ->
                    // Extract damage type (e.g., "Fire Resistance" → "Fire")
                    skillName.removeSuffix(" Resistance")
                }
                ?.mapValues { (_, skill) -> skill.getEffectiveLevel() }
                ?: emptyMap()
        }
    }
}
