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
    "UnusedParameter"
)

package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.PedestalState
import com.jcraw.mud.core.Rarity
import com.jcraw.mud.core.TreasureRoomComponent
import com.jcraw.mud.reasoning.treasureroom.TreasureRoomHandler

/**
 * Examine pedestals for [TreasureRoomHandlers] facade (MUD-034l pure-move).
 */
internal object TreasureExamineHandlers {

    fun handleExaminePedestal(game: MudGame, target: String?) {
        val spaceId = game.worldState.player.currentRoomId
        val treasureRoomComponent = game.worldState.getTreasureRoom(spaceId)
        if (treasureRoomComponent == null) {
            println("There are no pedestals or altars here.")
            return
        }
        val templates = TreasurePedestalSupport.buildItemTemplatesMap(game, treasureRoomComponent)
        val pedestalInfos = game.treasureRoomHandler.getPedestalInfo(treasureRoomComponent, templates)
        if (treasureRoomComponent.hasBeenLooted) {
            println("The treasure room stands empty, its magic spent. Only bare altars remain.")
            return
        }
        printExamineBody(treasureRoomComponent, templates, pedestalInfos)
    }

    private fun printExamineBody(
        treasureRoomComponent: TreasureRoomComponent,
        templates: Map<String, ItemTemplate>,
        pedestalInfos: List<TreasureRoomHandler.PedestalInfo>
    ) {
        println("=== Treasure Room ===")
        printCurrentChoice(treasureRoomComponent, templates)
        println()
        pedestalInfos.sortedBy { it.pedestalIndex }.forEach { info ->
            printPedestal(treasureRoomComponent, templates, info)
        }
    }

    private fun printCurrentChoice(
        treasureRoomComponent: TreasureRoomComponent,
        templates: Map<String, ItemTemplate>
    ) {
        if (treasureRoomComponent.currentlyTakenItem != null) {
            val currentTemplate = templates[treasureRoomComponent.currentlyTakenItem]
            val currentName = currentTemplate?.name ?: treasureRoomComponent.currentlyTakenItem
            println("Current choice: ${currentName}")
            println("(Magical barriers seal the other treasures. Return your choice to swap.)")
        } else {
            println("You may claim one treasure. Choose wisely - the others will be sealed away.")
        }
    }

    private fun printPedestal(
        treasureRoomComponent: TreasureRoomComponent,
        templates: Map<String, ItemTemplate>,
        info: TreasureRoomHandler.PedestalInfo
    ) {
        val stateSymbol = stateSymbol(info.state)
        val stateText = stateText(info.state)
        val rarityColor = rarityPrefix(info.rarity)
        val statsText = pedestalStats(treasureRoomComponent, templates, info)
        println("${info.pedestalIndex + 1}. $stateSymbol $rarityColor${info.itemName}$statsText - $stateText")
        println("   ${info.themeDescription}")
        if (info.state == PedestalState.AVAILABLE) {
            println("   ${info.itemDescription}")
        }
        println()
    }

    private fun stateSymbol(state: PedestalState): String = when (state) {
        PedestalState.AVAILABLE -> "✓"
        PedestalState.LOCKED -> "✗"
        PedestalState.EMPTY -> "○"
    }

    private fun stateText(state: PedestalState): String = when (state) {
        PedestalState.AVAILABLE -> "Available"
        PedestalState.LOCKED -> "Locked"
        PedestalState.EMPTY -> "Empty"
    }

    private fun rarityPrefix(rarity: Rarity): String = when (rarity) {
        Rarity.COMMON -> ""
        Rarity.UNCOMMON -> "[Uncommon] "
        Rarity.RARE -> "[RARE] "
        Rarity.EPIC -> "[EPIC] "
        Rarity.LEGENDARY -> "[LEGENDARY] "
    }

    private fun pedestalStats(
        treasureRoomComponent: TreasureRoomComponent,
        templates: Map<String, ItemTemplate>,
        info: TreasureRoomHandler.PedestalInfo
    ): String {
        val itemTemplate = treasureRoomComponent.pedestals[info.pedestalIndex].let { pedestal ->
            templates[pedestal.itemTemplateId]
        }
        return itemTemplate?.let { template ->
            val stats = TreasurePedestalSupport.extractItemStats(template)
            if (stats.isNotEmpty()) " (${stats.joinToString(", ")})" else ""
        } ?: ""
    }
}
