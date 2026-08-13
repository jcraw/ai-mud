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
import com.jcraw.mud.core.SocialEvent
import com.jcraw.mud.memory.social.SocialComponentRepository
import com.jcraw.mud.memory.social.SocialEventRecord
import com.jcraw.mud.memory.social.SocialEventRepository
import com.jcraw.mud.reasoning.skill.SkillCheckResult
import com.jcraw.mud.reasoning.skill.SkillManager
import kotlinx.serialization.json.Json

/**
 * Manages NPC disposition and social event application
 *
 * Responsibilities:
 * - Apply social events to NPCs and persist changes
 * - Calculate disposition-based behavior (dialogue tone, quest hints, prices)
 * - Log social event history for analytics
 * - Integrate skill checks (Diplomacy, Charisma) for persuasion/intimidation
 *
 * Thin facade — bodies in Disposition* extracts (MUD-034n).
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
    ): Entity.NPC = DispositionEvents.applyEvent(socialRepo, eventRepo, json, npc, event)

    /**
     * Determine if NPC should provide quest hints based on disposition
     *
     * NPCs with FRIENDLY or ALLIED disposition will provide hints
     */
    fun shouldProvideQuestHints(npc: Entity.NPC): Boolean =
        DispositionQueries.shouldProvideQuestHints(npc)

    /**
     * Get dialogue tone instruction for LLM based on disposition
     *
     * Returns a string that can be included in LLM prompts to adjust NPC behavior
     */
    fun getDialogueTone(npc: Entity.NPC): String = DispositionQueries.getDialogueTone(npc)

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
    fun getPriceModifier(npc: Entity.NPC): Double = DispositionQueries.getPriceModifier(npc)

    /**
     * Get recent social event history for an NPC
     *
     * Useful for understanding NPC's relationship with player
     */
    fun getRecentEvents(npcId: String, limit: Int = 10): List<SocialEventRecord> =
        DispositionEvents.getRecentEvents(eventRepo, npcId, limit)

    /**
     * Get current disposition value for an NPC
     *
     * Returns 0 if NPC has no social component
     */
    fun getDisposition(npc: Entity.NPC): Int = DispositionQueries.getDisposition(npc)

    /**
     * Get disposition tier for an NPC
     *
     * Returns NEUTRAL if NPC has no social component
     */
    fun getDispositionTier(npc: Entity.NPC): DispositionTier =
        DispositionQueries.getDispositionTier(npc)

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
    ): Triple<Boolean, SkillCheckResult?, Entity.NPC> =
        DispositionChecks.attemptPersuasion(
            skillManager, socialRepo, eventRepo, json, playerId, npc, difficulty
        )

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
    ): Triple<Boolean, SkillCheckResult?, Entity.NPC> =
        DispositionChecks.attemptIntimidation(
            skillManager, socialRepo, eventRepo, json, playerId, npc, difficulty
        )

    /**
     * Check if NPC will allow training based on disposition
     *
     * NPCs with FRIENDLY or ALLIED disposition will train players
     */
    fun canTrainPlayer(npc: Entity.NPC): Boolean = DispositionTraining.canTrainPlayer(npc)

    /**
     * Get XP multiplier for training based on disposition
     *
     * - ALLIED: 2.5x XP
     * - FRIENDLY: 2.0x XP
     * - Others: No training allowed
     */
    fun getTrainingMultiplier(npc: Entity.NPC): Double =
        DispositionTraining.getTrainingMultiplier(npc)

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
    ): Result<String> = DispositionTraining.trainSkillWithNPC(skillManager, playerId, npc, skillName)
}
