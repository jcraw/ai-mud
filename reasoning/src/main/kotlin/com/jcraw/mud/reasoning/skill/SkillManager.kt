package com.jcraw.mud.reasoning.skill

import com.jcraw.mud.core.SkillComponent
import com.jcraw.mud.core.SkillEvent
import com.jcraw.mud.core.SkillState
import com.jcraw.mud.core.repository.SkillComponentRepository
import com.jcraw.mud.core.repository.SkillRepository
import kotlin.random.Random

/**
 * Core skill progression logic
 * Manages XP granting, skill unlocking, skill checks, and level-ups
 */
class SkillManager(
    private val skillRepo: SkillRepository,
    private val componentRepo: SkillComponentRepository,
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

            // Check if skill is unlocked
            if (!currentSkill.unlocked) {
                return Result.failure(IllegalStateException("Skill '$skillName' is not unlocked for entity $entityId"))
            }

            // Calculate XP (full if success, 20% if failure)
            val xpToGrant = if (success) baseXp else (baseXp * 0.2).toLong()

            // Record old level
            val oldLevel = currentSkill.level

            // Add XP and check for level-up
            val updatedSkill = currentSkill.addXp(xpToGrant)
            val newLevel = updatedSkill.level

            // Update component
            val newComponent = component.updateSkill(skillName, updatedSkill)
            updateSkillComponent(entityId, newComponent).getOrThrow()

            // Save to denormalized table
            skillRepo.save(entityId, skillName, updatedSkill).getOrThrow()

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
            }

            events
        }
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
                    // d100 < 5% success chance
                    val roll = rng.nextInt(1, 101)
                    if (roll <= 5) {
                        true to currentSkill.unlock()
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

            result
        }
    }

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
}
