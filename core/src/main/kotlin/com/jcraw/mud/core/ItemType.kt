package com.jcraw.mud.core

import kotlinx.serialization.Serializable

/**
 * Type classification for items
 * Determines how items can be used and where they can be equipped
 */
@Serializable
enum class ItemType {
    /** Melee or ranged weapons for combat */
    WEAPON,

    /** Protective gear that provides defense bonuses */
    ARMOR,

    /** Single-use items like potions, food, scrolls */
    CONSUMABLE,

    /** Raw materials for crafting (ore, wood, herbs) */
    RESOURCE,

    /** Special items required for quest objectives */
    QUEST,

    /** Items used for gathering, crafting, or other specialized tasks */
    TOOL,

    /** Items that can hold other items and increase carrying capacity */
    CONTAINER,

    /** Books containing spells that can be learned */
    SPELL_BOOK,

    /** Books containing skill training knowledge */
    SKILL_BOOK,

    /** Rings, amulets, and other accessories that provide bonuses */
    ACCESSORY,

    /** @deprecated Legacy type for generic items - use RESOURCE or TOOL instead */
    @Deprecated("Use more specific types like RESOURCE or TOOL")
    MISC
}
