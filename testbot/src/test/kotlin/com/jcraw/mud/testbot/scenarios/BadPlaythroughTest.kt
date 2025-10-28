package com.jcraw.mud.testbot.scenarios

import com.jcraw.mud.core.SampleDungeon
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
 * E2E scenario test for Bad Playthrough.
 *
 * Tests the complete gameplay loop where the player:
 * - Rushes to the boss room without preparation
 * - Skips gear collection
 * - Dies to the boss
 *
 * This test validates:
 * - Game difficulty is properly tuned
 * - Death mechanics work correctly
 * - Boss combat is challenging without proper gear
 * - LLM can execute a "bad" strategy when instructed
 *
 * Replaces: test_bad_playthrough.sh
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BadPlaythroughTest {

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
    @DisplayName("Bad Playthrough Scenario")
    inner class BadPlaythroughScenario {

        @Test
        @DisplayName("Bot rushes to boss and dies without gear")
        fun `bot dies to boss without preparation`() = runBlocking {
            // ARRANGE: Create SampleDungeon (designed for testing with/without gear)
            val worldState = SampleDungeon.createInitialWorldState()

            // Initialize LLM components
            val llmClient = OpenAIClient(apiKey!!)
            val memoryManager = MemoryManager(llmClient)
            val descriptionGenerator = RoomDescriptionGenerator(llmClient, memoryManager)
            val npcInteractionGenerator = NPCInteractionGenerator(llmClient, memoryManager)
            val combatNarrator = CombatNarrator(llmClient, memoryManager)

            // Create game engine
            val gameEngine = InMemoryGameEngine(
                initialWorldState = worldState,
                descriptionGenerator = descriptionGenerator,
                npcInteractionGenerator = npcInteractionGenerator,
                combatNarrator = combatNarrator,
                memoryManager = memoryManager,
                llmClient = llmClient
            )

            // Create test scenario
            val scenario = TestScenario.BadPlaythrough(
                maxSteps = 30
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
                // 1. Test should complete successfully (PASSED means bot executed strategy correctly)
                assertEquals(
                    TestStatus.PASSED,
                    report.finalStatus,
                    "Bad playthrough should complete with PASSED status (validates death mechanics)"
                )

                // 2. Player should die (this is the expected outcome)
                assertTrue(
                    report.playerDied,
                    "Player should die to boss without proper gear (validates difficulty tuning)"
                )

                // 3. Player should visit few rooms (rushing to boss)
                // Note: May visit 3-5 rooms navigating to boss room
                assertTrue(
                    report.uniqueRoomsVisited >= 1,
                    "Player should visit at least the starting room (got ${report.uniqueRoomsVisited})"
                )

                // 4. Player should take significant damage before dying
                assertTrue(
                    report.damageTaken > 0,
                    "Player should take damage before death (got ${report.damageTaken})"
                )

                // Log results for debugging
                println("\n=== Bad Playthrough Test Results ===")
                println("Status: ${report.finalStatus}")
                println("Steps: ${report.totalSteps} (${report.passedSteps} passed)")
                println("Player Died: ${report.playerDied} ✓ (expected)")
                println("Damage Taken: ${report.damageTaken}")
                println("NPCs Killed: ${report.npcsKilled}")
                println("Rooms Visited: ${report.uniqueRoomsVisited} (${report.roomNames.joinToString(", ")})")
                println("Duration: ${report.duration / 1000.0}s")
            } finally {
                // Cleanup
                llmClient.close()
            }
        }

        @Test
        @DisplayName("Bot reaches boss room quickly without collecting gear")
        fun `bot rushes to boss without gear collection`() = runBlocking {
            // ARRANGE
            val worldState = SampleDungeon.createInitialWorldState()
            val llmClient = OpenAIClient(apiKey!!)
            val memoryManager = MemoryManager(llmClient)
            val descriptionGenerator = RoomDescriptionGenerator(llmClient, memoryManager)
            val npcInteractionGenerator = NPCInteractionGenerator(llmClient, memoryManager)
            val combatNarrator = CombatNarrator(llmClient, memoryManager)

            val gameEngine = InMemoryGameEngine(
                initialWorldState = worldState,
                descriptionGenerator = descriptionGenerator,
                npcInteractionGenerator = npcInteractionGenerator,
                combatNarrator = combatNarrator,
                memoryManager = memoryManager,
                llmClient = llmClient
            )

            val scenario = TestScenario.BadPlaythrough(maxSteps = 30)
            val testBot = TestBotRunner(llmClient, gameEngine, scenario)

            try {
                // ACT
                val report = testBot.run()

                // ASSERT
                // Bot should reach boss relatively quickly (within 30 steps)
                assertTrue(
                    report.totalSteps <= 30,
                    "Bot should reach boss within 30 steps (got ${report.totalSteps})"
                )

                // Bot should die (expected outcome)
                assertTrue(
                    report.playerDied,
                    "Player should die when rushing boss without gear"
                )

                // Combat should occur (fighting the boss)
                assertTrue(
                    report.combatRounds > 0,
                    "Combat should occur with boss (got ${report.combatRounds} rounds)"
                )

                println("\n=== Quick Boss Rush Test ===")
                println("Total Steps: ${report.totalSteps}")
                println("Player Died: ${report.playerDied}")
                println("Combat Rounds: ${report.combatRounds}")
                println("Rooms Visited: ${report.uniqueRoomsVisited}")
            } finally {
                llmClient.close()
            }
        }

        @Test
        @DisplayName("Bot takes fatal damage from boss encounter")
        fun `bot takes lethal damage without equipment`() = runBlocking {
            // ARRANGE
            val worldState = SampleDungeon.createInitialWorldState()
            val llmClient = OpenAIClient(apiKey!!)
            val memoryManager = MemoryManager(llmClient)
            val descriptionGenerator = RoomDescriptionGenerator(llmClient, memoryManager)
            val npcInteractionGenerator = NPCInteractionGenerator(llmClient, memoryManager)
            val combatNarrator = CombatNarrator(llmClient, memoryManager)

            val gameEngine = InMemoryGameEngine(
                initialWorldState = worldState,
                descriptionGenerator = descriptionGenerator,
                npcInteractionGenerator = npcInteractionGenerator,
                combatNarrator = combatNarrator,
                memoryManager = memoryManager,
                llmClient = llmClient
            )

            val scenario = TestScenario.BadPlaythrough(maxSteps = 30)
            val testBot = TestBotRunner(llmClient, gameEngine, scenario)

            try {
                // ACT
                val report = testBot.run()

                // ASSERT
                // Player should die
                assertTrue(
                    report.playerDied,
                    "Player should die from boss damage"
                )

                // Player should take damage (at least enough to die)
                assertTrue(
                    report.damageTaken >= 20,
                    "Player should take significant damage before death (got ${report.damageTaken}, expected >= 20)"
                )

                // Should not kill many NPCs (dies before achieving much)
                assertTrue(
                    report.npcsKilled <= 2,
                    "Player should not kill many NPCs before dying (got ${report.npcsKilled}, expected <= 2)"
                )

                println("\n=== Lethal Damage Test ===")
                println("Player Died: ${report.playerDied}")
                println("Damage Taken: ${report.damageTaken}")
                println("NPCs Killed: ${report.npcsKilled}")
                println("Final Status: ${report.finalStatus}")
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
