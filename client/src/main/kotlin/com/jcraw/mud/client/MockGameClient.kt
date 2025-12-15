package com.jcraw.mud.client

import com.jcraw.mud.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Mock game client for UI testing without real engine.
 * Simulates game responses to player inputs.
 */
class MockGameClient : GameClient {
    private val _events = MutableSharedFlow<GameEvent>(replay = 0)
    private var playerState: PlayerState? = null

    init {
        // Send welcome message
        sendEvent(GameEvent.Narrative("Welcome to the dungeon, adventurer!"))
        sendEvent(GameEvent.Narrative("You find yourself in a dark stone corridor. Flickering torches cast dancing shadows on the walls."))

        // Initialize mock player
        playerState = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "entrance",
            health = 100,
            maxHealth = 100,
            experiencePoints = 0,
            inventoryComponent = InventoryComponent(gold = 50)
        )
    }

    override suspend fun sendInput(text: String) {
        // Simple pattern matching for mock responses
        when {
            text.matches(Regex(".*\\b(look|examine)\\b.*", RegexOption.IGNORE_CASE)) -> {
                sendEvent(GameEvent.Narrative("The stone corridor extends north and south. Ancient runes cover the walls."))
            }
            text.matches(Regex(".*\\b(north|n)\\b.*", RegexOption.IGNORE_CASE)) -> {
                sendEvent(GameEvent.Narrative("You move north into a larger chamber."))
                sendEvent(GameEvent.Narrative("A skeleton warrior blocks your path!"))
            }
            text.matches(Regex(".*\\b(south|s)\\b.*", RegexOption.IGNORE_CASE)) -> {
                sendEvent(GameEvent.Narrative("You move south into a dimly lit room."))
                sendEvent(GameEvent.Narrative("You see a wooden chest in the corner."))
            }
            text.matches(Regex(".*\\b(attack|fight|kill)\\b.*", RegexOption.IGNORE_CASE)) -> {
                sendEvent(GameEvent.Combat("You swing your weapon! The skeleton takes 12 damage!", damage = 12))
                sendEvent(GameEvent.Combat("The skeleton retaliates! You take 8 damage!", damage = 8))
                playerState = playerState?.takeDamage(8)
                updateStatus()
            }
            text.matches(Regex(".*\\b(inventory|i)\\b.*", RegexOption.IGNORE_CASE)) -> {
                sendEvent(GameEvent.Narrative("Your inventory: Rusty Sword, Healing Potion"))
            }
            text.matches(Regex(".*\\b(help|\\?)\\b.*", RegexOption.IGNORE_CASE)) -> {
                sendEvent(GameEvent.System("""
                    Available commands:
                    • Movement: north, south, east, west (or n, s, e, w)
                    • Actions: look, take, attack, use
                    • Info: inventory, quests, help
                """.trimIndent()))
            }
            text.matches(Regex(".*\\b(quests?|journal)\\b.*", RegexOption.IGNORE_CASE)) -> {
                sendEvent(GameEvent.Quest("Active Quests: Defeat the Skeleton Warrior (0/1 killed)", "quest1"))
            }
            text.isNotBlank() -> {
                sendEvent(GameEvent.System("I don't understand that command. Type 'help' for assistance."))
            }
        }
    }

    override fun observeEvents(): Flow<GameEvent> = _events.asSharedFlow()

    override fun getCurrentState(): PlayerState? = playerState

    override suspend fun close() {
        // Cleanup if needed
    }

    private fun sendEvent(event: GameEvent) {
        kotlinx.coroutines.runBlocking {
            _events.emit(event)
        }
    }

    private fun updateStatus() {
        playerState?.let { state ->
            sendEvent(GameEvent.StatusUpdate(
                hp = state.health,
                maxHp = state.maxHealth,
                location = "Dungeon Corridor"
            ))
        }
    }
}
