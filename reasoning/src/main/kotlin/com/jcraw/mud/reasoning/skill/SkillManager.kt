package com.jcraw.mud.reasoning.skill

import com.jcraw.mud.config.GameConfig
import com.jcraw.mud.core.SkillComponent
import com.jcraw.mud.core.SkillEvent
import com.jcraw.mud.core.SkillState
import com.jcraw.mud.core.repository.SkillComponentRepository
import com.jcraw.mud.core.repository.SkillRepository
import com.jcraw.mud.memory.MemoryManager
import kotlinx.coroutines.runBlocking
import kotlin.math.floor
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Core skill progression logic
 * Manages XP granting, skill unlocking, skill checks, and level-ups
 *
 * Optionally integrates with MemoryManager for RAG-enhanced narratives
 */
class SkillManager(
    internal val skillRepo: SkillRepository, // Internal for DispositionManager training access
    private val componentRepo: SkillComponentRepository,
    private val memoryManager: MemoryManager? = null, // Optional for RAG integration
    private val rng: Random = Random.Default
) {

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
    ): Result<List<SkillEvent>> {
        return runCatching {
            require(baseXp >= 0) { "Base XP must be non-negative" }

            // Get current component
            val component = getSkillComponent(entityId)
            val currentSkill = component.getSkill(skillName) ?: SkillState()

            // Calculate XP (full if success, 20% if failure) with config multiplier
            val baseAmount = if (success) baseXp else (baseXp * 0.2).toLong()
            val xpToGrant = (baseAmount.toFloat() * GameConfig.skillXpMultiplier).toLong()

            // Record old level and unlock status
            val oldLevel = currentSkill.level
            val wasUnlocked = currentSkill.unlocked

            // Add XP and check for level-up
            var updatedSkill = currentSkill.addXp(xpToGrant)
            val newLevel = updatedSkill.level

            // Log Dodge XP awards for testbot debugging
            if (skillName.equals("Dodge", ignoreCase = true)) {
                println("DODGE XP AWARD [$entityId]: +$xpToGrant XP (now ${updatedSkill.xp}/${updatedSkill.xpToNext}, level ${updatedSkill.level})")
            }

            // Auto-unlock skill if it reaches level 1 or higher through use-based progression
            if (!wasUnlocked && updatedSkill.level >= 1) {
                updatedSkill = updatedSkill.unlock()
            }

            // Update component
            val newComponent = component.updateSkill(skillName, updatedSkill)
            updateSkillComponent(entityId, newComponent).getOrThrow()

            // Save to denormalized table
            skillRepo.save(entityId, skillName, updatedSkill).getOrThrow()

            // DEBUG: Verify skill was saved correctly
            if (skillName.equals("Dodge", ignoreCase = true)) {
                println("DODGE SAVED TO DB [$entityId]: level=${updatedSkill.level}, xp=${updatedSkill.xp}/${updatedSkill.xpToNext}")
            }

            // Generate events
            val events = mutableListOf<SkillEvent>()

            // Always generate XpGained event
            val xpEvent = SkillEvent.XpGained(
                entityId = entityId,
                skillName = skillName,
                xpAmount = xpToGrant,
                currentXp = updatedSkill.xp,
                currentLevel = updatedSkill.level,
                success = success
            )
            events.add(xpEvent)
            skillRepo.logEvent(xpEvent).getOrThrow()

            // Log to memory for RAG
            memoryManager?.let { mm ->
                runBlocking {
                    val outcome = if (success) "success" else "failure"
                    mm.remember(
                        "Practiced $skillName: $outcome (+${xpToGrant} XP, level ${updatedSkill.level})",
                        metadata = mapOf("skill" to skillName, "event_type" to "xp_gained")
                    )
                }
            }

            // Generate SkillUnlocked event if skill was auto-unlocked through use
            if (!wasUnlocked && updatedSkill.unlocked) {
                val unlockEvent = SkillEvent.SkillUnlocked(
                    entityId = entityId,
                    skillName = skillName,
                    unlockMethod = "use-based progression"
                )
                events.add(unlockEvent)
                skillRepo.logEvent(unlockEvent).getOrThrow()

                // Log to memory for RAG
                memoryManager?.let { mm ->
                    runBlocking {
                        mm.remember(
                            "Unlocked $skillName through use-based progression!",
                            metadata = mapOf("skill" to skillName, "event_type" to "skill_unlocked")
                        )
                    }
                }
            }

            // Generate LevelUp event if leveled up
            if (newLevel > oldLevel) {
                val levelUpEvent = SkillEvent.LevelUp(
                    entityId = entityId,
                    skillName = skillName,
                    oldLevel = oldLevel,
                    newLevel = newLevel,
                    isAtPerkMilestone = updatedSkill.isAtPerkMilestone()
                )
                events.add(levelUpEvent)
                skillRepo.logEvent(levelUpEvent).getOrThrow()

                // Log Dodge XP-based level-ups for testbot debugging
                if (skillName.equals("Dodge", ignoreCase = true)) {
                    println("DODGE XP LEVEL-UP: ${oldLevel} → ${newLevel} (accumulated XP)")
                }

                // Log to memory for RAG
                memoryManager?.let { mm ->
                    runBlocking {
                        mm.remember(
                            "$skillName leveled up from $oldLevel to $newLevel!",
                            metadata = mapOf("skill" to skillName, "event_type" to "level_up")
                        )
                    }
                }
            }

            events
        }
    }

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
    ): Result<List<SkillEvent>> {
        return runCatching {
            val component = getSkillComponent(entityId)
            val currentSkill = component.getSkill(skillName) ?: SkillState()

            val events = mutableListOf<SkillEvent>()

            // Check if lucky progression is enabled
            if (!GameConfig.enableLuckyProgression) {
                // Lucky progression disabled, use XP-only path
                val xpEvents = grantXp(entityId, skillName, baseXp, success).getOrThrow()
                events.addAll(xpEvents)
                return Result.success(events)
            }

            // Path 1: Lucky progression (chance-based)
            val targetLevel = if (!currentSkill.unlocked) 1 else currentSkill.level + 1
            val luckyChance = calculateLuckyChance(targetLevel)
            val roll = rng.nextInt(1, 101)

            if (roll <= luckyChance) {
                // Lucky progression! Preserve accumulated XP toward next level
                val updatedSkill = if (!currentSkill.unlocked) {
                    currentSkill.unlock().copy(level = 1, xp = currentSkill.xp)
                } else {
                    currentSkill.copy(level = currentSkill.level + 1, xp = currentSkill.xp)
                }

                // Update component and save
                val newComponent = component.updateSkill(skillName, updatedSkill)
                updateSkillComponent(entityId, newComponent).getOrThrow()
                skillRepo.save(entityId, skillName, updatedSkill).getOrThrow()

                // Generate events
                if (!currentSkill.unlocked) {
                    events.add(SkillEvent.SkillUnlocked(
                        entityId = entityId,
                        skillName = skillName,
                        unlockMethod = "lucky progression"
                    ))
                    skillRepo.logEvent(events.last()).getOrThrow()

                    // Log to memory for RAG
                    memoryManager?.let { mm ->
                        runBlocking {
                            mm.remember(
                                "Unlocked $skillName through lucky progression!",
                                metadata = mapOf("skill" to skillName, "event_type" to "skill_unlocked")
                            )
                        }
                    }
                }

                events.add(SkillEvent.LevelUp(
                    entityId = entityId,
                    skillName = skillName,
                    oldLevel = currentSkill.level,
                    newLevel = updatedSkill.level,
                    isAtPerkMilestone = updatedSkill.isAtPerkMilestone()
                ))
                skillRepo.logEvent(events.last()).getOrThrow()

                // Log Dodge lucky level-ups for testbot debugging
                if (skillName.equals("Dodge", ignoreCase = true)) {
                    println("DODGE LUCKY LEVEL-UP [$entityId]: ${currentSkill.level} → ${updatedSkill.level} (${luckyChance}% chance)")
                }

                // Log to memory for RAG
                memoryManager?.let { mm ->
                    runBlocking {
                        mm.remember(
                            "$skillName leveled up from ${currentSkill.level} to ${updatedSkill.level} (lucky progression)!",
                            metadata = mapOf("skill" to skillName, "event_type" to "level_up_lucky")
                        )
                    }
                }

                return Result.success(events)
            }

            // Path 2: Lucky roll failed, grant XP instead
            val xpEvents = grantXp(entityId, skillName, baseXp, success).getOrThrow()
            events.addAll(xpEvents)

            events
        }
    }

    /**
     * Calculate lucky progression chance for target level
     * Formula: floor(15 / sqrt(targetLevel + 1))
     */
    private fun calculateLuckyChance(targetLevel: Int): Int {
        val baseChance = GameConfig.baseLuckyChance.toDouble()
        return floor(baseChance / sqrt(targetLevel + 1.0)).toInt()
    }

    /**
     * Attempt to unlock a skill using the specified method
     * Returns SkillEvent.SkillUnlocked on success, null on failure
     */
    fun unlockSkill(
        entityId: String,
        skillName: String,
        method: UnlockMethod
    ): Result<SkillEvent.SkillUnlocked?> {
        return runCatching {
            // Get current component
            val component = getSkillComponent(entityId)
            val currentSkill = component.getSkill(skillName) ?: SkillState()

            // Already unlocked?
            if (currentSkill.unlocked) {
                return Result.success(null)
            }

            // Process unlock based on method
            val (unlocked, updatedSkill) = when (method) {
                is UnlockMethod.Attempt -> {
                    // d100 <= 15% success chance
                    val roll = rng.nextInt(1, 101)
                    if (roll <= 15) {
                        // Unlock at level 1 (consistent with use-based unlocking)
                        true to currentSkill.unlock().copy(level = 1)
                    } else {
                        false to currentSkill
                    }
                }

                is UnlockMethod.Observation -> {
                    // Always succeeds, grants 1.5x XP buff
                    // Buff is represented as temp levels: 1.5x = +50% = effective +skill_level/2
                    // For simplicity, we'll add a fixed buff amount that represents observation benefit
                    val buffAmount = 5 // Fixed buff for observation
                    true to currentSkill.unlock().applyBuff(buffAmount)
                }

                is UnlockMethod.Training -> {
                    // Always succeeds, grants level 1 + 2x XP buff
                    val buffAmount = 10 // Fixed buff for training (represents 2x XP multiplier effect)
                    val trainedSkill = currentSkill.unlock().copy(level = 1).applyBuff(buffAmount)
                    true to trainedSkill
                }

                is UnlockMethod.Prerequisite -> {
                    // Check prerequisite
                    val prereqSkill = component.getSkill(method.prerequisiteSkillName)
                    if (prereqSkill != null && prereqSkill.unlocked && prereqSkill.level >= method.requiredLevel) {
                        true to currentSkill.unlock()
                    } else {
                        false to currentSkill
                    }
                }
            }

            if (!unlocked) {
                return Result.success(null)
            }

            // Update component
            val newComponent = component.updateSkill(skillName, updatedSkill)
            updateSkillComponent(entityId, newComponent).getOrThrow()

            // Save to denormalized table
            skillRepo.save(entityId, skillName, updatedSkill).getOrThrow()

            // Generate unlock event
            val methodName = when (method) {
                is UnlockMethod.Attempt -> "attempt"
                is UnlockMethod.Observation -> "observation"
                is UnlockMethod.Training -> "training"
                is UnlockMethod.Prerequisite -> "prerequisite"
            }

            val unlockEvent = SkillEvent.SkillUnlocked(
                entityId = entityId,
                skillName = skillName,
                unlockMethod = methodName
            )

            skillRepo.logEvent(unlockEvent).getOrThrow()

            // Log to memory for RAG
            memoryManager?.let { mm ->
                runBlocking {
                    mm.remember(
                        "Unlocked $skillName via $methodName!",
                        metadata = mapOf("skill" to skillName, "event_type" to "skill_unlocked")
                    )
                }
            }

            unlockEvent
        }
    }

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
    ): Result<SkillCheckResult> {
        return runCatching {
            // Get skill level
            val component = getSkillComponent(entityId)
            val skill = component.getSkill(skillName)

            if (skill == null || !skill.unlocked) {
                // No skill or not unlocked = level 0
                val roll = rng.nextInt(1, 21)
                val total = roll + 0
                val margin = total - difficulty
                val success = total >= difficulty

                val result = SkillCheckResult(
                    success = success,
                    roll = roll,
                    skillLevel = 0,
                    difficulty = difficulty,
                    margin = margin,
                    narrative = if (success) "Lucky success with no skill!" else "Failed (no skill)"
                )

                // Log event
                val event = SkillEvent.SkillCheckAttempt(
                    entityId = entityId,
                    skillName = skillName,
                    difficulty = difficulty,
                    roll = roll,
                    skillLevel = 0,
                    success = success,
                    margin = margin
                )
                skillRepo.logEvent(event).getOrThrow()

                // Log to memory for RAG
                memoryManager?.let { mm ->
                    runBlocking {
                        val outcome = if (success) "success" else "failure"
                        mm.remember(
                            "Attempted $skillName check (no skill): $outcome (roll: $roll vs DC $difficulty)",
                            metadata = mapOf("skill" to skillName, "event_type" to "skill_check")
                        )
                    }
                }

                return Result.success(result)
            }

            val skillLevel = skill.getEffectiveLevel()

            // Opposed check?
            if (opposedEntityId != null && opposedSkill != null) {
                val opponentComponent = getSkillComponent(opposedEntityId)
                val opponentSkillState = opponentComponent.getSkill(opposedSkill)
                val opponentLevel = opponentSkillState?.getEffectiveLevel() ?: 0

                val roll = rng.nextInt(1, 21)
                val opposingRoll = rng.nextInt(1, 21)

                val total = roll + skillLevel
                val opponentTotal = opposingRoll + opponentLevel

                val success = total > opponentTotal
                val margin = total - opponentTotal

                val narrative = if (success) {
                    "You succeed with $skillName against opponent's $opposedSkill!"
                } else {
                    "Your $skillName fails against opponent's $opposedSkill."
                }

                val result = SkillCheckResult.opposed(
                    roll = roll,
                    skillLevel = skillLevel,
                    opposingSkill = opposedSkill,
                    opposingRoll = opposingRoll,
                    opposingSkillLevel = opponentLevel,
                    narrative = narrative
                )

                // Log event
                val event = SkillEvent.SkillCheckAttempt(
                    entityId = entityId,
                    skillName = skillName,
                    difficulty = opponentTotal,
                    roll = roll,
                    skillLevel = skillLevel,
                    success = success,
                    margin = margin,
                    isOpposed = true,
                    opposingSkill = opposedSkill
                )
                skillRepo.logEvent(event).getOrThrow()

                // Log to memory for RAG
                memoryManager?.let { mm ->
                    runBlocking {
                        val outcome = if (success) "success" else "failure"
                        mm.remember(
                            "Attempted $skillName vs $opposedSkill check: $outcome (roll: $roll+$skillLevel vs $opposingRoll+$opponentLevel)",
                            metadata = mapOf("skill" to skillName, "event_type" to "skill_check_opposed")
                        )
                    }
                }

                return Result.success(result)
            }

            // Regular skill check
            val roll = rng.nextInt(1, 21)
            val total = roll + skillLevel
            val success = total >= difficulty
            val margin = total - difficulty

            val narrative = when {
                success && margin >= 10 -> "Overwhelming success with $skillName!"
                success && margin >= 5 -> "Strong success with $skillName."
                success -> "Success with $skillName."
                margin >= -5 -> "Narrow failure with $skillName."
                else -> "Failed $skillName check badly."
            }

            val result = SkillCheckResult(
                success = success,
                roll = roll,
                skillLevel = skillLevel,
                difficulty = difficulty,
                margin = margin,
                narrative = narrative
            )

            // Log event
            val event = SkillEvent.SkillCheckAttempt(
                entityId = entityId,
                skillName = skillName,
                difficulty = difficulty,
                roll = roll,
                skillLevel = skillLevel,
                success = success,
                margin = margin
            )
            skillRepo.logEvent(event).getOrThrow()

            // Log to memory for RAG
            memoryManager?.let { mm ->
                runBlocking {
                    val outcome = if (success) "success" else "failure"
                    mm.remember(
                        "Attempted $skillName check: $outcome (roll: $roll+$skillLevel vs DC $difficulty, margin: $margin)",
                        metadata = mapOf("skill" to skillName, "event_type" to "skill_check")
                    )
                }
            }

            result
        }
    }

    /**
     * Get skill component for entity
     * Returns empty component if none exists
     */
    fun getSkillComponent(entityId: String): SkillComponent {
        val component = componentRepo.load(entityId).getOrNull() ?: SkillComponent()

        // DEBUG: Log loaded Dodge skill
        val dodgeSkill = component.getSkill("Dodge")
        if (dodgeSkill != null) {
            println("DODGE LOADED FROM DB [$entityId]: level=${dodgeSkill.level}, xp=${dodgeSkill.xp}/${dodgeSkill.xpToNext}")
        }

        return component
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
