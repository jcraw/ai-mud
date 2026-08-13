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

package com.jcraw.mud.reasoning.pickpocket

import com.jcraw.mud.core.CombatComponent
import com.jcraw.mud.core.ComponentType
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.SkillCheckResult
import com.jcraw.mud.core.SocialComponent
import com.jcraw.mud.core.StatusEffect
import com.jcraw.mud.core.StatusEffectType

/**
 * Caught pickpocket: disposition Δ −20..−50 + wariness +20/10 turns (MUD-034n).
 */
internal object PickpocketCaught {

    /**
     * Handle consequences of being caught pickpocketing
     */
    fun handle(
        targetNpc: Entity.NPC,
        skillCheckResult: SkillCheckResult,
        targetSocial: SocialComponent?
    ): PickpocketHandler.PickpocketResult.Caught {
        val dispositionDelta = dispositionPenalty(skillCheckResult.margin)
        val updatedSocial = applySocialPenalty(targetSocial, dispositionDelta)
        val updatedCombat = applyWariness(targetNpc)
        return caughtResult(
            targetNpc.withComponent(updatedSocial).withComponent(updatedCombat),
            updatedSocial,
            updatedCombat,
            dispositionDelta,
            skillCheckResult
        )
    }

    private fun applySocialPenalty(targetSocial: SocialComponent?, dispositionDelta: Int): SocialComponent {
        return (targetSocial ?: SocialComponent(personality = "ordinary", traits = emptyList()))
            .applyDispositionChange(dispositionDelta)
    }

    private fun caughtResult(
        updatedNpc: Entity.NPC,
        updatedSocial: SocialComponent,
        updatedCombat: CombatComponent,
        dispositionDelta: Int,
        skillCheckResult: SkillCheckResult
    ) = PickpocketHandler.PickpocketResult.Caught(
        targetNpc = updatedNpc,
        targetSocial = updatedSocial,
        targetCombat = updatedCombat,
        dispositionDelta = dispositionDelta,
        roll = skillCheckResult.roll,
        total = skillCheckResult.total,
        dc = skillCheckResult.dc,
        margin = skillCheckResult.margin
    )

    private fun dispositionPenalty(margin: Int): Int {
        // Calculate disposition penalty based on how badly they failed
        // Margin is negative, so we negate it and scale: -20 to -50
        return (-20 - (kotlin.math.abs(margin) * 3)).coerceAtMost(-20).coerceAtLeast(-50)
    }

    private fun applyWariness(targetNpc: Entity.NPC): CombatComponent {
        // Apply wariness status effect (+20 Perception for 10 turns)
        val targetCombat = targetNpc.getComponent<CombatComponent>(ComponentType.COMBAT)
        val warinessEffect = StatusEffect(
            type = StatusEffectType.WARINESS,
            magnitude = 20,
            duration = 10,
            source = "pickpocket_failure"
        )

        return if (targetCombat != null) {
            // Add wariness to existing status effects
            val newEffects = targetCombat.statusEffects + warinessEffect
            targetCombat.copy(statusEffects = newEffects)
        } else {
            // Create new combat component with wariness
            CombatComponent(
                maxHp = targetNpc.maxHealth,
                currentHp = targetNpc.health,
                statusEffects = listOf(warinessEffect)
            )
        }
    }
}
