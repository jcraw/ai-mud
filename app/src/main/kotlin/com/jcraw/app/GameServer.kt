package com.jcraw.app

import com.jcraw.mud.core.*
import com.jcraw.mud.perception.Intent
import com.jcraw.mud.reasoning.*
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
    private val socialDatabase: SocialDatabase? = null
) {
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
    suspend fun addPlayerSession(session: PlayerSession, startingRoomId: RoomId) = stateMutex.withLock {
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
     * Broadcast a game event to all players in the relevant room
     */
    private suspend fun broadcastEvent(event: GameEvent) {
        val roomId = event.roomId ?: return

        // Find all players in the room
        val playersInRoom = worldState.getPlayersInRoom(roomId)

        // Send event to each player's session (except excluded player)
        playersInRoom.forEach { player ->
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
     * Handle a specific intent and return response, new state, and optional event
     */
    private suspend fun handleIntent(
        playerId: PlayerId,
        playerState: PlayerState,
        intent: Intent
    ): Triple<String, WorldState, GameEvent?> {
        val currentRoom = worldState.getCurrentRoom(playerId) ?: return Triple(
            "Error: Current room not found",
            worldState,
            null
        )

        return when (intent) {
            is Intent.Move -> handleMove(playerId, playerState, intent.direction)
            is Intent.Scout -> Triple("Scout not yet integrated with world generation system", worldState, null)
            is Intent.Travel -> Triple("Travel not yet integrated with world generation system", worldState, null)
            is Intent.Look -> handleLook(playerId, playerState, intent.target)
            is Intent.Search -> handleSearch(playerId, playerState, intent.target, currentRoom)
            is Intent.Rest -> Triple("Rest not yet supported in multi-user mode", worldState, null)
            is Intent.LootCorpse -> Triple("Corpse looting not yet supported in multi-user mode", worldState, null)
            is Intent.Craft -> Triple("Crafting not yet supported in multi-user mode", worldState, null)
            is Intent.Pickpocket -> Triple("Pickpocketing not yet supported in multi-user mode", worldState, null)
            is Intent.Trade -> Triple("Trading not yet supported in multi-user mode", worldState, null)
            is Intent.UseItem -> Triple("Advanced item use not yet supported in multi-user mode", worldState, null)
            is Intent.Attack -> handleAttack(playerId, playerState, intent.target)
            is Intent.Talk -> handleTalk(playerId, playerState, intent.target)
            is Intent.Take -> handleTake(playerId, playerState, intent.target, currentRoom)
            is Intent.TakeAll -> handleTakeAll(playerId, playerState, currentRoom)
            is Intent.Drop -> handleDrop(playerId, playerState, intent.target, currentRoom)
            is Intent.Give -> handleGive(playerId, playerState, intent.itemTarget, intent.npcTarget, currentRoom)
            is Intent.Equip -> handleEquip(playerId, playerState, intent.target)
            is Intent.Use -> handleUse(playerId, playerState, intent.target)
            is Intent.Check -> handleCheck(playerId, playerState, intent.target, currentRoom)
            is Intent.Persuade -> handlePersuade(playerId, playerState, intent.target, currentRoom)
            is Intent.Intimidate -> handleIntimidate(playerId, playerState, intent.target, currentRoom)
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
        // V2: No modal combat, movement always allowed
        val oldRoomId = playerState.currentRoomId
        val newWorldState = worldState.movePlayer(playerId, direction)

        return if (newWorldState != null) {
            val newPlayerState = newWorldState.getPlayer(playerId)!!
            val newRoomId = newPlayerState.currentRoomId
            val newRoom = newWorldState.getRoom(newRoomId)!!

            // Generate room description
            val description = roomDescriptionGenerator.generateDescription(newRoom)

            // Track room exploration for quests
            val questResult = trackQuests(newPlayerState, QuestAction.VisitedRoom(newRoomId))
            val finalWorldState = questResult.updatedWorld

            // Create movement events
            val leaveEvent = GameEvent.PlayerMoved(
                playerId = playerId,
                playerName = playerState.name,
                fromRoomId = oldRoomId,
                toRoomId = newRoomId,
                direction = direction.name.lowercase(),
                roomId = oldRoomId,
                excludePlayer = playerId
            )

            val enterEvent = GameEvent.PlayerJoined(
                playerId = playerId,
                playerName = playerState.name,
                roomId = newRoomId,
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
        val currentRoom = worldState.getCurrentRoom(playerId)!!

        return if (target == null) {
            // Room description generator should handle all room contents
            val description = roomDescriptionGenerator.generateDescription(currentRoom)
            Triple(description, worldState, null)
        } else {
            // Look at specific entity
            val entity = currentRoom.entities.find { it.name.equals(target, ignoreCase = true) }

            if (entity != null) {
                Triple(entity.description, worldState, null)
            } else {
                // Try scenery
                val roomDescription = roomDescriptionGenerator.generateDescription(currentRoom)
                val sceneryDescription = sceneryGenerator.describeScenery(target, currentRoom, roomDescription)
                val response = sceneryDescription ?: "You don't see that here."
                Triple(response, worldState, null)
            }
        }
    }

    private fun handleSearch(
        playerId: PlayerId,
        playerState: PlayerState,
        target: String?,
        currentRoom: Room
    ): Triple<String, WorldState, GameEvent?> {
        val searchMessage = "You search the area carefully${if (target != null) ", focusing on the $target" else ""}..."

        // Perform a Wisdom (Perception) skill check to find hidden items
        val result = skillCheckResolver.checkPlayer(
            playerState,
            StatType.WISDOM,
            Difficulty.MEDIUM  // DC 15 for finding hidden items
        )

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

                // Find items in the room
                val hiddenItems = currentRoom.entities.filterIsInstance<Entity.Item>().filter { !it.isPickupable }
                val pickupableItems = currentRoom.entities.filterIsInstance<Entity.Item>().filter { it.isPickupable }

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
        val currentRoom = worldState.getCurrentRoom(playerId)!!
        val npc = currentRoom.entities.filterIsInstance<Entity.NPC>()
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
        itemId: String,
        currentRoom: Room
    ): Triple<String, WorldState, GameEvent?> {
        val item = currentRoom.entities.filterIsInstance<Entity.Item>()
            .find { it.name.equals(itemId, ignoreCase = true) }

        return if (item != null && item.isPickupable) {
            val updatedPlayer = playerState.addToInventory(item)
            val updatedRoom = currentRoom.removeEntity(item.id)
            val newWorldState = worldState.updatePlayer(updatedPlayer).updateRoom(updatedRoom)

            // Track item collection for quests
            val questResult = trackQuests(updatedPlayer, QuestAction.CollectedItem(item.id))

            val event = GameEvent.GenericAction(
                playerId = playerId,
                playerName = playerState.name,
                actionDescription = "picks up ${item.name}",
                roomId = currentRoom.id,
                excludePlayer = playerId
            )

            Triple("You take the ${item.name}." + questResult.notifications, questResult.updatedWorld, event)
        } else if (item != null && !item.isPickupable) {
            Triple("That's part of the environment and can't be taken.", worldState, null)
        } else {
            // Not an item - check if it's scenery (room trait or entity)
            val isScenery = currentRoom.traits.any { it.lowercase().contains(itemId.lowercase()) } ||
                           currentRoom.entities.any { it.name.lowercase().contains(itemId.lowercase()) }
            if (isScenery) {
                Triple("That's part of the environment and can't be taken.", worldState, null)
            } else {
                Triple("You don't see that here.", worldState, null)
            }
        }
    }

    private fun handleTakeAll(
        playerId: PlayerId,
        playerState: PlayerState,
        currentRoom: Room
    ): Triple<String, WorldState, GameEvent?> {
        val items = currentRoom.entities.filterIsInstance<Entity.Item>().filter { it.isPickupable }

        return if (items.isEmpty()) {
            Triple("There are no items to take here.", worldState, null)
        } else {
            var currentPlayer = playerState
            var currentWorld = worldState
            var currentRoom = currentRoom
            val takenItems = mutableListOf<String>()
            var allQuestNotifications = ""

            items.forEach { item ->
                currentPlayer = currentPlayer.addToInventory(item)
                currentRoom = currentRoom.removeEntity(item.id)
                takenItems.add(item.name)

                // Track item collection for quests
                val questResult = trackQuests(currentPlayer, QuestAction.CollectedItem(item.id))
                currentPlayer = questResult.updatedPlayer
                currentWorld = questResult.updatedWorld
                allQuestNotifications += questResult.notifications
            }

            val newWorldState = currentWorld.updatePlayer(currentPlayer).updateRoom(currentRoom)

            val message = buildString {
                takenItems.forEach { append("You take the $it.\n") }
                append("\nYou took ${items.size} item${if (items.size > 1) "s" else ""}.")
                append(allQuestNotifications)
            }

            val event = GameEvent.GenericAction(
                playerId = playerId,
                playerName = playerState.name,
                actionDescription = "picks up all items",
                roomId = currentRoom.id,
                excludePlayer = playerId
            )

            Triple(message, newWorldState, event)
        }
    }

    private fun handleDrop(
        playerId: PlayerId,
        playerState: PlayerState,
        itemId: String,
        currentRoom: Room
    ): Triple<String, WorldState, GameEvent?> {
        // First check inventory
        var item = playerState.inventory.find { it.name.equals(itemId, ignoreCase = true) }
        var isEquippedWeapon = false
        var isEquippedArmor = false

        // Check equipped weapon
        if (item == null && playerState.equippedWeapon != null &&
            playerState.equippedWeapon!!.name.equals(itemId, ignoreCase = true)) {
            item = playerState.equippedWeapon
            isEquippedWeapon = true
        }

        // Check equipped armor
        if (item == null && playerState.equippedArmor != null &&
            playerState.equippedArmor!!.name.equals(itemId, ignoreCase = true)) {
            item = playerState.equippedArmor
            isEquippedArmor = true
        }

        return if (item != null) {
            val updatedPlayer = when {
                isEquippedWeapon -> playerState.copy(equippedWeapon = null)
                isEquippedArmor -> playerState.copy(equippedArmor = null)
                else -> playerState.removeFromInventory(item.id)
            }
            val updatedRoom = currentRoom.addEntity(item)
            val newWorldState = worldState.updatePlayer(updatedPlayer).updateRoom(updatedRoom)

            val event = GameEvent.GenericAction(
                playerId = playerId,
                playerName = playerState.name,
                actionDescription = "drops ${item.name}",
                roomId = currentRoom.id,
                excludePlayer = playerId
            )

            Triple("You drop the ${item.name}.", newWorldState, event)
        } else {
            Triple("You don't have that item.", worldState, null)
        }
    }

    private fun handleGive(
        playerId: PlayerId,
        playerState: PlayerState,
        itemTarget: String,
        npcTarget: String,
        currentRoom: Room
    ): Triple<String, WorldState, GameEvent?> {
        // Find the item in inventory
        val item = playerState.inventory.find { invItem ->
            invItem.name.lowercase().contains(itemTarget.lowercase()) ||
            invItem.id.lowercase().contains(itemTarget.lowercase())
        }

        if (item == null) {
            return Triple("You don't have that item.", worldState, null)
        }

        // Find the NPC in the room
        val npc = currentRoom.entities.filterIsInstance<Entity.NPC>()
            .find { entity ->
                entity.name.lowercase().contains(npcTarget.lowercase()) ||
                entity.id.lowercase().contains(npcTarget.lowercase())
            }

        if (npc == null) {
            return Triple("There's no one here by that name.", worldState, null)
        }

        // Remove item from inventory
        val updatedPlayer = playerState.removeFromInventory(item.id)

        // Track delivery for quests
        val questResult = trackQuests(updatedPlayer, QuestAction.DeliveredItem(item.id, npc.id))

        val event = GameEvent.GenericAction(
            playerId = playerId,
            playerName = playerState.name,
            actionDescription = "gives ${item.name} to ${npc.name}",
            roomId = currentRoom.id,
            excludePlayer = playerId
        )

        return Triple("You give the ${item.name} to ${npc.name}." + questResult.notifications, questResult.updatedWorld, event)
    }

    private fun handleEquip(
        playerId: PlayerId,
        playerState: PlayerState,
        itemId: String
    ): Triple<String, WorldState, GameEvent?> {
        val item = playerState.inventory.find { it.name.equals(itemId, ignoreCase = true) }

        return if (item != null) {
            when (item.itemType) {
                ItemType.WEAPON -> {
                    val updatedPlayer = playerState.equipWeapon(item)
                    val newWorldState = worldState.updatePlayer(updatedPlayer)
                    Triple("You equip the ${item.name}.", newWorldState, null)
                }
                ItemType.ARMOR -> {
                    val updatedPlayer = playerState.equipArmor(item)
                    val newWorldState = worldState.updatePlayer(updatedPlayer)
                    Triple("You equip the ${item.name}.", newWorldState, null)
                }
                else -> Triple("You can't equip that.", worldState, null)
            }
        } else {
            Triple("You don't have that item.", worldState, null)
        }
    }

    private fun handleUse(
        playerId: PlayerId,
        playerState: PlayerState,
        itemId: String
    ): Triple<String, WorldState, GameEvent?> {
        val item = playerState.inventory.find { it.name.equals(itemId, ignoreCase = true) }

        return if (item != null && item.isConsumable) {
            val oldHealth = playerState.health
            val inCombat = false  // V2: No modal combat

            // Consume the item and heal
            var updatedPlayer = playerState.useConsumable(item)
            val healedAmount = updatedPlayer.health - oldHealth

            val message = buildString {
                if (healedAmount > 0) {
                    append("You consume the ${item.name} and restore $healedAmount HP.\n")
                    append("Current health: ${updatedPlayer.health}/${updatedPlayer.maxHealth}")
                } else {
                    append("You consume the ${item.name}, but you're already at full health.")
                }

                // V2: No modal combat in multi-user mode, consumables simply heal
            }

            val newWorldState = worldState.updatePlayer(updatedPlayer)
            Triple(message, newWorldState, null)
        } else {
            Triple("You can't use that.", worldState, null)
        }
    }

    /**
     * Calculate damage dealt by NPC attack (helper for potion use during combat).
     * Base damage + STR modifier - player armor defense.
     */
    private fun calculateNpcDamage(npc: Entity.NPC, player: PlayerState): Int {
        // Base damage 3-12 + STR modifier - armor defense
        val baseDamage = kotlin.random.Random.nextInt(3, 13)
        val strModifier = npc.stats.strModifier()
        val armorDefense = player.getArmorDefenseBonus()
        return (baseDamage + strModifier - armorDefense).coerceAtLeast(1)
    }

    private fun handleCheck(
        playerId: PlayerId,
        playerState: PlayerState,
        targetId: String,
        currentRoom: Room
    ): Triple<String, WorldState, GameEvent?> {
        // Normalize target for matching (replace underscores with spaces)
        val normalizedTarget = targetId.lowercase().replace("_", " ")

        // Find the feature in the room with flexible matching
        val feature = currentRoom.entities.filterIsInstance<Entity.Feature>()
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
        val updatedRoom = currentRoom.removeEntity(feature.id).addEntity(updatedFeature)
        val newWorldState = worldState.updateRoom(updatedRoom)

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
        targetId: String,
        currentRoom: Room
    ): Triple<String, WorldState, GameEvent?> {
        val npc = currentRoom.entities.filterIsInstance<Entity.NPC>()
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
        val updatedRoom = currentRoom.removeEntity(npc.id).addEntity(updatedNpc)
        val newWorldState = worldState.updateRoom(updatedRoom)
        return Triple(description, newWorldState, null)
    }

    private fun handleIntimidate(
        playerId: PlayerId,
        playerState: PlayerState,
        targetId: String,
        currentRoom: Room
    ): Triple<String, WorldState, GameEvent?> {
        val npc = currentRoom.entities.filterIsInstance<Entity.NPC>()
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
        val updatedRoom = currentRoom.removeEntity(npc.id).addEntity(updatedNpc)
        val newWorldState = worldState.updateRoom(updatedRoom)
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
