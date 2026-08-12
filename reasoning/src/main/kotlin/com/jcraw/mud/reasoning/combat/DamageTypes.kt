package com.jcraw.mud.reasoning.combat

import com.jcraw.mud.core.DamageType
import com.jcraw.mud.core.SkillComponent

/**
 * Context for damage calculation
 * Encapsulates all information needed to calculate damage
 */
data class DamageContext(
    val attackerId: String,
    val defenderId: String,
    val action: String,
    val skillWeights: List<SkillWeight>,
    val attackerSkills: SkillComponent,
    val defenderSkills: SkillComponent?,
    val attackRoll: Int,
    val defenseRoll: Int
)

/**
 * Result of damage calculation
 */
data class DamageResult(
    val baseDamage: Int,
    val skillModifier: Int,
    val itemBonus: Int,
    val resistanceReduction: Int,
    val armorDefense: Int = 0,
    val variance: Int,
    val finalDamage: Int,
    val damageType: DamageType
)
