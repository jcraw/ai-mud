@file:Suppress("LongParameterList", "MaxLineLength")

package com.jcraw.app

import com.jcraw.mud.core.*
import com.jcraw.mud.core.repository.ItemRepository
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
    internal var worldState: WorldState,
    private val memoryManager: MemoryManager,
    private val roomDescriptionGenerator: RoomDescriptionGenerator,
    internal val npcInteractionGenerator: NPCInteractionGenerator,
    private val combatResolver: CombatResolver,
    private val combatNarrator: CombatNarrator,
    internal val skillCheckResolver: SkillCheckResolver,
    private val sceneryGenerator: SceneryDescriptionGenerator,
    internal val skillManager: com.jcraw.mud.reasoning.skill.SkillManager,
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
    internal val questTracker = QuestTracker(dispositionManager)

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
    internal suspend fun broadcastEvent(event: GameEvent) {
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
            is Intent.Move -> GameServerNavHandlers.handleMove(this, playerId, playerState, intent.direction)
            is Intent.Scout -> stub("Scout not yet integrated with world generation system")
            is Intent.Travel -> stub("Travel not yet integrated with world generation system")
            is Intent.Look -> GameServerNavHandlers.handleLook(this, playerId, playerState, intent.target)
            is Intent.Search -> GameServerNavHandlers.handleSearch(this, playerId, playerState, intent.target)
            is Intent.Rest -> stub("Rest not yet supported in multi-user mode")
            is Intent.LootCorpse -> stub("Corpse looting not yet supported in multi-user mode")
            is Intent.Craft -> stub("Crafting not yet supported in multi-user mode")
            is Intent.Pickpocket -> stub("Pickpocketing not yet supported in multi-user mode")
            is Intent.Trade -> stub("Trading not yet supported in multi-user mode")
            is Intent.UseItem -> stub("Advanced item use not yet supported in multi-user mode")
            is Intent.Attack -> GameServerNavHandlers.handleAttack(this, playerId, playerState, intent.target)
            is Intent.Flee -> stub("Flee not yet supported in multi-user mode")
            is Intent.Talk -> GameServerSocialHandlers.handleTalk(this, playerId, playerState, intent.target)
            is Intent.TakeTreasure -> stub("Treasure rooms not yet supported in multi-user mode")
            is Intent.ReturnTreasure -> stub("Treasure rooms not yet supported in multi-user mode")
            is Intent.ExaminePedestal -> stub("Treasure rooms not yet supported in multi-user mode")
            is Intent.Take -> GameServerItemTake.handleTake(this, playerId, playerState, intent.target)
            is Intent.TakeAll -> GameServerItemTake.handleTakeAll(this, playerId, playerState)
            is Intent.Drop -> GameServerItemHandlers.handleDrop(this, playerId, playerState, intent.target)
            is Intent.Give -> GameServerItemHandlers.handleGive(this, playerId, playerState, intent.itemTarget, intent.npcTarget)
            is Intent.Equip -> GameServerItemHandlers.handleEquip(this, playerId, playerState, intent.target)
            is Intent.Use -> GameServerItemHandlers.handleUse(this, playerId, playerState, intent.target)
            is Intent.Check -> GameServerSocialHandlers.handleCheck(this, playerId, playerState, intent.target)
            is Intent.Persuade -> GameServerSocialHandlers.handlePersuade(this, playerId, playerState, intent.target)
            is Intent.Intimidate -> GameServerSocialHandlers.handleIntimidate(this, playerId, playerState, intent.target)
            is Intent.Emote -> stub("Emote system not yet fully supported in multi-user mode")
            is Intent.Say -> stub("Say is not yet supported in multi-user mode")
            is Intent.AskQuestion -> stub("Ask system not yet fully supported in multi-user mode")
            is Intent.UseSkill -> stub("Skill system not yet fully supported in multi-user mode")
            is Intent.TrainSkill -> stub("Skill system not yet fully supported in multi-user mode")
            is Intent.ChoosePerk -> stub("Skill system not yet fully supported in multi-user mode")
            is Intent.ViewSkills -> stub("Skill system not yet fully supported in multi-user mode")
            is Intent.Inventory -> Triple(formatInventory(playerState), worldState, null)
            is Intent.Save -> stub("Save not supported in multi-user mode")
            is Intent.Load -> stub("Load not supported in multi-user mode")
            is Intent.Quests -> Triple(GameServerQuestSupport.formatQuests(playerState), worldState, null)
            is Intent.AcceptQuest -> stub("Quest system not yet supported in multi-user mode")
            is Intent.AbandonQuest -> stub("Quest system not yet supported in multi-user mode")
            is Intent.ClaimReward -> stub("Quest system not yet supported in multi-user mode")
            is Intent.Help -> Triple(getHelpText(), worldState, null)
            is Intent.Quit -> Triple("Goodbye!", worldState, null)
            is Intent.Invalid -> Triple(intent.message, worldState, null)
            is Intent.Interact -> stub("You need to be more specific about how you want to interact.")
        }
    }

    private fun stub(msg: String) = Triple(msg, worldState, null as GameEvent?)

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
