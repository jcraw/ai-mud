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

package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.PedestalState
import com.jcraw.mud.core.Rarity
import com.jcraw.mud.core.TreasureRoomComponent
import com.jcraw.mud.reasoning.treasureroom.TreasureRoomHandler

/**
 * Examine pedestals for [ClientTreasureRoomHandlers] facade (MUD-034l pure-move).
 */
internal object ClientTreasureExamineHandlers {

    fun handleExaminePedestal(game: EngineGameClient, target: String?) {
        val spaceId = game.worldState.player.currentRoomId
        val treasureRoomComponent = game.worldState.getTreasureRoom(spaceId)
        if (treasureRoomComponent == null) {
            game.emitEvent(
                GameEvent.System("There are no pedestals or altars here.", GameEvent.MessageLevel.INFO)
            )
            return
        }
        val templates = ClientTreasurePedestalSupport.buildItemTemplatesMap(game, treasureRoomComponent)
        val pedestalInfos = game.treasureRoomHandler.getPedestalInfo(treasureRoomComponent, templates)
        if (treasureRoomComponent.hasBeenLooted) {
            game.emitEvent(
                GameEvent.Narrative("The treasure room stands empty, its magic spent. Only bare altars remain.")
            )
            return
        }
        game.emitEvent(GameEvent.Narrative(buildExamineText(treasureRoomComponent, templates, pedestalInfos)))
    }

    private fun buildExamineText(
        treasureRoomComponent: TreasureRoomComponent,
        templates: Map<String, ItemTemplate>,
        pedestalInfos: List<TreasureRoomHandler.PedestalInfo>
    ): String {
        return buildString {
            appendLine("=== Treasure Room ===")
            appendCurrentChoice(this, treasureRoomComponent, templates)
            appendLine()
            pedestalInfos.sortedBy { it.pedestalIndex }.forEach { info ->
                appendPedestal(this, treasureRoomComponent, templates, info)
            }
        }
    }

    private fun appendCurrentChoice(
        builder: StringBuilder,
        treasureRoomComponent: TreasureRoomComponent,
        templates: Map<String, ItemTemplate>
    ) {
        if (treasureRoomComponent.currentlyTakenItem != null) {
            val currentTemplate = templates[treasureRoomComponent.currentlyTakenItem]
            val currentName = currentTemplate?.name ?: treasureRoomComponent.currentlyTakenItem
            builder.appendLine("Current choice: $currentName")
            builder.appendLine("(Magical barriers seal the other treasures. Return your choice to swap.)")
        } else {
            builder.appendLine("You may claim one treasure. Choose wisely - the others will be sealed away.")
        }
    }

    private fun appendPedestal(
        builder: StringBuilder,
        treasureRoomComponent: TreasureRoomComponent,
        templates: Map<String, ItemTemplate>,
        info: TreasureRoomHandler.PedestalInfo
    ) {
        val stateSymbol = stateSymbol(info.state)
        val stateText = stateText(info.state)
        val rarityColor = rarityPrefix(info.rarity)
        val statsText = pedestalStats(treasureRoomComponent, templates, info)
        builder.appendLine(
            "${info.pedestalIndex + 1}. $stateSymbol $rarityColor${info.itemName}$statsText - $stateText"
        )
        builder.appendLine("   ${info.themeDescription}")
        if (info.state == PedestalState.AVAILABLE) {
            builder.appendLine("   ${info.itemDescription}")
        }
        builder.appendLine()
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
            val stats = ClientTreasurePedestalSupport.extractItemStats(template)
            if (stats.isNotEmpty()) " (${stats.joinToString(", ")})" else ""
        } ?: ""
    }
}
