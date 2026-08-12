package com.jcraw.app

import com.jcraw.mud.core.*
import com.jcraw.mud.core.repository.ItemRepository
import com.jcraw.mud.perception.Intent
import com.jcraw.mud.reasoning.*
import com.jcraw.mud.reasoning.inventory.FloorItemDropApply
import com.jcraw.mud.reasoning.inventory.FloorItemTakeApply
import com.jcraw.mud.reasoning.inventory.GiveItemApply
import com.jcraw.mud.reasoning.inventory.UseConsumableApply
import com.jcraw.mud.memory.MemoryManager
import com.jcraw.mud.memory.social.SocialDatabase
import com.jcraw.mud.memory.social.SqliteSocialComponentRepository
import com.jcraw.mud.memory.social.SqliteSocialEventRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Result of quest tracking, including updated states and notification messages
 */
data class QuestTrackingResult(
    val updatedPlayer: PlayerState,
    val updatedWorld: WorldState,
    val notifications: String
)

/**
 * GameServer manages the shared WorldState and coordinates multiple player sessions.
 * Handles game logic, state updates, and broadcasting events to relevant players.
 */
class GameServer(
    private var worldState: WorldState,
    private val memoryManager: MemoryManager,
    private val roomDescriptionGenerator: RoomDescriptionGenerator,
    private val npcInteractionGenerator: NPCInteractionGenerator,
    private val combatResolver: CombatResolver,
    private val combatNarrator: CombatNarrator,
    private val skillCheckResolver: SkillCheckResolver,
    private val sceneryGenerator: SceneryDescriptionGenerator,
    private val skillManager: com.jcraw.mud.reasoning.skill.SkillManager,
    private val socialDatabase: SocialDatabase? = null
) {
    /** Optional item catalog for floor take → V2 inventory (MUD-019). */
    var itemRepository: ItemRepository? = null

    private val sessions = mutableMapOf<PlayerId, PlayerSession>()
    private val stateMutex = Mutex()

    // Social system components
    private val socialComponentRepo = socialDatabase?.let { SqliteSocialComponentRepository(it) }
    private val socialEventRepo = socialDatabase?.let { SqliteSocialEventRepository(it) }
    private val dispositionManager = if (socialComponentRepo != null && socialEventRepo != null) {
        DispositionManager(socialComponentRepo, socialEventRepo)
    } else null
    private val questTracker = QuestTracker(dispositionManager)

    /**
     * Add a player session to the server
     */
    suspend fun addPlayerSession(session: PlayerSession, startingRoomId: SpaceId) = stateMutex.withLock {
        // Create player state
        val playerState = PlayerState(
            id = session.playerId,
            name = session.playerName,
            currentRoomId = startingRoomId
        )

        // Add to world state
        worldState = worldState.addPlayer(playerState)
        sessions[session.playerId] = session

        // Broadcast join event
        broadcastEvent(
            GameEvent.PlayerJoined(
                playerId = session.playerId,
                playerName = session.playerName,
                roomId = startingRoomId,
                excludePlayer = session.playerId
            )
        )

        session.sendMessage("Welcome to the dungeon, ${session.playerName}!")
    }

    /**
     * Remove a player session from the server
     */
    suspend fun removePlayerSession(playerId: PlayerId) = stateMutex.withLock {
        val session = sessions[playerId] ?: return@withLock
        val playerState = worldState.getPlayer(playerId) ?: return@withLock

        // Broadcast leave event
        broadcastEvent(
            GameEvent.PlayerLeft(
                playerId = playerId,
                playerName = playerState.name,
                roomId = playerState.currentRoomId,
                excludePlayer = playerId
            )
        )

        // Remove from world state and sessions
        worldState = worldState.removePlayer(playerId)
        sessions.remove(playerId)
        session.close()
    }

    /**
     * Process an intent from a player and return the response
     */
    suspend fun processIntent(playerId: PlayerId, intent: Intent): String = stateMutex.withLock {
        val session = sessions[playerId] ?: return@withLock "Error: Session not found"
        val playerState = worldState.getPlayer(playerId) ?: return@withLock "Error: Player not found"

        // Process the intent and get response + new state
        val (response, newWorldState, event) = handleIntent(playerId, playerState, intent)

        // Update world state
        worldState = newWorldState

        // Broadcast event if any
        event?.let { broadcastEvent(it) }

        response
    }

    /**
     * Get current world state (thread-safe read)
     */
    suspend fun getWorldState(): WorldState = stateMutex.withLock { worldState }

    /**
     * Update world state (thread-safe write)
     */
    suspend fun updateWorldState(newState: WorldState) = stateMutex.withLock {
        worldState = newState
    }

    /**
     * Broadcast a game event to all players in the relevant space (V3)
     */
    private suspend fun broadcastEvent(event: GameEvent) {
        val spaceId = event.roomId ?: return

        // V3: Find all players in the space
        val playersInSpace = worldState.players.values.filter { it.currentRoomId == spaceId }

        // Send event to each player's session (except excluded player)
        playersInSpace.forEach { player ->
            if (player.id != event.excludePlayer) {
                sessions[player.id]?.notifyEvent(event)
            }
        }
    }

    /**
     * Track quest progress and return notification messages
     * Also returns updated world state (quest giver NPCs may have disposition changes)
     */
    private fun trackQuests(playerState: PlayerState, action: QuestAction): QuestTrackingResult {
        val (updatedPlayer, updatedWorld) = questTracker.updateQuestsAfterAction(
            playerState,
            worldState,
            action
        )

        // Check if any quest objectives were completed
        val notifications = buildString {
            if (updatedPlayer != playerState) {
                updatedPlayer.activeQuests.forEach { quest ->
                    val oldQuest = playerState.getQuest(quest.id)
                    if (oldQuest != null) {
                        // Check for newly completed objectives
                        quest.objectives.zip(oldQuest.objectives).forEach { (newObj, oldObj) ->
                            if (newObj.isCompleted && !oldObj.isCompleted) {
                                appendLine("\n✓ Quest objective completed: ${newObj.description}")
                            }
                        }

                        // Check if quest just completed
                        if (quest.status == QuestStatus.COMPLETED &&
                            oldQuest.status == QuestStatus.ACTIVE) {
                            appendLine("\n🎉 Quest completed: ${quest.title}")
                            appendLine("Use 'claim ${quest.id}' to collect your reward!")
                        }
                    }
                }
            }
        }

        return QuestTrackingResult(updatedPlayer, updatedWorld, notifications)
    }

    /**
     * Handle a specific intent and return response, new state, and optional event (V3)
     */
    private suspend fun handleIntent(
        playerId: PlayerId,
        playerState: PlayerState,
        intent: Intent
    ): Triple<String, WorldState, GameEvent?> {
        // V3: Get current space instead of room
        val currentSpace = worldState.getCurrentSpace(playerId)
        val currentSpaceId = playerState.currentRoomId

        if (currentSpace == null) {
            return Triple("Error: Current location not found", worldState, null)
        }

        return when (intent) {
            is Intent.Move -> handleMove(playerId, playerState, intent.direction)
            is Intent.Scout -> Triple("Scout not yet integrated with world generation system", worldState, null)
            is Intent.Travel -> Triple("Travel not yet integrated with world generation system", worldState, null)
            is Intent.Look -> handleLook(playerId, playerState, intent.target)
            is Intent.Search -> handleSearch(playerId, playerState, intent.target)
            is Intent.Rest -> Triple("Rest not yet supported in multi-user mode", worldState, null)
            is Intent.LootCorpse -> Triple("Corpse looting not yet supported in multi-user mode", worldState, null)
            is Intent.Craft -> Triple("Crafting not yet supported in multi-user mode", worldState, null)
            is Intent.Pickpocket -> Triple("Pickpocketing not yet supported in multi-user mode", worldState, null)
            is Intent.Trade -> Triple("Trading not yet supported in multi-user mode", worldState, null)
            is Intent.UseItem -> Triple("Advanced item use not yet supported in multi-user mode", worldState, null)
            is Intent.Attack -> handleAttack(playerId, playerState, intent.target)
            is Intent.Flee -> Triple("Flee not yet supported in multi-user mode", worldState, null)
            is Intent.Talk -> handleTalk(playerId, playerState, intent.target)
            is Intent.TakeTreasure -> Triple("Treasure rooms not yet supported in multi-user mode", worldState, null)
            is Intent.ReturnTreasure -> Triple("Treasure rooms not yet supported in multi-user mode", worldState, null)
            is Intent.ExaminePedestal -> Triple("Treasure rooms not yet supported in multi-user mode", worldState, null)
            is Intent.Take -> handleTake(playerId, playerState, intent.target)
            is Intent.TakeAll -> handleTakeAll(playerId, playerState)
            is Intent.Drop -> handleDrop(playerId, playerState, intent.target)
            is Intent.Give -> handleGive(playerId, playerState, intent.itemTarget, intent.npcTarget)
            is Intent.Equip -> handleEquip(playerId, playerState, intent.target)
            is Intent.Use -> handleUse(playerId, playerState, intent.target)
            is Intent.Check -> handleCheck(playerId, playerState, intent.target)
            is Intent.Persuade -> handlePersuade(playerId, playerState, intent.target)
            is Intent.Intimidate -> handleIntimidate(playerId, playerState, intent.target)
            is Intent.Emote -> Triple("Emote system not yet fully supported in multi-user mode", worldState, null)
            is Intent.Say -> Triple("Say is not yet supported in multi-user mode", worldState, null)
            is Intent.AskQuestion -> Triple("Ask system not yet fully supported in multi-user mode", worldState, null)
            is Intent.UseSkill -> Triple("Skill system not yet fully supported in multi-user mode", worldState, null)
            is Intent.TrainSkill -> Triple("Skill system not yet fully supported in multi-user mode", worldState, null)
            is Intent.ChoosePerk -> Triple("Skill system not yet fully supported in multi-user mode", worldState, null)
            is Intent.ViewSkills -> Triple("Skill system not yet fully supported in multi-user mode", worldState, null)
            is Intent.Inventory -> Triple(formatInventory(playerState), worldState, null)
            is Intent.Save -> Triple("Save not supported in multi-user mode", worldState, null)
            is Intent.Load -> Triple("Load not supported in multi-user mode", worldState, null)
            is Intent.Quests -> Triple(formatQuests(playerState), worldState, null)
            is Intent.AcceptQuest -> Triple("Quest system not yet supported in multi-user mode", worldState, null)
            is Intent.AbandonQuest -> Triple("Quest system not yet supported in multi-user mode", worldState, null)
            is Intent.ClaimReward -> Triple("Quest system not yet supported in multi-user mode", worldState, null)
            is Intent.Help -> Triple(getHelpText(), worldState, null)
            is Intent.Quit -> Triple("Goodbye!", worldState, null)
            is Intent.Invalid -> Triple(intent.message, worldState, null)
            is Intent.Interact -> Triple("You need to be more specific about how you want to interact.", worldState, null)
        }
    }

    private fun formatQuests(playerState: PlayerState): String {
        return "Quest system coming soon to multi-user mode!"
    }

    private suspend fun handleMove(
        playerId: PlayerId,
        playerState: PlayerState,
        direction: Direction
    ): Triple<String, WorldState, GameEvent?> {
        // V3: No modal combat, movement always allowed
        val oldSpaceId = playerState.currentRoomId
        val playerSkills = skillManager.getSkillComponent(playerId)
        val newWorldState = worldState.movePlayerV3(playerId, direction, playerSkills)

        return if (newWorldState != null) {
            val newPlayerState = newWorldState.getPlayer(playerId)!!
            val newSpaceId = newPlayerState.currentRoomId
            val newSpace = newWorldState.getSpace(newSpaceId)!!

            // Generate space description (V3: use description field directly)
            val description = if (newSpace.description.isNotBlank()) {
                newSpace.description
            } else {
                "You arrive at a new location. The area awaits exploration."
            }

            // Track space exploration for quests
            val questResult = trackQuests(newPlayerState, QuestAction.VisitedRoom(newSpaceId))
            val finalWorldState = questResult.updatedWorld

            // Create movement events
            val leaveEvent = GameEvent.PlayerMoved(
                playerId = playerId,
                playerName = playerState.name,
                fromRoomId = oldSpaceId,
                toRoomId = newSpaceId,
                direction = direction.name.lowercase(),
                roomId = oldSpaceId,
                excludePlayer = playerId
            )

            val enterEvent = GameEvent.PlayerJoined(
                playerId = playerId,
                playerName = playerState.name,
                roomId = newSpaceId,
                excludePlayer = playerId
            )

            // Broadcast both events
            broadcastEvent(leaveEvent)
            broadcastEvent(enterEvent)

            Triple(description + questResult.notifications, finalWorldState, null)
        } else {
            Triple("You can't go that way.", worldState, null)
        }
    }

    private suspend fun handleLook(
        playerId: PlayerId,
        playerState: PlayerState,
        target: String?
    ): Triple<String, WorldState, GameEvent?> {
        // V3: Use getCurrentSpace
        val currentSpace = worldState.getCurrentSpace(playerId)!!
        val spaceId = playerState.currentRoomId

        return if (target == null) {
            // V3: Use space description directly
            val description = if (currentSpace.description.isNotBlank()) {
                currentSpace.description
            } else {
                "You see an unexplored area."
            }
            Triple(description, worldState, null)
        } else {
            // Look at specific entity (V3: use getEntitiesInSpace)
            val entities = worldState.getEntitiesInSpace(spaceId)
            val entity = entities.find { it.name.equals(target, ignoreCase = true) }

            if (entity != null) {
                Triple(entity.description, worldState, null)
            } else {
                // V3: Scenery not yet supported in multi-user V3 mode
                Triple("You don't see that here.", worldState, null)
            }
        }
    }

    private fun handleSearch(
        playerId: PlayerId,
        playerState: PlayerState,
        target: String?
    ): Triple<String, WorldState, GameEvent?> {
        val searchMessage = "You search the area carefully${if (target != null) ", focusing on the $target" else ""}..."

        // Perform a Wisdom (Perception) skill check to find hidden items
        val result = skillCheckResolver.checkPlayer(
            playerState,
            StatType.WISDOM,
            Difficulty.MEDIUM  // DC 15 for finding hidden items
        )

        // V3: Get entities from space
        val spaceId = playerState.currentRoomId
        val entities = worldState.getEntitiesInSpace(spaceId)

        val description = buildString {
            append("$searchMessage\n\n")
            append("Rolling Perception check...\n")
            append("d20 roll: ${result.roll} + WIS modifier: ${result.modifier} = ${result.total} vs DC ${result.dc}\n")

            if (result.isCriticalSuccess) {
                append("\n🎲 CRITICAL SUCCESS! (Natural 20)\n")
            } else if (result.isCriticalFailure) {
                append("\n💀 CRITICAL FAILURE! (Natural 1)\n")
            }

            if (result.success) {
                append("\n✅ Success!\n")

                // Find items in the space
                val hiddenItems = entities.filterIsInstance<Entity.Item>().filter { !it.isPickupable }
                val pickupableItems = entities.filterIsInstance<Entity.Item>().filter { it.isPickupable }

                if (hiddenItems.isNotEmpty() || pickupableItems.isNotEmpty()) {
                    if (pickupableItems.isNotEmpty()) {
                        append("You find the following items:\n")
                        pickupableItems.forEach { item ->
                            append("  - ${item.name}: ${item.description}\n")
                        }
                    }
                    if (hiddenItems.isNotEmpty()) {
                        append("\nYou also notice some interesting features:\n")
                        hiddenItems.forEach { item ->
                            append("  - ${item.name}: ${item.description}\n")
                        }
                    }
                } else {
                    append("You don't find anything hidden here.")
                }
            } else {
                append("\n❌ Failure!\n")
                append("You don't find anything of interest.")
            }
        }

        return Triple(description, worldState, null)
    }

    private suspend fun handleAttack(
        playerId: PlayerId,
        playerState: PlayerState,
        targetId: String?
    ): Triple<String, WorldState, GameEvent?> {
        // Combat not yet migrated to V2 in multi-user mode
        return Triple("Combat is not yet supported in multi-user mode. Coming soon!", worldState, null)
    }

    private suspend fun handleTalk(
        playerId: PlayerId,
        playerState: PlayerState,
        targetId: String
    ): Triple<String, WorldState, GameEvent?> {
        // V3: Get entities from space
        val spaceId = playerState.currentRoomId
        val entities = worldState.getEntitiesInSpace(spaceId)
        val npc = entities.filterIsInstance<Entity.NPC>()
            .find { it.name.equals(targetId, ignoreCase = true) }

        return if (npc != null) {
            val dialogue = npcInteractionGenerator.generateDialogue(npc, playerState)

            // Track NPC conversation for quests
            val questResult = trackQuests(playerState, QuestAction.TalkedToNPC(npc.id))

            Triple(dialogue + questResult.notifications, questResult.updatedWorld, null)
        } else {
            Triple("There's no one here by that name.", worldState, null)
        }
    }

    private fun handleTake(
        playerId: PlayerId,
        playerState: PlayerState,
        itemId: String
    ): Triple<String, WorldState, GameEvent?> {
        // V3: Get entities from space
        val spaceId = playerState.currentRoomId
        val entities = worldState.getEntitiesInSpace(spaceId)
        val item = entities.filterIsInstance<Entity.Item>()
            .find {
                it.name.equals(itemId, ignoreCase = true) ||
                    it.name.lowercase().contains(itemId.lowercase()) ||
                    it.id.lowercase().contains(itemId.lowercase())
            }

        return if (item != null && item.isPickupable) {
            val templates = buildFloorTakeTemplates(item)
            when (val result = FloorItemTakeApply.apply(
                world = worldState,
                player = playerState,
                spaceId = spaceId,
                floorItem = item,
                templates = templates
            )) {
                is FloorItemTakeApply.Result.Success -> {
                    // Point member world at take result so trackQuests does not drop V2 inventory
                    worldState = result.world
                    val updatedPlayer = worldState.getPlayer(playerId) ?: worldState.player
                    val questResult = trackQuests(updatedPlayer, QuestAction.CollectedItem(item.id))
                    val event = GameEvent.GenericAction(
                        playerId = playerId,
                        playerName = playerState.name,
                        actionDescription = "picks up ${result.itemName}",
                        roomId = spaceId,
                        excludePlayer = playerId
                    )
                    Triple(
                        "You take the ${result.itemName}." + questResult.notifications,
                        questResult.updatedWorld,
                        event
                    )
                }
                is FloorItemTakeApply.Result.Failure -> {
                    Triple(result.message, worldState, null)
                }
            }
        } else if (item != null && !item.isPickupable) {
            Triple("That's part of the environment and can't be taken.", worldState, null)
        } else {
            // V3: Check if it's an entity (no traits in V3 spaces)
            val isScenery = entities.any { it.name.lowercase().contains(itemId.lowercase()) }
            if (isScenery) {
                Triple("That's part of the environment and can't be taken.", worldState, null)
            } else {
                Triple("You don't see that here.", worldState, null)
            }
        }
    }

    private fun handleTakeAll(
        playerId: PlayerId,
        playerState: PlayerState
    ): Triple<String, WorldState, GameEvent?> {
        // V3: Get entities from space
        val spaceId = playerState.currentRoomId
        val entities = worldState.getEntitiesInSpace(spaceId)
        val items = entities.filterIsInstance<Entity.Item>().filter { it.isPickupable }

        return if (items.isEmpty()) {
            Triple("There are no items to take here.", worldState, null)
        } else {
            var currentPlayer = playerState
            var currentWorld = worldState
            val takenItems = mutableListOf<String>()
            var allQuestNotifications = ""
            val messages = mutableListOf<String>()

            items.forEach { item ->
                val templates = buildFloorTakeTemplates(item)
                when (val result = FloorItemTakeApply.apply(
                    world = currentWorld,
                    player = currentPlayer,
                    spaceId = spaceId,
                    floorItem = item,
                    templates = templates
                )) {
                    is FloorItemTakeApply.Result.Success -> {
                        currentWorld = result.world
                        currentPlayer = currentWorld.getPlayer(playerId) ?: currentWorld.player
                        takenItems.add(result.itemName)
                        messages.add("You take the ${result.itemName}.")

                        // Keep trackQuests on post-take world (V2 inventory + entity removal)
                        worldState = currentWorld
                        val questResult = trackQuests(currentPlayer, QuestAction.CollectedItem(item.id))
                        currentPlayer = questResult.updatedPlayer
                        currentWorld = questResult.updatedWorld
                        worldState = currentWorld
                        allQuestNotifications += questResult.notifications
                    }
                    is FloorItemTakeApply.Result.Failure -> {
                        messages.add(result.message)
                    }
                }
            }

            if (takenItems.isEmpty()) {
                Triple(messages.joinToString("\n").ifBlank { "You couldn't take any items." }, worldState, null)
            } else {
                val message = buildString {
                    messages.forEach { appendLine(it) }
                    append("\nYou took ${takenItems.size} item${if (takenItems.size > 1) "s" else ""}.")
                    append(allQuestNotifications)
                }

                val event = GameEvent.GenericAction(
                    playerId = playerId,
                    playerName = playerState.name,
                    actionDescription = "picks up all items",
                    roomId = spaceId,
                    excludePlayer = playerId
                )

                Triple(message, currentWorld, event)
            }
        }
    }

    private fun buildFloorTakeTemplates(item: Entity.Item): Map<String, ItemTemplate> {
        val repo = itemRepository ?: return emptyMap()
        val templates = mutableMapOf<String, ItemTemplate>()
        item.properties["templateId"]?.let { tid ->
            repo.findTemplateById(tid).getOrNull()?.let { templates[it.id] = it }
        }
        if (templates.isEmpty()) {
            repo.findAllTemplates().getOrNull()?.let { templates.putAll(it) }
        }
        return templates
    }

    private fun handleDrop(
        playerId: PlayerId,
        playerState: PlayerState,
        itemId: String
    ): Triple<String, WorldState, GameEvent?> {
        val spaceId = playerState.currentRoomId
        val templates = buildFloorDropTemplates(playerState)

        return when (val result = FloorItemDropApply.apply(
            world = worldState,
            player = playerState,
            spaceId = spaceId,
            target = itemId,
            templates = templates
        )) {
            is FloorItemDropApply.Result.Success -> {
                val event = GameEvent.GenericAction(
                    playerId = playerId,
                    playerName = playerState.name,
                    actionDescription = "drops ${result.itemName}",
                    roomId = spaceId,
                    excludePlayer = playerId
                )
                Triple("You drop the ${result.itemName}.", result.world, event)
            }
            is FloorItemDropApply.Result.Failure -> {
                Triple(result.message, worldState, null)
            }
        }
    }

    private fun buildFloorDropTemplates(player: PlayerState): Map<String, ItemTemplate> {
        val repo = itemRepository ?: return emptyMap()
        val templates = mutableMapOf<String, ItemTemplate>()
        repo.findAllTemplates().getOrNull()?.let { templates.putAll(it) }
        if (templates.isEmpty()) {
            player.inventoryComponent.items.forEach { instance ->
                repo.findTemplateById(instance.templateId).getOrNull()?.let {
                    templates[it.id] = it
                }
            }
        }
        return templates
    }

    private fun handleGive(
        playerId: PlayerId,
        playerState: PlayerState,
        itemTarget: String,
        npcTarget: String
    ): Triple<String, WorldState, GameEvent?> {
        val spaceId = playerState.currentRoomId
        val entities = worldState.getEntitiesInSpace(spaceId)

        // Find the NPC in the space
        val npc = entities.filterIsInstance<Entity.NPC>()
            .find { entity ->
                entity.name.lowercase().contains(npcTarget.lowercase()) ||
                entity.id.lowercase().contains(npcTarget.lowercase())
            }

        if (npc == null) {
            return Triple("There's no one here by that name.", worldState, null)
        }

        val templates = buildFloorDropTemplates(playerState)
        return when (val result = GiveItemApply.apply(
            world = worldState,
            player = playerState,
            target = itemTarget,
            templates = templates
        )) {
            is GiveItemApply.Result.Success -> {
                val givenPlayer = result.world.getPlayer(playerId) ?: playerState
                val questResult = trackQuests(
                    givenPlayer,
                    QuestAction.DeliveredItem(result.instanceId, npc.id)
                )
                // Merge inventory remove + quest updates (trackQuests base world may be pre-give)
                val finalWorld = questResult.updatedWorld.updatePlayer(questResult.updatedPlayer)

                val event = GameEvent.GenericAction(
                    playerId = playerId,
                    playerName = playerState.name,
                    actionDescription = "gives ${result.itemName} to ${npc.name}",
                    roomId = spaceId,
                    excludePlayer = playerId
                )

                Triple(
                    "You give the ${result.itemName} to ${npc.name}." + questResult.notifications,
                    finalWorld,
                    event
                )
            }
            is GiveItemApply.Result.Failure -> {
                Triple(result.message, worldState, null)
            }
        }
    }

    private fun handleEquip(
        playerId: PlayerId,
        playerState: PlayerState,
        itemId: String
    ): Triple<String, WorldState, GameEvent?> {
        val invComp = playerState.inventoryComponent
        val query = itemId.lowercase()
        val templates = buildFloorDropTemplates(playerState)

        // V2 only — inventoryComponent.equip (no V1 equip field mutators)
        val itemInstance = invComp.items.find { instance ->
            val template = templates[instance.templateId]
            (template?.name?.lowercase()?.contains(query) == true) ||
                instance.templateId.lowercase().contains(query) ||
                instance.id.equals(query, ignoreCase = true) ||
                (template?.name?.equals(itemId, ignoreCase = true) == true)
        }

        val template = itemInstance?.let {
            templates[it.templateId]
                ?: itemRepository?.findTemplateById(it.templateId)?.getOrNull()
        }
        val equipSlot = template?.equipSlot
        val updatedInventory = if (itemInstance != null && equipSlot != null) {
            invComp.equip(itemInstance, equipSlot)
        } else {
            null
        }

        return when {
            itemInstance == null -> Triple("You don't have that item.", worldState, null)
            template == null -> Triple("Error: Item template not found", worldState, null)
            equipSlot == null -> Triple("You can't equip that.", worldState, null)
            updatedInventory == null -> Triple("Error: Could not equip item", worldState, null)
            else -> {
                val updatedPlayer = playerState.copy(inventoryComponent = updatedInventory)
                Triple("You equip the ${template.name}.", worldState.updatePlayer(updatedPlayer), null)
            }
        }
    }

    private fun handleUse(
        playerId: PlayerId,
        playerState: PlayerState,
        itemId: String
    ): Triple<String, WorldState, GameEvent?> {
        val templates = buildFloorDropTemplates(playerState)

        return when (val result = UseConsumableApply.apply(playerState, itemId, templates)) {
            is UseConsumableApply.Result.Success -> {
                val message = if (result.healedAmount > 0) {
                    "You consume the ${result.itemName} and restore ${result.healedAmount} HP.\n" +
                        "Current health: ${result.player.health}/${result.player.maxHealth}"
                } else {
                    "You consume the ${result.itemName}, but you're already at full health."
                }
                Triple(message, worldState.updatePlayer(result.player), null)
            }
            is UseConsumableApply.Result.Failure -> {
                // Multi-user previously used a single "can't use" string for non-consumables;
                // keep Failure message from apply (equip tip / not sure / don't have).
                Triple(result.message, worldState, null)
            }
        }
    }

    /**
     * Calculate damage dealt by NPC attack (helper for potion use during combat).
     * Base damage + STR modifier. V2 combat uses AttackResolver for proper equipment bonuses.
     */
    private fun calculateNpcDamage(npc: Entity.NPC, player: PlayerState): Int {
        // Base damage 3-12 + STR modifier (V1 armor defense removed - use V2 AttackResolver)
        val baseDamage = kotlin.random.Random.nextInt(3, 13)
        val strModifier = npc.stats.strModifier()
        return (baseDamage + strModifier).coerceAtLeast(1)
    }

    private fun handleCheck(
        playerId: PlayerId,
        playerState: PlayerState,
        targetId: String
    ): Triple<String, WorldState, GameEvent?> {
        // V3: Get current space ID and entities
        val spaceId = playerState.currentRoomId
        val entities = worldState.getEntitiesInSpace(spaceId)

        // Normalize target for matching (replace underscores with spaces)
        val normalizedTarget = targetId.lowercase().replace("_", " ")

        // Find the feature in the space with flexible matching
        val feature = entities.filterIsInstance<Entity.Feature>()
            .find { entity ->
                val normalizedName = entity.name.lowercase()
                val normalizedId = entity.id.lowercase().replace("_", " ")

                // Check if target matches name or ID (with underscore normalization)
                normalizedName.contains(normalizedTarget) ||
                normalizedId.contains(normalizedTarget) ||
                normalizedTarget.contains(normalizedName) ||
                normalizedTarget.contains(normalizedId) ||
                // Also check if all words in target appear in name/id (any order)
                normalizedTarget.split(" ").all { word ->
                    normalizedName.contains(word) || normalizedId.contains(word)
                }
            }

        if (feature == null || feature.skillChallenge == null) {
            return Triple("There's nothing here to check.", worldState, null)
        }

        if (feature.isCompleted) {
            return Triple("You've already overcome this challenge.", worldState, null)
        }

        // Extract challenge to local variable for smart cast
        val challenge = feature.skillChallenge ?: return Triple("There's nothing here to check.", worldState, null)
        val result = skillCheckResolver.checkPlayer(playerState, challenge.statType, challenge.difficulty)

        val description = buildString {
            append("You rolled ${result.roll} + ${result.modifier} = ${result.total} vs DC ${result.dc}\n")
            if (result.isCriticalSuccess) append("Critical success! ")
            if (result.isCriticalFailure) append("Critical failure! ")
            append(if (result.success) challenge.successDescription else challenge.failureDescription)
        }

        val updatedFeature = if (result.success) feature.copy(isCompleted = true) else feature
        val newWorldState = worldState.removeEntityFromSpace(spaceId, feature.id).addEntityToSpace(spaceId, updatedFeature)

        // Track skill check for quests
        val questResult = if (result.success) {
            trackQuests(playerState, QuestAction.UsedSkill(feature.id))
        } else {
            QuestTrackingResult(playerState, newWorldState, "")
        }

        return Triple(description + questResult.notifications, questResult.updatedWorld, null)
    }

    private fun handlePersuade(
        playerId: PlayerId,
        playerState: PlayerState,
        targetId: String
    ): Triple<String, WorldState, GameEvent?> {
        // V3: Get current space ID and entities
        val spaceId = playerState.currentRoomId
        val entities = worldState.getEntitiesInSpace(spaceId)

        val npc = entities.filterIsInstance<Entity.NPC>()
            .find { it.name.equals(targetId, ignoreCase = true) }

        if (npc == null || npc.persuasionChallenge == null) {
            return Triple("You can't persuade that.", worldState, null)
        }

        if (npc.hasBeenPersuaded) {
            return Triple("${npc.name} has already been persuaded.", worldState, null)
        }

        val challenge = npc.persuasionChallenge ?: return Triple("You can't persuade that.", worldState, null)
        val result = skillCheckResolver.checkPlayer(playerState, challenge.statType, challenge.difficulty)

        val description = buildString {
            append("You rolled ${result.roll} + ${result.modifier} = ${result.total} vs DC ${result.dc}\n")
            if (result.isCriticalSuccess) append("Critical success! ")
            if (result.isCriticalFailure) append("Critical failure! ")
            append(if (result.success) challenge.successDescription else challenge.failureDescription)
        }

        val updatedNpc = if (result.success) npc.copy(hasBeenPersuaded = true) else npc
        val newWorldState = worldState.removeEntityFromSpace(spaceId, npc.id).addEntityToSpace(spaceId, updatedNpc)
        return Triple(description, newWorldState, null)
    }

    private fun handleIntimidate(
        playerId: PlayerId,
        playerState: PlayerState,
        targetId: String
    ): Triple<String, WorldState, GameEvent?> {
        // V3: Get current space ID and entities
        val spaceId = playerState.currentRoomId
        val entities = worldState.getEntitiesInSpace(spaceId)

        val npc = entities.filterIsInstance<Entity.NPC>()
            .find { it.name.equals(targetId, ignoreCase = true) }

        if (npc == null || npc.intimidationChallenge == null) {
            return Triple("You can't intimidate that.", worldState, null)
        }

        if (npc.hasBeenIntimidated) {
            return Triple("${npc.name} has already been intimidated.", worldState, null)
        }

        val challenge = npc.intimidationChallenge ?: return Triple("You can't intimidate that.", worldState, null)
        val result = skillCheckResolver.checkPlayer(playerState, challenge.statType, challenge.difficulty)

        val description = buildString {
            append("You rolled ${result.roll} + ${result.modifier} = ${result.total} vs DC ${result.dc}\n")
            if (result.isCriticalSuccess) append("Critical success! ")
            if (result.isCriticalFailure) append("Critical failure! ")
            append(if (result.success) challenge.successDescription else challenge.failureDescription)
        }

        val updatedNpc = if (result.success) npc.copy(hasBeenIntimidated = true) else npc
        val newWorldState = worldState.removeEntityFromSpace(spaceId, npc.id).addEntityToSpace(spaceId, updatedNpc)
        return Triple(description, newWorldState, null)
    }

    private fun formatInventory(playerState: PlayerState): String {
        val builder = StringBuilder()
        builder.appendLine("=== Inventory ===")
        builder.appendLine("Health: ${playerState.health}/${playerState.maxHealth}")

        playerState.equippedWeapon?.let { weapon ->
            builder.appendLine("Weapon: ${weapon.name} (+${weapon.damageBonus} damage)")
        }
        playerState.equippedArmor?.let { armor ->
            builder.appendLine("Armor: ${armor.name} (+${armor.defenseBonus} defense)")
        }

        if (playerState.inventory.isNotEmpty()) {
            builder.appendLine("\nCarrying:")
            playerState.inventory.forEach { item ->
                builder.appendLine("  - ${item.name}")
            }
        } else {
            builder.appendLine("\nYour inventory is empty.")
        }

        return builder.toString()
    }

    private fun getHelpText(): String = """
        === Commands ===
        Movement: north/south/east/west (or n/s/e/w)
        Interaction: look [target], search [target], take <item>, drop <item>, give <item> to <npc>, talk <npc>
        Combat: attack <npc>
        Equipment: equip <item>
        Consumables: use <item>
        Skills: check <feature>, persuade <npc>, intimidate <npc>
        Meta: inventory (or i), help, quit
    """.trimIndent()
}
