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

/** Opposed skill check path (MUD-034j). */
internal object SkillManagerCheckOpposed {
    fun check(
        ctx: SkillManagerCtx,
        entityId: String,
        skillName: String,
        skillLevel: Int,
        opposedSkill: String,
        opponentLevel: Int
    ): SkillCheckResult {
        val b = SkillManagerCheckOpposedBuild.build(
            ctx, skillName, skillLevel, opposedSkill, opponentLevel
        )
        val outcome = if (b.success) "success" else "failure"
        SkillManagerCheckLog.log(
            ctx, entityId, skillName, b.opponentTotal, b.roll, skillLevel,
            b.success, b.margin,
            "Attempted $skillName vs $opposedSkill check: $outcome (roll: ${b.roll}+$skillLevel vs ${b.opposingRoll}+$opponentLevel)",
            "skill_check_opposed",
            isOpposed = true,
            opposingSkill = opposedSkill
        )
        return b.result
    }
}
