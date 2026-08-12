@file:Suppress("LongParameterList")

package com.jcraw.app

import com.jcraw.mud.core.Direction
import com.jcraw.mud.core.WorldState
import com.jcraw.mud.core.GraphNodeComponent
import com.jcraw.mud.core.SpacePropertiesComponent
import com.jcraw.mud.perception.Intent
import com.jcraw.mud.perception.IntentRecognizer
import com.jcraw.mud.reasoning.RoomDescriptionGenerator
import com.jcraw.mud.reasoning.SceneryDescriptionGenerator
import com.jcraw.mud.reasoning.NPCInteractionGenerator
import com.jcraw.mud.reasoning.CombatResolver
import com.jcraw.mud.reasoning.CombatNarrator
import com.jcraw.mud.reasoning.SkillCheckResolver
import com.jcraw.mud.reasoning.QuestTracker
import com.jcraw.mud.reasoning.QuestAction
import com.jcraw.mud.reasoning.combat.TurnQueueManager
import com.jcraw.mud.reasoning.combat.MonsterAIHandler
import com.jcraw.mud.reasoning.combat.AttackResolver
import com.jcraw.mud.reasoning.combat.SkillClassifier
import com.jcraw.mud.reasoning.combat.ActionCosts
import com.jcraw.mud.reasoning.combat.DeathHandler
import com.jcraw.mud.reasoning.combat.CorpseDecayManager
import com.jcraw.mud.reasoning.death.PlayerRespawnService
import com.jcraw.mud.memory.MemoryManager
import com.jcraw.mud.memory.PersistenceManager
import com.jcraw.mud.memory.social.SocialDatabase
import com.jcraw.mud.memory.social.SqliteSocialComponentRepository
import com.jcraw.mud.memory.social.SqliteSocialEventRepository
import com.jcraw.mud.reasoning.DispositionManager
import com.jcraw.mud.memory.item.ItemDatabase
import com.jcraw.mud.memory.item.SQLiteItemRepository
import com.jcraw.mud.reasoning.loot.LootGenerator
import com.jcraw.sophia.llm.OpenAIClient
import kotlinx.coroutines.runBlocking

/**
 * Core MUD game engine - handles game loop, state management, and intent processing.
 *
 * This class orchestrates all game systems and dispatches player actions to appropriate handlers.
 */
class MudGame(
    internal val initialWorldState: WorldState,
    internal val descriptionGenerator: RoomDescriptionGenerator? = null,
    internal val npcInteractionGenerator: NPCInteractionGenerator? = null,
    internal val combatNarrator: CombatNarrator? = null,
    private val memoryManager: MemoryManager? = null,
    private val llmClient: OpenAIClient? = null,
    private val skillDbPath: String = com.jcraw.mud.core.DatabaseConfig.SKILLS_DB
) {
    internal var worldState: WorldState = initialWorldState
    internal var running = true
    internal var respawnState: MudGameDeathRespawn.RespawnState? = null
    internal val combatResolver = CombatResolver()
    internal val skillCheckResolver = SkillCheckResolver()
    internal val persistenceManager = PersistenceManager()
    internal val intentRecognizer = IntentRecognizer(llmClient)
    internal val sceneryGenerator = SceneryDescriptionGenerator(llmClient)
    internal var lastConversationNpcId: String? = null

    // Combat System V2 components
    internal val turnQueue: TurnQueueManager? = if (llmClient != null) TurnQueueManager() else null
    internal val monsterAIHandler: MonsterAIHandler? = if (llmClient != null) MonsterAIHandler(llmClient) else null
    // SkillClassifier has fallback classification when LLM is unavailable
    private val skillClassifier = SkillClassifier(llmClient)
    internal val attackResolver = AttackResolver(skillClassifier)
    internal val llmService = llmClient

    // Item System V2 components
    private val itemDatabase = ItemDatabase(com.jcraw.mud.core.DatabaseConfig.ITEMS_DB)
    internal val itemRepository = SQLiteItemRepository(itemDatabase)
    internal val recipeRepository = com.jcraw.mud.memory.item.SQLiteRecipeRepository(itemDatabase)
    private val lootGenerator = LootGenerator(itemRepository)
    internal val deathHandler = DeathHandler(lootGenerator)
    internal val corpseDecayManager = CorpseDecayManager()

    init {
        // Load item templates from JSON on first startup
        com.jcraw.mud.memory.item.ItemTemplateLoader.loadTemplatesFromResource(itemRepository)
    }

    // Social system components
    private val socialDatabase = SocialDatabase(com.jcraw.mud.core.DatabaseConfig.SOCIAL_DB)
    private val socialComponentRepo = SqliteSocialComponentRepository(socialDatabase)
    private val socialEventRepo = SqliteSocialEventRepository(socialDatabase)
    private val knowledgeRepo = com.jcraw.mud.memory.social.SqliteKnowledgeRepository(socialDatabase)
    internal val dispositionManager = DispositionManager(socialComponentRepo, socialEventRepo)
    internal val emoteHandler = com.jcraw.mud.reasoning.EmoteHandler(dispositionManager)
    internal val npcKnowledgeManager = com.jcraw.mud.reasoning.NPCKnowledgeManager(knowledgeRepo, socialComponentRepo, llmClient)
    internal val questTracker = QuestTracker(dispositionManager)

    // Skill system components
    private val skillDatabase = com.jcraw.mud.memory.skill.SkillDatabase(skillDbPath)
    private val skillRepo = com.jcraw.mud.memory.skill.SQLiteSkillRepository(skillDatabase)
    private val skillComponentRepo = com.jcraw.mud.memory.skill.SQLiteSkillComponentRepository(skillDatabase)
    internal val skillManager = com.jcraw.mud.reasoning.skill.SkillManager(skillRepo, skillComponentRepo, memoryManager)
    internal val perkSelector = com.jcraw.mud.reasoning.skill.PerkSelector(skillComponentRepo, memoryManager)

    // World Generation V2 components
    private val worldDatabase = com.jcraw.mud.memory.world.WorldDatabase(com.jcraw.mud.core.DatabaseConfig.WORLD_DB)
    private val worldSeedRepository = com.jcraw.mud.memory.world.SQLiteWorldSeedRepository(worldDatabase)
    internal val worldChunkRepository = com.jcraw.mud.memory.world.SQLiteWorldChunkRepository(worldDatabase)
    internal val spacePropertiesRepository = com.jcraw.mud.memory.world.SQLiteSpacePropertiesRepository(worldDatabase)
    internal val graphNodeRepository = com.jcraw.mud.memory.world.SQLiteGraphNodeRepository(worldDatabase)
    internal val exitResolver = if (llmClient != null) com.jcraw.mud.reasoning.world.ExitResolver(llmClient) else null
    internal val movementCostCalculator = com.jcraw.mud.reasoning.world.MovementCostCalculator()
    internal var navigationState: com.jcraw.mud.core.world.NavigationState? = null
    internal val worldPersistence = com.jcraw.mud.memory.world.WorldPersistence(
        worldSeedRepository,
        worldChunkRepository,
        spacePropertiesRepository
    )

    // Respawn & population components
    internal val respawnRepository = com.jcraw.mud.memory.world.SQLiteRespawnRepository(worldDatabase)
    internal val mobSpawner = com.jcraw.mud.reasoning.world.MobSpawner(llmClient)
    private val trapGenerator = com.jcraw.mud.reasoning.world.TrapGenerator(llmClient)
    private val resourceGenerator = com.jcraw.mud.reasoning.world.ResourceGenerator(itemRepository, llmClient)
    internal val spacePopulator = com.jcraw.mud.reasoning.world.SpacePopulator(trapGenerator, resourceGenerator, mobSpawner)
    internal val respawnChecker = com.jcraw.mud.reasoning.world.RespawnChecker(respawnRepository, mobSpawner)
    internal val spacePopulationService = com.jcraw.mud.reasoning.world.SpacePopulationService(spacePopulator, respawnChecker)

    // Treasure Room System components (after worldDatabase initialization)
    internal val treasureRoomRepository = com.jcraw.mud.memory.world.SQLiteTreasureRoomRepository(worldDatabase)
    internal val treasureRoomHandler = com.jcraw.mud.reasoning.treasureroom.TreasureRoomHandler(itemRepository)

    // Death & Corpse System components (Chunk 6)
    internal val corpseRepository = com.jcraw.mud.memory.world.SQLiteCorpseRepository(worldDatabase)
    internal val playerRespawnService = PlayerRespawnService(corpseRepository, treasureRoomRepository)
    // TODO: Add corpse decay scheduler when integrated
    // internal val corpseDecayScheduler = CorpseDecayScheduler(corpseRepository)

    // Victory System components (Chunk 7)
    internal val victoryHandlers = com.jcraw.app.handlers.VictoryHandlers()
    internal val bossCombatEnhancements = com.jcraw.mud.reasoning.boss.BossCombatEnhancements()
    internal val bossSummonedTracker = mutableSetOf<String>() // Track which bosses have already summoned

    // World System V3 components (graph-based navigation)
    private val loreInheritanceEngine = if (llmClient != null) {
        com.jcraw.mud.reasoning.world.LoreInheritanceEngine(llmClient)
    } else null
    private val graphGenerator = com.jcraw.mud.reasoning.worldgen.GraphGenerator(
        rng = kotlin.random.Random.Default,
        difficultyLevel = 1 // Default difficulty, can be adjusted per chunk
    )
    private val graphValidator = com.jcraw.mud.reasoning.worldgen.GraphValidator()
    internal val worldGenerator = if (llmClient != null && loreInheritanceEngine != null) {
        com.jcraw.mud.reasoning.world.WorldGenerator(
            llmClient = llmClient,
            loreEngine = loreInheritanceEngine,
            graphGenerator = graphGenerator,
            graphValidator = graphValidator,
            memoryManager = memoryManager
        )
    } else null

    /**
     * Start the main game loop.
     */
    fun start() {
        printWelcome()
        describeCurrentRoom()

        while (running) {
            print("\n> ")
            val input = readLine()?.trim() ?: continue

            if (input.isBlank()) continue

            respawnState?.let {
                MudGameDeathRespawn.handleRespawnInput(this, input)
                continue
            }

            val space = worldState.getCurrentSpace()
            val spaceContext = space?.let {
                val desc = if (it.description.isNotBlank()) it.description else "Unexplored area"
                "${it.name}: $desc"
            }
            val exitsWithNames = worldState.getCurrentGraphNode()?.let { buildExitsWithNames(it) }
            val intent = runBlocking {
                intentRecognizer.parseIntent(input, spaceContext, exitsWithNames)
            }
            processIntent(intent)

            // Advance game time after player action (Combat V2)
            // Calculate action cost based on intent type and player's Speed skill
            val speedLevel = skillManager.getSkillComponent(worldState.player.id)?.getEffectiveLevel("Speed") ?: 0
            val baseCost = getBaseCostForIntent(intent)
            val actionCost = ActionCosts.calculateCost(baseCost, speedLevel)
            worldState = worldState.advanceTime(actionCost)

            // Process NPC turns after player action (Combat V2)
            // Pause NPC actions while waiting on permadeath prompts
            if (respawnState == null) {
                processNPCTurns()
            }
        }

        println("\nThanks for playing!")
    }

    /** Print welcome message. */
    internal fun printWelcome() = MudGameRoomDescribe.printWelcome(this)

    /** Describe the current room, including combat status, exits, and entities. */
    internal fun describeCurrentRoom() = MudGameRoomDescribe.describeCurrentRoom(this)

    /** Generate a space description using LLM or fallback heuristics. */
    internal fun generateRoomDescription(
        space: SpacePropertiesComponent,
        spaceId: String? = null
    ): String = MudGameRoomDescribe.generateRoomDescription(this, space, spaceId)

    /**
     * Process a parsed intent by dispatching to appropriate handler.
     */
    internal fun processIntent(intent: Intent) {
        when (intent) {
            is Intent.Move -> com.jcraw.app.handlers.MovementHandlers.handleMove(this, intent.direction)
            is Intent.Scout -> com.jcraw.app.handlers.MovementHandlers.handleScout(this, intent.direction)
            is Intent.Travel -> com.jcraw.app.handlers.MovementHandlers.handleTravel(this, intent.direction)
            is Intent.Look -> com.jcraw.app.handlers.MovementHandlers.handleLook(this, intent.target)
            is Intent.Search -> com.jcraw.app.handlers.MovementHandlers.handleSearch(this, intent.target)
            is Intent.Interact -> com.jcraw.app.handlers.SkillQuestHandlers.handleInteract(this, intent.target)
            is Intent.Craft -> com.jcraw.app.handlers.SkillQuestHandlers.handleCraft(this, intent.target)
            is Intent.Pickpocket -> when (intent.action) {
                "place" -> com.jcraw.app.handlers.PickpocketHandlers.handlePlace(
                    this, intent.npcTarget, intent.itemTarget ?: ""
                )
                else -> com.jcraw.app.handlers.PickpocketHandlers.handleSteal(
                    this, intent.action, intent.npcTarget, intent.itemTarget
                )
            }
            is Intent.Trade -> when (intent.action) {
                "list" -> com.jcraw.app.handlers.TradeHandlers.handleListStock(this, intent.merchantTarget)
                else -> com.jcraw.app.handlers.TradeHandlers.handleTrade(
                    this, intent.action, intent.target, intent.quantity, intent.merchantTarget
                )
            }
            is Intent.UseItem -> {
                val h = com.jcraw.mud.reasoning.items.ItemUseHandler(itemRepository)
                val (w, n) = com.jcraw.app.handlers.handleUseItem(
                    intent, worldState, worldState.player, h, itemRepository
                )
                worldState = w; println(n)
            }
            is Intent.Inventory -> com.jcraw.app.handlers.ItemHandlers.handleInventory(this)
            is Intent.Take -> com.jcraw.app.handlers.ItemHandlers.handleTake(this, intent.target)
            is Intent.TakeAll -> com.jcraw.app.handlers.ItemHandlers.handleTakeAll(this)
            is Intent.Drop -> com.jcraw.app.handlers.ItemHandlers.handleDrop(this, intent.target)
            is Intent.Give -> com.jcraw.app.handlers.ItemHandlers.handleGive(this, intent.itemTarget, intent.npcTarget)
            is Intent.TakeTreasure -> com.jcraw.app.handlers.TreasureRoomHandlers.handleTakeTreasure(this, intent.itemTarget)
            is Intent.ReturnTreasure -> com.jcraw.app.handlers.TreasureRoomHandlers.handleReturnTreasure(this, intent.itemTarget)
            is Intent.ExaminePedestal -> com.jcraw.app.handlers.TreasureRoomHandlers.handleExaminePedestal(this, intent.target)
            is Intent.Talk -> com.jcraw.app.handlers.SocialHandlers.handleTalk(this, intent.target)
            is Intent.Say -> com.jcraw.app.handlers.SocialHandlers.handleSay(this, intent.message, intent.npcTarget)
            is Intent.Attack -> com.jcraw.app.handlers.CombatHandlers.handleAttack(this, intent.target)
            is Intent.Flee -> com.jcraw.app.handlers.MovementHandlers.handleMove(this, intent.direction)
            is Intent.Equip -> com.jcraw.app.handlers.ItemHandlers.handleEquip(this, intent.target)
            is Intent.Use -> com.jcraw.app.handlers.ItemHandlers.handleUse(this, intent.target)
            is Intent.LootCorpse -> {
                val (w, n) = com.jcraw.app.handlers.handleLootCorpse(
                    intent, worldState, worldState.player, corpseRepository, itemRepository, worldState.gameTime
                )
                worldState = w; println(n)
            }
            is Intent.Check -> com.jcraw.app.handlers.SkillQuestHandlers.handleCheck(this, intent.target)
            is Intent.Persuade -> com.jcraw.app.handlers.SocialHandlers.handlePersuade(this, intent.target)
            is Intent.Intimidate -> com.jcraw.app.handlers.SocialHandlers.handleIntimidate(this, intent.target)
            is Intent.Emote -> com.jcraw.app.handlers.SocialHandlers.handleEmote(this, intent.emoteType, intent.target)
            is Intent.AskQuestion -> runBlocking {
                com.jcraw.app.handlers.SocialHandlers.handleAskQuestion(this@MudGame, intent.npcTarget, intent.topic)
            }
            is Intent.UseSkill -> com.jcraw.app.handlers.SkillQuestHandlers.handleUseSkill(this, intent.skill, intent.action)
            is Intent.TrainSkill -> com.jcraw.app.handlers.SkillQuestHandlers.handleTrainSkill(this, intent.skill, intent.method)
            is Intent.ChoosePerk -> com.jcraw.app.handlers.SkillQuestHandlers.handleChoosePerk(this, intent.skillName, intent.choice)
            is Intent.ViewSkills -> com.jcraw.app.handlers.SkillQuestHandlers.handleViewSkills(this)
            is Intent.Save -> com.jcraw.app.handlers.SkillQuestHandlers.handleSave(this, intent.saveName)
            is Intent.Load -> com.jcraw.app.handlers.SkillQuestHandlers.handleLoad(this, intent.saveName)
            is Intent.Quests -> com.jcraw.app.handlers.SkillQuestHandlers.handleQuests(this)
            is Intent.AcceptQuest -> com.jcraw.app.handlers.SkillQuestHandlers.handleAcceptQuest(this, intent.questId)
            is Intent.AbandonQuest -> com.jcraw.app.handlers.SkillQuestHandlers.handleAbandonQuest(this, intent.questId)
            is Intent.ClaimReward -> com.jcraw.app.handlers.SkillQuestHandlers.handleClaimReward(this, intent.questId)
            is Intent.Rest -> {
                val (w, n) = com.jcraw.app.handlers.handleRest(
                    intent, worldState, worldState.player, spacePropertiesRepository
                )
                worldState = w; println(n)
            }
            is Intent.Help -> com.jcraw.app.handlers.SkillQuestHandlers.handleHelp()
            is Intent.Quit -> com.jcraw.app.handlers.SkillQuestHandlers.handleQuit(this)
            is Intent.Invalid -> println(intent.message)
        }

        // Sync player max HP after every action (handles skill level-ups)
        MudGameQuestSupport.syncPlayerMaxHp(this)
    }

    /** Track quest progress after player actions. */
    internal fun trackQuests(action: QuestAction) = MudGameQuestSupport.trackQuests(this, action)

    /** Process NPC turns that are ready to act (Combat V2 integration). */
    internal fun processNPCTurns() = MudGameNpcCombat.processNPCTurns(this)

    /** Handle player death with permadeath mechanics. */
    internal fun handlePlayerDeath() = MudGameDeathRespawn.handlePlayerDeath(this)

    /** Build a map of exits with their destination names for navigation parsing. */
    internal fun buildExitsWithNames(node: GraphNodeComponent): Map<Direction, String> =
        MudGameRoomDescribe.buildExitsWithNames(this, node)

    /** Determine the base action cost for an intent type. */
    internal fun getBaseCostForIntent(intent: Intent): Int =
        MudGameNpcCombat.getBaseCostForIntent(intent)
}

/**
 * String repetition helper for formatting.
 */
internal operator fun String.times(n: Int): String = repeat(n)
