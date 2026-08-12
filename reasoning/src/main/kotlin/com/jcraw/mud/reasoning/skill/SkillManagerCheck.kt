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

/** checkSkill apply entry (MUD-034j pure-move). */
internal object SkillManagerCheck {
    fun check(
        ctx: SkillManagerCtx,
        entityId: String,
        skillName: String,
        difficulty: Int,
        opposedEntityId: String?,
        opposedSkill: String?
    ): Result<SkillCheckResult> = runCatching {
        val skill = ctx.getComponent(entityId).getSkill(skillName)
        if (skill == null || !skill.unlocked) {
            return Result.success(
                SkillManagerCheckLocked.check(ctx, entityId, skillName, difficulty)
            )
        }
        val skillLevel = skill.getEffectiveLevel()
        if (opposedEntityId != null && opposedSkill != null) {
            val opp = ctx.getComponent(opposedEntityId).getSkill(opposedSkill)
            val oppLevel = opp?.getEffectiveLevel() ?: 0
            return Result.success(
                SkillManagerCheckOpposed.check(
                    ctx, entityId, skillName, skillLevel, opposedSkill, oppLevel
                )
            )
        }
        SkillManagerCheckRegular.check(ctx, entityId, skillName, skillLevel, difficulty)
    }
}
