package com.jcraw.mud.reasoning.skill

/**
 * Result of a skill check attempt
 * Contains success status, margin, and narrative context
 */
data class SkillCheckResult(
    val success: Boolean,
    val roll: Int, // The d20 roll result
    val skillLevel: Int, // The skill level used
    val difficulty: Int, // The target difficulty
    val margin: Int, // How much succeeded/failed by (positive = success margin, negative = failure margin)
    val narrative: String, // Brief narrative description of the result
    val isOpposed: Boolean = false, // True if this was an opposed check
    val opposingSkill: String? = null, // The skill used by the opponent (if opposed)
    val opposingRoll: Int? = null, // The opponent's roll (if opposed)
    val opposingSkillLevel: Int? = null // The opponent's skill level (if opposed)
) {
    companion object {
        /**
         * Create a simple success result
         */
        fun success(roll: Int, skillLevel: Int, difficulty: Int, margin: Int, narrative: String = "Success!"): SkillCheckResult {
            return SkillCheckResult(
                success = true,
                roll = roll,
                skillLevel = skillLevel,
                difficulty = difficulty,
                margin = margin,
                narrative = narrative
            )
        }

        /**
         * Create a simple failure result
         */
        fun failure(roll: Int, skillLevel: Int, difficulty: Int, margin: Int, narrative: String = "Failed."): SkillCheckResult {
            return SkillCheckResult(
                success = false,
                roll = roll,
                skillLevel = skillLevel,
                difficulty = difficulty,
                margin = margin,
                narrative = narrative
            )
        }

        /**
         * Create an opposed check result
         */
        fun opposed(
            roll: Int,
            skillLevel: Int,
            opposingSkill: String,
            opposingRoll: Int,
            opposingSkillLevel: Int,
            narrative: String
        ): SkillCheckResult {
            val attackerTotal = roll + skillLevel
            val defenderTotal = opposingRoll + opposingSkillLevel
            val success = attackerTotal > defenderTotal
            val margin = attackerTotal - defenderTotal

            return SkillCheckResult(
                success = success,
                roll = roll,
                skillLevel = skillLevel,
                difficulty = defenderTotal, // Effective difficulty = defender's total
                margin = margin,
                narrative = narrative,
                isOpposed = true,
                opposingSkill = opposingSkill,
                opposingRoll = opposingRoll,
                opposingSkillLevel = opposingSkillLevel
            )
        }
    }
}
