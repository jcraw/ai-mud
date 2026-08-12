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

import com.jcraw.mud.core.Direction
import com.jcraw.mud.core.WorldState
import com.jcraw.mud.reasoning.skill.SkillManager
import kotlin.random.Random

/**
 * Resolves flee attempts using skill-based checks.
 * Apply body lives in FleeResolve* extracts (MUD-034k pure-move).
 */
class FleeResolver(
    private val attackResolver: AttackResolver,
    private val random: Random = Random.Default
) {

    suspend fun resolveFlee(
        fleeingEntityId: String,
        pursuers: List<String>,
        targetDirection: Direction,
        worldState: WorldState,
        skillManager: SkillManager
    ): FleeResult = FleeResolveApply.resolve(
        FleeResolveParams(
            attackResolver = attackResolver,
            random = random,
            fleeingEntityId = fleeingEntityId,
            pursuers = pursuers,
            targetDirection = targetDirection,
            worldState = worldState,
            skillManager = skillManager
        )
    )
}
