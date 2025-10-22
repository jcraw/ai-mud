package com.jcraw.mud.reasoning.skill

import com.jcraw.mud.core.repository.SkillComponentRepository
import kotlin.random.Random

/**
 * Resolves multi-skill combinations
 * Maps actions to multiple skills with weights, then resolves using weighted average
 *
 * Example: "cast fireball" → Fire Magic (60%), Gesture Casting (30%), Magical Projectile Accuracy (10%)
 */
class SkillComboResolver(
    private val componentRepo: SkillComponentRepository,
    private val rng: Random = Random.Default
) {

    /**
     * Identify skills involved in an action
     * Returns map of skill names to weights (weights should sum to 1.0)
     *
     * This uses rule-based mapping for common actions. In future, could use LLM for dynamic mapping.
     */
    fun identifySkills(action: String): Map<String, Float> {
        val actionLower = action.lowercase()

        // Magic actions
        if ("fireball" in actionLower || "fire" in actionLower && "cast" in actionLower) {
            return mapOf(
                "Fire Magic" to 0.6f,
                "Gesture Casting" to 0.3f,
                "Magical Projectile Accuracy" to 0.1f
            )
        }

        if ("water" in actionLower && "cast" in actionLower) {
            return mapOf(
                "Water Magic" to 0.6f,
                "Gesture Casting" to 0.3f,
                "Magical Projectile Accuracy" to 0.1f
            )
        }

        if ("earth" in actionLower && "cast" in actionLower) {
            return mapOf(
                "Earth Magic" to 0.6f,
                "Gesture Casting" to 0.3f,
                "Magical Projectile Accuracy" to 0.1f
            )
        }

        if ("air" in actionLower && "cast" in actionLower) {
            return mapOf(
                "Air Magic" to 0.6f,
                "Gesture Casting" to 0.3f,
                "Magical Projectile Accuracy" to 0.1f
            )
        }

        // Stealth + lockpicking
        if ("pick" in actionLower && "lock" in actionLower) {
            return mapOf(
                "Lockpicking" to 0.7f,
                "Agility" to 0.3f
            )
        }

        // Backstab requires stealth + backstab skill
        if ("backstab" in actionLower || "sneak attack" in actionLower) {
            return mapOf(
                "Backstab" to 0.6f,
                "Stealth" to 0.4f
            )
        }

        // Disarm trap
        if ("disarm" in actionLower && "trap" in actionLower) {
            return mapOf(
                "Trap Disarm" to 0.7f,
                "Intelligence" to 0.3f
            )
        }

        // Set trap
        if ("set" in actionLower && "trap" in actionLower) {
            return mapOf(
                "Trap Setting" to 0.7f,
                "Intelligence" to 0.3f
            )
        }

        // Crafting
        if ("smith" in actionLower || "forge" in actionLower) {
            return mapOf(
                "Blacksmithing" to 0.7f,
                "Strength" to 0.3f
            )
        }

        // Default: single skill for simple actions
        return emptyMap()
    }

    /**
     * Resolve a multi-skill combo check
     * - Calculates weighted average of skill levels
     * - Rolls d20 + effectiveLevel vs difficulty
     * - Returns SkillCheckResult with combo details
     */
    fun resolveCombo(
        entityId: String,
        skillWeights: Map<String, Float>,
        difficulty: Int
    ): Result<SkillCheckResult> {
        return runCatching {
            require(skillWeights.isNotEmpty()) { "Must specify at least one skill" }
            require(skillWeights.values.all { it > 0 }) { "All weights must be positive" }

            // Get component
            val component = componentRepo.load(entityId).getOrNull()

            // Calculate weighted effective level
            var effectiveLevel = 0.0
            val skillLevels = mutableListOf<Pair<String, Int>>()

            for ((skillName, weight) in skillWeights) {
                val skillLevel = component?.getEffectiveLevel(skillName) ?: 0
                effectiveLevel += skillLevel * weight
                skillLevels.add(skillName to skillLevel)
            }

            val finalLevel = effectiveLevel.toInt()

            // Roll d20 + effectiveLevel
            val roll = rng.nextInt(1, 21)
            val total = roll + finalLevel
            val success = total >= difficulty
            val margin = total - difficulty

            // Build narrative
            val skillDescriptions = skillLevels.joinToString(", ") { (name, level) ->
                "$name (L$level)"
            }

            val narrative = when {
                success && margin >= 10 -> "Masterful combo using $skillDescriptions!"
                success && margin >= 5 -> "Strong combo with $skillDescriptions."
                success -> "Successful combo using $skillDescriptions."
                margin >= -5 -> "Narrow failure combining $skillDescriptions."
                else -> "Failed combo badly despite $skillDescriptions."
            }

            SkillCheckResult(
                success = success,
                roll = roll,
                skillLevel = finalLevel,
                difficulty = difficulty,
                margin = margin,
                narrative = narrative
            )
        }
    }

    /**
     * Convenience method: identify skills from action and resolve combo
     */
    fun resolveAction(
        entityId: String,
        action: String,
        difficulty: Int
    ): Result<SkillCheckResult> {
        val skillWeights = identifySkills(action)

        if (skillWeights.isEmpty()) {
            // No combo identified, return failure result
            return Result.success(
                SkillCheckResult(
                    success = false,
                    roll = 0,
                    skillLevel = 0,
                    difficulty = difficulty,
                    margin = -difficulty,
                    narrative = "Could not identify skills for action: $action"
                )
            )
        }

        return resolveCombo(entityId, skillWeights, difficulty)
    }
}
