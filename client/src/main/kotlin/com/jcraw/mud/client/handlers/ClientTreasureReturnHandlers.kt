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
import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.core.ItemInstance
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.TreasureRoomComponent
import com.jcraw.mud.reasoning.treasureroom.TreasureRoomHandler
import com.jcraw.mud.reasoning.treasureroom.TreasureRoomStateApply

/**
 * Return-to-pedestal for [ClientTreasureRoomHandlers] facade (MUD-034l pure-move).
 */
internal object ClientTreasureReturnHandlers {

    fun handleReturnTreasure(game: EngineGameClient, itemTarget: String) {
        val spaceId = game.worldState.player.currentRoomId
        val treasureRoomComponent = game.worldState.getTreasureRoom(spaceId)
        if (treasureRoomComponent == null) {
            game.emitEvent(GameEvent.System("This isn't a treasure room.", GameEvent.MessageLevel.WARNING))
            return
        }
        val templates = ClientTreasurePedestalSupport.buildItemTemplatesMap(game, treasureRoomComponent)
        val itemInstance = findReturnItem(game, itemTarget, templates) ?: return
        applyReturn(game, spaceId, treasureRoomComponent, itemInstance, templates)
    }

    private fun findReturnItem(
        game: EngineGameClient,
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
            game.emitEvent(
                GameEvent.System(
                    "You don't have that item in your inventory.",
                    GameEvent.MessageLevel.WARNING
                )
            )
            return null
        }
        return itemInstance
    }

    private fun applyReturn(
        game: EngineGameClient,
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
                game.emitEvent(GameEvent.System(result.reason, GameEvent.MessageLevel.WARNING))
        }
    }

    private fun emitReturnSuccess(
        game: EngineGameClient,
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
        emitReturnNarration(game, treasureRoomComponent, itemInstance, result)
        ClientTreasurePedestalSupport.emitStatusUpdate(game, spaceId)
    }

    private fun emitReturnNarration(
        game: EngineGameClient,
        treasureRoomComponent: TreasureRoomComponent,
        itemInstance: ItemInstance,
        result: TreasureRoomHandler.TreasureRoomResult.Success
    ) {
        val pedestalDesc = ClientTreasurePedestalSupport.getPedestalDescription(
            treasureRoomComponent, itemInstance.templateId
        )
        game.emitEvent(GameEvent.Narrative("You return the ${result.itemName} to its $pedestalDesc."))
        val barrierType = ClientTreasurePedestalSupport.getBarrierTypeForBiome(
            treasureRoomComponent.biomeTheme
        )
        game.emitEvent(
            GameEvent.Narrative(
                "\nThe $barrierType shimmer and fade, revealing the other treasures once more. You may choose again."
            )
        )
    }
}
