package com.jcraw.mud.testbot.scenarios

import com.jcraw.mud.memory.MemoryManager
import com.jcraw.mud.reasoning.CombatNarrator
import com.jcraw.mud.reasoning.NPCInteractionGenerator
import com.jcraw.mud.reasoning.RoomDescriptionGenerator
import com.jcraw.mud.reasoning.procedural.ProceduralDungeonBuilder
import com.jcraw.mud.testbot.*
import com.jcraw.sophia.llm.OpenAIClient
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * E2E test suite that runs all playthrough scenarios to validate game balance.
 *
 * Runs all three playthrough tests:
 * 1. Bad Playthrough - Validates game is challenging (player dies)
 * 2. Brute Force Playthrough - Validates game is beatable with gear
 * 3. Smart Playthrough - Validates multiple solution paths exist
 *
 * This comprehensive test validates:
 * - Game difficulty is properly tuned
 * - Multiple winning strategies are viable
 * - Death mechanics work correctly
 * - All core systems integrate properly
 * - Game balance across different playstyles
 *
 * Replaces: test_all_playthroughs.sh
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AllPlaythroughsTest {

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
    @DisplayName("All Playthroughs Validation")
    inner class AllPlaythroughsValidation {

        @Test
        @DisplayName("All three playthrough scenarios complete successfully")
        fun `all playthrough scenarios pass validation`() = runBlocking {
            // ARRANGE: Run all three scenarios with different strategies
            val dungeonSize = 5
            val reports = mutableListOf<Triple<String, TestReport, TestScenario>>()

            // Test 1: Bad Playthrough (should die)
            val badReport = runPlaythrough(
                scenarioName = "Bad Playthrough",
                dungeonSeed = 10001,
                dungeonSize = dungeonSize,
                scenario = TestScenario.BadPlaythrough(maxSteps = 30)
            )
            reports.add(Triple("Bad", badReport, TestScenario.BadPlaythrough(maxSteps = 30)))

            // Test 2: Brute Force Playthrough (should win with gear)
            val bruteReport = runPlaythrough(
                scenarioName = "Brute Force Playthrough",
                dungeonSeed = 10002,
                dungeonSize = dungeonSize,
                scenario = TestScenario.BruteForcePlaythrough(maxSteps = 50)
            )
            reports.add(Triple("Brute Force", bruteReport, TestScenario.BruteForcePlaythrough(maxSteps = 50)))

            // Test 3: Smart Playthrough (should win with social skills)
            val smartReport = runPlaythrough(
                scenarioName = "Smart Playthrough",
                dungeonSeed = 10003,
                dungeonSize = dungeonSize,
                scenario = TestScenario.SmartPlaythrough(maxSteps = 50)
            )
            reports.add(Triple("Smart", smartReport, TestScenario.SmartPlaythrough(maxSteps = 50)))

            // ASSERT: Validate all scenarios completed successfully
            reports.forEach { (name, report, _) ->
                assertEquals(
                    TestStatus.PASSED,
                    report.finalStatus,
                    "$name scenario should complete with PASSED status"
                )
            }

            // Validate game balance expectations
            validateGameBalance(reports)

            // Print summary
            printTestSummary(reports)
        }

        @Test
        @DisplayName("Game balance validation - bad player dies, good players win")
        fun `game balance is properly tuned`() = runBlocking {
            // ARRANGE: Run scenarios with focus on outcomes
            val badReport = runPlaythrough(
                scenarioName = "Bad",
                dungeonSeed = 20001,
                dungeonSize = 5,
                scenario = TestScenario.BadPlaythrough(maxSteps = 30)
            )

            val bruteReport = runPlaythrough(
                scenarioName = "Brute Force",
                dungeonSeed = 20002,
                dungeonSize = 5,
                scenario = TestScenario.BruteForcePlaythrough(maxSteps = 50)
            )

            // ASSERT: Validate game balance
            // Bad player should die (game is challenging)
            assertTrue(
                badReport.playerDied,
                "Bad playthrough should result in player death (validates difficulty)"
            )

            // Brute force player should survive (game is beatable)
            assertFalse(
                bruteReport.playerDied,
                "Brute force playthrough should result in player survival (validates beatable with gear)"
            )

            // Brute force player should defeat enemies
            assertTrue(
                bruteReport.npcsKilled > 0,
                "Brute force player should defeat NPCs (got ${bruteReport.npcsKilled})"
            )

            println("\n=== Game Balance Validation ===")
            println("✓ Game is challenging: Bad player died")
            println("✓ Game is beatable: Brute force player survived with ${bruteReport.npcsKilled} kills")
            println("Bad player damage taken: ${badReport.damageTaken}")
            println("Brute force player damage taken: ${bruteReport.damageTaken}")
        }

        @Test
        @DisplayName("Multiple solution paths exist and are viable")
        fun `multiple winning strategies work`() = runBlocking {
            // ARRANGE: Run winning strategies
            val bruteReport = runPlaythrough(
                scenarioName = "Brute Force",
                dungeonSeed = 30001,
                dungeonSize = 5,
                scenario = TestScenario.BruteForcePlaythrough(maxSteps = 50)
            )

            val smartReport = runPlaythrough(
                scenarioName = "Smart",
                dungeonSeed = 30002,
                dungeonSize = 5,
                scenario = TestScenario.SmartPlaythrough(maxSteps = 50)
            )

            // ASSERT: Both strategies should succeed
            assertEquals(
                TestStatus.PASSED,
                bruteReport.finalStatus,
                "Brute force strategy should succeed"
            )

            assertEquals(
                TestStatus.PASSED,
                smartReport.finalStatus,
                "Smart strategy should succeed"
            )

            // Validate different approaches
            // Brute force should have more combat
            assertTrue(
                bruteReport.combatRounds > 0,
                "Brute force should engage in combat (got ${bruteReport.combatRounds} rounds)"
            )

            // Smart playthrough should minimize combat (but may have some due to dice rolls)
            assertTrue(
                smartReport.combatRounds <= bruteReport.combatRounds + 10,
                "Smart playthrough should have similar or less combat than brute force " +
                    "(smart: ${smartReport.combatRounds}, brute: ${bruteReport.combatRounds})"
            )

            println("\n=== Multiple Solution Paths Validation ===")
            println("✓ Brute force strategy: ${bruteReport.finalStatus} with ${bruteReport.combatRounds} combat rounds")
            println("✓ Smart strategy: ${smartReport.finalStatus} with ${smartReport.combatRounds} combat rounds")
            println("✓ Multiple winning strategies validated")
        }
    }

    /**
     * Run a single playthrough scenario.
     */
    private suspend fun runPlaythrough(
        scenarioName: String,
        dungeonSeed: Int,
        dungeonSize: Int,
        scenario: TestScenario
    ): TestReport {
        // Create dungeon
        val worldState = ProceduralDungeonBuilder.generateCrypt(
            roomCount = dungeonSize,
            seed = dungeonSeed.toLong()
        )

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

        // Create test bot
        val testBot = TestBotRunner(
            llmClient = llmClient,
            gameEngine = gameEngine,
            scenario = scenario
        )

        return try {
            println("\n========================================")
            println("  Running: $scenarioName")
            println("========================================")
            testBot.run()
        } finally {
            llmClient.close()
        }
    }

    /**
     * Validate game balance across all scenarios.
     */
    private fun validateGameBalance(reports: List<Triple<String, TestReport, TestScenario>>) {
        val (badName, badReport, _) = reports[0]
        val (bruteName, bruteReport, _) = reports[1]
        val (smartName, smartReport, _) = reports[2]

        // Bad playthrough should result in death
        assertTrue(
            badReport.playerDied,
            "$badName: Player should die (validates game difficulty)"
        )

        // Brute force should survive with gear
        assertFalse(
            bruteReport.playerDied,
            "$bruteName: Player should survive with proper gear"
        )

        // Smart playthrough should succeed (may or may not die depending on dice rolls)
        assertEquals(
            TestStatus.PASSED,
            smartReport.finalStatus,
            "$smartName: Should complete successfully"
        )

        // Brute force should kill enemies
        assertTrue(
            bruteReport.npcsKilled > 0,
            "$bruteName: Should defeat at least one NPC (got ${bruteReport.npcsKilled})"
        )

        // Smart playthrough should minimize combat (but may have some)
        assertTrue(
            smartReport.combatRounds <= bruteReport.combatRounds + 10,
            "$smartName: Should have less or similar combat than brute force " +
                "(smart: ${smartReport.combatRounds}, brute: ${bruteReport.combatRounds})"
        )
    }

    /**
     * Print comprehensive test summary.
     */
    private fun printTestSummary(reports: List<Triple<String, TestReport, TestScenario>>) {
        println("\n========================================")
        println("  All Tests Complete!")
        println("========================================")
        println()

        reports.forEach { (name, report, _) ->
            val passRate = if (report.totalSteps > 0) {
                (report.passedSteps.toDouble() / report.totalSteps * 100).toInt()
            } else {
                0
            }

            println("--- $name ---")
            println("  Status: ${report.finalStatus}")
            println("  Steps: ${report.totalSteps} (${report.passedSteps} passed, $passRate%)")
            println("  Player Died: ${report.playerDied}")
            println("  Combat Rounds: ${report.combatRounds}")
            println("  NPCs Killed: ${report.npcsKilled}")
            println("  Damage Taken: ${report.damageTaken}")
            println("  Rooms Visited: ${report.uniqueRoomsVisited}")
            println("  Duration: ${report.duration / 1000.0}s")
            println()
        }

        println("Game Balance Validation:")
        println("  ✓ Game is challenging (bad player dies)")
        println("  ✓ Game is beatable (good player wins)")
        println("  ✓ Multiple solution paths exist")
        println()
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
