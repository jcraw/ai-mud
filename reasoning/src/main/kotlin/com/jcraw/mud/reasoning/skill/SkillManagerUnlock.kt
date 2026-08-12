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

import com.jcraw.mud.core.SkillEvent
import com.jcraw.mud.core.SkillState

/** unlockSkill apply entry (MUD-034j pure-move). */
internal object SkillManagerUnlock {
    fun unlock(
        ctx: SkillManagerCtx,
        entityId: String,
        skillName: String,
        method: UnlockMethod
    ): Result<SkillEvent.SkillUnlocked?> = runCatching {
        val component = ctx.getComponent(entityId)
        val current = component.getSkill(skillName) ?: SkillState()
        if (current.unlocked) return Result.success(null)
        val (unlocked, updated) = SkillManagerUnlockResolve.resolve(
            method, component, current, ctx.rng
        )
        if (!unlocked) return Result.success(null)
        SkillManagerUnlockPersist.saveAndEmit(
            ctx, entityId, skillName, component, updated,
            SkillManagerUnlockResolve.methodName(method)
        )
    }
}
