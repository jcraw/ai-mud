package com.jcraw.mud.reasoning.world

import com.jcraw.mud.core.*
import com.jcraw.mud.core.repository.SpacePropertiesRepository
import java.util.UUID

/**
 * Generates boss encounters and boss lairs
 * Handles special boss NPCs like the Abyssal Lord
 */
class BossGenerator(
    private val worldGenerator: WorldGenerator,
    private val spaceRepo: SpacePropertiesRepository
) {
    /**
     * Generate the Abyssal Lord boss lair space
     * Creates a dramatic boss arena at the bottom of the Abyssal Core
     *
     * @param parentSubzone Parent subzone chunk for the boss lair
     * @return Result with boss lair space ID
     */
    suspend fun generateAbyssalLordSpace(
        parentSubzone: WorldChunkComponent
    ): Result<String> {
        // Generate boss lair SPACE
        val (bossSpace, bossSpaceId) = worldGenerator.generateSpace(parentSubzone, parentSubzone.parentId ?: "")
            .getOrElse { return Result.failure(it) }

        // Create Abyssal Lord boss NPC
        val abyssalLord = createAbyssalLord()

        // Boss lair description
        val bossDescription = """
            You enter a vast circular chamber at the heart of the Abyssal Core.
            Rivers of molten lava flow in channels carved into the obsidian floor, casting an infernal glow.
            At the center, upon a throne of blackened bones, sits the Abyssal Lord - an ancient demon of
            immense power. His eyes burn with crimson fire as he rises to face you, dark flames wreathing
            his massive form. The air crackles with malevolent energy.

            "Another fool seeks to claim what is mine," he rumbles. "The Abyss Heart shall never leave this place."
        """.trimIndent()

        // Populate boss lair
        val populatedSpace = bossSpace.copy(
            description = bossDescription,
            entities = listOf(abyssalLord.id),
            isSafeZone = false,
            traps = emptyList(), // Boss is challenge enough
            terrainType = TerrainType.LAVA // Dangerous terrain
        )

        spaceRepo.save(populatedSpace, bossSpaceId).getOrElse { return Result.failure(it) }

        return Result.success(bossSpaceId)
    }

    /**
     * Create the Abyssal Lord boss NPC
     * Extremely powerful demon with boss designation and legendary loot
     *
     * @return Abyssal Lord NPC entity
     */
    fun createAbyssalLord(): Entity.NPC {
        val bossDesignation = BossDesignation.create(
            title = "Abyssal Lord",
            victoryFlag = "abyssal_lord_defeated"
        )

        val socialComponent = SocialComponent(
            disposition = -100, // HOSTILE
            personality = "ancient demon lord of immense power and cunning",
            traits = listOf("ruthless", "strategic", "ancient", "powerful", "arrogant")
        )

        // Boss has massive stats
        val bossStats = Stats(
            strength = 80,
            dexterity = 60,
            constitution = 100,
            intelligence = 70,
            wisdom = 60,
            charisma = 50
        )

        return Entity.NPC(
            id = "boss_abyssal_lord",
            name = "Abyssal Lord",
            description = "A towering demon wreathed in dark flames. Ancient, powerful, and utterly malevolent.",
            isHostile = true,
            health = 1000,
            maxHealth = 1000,
            stats = bossStats,
            properties = mapOf(
                "boss_designation" to bossDesignation.toString(),
                "ai_prompt" to getAbyssalLordAIPrompt(),
                "loot_table" to "abyssal_lord_loot"
            ),
            components = mapOf(
                ComponentType.SOCIAL to socialComponent
            )
        )
    }

    /**
     * Get AI prompt for Abyssal Lord boss behavior
     * LLM will use this to generate tactical combat decisions
     *
     * @return Prompt string for LLM
     */
    private fun getAbyssalLordAIPrompt(): String {
        return """
            You are the Abyssal Lord, an ancient demon of immense power who has ruled the Abyssal Core
            for millennia. You wield fire magic, dark sorcery, and devastating melee attacks.

            Combat Strategy:
            - At 100-75% HP: Use fire magic at range, testing the enemy's defenses
            - At 75-50% HP: Mix fire magic with dark sorcery, become more aggressive
            - At 50-25% HP: Summon minor demons for support, use melee when cornered
            - At 25-0% HP: Desperate all-out assault with strongest spells

            You are ruthless but strategic. Mock weak opponents, respect worthy foes.
            You will NOT surrender or flee - this is your domain and you defend it to the death.

            Describe your actions dramatically and with confidence befitting a demon lord.
        """.trimIndent()
    }

    /**
     * Create the Abyss Heart legendary item
     * Quest objective and powerful treasure
     *
     * @return Abyss Heart item instance
     */
    fun createAbyssHeart(): ItemInstance {
        return ItemInstance(
            id = UUID.randomUUID().toString(),
            templateId = "abyss_heart_legendary",
            quality = 10, // Maximum quality
            quantity = 1
        )
    }

    /**
     * Create Abyss Heart item template
     * Should be saved to item template repository
     *
     * @return ItemTemplate for Abyss Heart
     */
    fun createAbyssHeartTemplate(): ItemTemplate {
        return ItemTemplate(
            id = "abyss_heart_legendary",
            name = "Abyss Heart",
            type = ItemType.QUEST,
            tags = listOf("legendary", "quest", "unique", "powerful"),
            properties = mapOf(
                "value" to "10000",
                "weight" to "1.0",
                "strength_bonus" to "50",
                "dexterity_bonus" to "50",
                "constitution_bonus" to "50",
                "intelligence_bonus" to "50",
                "wisdom_bonus" to "50",
                "charisma_bonus" to "50",
                "xp_multiplier" to "2.0",
                "quest_flag" to "abyss_heart_retrieved"
            ),
            rarity = Rarity.LEGENDARY,
            description = """
                The crystallized heart of the Abyss itself, pulsing with ancient malevolent power.
                This fist-sized obsidian crystal thrums with dark energy, granting its bearer
                immense power. Legends say returning it to the surface will end the Abyssal curse.

                +50 to all stats
                +100% XP gain

                "Power beyond mortal ken, sealed in darkest stone."
            """.trimIndent(),
            equipSlot = EquipSlot.ACCESSORY_1
        )
    }

    /**
     * Generate standard boss loot (non-Abyssal Lord)
     * For future boss encounters
     *
     * @param bossName Name of the boss
     * @param difficulty Difficulty level
     * @return List of legendary/epic loot
     */
    fun generateBossLoot(bossName: String, difficulty: Int): List<ItemInstance> {
        // For now, return placeholder legendary items
        // Future: integrate with loot table generator
        return listOf(
            ItemInstance(
                id = UUID.randomUUID().toString(),
                templateId = "legendary_weapon_placeholder",
                quality = 9,
                quantity = 1
            ),
            ItemInstance(
                id = UUID.randomUUID().toString(),
                templateId = "legendary_armor_placeholder",
                quality = 9,
                quantity = 1
            )
        )
    }

    /**
     * Check if NPC is a boss based on properties
     *
     * @param npc NPC entity to check
     * @return True if NPC is designated as boss
     */
    fun isBoss(npc: Entity.NPC): Boolean {
        val bossProperty = npc.properties["boss_designation"] ?: return false
        return bossProperty.contains("isBoss=true")
    }

    /**
     * Get boss title from NPC properties
     *
     * @param npc Boss NPC entity
     * @return Boss title or empty string
     */
    fun getBossTitle(npc: Entity.NPC): String {
        if (!isBoss(npc)) return ""

        // Extract title from boss_designation property
        val bossProperty = npc.properties["boss_designation"] ?: return ""
        val titleMatch = Regex("bossTitle=([^,)]+)").find(bossProperty)
        return titleMatch?.groupValues?.get(1) ?: ""
    }

    /**
     * Get victory flag from boss NPC
     *
     * @param npc Boss NPC entity
     * @return Victory flag string or empty
     */
    fun getVictoryFlag(npc: Entity.NPC): String {
        if (!isBoss(npc)) return ""

        val bossProperty = npc.properties["boss_designation"] ?: return ""
        val flagMatch = Regex("victoryFlag=([^,)]+)").find(bossProperty)
        return flagMatch?.groupValues?.get(1) ?: ""
    }
}
