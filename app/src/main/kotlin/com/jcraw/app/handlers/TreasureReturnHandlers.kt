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
import com.jcraw.mud.core.ItemInstance
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.TreasureRoomComponent
import com.jcraw.mud.reasoning.treasureroom.TreasureRoomHandler
import com.jcraw.mud.reasoning.treasureroom.TreasureRoomStateApply

/**
 * Return-to-pedestal for [TreasureRoomHandlers] facade (MUD-034l pure-move).
 */
internal object TreasureReturnHandlers {

    fun handleReturnTreasure(game: MudGame, itemTarget: String) {
        val spaceId = game.worldState.player.currentRoomId
        val treasureRoomComponent = game.worldState.getTreasureRoom(spaceId)
        if (treasureRoomComponent == null) {
            println("This isn't a treasure room.")
            return
        }
        val templates = TreasurePedestalSupport.buildItemTemplatesMap(game, treasureRoomComponent)
        val itemInstance = findReturnItem(game, itemTarget, templates) ?: return
        applyReturn(game, spaceId, treasureRoomComponent, itemInstance, templates)
    }

    private fun findReturnItem(
        game: MudGame,
        itemTarget: String,
        templates: Map<String, ItemTemplate>
    ): ItemInstance? {
        val playerInventory = game.worldState.player.inventoryComponent
        val itemInstance = playerInventory.items.find { instance ->
            val template = templates[instance.templateId]
            template?.name?.lowercase()?.contains(itemTarget.lowercase()) == true ||
                instance.templateId.lowercase().contains(itemTarget.lowercase())
        }
        if (itemInstance == null) {
            println("You don't have that item in your inventory.")
            return null
        }
        return itemInstance
    }

    private fun applyReturn(
        game: MudGame,
        spaceId: String,
        treasureRoomComponent: TreasureRoomComponent,
        itemInstance: ItemInstance,
        templates: Map<String, ItemTemplate>
    ) {
        val playerInventory = game.worldState.player.inventoryComponent
        val result = game.treasureRoomHandler.returnItemToPedestal(
            treasureRoom = treasureRoomComponent,
            playerInventory = playerInventory,
            itemInstanceId = itemInstance.id,
            itemTemplates = templates
        )
        when (result) {
            is TreasureRoomHandler.TreasureRoomResult.Success ->
                emitReturnSuccess(game, spaceId, treasureRoomComponent, itemInstance, result)
            is TreasureRoomHandler.TreasureRoomResult.Failure ->
                println(result.reason)
        }
    }

    private fun emitReturnSuccess(
        game: MudGame,
        spaceId: String,
        treasureRoomComponent: TreasureRoomComponent,
        itemInstance: ItemInstance,
        result: TreasureRoomHandler.TreasureRoomResult.Success
    ) {
        game.worldState = TreasureRoomStateApply.applySuccess(
            world = game.worldState,
            spaceId = spaceId,
            player = game.worldState.player,
            success = result
        )
        println(
            "You return the ${result.itemName} to its ${
                TreasurePedestalSupport.getPedestalDescription(treasureRoomComponent, itemInstance.templateId)
            }."
        )
        val barrierType = TreasurePedestalSupport.getBarrierTypeForBiome(treasureRoomComponent.biomeTheme)
        println("\nThe $barrierType shimmer and fade, revealing the other treasures once more. You may choose again.")
    }
}
