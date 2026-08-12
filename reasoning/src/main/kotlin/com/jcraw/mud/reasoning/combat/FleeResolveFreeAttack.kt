@file:Suppress(
    "LongParameterList",
    "MagicNumber",
    "MaxLineLength",
    "ReturnCount",
    "LongMethod",
    "CyclomaticComplexMethod",
    "ComplexCondition",
    "NestedBlockDepth",
    "TooManyFunctions",
    "ForbiddenComment"
)

package com.jcraw.mud.reasoning.combat

/**
 * Free attack on flee failure (MUD-034k pure-move).
 */
internal object FleeResolveFreeAttack {

    suspend fun onFailure(
        p: FleeResolveParams,
        fleeRoll: Int,
        pursuit: FleeResolvePursuit.Outcome,
        escapeLevel: Int
    ): FleeResult {
        val bestPursuer = pursuit.bestPursuer
        if (bestPursuer == null) {
            return failure(p, fleeRoll, pursuit, escapeLevel, emptyList(), null)
        }
        val freeAttack = p.attackResolver.resolveAttack(
            attackerId = bestPursuer,
            defenderId = p.fleeingEntityId,
            action = "free attack during flee",
            worldState = p.worldState,
            skillManager = p.skillManager,
            attackerEquipped = emptyMap(), // TODO: Get equipped items if needed
            defenderEquipped = emptyMap(),
            templates = emptyMap()
        )
        return failure(p, fleeRoll, pursuit, escapeLevel, listOf(freeAttack), bestPursuer)
    }

    private fun failure(
        p: FleeResolveParams,
        fleeRoll: Int,
        pursuit: FleeResolvePursuit.Outcome,
        escapeLevel: Int,
        freeAttacks: List<AttackResult>,
        interceptorId: String?
    ): FleeResult = FleeResult.failure(
        fleeingEntityId = p.fleeingEntityId,
        targetDirection = p.targetDirection,
        fleeRoll = fleeRoll,
        pursuitRoll = pursuit.highestPursuitRoll,
        escapeSkillUsed = escapeLevel > 0,
        pursuitSkillsUsed = pursuit.pursuitSkillsUsed,
        freeAttacks = freeAttacks,
        interceptorId = interceptorId
    )
}
