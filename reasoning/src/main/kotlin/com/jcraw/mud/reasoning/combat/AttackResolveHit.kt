@file:Suppress(
    "LongParameterList",
    "MagicNumber",
    "MaxLineLength",
    "ReturnCount",
    "LongMethod",
    "CyclomaticComplexMethod",
    "ComplexCondition",
    "NestedBlockDepth",
    "TooManyFunctions"
)

package com.jcraw.mud.reasoning.combat

import com.jcraw.mud.core.CombatComponent
import com.jcraw.mud.core.EquipSlot
import com.jcraw.mud.core.ItemInstance
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.SkillComponent
import com.jcraw.mud.core.StatusEffect
import com.jcraw.mud.core.WorldState

/** Params for hit damage apply (MUD-034k). */
internal data class AttackHitParams(
    val attackerId: String,
    val defenderId: String,
    val action: String,
    val worldState: WorldState,
    val attackerSkills: SkillComponent,
    val defenderSkills: SkillComponent?,
    val defenderCombat: CombatComponent,
    val rolls: AttackResolveRolls.RollOutcome,
    val damageCalculator: DamageCalculator,
    val attackerEquipped: Map<EquipSlot, ItemInstance>,
    val defenderEquipped: Map<EquipSlot, ItemInstance>,
    val templates: Map<String, ItemTemplate>
)

/**
 * Damage calculation + hit result for [AttackResolveApply] (MUD-034k).
 */
internal object AttackResolveHit {

    suspend fun apply(p: AttackHitParams): AttackResult {
        val damageResult = calcDamage(p)
        val updated = p.defenderCombat.applyDamage(
            damageResult.finalDamage,
            damageResult.damageType
        )
        // V1: No status effects on basic attacks, will be added in later phases
        return toHitResult(p, damageResult, updated, emptyList())
    }

    private suspend fun calcDamage(p: AttackHitParams) =
        p.damageCalculator.calculateDamage(
            DamageContext(
                attackerId = p.attackerId,
                defenderId = p.defenderId,
                action = p.action,
                skillWeights = p.rolls.skillWeights,
                attackerSkills = p.attackerSkills,
                defenderSkills = p.defenderSkills,
                attackRoll = p.rolls.attackRoll,
                defenseRoll = p.rolls.defenseRoll
            ),
            p.worldState,
            p.attackerEquipped,
            p.defenderEquipped,
            p.templates
        )

    private fun toHitResult(
        p: AttackHitParams,
        damageResult: DamageResult,
        updated: CombatComponent,
        statusEffects: List<StatusEffect>
    ): AttackResult = AttackResult.hit(
        attackerId = p.attackerId,
        defenderId = p.defenderId,
        damage = damageResult.finalDamage,
        damageType = damageResult.damageType,
        attackRoll = p.rolls.attackRoll,
        defenseRoll = p.rolls.defenseRoll,
        attackerSkillsUsed = p.rolls.skillWeights.map { it.skill },
        defenderSkillsUsed = p.rolls.defenderSkillsUsed,
        defenseOutcome = p.rolls.defenseOutcome,
        updatedDefenderCombat = updated,
        statusEffects = statusEffects,
        wasKilled = updated.isDead()
    )
}
