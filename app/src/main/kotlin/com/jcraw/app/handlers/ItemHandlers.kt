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
 * Thin facade for item handlers.
 * Public handle* names preserved for MudGameEngine dispatch; bodies live in cluster extracts.
 */
object ItemHandlers {

    fun handleInventory(game: MudGame) =
        ItemInventoryHandlers.handleInventory(game)

    fun handleTake(game: MudGame, target: String) =
        ItemTakeHandlers.handleTake(game, target)

    fun handleTakeAll(game: MudGame) =
        ItemTakeHandlers.handleTakeAll(game)

    fun handleDrop(game: MudGame, target: String) =
        ItemDropGiveHandlers.handleDrop(game, target)

    fun handleGive(game: MudGame, itemTarget: String, npcTarget: String) =
        ItemDropGiveHandlers.handleGive(game, itemTarget, npcTarget)

    fun handleEquip(game: MudGame, target: String) =
        ItemEquipHandlers.handleEquip(game, target)

    fun handleUse(game: MudGame, target: String) =
        ItemConsumableHandlers.handleUse(game, target)

    fun handleLoot(game: MudGame, corpseTarget: String, itemTarget: String?) =
        ItemLootHandlers.handleLoot(game, corpseTarget, itemTarget)

    fun handleLootAll(game: MudGame, corpseTarget: String) =
        ItemLootHandlers.handleLootAll(game, corpseTarget)
}
