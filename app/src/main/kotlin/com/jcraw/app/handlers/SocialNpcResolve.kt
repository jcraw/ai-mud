@file:Suppress(
    "ReturnCount",
    "MagicNumber",
    "MaxLineLength",
    "TooManyFunctions",
    "LongMethod",
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "LongParameterList"
)

package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.core.Entity

/**
 * NPC lookup / question detect for [SocialHandlers] facade (MUD-034l pure-move).
 */
internal object SocialNpcResolve {

    fun findNpcByName(game: MudGame, target: String): Entity.NPC? {
        val spaceId = game.worldState.player.currentRoomId
        return game.worldState.getEntitiesInSpace(spaceId)
            .filterIsInstance<Entity.NPC>()
            .find { entity ->
                entity.name.lowercase().contains(target.lowercase()) ||
                    entity.id.lowercase().contains(target.lowercase())
            }
    }

    fun resolveNpcTarget(game: MudGame, npcTarget: String?): Entity.NPC? {
        val spaceId = game.worldState.player.currentRoomId
        val npcs = game.worldState.getEntitiesInSpace(spaceId).filterIsInstance<Entity.NPC>()

        if (npcTarget != null) {
            val lower = npcTarget.lowercase()
            val explicit = npcs.find {
                it.name.lowercase().contains(lower) || it.id.lowercase().contains(lower)
            }
            if (explicit != null) {
                return explicit
            }
        }

        val recent = game.lastConversationNpcId
        if (recent != null) {
            return npcs.find { it.id == recent }
        }

        return null
    }

    fun isQuestion(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.endsWith("?")) return true

        val lower = trimmed.lowercase()
        val prefixes = listOf(
            "who", "what", "where", "when", "why", "how",
            "can", "will", "is", "are", "am",
            "do", "does", "did",
            "should", "could", "would",
            "have", "has", "had",
            "tell me", "explain", "describe"
        )

        return prefixes.any { lower.startsWith(it) }
    }
}
