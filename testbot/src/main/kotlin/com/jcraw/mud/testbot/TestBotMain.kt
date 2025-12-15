package com.jcraw.mud.testbot

import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Main entry point for the test bot.
 * Uses V3 world generation with Ancient Abyss dungeon.
 */
fun main(args: Array<String>) = runBlocking {
    println("=".repeat(60))
    println("AI-MUD Test Bot - V3 World Generation")
    println("=".repeat(60))
    println()

    // Get API key
    val apiKey = getApiKey()
    if (apiKey == null) {
        println("ERROR: No OpenAI API key found.")
        println("Set OPENAI_API_KEY environment variable or add openai.api.key to local.properties")
        return@runBlocking
    }

    // Parse scenario from args
    val scenario = parseScenarioFromArgs(args)
    println("Selected scenario: ${scenario.name}")
    println()

    // Initialize V3 world
    println("Initializing V3 world (Ancient Abyss)...")
    val ancientAbyssWorld = try {
        V3TestWorldHelper.createFullTestWorld(
            apiKey = apiKey,
            playerId = "testbot_player",
            playerName = "Test Bot"
        )
    } catch (e: Exception) {
        println("ERROR: Failed to initialize world: ${e.message}")
        e.printStackTrace()
        return@runBlocking
    }
    println("World initialized successfully!")
    println()

    // Create game engine
    val gameEngine = V3TestGameEngine(ancientAbyssWorld)

    // Run initial look command
    println("Starting location: ${ancientAbyssWorld.worldState.getCurrentSpace()?.name ?: "Unknown"}")
    println()

    // Create and run test bot
    val testBot = TestBotRunner(
        llmClient = ancientAbyssWorld.llmClient,
        gameEngine = gameEngine,
        scenario = scenario
    )

    val report = testBot.run()

    // Print final status
    println()
    if (report.finalStatus == TestStatus.PASSED) {
        println("Test bot completed successfully!")
    } else {
        println("Test bot finished with status: ${report.finalStatus}")
    }
}

/**
 * Get OpenAI API key from environment or local.properties.
 */
private fun getApiKey(): String? {
    // Try environment variable first
    System.getenv("OPENAI_API_KEY")?.let { return it }

    // Try local.properties
    val localProps = File("local.properties")
    if (localProps.exists()) {
        localProps.readLines().forEach { line ->
            if (line.startsWith("openai.api.key=")) {
                return line.substringAfter("=").trim()
            }
        }
    }

    return null
}

/**
 * Parse scenario from command line args.
 */
private fun parseScenarioFromArgs(args: Array<String>): TestScenario {
    val scenarioName = args.firstOrNull()?.lowercase() ?: "exploration"

    return when (scenarioName) {
        "exploration" -> TestScenario.Exploration()
        "combat" -> TestScenario.Combat()
        "skill_checks", "skills" -> TestScenario.SkillChecks()
        "items", "item_interaction" -> TestScenario.ItemInteraction()
        "social", "social_interaction" -> TestScenario.SocialInteraction()
        "exploratory", "random" -> TestScenario.Exploratory()
        "full", "full_playthrough" -> TestScenario.FullPlaythrough()
        "quest", "quests", "quest_testing" -> TestScenario.QuestTesting()
        "bad", "bad_playthrough" -> TestScenario.BadPlaythrough()
        "brute", "brute_force", "brute_force_playthrough" -> TestScenario.BruteForcePlaythrough()
        "smart", "smart_playthrough" -> TestScenario.SmartPlaythrough()
        "skill_progression", "progression" -> TestScenario.SkillProgression()
        "treasure", "treasure_room" -> TestScenario.TreasureRoomPlaythrough()
        else -> {
            println("Unknown scenario '$scenarioName'. Using default: exploration")
            println("Available scenarios: exploration, combat, skill_checks, items, social,")
            println("                     exploratory, full, quest, bad, brute, smart,")
            println("                     skill_progression, treasure")
            println()
            TestScenario.Exploration()
        }
    }
}
