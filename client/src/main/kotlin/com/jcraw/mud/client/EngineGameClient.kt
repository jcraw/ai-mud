package com.jcraw.mud.client

import com.jcraw.mud.client.handlers.*
import com.jcraw.mud.core.*
import com.jcraw.mud.perception.Intent
import com.jcraw.mud.perception.IntentRecognizer
import com.jcraw.mud.reasoning.*
import com.jcraw.mud.reasoning.procedural.QuestGenerator
import com.jcraw.mud.reasoning.procedural.DungeonTheme
import com.jcraw.mud.reasoning.death.PlayerRespawnService
import com.jcraw.mud.memory.MemoryManager
import com.jcraw.mud.memory.PersistenceManager
import com.jcraw.mud.memory.social.SocialDatabase
import com.jcraw.mud.memory.social.SqliteSocialComponentRepository
import com.jcraw.mud.memory.social.SqliteSocialEventRepository
import com.jcraw.mud.memory.item.ItemDatabase
import com.jcraw.mud.memory.item.SQLiteItemRepository
import com.jcraw.sophia.llm.OpenAIClient
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking

/**
 * Real game client implementation that wraps the actual game engine.
 * Integrates with the existing MudGame logic but exposes it through the GameClient interface.
 *
 * Heavy clusters live in Client* extract objects (MUD-034a); this stays the GameClient surface.
 */
class EngineGameClient(
    private val apiKey: String? = null
) : GameClient {

    private val _events = MutableSharedFlow<GameEvent>(replay = 10)
    internal var worldState: WorldState
    internal var running = true
    internal var respawnState: ClientQuestDeathSupport.RespawnState? = null
    internal var lastConversationNpcId: String? = null

    // Engine components
    internal val llmClient: OpenAIClient?
    internal val descriptionGenerator: RoomDescriptionGenerator?
    internal val npcInteractionGenerator: NPCInteractionGenerator?
    internal val combatNarrator: CombatNarrator?
    private val memoryManager: MemoryManager?
    internal val combatResolver: CombatResolver
    internal val skillCheckResolver: SkillCheckResolver
    internal val persistenceManager: PersistenceManager
    private val intentRecognizer: IntentRecognizer
    internal val sceneryGenerator: SceneryDescriptionGenerator
    private val questGenerator: QuestGenerator
    internal val questTracker: QuestTracker

    // Combat System V2 components
    internal val turnQueue: com.jcraw.mud.reasoning.combat.TurnQueueManager?
    internal val monsterAIHandler: com.jcraw.mud.reasoning.combat.MonsterAIHandler?
    private val skillClassifier: com.jcraw.mud.reasoning.combat.SkillClassifier?
    internal val attackResolver: com.jcraw.mud.reasoning.combat.AttackResolver?
    internal val deathHandler: com.jcraw.mud.reasoning.combat.DeathHandler

    // Item system components
    private val itemJson = Json { ignoreUnknownKeys = true }
    private val itemDatabase = ItemDatabase(DatabaseConfig.ITEMS_DB)
    internal val itemRepository = SQLiteItemRepository(itemDatabase)
    internal val recipeRepository = com.jcraw.mud.memory.item.SQLiteRecipeRepository(itemDatabase)
    internal val itemTemplateCache: MutableMap<String, ItemTemplate> = ClientItemTemplateCache.load(
        itemRepository = itemRepository,
        itemJson = itemJson,
        onWarning = { msg ->
            emitEvent(GameEvent.System(msg, GameEvent.MessageLevel.WARNING))
        }
    )

    // Social system components
    private val socialDatabase: SocialDatabase
    private val socialComponentRepo: SqliteSocialComponentRepository
    private val socialEventRepo: SqliteSocialEventRepository
    private val knowledgeRepo: com.jcraw.mud.memory.social.SqliteKnowledgeRepository
    internal val dispositionManager: DispositionManager
    internal val emoteHandler: EmoteHandler
    internal val npcKnowledgeManager: NPCKnowledgeManager

    // Skill system components
    private val skillDatabase: com.jcraw.mud.memory.skill.SkillDatabase
    private val skillRepo: com.jcraw.mud.memory.skill.SQLiteSkillRepository
    private val skillComponentRepo: com.jcraw.mud.memory.skill.SQLiteSkillComponentRepository
    internal val skillManager: com.jcraw.mud.reasoning.skill.SkillManager
    internal val perkSelector: com.jcraw.mud.reasoning.skill.PerkSelector

    // World Generation V2 components
    private val worldDatabase: com.jcraw.mud.memory.world.WorldDatabase
    private val worldSeedRepository: com.jcraw.mud.memory.world.SQLiteWorldSeedRepository
    internal val worldChunkRepository: com.jcraw.mud.memory.world.SQLiteWorldChunkRepository
    internal val spacePropertiesRepository: com.jcraw.mud.memory.world.SQLiteSpacePropertiesRepository
    internal val treasureRoomRepository: com.jcraw.mud.memory.world.SQLiteTreasureRoomRepository
    internal val spaceEntityRepository: com.jcraw.mud.memory.world.SQLiteSpaceEntityRepository
    private val corpseRepository: com.jcraw.mud.memory.world.SQLiteCorpseRepository
    internal val graphNodeRepository: com.jcraw.mud.memory.world.SQLiteGraphNodeRepository
    internal val exitLinker: com.jcraw.mud.reasoning.world.ExitLinker?
    internal val exitResolver: com.jcraw.mud.reasoning.world.ExitResolver?
    internal var navigationState: com.jcraw.mud.core.world.NavigationState? = null
    private val respawnRepository: com.jcraw.mud.memory.world.SQLiteRespawnRepository
    private val trapGenerator: com.jcraw.mud.reasoning.world.TrapGenerator
    private val resourceGenerator: com.jcraw.mud.reasoning.world.ResourceGenerator
    private val mobSpawner: com.jcraw.mud.reasoning.world.MobSpawner
    private val spacePopulator: com.jcraw.mud.reasoning.world.SpacePopulator
    private val respawnChecker: com.jcraw.mud.reasoning.world.RespawnChecker
    internal val spacePopulationService: com.jcraw.mud.reasoning.world.SpacePopulationService
    private val worldStateSeeder: com.jcraw.mud.reasoning.world.WorldStateSeeder?
    internal val playerRespawnService: PlayerRespawnService
    internal val treasureRoomHandler: com.jcraw.mud.reasoning.treasureroom.TreasureRoomHandler

    // World System V3 components (graph-based navigation)
    private val loreInheritanceEngine: com.jcraw.mud.reasoning.world.LoreInheritanceEngine?
    private val graphGenerator: com.jcraw.mud.reasoning.worldgen.GraphGenerator
    private val graphValidator: com.jcraw.mud.reasoning.worldgen.GraphValidator
    internal val worldGenerator: com.jcraw.mud.reasoning.world.WorldGenerator?

    init {
        // Initialize shared database configuration
        DatabaseConfig.init()

        // Load item templates from JSON on first startup
        com.jcraw.mud.memory.item.ItemTemplateLoader.loadTemplatesFromResource(itemRepository)

        // Initialize social system components
        socialDatabase = SocialDatabase(DatabaseConfig.SOCIAL_DB)
        socialComponentRepo = SqliteSocialComponentRepository(socialDatabase)
        socialEventRepo = SqliteSocialEventRepository(socialDatabase)
        knowledgeRepo = com.jcraw.mud.memory.social.SqliteKnowledgeRepository(socialDatabase)

        // Initialize LLM components if API key available
        llmClient = if (!apiKey.isNullOrBlank()) {
            OpenAIClient(apiKey)
        } else {
            null
        }

        memoryManager = llmClient?.let { MemoryManager(it) }
        descriptionGenerator = llmClient?.let { RoomDescriptionGenerator(it, memoryManager!!) }
        npcInteractionGenerator = llmClient?.let { NPCInteractionGenerator(it, memoryManager!!) }
        combatNarrator = llmClient?.let { CombatNarrator(it, memoryManager!!) }

        // Initialize Combat System V2 components
        turnQueue = if (llmClient != null) com.jcraw.mud.reasoning.combat.TurnQueueManager() else null
        monsterAIHandler = if (llmClient != null) com.jcraw.mud.reasoning.combat.MonsterAIHandler(llmClient) else null
        skillClassifier = if (llmClient != null) com.jcraw.mud.reasoning.combat.SkillClassifier(llmClient) else null
        attackResolver = if (skillClassifier != null) com.jcraw.mud.reasoning.combat.AttackResolver(skillClassifier!!) else null
        val lootGenerator = com.jcraw.mud.reasoning.loot.LootGenerator(itemRepository)
        deathHandler = com.jcraw.mud.reasoning.combat.DeathHandler(lootGenerator)

        dispositionManager = DispositionManager(socialComponentRepo, socialEventRepo)
        emoteHandler = EmoteHandler(dispositionManager)
        npcKnowledgeManager = NPCKnowledgeManager(knowledgeRepo, socialComponentRepo, llmClient)

        // Initialize skill system components
        skillDatabase = com.jcraw.mud.memory.skill.SkillDatabase(DatabaseConfig.SKILLS_DB)
        skillRepo = com.jcraw.mud.memory.skill.SQLiteSkillRepository(skillDatabase)
        skillComponentRepo = com.jcraw.mud.memory.skill.SQLiteSkillComponentRepository(skillDatabase)
        skillManager = com.jcraw.mud.reasoning.skill.SkillManager(skillRepo, skillComponentRepo, memoryManager)
        perkSelector = com.jcraw.mud.reasoning.skill.PerkSelector(skillComponentRepo, memoryManager)

        combatResolver = CombatResolver()
        skillCheckResolver = SkillCheckResolver()
        persistenceManager = PersistenceManager()
        intentRecognizer = IntentRecognizer(llmClient)
        sceneryGenerator = SceneryDescriptionGenerator(llmClient)
        questGenerator = QuestGenerator()
        questTracker = QuestTracker(dispositionManager)

        // Initialize World V2 components
        worldDatabase = com.jcraw.mud.memory.world.WorldDatabase(DatabaseConfig.WORLD_DB)
        worldSeedRepository = com.jcraw.mud.memory.world.SQLiteWorldSeedRepository(worldDatabase)
        worldChunkRepository = com.jcraw.mud.memory.world.SQLiteWorldChunkRepository(worldDatabase)
        spacePropertiesRepository = com.jcraw.mud.memory.world.SQLiteSpacePropertiesRepository(worldDatabase)
        treasureRoomRepository = com.jcraw.mud.memory.world.SQLiteTreasureRoomRepository(worldDatabase)
        spaceEntityRepository = com.jcraw.mud.memory.world.SQLiteSpaceEntityRepository(worldDatabase)
        corpseRepository = com.jcraw.mud.memory.world.SQLiteCorpseRepository(worldDatabase)
        graphNodeRepository = com.jcraw.mud.memory.world.SQLiteGraphNodeRepository(worldDatabase)
        respawnRepository = com.jcraw.mud.memory.world.SQLiteRespawnRepository(worldDatabase)
        trapGenerator = com.jcraw.mud.reasoning.world.TrapGenerator(llmClient)
        resourceGenerator = com.jcraw.mud.reasoning.world.ResourceGenerator(itemRepository, llmClient)
        mobSpawner = com.jcraw.mud.reasoning.world.MobSpawner(llmClient)
        spacePopulator = com.jcraw.mud.reasoning.world.SpacePopulator(trapGenerator, resourceGenerator, mobSpawner)
        respawnChecker = com.jcraw.mud.reasoning.world.RespawnChecker(respawnRepository, mobSpawner)
        spacePopulationService = com.jcraw.mud.reasoning.world.SpacePopulationService(spacePopulator, respawnChecker)
        playerRespawnService = PlayerRespawnService(corpseRepository, treasureRoomRepository)
        treasureRoomHandler = com.jcraw.mud.reasoning.treasureroom.TreasureRoomHandler(itemRepository)

        // Initialize World System V3 components
        loreInheritanceEngine = if (llmClient != null) {
            com.jcraw.mud.reasoning.world.LoreInheritanceEngine(llmClient)
        } else null
        graphGenerator = com.jcraw.mud.reasoning.worldgen.GraphGenerator(
            rng = kotlin.random.Random.Default,
            difficultyLevel = 1 // Default difficulty, can be adjusted per chunk
        )
        graphValidator = com.jcraw.mud.reasoning.worldgen.GraphValidator()
        worldGenerator = if (llmClient != null && loreInheritanceEngine != null) {
            com.jcraw.mud.reasoning.world.WorldGenerator(
                llmClient = llmClient,
                loreEngine = loreInheritanceEngine!!,
                graphGenerator = graphGenerator,
                graphValidator = graphValidator,
                memoryManager = memoryManager
            )
        } else null

        worldStateSeeder = worldGenerator?.let {
            com.jcraw.mud.reasoning.world.WorldStateSeeder(
                worldChunkRepository,
                graphNodeRepository,
                spacePropertiesRepository,
                treasureRoomRepository,
                spaceEntityRepository,
                it
            )
        }

        // Initialize Ancient Abyss dungeon if LLM is available
        if (llmClient != null && worldGenerator != null && worldStateSeeder != null) {
            exitLinker = com.jcraw.mud.reasoning.world.ExitLinker(worldGenerator, worldChunkRepository, spacePropertiesRepository)
            exitResolver = com.jcraw.mud.reasoning.world.ExitResolver(llmClient)
            val townGenerator = com.jcraw.mud.reasoning.world.TownGenerator(worldGenerator, worldChunkRepository, spacePropertiesRepository, spaceEntityRepository, treasureRoomRepository, graphNodeRepository)
            val bossGenerator = com.jcraw.mud.reasoning.world.BossGenerator(worldGenerator, spacePropertiesRepository)
            val hiddenExitPlacer = com.jcraw.mud.reasoning.world.HiddenExitPlacer(worldGenerator, worldChunkRepository, spacePropertiesRepository)
            val dungeonInitializer = com.jcraw.mud.reasoning.world.DungeonInitializer(
                worldGenerator, worldSeedRepository, worldChunkRepository, spacePropertiesRepository,
                townGenerator, bossGenerator, hiddenExitPlacer, graphNodeRepository, treasureRoomRepository
            )
            val abyssStarter = com.jcraw.mud.reasoning.world.AncientAbyssStarter(
                worldSeedRepository,
                worldChunkRepository,
                dungeonInitializer
            )
            val abyssStart = runBlocking {
                abyssStarter.ensureAncientAbyss().getOrElse {
                    emitEvent(
                        GameEvent.System(
                            "Failed to prepare Ancient Abyss: ${it.message}",
                            GameEvent.MessageLevel.ERROR
                        )
                    )
                    throw it
                }
            }

            navigationState = abyssStart.navigationState

            val playerState = PlayerState(
                id = "player_ui",
                name = "Adventurer",
                currentRoomId = abyssStart.startingSpaceId,
                health = 40,
                maxHealth = 40,
                stats = Stats(
                    strength = 10,
                    dexterity = 8,
                    constitution = 10,
                    intelligence = 9,
                    wisdom = 8,
                    charisma = 9
                ),
                inventoryComponent = InventoryComponent(
                    items = emptyList(),
                    equipped = emptyMap(),
                    gold = 0,
                    capacityWeight = 50.0
                )
            )

            var seededState = WorldState(
                players = mapOf(playerState.id to playerState)
            )
            seededState = seededState.copy(
                gameProperties = seededState.gameProperties + ("starting_space" to abyssStart.startingSpaceId)
            )
            worldState = worldStateSeeder.seedWorldState(
                seededState,
                abyssStart.startingSpaceId,
                onWarning = { message ->
                    emitEvent(
                        GameEvent.System(
                            message,
                            GameEvent.MessageLevel.WARNING
                        )
                    )
                },
                onError = { message ->
                    emitEvent(
                        GameEvent.System(
                            message,
                            GameEvent.MessageLevel.ERROR
                        )
                    )
                }
            )
        } else {
            // V3 requires API key for world generation
            exitLinker = null
            exitResolver = null
            throw IllegalArgumentException("API key required for GUI client - Ancient Abyss generation needs LLM")
        }

        // Generate and add quests (using CRYPT theme as default for V3)
        val initialQuests = questGenerator.generateQuestPool(worldState, DungeonTheme.CRYPT, count = 3)
        initialQuests.forEach { quest ->
            worldState = worldState.addAvailableQuest(quest)
        }

        // Send welcome message
        emitEvent(GameEvent.System("Welcome to the Ancient Abyss, ${worldState.player.name}!"))

        // Describe initial room
        describeCurrentRoom()
    }

    override suspend fun sendInput(text: String) {
        if (text.isBlank()) return

        respawnState?.let {
            ClientQuestDeathSupport.handleRespawnInput(this, text)
            return
        }

        if (!running) return

        // Parse intent - V3 uses space-based context
        val space = worldState.getCurrentSpace()
        val spaceContext = space?.description
        val exitsWithNames = worldState.getCurrentGraphNode()?.let {
            ClientSpaceContent.buildExitsWithNames(this, it)
        }
        val intent = runBlocking {
            intentRecognizer.parseIntent(text, spaceContext, exitsWithNames)
        }

        // Process intent
        processIntent(intent)

        // Advance game time after player action (Combat V2)
        val speedLevel = skillManager.getSkillComponent(worldState.player.id)?.getEffectiveLevel("Speed") ?: 0
        val baseCost = ClientNpcCombat.getBaseCostForIntent(intent)
        val actionCost = com.jcraw.mud.reasoning.combat.ActionCosts.calculateCost(baseCost, speedLevel)
        worldState = worldState.advanceTime(actionCost)

        // Process NPC turns after player action (Combat V2)
        ClientNpcCombat.processNPCTurns(this)
    }

    override fun observeEvents(): Flow<GameEvent> = _events.asSharedFlow()

    override fun getCurrentState(): PlayerState? = worldState.player

    override suspend fun close() {
        running = false
        llmClient?.close()
        itemDatabase.close()
    }

    internal fun emitEvent(event: GameEvent) {
        runBlocking {
            _events.emit(event)
        }
    }

    // --- Thin delegates to extract objects (handlers keep EngineGameClient API) ---

    internal fun getItemTemplate(templateId: String): ItemTemplate =
        ClientItemTemplateCache.getItemTemplate(templateId, itemTemplateCache, itemRepository)

    internal fun loadSpace(spaceId: String): SpacePropertiesComponent? =
        ClientSpaceContent.loadSpace(this, spaceId)

    internal fun loadEntity(entityId: String): Entity? =
        ClientSpaceContent.loadEntity(this, entityId)

    internal fun currentSpace(): SpacePropertiesComponent? =
        ClientSpaceContent.currentSpace(this)

    internal fun ensureSpaceContent(spaceId: String) =
        ClientSpaceContent.ensureSpaceContent(this, spaceId)

    internal fun describeCurrentRoom() =
        ClientSpaceContent.describeCurrentRoom(this)

    internal fun handlePlayerMovement(movementLabel: String, treasureExitMessage: String? = null) =
        ClientSpaceContent.handlePlayerMovement(this, movementLabel, treasureExitMessage)

    internal fun ensureGraphNodeLoaded(spaceId: String): GraphNodeComponent? =
        ClientSpaceContent.ensureGraphNodeLoaded(this, spaceId)

    internal fun trackQuests(action: QuestAction) =
        ClientQuestDeathSupport.trackQuests(this, action)

    internal fun handlePlayerDeath() =
        ClientQuestDeathSupport.handlePlayerDeath(this)

    /**
     * Intent when-dispatch — stays on facade (residual override) so global FN_E
     * is not applied to a new-file process() (MUD-034a optional router skipped).
     */
    private suspend fun processIntent(intent: Intent) {
        when (intent) {
            is Intent.Move -> ClientMovementHandlers.handleMove(this, intent.direction)
            is Intent.Scout -> ClientMovementHandlers.handleScout(this, intent.direction)
            is Intent.Travel -> ClientMovementHandlers.handleTravel(this, intent.direction)
            is Intent.Look -> ClientMovementHandlers.handleLook(this, intent.target)
            is Intent.Search -> ClientMovementHandlers.handleSearch(this, intent.target)
            is Intent.Interact -> ClientMovementHandlers.handleInteract(this, intent.target)
            is Intent.Craft -> emitEvent(GameEvent.System("Crafting not yet integrated", GameEvent.MessageLevel.WARNING))
            is Intent.Pickpocket -> emitEvent(GameEvent.System("Pickpocketing not yet integrated", GameEvent.MessageLevel.WARNING))
            is Intent.Trade -> ClientTradeHandlers.handleTrade(this, intent)
            is Intent.UseItem -> emitEvent(GameEvent.System("Advanced item use not yet integrated", GameEvent.MessageLevel.WARNING))
            is Intent.Inventory -> ClientItemHandlers.handleInventory(this)
            is Intent.Take -> dispatchTake(intent)
            is Intent.TakeAll -> ClientItemHandlers.handleTakeAll(this)
            is Intent.Drop -> ClientItemHandlers.handleDrop(this, intent.target)
            is Intent.Give -> ClientItemHandlers.handleGive(this, intent.itemTarget, intent.npcTarget)
            is Intent.TakeTreasure -> ClientTreasureRoomHandlers.handleTakeTreasure(this, intent.itemTarget)
            is Intent.ReturnTreasure -> ClientTreasureRoomHandlers.handleReturnTreasure(this, intent.itemTarget)
            is Intent.ExaminePedestal -> ClientTreasureRoomHandlers.handleExaminePedestal(this, intent.target)
            is Intent.Talk -> ClientSocialHandlers.handleTalk(this, intent.target)
            is Intent.Say -> ClientSocialHandlers.handleSay(this, intent.message, intent.npcTarget)
            is Intent.Attack -> ClientCombatHandlers.handleAttack(this, intent.target)
            is Intent.Flee -> ClientMovementHandlers.handleMove(this, intent.direction)
            is Intent.Equip -> ClientItemHandlers.handleEquip(this, intent.target)
            is Intent.Use -> ClientItemHandlers.handleUse(this, intent.target)
            is Intent.Check -> ClientSocialHandlers.handleCheck(this, intent.target)
            is Intent.Persuade -> ClientSocialHandlers.handlePersuade(this, intent.target)
            is Intent.Intimidate -> ClientSocialHandlers.handleIntimidate(this, intent.target)
            is Intent.Emote -> ClientSocialHandlers.handleEmote(this, intent.emoteType, intent.target)
            is Intent.AskQuestion -> ClientSocialHandlers.handleAskQuestion(this, intent.npcTarget, intent.topic)
            is Intent.UseSkill -> ClientSkillQuestHandlers.handleUseSkill(this, intent.skill, intent.action)
            is Intent.TrainSkill -> ClientSkillQuestHandlers.handleTrainSkill(this, intent.skill, intent.method)
            is Intent.ChoosePerk -> ClientSkillQuestHandlers.handleChoosePerk(this, intent.skillName, intent.choice)
            is Intent.ViewSkills -> ClientSkillQuestHandlers.handleViewSkills(this)
            is Intent.Save -> ClientSkillQuestHandlers.handleSave(this, intent.saveName)
            is Intent.Load -> ClientSkillQuestHandlers.handleLoad(this, intent.saveName)
            is Intent.Quests -> ClientSkillQuestHandlers.handleQuests(this)
            is Intent.AcceptQuest -> ClientSkillQuestHandlers.handleAcceptQuest(this, intent.questId)
            is Intent.AbandonQuest -> ClientSkillQuestHandlers.handleAbandonQuest(this, intent.questId)
            is Intent.ClaimReward -> ClientSkillQuestHandlers.handleClaimReward(this, intent.questId)
            is Intent.Help -> ClientSkillQuestHandlers.handleHelp(this)
            is Intent.Quit -> ClientSkillQuestHandlers.handleQuit(this)
            is Intent.Rest -> emitEvent(GameEvent.System("Rest not yet integrated", GameEvent.MessageLevel.WARNING))
            is Intent.LootCorpse -> emitEvent(GameEvent.System("Corpse looting not yet integrated", GameEvent.MessageLevel.WARNING))
            is Intent.Invalid -> emitEvent(GameEvent.System(intent.message, GameEvent.MessageLevel.WARNING))
        }
        ClientQuestDeathSupport.syncPlayerMaxHp(this)
    }

    private fun dispatchTake(intent: Intent.Take) {
        val spaceId = worldState.player.currentRoomId
        val treasureRoom = worldState.getTreasureRoom(spaceId)
        if (treasureRoom != null && !treasureRoom.hasBeenLooted) {
            val matchesPedestal = treasureRoom.pedestals.any { pedestal ->
                val template = itemTemplateCache[pedestal.itemTemplateId]
                val itemName = template?.name ?: ""
                val templateId = pedestal.itemTemplateId
                itemName.lowercase().contains(intent.target.lowercase()) ||
                    templateId.lowercase().contains(intent.target.lowercase())
            }
            if (matchesPedestal) {
                ClientTreasureRoomHandlers.handleTakeTreasure(this, intent.target)
            } else {
                ClientItemHandlers.handleTake(this, intent.target)
            }
        } else {
            ClientItemHandlers.handleTake(this, intent.target)
        }
    }
}
