package com.jcraw.app

import com.jcraw.mud.core.Direction
import com.jcraw.mud.core.WorldState
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.QuestStatus
import com.jcraw.mud.core.SpacePropertiesComponent
import com.jcraw.mud.core.GraphNodeComponent
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
import com.jcraw.mud.reasoning.combat.AttackResult
import com.jcraw.mud.reasoning.combat.SkillClassifier
import com.jcraw.mud.reasoning.combat.ActionCosts
import com.jcraw.mud.reasoning.combat.SpeedCalculator
import com.jcraw.mud.reasoning.combat.AIDecision
import com.jcraw.mud.reasoning.combat.DeathHandler
import com.jcraw.mud.reasoning.combat.CorpseDecayManager
import com.jcraw.mud.reasoning.death.PlayerRespawnService
import com.jcraw.mud.core.Component
import com.jcraw.mud.core.ComponentType
import com.jcraw.mud.core.CombatComponent
import com.jcraw.mud.core.SkillComponent
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
    private val descriptionGenerator: RoomDescriptionGenerator? = null,
    internal val npcInteractionGenerator: NPCInteractionGenerator? = null,
    internal val combatNarrator: CombatNarrator? = null,
    private val memoryManager: MemoryManager? = null,
    private val llmClient: OpenAIClient? = null
) {
    internal var worldState: WorldState = initialWorldState
    private var running = true
    private var respawnState: RespawnState? = null
    internal val combatResolver = CombatResolver()
    internal val skillCheckResolver = SkillCheckResolver()
    internal val persistenceManager = PersistenceManager()
    private val intentRecognizer = IntentRecognizer(llmClient)
    internal val sceneryGenerator = SceneryDescriptionGenerator(llmClient)
    internal var lastConversationNpcId: String? = null

    // Combat System V2 components
    internal val turnQueue: TurnQueueManager? = if (llmClient != null) TurnQueueManager() else null
    internal val monsterAIHandler: MonsterAIHandler? = if (llmClient != null) MonsterAIHandler(llmClient) else null
    private val skillClassifier: SkillClassifier? = if (llmClient != null) SkillClassifier(llmClient) else null
    internal val attackResolver: AttackResolver? = if (skillClassifier != null) AttackResolver(skillClassifier) else null
    internal val llmService = llmClient

    // Item System V2 components
    private val itemDatabase = ItemDatabase(com.jcraw.mud.core.DatabaseConfig.ITEMS_DB)
    internal val itemRepository = SQLiteItemRepository(itemDatabase)
    internal val recipeRepository = com.jcraw.mud.memory.item.SQLiteRecipeRepository(itemDatabase)
    private val lootGenerator = LootGenerator(itemRepository)
    internal val deathHandler = DeathHandler(lootGenerator)
    internal val corpseDecayManager = CorpseDecayManager()

    // Social system components
    private val socialDatabase = SocialDatabase(com.jcraw.mud.core.DatabaseConfig.SOCIAL_DB)
    private val socialComponentRepo = SqliteSocialComponentRepository(socialDatabase)
    private val socialEventRepo = SqliteSocialEventRepository(socialDatabase)
    private val knowledgeRepo = com.jcraw.mud.memory.social.SqliteKnowledgeRepository(socialDatabase)
    internal val dispositionManager = DispositionManager(socialComponentRepo, socialEventRepo)
    internal val emoteHandler = com.jcraw.mud.reasoning.EmoteHandler(dispositionManager)
    internal val npcKnowledgeManager = com.jcraw.mud.reasoning.NPCKnowledgeManager(knowledgeRepo, socialComponentRepo, llmClient)
    private val questTracker = QuestTracker(dispositionManager)

    // Skill system components
    private val skillDatabase = com.jcraw.mud.memory.skill.SkillDatabase(com.jcraw.mud.core.DatabaseConfig.SKILLS_DB)
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
    private val playerRespawnService = PlayerRespawnService(corpseRepository, treasureRoomRepository)
    // TODO: Add corpse decay scheduler when integrated
    // internal val corpseDecayScheduler = CorpseDecayScheduler(corpseRepository)

    // Victory System components (Chunk 7)
    internal val victoryHandlers = com.jcraw.app.handlers.VictoryHandlers()
    internal val bossCombatEnhancements = com.jcraw.mud.reasoning.boss.BossCombatEnhancements()
    private val bossSummonedTracker = mutableSetOf<String>() // Track which bosses have already summoned

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
            // Pause NPC actions while waiting on permadeath prompts
            if (respawnState == null) {
                processNPCTurns()
            }

            print("\n> ")
            val input = readLine()?.trim() ?: continue

            if (input.isBlank()) continue

            respawnState?.let {
                handleRespawnInput(input)
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
            // Note: This is a basic implementation - real action costs would come from ActionCosts
            val actionCost = 5L // Default cost for most actions
            worldState = worldState.advanceTime(actionCost)
        }

        println("\nThanks for playing!")
    }

    /**
     * Set running state (used by quit handler).
     */
    internal fun setRunning(value: Boolean) {
        running = value
    }

    /**
     * Print welcome message.
     */
    internal fun printWelcome() {
        println("\nWelcome, ${worldState.player.name}!")
        val spaceCount = worldState.graphNodes.size.takeIf { it > 0 } ?: worldState.spaces.size
        println("You have entered a dungeon with $spaceCount spaces to explore.")
        println("Type 'help' for available commands.\n")
    }

    /**
     * Describe the current room, including combat status, exits, and entities.
     */
    internal fun describeCurrentRoom() {
        val currentSpace = worldState.getCurrentSpace()
        val currentNode = worldState.getCurrentGraphNode()
        val player = worldState.player

        if (currentSpace == null || currentNode == null) {
            println("\n[No space data loaded - unable to describe surroundings]")
            return
        }

        println("\n${currentSpace.name}")
        println("-" * currentSpace.name.length)
        val description = generateRoomDescription(currentSpace, player.currentRoomId)
        println(description.ifBlank { "An unexplored area..." })

        if (currentSpace.isTreasureRoom) {
            describeTreasureRoomState(player.currentRoomId)
        }

        val visibleExits = currentNode.neighbors.filter { edge ->
            !edge.hidden || player.hasRevealedExit("${currentNode.id}:${edge.targetId}")
        }

        if (visibleExits.isNotEmpty()) {
            val exitText = visibleExits.joinToString(", ") { edge ->
                val targetName = worldState.getSpace(edge.targetId)?.name ?: edge.targetId
                "${edge.direction} (${targetName})"
            }
            println("\nExits: $exitText")
        }

        val entities = worldState.getEntitiesInSpace(player.currentRoomId)
        if (lastConversationNpcId != null && entities.none { it.id == lastConversationNpcId }) {
            lastConversationNpcId = null
        }
        if (entities.isNotEmpty()) {
            println("\nYou see:")
            entities.forEach { entity ->
                when (entity) {
                    is Entity.NPC -> {
                        val disposition = entity.getDisposition()
                        val statusText = when {
                            disposition < -75 -> " ⚔️  (hostile - glares at you!)"
                            disposition < -50 -> " ⚠️  (unfriendly - watches you warily)"
                            disposition < -25 -> " (neutral)"
                            disposition < 25 -> " (neutral)"
                            disposition < 75 -> " ✓ (friendly)"
                            else -> " ★ (allied)"
                        }
                        println("  - ${entity.name}$statusText")
                    }
                    else -> println("  - ${entity.name}")
                }
            }
        }
    }

    private fun describeTreasureRoomState(spaceId: String) {
        val treasureRoom = worldState.getTreasureRoom(spaceId)
        if (treasureRoom == null) {
            println("\n(An eerie hush lingers—this treasure room's state couldn't be loaded.)")
            return
        }

        println()
        val takenItem = treasureRoom.currentlyTakenItem
        when {
            treasureRoom.hasBeenLooted -> println("Only bare pedestals remain; the room's magic has faded.")
            takenItem == null -> println("Five pedestals glow softly. Claim a single treasure with 'examine pedestals' before the others seal away.")
            else -> {
                val templateName = itemRepository.findTemplateById(takenItem)
                    .getOrNull()
                    ?.name
                    ?: takenItem
                println("The other pedestals are sealed while you hold the $templateName. Return it if you wish to choose again.")
            }
        }
    }

    /**
     * Generate a space description using LLM or fallback heuristics.
     */
    internal fun generateRoomDescription(
        space: SpacePropertiesComponent,
        spaceId: String? = null
    ): String {
        if (space.description.isNotBlank() && !space.descriptionStale) {
            return space.description
        }

        if (descriptionGenerator != null) {
            val generated = runBlocking {
                descriptionGenerator.generateDescription(space)
            }
            if (spaceId != null) {
                worldState = worldState.updateSpace(spaceId, space.withDescription(generated))
            }
            return generated
        }

        return "You are in ${space.name}. The ${space.terrainType.name.lowercase()} terrain reveals little else."
    }

    /**
     * Process a parsed intent by dispatching to appropriate handler.
     */
    private fun processIntent(intent: Intent) {
        when (intent) {
            is Intent.Move -> com.jcraw.app.handlers.MovementHandlers.handleMove(this, intent.direction)
            is Intent.Scout -> com.jcraw.app.handlers.MovementHandlers.handleScout(this, intent.direction)
            is Intent.Travel -> com.jcraw.app.handlers.MovementHandlers.handleTravel(this, intent.direction)
            is Intent.Look -> com.jcraw.app.handlers.MovementHandlers.handleLook(this, intent.target)
            is Intent.Search -> com.jcraw.app.handlers.MovementHandlers.handleSearch(this, intent.target)
            is Intent.Interact -> com.jcraw.app.handlers.SkillQuestHandlers.handleInteract(this, intent.target)
            is Intent.Craft -> com.jcraw.app.handlers.SkillQuestHandlers.handleCraft(this, intent.target)
            is Intent.Pickpocket -> println("Pickpocketing not yet integrated") // TODO
            is Intent.Trade -> println("Trading not yet integrated") // TODO
            is Intent.UseItem -> println("Item use not yet integrated") // TODO
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
            is Intent.Equip -> com.jcraw.app.handlers.ItemHandlers.handleEquip(this, intent.target)
            is Intent.Use -> com.jcraw.app.handlers.ItemHandlers.handleUse(this, intent.target)
            is Intent.LootCorpse -> {
                val (newWorld, narration) = com.jcraw.app.handlers.handleLootCorpse(
                    intent, worldState, worldState.player, corpseRepository, worldState.gameTime
                )
                worldState = newWorld
                println(narration)
            }
            is Intent.Check -> com.jcraw.app.handlers.SkillQuestHandlers.handleCheck(this, intent.target)
            is Intent.Persuade -> com.jcraw.app.handlers.SocialHandlers.handlePersuade(this, intent.target)
            is Intent.Intimidate -> com.jcraw.app.handlers.SocialHandlers.handleIntimidate(this, intent.target)
            is Intent.Emote -> com.jcraw.app.handlers.SocialHandlers.handleEmote(this, intent.emoteType, intent.target)
            is Intent.AskQuestion -> runBlocking { com.jcraw.app.handlers.SocialHandlers.handleAskQuestion(this@MudGame, intent.npcTarget, intent.topic) }
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
                val (newWorld, narration) = com.jcraw.app.handlers.handleRest(
                    intent, worldState, worldState.player, spacePropertiesRepository
                )
                worldState = newWorld
                println(narration)
            }
            is Intent.Help -> com.jcraw.app.handlers.SkillQuestHandlers.handleHelp()
            is Intent.Quit -> com.jcraw.app.handlers.SkillQuestHandlers.handleQuit(this)
            is Intent.Invalid -> println(intent.message)
        }
    }

    /**
     * Track quest progress after player actions.
     */
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
                            println("\n✓ Quest objective completed: ${newObj.description}")
                        }
                    }

                    // Check if quest just completed
                    if (quest.status == QuestStatus.COMPLETED &&
                        oldQuest.status == QuestStatus.ACTIVE) {
                        println("\n🎉 Quest completed: ${quest.title}")
                        println("Use 'claim ${quest.id}' to collect your reward!")
                    }
                }
            }

            // Update both player and world state (world may have NPC disposition changes)
            worldState = updatedWorld.updatePlayer(updatedPlayer)
        }
    }

    /**
     * Process NPC turns that are ready to act (Combat V2 integration)
     *
     * Executes all NPC actions whose actionTimerEnd <= current game time.
     * This should be called before processing player input.
     */
    private fun processNPCTurns() {
        val queue = turnQueue ?: return
        val aiHandler = monsterAIHandler ?: return

        // Process all NPCs whose turn has come
        while (true) {
            val currentTime = worldState.gameTime
            val nextEntry = queue.peek() ?: break

            // Check if it's time for this entity to act
            if (nextEntry.second > currentTime) {
                break // No more entities ready to act
            }

            // Dequeue the entity
            val entityId = queue.dequeue(currentTime) ?: break

            val spaceId = worldState.findSpaceContainingEntity(entityId)
            val npc = worldState.getEntity(entityId) as? Entity.NPC

            if (npc == null || spaceId == null) {
                // NPC not found (died or fled), skip
                continue
            }

            val playerSpaceId = worldState.player.currentRoomId
            if (spaceId != playerSpaceId) {
                // NPC not in player's room, re-enqueue for later
                val skillComponent = npc.components[ComponentType.SKILL] as? SkillComponent
                val speedLevel = skillComponent?.getEffectiveLevel("Speed") ?: 0
                val cost = SpeedCalculator.calculateActionCost("melee_attack", speedLevel)
                queue.enqueue(entityId, currentTime + cost)
                continue
            }

            // Get AI decision
            val decision = runBlocking {
                aiHandler.decideAction(entityId, worldState)
            }

            // Execute the decision
            executeNPCDecision(npc, decision)

            // Re-enqueue the NPC for next turn (if not dead)
            val combatComponent = npc.components[ComponentType.COMBAT] as? CombatComponent
            if (combatComponent == null || !combatComponent.isDead()) {
                val skillComponent = npc.components[ComponentType.SKILL] as? SkillComponent
                val speedLevel = skillComponent?.getEffectiveLevel("Speed") ?: 0
                val cost = SpeedCalculator.calculateActionCost("melee_attack", speedLevel)
                queue.enqueue(entityId, currentTime + cost)
            }
        }
    }

    /**
     * Execute an NPC's AI decision
     */
    private fun executeNPCDecision(npc: Entity.NPC, decision: AIDecision) {
        when (decision) {
            is AIDecision.Attack -> {
                // NPC attacks player
                println("\n${npc.name} attacks you!")
                executeNPCAttack(npc)
            }
            is AIDecision.Defend -> {
                println("\n${npc.name} takes a defensive stance.")
                // TODO: Apply defensive buff
            }
            is AIDecision.UseItem -> {
                println("\n${npc.name} attempts to use an item.")
                // TODO: Implement NPC item usage
            }
            is AIDecision.Flee -> {
                println("\n${npc.name} attempts to flee!")
                // TODO: Implement NPC flee mechanics
            }
            is AIDecision.Wait -> {
                println("\n${npc.name} waits, watching carefully.")
            }
            is AIDecision.Error -> {
                // Silent error, NPC does nothing
            }
        }
    }

    /**
     * Execute NPC attack on player
     */
    private fun executeNPCAttack(npc: Entity.NPC) {
        val resolver = attackResolver
        if (resolver != null) {
            // Use new attack resolver
            val result = runBlocking {
                resolver.resolveAttack(
                    attackerId = npc.id,
                    defenderId = worldState.player.id,
                    action = "${npc.name} attacks",
                    worldState = worldState
                )
            }

            when (result) {
                is AttackResult.Hit -> {
                    // Apply damage to player
                    val newPlayer = worldState.player.takeDamage(result.damage)
                    worldState = worldState.updatePlayer(newPlayer)

                    // Generate narrative
                    // NPCs don't have equipment in V2 yet, use a generic weapon
                    val npcWeapon = "weapon"
                    val narrative = if (combatNarrator != null) {
                        runBlocking {
                            combatNarrator.narrateAction(
                                weapon = npcWeapon,
                                damage = result.damage,
                                maxHp = worldState.player.maxHealth,
                                isHit = true,
                                isCritical = false,
                                isDeath = newPlayer.isDead(),
                                isSpell = false,
                                targetName = worldState.player.name
                            )
                        }
                    } else {
                        "${npc.name} hits you for ${result.damage} damage! (HP: ${newPlayer.health}/${newPlayer.maxHealth})"
                    }
                    println(narrative)

                    // Check if player died
                    if (newPlayer.isDead()) {
                        handlePlayerDeath()
                    }
                }
                is AttackResult.Miss -> {
                    val narrative = if (result.wasDodged) {
                        "You dodge ${npc.name}'s attack!"
                    } else {
                        "${npc.name} misses you!"
                    }
                    println(narrative)
                }
                is AttackResult.Failure -> {
                    // Silent failure, fall back to simple damage
                    val damage = (1..6).random()
                    val newPlayer = worldState.player.takeDamage(damage)
                    worldState = worldState.updatePlayer(newPlayer)
                    println("${npc.name} hits you for $damage damage! (HP: ${newPlayer.health}/${newPlayer.maxHealth})")

                    if (newPlayer.isDead()) {
                        handlePlayerDeath()
                    }
                }
            }
        } else {
            // Fallback to simple damage
            val damage = (1..6).random()
            val newPlayer = worldState.player.takeDamage(damage)
            worldState = worldState.updatePlayer(newPlayer)
            println("${npc.name} hits you for $damage damage! (HP: ${newPlayer.health}/${newPlayer.maxHealth})")

            if (newPlayer.isDead()) {
                handlePlayerDeath()
            }
        }

        // Boss summon mechanics (Chunk 7 integration)
        // Check if this is a boss and should summon minions
        val hasSummoned = bossSummonedTracker.contains(npc.id)
        if (bossCombatEnhancements.shouldSummon(npc, hasSummoned)) {
            // Boss should summon minions!
            val summonResult = bossCombatEnhancements.summonMinions(npc, difficulty = 5)

            summonResult.onSuccess { minions ->
                if (minions.isNotEmpty()) {
                    // Mark boss as having summoned
                    bossSummonedTracker.add(npc.id)

                    // Display summon narration
                    val narration = com.jcraw.mud.reasoning.boss.BossCombatEnhancements.getSummonNarration(npc, minions.size)
                    println("\n" + "=".repeat(60))
                    println(narration)
                    println("=".repeat(60))

                    // Add minions to the current room
                    val currentSpaceId = worldState.player.currentRoomId
                    minions.forEach { minion ->
                        worldState = worldState.addEntityToSpace(currentSpaceId, minion)
                    }

                    if (turnQueue != null) {
                        minions.forEach { minion ->
                            val cost = SpeedCalculator.calculateActionCost("melee_attack", 0)
                            turnQueue.enqueue(minion.id, worldState.gameTime + cost)
                        }
                    }
                }
            }.onFailure { e ->
                // Silent failure - boss doesn't summon
                println("Debug: Boss summon failed: ${e.message}")
            }
        }
    }

    /**
     * Handle player death with permadeath mechanics:
     * - Persist corpse with player's items at death location
     * - Prompt player to continue with a brand new character
     * - Respawn at starting location once player provides a new name
     */
    internal fun handlePlayerDeath() {
        if (respawnState != null) return

        val pending = playerRespawnService.createPendingRespawn(
            worldState = worldState,
            playerId = worldState.player.id,
            spawnSpaceIdOverride = worldState.gameProperties["starting_space"]
        ).getOrElse { error ->
            println("\nFailed to process permadeath: ${error.message}")
            running = false
            return
        }

        respawnState = RespawnState.AwaitingConfirmation(pending)

        println()
        println(pending.deathResult.narration)
        println("\nContinue as new character (Y/N)?")
    }

    private fun handleRespawnInput(input: String) {
        val state = respawnState ?: return
        when (state) {
            is RespawnState.AwaitingConfirmation -> {
                when (input.lowercase()) {
                    "y", "yes" -> {
                        respawnState = RespawnState.AwaitingName(state.pending)
                        println("Name your new character:")
                    }
                    "n", "no" -> {
                        println("You accept your fate. Game over.")
                        respawnState = null
                        running = false
                    }
                    else -> println("Please answer Y or N.")
                }
            }
            is RespawnState.AwaitingName -> {
                val trimmed = input.trim()
                if (trimmed.isEmpty()) {
                    println("Name cannot be blank.")
                    return
                }

                val outcome = playerRespawnService.completeRespawn(worldState, state.pending, trimmed)
                outcome.onFailure { error ->
                    println("Failed to respawn: ${error.message}")
                }.onSuccess { result ->
                    worldState = result.worldState
                    respawnState = null
                    lastConversationNpcId = null
                    println(result.respawnMessage)
                    describeCurrentRoom()
                }
            }
        }
    }

    private sealed interface RespawnState {
        data class AwaitingConfirmation(val pending: PlayerRespawnService.PendingRespawn) : RespawnState
        data class AwaitingName(val pending: PlayerRespawnService.PendingRespawn) : RespawnState
    }

    /**
     * Build a map of exits with their destination names for navigation parsing.
     */
    private fun buildExitsWithNames(node: GraphNodeComponent): Map<Direction, String> {
        return node.neighbors.mapNotNull { edge ->
            val direction = Direction.fromString(edge.direction) ?: return@mapNotNull null
            val targetName = worldState.getSpace(edge.targetId)?.name ?: edge.targetId
            direction to targetName
        }.toMap()
    }
}

/**
 * String repetition helper for formatting.
 */
internal operator fun String.times(n: Int): String = repeat(n)
