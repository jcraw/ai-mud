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

package com.jcraw.mud.reasoning.procedural

import com.jcraw.mud.core.Difficulty
import com.jcraw.mud.core.SkillChallenge
import com.jcraw.mud.core.StatType
import com.jcraw.mud.core.Stats
import kotlin.random.Random

/**
 * Stats / health / social challenges for [NPCGenerator] (MUD-034n).
 */
internal object NPCGeneratorStats {

    /**
     * Generate stats based on power level
     */
    fun generateStatsForPowerLevel(powerLevel: Int, random: Random): Stats {
        // Base stats increase with power level
        val baseStatMin = 8 + (powerLevel * 2)
        val baseStatMax = 12 + (powerLevel * 3)

        return Stats(
            strength = random.nextInt(baseStatMin, baseStatMax + 1),
            dexterity = random.nextInt(baseStatMin, baseStatMax + 1),
            constitution = random.nextInt(baseStatMin, baseStatMax + 1),
            intelligence = random.nextInt(baseStatMin, baseStatMax + 1),
            wisdom = random.nextInt(baseStatMin, baseStatMax + 1),
            charisma = random.nextInt(baseStatMin, baseStatMax + 1)
        )
    }

    /**
     * Calculate health based on power level and constitution
     */
    fun calculateHealthForPowerLevel(powerLevel: Int, constitution: Int): Pair<Int, Int> {
        val conModifier = (constitution - 10) / 2
        val baseHealth = 20 + (powerLevel * 15)
        val healthBonus = conModifier * powerLevel * 2
        val maxHealth = baseHealth + healthBonus

        return Pair(maxHealth, maxHealth)
    }

    /**
     * Create social challenges based on power level
     * Returns pair of (persuasionChallenge, intimidationChallenge)
     */
    fun createSocialChallenges(
        npcName: String,
        powerLevel: Int,
        includePersuasion: Boolean,
        includeIntimidation: Boolean
    ): Pair<SkillChallenge?, SkillChallenge?> {
        val difficulty = difficultyFor(powerLevel)
        val persuasion = if (includePersuasion) persuasionChallenge(npcName, difficulty) else null
        val intimidation = if (includeIntimidation) intimidationChallenge(npcName, difficulty) else null
        return Pair(persuasion, intimidation)
    }

    private fun difficultyFor(powerLevel: Int): Difficulty {
        // Difficulty scales with power level
        return when (powerLevel) {
            1 -> Difficulty.EASY          // DC 10
            2 -> Difficulty.MEDIUM        // DC 15
            3 -> Difficulty.HARD          // DC 20
            else -> Difficulty.VERY_HARD  // DC 25 for bosses
        }
    }

    private fun persuasionChallenge(npcName: String, difficulty: Difficulty): SkillChallenge {
        return SkillChallenge(
            statType = StatType.CHARISMA,
            difficulty = difficulty,
            description = "Attempt to persuade $npcName through charm and reason",
            successDescription = "Your words strike a chord. $npcName is persuaded.",
            failureDescription = "Your attempt at persuasion falls flat. $npcName is unmoved."
        )
    }

    private fun intimidationChallenge(npcName: String, difficulty: Difficulty): SkillChallenge {
        return SkillChallenge(
            statType = StatType.CHARISMA,
            difficulty = difficulty,
            description = "Attempt to intimidate $npcName through force of will",
            successDescription = "Your intimidating presence works. $npcName backs down.",
            failureDescription = "Your attempt at intimidation fails. $npcName is unimpressed."
        )
    }
}
