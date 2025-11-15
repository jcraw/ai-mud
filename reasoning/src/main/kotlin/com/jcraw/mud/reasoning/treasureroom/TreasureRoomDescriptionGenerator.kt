package com.jcraw.mud.reasoning.treasureroom

import com.jcraw.mud.core.PedestalState
import com.jcraw.mud.core.TreasureRoomComponent
import com.jcraw.sophia.llm.LLMClient

/**
 * Generates atmospheric descriptions for treasure rooms with biome-adaptive theming
 * Follows Brogue-inspired design with emphasis on choice and consequence
 */
class TreasureRoomDescriptionGenerator(
    private val llmClient: LLMClient
) {

    /**
     * Generate full treasure room description with atmospheric narrative and pedestal details
     *
     * @param treasureRoom Current treasure room component state
     * @param pedestalInfo List of pedestal information from TreasureRoomHandler
     * @param biomeName Name of the dungeon biome (e.g., "ancient_abyss", "magma_cave")
     * @param biomeTheme Biome theme data (material, barrier type, atmosphere hints)
     * @return Atmospheric room description
     */
    suspend fun generateRoomDescription(
        treasureRoom: TreasureRoomComponent,
        pedestalInfo: List<TreasureRoomHandler.PedestalInfo>,
        biomeName: String,
        biomeTheme: BiomeTheme
    ): String {
        // If room has been looted, return simple description
        if (treasureRoom.hasBeenLooted) {
            return generateLootedRoomDescription(biomeTheme)
        }

        val systemPrompt = buildSystemPrompt()
        val userContext = buildUserContext(
            treasureRoom = treasureRoom,
            pedestalInfo = pedestalInfo,
            biomeName = biomeName,
            biomeTheme = biomeTheme
        )

        return try {
            val response = llmClient.chatCompletion(
                modelId = "gpt-4o-mini",
                systemPrompt = systemPrompt,
                userContext = userContext,
                maxTokens = 250,
                temperature = 0.8  // Higher temperature for atmospheric variety
            )

            response.choices.firstOrNull()?.message?.content?.trim()
                ?: generateFallbackDescription(treasureRoom, pedestalInfo, biomeTheme)
        } catch (e: Exception) {
            println("⚠️ Treasure room description generation failed: ${e.message}")
            generateFallbackDescription(treasureRoom, pedestalInfo, biomeTheme)
        }
    }

    /**
     * Generate pedestal-specific description with state-aware barrier descriptions
     *
     * @param pedestal Pedestal information
     * @param biomeTheme Biome theme for barrier type
     * @return State-aware pedestal description
     */
    fun generatePedestalDescription(
        pedestal: TreasureRoomHandler.PedestalInfo,
        biomeTheme: BiomeTheme
    ): String {
        return when (pedestal.state) {
            PedestalState.AVAILABLE -> {
                "${pedestal.themeDescription} displaying ${pedestal.itemName}. The item rests freely, ready to be claimed."
            }
            PedestalState.LOCKED -> {
                "${pedestal.themeDescription} with ${pedestal.itemName} visible beyond a ${biomeTheme.barrierType}."
            }
            PedestalState.EMPTY -> {
                "${pedestal.themeDescription}, now bare. Its treasure has been claimed."
            }
        }
    }

    private fun buildSystemPrompt(): String = """
        You are a descriptive narrator for a treasure room in a dungeon crawler game.

        The treasure room follows Brogue-inspired mechanics where players must choose ONE item from multiple pedestals.
        Taking an item locks the others with magical barriers. Returning the item unlocks them for swapping.

        Guidelines:
        - Write in second person present tense ("You see...", "The chamber...")
        - Create 2-3 paragraphs that emphasize CHOICE and CONSEQUENCE
        - Incorporate the biome theme naturally (materials, atmosphere)
        - First visit: Emphasize "choose wisely" and one-time nature
        - Item taken: Describe magical barriers sealing other pedestals
        - Keep total description under 150 words
        - Be atmospheric but concise

        Example: "You enter a chamber where five ancient stone altars stand in a circle, each bearing a legendary treasure. The air hums with barely contained magic. You sense this is a place of singular importance—you may claim one treasure to define your path, but the moment you do, magical barriers will seal the others away forever."
    """.trimIndent()

    private fun buildUserContext(
        treasureRoom: TreasureRoomComponent,
        pedestalInfo: List<TreasureRoomHandler.PedestalInfo>,
        biomeName: String,
        biomeTheme: BiomeTheme
    ): String {
        val pedestalDescriptions = pedestalInfo.joinToString("\n") { pedestal ->
            val stateLabel = when (pedestal.state) {
                PedestalState.AVAILABLE -> "available"
                PedestalState.LOCKED -> "locked by ${biomeTheme.barrierType}"
                PedestalState.EMPTY -> "empty"
            }
            "- ${pedestal.themeDescription}: ${pedestal.itemName} ($stateLabel)"
        }

        val roomState = when {
            treasureRoom.currentlyTakenItem != null -> {
                val takenItem = pedestalInfo.firstOrNull {
                    it.itemName == treasureRoom.currentlyTakenItem
                }?.itemName ?: treasureRoom.currentlyTakenItem
                "The player has taken the $takenItem. Magical barriers have sealed the other pedestals."
            }
            else -> "All pedestals are available. The player may claim one treasure."
        }

        return """
            Dungeon biome: $biomeName
            Biome aesthetic: ${biomeTheme.aesthetic}
            Pedestal material: ${biomeTheme.material}
            Barrier type (when locked): ${biomeTheme.barrierType}

            Pedestals:
            $pedestalDescriptions

            Current state: $roomState

            Generate a 2-3 paragraph atmospheric description that captures the weight of this choice.
        """.trimIndent()
    }

    private fun generateFallbackDescription(
        treasureRoom: TreasureRoomComponent,
        pedestalInfo: List<TreasureRoomHandler.PedestalInfo>,
        biomeTheme: BiomeTheme
    ): String {
        val availableCount = pedestalInfo.count { it.state == PedestalState.AVAILABLE }
        val lockedCount = pedestalInfo.count { it.state == PedestalState.LOCKED }

        return when {
            treasureRoom.currentlyTakenItem != null -> {
                "You stand in a treasure chamber of ${biomeTheme.material} pedestals. " +
                "Having claimed one treasure, magical barriers now seal the remaining pedestals. " +
                "You may return your treasure to swap, or depart with your choice."
            }
            availableCount > 0 -> {
                "Five ${biomeTheme.material} pedestals stand before you, each bearing a legendary treasure. " +
                "Ancient magic permeates this chamber—you may claim one item to define your path, " +
                "but choose wisely. Once taken, the others will be sealed away."
            }
            else -> {
                "The treasure chamber stands empty, its pedestals bare. The magic has faded."
            }
        }
    }

    private fun generateLootedRoomDescription(biomeTheme: BiomeTheme): String {
        return "The treasure chamber feels hollow, its magic spent. " +
            "Empty ${biomeTheme.material} pedestals stand as silent monuments to the choice you made."
    }

    /**
     * Biome theme data for atmospheric descriptions
     */
    data class BiomeTheme(
        val material: String,              // e.g., "weathered stone", "obsidian"
        val aesthetic: String,              // e.g., "ancient, crumbling, moss-covered"
        val barrierType: String,            // e.g., "shimmering arcane barrier"
        val atmosphereHints: List<String>   // e.g., ["ancient", "crumbling", "weathered"]
    )

    companion object {
        /**
         * Default biome themes for treasure rooms
         * These map to the themes defined in treasure_room_templates.json
         */
        val DEFAULT_BIOME_THEMES = mapOf(
            "ancient_abyss" to BiomeTheme(
                material = "weathered stone",
                aesthetic = "ancient, crumbling, moss-covered",
                barrierType = "shimmering arcane barrier",
                atmosphereHints = listOf("ancient", "crumbling", "moss-covered", "weathered")
            ),
            "magma_cave" to BiomeTheme(
                material = "obsidian",
                aesthetic = "glowing, volcanic, heat-warped",
                barrierType = "wall of molten energy",
                atmosphereHints = listOf("glowing", "volcanic", "heat-warped", "smoldering")
            ),
            "frozen_depths" to BiomeTheme(
                material = "ice crystal",
                aesthetic = "frosted, glacial, pristine",
                barrierType = "frozen barrier of solid ice",
                atmosphereHints = listOf("frosted", "glacial", "pristine", "crystalline")
            ),
            "bone_crypt" to BiomeTheme(
                material = "bone",
                aesthetic = "skeletal, macabre, dusty",
                barrierType = "cage of blackened bone",
                atmosphereHints = listOf("skeletal", "macabre", "dusty", "grim")
            ),
            "elven_ruins" to BiomeTheme(
                material = "silver-veined marble",
                aesthetic = "elegant, ancient, luminous",
                barrierType = "translucent barrier of woven moonlight",
                atmosphereHints = listOf("elegant", "ancient", "luminous", "graceful")
            ),
            "dwarven_halls" to BiomeTheme(
                material = "granite",
                aesthetic = "sturdy, geometric, metallic",
                barrierType = "mechanical barrier of interlocking gears",
                atmosphereHints = listOf("sturdy", "geometric", "metallic", "fortified")
            )
        )

        /**
         * Get biome theme by name, with fallback to ancient_abyss default
         */
        fun getBiomeTheme(biomeName: String): BiomeTheme {
            return DEFAULT_BIOME_THEMES[biomeName]
                ?: DEFAULT_BIOME_THEMES["ancient_abyss"]!!
        }
    }
}
