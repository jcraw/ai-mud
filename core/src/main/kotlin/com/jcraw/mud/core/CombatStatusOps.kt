@file:Suppress(
    "ReturnCount",
    "MagicNumber",
    "MaxLineLength",
    "TooManyFunctions",
    "LongMethod",
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "LongParameterList",
    "TooGenericExceptionCaught",
    "SwallowedException",
    "ThrowsCount",
    "UnusedParameter"
)

package com.jcraw.mud.core

/**
 * Status apply/remove/query for [CombatComponent] members (MUD-034m).
 */
internal object CombatStatusOps {

    fun apply(component: CombatComponent, effect: StatusEffect): CombatComponent {
        val existingEffects = component.statusEffects.toMutableList()

        // Check for existing effect of same type
        val existingIndex = existingEffects.indexOfFirst { it.type == effect.type }

        val newEffects = when (effect.type) {
            StatusEffectType.POISON_DOT -> applyDot(existingEffects, existingIndex, effect)

            StatusEffectType.STRENGTH_BOOST,
            StatusEffectType.REGENERATION,
            StatusEffectType.SHIELD -> applyBuff(existingEffects, effect)

            StatusEffectType.SLOW,
            StatusEffectType.HIDDEN,
            StatusEffectType.DEFENSIVE_STANCE,
            StatusEffectType.WARINESS -> applySingle(existingEffects, existingIndex, effect)
        }

        return component.copy(statusEffects = newEffects)
    }

    fun remove(component: CombatComponent, type: StatusEffectType): CombatComponent {
        return component.copy(statusEffects = component.statusEffects.filterNot { it.type == type })
    }

    fun has(component: CombatComponent, type: StatusEffectType): Boolean {
        return component.statusEffects.any { it.type == type }
    }

    fun magnitude(component: CombatComponent, type: StatusEffectType): Int {
        return component.statusEffects.filter { it.type == type }.sumOf { it.magnitude }
    }

    private fun applyDot(
        existingEffects: MutableList<StatusEffect>,
        existingIndex: Int,
        effect: StatusEffect
    ): List<StatusEffect> {
        // DOT: Replace if same type and new magnitude is higher
        if (existingIndex >= 0) {
            val existing = existingEffects[existingIndex]
            if (effect.magnitude > existing.magnitude) {
                existingEffects[existingIndex] = effect
            }
            return existingEffects
        }
        return existingEffects + effect
    }

    private fun applyBuff(
        existingEffects: MutableList<StatusEffect>,
        effect: StatusEffect
    ): List<StatusEffect> {
        // Buffs: Stack up to 3 of same type
        val sameTypeCount = existingEffects.count { it.type == effect.type }
        return if (sameTypeCount < 3) {
            existingEffects + effect
        } else {
            existingEffects // Already at cap, don't add
        }
    }

    private fun applySingle(
        existingEffects: MutableList<StatusEffect>,
        existingIndex: Int,
        effect: StatusEffect
    ): List<StatusEffect> {
        // Single-instance effects: Replace if exists
        if (existingIndex >= 0) {
            existingEffects[existingIndex] = effect
            return existingEffects
        }
        return existingEffects + effect
    }
}
