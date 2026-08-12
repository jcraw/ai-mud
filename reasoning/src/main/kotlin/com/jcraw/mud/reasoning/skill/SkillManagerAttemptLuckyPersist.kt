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

import com.jcraw.mud.core.SkillComponent
import com.jcraw.mud.core.SkillState

/** Persist lucky progression skill update (MUD-034j). */
internal object SkillManagerAttemptLuckyPersist {
    fun apply(
        ctx: SkillManagerCtx,
        entityId: String,
        skillName: String,
        component: SkillComponent,
        current: SkillState
    ): SkillState {
        val updated = if (!current.unlocked) {
            current.unlock().copy(level = 1, xp = current.xp)
        } else {
            current.copy(level = current.level + 1, xp = current.xp)
        }
        val newComponent = component.updateSkill(skillName, updated)
        ctx.updateComponent(entityId, newComponent).getOrThrow()
        ctx.skillRepo.save(entityId, skillName, updated).getOrThrow()
        return updated
    }
}
