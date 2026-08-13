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
 * Looted / fallback descriptions for [TreasureRoomDescriptionGenerator] (MUD-034n).
 */
internal object TreasureRoomFallbacks {

    fun generateFallbackDescription(
        treasureRoom: TreasureRoomComponent,
        pedestalInfo: List<TreasureRoomHandler.PedestalInfo>,
        biomeTheme: TreasureRoomDescriptionGenerator.BiomeTheme
    ): String {
        val availableCount = pedestalInfo.count { it.state == PedestalState.AVAILABLE }
        return when {
            treasureRoom.currentlyTakenItem != null -> takenFallback(biomeTheme)
            availableCount > 0 -> availableFallback(biomeTheme)
            else -> "The treasure chamber stands empty, its pedestals bare. The magic has faded."
        }
    }

    private fun takenFallback(biomeTheme: TreasureRoomDescriptionGenerator.BiomeTheme): String {
        return "You stand in a treasure chamber of ${biomeTheme.material} pedestals. " +
            "Having claimed one treasure, magical barriers now seal the remaining pedestals. " +
            "You may return your treasure to swap, or depart with your choice."
    }

    private fun availableFallback(biomeTheme: TreasureRoomDescriptionGenerator.BiomeTheme): String {
        return "Five ${biomeTheme.material} pedestals stand before you, each bearing a legendary treasure. " +
            "Ancient magic permeates this chamber—you may claim one item to define your path, " +
            "but choose wisely. Once taken, the others will be sealed away."
    }

    fun generateLootedRoomDescription(biomeTheme: TreasureRoomDescriptionGenerator.BiomeTheme): String {
        return "The treasure chamber feels hollow, its magic spent. " +
            "Empty ${biomeTheme.material} pedestals stand as silent monuments to the choice you made."
    }
}
