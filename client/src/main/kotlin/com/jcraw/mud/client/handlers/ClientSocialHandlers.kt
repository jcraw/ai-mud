@file:Suppress(
    "ReturnCount",
    "MagicNumber",
    "MaxLineLength",
    "TooManyFunctions",
    "LongMethod",
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "LongParameterList",
    "UnusedParameter"
)

package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.GameEvent

/**
 * Thin facade for GUI social handlers.
 * Public handle* + isQuestion preserved; dialogue in ClientSocial* extracts (MUD-034l).
 * Persuade / intimidate / check stay stubs (no port from app).
 */
object ClientSocialHandlers {

    fun handleTalk(game: EngineGameClient, target: String) =
        ClientSocialDialogueHandlers.handleTalk(game, target)

    suspend fun handleSay(game: EngineGameClient, message: String, npcTarget: String?) =
        ClientSocialDialogueHandlers.handleSay(game, message, npcTarget)

    fun handleEmote(game: EngineGameClient, emoteType: String, target: String?) =
        ClientSocialDialogueHandlers.handleEmote(game, emoteType, target)

    suspend fun handleAskQuestion(game: EngineGameClient, npcTarget: String, topic: String) =
        ClientSocialDialogueHandlers.handleAskQuestion(game, npcTarget, topic)

    fun handleCheck(game: EngineGameClient, target: String) {
        game.emitEvent(GameEvent.System("Skill check system integrated - implement if needed", GameEvent.MessageLevel.INFO))
    }

    fun handlePersuade(game: EngineGameClient, target: String) {
        game.emitEvent(GameEvent.System("Persuasion system integrated - implement if needed", GameEvent.MessageLevel.INFO))
    }

    fun handleIntimidate(game: EngineGameClient, target: String) {
        game.emitEvent(GameEvent.System("Intimidation system integrated - implement if needed", GameEvent.MessageLevel.INFO))
    }

    fun isQuestion(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.endsWith("?")) return true

        val lower = trimmed.lowercase()
        val questionPrefixes = listOf(
            "who", "what", "where", "when", "why", "how",
            "can", "will", "is", "are", "am",
            "do", "does", "did",
            "should", "could", "would",
            "have", "has", "had",
            "tell me", "explain", "describe"
        )

        return questionPrefixes.any { lower.startsWith(it) }
    }
}
