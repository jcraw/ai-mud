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

/** Regular (non-opposed) skill check path (MUD-034j). */
internal object SkillManagerCheckRegular {
    fun narrative(skillName: String, success: Boolean, margin: Int): String = when {
        success && margin >= 10 -> "Overwhelming success with $skillName!"
        success && margin >= 5 -> "Strong success with $skillName."
        success -> "Success with $skillName."
        margin >= -5 -> "Narrow failure with $skillName."
        else -> "Failed $skillName check badly."
    }

    fun check(
        ctx: SkillManagerCtx,
        entityId: String,
        skillName: String,
        skillLevel: Int,
        difficulty: Int
    ): SkillCheckResult {
        val roll = ctx.rng.nextInt(1, 21)
        val total = roll + skillLevel
        val success = total >= difficulty
        val margin = total - difficulty
        val result = SkillCheckResult(
            success = success,
            roll = roll,
            skillLevel = skillLevel,
            difficulty = difficulty,
            margin = margin,
            narrative = narrative(skillName, success, margin)
        )
        val outcome = if (success) "success" else "failure"
        SkillManagerCheckLog.log(
            ctx, entityId, skillName, difficulty, roll, skillLevel, success, margin,
            "Attempted $skillName check: $outcome (roll: $roll+$skillLevel vs DC $difficulty, margin: $margin)",
            "skill_check"
        )
        return result
    }
}
