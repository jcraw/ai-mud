package com.jcraw.mud.testbot

import kotlinx.serialization.Serializable

/**
 * Sealed class hierarchy for test scenarios.
 * Each scenario represents a different type of gameplay to test.
 */
@Serializable
sealed class TestScenario {
    abstract val name: String
    abstract val description: String
    abstract val maxSteps: Int

    @Serializable
    data class Exploration(
        override val name: String = "exploration",
        override val description: String = "Test room navigation, look commands, and description variability",
        override val maxSteps: Int = 20,
        val targetRoomsToVisit: Int = 5
    ) : TestScenario()

    @Serializable
    data class Combat(
        override val name: String = "combat",
        override val description: String = "Test combat mechanics, health tracking, and victory/defeat conditions",
        override val maxSteps: Int = 30,
        val targetNPCsToFight: Int = 2
    ) : TestScenario()

    @Serializable
    data class SkillChecks(
        override val name: String = "skill_checks",
        override val description: String = "Test D20 skill checks, stat modifiers, and interactive features",
        override val maxSteps: Int = 25,
        val targetChecksToAttempt: Int = 4
    ) : TestScenario()

    @Serializable
    data class ItemInteraction(
        override val name: String = "item_interaction",
        override val description: String = "Test pickup, drop, equip, and use item mechanics",
        override val maxSteps: Int = 20,
        val targetItemsToInteract: Int = 5
    ) : TestScenario()

    @Serializable
    data class SocialInteraction(
        override val name: String = "social_interaction",
        override val description: String = "Test NPC dialogue, persuasion, and intimidation mechanics",
        override val maxSteps: Int = 15,
        val targetNPCsToTalk: Int = 2
    ) : TestScenario()

    @Serializable
    data class Exploratory(
        override val name: String = "exploratory",
        override val description: String = "Random inputs to test robustness and edge cases",
        override val maxSteps: Int = 50
    ) : TestScenario()

    @Serializable
    data class FullPlaythrough(
        override val name: String = "full_playthrough",
        override val description: String = "Complete playthrough from start to dungeon completion",
        override val maxSteps: Int = 100
    ) : TestScenario()

    @Serializable
    data class QuestTesting(
        override val name: String = "quest_testing",
        override val description: String = "Test quest system: viewing, accepting, progressing, and claiming quests",
        override val maxSteps: Int = 40,
        val targetQuestsToAccept: Int = 2,
        val targetQuestsToClaim: Int = 1
    ) : TestScenario()
}
