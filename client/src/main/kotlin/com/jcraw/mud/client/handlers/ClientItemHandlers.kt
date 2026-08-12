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

/**
 * Thin facade for client item handlers.
 * Public handle* names preserved for EngineGameClient dispatch; bodies live in cluster extracts.
 */
object ClientItemHandlers {

    fun handleInventory(game: EngineGameClient) =
        ClientItemInventoryHandlers.handleInventory(game)

    fun handleTake(game: EngineGameClient, target: String) =
        ClientItemTakeHandlers.handleTake(game, target)

    fun handleTakeAll(game: EngineGameClient) =
        ClientItemTakeHandlers.handleTakeAll(game)

    fun handleDrop(game: EngineGameClient, target: String) =
        ClientItemDropGiveHandlers.handleDrop(game, target)

    fun handleGive(game: EngineGameClient, itemTarget: String, npcTarget: String) =
        ClientItemDropGiveHandlers.handleGive(game, itemTarget, npcTarget)

    fun handleEquip(game: EngineGameClient, target: String) =
        ClientItemEquipHandlers.handleEquip(game, target)

    fun handleUse(game: EngineGameClient, target: String) =
        ClientItemConsumableHandlers.handleUse(game, target)
}
