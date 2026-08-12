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
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.TreasureRoomComponent
import com.jcraw.mud.reasoning.treasureroom.TreasureRoomHandler
import com.jcraw.mud.reasoning.treasureroom.TreasureRoomStateApply

/**
 * Take-from-pedestal for [ClientTreasureRoomHandlers] facade (MUD-034l pure-move).
 */
internal object ClientTreasureTakeHandlers {

    fun handleTakeTreasure(game: EngineGameClient, itemTarget: String) {
        val spaceId = game.worldState.player.currentRoomId
        val treasureRoomComponent = game.worldState.getTreasureRoom(spaceId)
        if (treasureRoomComponent == null) {
            game.emitEvent(
                GameEvent.System(
                    "This isn't a treasure room. Use 'take' for regular items.",
                    GameEvent.MessageLevel.WARNING
                )
            )
            return
        }
        val templates = ClientTreasurePedestalSupport.buildItemTemplatesMap(game, treasureRoomComponent)
        val itemTemplateId = resolveTakeTarget(game, itemTarget, templates, treasureRoomComponent) ?: return
        applyTake(game, spaceId, treasureRoomComponent, itemTemplateId, templates)
    }

    private fun resolveTakeTarget(
        game: EngineGameClient,
        itemTarget: String,
        templates: Map<String, ItemTemplate>,
        treasureRoomComponent: TreasureRoomComponent
    ): String? {
        val itemTemplateId = ClientTreasurePedestalSupport.findItemTemplateByName(
            itemTarget, templates, treasureRoomComponent
        )
        if (itemTemplateId == null) {
            val available = ClientTreasurePedestalSupport
                .getAvailableItemNames(treasureRoomComponent, templates)
                .joinToString(", ")
            game.emitEvent(
                GameEvent.System(
                    "That item is not on any pedestal in this room.\nAvailable items: $available",
                    GameEvent.MessageLevel.WARNING
                )
            )
            return null
        }
        return itemTemplateId
    }

    private fun applyTake(
        game: EngineGameClient,
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
                game.emitEvent(GameEvent.System(result.reason, GameEvent.MessageLevel.WARNING))
        }
    }

    private fun emitTakeSuccess(
        game: EngineGameClient,
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
        val pedestalDesc = ClientTreasurePedestalSupport.getPedestalDescription(
            treasureRoomComponent, itemTemplateId
        )
        game.emitEvent(GameEvent.Narrative("You take the ${result.itemName} from its $pedestalDesc."))
        emitTakeBarriers(game, treasureRoomComponent, result)
        ClientTreasurePedestalSupport.emitStatusUpdate(game, spaceId)
    }

    private fun emitTakeBarriers(
        game: EngineGameClient,
        treasureRoomComponent: TreasureRoomComponent,
        result: TreasureRoomHandler.TreasureRoomResult.Success
    ) {
        if (result.treasureRoomComponent.currentlyTakenItem == null) return
        val barrierType = ClientTreasurePedestalSupport.getBarrierTypeForBiome(
            treasureRoomComponent.biomeTheme
        )
        game.emitEvent(
            GameEvent.Narrative(
                "\nAs you claim the ${result.itemName}, $barrierType descend over the other pedestals, sealing them away."
            )
        )
        game.emitEvent(
            GameEvent.Narrative(
                "You may return to this room at any time to swap your choice for a different treasure."
            )
        )
    }
}
