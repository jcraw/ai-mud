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
 * Per-tick effect processing for [CombatComponent] members (MUD-034m).
 */
internal object CombatTickOps {

    fun tick(component: CombatComponent, gameTime: Long): Pair<CombatComponent, List<EffectApplication>> {
        val applications = mutableListOf<EffectApplication>()
        var updatedComponent = component

        // Process each effect
        val updatedEffects = component.statusEffects.mapNotNull { effect ->
            updatedComponent = applyTick(updatedComponent, effect, applications)
            // Tick the effect (decrement duration)
            effect.tick()
        }

        // Update with new effects list (expired effects removed by tick() returning null)
        updatedComponent = updatedComponent.copy(statusEffects = updatedEffects)

        return updatedComponent to applications
    }

    private fun applyTick(
        component: CombatComponent,
        effect: StatusEffect,
        applications: MutableList<EffectApplication>
    ): CombatComponent {
        return when (effect.type) {
            StatusEffectType.POISON_DOT -> {
                // Apply damage
                applications.add(EffectApplication(effect.type, effect.magnitude, EffectResult.DAMAGE))
                component.applyDamage(effect.magnitude, DamageType.POISON)
            }
            StatusEffectType.REGENERATION -> {
                // Apply healing
                applications.add(EffectApplication(effect.type, effect.magnitude, EffectResult.HEALING))
                component.heal(effect.magnitude)
            }
            else -> {
                // Other effects don't apply per-tick damage/healing
                applications.add(EffectApplication(effect.type, effect.magnitude, EffectResult.ACTIVE))
                component
            }
        }
    }
}
