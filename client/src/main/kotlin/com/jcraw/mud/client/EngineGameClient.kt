package com.jcraw.mud.client

import com.jcraw.mud.client.handlers.*
import com.jcraw.mud.core.*
import com.jcraw.mud.client.SpaceEntitySupport
import com.jcraw.mud.perception.Intent
import com.jcraw.mud.perception.IntentRecognizer
import com.jcraw.mud.reasoning.*
import com.jcraw.mud.reasoning.procedural.ProceduralDungeonBuilder
import com.jcraw.mud.reasoning.procedural.DungeonTheme
import com.jcraw.mud.reasoning.procedural.QuestGenerator
import com.jcraw.mud.reasoning.death.PlayerRespawnService
import com.jcraw.mud.memory.MemoryManager
import com.jcraw.mud.memory.PersistenceManager
import com.jcraw.mud.memory.social.SocialDatabase
import com.jcraw.mud.memory.social.SqliteSocialComponentRepository
import com.jcraw.mud.memory.social.SqliteSocialEventRepository
import com.jcraw.mud.memory.item.ItemDatabase
import com.jcraw.mud.memory.item.SQLiteItemRepository
import com.jcraw.sophia.llm.OpenAIClient
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Real game client implementation that wraps the actual game engine.
 * Integrates with the existing MudGame logic but exposes it through the GameClient interface.
 */
class EngineGameClient(
    private val apiKey: String? = null,
    private val dungeonTheme: DungeonTheme = DungeonTheme.CRYPT,
    private val roomCount: Int = 10
) : GameClient {

    private val _events = MutableSharedFlow<GameEvent>(replay = 10)
    internal var worldState: WorldState
    internal var running = true
    private var respawnState: RespawnState? = null
    internal var lastConversationNpcId: String? = null

    // Engine components
    private val llmClient: OpenAIClient?
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
    private val questTracker: QuestTracker

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
    internal val itemTemplateCache: MutableMap<String, ItemTemplate> = loadItemTemplateCache()

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
    private val spacePopulationService: com.jcraw.mud.reasoning.world.SpacePopulationService
    private val worldStateSeeder: com.jcraw.mud.reasoning.world.WorldStateSeeder?
    private val playerRespawnService: PlayerRespawnService
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

        // Generate and add quests
        val initialQuests = questGenerator.generateQuestPool(worldState, dungeonTheme, count = 3)
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
            handleRespawnInput(text)
            return
        }

        if (!running) return

        // Parse intent - V3 uses space-based context
        val space = worldState.getCurrentSpace()
        val spaceContext = space?.description
        val exitsWithNames = worldState.getCurrentGraphNode()?.let { buildExitsWithNames(it) }
        val intent = runBlocking {
            intentRecognizer.parseIntent(text, spaceContext, exitsWithNames)
        }

        // Process intent
        processIntent(intent)
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

    private fun loadItemTemplateCache(): MutableMap<String, ItemTemplate> {
        val existing = itemRepository.findAllTemplates().getOrElse { emptyMap() }
        if (existing.isNotEmpty()) {
            return existing.toMutableMap()
        }

        val templates = loadTemplatesFromResource()
        if (templates.isNotEmpty()) {
            itemRepository.saveTemplates(templates).onFailure {
                emitEvent(
                    GameEvent.System(
                        "Warning: failed to seed item templates (${it.message})",
                        GameEvent.MessageLevel.WARNING
                    )
                )
            }
        }
        return templates.associateBy { it.id }.toMutableMap()
    }

    private fun loadTemplatesFromResource(): List<ItemTemplate> {
        val resourceCandidates = listOf(
            "item_templates.json",
            "memory/src/main/resources/item_templates.json"
        )

        for (resource in resourceCandidates) {
            val jsonText = readResourceText(resource)
            if (jsonText != null) {
                return runCatching {
                    itemJson.decodeFromString<List<ItemTemplate>>(jsonText)
                }.getOrElse { emptyList() }
            }
        }

        return emptyList()
    }

    private fun readResourceText(path: String): String? {
        val classLoaderStream = EngineGameClient::class.java.classLoader.getResourceAsStream(path)
        if (classLoaderStream != null) {
            return classLoaderStream.bufferedReader().use { it.readText() }
        }

        val filePath = Paths.get(path)
        return if (Files.exists(filePath)) {
            Files.readString(filePath)
        } else {
            null
        }
    }

    internal fun getItemTemplate(templateId: String): ItemTemplate {
        return itemTemplateCache[templateId]
            ?: itemRepository.findTemplateById(templateId).getOrNull()?.also {
                itemTemplateCache[templateId] = it
            }
            ?: createFallbackTemplate(templateId)
    }

    private fun createFallbackTemplate(templateId: String): ItemTemplate {
        val displayName = templateId.split('_').joinToString(" ") { token ->
            token.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase() else ch.toString() }
        }.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase() else ch.toString() }

        val fallback = ItemTemplate(
            id = templateId,
            name = displayName,
            type = ItemType.MISC,
            tags = emptyList(),
            properties = mapOf("value" to "10"),
            rarity = Rarity.COMMON,
            description = "A $displayName."
        )
        itemTemplateCache[templateId] = fallback
        return fallback
    }

    internal fun loadSpace(spaceId: String): SpacePropertiesComponent? = runBlocking {
        spacePropertiesRepository.findByChunkId(spaceId)
    }.getOrNull()?.also { loaded ->
        worldState = worldState.updateSpace(spaceId, loaded)
    }

    internal fun loadEntity(entityId: String): Entity? = runBlocking {
        spaceEntityRepository.findById(entityId)
    }.getOrNull()

    internal fun currentSpace(): SpacePropertiesComponent? =
        loadSpace(worldState.player.currentRoomId)

    internal fun ensureSpaceContent(spaceId: String) {
        val generator = worldGenerator ?: return
        val currentSpace = worldState.getSpace(spaceId) ?: return
        if (currentSpace.description.isNotEmpty() && !currentSpace.descriptionStale) return
        val node = ensureGraphNodeLoaded(spaceId) ?: return

        val chunk = worldState.getChunk(node.chunkId) ?: runBlocking {
            worldChunkRepository.findById(node.chunkId).getOrNull()
        }?.also { loaded ->
            worldState = worldState.addChunk(node.chunkId, loaded)
        } ?: return

        val filledSpace = runBlocking {
            generator.fillSpaceContent(currentSpace, node, chunk)
        }.getOrElse {
            emitEvent(
                GameEvent.System(
                    "Failed to describe $spaceId: ${it.message}",
                    GameEvent.MessageLevel.WARNING
                )
            )
            return
        }

        worldState = worldState.updateSpace(spaceId, filledSpace.withDescription(filledSpace.description))
        spacePropertiesRepository.save(filledSpace, spaceId).onFailure {
            emitEvent(
                GameEvent.System(
                    "Failed to persist description for $spaceId: ${it.message}",
                    GameEvent.MessageLevel.WARNING
                )
            )
        }
    }

    private fun populateSpaceIfNeeded(spaceId: String) {
        val space = worldState.getSpace(spaceId) ?: return
        if (space.stateFlags["populated"] == true) return
        if (space.entities.isNotEmpty()) return
        if (space.isSafeZone) return

        val node = ensureGraphNodeLoaded(spaceId) ?: return
        val chunk = worldState.getChunk(node.chunkId) ?: worldChunkRepository.findById(node.chunkId).getOrElse {
            emitEvent(
                GameEvent.System(
                    "Failed to load chunk ${node.chunkId}: ${it.message}",
                    GameEvent.MessageLevel.WARNING
                )
            )
            null
        } ?: return

        val populationResult = runBlocking {
            spacePopulationService.populateSpace(spaceId, space, chunk)
        }

        populationResult.onSuccess { (populatedSpace, spawnedEntities) ->
            val flaggedSpace = populatedSpace.copy(
                stateFlags = populatedSpace.stateFlags + ("populated" to true)
            )

            var updatedWorld = worldState.updateSpace(spaceId, flaggedSpace)
            spawnedEntities.forEach { entity ->
                spaceEntityRepository.save(entity).onFailure {
                    emitEvent(
                        GameEvent.System(
                            "Failed to persist entity ${entity.id}: ${it.message}",
                            GameEvent.MessageLevel.WARNING
                        )
                    )
                }
                updatedWorld = updatedWorld.updateEntity(entity)
            }

            spacePropertiesRepository.save(flaggedSpace, spaceId).onFailure {
                emitEvent(
                    GameEvent.System(
                        "Failed to persist space $spaceId: ${it.message}",
                        GameEvent.MessageLevel.WARNING
                    )
                )
            }

            worldState = updatedWorld
        }.onFailure { error ->
            emitEvent(
                GameEvent.System(
                    "Population failed for $spaceId: ${error.message}",
                    GameEvent.MessageLevel.WARNING
                )
            )
        }
    }

    internal fun describeCurrentRoom() {
        // V3-only: Use space-based description
        val currentRoomId = worldState.player.currentRoomId
        ensureSpaceContent(currentRoomId)
        populateSpaceIfNeeded(currentRoomId)
        val space = worldState.getCurrentSpace()

        if (space == null) {
            emitEvent(GameEvent.System("Error: No current space", GameEvent.MessageLevel.ERROR))
            return
        }

        describeWorldV2Space(space, currentRoomId)
    }

    /**
     * Describes a World V2 procedurally generated space.
     */
    private fun describeWorldV2Space(space: com.jcraw.mud.core.SpacePropertiesComponent, spaceId: String) {
        val narrativeText = buildString {
            appendLine("\n${space.description}")
            if (space.isTreasureRoom) {
                appendTreasureRoomStatus(this, spaceId)
            }

            // V3: Show exits from graph, not from space.exits (LLM-generated exits are unreliable)
            val graphNode = worldState.getGraphNode(spaceId)
            if (graphNode != null) {
                val visibleEdges = graphNode.getVisibleEdges(worldState.player.revealedExits)
                if (visibleEdges.isNotEmpty()) {
                    appendLine("\nExits:")
                    visibleEdges.forEach { edge ->
                        appendLine("  - ${edge.direction}")
                    }
                }
            }

            // Show entities
            if (space.entities.isNotEmpty()) {
                appendLine("\nYou see:")
                space.entities.forEach { entityId ->
                    val entity = loadEntity(entityId)
                    val name = when (entity) {
                        is Entity.NPC -> entity.name
                        else -> SpaceEntitySupport.getStub(entityId).displayName
                    }
                    appendLine("  - $name")
                }
            }

            // Show resources
            if (space.resources.isNotEmpty()) {
                appendLine("\nResources:")
                space.resources.forEach { resource ->
                    val desc = resource.description.ifBlank { resource.templateId }
                    appendLine("  - $desc (quantity ${resource.quantity})")
                }
            }

            // Show dropped items
            if (space.itemsDropped.isNotEmpty()) {
                appendLine("\nItems on the ground:")
                space.itemsDropped.forEach { item ->
                    appendLine("  - ${item.templateId} (x${item.quantity})")
                }
            }
        }

        emitEvent(GameEvent.Narrative(narrativeText))

        // Update status
        emitEvent(GameEvent.StatusUpdate(
            hp = worldState.player.health,
            maxHp = worldState.player.maxHealth,
            location = worldState.getSpace(spaceId)?.name ?: spaceId
        ))
    }

    private fun appendTreasureRoomStatus(builder: StringBuilder, spaceId: String) {
        val treasureRoom = worldState.getTreasureRoom(spaceId)
        builder.appendLine()
        val takenItem = treasureRoom?.currentlyTakenItem
        when {
            treasureRoom == null -> builder.appendLine("(Treasure room data failed to load.)")
            treasureRoom.hasBeenLooted -> builder.appendLine("Only dust-coated pedestals remain; the room has been looted.")
            takenItem == null -> builder.appendLine("Five pedestals hum with magic. Claim one treasure via 'examine pedestals'—the others will seal away.")
            else -> {
                val itemName = itemRepository.findTemplateById(takenItem)
                    .getOrNull()
                    ?.name
                    ?: takenItem
                builder.appendLine("The other treasures are sealed while you hold the $itemName. Return it to swap your choice.")
            }
        }
    }

    internal fun handlePlayerMovement(movementLabel: String, treasureExitMessage: String? = null) {
        emitEvent(GameEvent.Narrative("You move $movementLabel."))
        treasureExitMessage?.let { emitEvent(GameEvent.Narrative(it)) }
        val currentSpaceId = worldState.player.currentRoomId
        ensureSpaceContent(currentSpaceId)
        worldState.getCurrentGraphNode()?.let { maybeExpandFrontier(it) }
        trackQuests(QuestAction.VisitedRoom(currentSpaceId))
        describeCurrentRoom()
    }

    internal fun ensureGraphNodeLoaded(spaceId: String): GraphNodeComponent? {
        worldState.getGraphNode(spaceId)?.let { return it }
        val node = runBlocking { graphNodeRepository.findById(spaceId).getOrNull() } ?: return null
        worldState = worldState.updateGraphNode(spaceId, node)
        return node
    }

    private fun maybeExpandFrontier(currentNode: GraphNodeComponent) {
        if (currentNode.type !is com.jcraw.mud.core.world.NodeType.Frontier) return
        val chunkId = currentNode.chunkId
        val chunk = worldState.getChunk(chunkId) ?: runBlocking {
            worldChunkRepository.findById(chunkId).getOrNull()
        }?.also { worldState = worldState.addChunk(chunkId, it) } ?: return

        val hasGeneratedExit = currentNode.neighbors.any { edge ->
            worldState.getGraphNode(edge.targetId) != null
        }
        if (hasGeneratedExit) return

        val generator = worldGenerator ?: return
        runBlocking {
            val context = com.jcraw.mud.core.world.GenerationContext(
                seed = (chunkId.hashCode().toLong() + System.currentTimeMillis()).toString(),
                globalLore = chunk.lore,
                parentChunk = chunk,
                parentChunkId = chunk.parentId,
                level = com.jcraw.mud.core.world.ChunkLevel.SUBZONE,
                direction = "frontier_expansion"
            )

            val result = generator.generateChunk(context)
            result.onSuccess { genResult ->
                worldChunkRepository.save(genResult.chunk, genResult.chunkId)
                worldState = worldState.addChunk(genResult.chunkId, genResult.chunk)

                genResult.graphNodes.forEach { node ->
                    graphNodeRepository.save(node)
                    worldState = worldState.updateGraphNode(node.id, node)

                    val spaceStub = generator.generateSpaceStub(node, genResult.chunk)
                    spaceStub.onSuccess { space ->
                        spacePropertiesRepository.save(space, node.id)
                        worldState = worldState.updateSpace(node.id, space)
                    }.onFailure {
                        emitEvent(
                            GameEvent.System(
                                "Failed to seed space ${node.id}: ${it.message}",
                                GameEvent.MessageLevel.WARNING
                            )
                        )
                    }
                }

                val hubNode = genResult.graphNodes.find { it.type is com.jcraw.mud.core.world.NodeType.Hub }
                if (hubNode != null) {
                    val edgeDirection = "frontier passage"
                    val newEdge = com.jcraw.mud.core.world.EdgeData(
                        targetId = hubNode.id,
                        direction = edgeDirection,
                        hidden = false
                    )

                    val updatedFrontier = currentNode.copy(neighbors = currentNode.neighbors + newEdge)
                    graphNodeRepository.update(updatedFrontier)
                    worldState = worldState.updateGraphNode(updatedFrontier.id, updatedFrontier)
                    emitEvent(GameEvent.System("A new horizon opens beyond the frontier.", GameEvent.MessageLevel.INFO))
                }
            }.onFailure { error ->
                emitEvent(
                    GameEvent.System(
                        "Frontier generation failed: ${error.message}",
                        GameEvent.MessageLevel.WARNING
                    )
                )
            }
        }
    }

    private fun buildExitsWithNames(node: GraphNodeComponent): Map<Direction, String> =
        node.neighbors.mapNotNull { edge ->
            val direction = Direction.fromString(edge.direction) ?: return@mapNotNull null
            val targetName = worldState.getSpace(edge.targetId)?.name
                ?: loadSpace(edge.targetId)?.name
                ?: edge.targetId
            direction to targetName
        }.toMap()

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
            is Intent.Take -> {
                // Check if in treasure room and item matches a pedestal item
                val spaceId = worldState.player.currentRoomId
                val treasureRoom = worldState.getTreasureRoom(spaceId)
                if (treasureRoom != null && !treasureRoom.hasBeenLooted) {
                    // Check if target matches any pedestal item (by template ID or item name)
                    val matchesPedestal = treasureRoom.pedestals.any { pedestal ->
                        val template = itemTemplateCache[pedestal.itemTemplateId]
                        val itemName = template?.name ?: ""
                        val templateId = pedestal.itemTemplateId

                        itemName.lowercase().contains(intent.target.lowercase()) ||
                        templateId.lowercase().contains(intent.target.lowercase())
                    }

                    if (matchesPedestal) {
                        // Route to treasure handler
                        ClientTreasureRoomHandlers.handleTakeTreasure(this, intent.target)
                    } else {
                        // Regular floor item
                        ClientItemHandlers.handleTake(this, intent.target)
                    }
                } else {
                    // Not in treasure room or already looted - regular take
                    ClientItemHandlers.handleTake(this, intent.target)
                }
            }
            is Intent.TakeAll -> ClientItemHandlers.handleTakeAll(this)
            is Intent.Drop -> ClientItemHandlers.handleDrop(this, intent.target)
            is Intent.Give -> ClientItemHandlers.handleGive(this, intent.itemTarget, intent.npcTarget)
            is Intent.TakeTreasure -> ClientTreasureRoomHandlers.handleTakeTreasure(this, intent.itemTarget)
            is Intent.ReturnTreasure -> ClientTreasureRoomHandlers.handleReturnTreasure(this, intent.itemTarget)
            is Intent.ExaminePedestal -> ClientTreasureRoomHandlers.handleExaminePedestal(this, intent.target)
            is Intent.Talk -> ClientSocialHandlers.handleTalk(this, intent.target)
            is Intent.Say -> ClientSocialHandlers.handleSay(this, intent.message, intent.npcTarget)
            is Intent.Attack -> ClientCombatHandlers.handleAttack(this, intent.target)
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
    }

    internal fun trackQuests(action: QuestAction) {
        val (updatedPlayer, updatedWorld) = questTracker.updateQuestsAfterAction(
            worldState.player,
            worldState,
            action
        )

        // Check if any quest objectives were completed
        if (updatedPlayer != worldState.player) {
            updatedPlayer.activeQuests.forEach { quest ->
                val oldQuest = worldState.player.getQuest(quest.id)
                if (oldQuest != null) {
                    // Check for newly completed objectives
                    quest.objectives.zip(oldQuest.objectives).forEach { (newObj, oldObj) ->
                        if (newObj.isCompleted && !oldObj.isCompleted) {
                            emitEvent(GameEvent.Quest("\n✓ Quest objective completed: ${newObj.description}"))
                        }
                    }

                    // Check if quest just completed
                    if (quest.status == QuestStatus.COMPLETED &&
                        oldQuest.status == QuestStatus.ACTIVE) {
                        emitEvent(GameEvent.Quest("\n🎉 Quest completed: ${quest.title}\nUse 'claim ${quest.id}' to collect your reward!"))
                    }
                }
            }

            // Update both player and world state (world may have NPC disposition changes)
            worldState = updatedWorld.updatePlayer(updatedPlayer)
        }
    }

    internal fun handlePlayerDeath() {
        if (respawnState != null) return

        val pending = playerRespawnService.createPendingRespawn(
            worldState = worldState,
            playerId = worldState.player.id,
            spawnSpaceIdOverride = worldState.gameProperties["starting_space"]
        ).getOrElse { error ->
            emitEvent(
                GameEvent.System(
                    "Failed to process permadeath: ${error.message}",
                    GameEvent.MessageLevel.ERROR
                )
            )
            running = false
            return
        }

        respawnState = RespawnState.AwaitingConfirmation(pending)
        emitEvent(GameEvent.Combat("\n${pending.deathResult.narration}"))
        emitEvent(GameEvent.System("Continue as new character? (Y/N)", GameEvent.MessageLevel.INFO))
    }

    private fun handleRespawnInput(rawInput: String) {
        val state = respawnState ?: return
        val input = rawInput.trim()

        when (state) {
            is RespawnState.AwaitingConfirmation -> {
                when (input.lowercase()) {
                    "y", "yes" -> {
                        respawnState = RespawnState.AwaitingName(state.pending)
                        emitEvent(GameEvent.System("Name your new character:", GameEvent.MessageLevel.INFO))
                    }
                    "n", "no" -> {
                        emitEvent(GameEvent.System("You accept your fate. Game over.", GameEvent.MessageLevel.INFO))
                        respawnState = null
                        running = false
                    }
                    else -> emitEvent(GameEvent.System("Please answer Y or N.", GameEvent.MessageLevel.WARNING))
                }
            }
            is RespawnState.AwaitingName -> {
                if (input.isBlank()) {
                    emitEvent(GameEvent.System("Name cannot be blank.", GameEvent.MessageLevel.WARNING))
                    return
                }

                val outcome = playerRespawnService.completeRespawn(worldState, state.pending, input)
                outcome.onFailure { error ->
                    emitEvent(GameEvent.System("Failed to respawn: ${error.message}", GameEvent.MessageLevel.ERROR))
                }.onSuccess { result ->
                    worldState = result.worldState
                    respawnState = null
                    lastConversationNpcId = null
                    emitEvent(GameEvent.System(result.respawnMessage))
                    emitEvent(
                        GameEvent.StatusUpdate(
                            hp = worldState.player.health,
                            maxHp = worldState.player.maxHealth,
                            location = worldState.player.currentRoomId
                        )
                    )
                    describeCurrentRoom()
                }
            }
        }
    }

    private sealed interface RespawnState {
        data class AwaitingConfirmation(val pending: PlayerRespawnService.PendingRespawn) : RespawnState
        data class AwaitingName(val pending: PlayerRespawnService.PendingRespawn) : RespawnState
    }
}
