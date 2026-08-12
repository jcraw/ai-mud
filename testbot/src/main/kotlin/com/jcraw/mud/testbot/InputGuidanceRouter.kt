@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList", "UnusedParameter", "TooGenericExceptionCaught")

package com.jcraw.mud.testbot

/**
 * Routes scenario → guidance pack (MUD-034f).
 */
internal object InputGuidanceRouter {

    fun guidanceFor(
        scenario: TestScenario,
        actionsTaken: List<String>,
        recentHistory: List<TestStep>,
        roomsVisited: Set<String>,
        currentContext: String
    ): String = when (scenario) {
        is TestScenario.Exploration -> InputGuidanceCore.exploration(actionsTaken, roomsVisited)
        is TestScenario.Combat -> InputGuidanceCore.combat(actionsTaken)
        is TestScenario.SkillChecks -> InputGuidanceCore.skillChecks(actionsTaken)
        is TestScenario.ItemInteraction -> InputGuidanceItemsSocial.itemInteraction(actionsTaken)
        is TestScenario.SocialInteraction -> InputGuidanceItemsSocial.socialInteraction(actionsTaken)
        is TestScenario.Exploratory -> InputGuidanceCore.exploratory()
        is TestScenario.FullPlaythrough -> InputGuidanceCore.fullPlaythrough()
        is TestScenario.QuestTesting -> InputGuidanceItemsSocial.questTesting(actionsTaken)
        is TestScenario.BadPlaythrough -> InputGuidanceBadBrute.badPlaythrough(actionsTaken)
        is TestScenario.BruteForcePlaythrough ->
            InputGuidanceBadBrute.bruteForcePlaythrough(actionsTaken, recentHistory)
        is TestScenario.SmartPlaythrough ->
            InputGuidanceSmartSkill.smartPlaythrough(actionsTaken, recentHistory)
        is TestScenario.SkillProgression ->
            InputGuidanceSkillTreasure.skillProgression(scenario, actionsTaken, currentContext)
        is TestScenario.TreasureRoomPlaythrough ->
            InputGuidanceSkillTreasure.treasureRoomPlaythrough(actionsTaken)
    }
}
