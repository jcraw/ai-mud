package com.jcraw.app

import com.jcraw.mud.core.PlayerId
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.BufferedReader
import java.io.PrintWriter

/**
 * Represents an individual player's connection to the game server.
 * Handles I/O for a single player and maintains player context.
 */
class PlayerSession(
    val playerId: PlayerId,
    val playerName: String,
    private val input: BufferedReader,
    private val output: PrintWriter
) {
    private val eventChannel = Channel<GameEvent>(Channel.BUFFERED)
    private val _outputFlow = MutableSharedFlow<String>(replay = 0)
    val outputFlow = _outputFlow.asSharedFlow()

    /**
     * Read a line of input from the player (blocking)
     */
    suspend fun readLine(): String? = input.readLine()

    /**
     * Send a message to this player
     */
    fun sendMessage(message: String) {
        output.println(message)
        output.flush()
    }

    /**
     * Send a game event notification to this player
     */
    suspend fun notifyEvent(event: GameEvent) {
        eventChannel.send(event)
    }

    /**
     * Process pending events and convert them to player-visible messages
     */
    suspend fun processEvents(): List<String> {
        val messages = mutableListOf<String>()
        while (!eventChannel.isEmpty) {
            val event = eventChannel.tryReceive().getOrNull() ?: break
            formatEvent(event)?.let { messages.add(it) }
        }
        return messages
    }

    /**
     * Format a GameEvent into a human-readable message for this player
     */
    private fun formatEvent(event: GameEvent): String? {
        return when (event) {
            is GameEvent.PlayerJoined -> {
                if (event.playerId != playerId) {
                    "\n[${event.playerName} has entered the area]"
                } else null
            }
            is GameEvent.PlayerLeft -> {
                if (event.playerId != playerId) {
                    "\n[${event.playerName} has left the area]"
                } else null
            }
            is GameEvent.PlayerMoved -> {
                if (event.playerId != playerId) {
                    "\n[${event.playerName} leaves ${event.direction}]"
                } else null
            }
            is GameEvent.PlayerSaid -> {
                if (event.playerId != playerId) {
                    "\n${event.playerName} says: ${event.message}"
                } else null
            }
            is GameEvent.CombatAction -> {
                if (event.playerId != playerId) {
                    "\n[${event.playerName} ${event.actionDescription}]"
                } else null
            }
            is GameEvent.PlayerDied -> {
                if (event.playerId != playerId) {
                    "\n[${event.playerName} has died!]"
                } else null
            }
            is GameEvent.GenericAction -> {
                if (event.playerId != playerId) {
                    "\n[${event.playerName} ${event.actionDescription}]"
                } else null
            }
        }
    }

    /**
     * Close this player session
     */
    fun close() {
        eventChannel.close()
        output.close()
        input.close()
    }
}
