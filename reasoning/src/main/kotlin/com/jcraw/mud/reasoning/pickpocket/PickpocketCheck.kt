@file:Suppress(
    "ReturnCount",
    "MagicNumber",
    "MaxLineLength",
    "TooManyFunctions",
    "LongMethod",
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "LongParameterList",
    "TooGenericExceptionCaught",
    "SwallowedException",
    "ThrowsCount",
    "UnusedParameter"
)

package com.jcraw.mud.reasoning.pickpocket

import com.jcraw.mud.core.CombatComponent
import com.jcraw.mud.core.ComponentType
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.SkillCheckResult
import com.jcraw.mud.core.SkillComponent
import com.jcraw.mud.core.StatusEffectType
import kotlin.random.Random

/**
 * max(Stealth, Agility) vs Perception (+20 wariness) for [PickpocketHandler] (MUD-034n).
 */
internal object PickpocketCheck {

    /**
     * Perform pickpocket skill check: max(Stealth, Agility) vs Perception passive DC
     */
    fun perform(
        playerSkills: SkillComponent,
        targetNpc: Entity.NPC,
        random: Random
    ): SkillCheckResult {
        // Get player's stealth and agility levels
        val stealthLevel = playerSkills.getEffectiveLevel("Stealth")
        val agilityLevel = playerSkills.getEffectiveLevel("Agility")
        val pickpocketSkill = maxOf(stealthLevel, agilityLevel)

        val dc = perceptionDc(targetNpc)

        // Roll d20 + pickpocket skill
        val roll = random.nextInt(1, 21)
        val total = roll + pickpocketSkill
        val success = total >= dc
        val margin = total - dc

        return SkillCheckResult(
            success = success,
            roll = roll,
            modifier = pickpocketSkill,
            total = total,
            dc = dc,
            margin = margin,
            isCriticalSuccess = roll == 20,
            isCriticalFailure = roll == 1
        )
    }

    private fun perceptionDc(targetNpc: Entity.NPC): Int {
        // Calculate target's passive Perception DC (10 + Wisdom modifier + Perception skill bonus)
        val targetWisModifier = targetNpc.stats.wisModifier()
        val targetSkills = targetNpc.getComponent<SkillComponent>(ComponentType.SKILL)
        val targetPerceptionSkill = targetSkills?.getEffectiveLevel("Perception") ?: 0

        // Check if target has wariness status (adds +20 to perception)
        val targetCombat = targetNpc.getComponent<CombatComponent>(ComponentType.COMBAT)
        val warinessBonus = if (targetCombat?.statusEffects?.any { it.type == StatusEffectType.WARINESS } == true) {
            20
        } else {
            0
        }

        return 10 + targetWisModifier + targetPerceptionSkill + warinessBonus
    }
}
