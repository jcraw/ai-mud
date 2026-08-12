@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList", "UnusedParameter", "TooGenericExceptionCaught")

package com.jcraw.mud.testbot

import com.jcraw.mud.core.GameEngineInterface
import com.jcraw.mud.core.WorldState
import com.jcraw.mud.core.Direction
import com.jcraw.mud.core.DatabaseConfig
import com.jcraw.mud.memory.MemoryManager
import com.jcraw.mud.memory.world.*
import com.jcraw.mud.memory.item.ItemDatabase
import com.jcraw.mud.memory.item.SQLiteItemRepository
import com.jcraw.mud.memory.item.ItemTemplateLoader
import com.jcraw.mud.memory.skill.SkillDatabase
import com.jcraw.mud.memory.skill.SQLiteSkillRepository
import com.jcraw.mud.memory.skill.SQLiteSkillComponentRepository
import com.jcraw.mud.perception.IntentRecognizer
import com.jcraw.mud.perception.Intent
import com.jcraw.mud.reasoning.world.*
import com.jcraw.mud.reasoning.skill.SkillManager
import com.jcraw.sophia.llm.OpenAIClient

/**
 * Test game engine for testbot that uses V3 world generation.
 * Implements GameEngineInterface to work with TestBotRunner.
 *
 * Handler bodies live in V3Test*Handlers extracts (MUD-034f).
 */
class V3TestGameEngine(
    private val ancientAbyssWorld: AncientAbyssWorld
) : GameEngineInterface {

    private val llmClient: OpenAIClient = ancientAbyssWorld.llmClient

    // Core components
    private val intentRecognizer = IntentRecognizer(llmClient)
    private val memoryManager = MemoryManager(llmClient)

    // Item system
    private val itemDatabase = ItemDatabase(DatabaseConfig.ITEMS_DB)
    private val itemRepository = SQLiteItemRepository(itemDatabase)

    init {
        ItemTemplateLoader.loadTemplatesFromResource(itemRepository)
    }

    // Skill system
    private val skillDatabase = SkillDatabase(DatabaseConfig.SKILLS_DB)
    private val skillRepo = SQLiteSkillRepository(skillDatabase)
    private val skillComponentRepo = SQLiteSkillComponentRepository(skillDatabase)
    private val skillManager = SkillManager(skillRepo, skillComponentRepo, memoryManager)

    // World system
    private val worldDatabase = ancientAbyssWorld.worldDatabase
    private val worldGenerator = ancientAbyssWorld.worldGenerator

    private val engineState = V3TestEngineState(
        worldState = ancientAbyssWorld.worldState,
        running = true,
        itemRepository = itemRepository,
        skillManager = skillManager
    )

    override suspend fun processInput(input: String): String {
        if (!engineState.running) return "Game has ended."

        val space = engineState.worldState.getCurrentSpace()
        val spaceContext = space?.let {
            val desc = if (it.description.isNotBlank()) it.description else "Unexplored area"
            "${it.name}: $desc"
        }
        val exitsWithNames = engineState.worldState.getCurrentGraphNode()?.let { buildExitsWithNames(it) }

        val intent = intentRecognizer.parseIntent(input, spaceContext, exitsWithNames)
        val result = processIntent(intent)

        // Advance game time by 1 tick
        engineState.worldState = engineState.worldState.advanceTime(1)

        return result
    }

    override fun getWorldState(): WorldState = engineState.worldState

    override fun reset() {
        engineState.worldState = ancientAbyssWorld.worldState
        engineState.running = true
    }

    override fun isRunning(): Boolean = engineState.running

    private fun buildExitsWithNames(node: com.jcraw.mud.core.GraphNodeComponent): Map<Direction, String> {
        val player = engineState.worldState.player
        return node.neighbors
            .filter { edge -> !edge.hidden || player.hasRevealedExit("${node.id}:${edge.targetId}") }
            .mapNotNull { edge ->
                val direction = Direction.fromString(edge.direction) ?: return@mapNotNull null
                val targetName = engineState.worldState.getSpace(edge.targetId)?.name ?: edge.targetId
                direction to targetName
            }
            .toMap()
    }

    private fun processIntent(intent: Intent): String {
        return when (intent) {
            is Intent.Move -> V3TestMoveHandlers.handleMove(engineState, intent.direction)
            is Intent.Look -> V3TestMoveHandlers.handleLook(engineState)
            is Intent.Inventory -> V3TestItemHandlers.handleInventory(engineState)
            is Intent.Take -> V3TestItemHandlers.handleTake(engineState, intent.target)
            is Intent.Drop -> V3TestItemHandlers.handleDrop(engineState, intent.target)
            is Intent.Talk -> V3TestSocialMetaHandlers.handleTalk(engineState, intent.target)
            is Intent.Attack -> V3TestCombatHandlers.handleAttack(engineState, intent.target)
            is Intent.Search -> V3TestSocialMetaHandlers.handleSearch(engineState)
            is Intent.Quests -> V3TestSocialMetaHandlers.handleViewQuests(engineState)
            is Intent.Rest -> V3TestSocialMetaHandlers.handleRest(engineState)
            is Intent.Help -> V3TestSocialMetaHandlers.handleHelp()
            is Intent.Quit -> V3TestSocialMetaHandlers.handleQuit(engineState)
            is Intent.Invalid -> intent.message
            else -> "Command not supported in test mode."
        }
    }
}
