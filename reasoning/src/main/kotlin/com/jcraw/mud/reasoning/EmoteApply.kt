package com.jcraw.mud.reasoning

import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.WorldState

/**
 * Pure apply for emote → parse keyword, process, write updated NPC into the space.
 * Shared by console and GUI. GameServer emote stays a stub.
 */
object EmoteApply {

    sealed class Result {
        data class Success(
            val world: WorldState,
            val narrative: String,
            val delta: Int,
            val npcId: String
        ) : Result()

        data class Failure(val message: String) : Result()
    }

    fun apply(
        world: WorldState,
        spaceId: String,
        npc: Entity.NPC,
        keyword: String,
        emoteHandler: EmoteHandler
    ): Result {
        val emoteType = emoteHandler.parseEmoteKeyword(keyword)
            ?: return Result.Failure("Unknown emote: $keyword")
        val (narrative, updatedNpc) = emoteHandler.processEmote(npc, emoteType, "You")
        return Result.Success(
            world = world.replaceEntityInSpace(spaceId, npc.id, updatedNpc),
            narrative = narrative,
            delta = emoteType.dispositionDelta,
            npcId = npc.id
        )
    }
}
