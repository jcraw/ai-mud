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
import com.jcraw.mud.core.DamageType
import com.jcraw.mud.core.StatusEffect

/**
 * Result of an attack resolution
 * Contains all information about what happened
 */
sealed class AttackResult {
    abstract val attackerId: String
    abstract val defenderId: String
    abstract val attackerSkillsUsed: List<String>
    abstract val defenderSkillsUsed: List<String>

    /**
     * Attack hit successfully
     */
    data class Hit(
        override val attackerId: String,
        override val defenderId: String,
        val damage: Int,
        val damageType: DamageType,
        val attackRoll: Int,
        val defenseRoll: Int,
        override val attackerSkillsUsed: List<String>,
        override val defenderSkillsUsed: List<String>,
        val defenseOutcome: DefenseOutcome,
        val updatedDefenderCombat: CombatComponent,
        val statusEffects: List<StatusEffect>,
        val wasKilled: Boolean
    ) : AttackResult() {
        val isSuccess = true
    }

    /**
     * Attack missed or was dodged
     */
    data class Miss(
        override val attackerId: String,
        override val defenderId: String,
        val attackRoll: Int,
        val defenseRoll: Int,
        override val attackerSkillsUsed: List<String>,
        override val defenderSkillsUsed: List<String>,
        val defenseOutcome: DefenseOutcome,
        val wasDodged: Boolean
    ) : AttackResult() {
        val isSuccess = false
    }

    /**
     * Attack failed due to error
     */
    data class Failure(
        val reason: String
    ) : AttackResult() {
        override val attackerId: String = ""
        override val defenderId: String = ""
        override val attackerSkillsUsed: List<String> = emptyList()
        override val defenderSkillsUsed: List<String> = emptyList()
        val isSuccess = false
    }

    companion object {
        fun hit(
            attackerId: String,
            defenderId: String,
            damage: Int,
            damageType: DamageType,
            attackRoll: Int,
            defenseRoll: Int,
            attackerSkillsUsed: List<String>,
            defenderSkillsUsed: List<String>,
            defenseOutcome: DefenseOutcome,
            updatedDefenderCombat: CombatComponent,
            statusEffects: List<StatusEffect> = emptyList(),
            wasKilled: Boolean = false
        ) = Hit(
            attackerId, defenderId, damage, damageType, attackRoll, defenseRoll,
            attackerSkillsUsed, defenderSkillsUsed, defenseOutcome,
            updatedDefenderCombat, statusEffects, wasKilled
        )

        fun miss(
            attackerId: String,
            defenderId: String,
            attackRoll: Int,
            defenseRoll: Int,
            attackerSkillsUsed: List<String>,
            defenderSkillsUsed: List<String>,
            defenseOutcome: DefenseOutcome,
            wasDodged: Boolean
        ) = Miss(attackerId, defenderId, attackRoll, defenseRoll,
                 attackerSkillsUsed, defenderSkillsUsed, defenseOutcome, wasDodged)

        fun failure(reason: String) = Failure(reason)
    }
}
