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
import com.jcraw.mud.core.ComponentType
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.SkillComponent
import com.jcraw.mud.core.WorldState
import com.jcraw.mud.reasoning.skill.SkillManager

/**
 * Entity/component load + validation for [AttackResolveApply] (MUD-034k).
 */
internal object AttackResolveSetup {

    data class Loaded(
        val attacker: Entity,
        val defender: Entity,
        val attackerSkills: SkillComponent,
        val defenderSkills: SkillComponent?,
        val defenderCombat: CombatComponent
    )

    sealed class Outcome {
        data class Ok(val loaded: Loaded) : Outcome()
        data class Fail(val result: AttackResult) : Outcome()
    }

    fun load(
        attackerId: String,
        defenderId: String,
        worldState: WorldState,
        skillManager: SkillManager
    ): Outcome {
        val attacker = CombatEntityLookup.findEntity(worldState, attackerId)
        val defender = CombatEntityLookup.findEntity(worldState, defenderId)
        if (attacker == null || defender == null) {
            return Outcome.Fail(AttackResult.failure("Invalid attacker or defender"))
        }
        return loadComponents(attacker, defender, worldState, skillManager)
    }

    private fun loadComponents(
        attacker: Entity,
        defender: Entity,
        worldState: WorldState,
        skillManager: SkillManager
    ): Outcome {
        val attackerSkills = skillOf(attacker, worldState, skillManager)
        val defenderSkills = skillOf(defender, worldState, skillManager)
        val defenderCombat = CombatEntityLookup.getComponent<CombatComponent>(
            defender, ComponentType.COMBAT, worldState, skillManager
        )
        AttackResolveDebug.logComponents(
            attacker, defender, attackerSkills, defenderSkills, defenderCombat
        )
        if (attackerSkills == null || defenderCombat == null) {
            return Outcome.Fail(AttackResult.failure("Missing required components"))
        }
        return Outcome.Ok(
            Loaded(attacker, defender, attackerSkills, defenderSkills, defenderCombat)
        )
    }

    private fun skillOf(
        entity: Entity,
        worldState: WorldState,
        skillManager: SkillManager
    ): SkillComponent? = CombatEntityLookup.getComponent(
        entity, ComponentType.SKILL, worldState, skillManager
    )
}
