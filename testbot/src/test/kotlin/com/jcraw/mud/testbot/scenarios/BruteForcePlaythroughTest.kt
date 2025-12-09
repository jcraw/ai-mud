package com.jcraw.mud.testbot.scenarios

import com.jcraw.app.MudGame
import com.jcraw.app.RealGameEngineAdapter

// SampleDungeon replaced with V3TestWorldHelper
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * E2E scenario test for Brute Force Playthrough.
 *
 * Tests the complete gameplay loop where the player:
 * - Explores all rooms
 * - Collects best gear (weapons and armor)
 * - Defeats the boss through superior equipment
 *
 * This test validates:
 * - Procedural dungeon generation
 * - Equipment collection and equipping
 * - Combat with equipment bonuses
 * - Boss defeat mechanics
 * - LLM-powered decision making
 *
 * Replaces: test_brute_force_playthrough.sh
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BruteForcePlaythroughTest {

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
    @DisplayName("Brute Force Playthrough Scenario")
    inner class BruteForceScenario {

        @Test
        @DisplayName("Bot completes brute force playthrough by collecting gear and defeating boss")
        fun `bot completes brute force playthrough successfully`() = runBlocking {
            // ARRANGE: Create SampleDungeon (designed for gear collection testing)
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
            val scenario = TestScenario.BruteForcePlaythrough(
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
                    "Brute force playthrough should complete with PASSED status"
                )

                // 2. Player should defeat at least one NPC (the boss or other enemies)
                assertTrue(
                    report.npcsKilled > 0,
                    "Player should defeat at least one NPC during playthrough (got ${report.npcsKilled})"
                )

                // 3. Player should NOT die (should win with proper gear)
                assertFalse(
                    report.playerDied,
                    "Player should survive and win with proper gear collection"
                )

                // 4. Player should visit multiple rooms (exploration)
                assertTrue(
                    report.uniqueRoomsVisited >= 3,
                    "Player should explore at least 3 rooms (got ${report.uniqueRoomsVisited})"
                )

                // 5. Pass rate should be high (>= 80%)
                val passRate = if (report.totalSteps > 0) {
                    report.passedSteps.toDouble() / report.totalSteps
                } else {
                    0.0
                }
                assertTrue(
                    passRate >= 0.8,
                    "Pass rate should be >= 80% (got ${(passRate * 100).toInt()}%)"
                )

                // Log results for debugging
                println("\n=== Brute Force Playthrough Test Results ===")
                println("Status: ${report.finalStatus}")
                println("Steps: ${report.totalSteps} (${report.passedSteps} passed)")
                println("Pass Rate: ${(passRate * 100).toInt()}%")
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
        @DisplayName("Bot explores multiple rooms during brute force playthrough")
        fun `bot explores multiple rooms looking for gear`() = runBlocking {
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

            val scenario = TestScenario.BruteForcePlaythrough(maxSteps = 50)
            val testBot = TestBotRunner(llmClient, gameEngine, scenario)

            try {
                // ACT
                val report = testBot.run()

                // ASSERT
                // Bot should explore at least 3 unique rooms
                assertTrue(
                    report.uniqueRoomsVisited >= 3,
                    "Bot should explore at least 3 rooms during brute force playthrough (got ${report.uniqueRoomsVisited})"
                )

                // Each room should have a non-empty name
                report.roomNames.forEach { roomName ->
                    assertTrue(
                        roomName.isNotBlank(),
                        "All visited rooms should have valid names"
                    )
                }

                println("\n=== Room Exploration Test ===")
                println("Rooms Visited: ${report.uniqueRoomsVisited}")
                println("Room Names: ${report.roomNames.joinToString(", ")}")
            } finally {
                llmClient.close()
            }
        }

        @Test
        @DisplayName("Bot takes damage but survives with proper gear")
        fun `bot takes damage but survives with equipment`() = runBlocking {
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

            val scenario = TestScenario.BruteForcePlaythrough(maxSteps = 50)
            val testBot = TestBotRunner(llmClient, gameEngine, scenario)

            try {
                // ACT
                val report = testBot.run()

                // ASSERT
                // Player should survive (not die)
                assertFalse(
                    report.playerDied,
                    "Player should survive brute force playthrough with proper gear"
                )

                // Note: Damage taken may be 0 if player defeats enemies quickly
                // or avoids combat, so we don't assert on damage taken amount

                println("\n=== Survival Test ===")
                println("Player Died: ${report.playerDied}")
                println("Damage Taken: ${report.damageTaken}")
                println("NPCs Killed: ${report.npcsKilled}")
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
