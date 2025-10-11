package com.jcraw.mud.core

import kotlinx.serialization.Serializable

/**
 * Pre-made character templates for quick character selection in UI.
 */
@Serializable
data class CharacterTemplate(
    val name: String,
    val description: String,
    val stats: Stats,
    val startingItems: List<String> = emptyList()
) {
    companion object {
        val WARRIOR = CharacterTemplate(
            name = "Warrior",
            description = "A battle-hardened fighter skilled in melee combat. High strength and constitution.",
            stats = Stats(
                strength = 16,
                dexterity = 12,
                constitution = 14,
                intelligence = 8,
                wisdom = 10,
                charisma = 10
            ),
            startingItems = listOf("rusty_sword", "leather_armor")
        )

        val ROGUE = CharacterTemplate(
            name = "Rogue",
            description = "A cunning thief with quick reflexes and sharp wit. High dexterity and intelligence.",
            stats = Stats(
                strength = 10,
                dexterity = 16,
                constitution = 12,
                intelligence = 14,
                wisdom = 10,
                charisma = 12
            ),
            startingItems = listOf("dagger", "lockpicks")
        )

        val MAGE = CharacterTemplate(
            name = "Mage",
            description = "A scholarly wizard wielding arcane knowledge. High intelligence and wisdom.",
            stats = Stats(
                strength = 8,
                dexterity = 10,
                constitution = 10,
                intelligence = 16,
                wisdom = 14,
                charisma = 12
            ),
            startingItems = listOf("staff", "spell_book")
        )

        val CLERIC = CharacterTemplate(
            name = "Cleric",
            description = "A devoted healer blessed with divine power. High wisdom and charisma.",
            stats = Stats(
                strength = 12,
                dexterity = 10,
                constitution = 12,
                intelligence = 10,
                wisdom = 16,
                charisma = 14
            ),
            startingItems = listOf("mace", "holy_symbol", "healing_potion")
        )

        val BARD = CharacterTemplate(
            name = "Bard",
            description = "A charismatic performer skilled in social interaction. High charisma and dexterity.",
            stats = Stats(
                strength = 10,
                dexterity = 14,
                constitution = 10,
                intelligence = 12,
                wisdom = 10,
                charisma = 16
            ),
            startingItems = listOf("lute", "rapier")
        )

        val ALL_TEMPLATES = listOf(WARRIOR, ROGUE, MAGE, CLERIC, BARD)
    }
}
