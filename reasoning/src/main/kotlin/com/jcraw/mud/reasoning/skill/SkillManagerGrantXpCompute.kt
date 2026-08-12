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

package com.jcraw.mud.reasoning.skill

import com.jcraw.mud.config.GameConfig
import com.jcraw.mud.core.SkillComponent
import com.jcraw.mud.core.SkillState

/** XP amount + skill state for grantXp (MUD-034j). */
internal object SkillManagerGrantXpCompute {
    data class Prepared(
        val xpToGrant: Long,
        val oldLevel: Int,
        val wasUnlocked: Boolean,
        val updatedSkill: SkillState,
        val newLevel: Int,
        val newComponent: SkillComponent
    )

    fun xpAmount(baseXp: Long, success: Boolean): Long {
        require(baseXp >= 0) { "Base XP must be non-negative" }
        val baseAmount = if (success) baseXp else (baseXp * 0.2).toLong()
        return (baseAmount.toFloat() * GameConfig.skillXpMultiplier).toLong()
    }

    fun applyXp(current: SkillState, xpToGrant: Long): Pair<SkillState, Boolean> {
        val wasUnlocked = current.unlocked
        var updated = current.addXp(xpToGrant)
        if (!wasUnlocked && updated.level >= 1) {
            updated = updated.unlock()
        }
        return updated to wasUnlocked
    }

    fun prepare(
        component: SkillComponent,
        skillName: String,
        baseXp: Long,
        success: Boolean
    ): Prepared {
        val current = component.getSkill(skillName) ?: SkillState()
        val xpToGrant = xpAmount(baseXp, success)
        val oldLevel = current.level
        val (updatedSkill, wasUnlocked) = applyXp(current, xpToGrant)
        return Prepared(
            xpToGrant = xpToGrant,
            oldLevel = oldLevel,
            wasUnlocked = wasUnlocked,
            updatedSkill = updatedSkill,
            newLevel = updatedSkill.level,
            newComponent = component.updateSkill(skillName, updatedSkill)
        )
    }
}
