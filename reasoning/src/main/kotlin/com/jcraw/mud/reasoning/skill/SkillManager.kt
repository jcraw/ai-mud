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
import com.jcraw.mud.core.repository.SkillComponentRepository
import com.jcraw.mud.core.repository.SkillRepository
import com.jcraw.mud.memory.MemoryManager
import kotlin.random.Random

/**
 * Core skill progression logic
 * Manages XP granting, skill unlocking, skill checks, and level-ups
 *
 * Optionally integrates with MemoryManager for RAG-enhanced narratives
 *
 * Apply bodies live in SkillManager* extracts (MUD-034j pure-move).
 */
class SkillManager(
    internal val skillRepo: SkillRepository, // Internal for DispositionManager training access
    private val componentRepo: SkillComponentRepository,
    private val memoryManager: MemoryManager? = null, // Optional for RAG integration
    private val rng: Random = Random.Default
) {

    private fun ctx(): SkillManagerCtx = SkillManagerCtx(
        skillRepo = skillRepo,
        getComponent = ::getSkillComponent,
        updateComponent = ::updateSkillComponent,
        memoryManager = memoryManager,
        rng = rng
    )

    /**
     * Grant XP to a skill
     * - Full XP if success, 20% if failure
     * - Handles level-ups automatically
     * - Returns appropriate SkillEvent (XpGained or LevelUp)
     * - Logs event to repository
     */
    fun grantXp(
        entityId: String,
        skillName: String,
        baseXp: Long,
        success: Boolean
    ): Result<List<SkillEvent>> =
        SkillManagerGrantXp.grant(ctx(), entityId, skillName, baseXp, success)

    /**
     * Attempt skill progression using dual-path system
     *
     * 1. Roll for lucky progression (chance-based instant level-up)
     * 2. If lucky roll fails, grant XP (accumulation-based)
     *
     * Works for any skill: combat (attack/defend), crafting, gathering, social, etc.
     *
     * @param entityId Entity attempting progression
     * @param skillName Skill being used
     * @param baseXp Base XP to grant if lucky roll fails
     * @param success Whether the skill use was successful
     * @return List of SkillEvents (SkillUnlocked, LevelUp, XpGained)
     */
    fun attemptSkillProgress(
        entityId: String,
        skillName: String,
        baseXp: Long,
        success: Boolean
    ): Result<List<SkillEvent>> =
        SkillManagerAttemptProgress.attempt(
            ctx(), ::grantXp, entityId, skillName, baseXp, success
        )

    /**
     * Attempt to unlock a skill using the specified method
     * Returns SkillEvent.SkillUnlocked on success, null on failure
     */
    fun unlockSkill(
        entityId: String,
        skillName: String,
        method: UnlockMethod
    ): Result<SkillEvent.SkillUnlocked?> =
        SkillManagerUnlock.unlock(ctx(), entityId, skillName, method)

    /**
     * Perform a skill check
     * - Roll: d20 + skillLevel vs difficulty
     * - Can be opposed (vs another entity's skill)
     * - Returns SkillCheckResult with success, margin, and narrative
     * - Logs SkillCheckAttempt event
     */
    fun checkSkill(
        entityId: String,
        skillName: String,
        difficulty: Int,
        opposedEntityId: String? = null,
        opposedSkill: String? = null
    ): Result<SkillCheckResult> =
        SkillManagerCheck.check(
            ctx(), entityId, skillName, difficulty, opposedEntityId, opposedSkill
        )

    /**
     * Get skill component for entity
     * Returns empty component if none exists
     */
    fun getSkillComponent(entityId: String): SkillComponent {
        return componentRepo.load(entityId).getOrNull() ?: SkillComponent()
    }

    /**
     * Update skill component for entity
     */
    fun updateSkillComponent(entityId: String, component: SkillComponent): Result<Unit> {
        return componentRepo.save(entityId, component)
    }

    /**
     * Recall skill usage history from memory
     *
     * Returns list of past skill events for narrative coherence
     * @param skillName Optional filter for specific skill
     * @param k Number of memories to retrieve (default: 5)
     */
    suspend fun recallSkillHistory(
        query: String,
        skillName: String? = null,
        k: Int = 5
    ): List<String> {
        if (memoryManager == null) {
            return emptyList()
        }
        return if (skillName != null) {
            memoryManager.recallWithMetadata(
                query = query,
                k = k,
                metadataFilter = mapOf("skill" to skillName)
            )
        } else {
            memoryManager.recall(query, k)
        }
    }

    /**
     * Get the component repository (for PerkSelector creation)
     */
    fun getSkillComponentRepository(): SkillComponentRepository {
        return componentRepo
    }
}
