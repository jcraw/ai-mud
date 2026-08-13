@file:Suppress(
    "ReturnCount",
    "MagicNumber",
    "MaxLineLength",
    "TooManyFunctions",
    "LongMethod",
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "LongParameterList",
    "TooGenericExceptionCaught",
    "SwallowedException",
    "ThrowsCount",
    "UnusedParameter"
)

package com.jcraw.mud.reasoning.treasureroom

import com.jcraw.mud.core.PedestalState
import com.jcraw.mud.core.TreasureRoomComponent
import com.jcraw.sophia.llm.LLMClient

/**
 * Generates atmospheric descriptions for treasure rooms with biome-adaptive theming
 * Follows Brogue-inspired design with emphasis on choice and consequence
 *
 * Thin facade — bodies in TreasureRoom* / TreasureBiomeThemes extracts (MUD-034n).
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
            return TreasureRoomFallbacks.generateLootedRoomDescription(biomeTheme)
        }

        val systemPrompt = TreasureRoomPrompts.buildSystemPrompt()
        val userContext = TreasureRoomPrompts.buildUserContext(
            treasureRoom = treasureRoom,
            pedestalInfo = pedestalInfo,
            biomeName = biomeName,
            biomeTheme = biomeTheme
        )

        return completeRoomDescription(systemPrompt, userContext, treasureRoom, pedestalInfo, biomeTheme)
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

    private suspend fun completeRoomDescription(
        systemPrompt: String,
        userContext: String,
        treasureRoom: TreasureRoomComponent,
        pedestalInfo: List<TreasureRoomHandler.PedestalInfo>,
        biomeTheme: BiomeTheme
    ): String = TreasureRoomComplete.complete(
        llmClient,
        systemPrompt,
        userContext,
        treasureRoom,
        pedestalInfo,
        biomeTheme
    )

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
        val DEFAULT_BIOME_THEMES = TreasureBiomeThemes.DEFAULT

        /**
         * Get biome theme by name, with fallback to ancient_abyss default
         */
        fun getBiomeTheme(biomeName: String): BiomeTheme = TreasureBiomeThemes.get(biomeName)
    }
}
