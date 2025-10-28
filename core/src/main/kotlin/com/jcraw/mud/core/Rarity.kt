package com.jcraw.mud.core

import kotlinx.serialization.Serializable

/**
 * Item rarity classification for loot generation and value
 * Affects drop rates, vendor prices, and visual presentation
 *
 * Drop rates (for loot tables):
 * - COMMON: 70%
 * - UNCOMMON: 25%
 * - RARE: 4%
 * - EPIC: 0.9%
 * - LEGENDARY: 0.1%
 */
@Serializable
enum class Rarity {
    /** Common items found frequently, low value */
    COMMON,

    /** Uncommon items with moderate value and bonuses */
    UNCOMMON,

    /** Rare items with significant bonuses, found infrequently */
    RARE,

    /** Epic items with powerful bonuses, very rare drops */
    EPIC,

    /** Legendary items with exceptional bonuses, extremely rare */
    LEGENDARY
}
