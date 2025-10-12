package com.jcraw.mud.testbot

import com.jcraw.mud.core.SampleDungeon
import com.jcraw.mud.memory.MemoryManager
import com.jcraw.mud.reasoning.CombatNarrator
import com.jcraw.mud.reasoning.NPCInteractionGenerator
import com.jcraw.mud.reasoning.RoomDescriptionGenerator
import com.jcraw.mud.reasoning.procedural.ProceduralDungeonBuilder
import com.jcraw.sophia.llm.OpenAIClient
import kotlinx.coroutines.runBlocking

/**
 * Main entry point for running the test bot.
 */
fun main() = runBlocking {
    println("=" * 60)
    println("  MUD Test Bot - Automated Testing System")
    println("=" * 60)

    // Get API key
    val apiKey = System.getenv("OPENAI_API_KEY")
        ?: System.getProperty("openai.api.key")
        ?: loadApiKeyFromLocalProperties()

    if (apiKey.isNullOrBlank()) {
        println("\n❌ OpenAI API key not found!")
        println("   Set OPENAI_API_KEY environment variable or openai.api.key in local.properties")
        println("   The test bot requires LLM access to generate inputs and validate outputs.\n")
        return@runBlocking
    }

    println("\n✅ OpenAI API key found")

    // Select dungeon type
    println("\nSelect dungeon for testing:")
    println("  1. Sample Dungeon (handcrafted, 6 rooms)")
    println("  2. Procedural Crypt (5 rooms)")
    println("  3. Procedural Castle (5 rooms)")
    print("\nEnter choice (1-3) [default: 1]: ")

    val dungeonChoice = readLine()?.trim() ?: "1"

    // Select scenario first to determine starting room
    println("\nSelect test scenario:")
    println("  1. Exploration (${TestScenario.Exploration().maxSteps} steps)")
    println("  2. Combat (${TestScenario.Combat().maxSteps} steps)")
    println("  3. Skill Checks (${TestScenario.SkillChecks().maxSteps} steps)")
    println("  4. Item Interaction (${TestScenario.ItemInteraction().maxSteps} steps)")
    println("  5. Social Interaction (${TestScenario.SocialInteraction().maxSteps} steps)")
    println("  6. Quest Testing (${TestScenario.QuestTesting().maxSteps} steps)")
    println("  7. Exploratory (${TestScenario.Exploratory().maxSteps} steps)")
    println("  8. Full Playthrough (${TestScenario.FullPlaythrough().maxSteps} steps)")
    print("\nEnter choice (1-8) [default: 1]: ")

    val scenarioChoice = readLine()?.trim() ?: "1"
    val scenario = when (scenarioChoice) {
        "2" -> TestScenario.Combat()
        "3" -> TestScenario.SkillChecks()
        "4" -> TestScenario.ItemInteraction()
        "5" -> TestScenario.SocialInteraction()
        "6" -> TestScenario.QuestTesting()
        "7" -> TestScenario.Exploratory()
        "8" -> TestScenario.FullPlaythrough()
        else -> TestScenario.Exploration()
    }

    // Determine starting room based on scenario
    val startingRoomId = when (scenario) {
        is TestScenario.ItemInteraction -> SampleDungeon.ARMORY_ROOM_ID
        else -> SampleDungeon.STARTING_ROOM_ID
    }

    val worldState = when (dungeonChoice) {
        "2" -> ProceduralDungeonBuilder.generateCrypt(5)
        "3" -> ProceduralDungeonBuilder.generateCastle(5)
        else -> SampleDungeon.createInitialWorldState(startingRoomId = startingRoomId)
    }

    println("\n✅ Dungeon loaded: ${worldState.rooms.size} rooms")
    println("✅ Starting room: ${worldState.player.currentRoomId}")
    println("✅ Scenario selected: ${scenario.name}")
    println("   ${scenario.description}")

    // Initialize LLM components
    println("\n🔧 Initializing LLM components...")
    val llmClient = OpenAIClient(apiKey)
    val memoryManager = MemoryManager(llmClient)
    val descriptionGenerator = RoomDescriptionGenerator(llmClient, memoryManager)
    val npcInteractionGenerator = NPCInteractionGenerator(llmClient, memoryManager)
    val combatNarrator = CombatNarrator(llmClient, memoryManager)

    println("✅ LLM components initialized")

    // Create game engine
    val gameEngine = InMemoryGameEngine(
        initialWorldState = worldState,
        descriptionGenerator = descriptionGenerator,
        npcInteractionGenerator = npcInteractionGenerator,
        combatNarrator = combatNarrator,
        memoryManager = memoryManager,
        llmClient = llmClient
    )

    // Create test bot runner
    val testBot = TestBotRunner(
        llmClient = llmClient,
        gameEngine = gameEngine,
        scenario = scenario
    )

    // Run the test
    println("\n" + "=" * 60)
    println("  Starting Test Bot Run")
    println("=" * 60)

    val report = testBot.run()

    // Show where logs were saved
    println("\n📁 Test logs saved to: test-logs/")
    println("   - Gameplay log: ${scenario.name}_*.txt")
    println("   - Test report: ${scenario.name}_*_report.json")
    println("   - Test summary: ${scenario.name}_*_summary.txt")

    // Cleanup
    llmClient.close()

    println("\n✅ Test bot session complete!")
}

// String repetition helper
private operator fun String.times(n: Int): String = repeat(n)

/**
 * Load API key from local.properties file.
 * This allows the test bot to work when run directly from IDE.
 * Checks both current directory and project root.
 */
private fun loadApiKeyFromLocalProperties(): String? {
    // Try current directory first
    var localPropertiesFile = java.io.File("local.properties")

    // If not found, try project root (when run via Gradle from submodule)
    if (!localPropertiesFile.exists()) {
        localPropertiesFile = java.io.File("../local.properties")
    }

    // If still not found, try going up two levels (just in case)
    if (!localPropertiesFile.exists()) {
        localPropertiesFile = java.io.File("../../local.properties")
    }

    if (!localPropertiesFile.exists()) {
        return null
    }

    return try {
        localPropertiesFile.readLines()
            .firstOrNull { it.trim().startsWith("openai.api.key=") }
            ?.substringAfter("openai.api.key=")
            ?.trim()
    } catch (e: Exception) {
        null
    }
}
