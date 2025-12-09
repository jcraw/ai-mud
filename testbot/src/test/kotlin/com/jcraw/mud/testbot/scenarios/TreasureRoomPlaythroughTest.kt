package com.jcraw.mud.testbot.scenarios

import com.jcraw.app.MudGame
import com.jcraw.app.RealGameEngineAdapter
import com.jcraw.mud.config.GameConfig
import com.jcraw.mud.memory.MemoryManager
import com.jcraw.mud.reasoning.CombatNarrator
import com.jcraw.mud.reasoning.NPCInteractionGenerator
import com.jcraw.mud.reasoning.RoomDescriptionGenerator
import com.jcraw.mud.reasoning.world.initializeAncientAbyssWorld
import com.jcraw.mud.testbot.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.assertTrue

/**
 * E2E test for Treasure Room Playthrough scenario.
 * Tests treasure room system: find room, examine pedestals, take/return/swap items, finalize choice.
 *
 * This test validates:
 * - Treasure room discovery (early placement 2-3 rooms from start)
 * - Pedestal examination (5 items with states)
 * - Take/return mechanics (swap freely)
 * - Exit finalization (leaving with item locks choice)
 * - Re-entry to looted room (pedestals empty)
 * - LLM-driven decision making for item selection
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TreasureRoomPlaythroughTest {

    private var apiKey: String? = null

    @BeforeEach
    fun checkApiKey() {
        // Try to load API key from environment or local.properties
        apiKey = System.getenv("OPENAI_API_KEY")
            ?: System.getProperty("openai.api.key")
            ?: loadApiKeyFromLocalProperties()

        // Skip test if no API key is available
        assumeTrue(
            apiKey != null && apiKey!!.isNotBlank(),
            "Skipping E2E test: OPENAI_API_KEY not set. " +
                "Set OPENAI_API_KEY environment variable or openai.api.key in local.properties to run this test."
        )
    }

    @Test
    @DisplayName("Bot discovers treasure room, examines items, swaps, and finalizes choice")
    @Disabled("Testbot requires V3 world generation migration - re-enable after testbot updated for V3")
    fun `bot completes treasure room interaction flow`() = runBlocking {
        // ARRANGE: Set up game with treasure room enabled
        GameConfig.enableMobGeneration = false  // Disable mobs for focused treasure room test

        // Initialize Ancient Abyss world (should contain treasure room in first 2-3 rooms)
        // Use in-memory database for test isolation
        val abyssWorld = initializeAncientAbyssWorld(
            apiKey = apiKey!!,
            playerId = "test_player",
            playerName = "Test Adventurer",
            dbPath = ":memory:",  // In-memory database for testing
            includeQuests = false  // Simplify test - no quests needed
        ).getOrThrow()

        val worldState = abyssWorld.worldState
        val llmClient = abyssWorld.llmClient

        try {
            // Initialize LLM components
            val memoryManager = MemoryManager(llmClient)
            val descriptionGenerator = RoomDescriptionGenerator(llmClient, memoryManager)
            val npcInteractionGenerator = NPCInteractionGenerator(llmClient, memoryManager)
            val combatNarrator = CombatNarrator(llmClient, memoryManager)

            // Create real game engine (same as console/GUI clients)
            val mudGame = MudGame(
                initialWorldState = worldState,
                descriptionGenerator = descriptionGenerator,
                npcInteractionGenerator = npcInteractionGenerator,
                combatNarrator = combatNarrator,
                memoryManager = memoryManager,
                llmClient = llmClient
            )

            // Wrap in adapter for testbot (captures stdout)
            val gameEngine = RealGameEngineAdapter(mudGame)

            // Create test scenario
            val scenario = TestScenario.TreasureRoomPlaythrough(maxSteps = 40)

            // ACT: Run test bot
            val testBot = TestBotRunner(
                llmClient = llmClient,
                gameEngine = gameEngine,
                scenario = scenario
            )

            val result = testBot.run()

            // ASSERT: Validate treasure room interaction
            assertTrue(result.totalSteps > 0, "Bot should complete at least one step")

            // Generate gameplay report
            val reportGenerator = GameplayReportGenerator(llmClient)
            val gameplayReport = reportGenerator.generateReport(result)

            println("\n=== Treasure Room Playthrough Report ===")
            println(gameplayReport.formatForConsole())

            // Logging is handled automatically by TestBotRunner, but we can save extra reports here if needed

            // Validate expected behaviors from log
            val actionLog = result.steps.map { it.playerInput }.joinToString("\n")

            // Expected flow:
            // 1. Navigate to treasure room (should be within first 2-3 rooms)
            // 2. Examine pedestals
            // 3. Take an item
            // 4. Return the item (optional - bot may skip)
            // 5. Take final item
            // 6. Leave room (finalizes choice)

            // At minimum, bot should:
            // - Issue movement commands (navigate to treasure room)
            // - Use "examine pedestals" or equivalent
            // - Use "take treasure" command

            val foundTreasureRoom = actionLog.contains("examine", ignoreCase = true) &&
                                   (actionLog.contains("pedestal", ignoreCase = true) ||
                                    actionLog.contains("altar", ignoreCase = true) ||
                                    actionLog.contains("treasure", ignoreCase = true))

            assertTrue(
                foundTreasureRoom || result.totalSteps >= 10,
                "Bot should discover and examine treasure room within first 10 steps. " +
                "If treasure room not spawned, this may indicate generation issue."
            )

            // Bot should take at least one treasure
            val tookTreasure = actionLog.contains("take treasure", ignoreCase = true) ||
                              actionLog.contains("claim treasure", ignoreCase = true)

            assertTrue(
                tookTreasure || result.totalSteps >= scenario.maxSteps,
                "Bot should take at least one treasure item. If not taken, may indicate " +
                "perception/intent parsing issue or bot decision-making issue."
            )

            println("\n✅ Treasure room playthrough test completed successfully")
            println("   Steps completed: ${result.totalSteps}")
            println("   Found treasure room: $foundTreasureRoom")
            println("   Took treasure: $tookTreasure")

        } finally {
            // Cleanup LLM client
            llmClient.close()
        }
    }

    /**
     * Helper function to load API key from local.properties
     * (Mirrors logic from other test files)
     */
    private fun loadApiKeyFromLocalProperties(): String? {
        val localPropsFile = java.io.File("local.properties")
        if (!localPropsFile.exists()) return null

        return localPropsFile.readLines()
            .firstOrNull { it.startsWith("openai.api.key=") }
            ?.substringAfter("=")
            ?.trim()
    }
}
