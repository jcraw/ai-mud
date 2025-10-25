package com.jcraw.mud.core

import kotlinx.serialization.Serializable

/**
 * Combat component for entities participating in combat
 * Tracks health, action timing, status effects, and combat state
 *
 * Design principles:
 * - Immutable: All methods return new instances
 * - Self-contained: HP, timers, and effects are encapsulated
 * - Compatible with async turn-based system via actionTimerEnd
 */
@Serializable
data class CombatComponent(
    val currentHp: Int,
    val maxHp: Int,
    val actionTimerEnd: Long = 0L, // Game time tick when this entity can act next
    val statusEffects: List<StatusEffect> = emptyList(),
    val position: CombatPosition = CombatPosition.FRONT, // For future positioning system
    override val componentType: ComponentType = ComponentType.COMBAT
) : Component {

    companion object {
        /**
         * Calculate maximum HP based on skills and equipment
         *
         * Formula: 10 + (Vitality*5) + (Endurance*3) + (Constitution*2) + itemBonuses
         *
         * @param skills Entity's skill component (optional, defaults to base HP if null)
         * @param itemHpBonus Bonus HP from equipped items (default 0)
         * @return Calculated maximum HP (minimum 10)
         */
        fun calculateMaxHp(
            skills: SkillComponent? = null,
            itemHpBonus: Int = 0
        ): Int {
            val vitalityLevel = skills?.getEffectiveLevel("Vitality") ?: 0
            val enduranceLevel = skills?.getEffectiveLevel("Endurance") ?: 0
            val constitutionLevel = skills?.getEffectiveLevel("Constitution") ?: 0

            val baseHp = 10
            val skillHp = (vitalityLevel * 5) + (enduranceLevel * 3) + (constitutionLevel * 2)

            return (baseHp + skillHp + itemHpBonus).coerceAtLeast(10)
        }

        /**
         * Create new combat component with HP calculated from skills
         *
         * @param skills Entity's skill component
         * @param itemHpBonus Bonus HP from equipped items
         * @return New CombatComponent with calculated max HP
         */
        fun create(
            skills: SkillComponent? = null,
            itemHpBonus: Int = 0
        ): CombatComponent {
            val maxHp = calculateMaxHp(skills, itemHpBonus)
            return CombatComponent(
                currentHp = maxHp,
                maxHp = maxHp
            )
        }
    }

    /**
     * Apply damage to this entity
     *
     * @param amount Raw damage amount (before reductions)
     * @param type Type of damage for resistance calculations
     * @return New CombatComponent with reduced HP
     */
    fun applyDamage(amount: Int, type: DamageType = DamageType.PHYSICAL): CombatComponent {
        val newHp = (currentHp - amount).coerceAtLeast(0)
        return copy(currentHp = newHp)
    }

    /**
     * Heal this entity
     *
     * @param amount Healing amount
     * @return New CombatComponent with increased HP (capped at maxHp)
     */
    fun heal(amount: Int): CombatComponent {
        val newHp = (currentHp + amount).coerceAtMost(maxHp)
        return copy(currentHp = newHp)
    }

    /**
     * Advance action timer (set when entity can act next)
     *
     * @param cost Action cost in game time ticks
     * @param currentGameTime Current game time
     * @return New CombatComponent with updated timer
     */
    fun advanceTimer(cost: Long, currentGameTime: Long): CombatComponent {
        return copy(actionTimerEnd = currentGameTime + cost)
    }

    /**
     * Check if entity can act at current game time
     *
     * @param currentGameTime Current game time in ticks
     * @return True if entity's action timer has elapsed
     */
    fun canAct(currentGameTime: Long): Boolean {
        return actionTimerEnd <= currentGameTime
    }

    /**
     * Apply status effect to this entity
     * Handles stacking rules:
     * - DOT effects: Replace if same type, keep highest magnitude
     * - Buffs/debuffs: Stack additively up to cap (max 3 of same type)
     *
     * @param effect Status effect to apply
     * @return New CombatComponent with effect applied
     */
    fun applyStatus(effect: StatusEffect): CombatComponent {
        val existingEffects = statusEffects.toMutableList()

        // Check for existing effect of same type
        val existingIndex = existingEffects.indexOfFirst { it.type == effect.type }

        val newEffects = when (effect.type) {
            StatusEffectType.POISON_DOT -> {
                // DOT: Replace if same type and new magnitude is higher
                if (existingIndex >= 0) {
                    val existing = existingEffects[existingIndex]
                    if (effect.magnitude > existing.magnitude) {
                        existingEffects[existingIndex] = effect
                    }
                    existingEffects
                } else {
                    existingEffects + effect
                }
            }

            StatusEffectType.STRENGTH_BOOST,
            StatusEffectType.REGENERATION,
            StatusEffectType.SHIELD -> {
                // Buffs: Stack up to 3 of same type
                val sameTypeCount = existingEffects.count { it.type == effect.type }
                if (sameTypeCount < 3) {
                    existingEffects + effect
                } else {
                    existingEffects // Already at cap, don't add
                }
            }

            StatusEffectType.SLOW,
            StatusEffectType.HIDDEN,
            StatusEffectType.DEFENSIVE_STANCE -> {
                // Single-instance effects: Replace if exists
                if (existingIndex >= 0) {
                    existingEffects[existingIndex] = effect
                    existingEffects
                } else {
                    existingEffects + effect
                }
            }
        }

        return copy(statusEffects = newEffects)
    }

    /**
     * Remove status effect of specific type
     *
     * @param type Type of effect to remove
     * @return New CombatComponent with effect removed
     */
    fun removeStatus(type: StatusEffectType): CombatComponent {
        return copy(statusEffects = statusEffects.filterNot { it.type == type })
    }

    /**
     * Process one tick of all status effects
     * - Decrements duration
     * - Removes expired effects
     * - Applies DOT damage
     * - Applies regeneration healing
     *
     * @param gameTime Current game time (for logging/events)
     * @return Pair of (new CombatComponent, list of effects that were applied this tick)
     */
    fun tickEffects(gameTime: Long): Pair<CombatComponent, List<EffectApplication>> {
        val applications = mutableListOf<EffectApplication>()
        var updatedComponent = this

        // Process each effect
        val updatedEffects = statusEffects.mapNotNull { effect ->
            // Apply effect based on type
            when (effect.type) {
                StatusEffectType.POISON_DOT -> {
                    // Apply damage
                    updatedComponent = updatedComponent.applyDamage(effect.magnitude, DamageType.POISON)
                    applications.add(EffectApplication(effect.type, effect.magnitude, EffectResult.DAMAGE))
                }
                StatusEffectType.REGENERATION -> {
                    // Apply healing
                    updatedComponent = updatedComponent.heal(effect.magnitude)
                    applications.add(EffectApplication(effect.type, effect.magnitude, EffectResult.HEALING))
                }
                else -> {
                    // Other effects don't apply per-tick damage/healing
                    applications.add(EffectApplication(effect.type, effect.magnitude, EffectResult.ACTIVE))
                }
            }

            // Tick the effect (decrement duration)
            effect.tick()
        }

        // Update with new effects list (expired effects removed by tick() returning null)
        updatedComponent = updatedComponent.copy(statusEffects = updatedEffects)

        return updatedComponent to applications
    }

    /**
     * Check if entity has specific status effect active
     *
     * @param type Type of effect to check
     * @return True if effect is present
     */
    fun hasStatusEffect(type: StatusEffectType): Boolean {
        return statusEffects.any { it.type == type }
    }

    /**
     * Get total magnitude of all effects of a specific type
     * Useful for stacking buffs/debuffs
     *
     * @param type Type of effect
     * @return Sum of all magnitudes for that type
     */
    fun getStatusEffectMagnitude(type: StatusEffectType): Int {
        return statusEffects.filter { it.type == type }.sumOf { it.magnitude }
    }

    /**
     * Check if entity is alive
     */
    fun isAlive(): Boolean = currentHp > 0

    /**
     * Check if entity is dead
     */
    fun isDead(): Boolean = currentHp <= 0

    /**
     * Get HP percentage (0-100)
     */
    fun getHpPercentage(): Int {
        return if (maxHp > 0) {
            ((currentHp.toDouble() / maxHp.toDouble()) * 100).toInt()
        } else {
            0
        }
    }

    /**
     * Update max HP (useful when skills change or equipment is added/removed)
     * Scales current HP proportionally to maintain same percentage
     *
     * @param newMaxHp New maximum HP value
     * @return New CombatComponent with updated max HP
     */
    fun updateMaxHp(newMaxHp: Int): CombatComponent {
        val hpPercent = getHpPercentage()
        val newCurrentHp = ((newMaxHp * hpPercent) / 100).coerceAtLeast(1).coerceAtMost(newMaxHp)
        return copy(
            currentHp = newCurrentHp,
            maxHp = newMaxHp
        )
    }
}

/**
 * Combat position for future tactical positioning system
 * V1: Simple front/back, V2 could add flanking, cover, etc.
 */
@Serializable
enum class CombatPosition {
    FRONT,
    BACK
}

/**
 * Result of applying a status effect
 * Used for narration and event logging
 */
data class EffectApplication(
    val type: StatusEffectType,
    val magnitude: Int,
    val result: EffectResult
)

enum class EffectResult {
    DAMAGE,   // Effect dealt damage (DOT)
    HEALING,  // Effect healed HP (regeneration)
    ACTIVE    // Effect is active but didn't apply damage/healing this tick
}
