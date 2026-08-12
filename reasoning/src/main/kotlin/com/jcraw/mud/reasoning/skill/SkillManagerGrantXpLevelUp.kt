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

/** Level-up event for grantXp (MUD-034j). */
internal object SkillManagerGrantXpLevelUp {
    fun append(
        events: MutableList<SkillEvent>,
        ctx: SkillManagerCtx,
        entityId: String,
        skillName: String,
        oldLevel: Int,
        newLevel: Int,
        skill: SkillState
    ) {
        if (newLevel <= oldLevel) return
        val levelUpEvent = SkillEvent.LevelUp(
            entityId = entityId,
            skillName = skillName,
            oldLevel = oldLevel,
            newLevel = newLevel,
            isAtPerkMilestone = skill.isAtPerkMilestone()
        )
        events.add(levelUpEvent)
        ctx.skillRepo.logEvent(levelUpEvent).getOrThrow()
        logDodge(skillName, oldLevel, newLevel)
        SkillManagerMemory.remember(
            ctx.memoryManager,
            "$skillName leveled up from $oldLevel to $newLevel!",
            mapOf("skill" to skillName, "event_type" to "level_up")
        )
    }

    private fun logDodge(skillName: String, oldLevel: Int, newLevel: Int) {
        if (skillName.equals("Dodge", ignoreCase = true)) {
            println("DODGE XP LEVEL-UP: ${oldLevel} → ${newLevel} (accumulated XP)")
        }
    }
}
