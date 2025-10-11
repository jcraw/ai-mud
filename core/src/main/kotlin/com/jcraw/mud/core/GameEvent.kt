package com.jcraw.mud.core

import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

/**
 * Sealed class representing all possible game events that can be emitted to clients.
 * Used for structured output formatting in UI and other non-console clients.
 */
@Serializable
sealed class GameEvent {
    /**
     * Narrative text describing the game world, actions, or outcomes.
     */
    @Serializable
    data class Narrative(val text: String, val timestamp: Long = Clock.System.now().toEpochMilliseconds()) : GameEvent()

    /**
     * Player action echo - shows what the player typed/did.
     */
    @Serializable
    data class PlayerAction(val text: String, val timestamp: Long = Clock.System.now().toEpochMilliseconds()) : GameEvent()

    /**
     * Combat-related events with special formatting needs.
     */
    @Serializable
    data class Combat(val text: String, val damage: Int? = null, val timestamp: Long = Clock.System.now().toEpochMilliseconds()) : GameEvent()

    /**
     * System messages (errors, help text, meta information).
     */
    @Serializable
    data class System(val text: String, val level: MessageLevel = MessageLevel.INFO, val timestamp: Long = Clock.System.now().toEpochMilliseconds()) : GameEvent()

    /**
     * Quest-related events (new quest, objective update, completion).
     */
    @Serializable
    data class Quest(val text: String, val questId: String? = null, val timestamp: Long = Clock.System.now().toEpochMilliseconds()) : GameEvent()

    /**
     * Status updates (HP change, stat changes, equipment changes).
     */
    @Serializable
    data class StatusUpdate(val hp: Int? = null, val maxHp: Int? = null, val location: String? = null, val timestamp: Long = Clock.System.now().toEpochMilliseconds()) : GameEvent()

    @Serializable
    enum class MessageLevel {
        INFO, WARNING, ERROR
    }
}
