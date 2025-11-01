package com.jcraw.mud.reasoning.town

import com.jcraw.mud.core.DispositionTier
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.TradingComponent

/**
 * Calculates buy/sell prices for merchants based on player disposition.
 *
 * Pricing is affected by disposition tier:
 * - ALLIED: Buy -20%, Sell +10%
 * - FRIENDLY: Buy -20%, Sell +10%
 * - NEUTRAL: Standard prices (buy 100%, sell 50%)
 * - UNFRIENDLY: Buy +25%, Sell -20%
 * - HOSTILE: Refuses to trade
 *
 * This integrates with the social system to reward players who build relationships.
 */
object MerchantPricingCalculator {

    /**
     * Calculate the price a player must pay to buy an item from a merchant.
     *
     * Base price is multiplied by disposition modifier:
     * - ALLIED: 0.8 (20% discount)
     * - FRIENDLY: 0.8 (20% discount)
     * - NEUTRAL: 1.0 (standard price)
     * - UNFRIENDLY: 1.25 (25% markup)
     * - HOSTILE: Trade refused
     *
     * @param basePrice Item's base price
     * @param dispositionTier Merchant's disposition toward player
     * @return Adjusted buy price
     */
    fun calculateBuyPrice(basePrice: Int, dispositionTier: DispositionTier): Int {
        val modifier = when (dispositionTier) {
            DispositionTier.ALLIED -> 0.8
            DispositionTier.FRIENDLY -> 0.8
            DispositionTier.NEUTRAL -> 1.0
            DispositionTier.UNFRIENDLY -> 1.25
            DispositionTier.HOSTILE -> return -1 // Indicates trade refusal
        }
        return (basePrice * modifier).toInt()
    }

    /**
     * Calculate the price a merchant will pay when player sells an item.
     *
     * Base price is multiplied by disposition modifier:
     * - ALLIED: 0.6 (60% of base)
     * - FRIENDLY: 0.6 (60% of base)
     * - NEUTRAL: 0.5 (50% of base, standard)
     * - UNFRIENDLY: 0.4 (40% of base)
     * - HOSTILE: Trade refused
     *
     * @param basePrice Item's base price
     * @param dispositionTier Merchant's disposition toward player
     * @return Adjusted sell price
     */
    fun calculateSellPrice(basePrice: Int, dispositionTier: DispositionTier): Int {
        val modifier = when (dispositionTier) {
            DispositionTier.ALLIED -> 0.6
            DispositionTier.FRIENDLY -> 0.6
            DispositionTier.NEUTRAL -> 0.5
            DispositionTier.UNFRIENDLY -> 0.4
            DispositionTier.HOSTILE -> return -1 // Indicates trade refusal
        }
        return (basePrice * modifier).toInt()
    }

    /**
     * Check if a merchant can trade with a player.
     *
     * Trade is denied if:
     * - Disposition is HOSTILE
     * - Merchant has no gold remaining
     *
     * @param merchant NPC merchant entity
     * @param player Player attempting to trade
     * @return True if trade is allowed
     */
    fun canTrade(merchant: Entity.NPC, player: PlayerState): Boolean {
        // Check disposition
        val social = merchant.getComponent(com.jcraw.mud.core.ComponentType.SOCIAL) as? com.jcraw.mud.core.SocialComponent
        if (social != null && social.getDispositionTier() == DispositionTier.HOSTILE) {
            return false
        }

        // Check merchant gold (for buying from player)
        val trading = merchant.getComponent(com.jcraw.mud.core.ComponentType.TRADING) as? TradingComponent
        if (trading != null && trading.merchantGold <= 0) {
            return false
        }

        return true
    }

    /**
     * Get a description of why trade was refused.
     *
     * @param merchant NPC merchant
     * @param player Player attempting trade
     * @return Refusal reason
     */
    fun getTradeRefusalReason(merchant: Entity.NPC, player: PlayerState): String {
        val social = merchant.getComponent(com.jcraw.mud.core.ComponentType.SOCIAL) as? com.jcraw.mud.core.SocialComponent
        if (social != null && social.getDispositionTier() == DispositionTier.HOSTILE) {
            return "${merchant.name} refuses to trade with you. Their disposition is too low."
        }

        val trading = merchant.getComponent(com.jcraw.mud.core.ComponentType.TRADING) as? TradingComponent
        if (trading != null && trading.merchantGold <= 0) {
            return "${merchant.name} has no gold left to buy items from you."
        }

        return "Trade is not possible at this time."
    }

    /**
     * Format a price with disposition modifier indicator.
     *
     * Examples:
     * - "100 gold (20% discount)"
     * - "150 gold (25% markup)"
     * - "75 gold (standard price)"
     *
     * @param price Adjusted price
     * @param basePrice Original base price
     * @param dispositionTier Merchant's disposition
     * @return Formatted price string
     */
    fun formatPrice(price: Int, basePrice: Int, dispositionTier: DispositionTier): String {
        val modifier = when (dispositionTier) {
            DispositionTier.ALLIED -> " (20% discount)"
            DispositionTier.FRIENDLY -> " (20% discount)"
            DispositionTier.NEUTRAL -> ""
            DispositionTier.UNFRIENDLY -> " (25% markup)"
            DispositionTier.HOSTILE -> " (TRADE REFUSED)"
        }
        return "$price gold$modifier"
    }

    /**
     * Get disposition tier from merchant NPC.
     *
     * @param merchant NPC merchant
     * @return Disposition tier, or NEUTRAL if no social component
     */
    fun getDispositionTier(merchant: Entity.NPC): DispositionTier {
        val social = merchant.getComponent(com.jcraw.mud.core.ComponentType.SOCIAL) as? com.jcraw.mud.core.SocialComponent
        return social?.getDispositionTier() ?: DispositionTier.NEUTRAL
    }
}
