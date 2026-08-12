package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.*
import com.jcraw.mud.perception.Intent
import java.util.UUID

/**
 * Handles trading commands (list/buy/sell) in the client.
 */
object ClientTradeHandlers {

    fun handleTrade(game: EngineGameClient, intent: Intent.Trade) {
        when (intent.action.lowercase()) {
            "list" -> handleListStock(game, intent.merchantTarget)
            "buy" -> handleBuy(game, intent)
            "sell" -> handleSell(game)
            else -> game.emitEvent(
                GameEvent.System(
                    "Unknown trade action '${intent.action}'. Try 'list stock', 'buy <item>', or 'sell <item>'.",
                    GameEvent.MessageLevel.WARNING
                )
            )
        }
    }

    private fun handleListStock(game: EngineGameClient, merchantTarget: String?) {
        val context = findMerchant(game, merchantTarget)
        if (context == null) {
            game.emitEvent(
                GameEvent.System("No merchant found here.", GameEvent.MessageLevel.WARNING)
            )
            return
        }

        val merchant = context.npc
        val trading = merchant.getComponent<TradingComponent>(ComponentType.TRADING)
            ?: run {
                game.emitEvent(
                    GameEvent.System("${merchant.name} is not trading right now.", GameEvent.MessageLevel.INFO)
                )
                return
            }

        val disposition = merchant.getComponent<SocialComponent>(ComponentType.SOCIAL)?.disposition ?: 0
        val playerGold = game.worldState.player.gold

        if (trading.stock.isEmpty()) {
            game.emitEvent(
                GameEvent.Narrative(
                    buildString {
                        appendLine("\n${merchant.name} says: \"I'm afraid I'm all sold out.\"")
                        appendLine("You have $playerGold gold.")
                    }
                )
            )
            return
        }

        val lines = buildString {
            appendLine("\n${merchant.name}'s stock:")
            trading.stock.forEach { instance ->
                val template = game.getItemTemplate(instance.templateId)
                val price = trading.calculateBuyPrice(template, instance, disposition)
                val quantityLabel = if (instance.quantity > 1) " (x${instance.quantity})" else ""
                appendLine("  - ${template.name}$quantityLabel — $price gold")
            }
            appendLine()
            appendLine("You have $playerGold gold.")
        }

        game.emitEvent(GameEvent.Narrative(lines))
    }

    private fun handleBuy(game: EngineGameClient, intent: Intent.Trade) {
        val itemQuery = intent.target.trim()
        if (itemQuery.isEmpty()) {
            game.emitEvent(
                GameEvent.System("Buy what? Specify an item name.", GameEvent.MessageLevel.WARNING)
            )
            return
        }

        val context = findMerchant(game, intent.merchantTarget)
        if (context == null) {
            game.emitEvent(
                GameEvent.System("No merchant found here.", GameEvent.MessageLevel.WARNING)
            )
            return
        }

        val merchant = context.npc
        val trading = merchant.getComponent<TradingComponent>(ComponentType.TRADING)
            ?: run {
                game.emitEvent(
                    GameEvent.System("${merchant.name} is not trading right now.", GameEvent.MessageLevel.INFO)
                )
                return
            }

        val disposition = merchant.getComponent<SocialComponent>(ComponentType.SOCIAL)?.disposition ?: 0

        val matchingEntry = trading.stock.firstOrNull { instance ->
            val template = game.getItemTemplate(instance.templateId)
            template.name.lowercase().contains(itemQuery.lowercase()) ||
                instance.templateId.lowercase().contains(itemQuery.lowercase())
        }

        if (matchingEntry == null) {
            game.emitEvent(
                GameEvent.System("${merchant.name} doesn't carry anything like '$itemQuery'.", GameEvent.MessageLevel.INFO)
            )
            return
        }

        val template = game.getItemTemplate(matchingEntry.templateId)
        val quantityRequested = intent.quantity.coerceAtLeast(1)

        if (quantityRequested > matchingEntry.quantity) {
            game.emitEvent(
                GameEvent.System(
                    "${merchant.name} only has ${matchingEntry.quantity} in stock.",
                    GameEvent.MessageLevel.WARNING
                )
            )
            return
        }

        val pricePerItem = trading.calculateBuyPrice(template, matchingEntry, disposition)
        val totalCost = pricePerItem * quantityRequested
        val player = game.worldState.player

        if (player.gold < totalCost) {
            game.emitEvent(
                GameEvent.System(
                    "You need $totalCost gold, but only have ${player.gold}.",
                    GameEvent.MessageLevel.WARNING
                )
            )
            return
        }

        // Build template map for weight checks (existing inventory + purchased template)
        val templates = game.itemTemplateCache.toMutableMap()
        templates[template.id] = template

        // Trial: deduct gold then add V2 instances; fail closed on overweight (no world mutation)
        var trialPlayer = player.copy(inventoryComponent = player.inventoryComponent.addGold(-totalCost))
        repeat(quantityRequested) {
            val instanceForPlayer = ItemInstance(
                id = UUID.randomUUID().toString(),
                templateId = template.id,
                quality = matchingEntry.quality,
                charges = matchingEntry.charges,
                quantity = 1
            )
            val added = trialPlayer.addItemInstance(instanceForPlayer, templates)
            if (added == null) {
                game.emitEvent(
                    GameEvent.System(
                        "You can't carry that — you're already carrying too much weight.",
                        GameEvent.MessageLevel.WARNING
                    )
                )
                return
            }
            trialPlayer = added
        }

        val tradingAfterRemoval = trading.removeQuantityFromStock(matchingEntry.id, quantityRequested)
            ?: return game.emitEvent(
                GameEvent.System("Something went wrong removing the item from stock.", GameEvent.MessageLevel.ERROR)
            )

        val updatedTrading = tradingAfterRemoval.addGold(totalCost)
        val updatedNpc = merchant.withComponent(updatedTrading)

        game.worldState = game.worldState.updatePlayer(trialPlayer)
        updateMerchant(game, context, updatedNpc)
        game.lastConversationNpcId = merchant.id

        val purchasedSummary = if (quantityRequested > 1) {
            "${quantityRequested}x ${template.name}"
        } else {
            template.name
        }

        val message = buildString {
            appendLine("\nYou buy $purchasedSummary from ${merchant.name} for $totalCost gold.")
            appendLine("You now have ${trialPlayer.gold} gold.")
        }
        game.emitEvent(GameEvent.Narrative(message))
    }

    private fun handleSell(game: EngineGameClient) {
        game.emitEvent(
            GameEvent.System(
                "Selling items to merchants isn't implemented yet.",
                GameEvent.MessageLevel.INFO
            )
        )
    }

    private fun updateMerchant(game: EngineGameClient, context: MerchantContext, updatedNpc: Entity.NPC) {
        game.spaceEntityRepository.save(updatedNpc).onFailure {
            game.emitEvent(
                GameEvent.System("Failed to update merchant: ${it.message}", GameEvent.MessageLevel.ERROR)
            )
        }
    }

    private fun findMerchant(game: EngineGameClient, merchantTarget: String?): MerchantContext? {
        val space = game.currentSpace()
        if (space != null) {
            val merchants = space.entities.mapNotNull { entityId ->
                val entity = game.loadEntity(entityId) as? Entity.NPC
                if (entity?.getComponent<TradingComponent>(ComponentType.TRADING) != null) {
                    entityId to entity
                } else {
                    null
                }
            }

            if (merchants.isNotEmpty()) {
                val match = resolveMerchantCandidate(merchants.map { it.second }, merchantTarget, game.lastConversationNpcId)
                if (match != null) {
                    val entityId = merchants.first { it.second.id == match.id }.first
                    return MerchantContext(entityId = entityId, npc = match)
                }
            }
        }

        return null
    }

    private fun resolveMerchantCandidate(
        candidates: List<Entity.NPC>,
        merchantTarget: String?,
        recentId: String?
    ): Entity.NPC? {
        if (!merchantTarget.isNullOrBlank()) {
            val lower = merchantTarget.lowercase()
            candidates.firstOrNull {
                it.name.lowercase().contains(lower) || it.id.lowercase().contains(lower)
            }?.let { return it }
        }

        if (recentId != null) {
            candidates.firstOrNull { it.id == recentId }?.let { return it }
        }

        return candidates.firstOrNull()
    }

    private data class MerchantContext(
        val entityId: String,
        val npc: Entity.NPC
    )
}
