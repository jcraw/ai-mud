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

/** Log SkillCheckAttempt + memory (MUD-034j). */
internal object SkillManagerCheckLog {
    fun log(
        ctx: SkillManagerCtx,
        entityId: String,
        skillName: String,
        difficulty: Int,
        roll: Int,
        skillLevel: Int,
        success: Boolean,
        margin: Int,
        memoryText: String,
        eventType: String,
        isOpposed: Boolean = false,
        opposingSkill: String? = null
    ) {
        val event = SkillEvent.SkillCheckAttempt(
            entityId = entityId,
            skillName = skillName,
            difficulty = difficulty,
            roll = roll,
            skillLevel = skillLevel,
            success = success,
            margin = margin,
            isOpposed = isOpposed,
            opposingSkill = opposingSkill
        )
        ctx.skillRepo.logEvent(event).getOrThrow()
        SkillManagerMemory.remember(
            ctx.memoryManager,
            memoryText,
            mapOf("skill" to skillName, "event_type" to eventType)
        )
    }
}
