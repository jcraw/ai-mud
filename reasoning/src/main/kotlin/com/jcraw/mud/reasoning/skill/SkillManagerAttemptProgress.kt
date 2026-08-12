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

import com.jcraw.mud.config.GameConfig
import com.jcraw.mud.core.SkillEvent
import com.jcraw.mud.core.SkillState

/** attemptSkillProgress apply entry (MUD-034j pure-move). */
internal object SkillManagerAttemptProgress {
    fun attempt(
        ctx: SkillManagerCtx,
        grantXp: (String, String, Long, Boolean) -> Result<List<SkillEvent>>,
        entityId: String,
        skillName: String,
        baseXp: Long,
        success: Boolean
    ): Result<List<SkillEvent>> = runCatching {
        val component = ctx.getComponent(entityId)
        val current = component.getSkill(skillName) ?: SkillState()
        if (!GameConfig.enableLuckyProgression) {
            return Result.success(
                grantXp(entityId, skillName, baseXp, success).getOrThrow()
            )
        }
        val targetLevel = if (!current.unlocked) 1 else current.level + 1
        val luckyChance = SkillManagerLuckyChance.calculate(targetLevel)
        val roll = ctx.rng.nextInt(1, 101)
        val lucky = SkillManagerAttemptLucky.tryLucky(
            ctx, entityId, skillName, component, current, luckyChance, roll
        )
        if (lucky != null) return Result.success(lucky)
        grantXp(entityId, skillName, baseXp, success).getOrThrow()
    }
}
