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
import com.jcraw.mud.core.ComponentType
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.InventoryComponent
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.TradingComponent
import com.jcraw.mud.reasoning.trade.TradeHandler

/**
 * Merchant lookup / template map / session bag for [TradeHandlers] (MUD-034l).
 */
internal data class TradeSession(
    val game: MudGame,
    val tradeHandler: TradeHandler,
    val merchant: Entity.NPC,
    val tradingComponent: TradingComponent,
    val playerInventory: InventoryComponent,
    val target: String,
    val quantity: Int,
    val disposition: Int,
    val templates: Map<String, ItemTemplate>,
    val spaceId: String
)

internal object TradeMerchantSupport {

    fun findMerchant(game: MudGame, spaceId: String, merchantTarget: String?): Entity.NPC? {
        val npcs = game.worldState.getEntitiesInSpace(spaceId)
            .filterIsInstance<Entity.NPC>()
            .filter { it.hasComponent(ComponentType.TRADING) }

        return if (merchantTarget != null) {
            npcs.find { npc ->
                npc.name.lowercase().contains(merchantTarget.lowercase()) ||
                    npc.id.lowercase().contains(merchantTarget.lowercase())
            }
        } else {
            npcs.firstOrNull()
        }
    }

    fun buildTemplateMap(
        game: MudGame,
        playerInventory: InventoryComponent,
        tradingComponent: TradingComponent
    ): Map<String, ItemTemplate> {
        val templates = mutableMapOf<String, ItemTemplate>()
        playerInventory.items.forEach { instance ->
            val result = game.itemRepository.findTemplateById(instance.templateId)
            result.getOrNull()?.let { templates[it.id] = it }
        }
        tradingComponent.stock.forEach { instance ->
            val result = game.itemRepository.findTemplateById(instance.templateId)
            result.getOrNull()?.let { templates[it.id] = it }
        }
        return templates
    }

    fun openSession(
        game: MudGame,
        target: String,
        quantity: Int,
        merchantTarget: String?
    ): TradeSession? {
        val spaceId = game.worldState.player.currentRoomId
        val merchant = requireMerchant(game, spaceId, merchantTarget) ?: return null
        val tradingComponent = requireTrading(merchant) ?: return null
        val playerInventory = requireInventory(game) ?: return null
        return TradeSession(
            game = game,
            tradeHandler = TradeHandler(game.itemRepository),
            merchant = merchant,
            tradingComponent = tradingComponent,
            playerInventory = playerInventory,
            target = target,
            quantity = quantity,
            disposition = merchant.getDisposition(),
            templates = buildTemplateMap(game, playerInventory, tradingComponent),
            spaceId = spaceId
        )
    }

    private fun requireMerchant(game: MudGame, spaceId: String, merchantTarget: String?): Entity.NPC? {
        val merchant = findMerchant(game, spaceId, merchantTarget)
        if (merchant == null) {
            println("There's no merchant here to trade with.")
        }
        return merchant
    }

    private fun requireTrading(merchant: Entity.NPC): TradingComponent? {
        val tradingComponent = merchant.getComponent<TradingComponent>(ComponentType.TRADING)
        if (tradingComponent == null) {
            println("${merchant.name} doesn't appear to be a merchant.")
        }
        return tradingComponent
    }

    private fun requireInventory(game: MudGame): InventoryComponent? {
        val playerInventory = game.worldState.player.inventoryComponent
        if (playerInventory == null) {
            println("You don't have an inventory to trade with.")
        }
        return playerInventory
    }

    fun applyTradeSuccess(
        session: TradeSession,
        result: TradeHandler.TradeResult.Success
    ) {
        val updatedPlayer = session.game.worldState.player.copy(inventoryComponent = result.playerInventory)
        val updatedMerchant = session.merchant.withComponent(result.merchantTrading) as Entity.NPC
        val newState = session.game.worldState
            .updatePlayer(updatedPlayer)
            .replaceEntityInSpace(session.spaceId, session.merchant.id, updatedMerchant)
        session.game.worldState = newState
    }
}
