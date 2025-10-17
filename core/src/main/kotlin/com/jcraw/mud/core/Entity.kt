package com.jcraw.mud.core

import kotlinx.serialization.Serializable

/**
 * Type of item for usage/equipping behavior
 */
@Serializable
enum class ItemType {
    WEAPON,      // Can be equipped for damage bonus
    ARMOR,       // Can be equipped for defense (future)
    CONSUMABLE,  // Can be used once (potions, food)
    MISC         // Generic items
}

/**
 * Character stats (D&D-style)
 * Default values represent average human stats (10)
 */
@Serializable
data class Stats(
    val strength: Int = 10,     // Physical power, melee damage
    val dexterity: Int = 10,    // Agility, ranged attacks, dodge
    val constitution: Int = 10, // Endurance, health
    val intelligence: Int = 10, // Reasoning, magic
    val wisdom: Int = 10,       // Perception, insight
    val charisma: Int = 10      // Social interactions, persuasion
) {
    /**
     * Calculate modifier from stat value (D&D formula: (stat - 10) / 2)
     */
    fun getModifier(stat: Int): Int = (stat - 10) / 2

    fun strModifier(): Int = getModifier(strength)
    fun dexModifier(): Int = getModifier(dexterity)
    fun conModifier(): Int = getModifier(constitution)
    fun intModifier(): Int = getModifier(intelligence)
    fun wisModifier(): Int = getModifier(wisdom)
    fun chaModifier(): Int = getModifier(charisma)
}

@Serializable
sealed class Entity {
    abstract val id: String
    abstract val name: String
    abstract val description: String

    @Serializable
    data class Item(
        override val id: String,
        override val name: String,
        override val description: String,
        val isPickupable: Boolean = true,
        val isUsable: Boolean = false,
        val itemType: ItemType = ItemType.MISC,
        val properties: Map<String, String> = emptyMap(),
        // Weapon properties
        val damageBonus: Int = 0,
        // Armor properties
        val defenseBonus: Int = 0,
        // Consumable properties
        val healAmount: Int = 0,
        val isConsumable: Boolean = false
    ) : Entity()

    @Serializable
    data class NPC(
        override val id: String,
        override val name: String,
        override val description: String,
        val isHostile: Boolean = false,
        val health: Int = 100,
        val maxHealth: Int = 100,
        val stats: Stats = Stats(),
        val properties: Map<String, String> = emptyMap(),
        // Social interaction (legacy - will be migrated to SocialComponent)
        val persuasionChallenge: SkillChallenge? = null,
        val intimidationChallenge: SkillChallenge? = null,
        val hasBeenPersuaded: Boolean = false,
        val hasBeenIntimidated: Boolean = false,
        // Component system
        override val components: Map<ComponentType, Component> = emptyMap()
    ) : Entity(), ComponentHost {

        override fun withComponent(component: Component): NPC {
            return copy(components = components + (component.componentType to component))
        }

        override fun withoutComponent(type: ComponentType): NPC {
            return copy(components = components - type)
        }

        /**
         * Helper to get social component with fallback
         */
        fun getSocialComponent(): SocialComponent? {
            return getComponent(ComponentType.SOCIAL)
        }

        /**
         * Get current disposition (0 if no social component)
         */
        fun getDisposition(): Int {
            return getSocialComponent()?.disposition ?: 0
        }

        /**
         * Apply social event to NPC
         */
        fun applySocialEvent(event: SocialEvent): NPC {
            val social = getSocialComponent() ?: SocialComponent(
                personality = "ordinary",
                traits = emptyList()
            )
            return withComponent(social.applyDispositionChange(event.dispositionDelta))
        }

        /**
         * Check if NPC should be hostile based on disposition
         */
        fun isHostileByDisposition(): Boolean {
            return getSocialComponent()?.getDispositionTier() == DispositionTier.HOSTILE || isHostile
        }
    }

    @Serializable
    data class Feature(
        override val id: String,
        override val name: String,
        override val description: String,
        val isInteractable: Boolean = false,
        val properties: Map<String, String> = emptyMap(),
        val skillChallenge: SkillChallenge? = null,
        val isCompleted: Boolean = false  // Track if challenge has been overcome
    ) : Entity()

    @Serializable
    data class Player(
        override val id: String,
        override val name: String,
        override val description: String,
        val playerId: PlayerId,
        val health: Int,
        val maxHealth: Int,
        val equippedWeapon: String? = null,
        val equippedArmor: String? = null
    ) : Entity()
}