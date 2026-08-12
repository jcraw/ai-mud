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

/** XP / unlock / level-up events for grantXp (MUD-034j). logEvent then memory. */
internal object SkillManagerGrantXpEvents {
    fun appendXpGained(
        events: MutableList<SkillEvent>,
        ctx: SkillManagerCtx,
        entityId: String,
        skillName: String,
        xpToGrant: Long,
        skill: SkillState,
        success: Boolean
    ) {
        val xpEvent = SkillEvent.XpGained(
            entityId = entityId,
            skillName = skillName,
            xpAmount = xpToGrant,
            currentXp = skill.xp,
            currentLevel = skill.level,
            success = success
        )
        events.add(xpEvent)
        ctx.skillRepo.logEvent(xpEvent).getOrThrow()
        val outcome = if (success) "success" else "failure"
        SkillManagerMemory.remember(
            ctx.memoryManager,
            "Practiced $skillName: $outcome (+${xpToGrant} XP, level ${skill.level})",
            mapOf("skill" to skillName, "event_type" to "xp_gained")
        )
    }

    fun appendAutoUnlock(
        events: MutableList<SkillEvent>,
        ctx: SkillManagerCtx,
        entityId: String,
        skillName: String,
        wasUnlocked: Boolean,
        skill: SkillState
    ) {
        if (!wasUnlocked && skill.unlocked) {
            val unlockEvent = SkillEvent.SkillUnlocked(
                entityId = entityId,
                skillName = skillName,
                unlockMethod = "use-based progression"
            )
            events.add(unlockEvent)
            ctx.skillRepo.logEvent(unlockEvent).getOrThrow()
            SkillManagerMemory.remember(
                ctx.memoryManager,
                "Unlocked $skillName through use-based progression!",
                mapOf("skill" to skillName, "event_type" to "skill_unlocked")
            )
        }
    }
}
