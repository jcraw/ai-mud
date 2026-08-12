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
import kotlin.random.Random

/** Dispatch unlock method → (unlocked, skill) (MUD-034j). */
internal object SkillManagerUnlockResolve {
    fun resolve(
        method: UnlockMethod,
        component: SkillComponent,
        current: SkillState,
        rng: Random
    ): Pair<Boolean, SkillState> = when (method) {
        is UnlockMethod.Attempt -> SkillManagerUnlockAttempt.attempt(current, rng)
        is UnlockMethod.Observation -> SkillManagerUnlockAttempt.observation(current)
        is UnlockMethod.Training -> SkillManagerUnlockTrain.training(current)
        is UnlockMethod.Prerequisite ->
            SkillManagerUnlockTrain.prerequisite(method, component, current)
    }

    fun methodName(method: UnlockMethod): String = when (method) {
        is UnlockMethod.Attempt -> "attempt"
        is UnlockMethod.Observation -> "observation"
        is UnlockMethod.Training -> "training"
        is UnlockMethod.Prerequisite -> "prerequisite"
    }
}
