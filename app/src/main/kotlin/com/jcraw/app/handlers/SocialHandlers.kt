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

/**
 * Thin facade for social handlers.
 * Public handle* preserved for MudGameEngine dispatch; bodies in Social* extracts (MUD-034l).
 */
object SocialHandlers {

    fun handleTalk(game: MudGame, target: String) =
        SocialDialogueHandlers.handleTalk(game, target)

    fun handleSay(game: MudGame, message: String, npcTarget: String?) =
        SocialDialogueHandlers.handleSay(game, message, npcTarget)

    fun handleEmote(game: MudGame, emoteType: String, target: String?) =
        SocialDialogueHandlers.handleEmote(game, emoteType, target)

    suspend fun handleAskQuestion(game: MudGame, npcTarget: String, topic: String) =
        SocialDialogueHandlers.handleAskQuestion(game, npcTarget, topic)

    fun handlePersuade(game: MudGame, target: String) =
        SocialDispositionHandlers.handlePersuade(game, target)

    fun handleIntimidate(game: MudGame, target: String) =
        SocialDispositionHandlers.handleIntimidate(game, target)
}
