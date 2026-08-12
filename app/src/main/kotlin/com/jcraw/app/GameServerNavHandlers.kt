@file:Suppress("TooManyFunctions", "LongParameterList", "WildcardImport", "UnusedParameter")

package com.jcraw.app

import com.jcraw.mud.core.*
import com.jcraw.mud.reasoning.QuestAction

/**
 * Navigation handlers (move/look/search/attack stub) for [GameServer]. Pure extract.
 */
object GameServerNavHandlers {

    suspend fun handleMove(
        server: GameServer,
        playerId: PlayerId,
        playerState: PlayerState,
        direction: Direction
    ): Triple<String, WorldState, GameEvent?> {
        val oldSpaceId = playerState.currentRoomId
        val skills = server.skillManager.getSkillComponent(playerId)
        val moved = server.worldState.movePlayerV3(playerId, direction, skills)
        return if (moved != null) {
            completeMove(server, playerId, playerState, direction, oldSpaceId, moved)
        } else {
            Triple("You can't go that way.", server.worldState, null)
        }
    }

    private suspend fun completeMove(
        server: GameServer,
        playerId: PlayerId,
        playerState: PlayerState,
        direction: Direction,
        oldSpaceId: SpaceId,
        newWorld: WorldState
    ): Triple<String, WorldState, GameEvent?> {
        val newPlayer = newWorld.getPlayer(playerId)!!
        val newSpaceId = newPlayer.currentRoomId
        val newSpace = newWorld.getSpace(newSpaceId)!!
        val description = if (newSpace.description.isNotBlank()) {
            newSpace.description
        } else {
            "You arrive at a new location. The area awaits exploration."
        }
        val quest = GameServerQuestSupport.trackQuests(
            server, newPlayer, QuestAction.VisitedRoom(newSpaceId)
        )
        broadcastMove(server, playerId, playerState, direction, oldSpaceId, newSpaceId)
        return Triple(description + quest.notifications, quest.updatedWorld, null)
    }

    private suspend fun broadcastMove(
        server: GameServer,
        playerId: PlayerId,
        playerState: PlayerState,
        direction: Direction,
        oldSpaceId: SpaceId,
        newSpaceId: SpaceId
    ) {
        server.broadcastEvent(
            GameEvent.PlayerMoved(
                playerId = playerId,
                playerName = playerState.name,
                fromRoomId = oldSpaceId,
                toRoomId = newSpaceId,
                direction = direction.name.lowercase(),
                roomId = oldSpaceId,
                excludePlayer = playerId
            )
        )
        server.broadcastEvent(
            GameEvent.PlayerJoined(
                playerId = playerId,
                playerName = playerState.name,
                roomId = newSpaceId,
                excludePlayer = playerId
            )
        )
    }

    suspend fun handleLook(
        server: GameServer,
        playerId: PlayerId,
        playerState: PlayerState,
        target: String?
    ): Triple<String, WorldState, GameEvent?> {
        val currentSpace = server.worldState.getCurrentSpace(playerId)!!
        val spaceId = playerState.currentRoomId
        if (target == null) {
            val description = if (currentSpace.description.isNotBlank()) {
                currentSpace.description
            } else {
                "You see an unexplored area."
            }
            return Triple(description, server.worldState, null)
        }
        return lookAtEntity(server, spaceId, target)
    }

    private fun lookAtEntity(
        server: GameServer,
        spaceId: SpaceId,
        target: String
    ): Triple<String, WorldState, GameEvent?> {
        val entity = server.worldState.getEntitiesInSpace(spaceId)
            .find { it.name.equals(target, ignoreCase = true) }
        return if (entity != null) {
            Triple(entity.description, server.worldState, null)
        } else {
            Triple("You don't see that here.", server.worldState, null)
        }
    }

    fun handleSearch(
        server: GameServer,
        playerId: PlayerId,
        playerState: PlayerState,
        target: String?
    ): Triple<String, WorldState, GameEvent?> {
        val searchMessage =
            "You search the area carefully${if (target != null) ", focusing on the $target" else ""}..."
        val result = server.skillCheckResolver.checkPlayer(
            playerState, StatType.WISDOM, Difficulty.MEDIUM
        )
        val entities = server.worldState.getEntitiesInSpace(playerState.currentRoomId)
        return Triple(buildSearchDescription(searchMessage, result, entities), server.worldState, null)
    }

    private fun buildSearchDescription(
        searchMessage: String,
        result: SkillCheckResult,
        entities: List<Entity>
    ): String = buildString {
        append("$searchMessage\n\n")
        append("Rolling Perception check...\n")
        append(
            "d20 roll: ${result.roll} + WIS modifier: ${result.modifier} = " +
                "${result.total} vs DC ${result.dc}\n"
        )
        if (result.isCriticalSuccess) append("\n🎲 CRITICAL SUCCESS! (Natural 20)\n")
        else if (result.isCriticalFailure) append("\n💀 CRITICAL FAILURE! (Natural 1)\n")
        if (result.success) appendSearchSuccess(entities)
        else {
            append("\n❌ Failure!\n")
            append("You don't find anything of interest.")
        }
    }

    private fun StringBuilder.appendSearchSuccess(entities: List<Entity>) {
        append("\n✅ Success!\n")
        val hidden = entities.filterIsInstance<Entity.Item>().filter { !it.isPickupable }
        val pickup = entities.filterIsInstance<Entity.Item>().filter { it.isPickupable }
        if (hidden.isEmpty() && pickup.isEmpty()) {
            append("You don't find anything hidden here.")
            return
        }
        if (pickup.isNotEmpty()) {
            append("You find the following items:\n")
            pickup.forEach { append("  - ${it.name}: ${it.description}\n") }
        }
        if (hidden.isNotEmpty()) {
            append("\nYou also notice some interesting features:\n")
            hidden.forEach { append("  - ${it.name}: ${it.description}\n") }
        }
    }

    suspend fun handleAttack(
        server: GameServer,
        playerId: PlayerId,
        playerState: PlayerState,
        targetId: String?
    ): Triple<String, WorldState, GameEvent?> = Triple(
        "Combat is not yet supported in multi-user mode. Coming soon!",
        server.worldState,
        null
    )
}
