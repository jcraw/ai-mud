@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList", "UnusedParameter", "TooGenericExceptionCaught")

package com.jcraw.mud.testbot.validation

import com.jcraw.mud.testbot.TestScenario

/**
 * Routes scenario → criteria pack (MUD-034f).
 */
internal object ValidationScenarioCriteria {

    fun forScenario(scenario: TestScenario): String = coreCriteria(scenario) ?: playthroughCriteria(scenario)

    private fun coreCriteria(scenario: TestScenario): String? = when (scenario) {
        is TestScenario.Exploration -> ValidationCriteriaCore.exploration
        is TestScenario.Combat -> ValidationCriteriaCore.combat
        is TestScenario.SkillChecks -> ValidationCriteriaCore.skillChecks
        is TestScenario.ItemInteraction -> ValidationCriteriaCore.itemInteraction
        is TestScenario.SocialInteraction -> ValidationCriteriaCore.socialInteraction
        is TestScenario.QuestTesting -> ValidationCriteriaCore.questTesting
        is TestScenario.Exploratory -> ValidationCriteriaCore.exploratory
        is TestScenario.FullPlaythrough -> ValidationCriteriaCore.fullPlaythrough
        else -> null
    }

    private fun playthroughCriteria(scenario: TestScenario): String = when (scenario) {
        is TestScenario.BadPlaythrough -> ValidationCriteriaPlaythroughs.badPlaythrough
        is TestScenario.BruteForcePlaythrough -> ValidationCriteriaPlaythroughs.bruteForcePlaythrough
        is TestScenario.SmartPlaythrough -> ValidationCriteriaPlaythroughs.smartPlaythrough
        is TestScenario.SkillProgression -> ValidationCriteriaPlaythroughs.skillProgression
        is TestScenario.TreasureRoomPlaythrough -> ValidationCriteriaPlaythroughs.treasureRoomPlaythrough
        else -> ""
    }
}
