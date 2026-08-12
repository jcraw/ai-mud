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

import com.jcraw.mud.core.SkillComponent
import com.jcraw.mud.core.SkillState

/** UnlockMethod.Training + Prerequisite resolve (MUD-034j). */
internal object SkillManagerUnlockTrain {
    fun training(current: SkillState): Pair<Boolean, SkillState> {
        // Always succeeds, grants level 1 + 2x XP buff
        val buffAmount = 10 // Fixed buff for training (represents 2x XP multiplier effect)
        val trained = current.unlock().copy(level = 1).applyBuff(buffAmount)
        return true to trained
    }

    fun prerequisite(
        method: UnlockMethod.Prerequisite,
        component: SkillComponent,
        current: SkillState
    ): Pair<Boolean, SkillState> {
        val prereq = component.getSkill(method.prerequisiteSkillName)
        return if (prereq != null && prereq.unlocked && prereq.level >= method.requiredLevel) {
            true to current.unlock()
        } else {
            false to current
        }
    }
}
