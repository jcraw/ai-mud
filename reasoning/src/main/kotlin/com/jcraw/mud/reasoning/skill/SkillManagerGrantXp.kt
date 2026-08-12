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

/** grantXp apply entry (MUD-034j pure-move). */
internal object SkillManagerGrantXp {
    fun grant(
        ctx: SkillManagerCtx,
        entityId: String,
        skillName: String,
        baseXp: Long,
        success: Boolean
    ): Result<List<SkillEvent>> = runCatching {
        val prepared = SkillManagerGrantXpCompute.prepare(
            ctx.getComponent(entityId), skillName, baseXp, success
        )
        SkillManagerGrantXpPersist.save(ctx, entityId, skillName, prepared)
        val events = mutableListOf<SkillEvent>()
        SkillManagerGrantXpEvents.appendXpGained(
            events, ctx, entityId, skillName,
            prepared.xpToGrant, prepared.updatedSkill, success
        )
        SkillManagerGrantXpEvents.appendAutoUnlock(
            events, ctx, entityId, skillName,
            prepared.wasUnlocked, prepared.updatedSkill
        )
        SkillManagerGrantXpLevelUp.append(
            events, ctx, entityId, skillName,
            prepared.oldLevel, prepared.newLevel, prepared.updatedSkill
        )
        events
    }
}
