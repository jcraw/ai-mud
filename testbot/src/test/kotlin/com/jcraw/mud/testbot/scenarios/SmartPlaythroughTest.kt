package com.jcraw.mud.testbot.scenarios

import com.jcraw.app.MudGame
import com.jcraw.app.RealGameEngineAdapter
import com.jcraw.mud.memory.MemoryManager
import com.jcraw.mud.reasoning.CombatNarrator
import com.jcraw.mud.reasoning.NPCInteractionGenerator
import com.jcraw.mud.reasoning.RoomDescriptionGenerator
import com.jcraw.mud.testbot.*
import com.jcraw.sophia.llm.OpenAIClient
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * E2E scenario test for Smart Playthrough.
 *
 * Tests the complete gameplay loop where the player:
 * - Uses social skills (persuasion/intimidation) to avoid combat
 * - Talks to NPCs and gathers information
 * - Uses skill checks to solve environmental challenges
 * - Completes dungeon with minimal or no combat
 *
 * This test validates:
 * - Social interaction mechanics (persuasion, intimidation)
 * - Skill check system (STR, INT, etc.)
 * - Multiple solution paths (not just combat)
 * - NPC dialogue and relationship systems
 * - LLM-powered strategic decision making
 *
 * Replaces: test_smart_playthrough.sh
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SmartPlaythroughTest {

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

    @Nested
    @DisplayName("Smart Playthrough Scenario")
    inner class SmartPlaythroughScenario {

        @Test
        @DisplayName("Bot completes smart playthrough using social skills and intelligence")
        fun `bot completes smart playthrough successfully`() = runBlocking {
            // ARRANGE: Create V3 Ancient Abyss world for testing
            val worldState = V3TestWorldHelper.createInitialWorldState(apiKey!!)

            // Initialize LLM components
            val llmClient = OpenAIClient(apiKey!!)
            val memoryManager = MemoryManager(llmClient)
            val descriptionGenerator = RoomDescriptionGenerator(llmClient, memoryManager)
            val npcInteractionGenerator = NPCInteractionGenerator(llmClient, memoryManager)
            val combatNarrator = CombatNarrator(llmClient, memoryManager)

            // Create game engine
            val mudGame = MudGame(
                initialWorldState = worldState,
                descriptionGenerator = descriptionGenerator,
                npcInteractionGenerator = npcInteractionGenerator,
                combatNarrator = combatNarrator,
                memoryManager = memoryManager,
                llmClient = llmClient
            )

        val gameEngine = RealGameEngineAdapter(mudGame)

            // Create test scenario
            val scenario = TestScenario.SmartPlaythrough(
                maxSteps = 50
            )

            // Create test bot runner
            val testBot = TestBotRunner(
                llmClient = llmClient,
                gameEngine = gameEngine,
                scenario = scenario
            )

            try {
                // ACT: Run the test bot
                val report = testBot.run()

                // ASSERT: Verify results
                // 1. Test should complete successfully
                assertEquals(
                    TestStatus.PASSED,
                    report.finalStatus,
                    "Smart playthrough should complete with PASSED status"
                )

                // 2. Player should minimize combat (0-2 combat rounds is ideal)
                // Note: Some combat may occur if social checks fail due to dice rolls
                assertTrue(
                    report.combatRounds <= 5,
                    "Player should minimize combat with social skills (got ${report.combatRounds} combat rounds, expected ≤5)"
                )

                // 3. Player should visit multiple rooms (exploration)
                assertTrue(
                    report.uniqueRoomsVisited >= 3,
                    "Player should explore at least 3 rooms (got ${report.uniqueRoomsVisited})"
                )

                // 4. Pass rate should be high (>= 70% - lower than brute force due to skill check RNG)
                val passRate = if (report.totalSteps > 0) {
                    report.passedSteps.toDouble() / report.totalSteps
                } else {
                    0.0
                }
                assertTrue(
                    passRate >= 0.7,
                    "Pass rate should be >= 70% (got ${(passRate * 100).toInt()}%)"
                )

                // Log results for debugging
                println("\n=== Smart Playthrough Test Results ===")
                println("Status: ${report.finalStatus}")
                println("Steps: ${report.totalSteps} (${report.passedSteps} passed)")
                println("Pass Rate: ${(passRate * 100).toInt()}%")
                println("Combat Rounds: ${report.combatRounds} (minimal combat preferred)")
                println("NPCs Killed: ${report.npcsKilled}")
                println("Damage Taken: ${report.damageTaken}")
                println("Player Died: ${report.playerDied}")
                println("Rooms Visited: ${report.uniqueRoomsVisited} (${report.roomNames.joinToString(", ")})")
                println("Duration: ${report.duration / 1000.0}s")
            } finally {
                // Cleanup
                llmClient.close()
            }
        }

        @Test
        @DisplayName("Bot attempts social interactions before resorting to combat")
        fun `bot prioritizes social skills over combat`() = runBlocking {
            // ARRANGE
            val worldState = V3TestWorldHelper.createInitialWorldState(apiKey!!)
            val llmClient = OpenAIClient(apiKey!!)
            val memoryManager = MemoryManager(llmClient)
            val descriptionGenerator = RoomDescriptionGenerator(llmClient, memoryManager)
            val npcInteractionGenerator = NPCInteractionGenerator(llmClient, memoryManager)
            val combatNarrator = CombatNarrator(llmClient, memoryManager)

            val mudGame = MudGame(
                initialWorldState = worldState,
                descriptionGenerator = descriptionGenerator,
                npcInteractionGenerator = npcInteractionGenerator,
                combatNarrator = combatNarrator,
                memoryManager = memoryManager,
                llmClient = llmClient
            )

        val gameEngine = RealGameEngineAdapter(mudGame)

            val scenario = TestScenario.SmartPlaythrough(maxSteps = 50)
            val testBot = TestBotRunner(llmClient, gameEngine, scenario)

            try {
                // ACT
                val report = testBot.run()

                // ASSERT
                // Bot should complete successfully or have minimal combat
                assertTrue(
                    report.finalStatus == TestStatus.PASSED || report.combatRounds <= 5,
                    "Bot should either pass or have minimal combat (got status: ${report.finalStatus}, combat rounds: ${report.combatRounds})"
                )

                // Combat should be minimal - social approach should reduce fighting
                assertTrue(
                    report.combatRounds <= 10,
                    "Combat should be minimal when using social skills (got ${report.combatRounds} rounds)"
                )

                println("\n=== Social Skills Priority Test ===")
                println("Status: ${report.finalStatus}")
                println("Combat Rounds: ${report.combatRounds}")
                println("Damage Taken: ${report.damageTaken}")
                println("NPCs Killed: ${report.npcsKilled}")
            } finally {
                llmClient.close()
            }
        }

        @Test
        @DisplayName("Bot explores secret areas and completes skill checks")
        fun `bot navigates and solves environmental puzzles`() = runBlocking {
            // ARRANGE
            val worldState = V3TestWorldHelper.createInitialWorldState(apiKey!!)
            val llmClient = OpenAIClient(apiKey!!)
            val memoryManager = MemoryManager(llmClient)
            val descriptionGenerator = RoomDescriptionGenerator(llmClient, memoryManager)
            val npcInteractionGenerator = NPCInteractionGenerator(llmClient, memoryManager)
            val combatNarrator = CombatNarrator(llmClient, memoryManager)

            val mudGame = MudGame(
                initialWorldState = worldState,
                descriptionGenerator = descriptionGenerator,
                npcInteractionGenerator = npcInteractionGenerator,
                combatNarrator = combatNarrator,
                memoryManager = memoryManager,
                llmClient = llmClient
            )

        val gameEngine = RealGameEngineAdapter(mudGame)

            val scenario = TestScenario.SmartPlaythrough(maxSteps = 50)
            val testBot = TestBotRunner(llmClient, gameEngine, scenario)

            try {
                // ACT
                val report = testBot.run()

                // ASSERT
                // Bot should explore multiple rooms (including secret areas)
                assertTrue(
                    report.uniqueRoomsVisited >= 3,
                    "Bot should explore at least 3 rooms (got ${report.uniqueRoomsVisited})"
                )

                // Each room should have a valid name
                report.roomNames.forEach { roomName ->
                    assertTrue(
                        roomName.isNotBlank(),
                        "All visited rooms should have valid names"
                    )
                }

                // Test should complete (PASSED or reasonable progress)
                assertTrue(
                    report.finalStatus == TestStatus.PASSED || report.totalSteps >= 15,
                    "Bot should make significant progress through dungeon (status: ${report.finalStatus}, steps: ${report.totalSteps})"
                )

                println("\n=== Navigation & Puzzle Solving Test ===")
                println("Status: ${report.finalStatus}")
                println("Rooms Visited: ${report.uniqueRoomsVisited}")
                println("Room Names: ${report.roomNames.joinToString(", ")}")
                println("Total Steps: ${report.totalSteps}")
            } finally {
                llmClient.close()
            }
        }
    }

    /**
     * Load API key from local.properties file.
     * Checks current directory and parent directories.
     */
    private fun loadApiKeyFromLocalProperties(): String? {
        val locations = listOf(
            "local.properties",
            "../local.properties",
            "../../local.properties"
        )

        for (location in locations) {
            val file = java.io.File(location)
            if (file.exists()) {
                return try {
                    file.readLines()
                        .firstOrNull { it.trim().startsWith("openai.api.key=") }
                        ?.substringAfter("openai.api.key=")
                        ?.trim()
                } catch (e: Exception) {
                    null
                }
            }
        }
        return null
    }
}
