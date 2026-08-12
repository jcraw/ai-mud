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
import com.jcraw.mud.core.SkillEvent
import com.jcraw.mud.core.SkillState

/** Persist unlock + emit SkillUnlocked (MUD-034j). */
internal object SkillManagerUnlockPersist {
    fun saveAndEmit(
        ctx: SkillManagerCtx,
        entityId: String,
        skillName: String,
        component: SkillComponent,
        updated: SkillState,
        methodName: String
    ): SkillEvent.SkillUnlocked {
        val newComponent = component.updateSkill(skillName, updated)
        ctx.updateComponent(entityId, newComponent).getOrThrow()
        ctx.skillRepo.save(entityId, skillName, updated).getOrThrow()
        val unlockEvent = SkillEvent.SkillUnlocked(
            entityId = entityId,
            skillName = skillName,
            unlockMethod = methodName
        )
        ctx.skillRepo.logEvent(unlockEvent).getOrThrow()
        SkillManagerMemory.remember(
            ctx.memoryManager,
            "Unlocked $skillName via $methodName!",
            mapOf("skill" to skillName, "event_type" to "skill_unlocked")
        )
        return unlockEvent
    }
}
