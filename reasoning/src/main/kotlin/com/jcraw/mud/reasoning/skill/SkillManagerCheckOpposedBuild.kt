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

/** Build opposed skill check rolls/result (MUD-034j). */
internal object SkillManagerCheckOpposedBuild {
    data class Built(
        val roll: Int,
        val opposingRoll: Int,
        val skillLevel: Int,
        val opponentLevel: Int,
        val success: Boolean,
        val margin: Int,
        val opponentTotal: Int,
        val result: SkillCheckResult
    )

    fun narrative(success: Boolean, skillName: String, opposedSkill: String): String =
        if (success) {
            "You succeed with $skillName against opponent's $opposedSkill!"
        } else {
            "Your $skillName fails against opponent's $opposedSkill."
        }

    fun build(
        ctx: SkillManagerCtx,
        skillName: String,
        skillLevel: Int,
        opposedSkill: String,
        opponentLevel: Int
    ): Built {
        val roll = ctx.rng.nextInt(1, 21)
        val opposingRoll = ctx.rng.nextInt(1, 21)
        val total = roll + skillLevel
        val opponentTotal = opposingRoll + opponentLevel
        val success = total > opponentTotal
        val margin = total - opponentTotal
        val result = SkillCheckResult.opposed(
            roll = roll,
            skillLevel = skillLevel,
            opposingSkill = opposedSkill,
            opposingRoll = opposingRoll,
            opposingSkillLevel = opponentLevel,
            narrative = narrative(success, skillName, opposedSkill)
        )
        return Built(
            roll, opposingRoll, skillLevel, opponentLevel,
            success, margin, opponentTotal, result
        )
    }
}
