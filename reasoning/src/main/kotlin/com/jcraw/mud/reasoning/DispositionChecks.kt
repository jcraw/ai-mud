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

package com.jcraw.mud.reasoning

import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.SocialEvent
import com.jcraw.mud.memory.social.SocialComponentRepository
import com.jcraw.mud.memory.social.SocialEventRepository
import com.jcraw.mud.reasoning.skill.SkillCheckResult
import com.jcraw.mud.reasoning.skill.SkillManager
import kotlinx.serialization.json.Json

/**
 * Persuasion / intimidation skill checks for [DispositionManager] (MUD-034n).
 */
internal object DispositionChecks {

    fun attemptPersuasion(
        skillManager: SkillManager?,
        socialRepo: SocialComponentRepository,
        eventRepo: SocialEventRepository,
        json: Json,
        playerId: String,
        npc: Entity.NPC,
        difficulty: Int
    ): Triple<Boolean, SkillCheckResult?, Entity.NPC> {
        val manager = skillManager ?: return Triple(false, null, npc)

        // Perform Diplomacy skill check
        val checkResult = manager.checkSkill(playerId, "Diplomacy", difficulty)

        if (checkResult.isFailure) {
            return Triple(false, null, npc)
        }

        val result = checkResult.getOrNull() ?: return Triple(false, null, npc)
        grantCheckXp(manager, playerId, "Diplomacy", result.success)
        val event = persuasionEvent(npc.name, result.success, result.margin)
        val updatedNpc = DispositionEvents.applyEvent(socialRepo, eventRepo, json, npc, event)
        return Triple(result.success, result, updatedNpc)
    }

    private fun persuasionEvent(npcName: String, success: Boolean, margin: Int): SocialEvent {
        val dispositionChange = if (success) 10 + (margin.coerceIn(0, 10)) else -5
        val description = if (success) {
            "You successfully persuaded $npcName!"
        } else {
            "Your persuasion attempt failed."
        }
        return SocialEvent.PersuasionAttempt(
            dispositionDelta = dispositionChange,
            description = description
        )
    }

    fun attemptIntimidation(
        skillManager: SkillManager?,
        socialRepo: SocialComponentRepository,
        eventRepo: SocialEventRepository,
        json: Json,
        playerId: String,
        npc: Entity.NPC,
        difficulty: Int
    ): Triple<Boolean, SkillCheckResult?, Entity.NPC> {
        val manager = skillManager ?: return Triple(false, null, npc)

        // Perform Charisma skill check
        val checkResult = manager.checkSkill(playerId, "Charisma", difficulty)

        if (checkResult.isFailure) {
            return Triple(false, null, npc)
        }

        val result = checkResult.getOrNull() ?: return Triple(false, null, npc)
        grantCheckXp(manager, playerId, "Charisma", result.success)
        val event = intimidationEvent(npc.name, result.success, result.margin)
        val updatedNpc = DispositionEvents.applyEvent(socialRepo, eventRepo, json, npc, event)
        return Triple(result.success, result, updatedNpc)
    }

    private fun intimidationEvent(npcName: String, success: Boolean, margin: Int): SocialEvent {
        val dispositionChange = if (success) 5 + (margin.coerceIn(0, 10)) else -10
        val description = if (success) {
            "You successfully intimidated $npcName!"
        } else {
            "Your intimidation attempt failed."
        }
        return SocialEvent.IntimidationAttempt(
            dispositionDelta = dispositionChange,
            description = description
        )
    }

    private fun grantCheckXp(
        manager: SkillManager,
        playerId: String,
        skillName: String,
        success: Boolean
    ) {
        // Grant XP based on success/failure (base XP = 50)
        val xpResult = manager.grantXp(playerId, skillName, baseXp = 50, success = success)
        if (xpResult.isFailure) {
            println("Warning: Failed to grant $skillName XP: ${xpResult.exceptionOrNull()?.message}")
        }
    }
}
