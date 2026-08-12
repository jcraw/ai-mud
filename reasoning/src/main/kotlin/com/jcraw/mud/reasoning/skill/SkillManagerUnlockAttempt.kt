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

import com.jcraw.mud.core.SkillState
import kotlin.random.Random

/** UnlockMethod.Attempt + Observation resolve (MUD-034j). */
internal object SkillManagerUnlockAttempt {
    fun attempt(current: SkillState, rng: Random): Pair<Boolean, SkillState> {
        val roll = rng.nextInt(1, 101)
        return if (roll <= 15) {
            true to current.unlock().copy(level = 1)
        } else {
            false to current
        }
    }

    fun observation(current: SkillState): Pair<Boolean, SkillState> {
        // Always succeeds, grants 1.5x XP buff
        // Buff is represented as temp levels: 1.5x = +50% = effective +skill_level/2
        // For simplicity, we'll add a fixed buff amount that represents observation benefit
        val buffAmount = 5 // Fixed buff for observation
        return true to current.unlock().applyBuff(buffAmount)
    }
}
