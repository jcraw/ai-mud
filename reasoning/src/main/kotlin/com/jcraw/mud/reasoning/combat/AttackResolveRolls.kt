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

import com.jcraw.mud.core.SkillComponent
import kotlin.random.Random

/**
 * Skill classification + attack/defense rolls for [AttackResolveApply] (MUD-034k).
 */
internal object AttackResolveRolls {

    data class RollOutcome(
        val skillWeights: List<SkillWeight>,
        val attackRoll: Int,
        val defenseRoll: Int,
        val isHit: Boolean,
        val defenseOutcome: DefenseOutcome,
        val defenderSkillsUsed: List<String>,
        val dodgeLevel: Int,
        val parryLevel: Int
    )

    suspend fun classifyOrFail(
        skillClassifier: SkillClassifier,
        action: String,
        attackerSkills: SkillComponent
    ): Pair<List<SkillWeight>, AttackResult?> {
        val skillWeights = skillClassifier.classifySkills(action, attackerSkills)
        if (skillWeights.isEmpty()) {
            println("[COMBAT DEBUG] No skills classified (unexpected), using pure d20 roll")
            return emptyList<SkillWeight>() to AttackResult.failure("No applicable skills for this action")
        }
        return skillWeights to null
    }

    fun compute(
        skillWeights: List<SkillWeight>,
        attackerSkills: SkillComponent,
        defenderSkills: SkillComponent?,
        random: Random
    ): RollOutcome {
        val attackRoll = rollAttack(skillWeights, attackerSkills, random)
        val defense = rollDefense(defenderSkills, random)
        val isHit = attackRoll > defense.defenseRoll
        return RollOutcome(
            skillWeights = skillWeights,
            attackRoll = attackRoll,
            defenseRoll = defense.defenseRoll,
            isHit = isHit,
            defenseOutcome = defenseOutcome(isHit, defense.dodgeLevel, defense.parryLevel),
            defenderSkillsUsed = defense.skillsUsed,
            dodgeLevel = defense.dodgeLevel,
            parryLevel = defense.parryLevel
        )
    }

    private fun rollAttack(
        skillWeights: List<SkillWeight>,
        attackerSkills: SkillComponent,
        random: Random
    ): Int {
        val attackModifier = skillWeights.sumOf {
            val skillLevel = attackerSkills.getEffectiveLevel(it.skill)
            println("[COMBAT DEBUG]   - Using skill ${it.skill}: level=$skillLevel (weight=${it.weight})")
            skillLevel * it.weight
        }.toInt()
        println("[COMBAT DEBUG] Total attack modifier: $attackModifier")
        return rollD20(random) + attackModifier
    }

    private data class DefenseRoll(
        val defenseRoll: Int,
        val dodgeLevel: Int,
        val parryLevel: Int,
        val skillsUsed: List<String>
    )

    private fun rollDefense(defenderSkills: SkillComponent?, random: Random): DefenseRoll {
        if (defenderSkills == null) {
            return DefenseRoll(rollD20(random), 0, 0, emptyList())
        }
        val dodgeLevel = defenderSkills.getEffectiveLevel("Dodge")
        val parryLevel = defenderSkills.getEffectiveLevel("Parry")
        // Always track defensive skills (even at level 0) for XP progression
        val skillsUsed = listOf("Dodge", "Parry")
        val defenseModifier = (dodgeLevel * 0.6 + parryLevel * 0.4).toInt()
        return DefenseRoll(rollD20(random) + defenseModifier, dodgeLevel, parryLevel, skillsUsed)
    }

    fun missResult(
        attackerId: String,
        defenderId: String,
        rolls: RollOutcome
    ): AttackResult = AttackResult.miss(
        attackerId = attackerId,
        defenderId = defenderId,
        attackRoll = rolls.attackRoll,
        defenseRoll = rolls.defenseRoll,
        attackerSkillsUsed = rolls.skillWeights.map { it.skill },
        defenderSkillsUsed = rolls.defenderSkillsUsed,
        defenseOutcome = rolls.defenseOutcome,
        wasDodged = rolls.defenseRoll > rolls.attackRoll
    )

    private fun defenseOutcome(isHit: Boolean, dodgeLevel: Int, parryLevel: Int): DefenseOutcome {
        if (isHit) return DefenseOutcome.OVERWHELMED
        val dodgeContribution = dodgeLevel * 0.6
        val parryContribution = parryLevel * 0.4
        return when {
            dodgeContribution > parryContribution * 1.5 -> DefenseOutcome.DODGED
            parryContribution > dodgeContribution * 1.5 -> DefenseOutcome.PARRIED
            dodgeContribution > 0 || parryContribution > 0 -> DefenseOutcome.BLOCKED
            else -> DefenseOutcome.DODGED // Pure luck dodge (no skills)
        }
    }

    fun rollD20(random: Random): Int = random.nextInt(1, 21)
}
