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

import com.jcraw.mud.core.DispositionTier
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.SkillComponent
import com.jcraw.mud.reasoning.skill.SkillManager

/**
 * NPC mentor training for [DispositionManager] (MUD-034n).
 */
internal object DispositionTraining {

    fun canTrainPlayer(npc: Entity.NPC): Boolean {
        val tier = DispositionQueries.getDispositionTier(npc)
        return tier == DispositionTier.FRIENDLY || tier == DispositionTier.ALLIED
    }

    fun getTrainingMultiplier(npc: Entity.NPC): Double {
        return when (DispositionQueries.getDispositionTier(npc)) {
            DispositionTier.ALLIED -> 2.5
            DispositionTier.FRIENDLY -> 2.0
            else -> 0.0
        }
    }

    fun trainSkillWithNPC(
        skillManager: SkillManager?,
        playerId: String,
        npc: Entity.NPC,
        skillName: String
    ): Result<String> {
        val manager = skillManager ?: return Result.failure(
            IllegalStateException("SkillManager not initialized")
        )
        val blocked = trainingGate(npc, skillName)
        if (blocked != null) return blocked
        val multiplier = getTrainingMultiplier(npc)
        val component = manager.getSkillComponent(playerId)
        val skill = component.getSkill(skillName)
        return if (skill == null || !skill.unlocked) {
            unlockViaTraining(manager, playerId, npc, skillName, multiplier)
        } else {
            grantTrainingXp(manager, component, playerId, npc, skillName, multiplier)
        }
    }

    private fun trainingGate(npc: Entity.NPC, skillName: String): Result<String>? {
        if (!canTrainPlayer(npc)) {
            val disp = DispositionQueries.getDisposition(npc)
            return Result.failure(
                IllegalStateException("${npc.name} is not friendly enough to train you. (Disposition: $disp)")
            )
        }
        if (!com.jcraw.mud.reasoning.skill.SkillDefinitions.skillExists(skillName)) {
            return Result.failure(IllegalArgumentException("Unknown skill: $skillName"))
        }
        return null
    }

    private fun unlockViaTraining(
        manager: SkillManager,
        playerId: String,
        npc: Entity.NPC,
        skillName: String,
        multiplier: Double
    ): Result<String> {
        val unlockResult = manager.unlockSkill(
            playerId,
            skillName,
            com.jcraw.mud.reasoning.skill.UnlockMethod.Training(npc.id)
        )
        if (unlockResult.isFailure) {
            return Result.failure(unlockResult.exceptionOrNull()!!)
        }
        if (unlockResult.getOrNull() != null) {
            return Result.success(unlockMessage(npc.name, skillName, multiplier))
        }
        return grantTrainingXp(
            manager,
            manager.getSkillComponent(playerId),
            playerId,
            npc,
            skillName,
            multiplier
        )
    }

    private fun unlockMessage(npcName: String, skillName: String, multiplier: Double): String {
        return "$npcName teaches you the basics of $skillName!\n" +
            "You've unlocked $skillName at level 1 with a ${multiplier}x XP training bonus."
    }

    private fun grantTrainingXp(
        manager: SkillManager,
        component: SkillComponent,
        playerId: String,
        npc: Entity.NPC,
        skillName: String,
        multiplier: Double
    ): Result<String> {
        val boostedXp = (100L * multiplier).toLong()
        val currentSkill = component.getSkill(skillName) ?: return Result.failure(
            IllegalStateException("Skill disappeared during training")
        )
        val updatedSkill = currentSkill.addXp(boostedXp)
        persistTrainedSkill(manager, component, playerId, skillName, updatedSkill)
        logTrainingXp(manager, playerId, skillName, boostedXp, updatedSkill.xp, updatedSkill.level)
        return levelUpOrContinue(manager, playerId, npc, skillName, currentSkill.level, updatedSkill, boostedXp, multiplier)
    }

    private fun persistTrainedSkill(
        manager: SkillManager,
        component: SkillComponent,
        playerId: String,
        skillName: String,
        updatedSkill: com.jcraw.mud.core.SkillState
    ) {
        val newComponent = component.updateSkill(skillName, updatedSkill)
        manager.updateSkillComponent(playerId, newComponent).getOrThrow()
        manager.skillRepo.save(playerId, skillName, updatedSkill).getOrThrow()
    }

    private fun logTrainingXp(
        manager: SkillManager,
        playerId: String,
        skillName: String,
        boostedXp: Long,
        currentXp: Long,
        currentLevel: Int
    ) {
        val xpEvent = com.jcraw.mud.core.SkillEvent.XpGained(
            entityId = playerId,
            skillName = skillName,
            xpAmount = boostedXp,
            currentXp = currentXp,
            currentLevel = currentLevel,
            success = true
        )
        manager.skillRepo.logEvent(xpEvent).getOrThrow()
    }

    private fun levelUpOrContinue(
        manager: SkillManager,
        playerId: String,
        npc: Entity.NPC,
        skillName: String,
        oldLevel: Int,
        updatedSkill: com.jcraw.mud.core.SkillState,
        boostedXp: Long,
        multiplier: Double
    ): Result<String> {
        if (updatedSkill.level > oldLevel) {
            logLevelUp(manager, playerId, skillName, oldLevel, updatedSkill)
            return Result.success(levelUpMessage(npc.name, skillName, boostedXp, multiplier, updatedSkill.level))
        }
        return Result.success(trainMessage(npc.name, skillName, boostedXp, multiplier, updatedSkill.level))
    }

    private fun logLevelUp(
        manager: SkillManager,
        playerId: String,
        skillName: String,
        oldLevel: Int,
        updatedSkill: com.jcraw.mud.core.SkillState
    ) {
        val levelUpEvent = com.jcraw.mud.core.SkillEvent.LevelUp(
            entityId = playerId,
            skillName = skillName,
            oldLevel = oldLevel,
            newLevel = updatedSkill.level,
            isAtPerkMilestone = updatedSkill.isAtPerkMilestone()
        )
        manager.skillRepo.logEvent(levelUpEvent).getOrThrow()
    }

    private fun levelUpMessage(
        npcName: String,
        skillName: String,
        boostedXp: Long,
        multiplier: Double,
        newLevel: Int
    ) = "$npcName trains you in $skillName!\n" +
        "You gained ${boostedXp} XP (${multiplier}x multiplier) and leveled up to $newLevel!"

    private fun trainMessage(
        npcName: String,
        skillName: String,
        boostedXp: Long,
        multiplier: Double,
        level: Int
    ) = "$npcName trains you in $skillName!\n" +
        "You gained ${boostedXp} XP (${multiplier}x multiplier). Current level: $level"
}
