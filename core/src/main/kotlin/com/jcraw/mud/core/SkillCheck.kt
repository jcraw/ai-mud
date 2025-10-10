package com.jcraw.mud.core

import kotlinx.serialization.Serializable

/**
 * Type of stat to check against
 */
@Serializable
enum class StatType {
    STRENGTH,
    DEXTERITY,
    CONSTITUTION,
    INTELLIGENCE,
    WISDOM,
    CHARISMA
}

/**
 * Difficulty class for skill checks (D&D-style)
 */
@Serializable
enum class Difficulty(val dc: Int) {
    TRIVIAL(5),      // Almost impossible to fail
    EASY(10),        // Easy for trained characters
    MEDIUM(15),      // Moderate challenge
    HARD(20),        // Difficult even for skilled characters
    VERY_HARD(25),   // Extremely difficult
    NEARLY_IMPOSSIBLE(30) // Only achievable by masters
}

/**
 * Result of a skill check
 */
@Serializable
data class SkillCheckResult(
    val success: Boolean,
    val roll: Int,              // The d20 roll
    val modifier: Int,          // Stat modifier applied
    val total: Int,             // roll + modifier
    val dc: Int,                // Difficulty class
    val margin: Int,            // How much passed/failed by
    val isCriticalSuccess: Boolean = false,  // Natural 20
    val isCriticalFailure: Boolean = false   // Natural 1
) {
    fun isSuccess(): Boolean = success
    fun isFailure(): Boolean = !success
}

/**
 * A skill check challenge attached to an entity or action
 */
@Serializable
data class SkillChallenge(
    val statType: StatType,
    val difficulty: Difficulty,
    val description: String,
    val successDescription: String,
    val failureDescription: String
)
