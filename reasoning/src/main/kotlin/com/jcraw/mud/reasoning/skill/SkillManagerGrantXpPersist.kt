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

import com.jcraw.mud.core.SkillState

/** Persist after grantXp (MUD-034j). */
internal object SkillManagerGrantXpPersist {
    fun save(ctx: SkillManagerCtx, entityId: String, skillName: String, p: SkillManagerGrantXpCompute.Prepared) {
        ctx.updateComponent(entityId, p.newComponent).getOrThrow()
        ctx.skillRepo.save(entityId, skillName, p.updatedSkill).getOrThrow()
    }
}
