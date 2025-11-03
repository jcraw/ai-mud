package com.jcraw.mud.reasoning

import com.jcraw.mud.core.*
import com.jcraw.mud.memory.social.*
import com.jcraw.mud.reasoning.skill.SkillManager
import com.jcraw.mud.reasoning.skill.SkillCheckResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Manages NPC disposition and social event application
 *
 * Responsibilities:
 * - Apply social events to NPCs and persist changes
 * - Calculate disposition-based behavior (dialogue tone, quest hints, prices)
 * - Log social event history for analytics
 * - Integrate skill checks (Diplomacy, Charisma) for persuasion/intimidation
 */
class DispositionManager(
    private val socialRepo: SocialComponentRepository,
    private val eventRepo: SocialEventRepository,
    private val skillManager: SkillManager? = null // Optional for backward compatibility
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Apply social event to NPC, persisting changes to database
     *
     * @param npc The NPC to apply the event to
     * @param event The social event that occurred
     * @return Updated NPC with new disposition, or original NPC if persistence fails
     */
    fun applyEvent(
        npc: Entity.NPC,
        event: SocialEvent
    ): Entity.NPC {
        // Get existing social component or create default
        val social = npc.getComponent<SocialComponent>(ComponentType.SOCIAL) ?: SocialComponent(
            personality = "ordinary",
            traits = emptyList()
        )

        // Apply disposition change (clamped to -100..100)
        val updated = social.applyDispositionChange(event.dispositionDelta)

        // Persist to database
        val saveResult = socialRepo.save(npc.id, updated)
        if (saveResult.isFailure) {
            println("Warning: Failed to save social component for ${npc.id}: ${saveResult.exceptionOrNull()?.message}")
        }

        // Log event to history
        val eventRecord = SocialEventRecord(
            npcId = npc.id,
            eventType = event.eventType,
            dispositionDelta = event.dispositionDelta,
            description = event.description,
            timestamp = System.currentTimeMillis(),
            metadata = event.metadata.takeIf { it.isNotEmpty() }?.let { json.encodeToString(it) }
        )
        val logResult = eventRepo.save(eventRecord)
        if (logResult.isFailure) {
            println("Warning: Failed to log social event for ${npc.id}: ${logResult.exceptionOrNull()?.message}")
        }

        // Return updated NPC
        return npc.withComponent(updated) as Entity.NPC
    }

    /**
     * Determine if NPC should provide quest hints based on disposition
     *
     * NPCs with FRIENDLY or ALLIED disposition will provide hints
     */
    fun shouldProvideQuestHints(npc: Entity.NPC): Boolean {
        val tier = npc.getComponent<SocialComponent>(ComponentType.SOCIAL)?.getDispositionTier()
            ?: DispositionTier.NEUTRAL
        return tier == DispositionTier.ALLIED || tier == DispositionTier.FRIENDLY
    }

    /**
     * Get dialogue tone instruction for LLM based on disposition
     *
     * Returns a string that can be included in LLM prompts to adjust NPC behavior
     */
    fun getDialogueTone(npc: Entity.NPC): String {
        val tier = npc.getComponent<SocialComponent>(ComponentType.SOCIAL)?.getDispositionTier()
            ?: DispositionTier.NEUTRAL

        return when (tier) {
            DispositionTier.ALLIED -> "extremely friendly, helpful, and warm. Offer hints and secrets willingly."
            DispositionTier.FRIENDLY -> "friendly and helpful. Be accommodating."
            DispositionTier.NEUTRAL -> "neutral and professional. Neither helpful nor rude."
            DispositionTier.UNFRIENDLY -> "cold and curt. Give short, unhelpful responses."
            DispositionTier.HOSTILE -> "hostile and threatening. Refuse to help."
        }
    }

    /**
     * Calculate price modifier for trading based on disposition
     *
     * Future enhancement: adjust shop prices based on disposition
     * - Allied NPCs give 30% discount (0.7x)
     * - Friendly NPCs give 15% discount (0.85x)
     * - Neutral NPCs use normal prices (1.0x)
     * - Unfriendly NPCs charge 15% markup (1.15x)
     * - Hostile NPCs charge 50% markup (1.5x)
     */
    fun getPriceModifier(npc: Entity.NPC): Double {
        val tier = npc.getComponent<SocialComponent>(ComponentType.SOCIAL)?.getDispositionTier()
            ?: DispositionTier.NEUTRAL

        return when (tier) {
            DispositionTier.ALLIED -> 0.7    // 30% discount
            DispositionTier.FRIENDLY -> 0.85  // 15% discount
            DispositionTier.NEUTRAL -> 1.0    // Normal price
            DispositionTier.UNFRIENDLY -> 1.15 // 15% markup
            DispositionTier.HOSTILE -> 1.5    // 50% markup
        }
    }

    /**
     * Get recent social event history for an NPC
     *
     * Useful for understanding NPC's relationship with player
     */
    fun getRecentEvents(npcId: String, limit: Int = 10): List<SocialEventRecord> {
        return eventRepo.findRecentByNpcId(npcId, limit).getOrElse { emptyList() }
    }

    /**
     * Get current disposition value for an NPC
     *
     * Returns 0 if NPC has no social component
     */
    fun getDisposition(npc: Entity.NPC): Int {
        return npc.getComponent<SocialComponent>(ComponentType.SOCIAL)?.disposition ?: 0
    }

    /**
     * Get disposition tier for an NPC
     *
     * Returns NEUTRAL if NPC has no social component
     */
    fun getDispositionTier(npc: Entity.NPC): DispositionTier {
        return npc.getComponent<SocialComponent>(ComponentType.SOCIAL)?.getDispositionTier()
            ?: DispositionTier.NEUTRAL
    }

    /**
     * Attempt to persuade an NPC using the Diplomacy skill
     *
     * Uses SkillManager to perform skill check, grants XP on success/failure,
     * and applies disposition change based on outcome.
     *
     * @param playerId The player attempting persuasion
     * @param npc The target NPC
     * @param difficulty The DC for the persuasion check (default: 15)
     * @return Triple of (success, skillCheckResult, updatedNPC)
     */
    fun attemptPersuasion(
        playerId: String,
        npc: Entity.NPC,
        difficulty: Int = 15
    ): Triple<Boolean, SkillCheckResult?, Entity.NPC> {
        val manager = skillManager ?: return Triple(false, null, npc)

        // Perform Diplomacy skill check
        val checkResult = manager.checkSkill(playerId, "Diplomacy", difficulty)

        if (checkResult.isFailure) {
            return Triple(false, null, npc)
        }

        val result = checkResult.getOrNull() ?: return Triple(false, null, npc)

        // Grant XP based on success/failure (base XP = 50)
        val xpResult = manager.grantXp(playerId, "Diplomacy", baseXp = 50, success = result.success)
        if (xpResult.isFailure) {
            println("Warning: Failed to grant Diplomacy XP: ${xpResult.exceptionOrNull()?.message}")
        }

        // Apply disposition change
        val dispositionChange = if (result.success) {
            // Success: +10 to +20 based on margin
            10 + (result.margin.coerceIn(0, 10))
        } else {
            // Failure: -5 (you annoyed them)
            -5
        }

        val event = SocialEvent.PersuasionAttempt(
            dispositionDelta = dispositionChange,
            description = if (result.success) {
                "You successfully persuaded ${npc.name}!"
            } else {
                "Your persuasion attempt failed."
            }
        )

        val updatedNpc = applyEvent(npc, event)

        return Triple(result.success, result, updatedNpc)
    }

    /**
     * Attempt to intimidate an NPC using the Charisma skill
     *
     * Uses SkillManager to perform skill check, grants XP on success/failure,
     * and applies disposition change based on outcome.
     *
     * @param playerId The player attempting intimidation
     * @param npc The target NPC
     * @param difficulty The DC for the intimidation check (default: 15)
     * @return Triple of (success, skillCheckResult, updatedNPC)
     */
    fun attemptIntimidation(
        playerId: String,
        npc: Entity.NPC,
        difficulty: Int = 15
    ): Triple<Boolean, SkillCheckResult?, Entity.NPC> {
        val manager = skillManager ?: return Triple(false, null, npc)

        // Perform Charisma skill check
        val checkResult = manager.checkSkill(playerId, "Charisma", difficulty)

        if (checkResult.isFailure) {
            return Triple(false, null, npc)
        }

        val result = checkResult.getOrNull() ?: return Triple(false, null, npc)

        // Grant XP based on success/failure (base XP = 50)
        val xpResult = manager.grantXp(playerId, "Charisma", baseXp = 50, success = result.success)
        if (xpResult.isFailure) {
            println("Warning: Failed to grant Charisma XP: ${xpResult.exceptionOrNull()?.message}")
        }

        // Apply disposition change
        val dispositionChange = if (result.success) {
            // Success: +5 to +15 based on margin (intimidation gives less disposition than persuasion)
            5 + (result.margin.coerceIn(0, 10))
        } else {
            // Failure: -10 (you made them angry)
            -10
        }

        val event = SocialEvent.IntimidationAttempt(
            dispositionDelta = dispositionChange,
            description = if (result.success) {
                "You successfully intimidated ${npc.name}!"
            } else {
                "Your intimidation attempt failed."
            }
        )

        val updatedNpc = applyEvent(npc, event)

        return Triple(result.success, result, updatedNpc)
    }

    /**
     * Check if NPC will allow training based on disposition
     *
     * NPCs with FRIENDLY or ALLIED disposition will train players
     */
    fun canTrainPlayer(npc: Entity.NPC): Boolean {
        val tier = getDispositionTier(npc)
        return tier == DispositionTier.FRIENDLY || tier == DispositionTier.ALLIED
    }

    /**
     * Get XP multiplier for training based on disposition
     *
     * - ALLIED: 2.5x XP
     * - FRIENDLY: 2.0x XP
     * - Others: No training allowed
     */
    fun getTrainingMultiplier(npc: Entity.NPC): Double {
        return when (getDispositionTier(npc)) {
            DispositionTier.ALLIED -> 2.5
            DispositionTier.FRIENDLY -> 2.0
            else -> 0.0
        }
    }

    /**
     * Train a skill with an NPC mentor
     *
     * Checks disposition to allow training, unlocks skill if not already unlocked,
     * and grants boosted XP based on disposition tier.
     *
     * @param playerId The player being trained
     * @param npc The NPC mentor
     * @param skillName The skill to train
     * @return Result with success message or error
     */
    fun trainSkillWithNPC(
        playerId: String,
        npc: Entity.NPC,
        skillName: String
    ): Result<String> {
        val manager = skillManager ?: return Result.failure(
            IllegalStateException("SkillManager not initialized")
        )

        // Check if NPC will train based on disposition
        if (!canTrainPlayer(npc)) {
            return Result.failure(
                IllegalStateException("${npc.name} is not friendly enough to train you. (Disposition: ${getDisposition(npc)})")
            )
        }

        val multiplier = getTrainingMultiplier(npc)

        // Get player's skill component
        val component = manager.getSkillComponent(playerId)
        val skill = component.getSkill(skillName)

        // Check if skill exists
        if (!com.jcraw.mud.reasoning.skill.SkillDefinitions.skillExists(skillName)) {
            return Result.failure(
                IllegalArgumentException("Unknown skill: $skillName")
            )
        }

        // If skill not unlocked, unlock it via Training method
        if (skill == null || !skill.unlocked) {
            val unlockResult = manager.unlockSkill(
                playerId,
                skillName,
                com.jcraw.mud.reasoning.skill.UnlockMethod.Training(npc.id)
            )

            if (unlockResult.isFailure) {
                return Result.failure(unlockResult.exceptionOrNull()!!)
            }

            val unlockEvent = unlockResult.getOrNull()
            if (unlockEvent != null) {
                return Result.success(
                    "${npc.name} teaches you the basics of $skillName!\n" +
                    "You've unlocked $skillName at level 1 with a ${multiplier}x XP training bonus."
                )
            }
        }

        // Skill already unlocked, grant boosted XP
        val baseXp = 100L
        val boostedXp = (baseXp * multiplier).toLong()

        // Apply XP manually to get the multiplier effect
        val currentSkill = component.getSkill(skillName) ?: return Result.failure(
            IllegalStateException("Skill disappeared during training")
        )

        val updatedSkill = currentSkill.addXp(boostedXp)
        val newComponent = component.updateSkill(skillName, updatedSkill)

        manager.updateSkillComponent(playerId, newComponent).getOrThrow()

        // Save to repository
        manager.skillRepo.save(playerId, skillName, updatedSkill).getOrThrow()

        // Log XP event
        val xpEvent = com.jcraw.mud.core.SkillEvent.XpGained(
            entityId = playerId,
            skillName = skillName,
            xpAmount = boostedXp,
            currentXp = updatedSkill.xp,
            currentLevel = updatedSkill.level,
            success = true
        )
        manager.skillRepo.logEvent(xpEvent).getOrThrow()

        // Check for level-up
        if (updatedSkill.level > currentSkill.level) {
            val levelUpEvent = com.jcraw.mud.core.SkillEvent.LevelUp(
                entityId = playerId,
                skillName = skillName,
                oldLevel = currentSkill.level,
                newLevel = updatedSkill.level,
                isAtPerkMilestone = updatedSkill.isAtPerkMilestone()
            )
            manager.skillRepo.logEvent(levelUpEvent).getOrThrow()

            return Result.success(
                "${npc.name} trains you in $skillName!\n" +
                "You gained ${boostedXp} XP (${multiplier}x multiplier) and leveled up to ${updatedSkill.level}!"
            )
        }

        return Result.success(
            "${npc.name} trains you in $skillName!\n" +
            "You gained ${boostedXp} XP (${multiplier}x multiplier). Current level: ${updatedSkill.level}"
        )
    }
}
