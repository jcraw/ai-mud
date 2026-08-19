@file:Suppress("TooManyFunctions", "LongParameterList", "MaxLineLength", "FunctionOnlyReturningConstant")

package com.jcraw.mud.reasoning.treasureroom

import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.PedestalState
import com.jcraw.mud.core.Rarity
import com.jcraw.mud.core.TreasureRoomComponent
import com.jcraw.mud.core.WorldState

/**
 * Take/return apply + examine/take/return text. Shared by console and GUI (MUD-039).
 */
object TreasurePedestalOps {

    data class Applied(
        val world: WorldState,
        val result: TreasureRoomHandler.TreasureRoomResult
    )

    fun takeAndApply(
        handler: TreasureRoomHandler,
        world: WorldState,
        spaceId: String,
        treasureRoom: TreasureRoomComponent,
        itemTemplateId: String,
        templates: Map<String, ItemTemplate>
    ): Applied {
        val result = handler.takeItemFromPedestal(
            treasureRoom = treasureRoom,
            playerInventory = world.player.inventoryComponent,
            itemTemplateId = itemTemplateId,
            itemTemplates = templates
        )
        val next = if (result is TreasureRoomHandler.TreasureRoomResult.Success) {
            TreasureRoomStateApply.applySuccess(world, spaceId, world.player, result)
        } else {
            world
        }
        return Applied(next, result)
    }

    fun returnAndApply(
        handler: TreasureRoomHandler,
        world: WorldState,
        spaceId: String,
        treasureRoom: TreasureRoomComponent,
        itemInstanceId: String,
        templates: Map<String, ItemTemplate>
    ): Applied {
        val result = handler.returnItemToPedestal(
            treasureRoom = treasureRoom,
            playerInventory = world.player.inventoryComponent,
            itemInstanceId = itemInstanceId,
            itemTemplates = templates
        )
        val next = if (result is TreasureRoomHandler.TreasureRoomResult.Success) {
            TreasureRoomStateApply.applySuccess(world, spaceId, world.player, result)
        } else {
            world
        }
        return Applied(next, result)
    }

    fun takeFromPedestalLine(itemName: String, pedestalDesc: String): String =
        "You take the $itemName from its $pedestalDesc."

    fun takeBarrierNarrative(itemName: String, barrierType: String): String =
        "\nAs you claim the $itemName, $barrierType descend over the other pedestals, sealing them away."

    fun takeSwapHint(): String =
        "You may return to this room at any time to swap your choice for a different treasure."

    fun returnToPedestalLine(itemName: String, pedestalDesc: String): String =
        "You return the $itemName to its $pedestalDesc."

    fun returnBarrierNarrative(barrierType: String): String =
        "\nThe $barrierType shimmer and fade, revealing the other treasures once more. You may choose again."

    fun buildExamineText(
        treasureRoom: TreasureRoomComponent,
        templates: Map<String, ItemTemplate>,
        pedestalInfos: List<TreasureRoomHandler.PedestalInfo>
    ): String {
        return buildString {
            appendLine("=== Treasure Room ===")
            appendCurrentChoice(this, treasureRoom, templates)
            appendLine()
            pedestalInfos.sortedBy { it.pedestalIndex }.forEach { info ->
                appendPedestal(this, treasureRoom, templates, info)
            }
        }
    }

    private fun appendCurrentChoice(
        builder: StringBuilder,
        treasureRoom: TreasureRoomComponent,
        templates: Map<String, ItemTemplate>
    ) {
        if (treasureRoom.currentlyTakenItem != null) {
            val currentTemplate = templates[treasureRoom.currentlyTakenItem]
            val currentName = currentTemplate?.name ?: treasureRoom.currentlyTakenItem
            builder.appendLine("Current choice: $currentName")
            builder.appendLine("(Magical barriers seal the other treasures. Return your choice to swap.)")
        } else {
            builder.appendLine("You may claim one treasure. Choose wisely - the others will be sealed away.")
        }
    }

    private fun appendPedestal(
        builder: StringBuilder,
        treasureRoom: TreasureRoomComponent,
        templates: Map<String, ItemTemplate>,
        info: TreasureRoomHandler.PedestalInfo
    ) {
        val statsText = pedestalStats(treasureRoom, templates, info)
        builder.appendLine(
            "${info.pedestalIndex + 1}. ${stateSymbol(info.state)} ${rarityPrefix(info.rarity)}${info.itemName}$statsText - ${stateText(info.state)}"
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
        treasureRoom: TreasureRoomComponent,
        templates: Map<String, ItemTemplate>,
        info: TreasureRoomHandler.PedestalInfo
    ): String {
        val itemTemplate = treasureRoom.pedestals[info.pedestalIndex].let { pedestal ->
            templates[pedestal.itemTemplateId]
        }
        return itemTemplate?.let { template ->
            val stats = TreasurePedestalSupport.extractItemStats(template)
            if (stats.isNotEmpty()) " (${stats.joinToString(", ")})" else ""
        } ?: ""
    }
}
