package com.jcraw.app

import com.jcraw.mud.core.PlayerId
import com.jcraw.mud.core.RoomId

/**
 * Events that occur in the game world that may be visible to multiple players
 */
sealed class GameEvent {
    abstract val roomId: RoomId?
    abstract val excludePlayer: PlayerId?

    data class PlayerJoined(
        val playerId: PlayerId,
        val playerName: String,
        override val roomId: RoomId,
        override val excludePlayer: PlayerId? = null
    ) : GameEvent()

    data class PlayerLeft(
        val playerId: PlayerId,
        val playerName: String,
        override val roomId: RoomId,
        override val excludePlayer: PlayerId? = null
    ) : GameEvent()

    data class PlayerMoved(
        val playerId: PlayerId,
        val playerName: String,
        val fromRoomId: RoomId,
        val toRoomId: RoomId,
        val direction: String,
        override val roomId: RoomId,
        override val excludePlayer: PlayerId? = null
    ) : GameEvent()

    data class PlayerSaid(
        val playerId: PlayerId,
        val playerName: String,
        val message: String,
        override val roomId: RoomId,
        override val excludePlayer: PlayerId? = null
    ) : GameEvent()

    data class CombatAction(
        val playerId: PlayerId,
        val playerName: String,
        val targetName: String,
        val actionDescription: String,
        override val roomId: RoomId,
        override val excludePlayer: PlayerId? = null
    ) : GameEvent()

    data class PlayerDied(
        val playerId: PlayerId,
        val playerName: String,
        override val roomId: RoomId,
        override val excludePlayer: PlayerId? = null
    ) : GameEvent()

    data class GenericAction(
        val playerId: PlayerId,
        val playerName: String,
        val actionDescription: String,
        override val roomId: RoomId,
        override val excludePlayer: PlayerId? = null
    ) : GameEvent()
}
