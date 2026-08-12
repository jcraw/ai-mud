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

package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.Direction
import com.jcraw.mud.core.GameEvent

/**
 * Thin facade for client movement handlers.
 * Public handle* names preserved for EngineGameClient dispatch; bodies live in cluster extracts.
 * Interact stub stays here (intentional app/client parity gap — app Interact is SkillQuest).
 */
object ClientMovementHandlers {

    fun handleMove(game: EngineGameClient, direction: Direction) =
        ClientMovementMoveHandlers.handleMove(game, direction)

    fun handleLook(game: EngineGameClient, target: String?) =
        ClientMovementLookHandlers.handleLook(game, target)

    fun handleSearch(game: EngineGameClient, target: String?) =
        ClientMovementSearchHandlers.handleSearch(game, target)

    fun handleInteract(game: EngineGameClient, target: String) {
        game.emitEvent(
            GameEvent.System(
                "Interaction system not yet implemented. (Target: $target)",
                GameEvent.MessageLevel.INFO
            )
        )
    }

    fun handleTravel(game: EngineGameClient, rawDirection: String) =
        ClientMovementTravelHandlers.handleTravel(game, rawDirection)

    fun handleScout(game: EngineGameClient, rawDirection: String?) =
        ClientMovementScoutHandlers.handleScout(game, rawDirection)
}
