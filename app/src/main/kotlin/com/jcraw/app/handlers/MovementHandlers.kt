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
import com.jcraw.mud.core.Direction

/**
 * Thin facade for movement handlers.
 * Public handle* names preserved for MudGameEngine dispatch; bodies live in cluster extracts.
 */
object MovementHandlers {

    fun handleMove(game: MudGame, direction: Direction) =
        MovementMoveHandlers.handleMove(game, direction)

    fun handleLook(game: MudGame, target: String?) =
        MovementLookHandlers.handleLook(game, target)

    fun handleSearch(game: MudGame, target: String?) =
        MovementSearchHandlers.handleSearch(game, target)

    fun handleTravel(game: MudGame, rawDirection: String) =
        MovementTravelHandlers.handleTravel(game, rawDirection)

    fun handleScout(game: MudGame, target: String?) =
        MovementScoutHandlers.handleScout(game, target)
}
