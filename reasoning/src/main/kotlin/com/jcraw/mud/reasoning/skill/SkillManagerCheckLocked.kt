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

/** Skill check when skill missing / locked (MUD-034j). */
internal object SkillManagerCheckLocked {
    fun check(
        ctx: SkillManagerCtx,
        entityId: String,
        skillName: String,
        difficulty: Int
    ): SkillCheckResult {
        val roll = ctx.rng.nextInt(1, 21)
        val total = roll + 0
        val margin = total - difficulty
        val success = total >= difficulty
        val result = SkillCheckResult(
            success = success,
            roll = roll,
            skillLevel = 0,
            difficulty = difficulty,
            margin = margin,
            narrative = if (success) "Lucky success with no skill!" else "Failed (no skill)"
        )
        val outcome = if (success) "success" else "failure"
        SkillManagerCheckLog.log(
            ctx, entityId, skillName, difficulty, roll, 0, success, margin,
            "Attempted $skillName check (no skill): $outcome (roll: $roll vs DC $difficulty)",
            "skill_check"
        )
        return result
    }
}
