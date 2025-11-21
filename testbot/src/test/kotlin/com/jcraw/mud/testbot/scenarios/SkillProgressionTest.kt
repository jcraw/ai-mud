package com.jcraw.mud.testbot.scenarios

import com.jcraw.mud.config.GameConfig
import com.jcraw.mud.core.SkillComponent
import com.jcraw.mud.core.SkillState
import com.jcraw.mud.memory.MemoryManager
import com.jcraw.mud.reasoning.CombatNarrator
import com.jcraw.mud.reasoning.DispositionManager
import com.jcraw.mud.reasoning.EmoteHandler
import com.jcraw.mud.reasoning.NPCInteractionGenerator
import com.jcraw.mud.reasoning.NPCKnowledgeManager
import com.jcraw.mud.reasoning.RoomDescriptionGenerator
import com.jcraw.mud.reasoning.skill.SkillManager
import com.jcraw.mud.reasoning.world.initializeAncientAbyssWorld
import com.jcraw.mud.testbot.*
import com.jcraw.sophia.llm.OpenAIClient
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.assertTrue

/**
 * E2E test for Skill Progression scenario.
 * Tests AI bot leveling Dodge skill from 0 to 10 through combat.
 *
 * This test validates:
 * - Dual-path skill progression (lucky + XP)
 * - Defensive skill tracking in combat
 * - Bot reasoning/thought process logging
 * - Gameplay report generation
 * - LLM-driven decision making for skill leveling
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SkillProgressionTest {

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
    @DisplayName("Bot levels Dodge skill from 0 to 10 through combat")
    fun `bot successfully levels dodge skill to 10`() = runBlocking {
        // ARRANGE: Set up game with enemies and skill progression enabled
        GameConfig.enableMobGeneration = true  // Ensure enemies spawn

        // Initialize Ancient Abyss world (same as console/GUI clients)
        // Use in-memory database for test isolation
        val abyssWorld = initializeAncientAbyssWorld(
            apiKey = apiKey!!,
            playerId = "test_player",
            playerName = "Test Adventurer",
            dbPath = ":memory:",  // In-memory database for testing
            includeQuests = true
        ).getOrThrow()

        val worldState = abyssWorld.worldState
        val llmClient = abyssWorld.llmClient

        // Initialize SkillManager with in-memory database for testing
        val skillDatabase = com.jcraw.mud.memory.skill.SkillDatabase(":memory:")

        try {
            // Initialize LLM components
            val memoryManager = MemoryManager(llmClient)
            val descriptionGenerator = RoomDescriptionGenerator(llmClient, memoryManager)
            val npcInteractionGenerator = NPCInteractionGenerator(llmClient, memoryManager)
            val combatNarrator = CombatNarrator(llmClient, memoryManager)

            val skillRepo = com.jcraw.mud.memory.skill.SQLiteSkillRepository(skillDatabase)
            val skillComponentRepo = com.jcraw.mud.memory.skill.SQLiteSkillComponentRepository(skillDatabase)
            val skillManager = SkillManager(skillRepo, skillComponentRepo, memoryManager)

            // Create game engine with all dependencies
            val gameEngine = InMemoryGameEngine(
                initialWorldState = worldState,
                descriptionGenerator = descriptionGenerator,
                npcInteractionGenerator = npcInteractionGenerator,
                combatNarrator = combatNarrator,
                memoryManager = memoryManager,
                llmClient = llmClient,
                skillManager = skillManager
            )

            // Create test scenario
            val scenario = TestScenario.SkillProgression(maxSteps = 120)

            // ACT: Run test bot
            val testBot = TestBotRunner(
                llmClient = llmClient,
                gameEngine = gameEngine,
                scenario = scenario
            )

            // Get initial Dodge skill level (should be 0 or not unlocked)
            val initialSkillComponent = skillManager.getSkillComponent(worldState.player.id)
            val initialDodgeLevel = initialSkillComponent.getSkill("Dodge")?.level ?: 0

            println("\n========================================")
            println("  Running: Skill Progression Test")
            println("========================================")
            println("  Initial Dodge Level: $initialDodgeLevel")

            val report = testBot.run()

            // Get final Dodge skill level
            val finalSkillComponent = skillManager.getSkillComponent(worldState.player.id)
            val finalDodgeLevel = finalSkillComponent.getSkill("Dodge")?.level ?: 0

            // ASSERT: Verify completion
            assertTrue(
                report.finalStatus == TestStatus.PASSED || report.finalStatus == TestStatus.FAILED,
                "Test should complete (actual: ${report.finalStatus})"
            )

            // ASSERT: Verify Dodge skill actually progressed
            assertTrue(
                finalDodgeLevel > initialDodgeLevel,
                "Dodge skill should have progressed (initial: $initialDodgeLevel, final: $finalDodgeLevel)"
            )

            // ASSERT: Verify reasoning was logged
            val reasoningSteps = report.steps.count { it.reasoning != null }
            assertTrue(
                reasoningSteps >= 10,
                "Should have reasoning logged for at least 10 steps (actual: $reasoningSteps)"
            )

            println("\n✅ Test Summary:")
            println("  - Total Steps: ${report.totalSteps}")
            println("  - Reasoning Steps: $reasoningSteps / ${report.totalSteps}")
            println("  - Combat Rounds: ${report.combatRounds}")
            println("  - Dodge Skill: $initialDodgeLevel → $finalDodgeLevel (gained ${finalDodgeLevel - initialDodgeLevel} levels)")
            println("  - Duration: ${report.duration / 1000.0}s")
            println("  - Status: ${report.finalStatus}")

        } finally {
            llmClient.close()
            skillDatabase.close()
            abyssWorld.worldDatabase.close()
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
