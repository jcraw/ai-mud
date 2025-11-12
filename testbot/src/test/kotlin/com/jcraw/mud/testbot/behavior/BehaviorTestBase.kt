package com.jcraw.mud.testbot.behavior

import com.jcraw.mud.core.*
import com.jcraw.mud.memory.MemoryManager
import com.jcraw.mud.reasoning.*
import com.jcraw.mud.reasoning.skill.SkillManager
import com.jcraw.mud.testbot.InMemoryGameEngine
import com.jcraw.sophia.llm.LLMClient
import com.jcraw.sophia.llm.OpenAIClient
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach

/**
 * Base class for manual BDD-style behavior tests.
 *
 * Provides easy setup of InMemoryGameEngine with optional LLM integration.
 * Reuses all real game logic - no mocks unless you want them.
 *
 * Example usage:
 * ```kotlin
 * class BiomeBehaviorTest : BehaviorTestBase() {
 *     @Test
 *     fun `entering new biome has sensory details`() = runTest {
 *         given { playerInDungeon() }
 *         `when` { command("go north") }
 *         then {
 *             output shouldContain "sensory"
 *             output shouldHaveWordCount 50..100
 *         }
 *     }
 * }
 * ```
 */
abstract class BehaviorTestBase {

    protected lateinit var engine: InMemoryGameEngine
    protected var lastOutput: String = ""
    protected var apiKey: String? = null
    protected var llmClient: LLMClient? = null

    /**
     * Override to disable LLM requirement (tests run without API key).
     */
    protected open val requiresLLM: Boolean = false

    /**
     * Override to provide custom initial world state.
     */
    protected open fun createInitialWorldState(): WorldState {
        return SampleDungeon.createSampleDungeonV3()
    }

    @BeforeEach
    fun setup() {
        // Load API key if available
        apiKey = System.getenv("OPENAI_API_KEY")
            ?: System.getProperty("openai.api.key")
            ?: loadApiKeyFromLocalProperties()

        // Skip test if LLM required but no key available
        if (requiresLLM) {
            assumeTrue(
                apiKey != null && apiKey!!.isNotBlank(),
                "Skipping test: OPENAI_API_KEY not set. " +
                    "Set OPENAI_API_KEY environment variable or openai.api.key in local.properties."
            )
        }

        // Initialize LLM client if key available
        llmClient = if (apiKey != null && apiKey!!.isNotBlank()) {
            OpenAIClient(apiKey!!)
        } else {
            null
        }

        // Create engine with optional LLM components
        engine = createEngine()
    }

    protected open fun createEngine(): InMemoryGameEngine {
        val worldState = createInitialWorldState()

        return if (llmClient != null) {
            // Full engine with LLM components
            val memoryManager = MemoryManager(llmClient!!)
            val descriptionGenerator = RoomDescriptionGenerator(llmClient!!, memoryManager)
            val npcInteractionGenerator = NPCInteractionGenerator(llmClient!!, memoryManager)
            val combatNarrator = CombatNarrator(llmClient!!)
            val emoteHandler = EmoteHandler()
            val knowledgeManager = NPCKnowledgeManager(llmClient!!, memoryManager)
            val dispositionManager = DispositionManager()
            val skillManager = SkillManager()

            InMemoryGameEngine(
                initialWorldState = worldState,
                descriptionGenerator = descriptionGenerator,
                npcInteractionGenerator = npcInteractionGenerator,
                combatNarrator = combatNarrator,
                memoryManager = memoryManager,
                llmClient = llmClient,
                emoteHandler = emoteHandler,
                knowledgeManager = knowledgeManager,
                dispositionManager = dispositionManager,
                skillManager = skillManager
            )
        } else {
            // Fallback engine without LLM
            InMemoryGameEngine(
                initialWorldState = worldState,
                emoteHandler = EmoteHandler(),
                dispositionManager = DispositionManager(),
                skillManager = SkillManager()
            )
        }
    }

    // ====================
    // BDD-style helpers
    // ====================

    protected fun runTest(block: suspend BehaviorTestContext.() -> Unit) = runBlocking {
        val context = BehaviorTestContext(this@BehaviorTestBase)
        context.block()
    }

    protected suspend fun given(block: suspend BehaviorTestContext.() -> Unit) {
        val context = BehaviorTestContext(this)
        context.block()
    }

    protected suspend fun command(input: String): String {
        lastOutput = engine.processInput(input)
        return lastOutput
    }

    protected fun worldState(): WorldState = engine.getWorldState()

    protected fun reset() {
        engine.reset()
        lastOutput = ""
    }

    // ====================
    // Common setups
    // ====================

    protected fun playerInDungeon(): WorldState {
        return engine.getWorldState()
    }

    protected suspend fun movePlayerTo(direction: Direction): String {
        return command("go ${direction.displayName}")
    }

    protected suspend fun givePlayerItem(item: Entity.Item) {
        val state = engine.getWorldState()
        val updated = state.updatePlayer(state.player.addToInventory(item))
        // Note: Can't directly update engine state, need to use a trick
        // For now, drop and pick up
        val spaceId = state.player.currentRoomId
        val withItem = updated.addEntityToSpace(spaceId, item)
        // This is a limitation - we can't directly mutate engine state
        // Users should use command() to manipulate state
    }

    private fun loadApiKeyFromLocalProperties(): String? {
        return try {
            val file = java.io.File("local.properties")
            if (file.exists()) {
                val props = java.util.Properties()
                props.load(file.inputStream())
                props.getProperty("openai.api.key")
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Context for BDD-style test execution.
 * Provides clean DSL: given/when/then.
 */
class BehaviorTestContext(private val base: BehaviorTestBase) {

    suspend fun playerInDungeon() {
        base.playerInDungeon()
    }

    suspend fun command(input: String): String {
        return base.command(input)
    }

    fun worldState(): WorldState = base.worldState()

    val output: String get() = base.lastOutput

    // Alias for readability
    suspend fun `when`(block: suspend BehaviorTestContext.() -> Unit) {
        this.block()
    }

    suspend fun then(block: suspend BehaviorTestContext.() -> Unit) {
        this.block()
    }
}
