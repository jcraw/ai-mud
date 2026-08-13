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

/**
 * System/user prompts for [TreasureRoomDescriptionGenerator] (MUD-034n).
 */
internal object TreasureRoomPrompts {

    fun buildSystemPrompt(): String = """
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

    fun buildUserContext(
        treasureRoom: TreasureRoomComponent,
        pedestalInfo: List<TreasureRoomHandler.PedestalInfo>,
        biomeName: String,
        biomeTheme: TreasureRoomDescriptionGenerator.BiomeTheme
    ): String {
        val pedestalDescriptions = pedestalLines(pedestalInfo, biomeTheme)
        val roomState = roomStateLine(treasureRoom, pedestalInfo)

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

    private fun pedestalLines(
        pedestalInfo: List<TreasureRoomHandler.PedestalInfo>,
        biomeTheme: TreasureRoomDescriptionGenerator.BiomeTheme
    ): String {
        return pedestalInfo.joinToString("\n") { pedestal ->
            val stateLabel = when (pedestal.state) {
                PedestalState.AVAILABLE -> "available"
                PedestalState.LOCKED -> "locked by ${biomeTheme.barrierType}"
                PedestalState.EMPTY -> "empty"
            }
            "- ${pedestal.themeDescription}: ${pedestal.itemName} ($stateLabel)"
        }
    }

    private fun roomStateLine(
        treasureRoom: TreasureRoomComponent,
        pedestalInfo: List<TreasureRoomHandler.PedestalInfo>
    ): String {
        return when {
            treasureRoom.currentlyTakenItem != null -> {
                val takenItem = pedestalInfo.firstOrNull {
                    it.itemName == treasureRoom.currentlyTakenItem
                }?.itemName ?: treasureRoom.currentlyTakenItem
                "The player has taken the $takenItem. Magical barriers have sealed the other pedestals."
            }
            else -> "All pedestals are available. The player may claim one treasure."
        }
    }
}
