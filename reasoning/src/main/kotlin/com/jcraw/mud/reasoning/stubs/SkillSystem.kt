package com.jcraw.mud.reasoning.stubs

import kotlin.random.Random

/**
 * Skill check result
 */
data class SkillCheckResult(
    val success: Boolean,
    val roll: Int,
    val modifier: Int = 0,
    val total: Int = roll + modifier,
    val difficulty: Int
)

/**
 * Interface for future :skill module
 *
 * TODO: INTEGRATION POINT - Replace with real :skill module implementation
 *
 * This stub provides basic d20 skill check functionality until the full skill
 * system is implemented.
 */
interface SkillSystem {
    /**
     * Perform a skill check
     *
     * @param playerId Player performing the check
     * @param skillName Name of the skill (e.g., "CHARISMA", "PERCEPTION", "STEALTH")
     * @param difficulty Difficulty class (DC) to beat
     * @return Result of the skill check
     */
    suspend fun checkSkill(
        playerId: String,
        skillName: String,
        difficulty: Int
    ): SkillCheckResult

    /**
     * Get player's skill level
     *
     * @param playerId Player to query
     * @param skillName Name of the skill
     * @return Skill level (0-20+)
     */
    suspend fun getSkillLevel(playerId: String, skillName: String): Int
}

/**
 * Stub implementation of SkillSystem
 *
 * Uses simple d20 rolls with random modifiers
 * Replace this with real skill system when :skill module is ready
 */
class StubSkillSystem : SkillSystem {

    override suspend fun checkSkill(
        playerId: String,
        skillName: String,
        difficulty: Int
    ): SkillCheckResult {
        // TODO: INTEGRATION POINT - Replace with real skill system
        val roll = Random.nextInt(1, 21) // d20
        val modifier = Random.nextInt(-2, 3) // Random modifier -2 to +2

        return SkillCheckResult(
            success = (roll + modifier) >= difficulty,
            roll = roll,
            modifier = modifier,
            difficulty = difficulty
        )
    }

    override suspend fun getSkillLevel(playerId: String, skillName: String): Int {
        // TODO: INTEGRATION POINT - Replace with real skill system
        return 0 // No skill progression in stub
    }
}
