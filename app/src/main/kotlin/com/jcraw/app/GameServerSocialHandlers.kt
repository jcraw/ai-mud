@file:Suppress("TooManyFunctions", "LongParameterList", "WildcardImport", "UnusedParameter", "ReturnCount")

package com.jcraw.app

import com.jcraw.mud.core.*
import com.jcraw.mud.reasoning.QuestAction

/**
 * Social handlers (talk/check/persuade/intimidate) for [GameServer]. Pure extract.
 */
object GameServerSocialHandlers {

    suspend fun handleTalk(
        server: GameServer,
        playerId: PlayerId,
        playerState: PlayerState,
        targetId: String
    ): Triple<String, WorldState, GameEvent?> {
        val spaceId = playerState.currentRoomId
        val npc = server.worldState.getEntitiesInSpace(spaceId)
            .filterIsInstance<Entity.NPC>()
            .find { it.name.equals(targetId, ignoreCase = true) }
        if (npc == null) {
            return Triple("There's no one here by that name.", server.worldState, null)
        }
        val dialogue = server.npcInteractionGenerator.generateDialogue(npc, playerState)
        val quest = GameServerQuestSupport.trackQuests(
            server, playerState, QuestAction.TalkedToNPC(npc.id)
        )
        return Triple(dialogue + quest.notifications, quest.updatedWorld, null)
    }

    fun handleCheck(
        server: GameServer,
        playerId: PlayerId,
        playerState: PlayerState,
        targetId: String
    ): Triple<String, WorldState, GameEvent?> {
        val spaceId = playerState.currentRoomId
        val entities = server.worldState.getEntitiesInSpace(spaceId)
        val feature = findFeature(entities, targetId)
        if (feature == null || feature.skillChallenge == null) {
            return Triple("There's nothing here to check.", server.worldState, null)
        }
        if (feature.isCompleted) {
            return Triple("You've already overcome this challenge.", server.worldState, null)
        }
        return resolveSkillCheck(server, playerState, spaceId, feature)
    }

    private fun findFeature(entities: List<Entity>, targetId: String): Entity.Feature? {
        val target = targetId.lowercase().replace("_", " ")
        return entities.filterIsInstance<Entity.Feature>().find { matchesFeature(it, target) }
    }

    private fun matchesFeature(entity: Entity.Feature, target: String): Boolean {
        val name = entity.name.lowercase()
        val id = entity.id.lowercase().replace("_", " ")
        return name.contains(target) || id.contains(target) ||
            target.contains(name) || target.contains(id) ||
            target.split(" ").all { word -> name.contains(word) || id.contains(word) }
    }

    private fun resolveSkillCheck(
        server: GameServer,
        playerState: PlayerState,
        spaceId: SpaceId,
        feature: Entity.Feature
    ): Triple<String, WorldState, GameEvent?> {
        val challenge = feature.skillChallenge
            ?: return Triple("There's nothing here to check.", server.worldState, null)
        val result = server.skillCheckResolver.checkPlayer(
            playerState, challenge.statType, challenge.difficulty
        )
        val description = buildCheckDescription(result, challenge)
        val newWorld = applyFeatureResult(server, spaceId, feature, result.success)
        val quest = if (result.success) {
            GameServerQuestSupport.trackQuests(
                server, playerState, QuestAction.UsedSkill(feature.id)
            )
        } else {
            QuestTrackingResult(playerState, newWorld, "")
        }
        return Triple(description + quest.notifications, quest.updatedWorld, null)
    }

    private fun applyFeatureResult(
        server: GameServer,
        spaceId: SpaceId,
        feature: Entity.Feature,
        success: Boolean
    ): WorldState {
        val updated = if (success) feature.copy(isCompleted = true) else feature
        return server.worldState
            .removeEntityFromSpace(spaceId, feature.id)
            .addEntityToSpace(spaceId, updated)
    }

    private fun buildCheckDescription(
        result: SkillCheckResult,
        challenge: SkillChallenge
    ): String = buildString {
        append("You rolled ${result.roll} + ${result.modifier} = ${result.total} vs DC ${result.dc}\n")
        if (result.isCriticalSuccess) append("Critical success! ")
        if (result.isCriticalFailure) append("Critical failure! ")
        append(if (result.success) challenge.successDescription else challenge.failureDescription)
    }

    fun handlePersuade(
        server: GameServer,
        playerId: PlayerId,
        playerState: PlayerState,
        targetId: String
    ): Triple<String, WorldState, GameEvent?> {
        val spaceId = playerState.currentRoomId
        val npc = findNpc(server, spaceId, targetId)
        if (npc == null || npc.persuasionChallenge == null) {
            return Triple("You can't persuade that.", server.worldState, null)
        }
        if (npc.hasBeenPersuaded) {
            return Triple("${npc.name} has already been persuaded.", server.worldState, null)
        }
        return resolveNpcChallenge(
            server, playerState, spaceId, npc, npc.persuasionChallenge!!, markPersuaded = true
        )
    }

    fun handleIntimidate(
        server: GameServer,
        playerId: PlayerId,
        playerState: PlayerState,
        targetId: String
    ): Triple<String, WorldState, GameEvent?> {
        val spaceId = playerState.currentRoomId
        val npc = findNpc(server, spaceId, targetId)
        if (npc == null || npc.intimidationChallenge == null) {
            return Triple("You can't intimidate that.", server.worldState, null)
        }
        if (npc.hasBeenIntimidated) {
            return Triple("${npc.name} has already been intimidated.", server.worldState, null)
        }
        return resolveNpcChallenge(
            server, playerState, spaceId, npc, npc.intimidationChallenge!!, markIntimidated = true
        )
    }

    private fun findNpc(server: GameServer, spaceId: SpaceId, targetId: String): Entity.NPC? =
        server.worldState.getEntitiesInSpace(spaceId)
            .filterIsInstance<Entity.NPC>()
            .find { it.name.equals(targetId, ignoreCase = true) }

    private fun resolveNpcChallenge(
        server: GameServer,
        playerState: PlayerState,
        spaceId: SpaceId,
        npc: Entity.NPC,
        challenge: SkillChallenge,
        markPersuaded: Boolean = false,
        markIntimidated: Boolean = false
    ): Triple<String, WorldState, GameEvent?> {
        val result = server.skillCheckResolver.checkPlayer(
            playerState, challenge.statType, challenge.difficulty
        )
        val description = buildCheckDescription(result, challenge)
        val updated = when {
            result.success && markPersuaded -> npc.copy(hasBeenPersuaded = true)
            result.success && markIntimidated -> npc.copy(hasBeenIntimidated = true)
            else -> npc
        }
        val newWorld = server.worldState
            .removeEntityFromSpace(spaceId, npc.id)
            .addEntityToSpace(spaceId, updated)
        return Triple(description, newWorld, null)
    }
}
