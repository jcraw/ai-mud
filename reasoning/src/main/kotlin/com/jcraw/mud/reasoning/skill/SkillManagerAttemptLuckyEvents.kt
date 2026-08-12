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

/** Events for lucky progression (MUD-034j). */
internal object SkillManagerAttemptLuckyEvents {
    fun unlockIfNeeded(
        events: MutableList<SkillEvent>,
        ctx: SkillManagerCtx,
        entityId: String,
        skillName: String,
        current: SkillState
    ) {
        if (current.unlocked) return
        events.add(
            SkillEvent.SkillUnlocked(
                entityId = entityId,
                skillName = skillName,
                unlockMethod = "lucky progression"
            )
        )
        ctx.skillRepo.logEvent(events.last()).getOrThrow()
        SkillManagerMemory.remember(
            ctx.memoryManager,
            "Unlocked $skillName through lucky progression!",
            mapOf("skill" to skillName, "event_type" to "skill_unlocked")
        )
    }

    fun levelUp(
        events: MutableList<SkillEvent>,
        ctx: SkillManagerCtx,
        entityId: String,
        skillName: String,
        current: SkillState,
        updated: SkillState,
        luckyChance: Int
    ) {
        events.add(
            SkillEvent.LevelUp(
                entityId = entityId,
                skillName = skillName,
                oldLevel = current.level,
                newLevel = updated.level,
                isAtPerkMilestone = updated.isAtPerkMilestone()
            )
        )
        ctx.skillRepo.logEvent(events.last()).getOrThrow()
        logDodgeLucky(entityId, skillName, current.level, updated.level, luckyChance)
        SkillManagerMemory.remember(
            ctx.memoryManager,
            "$skillName leveled up from ${current.level} to ${updated.level} (lucky progression)!",
            mapOf("skill" to skillName, "event_type" to "level_up_lucky")
        )
    }

    private fun logDodgeLucky(
        entityId: String,
        skillName: String,
        oldLevel: Int,
        newLevel: Int,
        luckyChance: Int
    ) {
        if (skillName.equals("Dodge", ignoreCase = true)) {
            println(
                "DODGE LUCKY LEVEL-UP [$entityId]: $oldLevel → $newLevel (${luckyChance}% chance)"
            )
        }
    }
}
