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
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.TreasureRoomComponent
import com.jcraw.mud.reasoning.treasureroom.TreasureRoomHandler
import com.jcraw.mud.reasoning.treasureroom.TreasureRoomStateApply

/**
 * Take-from-pedestal for [TreasureRoomHandlers] facade (MUD-034l pure-move).
 */
internal object TreasureTakeHandlers {

    fun handleTakeTreasure(game: MudGame, itemTarget: String) {
        val spaceId = game.worldState.player.currentRoomId
        val treasureRoomComponent = game.worldState.getTreasureRoom(spaceId)
        if (treasureRoomComponent == null) {
            println("This isn't a treasure room. Use 'take' for regular items.")
            return
        }
        val templates = TreasurePedestalSupport.buildItemTemplatesMap(game, treasureRoomComponent)
        val itemTemplateId = resolveTakeTarget(itemTarget, templates, treasureRoomComponent) ?: return
        applyTake(game, spaceId, treasureRoomComponent, itemTemplateId, templates)
    }

    private fun resolveTakeTarget(
        itemTarget: String,
        templates: Map<String, ItemTemplate>,
        treasureRoomComponent: TreasureRoomComponent
    ): String? {
        val itemTemplateId = TreasurePedestalSupport.findItemTemplateByName(
            itemTarget, templates, treasureRoomComponent
        )
        if (itemTemplateId == null) {
            println("That item is not on any pedestal in this room.")
            println(
                "Available items: ${
                    TreasurePedestalSupport.getAvailableItemNames(treasureRoomComponent, templates).joinToString(", ")
                }"
            )
            return null
        }
        return itemTemplateId
    }

    private fun applyTake(
        game: MudGame,
        spaceId: String,
        treasureRoomComponent: TreasureRoomComponent,
        itemTemplateId: String,
        templates: Map<String, ItemTemplate>
    ) {
        val playerInventory = game.worldState.player.inventoryComponent
        val result = game.treasureRoomHandler.takeItemFromPedestal(
            treasureRoom = treasureRoomComponent,
            playerInventory = playerInventory,
            itemTemplateId = itemTemplateId,
            itemTemplates = templates
        )
        when (result) {
            is TreasureRoomHandler.TreasureRoomResult.Success ->
                emitTakeSuccess(game, spaceId, treasureRoomComponent, itemTemplateId, result)
            is TreasureRoomHandler.TreasureRoomResult.Failure ->
                println(result.reason)
        }
    }

    private fun emitTakeSuccess(
        game: MudGame,
        spaceId: String,
        treasureRoomComponent: TreasureRoomComponent,
        itemTemplateId: String,
        result: TreasureRoomHandler.TreasureRoomResult.Success
    ) {
        game.worldState = TreasureRoomStateApply.applySuccess(
            world = game.worldState,
            spaceId = spaceId,
            player = game.worldState.player,
            success = result
        )
        val pedestalDesc = TreasurePedestalSupport.getPedestalDescription(
            treasureRoomComponent, itemTemplateId
        )
        println("You take the ${result.itemName} from its $pedestalDesc.")
        emitTakeBarriers(treasureRoomComponent, result)
    }

    private fun emitTakeBarriers(
        treasureRoomComponent: TreasureRoomComponent,
        result: TreasureRoomHandler.TreasureRoomResult.Success
    ) {
        if (result.treasureRoomComponent.currentlyTakenItem == null) return
        val barrierType = TreasurePedestalSupport.getBarrierTypeForBiome(treasureRoomComponent.biomeTheme)
        println("\nAs you claim the ${result.itemName}, $barrierType descend over the other pedestals, sealing them away.")
        println("You may return to this room at any time to swap your choice for a different treasure.")
    }
}
