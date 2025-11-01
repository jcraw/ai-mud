package com.jcraw.mud.client

import com.jcraw.mud.client.handlers.*
import com.jcraw.mud.core.*
import com.jcraw.mud.perception.Intent
import com.jcraw.mud.perception.IntentRecognizer
import com.jcraw.mud.reasoning.*
import com.jcraw.mud.reasoning.procedural.ProceduralDungeonBuilder
import com.jcraw.mud.reasoning.procedural.DungeonTheme
import com.jcraw.mud.reasoning.procedural.QuestGenerator
import com.jcraw.mud.memory.MemoryManager
import com.jcraw.mud.memory.PersistenceManager
import com.jcraw.mud.memory.social.SocialDatabase
import com.jcraw.mud.memory.social.SqliteSocialComponentRepository
import com.jcraw.mud.memory.social.SqliteSocialEventRepository
import com.jcraw.sophia.llm.OpenAIClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking

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
    internal var navigationState: com.jcraw.mud.core.world.NavigationState? = null

    init {
        // Initialize social system components
        socialDatabase = SocialDatabase("client_social.db")
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

        dispositionManager = DispositionManager(socialComponentRepo, socialEventRepo)
        emoteHandler = EmoteHandler(dispositionManager)
        npcKnowledgeManager = NPCKnowledgeManager(knowledgeRepo, socialComponentRepo, llmClient)

        // Initialize skill system components
        skillDatabase = com.jcraw.mud.memory.skill.SkillDatabase("client_skills.db")
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
        worldDatabase = com.jcraw.mud.memory.world.WorldDatabase("client_world.db")
        worldSeedRepository = com.jcraw.mud.memory.world.SQLiteWorldSeedRepository(worldDatabase)
        worldChunkRepository = com.jcraw.mud.memory.world.SQLiteWorldChunkRepository(worldDatabase)
        spacePropertiesRepository = com.jcraw.mud.memory.world.SQLiteSpacePropertiesRepository(worldDatabase)

        // Initialize Ancient Abyss dungeon if LLM is available
        if (llmClient != null) {
            val loreEngine = com.jcraw.mud.reasoning.world.LoreInheritanceEngine(llmClient)
            val worldGenerator = com.jcraw.mud.reasoning.world.WorldGenerator(llmClient, loreEngine)
            val townGenerator = com.jcraw.mud.reasoning.world.TownGenerator(worldGenerator, worldChunkRepository, spacePropertiesRepository)
            val bossGenerator = com.jcraw.mud.reasoning.world.BossGenerator(worldGenerator, spacePropertiesRepository)
            val hiddenExitPlacer = com.jcraw.mud.reasoning.world.HiddenExitPlacer(worldGenerator, worldChunkRepository, spacePropertiesRepository)
            val dungeonInitializer = com.jcraw.mud.reasoning.world.DungeonInitializer(
                worldGenerator, worldSeedRepository, worldChunkRepository, spacePropertiesRepository,
                townGenerator, bossGenerator, hiddenExitPlacer
            )

            // Generate Ancient Abyss
            emitEvent(GameEvent.System("Generating the Ancient Abyss... This may take a moment."))
            val abyssData = runBlocking {
                dungeonInitializer.initializeAncientAbyss().getOrElse {
                    emitEvent(GameEvent.System("Failed to generate dungeon: ${it.message}", GameEvent.MessageLevel.ERROR))
                    throw it
                }
            }
            emitEvent(GameEvent.System("Ancient Abyss generated! Starting in the town..."))

            // Initialize navigation state
            navigationState = runBlocking {
                com.jcraw.mud.core.world.NavigationState.fromSpaceId(
                    abyssData.townSpaceId,
                    worldChunkRepository
                ).getOrThrow()
            }

            // Create minimal world state for World V2 (navigation uses repositories)
            val playerState = PlayerState(
                id = "player_ui",
                name = "Adventurer",
                currentRoomId = abyssData.townSpaceId, // Use town space ID
                health = 40,
                maxHealth = 40,
                stats = Stats(
                    strength = 10,
                    dexterity = 8,
                    constitution = 10,
                    intelligence = 9,
                    wisdom = 8,
                    charisma = 9
                )
            )

            worldState = WorldState(
                rooms = emptyMap(), // World V2 uses SpacePropertiesRepository
                players = mapOf(playerState.id to playerState)
            )
        } else {
            // Fallback to Sample Dungeon if no API key
            emitEvent(GameEvent.System("No API key provided - using Sample Dungeon fallback", GameEvent.MessageLevel.WARNING))
            val baseWorldState = SampleDungeon.createInitialWorldState()

            val playerState = PlayerState(
                id = "player_ui",
                name = "Adventurer",
                currentRoomId = baseWorldState.rooms.values.first().id,
                health = 40,
                maxHealth = 40,
                stats = Stats(
                    strength = 10,
                    dexterity = 8,
                    constitution = 10,
                    intelligence = 9,
                    wisdom = 8,
                    charisma = 9
                )
            )

            worldState = baseWorldState.addPlayer(playerState)
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
        if (!running || text.isBlank()) return

        // Parse intent
        val room = worldState.getCurrentRoom()
        val roomContext = room?.let { "${it.name}: ${it.traits.joinToString(", ")}" }
        val exitsWithNames = room?.let { buildExitsWithNames(it) }
        val intent = runBlocking {
            intentRecognizer.parseIntent(text, roomContext, exitsWithNames)
        }

        // Process intent
        processIntent(intent)
    }

    override fun observeEvents(): Flow<GameEvent> = _events.asSharedFlow()

    override fun getCurrentState(): PlayerState? = worldState.player

    override suspend fun close() {
        running = false
        llmClient?.close()
    }

    internal fun emitEvent(event: GameEvent) {
        runBlocking {
            _events.emit(event)
        }
    }

    /**
     * Build a map of exits with their destination room names for navigation parsing.
     */
    private fun buildExitsWithNames(room: Room): Map<Direction, String> {
        return room.exits.mapNotNull { (direction, roomId) ->
            val destRoom = worldState.rooms[roomId]
            if (destRoom != null) {
                direction to destRoom.name
            } else {
                null
            }
        }.toMap()
    }

    internal fun describeCurrentRoom() {
        val currentRoomId = worldState.player.currentRoomId

        // Check if player is in a World V2 space
        val space = runBlocking {
            spacePropertiesRepository.findByChunkId(currentRoomId).getOrNull()
        }

        if (space != null) {
            // World V2 space - use procedurally generated description
            describeWorldV2Space(space, currentRoomId)
        } else {
            // Legacy room - use traditional Room-based description
            val room = worldState.getCurrentRoom() ?: return

            // Generate room description
            val description = if (descriptionGenerator != null) {
                runBlocking { descriptionGenerator.generateDescription(room) }
            } else {
                room.traits.joinToString(". ") + "."
            }

            val roomNpcs = room.entities.filterIsInstance<Entity.NPC>()
            if (lastConversationNpcId != null && roomNpcs.none { it.id == lastConversationNpcId }) {
                lastConversationNpcId = null
            }

            val narrativeText = buildString {
                appendLine("\n${room.name}")
                appendLine("-".repeat(room.name.length))
                appendLine(description)

                if (room.exits.isNotEmpty()) {
                    appendLine("\nExits: ${room.exits.keys.joinToString(", ") { it.displayName }}")
                }

                if (room.entities.isNotEmpty()) {
                    appendLine("\nYou see:")
                    room.entities.forEach { entity ->
                        appendLine("  - ${entity.name}")
                    }
                }
            }

            emitEvent(GameEvent.Narrative(narrativeText))

            // Update status
            emitEvent(GameEvent.StatusUpdate(
                hp = worldState.player.health,
                maxHp = worldState.player.maxHealth,
                location = room.name
            ))
        }
    }

    /**
     * Describes a World V2 procedurally generated space.
     */
    private fun describeWorldV2Space(space: com.jcraw.mud.core.SpacePropertiesComponent, spaceId: String) {
        val narrativeText = buildString {
            appendLine("\n${space.description}")

            // Show visible exits
            if (space.exits.isNotEmpty()) {
                val visibleExits = space.exits.filter { !it.isHidden }
                if (visibleExits.isNotEmpty()) {
                    appendLine("\nExits:")
                    visibleExits.forEach { exit ->
                        appendLine("  - ${exit.direction}: ${exit.description}")
                    }
                }
            }

            // Show entities
            if (space.entities.isNotEmpty()) {
                appendLine("\nYou see:")
                space.entities.forEach { entityId ->
                    appendLine("  - $entityId") // TODO: Look up entity names
                }
            }

            // Show resources
            if (space.resources.isNotEmpty()) {
                appendLine("\nResources:")
                space.resources.forEach { resource ->
                    appendLine("  - ${resource.description}")
                }
            }

            // Show dropped items
            if (space.itemsDropped.isNotEmpty()) {
                appendLine("\nItems on the ground:")
                space.itemsDropped.forEach { item ->
                    appendLine("  - ${item.id}") // TODO: Look up item names
                }
            }
        }

        emitEvent(GameEvent.Narrative(narrativeText))

        // Update status
        emitEvent(GameEvent.StatusUpdate(
            hp = worldState.player.health,
            maxHp = worldState.player.maxHealth,
            location = spaceId // Use space ID as location
        ))
    }

    private suspend fun processIntent(intent: Intent) {
        when (intent) {
            is Intent.Move -> ClientMovementHandlers.handleMove(this, intent.direction)
            is Intent.Scout -> emitEvent(GameEvent.System("Scout not yet integrated with world generation system", GameEvent.MessageLevel.WARNING))
            is Intent.Travel -> emitEvent(GameEvent.System("Travel not yet integrated with world generation system", GameEvent.MessageLevel.WARNING))
            is Intent.Look -> ClientMovementHandlers.handleLook(this, intent.target)
            is Intent.Search -> ClientMovementHandlers.handleSearch(this, intent.target)
            is Intent.Interact -> ClientMovementHandlers.handleInteract(this, intent.target)
            is Intent.Craft -> emitEvent(GameEvent.System("Crafting not yet integrated", GameEvent.MessageLevel.WARNING))
            is Intent.Pickpocket -> emitEvent(GameEvent.System("Pickpocketing not yet integrated", GameEvent.MessageLevel.WARNING))
            is Intent.Trade -> emitEvent(GameEvent.System("Trading not yet integrated", GameEvent.MessageLevel.WARNING))
            is Intent.UseItem -> emitEvent(GameEvent.System("Advanced item use not yet integrated", GameEvent.MessageLevel.WARNING))
            is Intent.Inventory -> ClientItemHandlers.handleInventory(this)
            is Intent.Take -> ClientItemHandlers.handleTake(this, intent.target)
            is Intent.TakeAll -> ClientItemHandlers.handleTakeAll(this)
            is Intent.Drop -> ClientItemHandlers.handleDrop(this, intent.target)
            is Intent.Give -> ClientItemHandlers.handleGive(this, intent.itemTarget, intent.npcTarget)
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
}
