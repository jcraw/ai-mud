package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.*
import com.jcraw.mud.reasoning.treasureroom.TreasureRoomHandler

/**
 * Handles treasure room interactions in the GUI client
 */
object ClientTreasureRoomHandlers {

    /**
     * Handle taking an item from a treasure room pedestal
     */
    fun handleTakeTreasure(game: EngineGameClient, itemTarget: String) {
        val spaceId = game.worldState.player.currentRoomId
        val treasureRoomComponent = game.worldState.getTreasureRoom(spaceId)

        if (treasureRoomComponent == null) {
            game.emitEvent(GameEvent.System("This isn't a treasure room. Use 'take' for regular items.", GameEvent.MessageLevel.WARNING))
            return
        }

        val playerInventory = game.worldState.player.inventoryComponent
        if (playerInventory == null) {
            game.emitEvent(GameEvent.System("Inventory system not available (V2 Item System required)", GameEvent.MessageLevel.WARNING))
            return
        }

        val templates = buildItemTemplatesMap(game, treasureRoomComponent)
        val itemTemplateId = findItemTemplateByName(itemTarget, templates, treasureRoomComponent)

        if (itemTemplateId == null) {
            val available = getAvailableItemNames(treasureRoomComponent, templates).joinToString(", ")
            game.emitEvent(GameEvent.System("That item is not on any pedestal in this room.\nAvailable items: $available", GameEvent.MessageLevel.WARNING))
            return
        }

        val result = game.treasureRoomHandler.takeItemFromPedestal(
            treasureRoom = treasureRoomComponent,
            playerInventory = playerInventory,
            itemTemplateId = itemTemplateId,
            itemTemplates = templates
        )

        when (result) {
            is TreasureRoomHandler.TreasureRoomResult.Success -> {
                var newState = game.worldState.updatePlayer(
                    game.worldState.player.copy(inventoryComponent = result.playerInventory)
                )
                newState = newState.updateTreasureRoom(spaceId, result.treasureRoomComponent)
                game.worldState = newState

                val pedestalDesc = getPedestalDescription(treasureRoomComponent, itemTemplateId)
                game.emitEvent(GameEvent.Narrative("You take the ${result.itemName} from its $pedestalDesc."))

                if (result.treasureRoomComponent.currentlyTakenItem != null) {
                    val barrierType = getBarrierTypeForBiome(treasureRoomComponent.biomeTheme)
                    game.emitEvent(GameEvent.Narrative("\nAs you claim the ${result.itemName}, $barrierType descend over the other pedestals, sealing them away."))
                    game.emitEvent(GameEvent.Narrative("You may return to this room at any time to swap your choice for a different treasure."))
                }
            }
            is TreasureRoomHandler.TreasureRoomResult.Failure -> {
                game.emitEvent(GameEvent.System(result.reason, GameEvent.MessageLevel.WARNING))
            }
        }
    }

    /**
     * Handle returning an item to a treasure room pedestal
     */
    fun handleReturnTreasure(game: EngineGameClient, itemTarget: String) {
        val spaceId = game.worldState.player.currentRoomId
        val treasureRoomComponent = game.worldState.getTreasureRoom(spaceId)

        if (treasureRoomComponent == null) {
            game.emitEvent(GameEvent.System("This isn't a treasure room.", GameEvent.MessageLevel.WARNING))
            return
        }

        val playerInventory = game.worldState.player.inventoryComponent
        if (playerInventory == null) {
            game.emitEvent(GameEvent.System("Inventory system not available (V2 Item System required)", GameEvent.MessageLevel.WARNING))
            return
        }

        val templates = buildItemTemplatesMap(game, treasureRoomComponent)
        val itemInstance = playerInventory.items.find { instance ->
            val template = templates[instance.templateId]
            template?.name?.lowercase()?.contains(itemTarget.lowercase()) == true
        }

        if (itemInstance == null) {
            game.emitEvent(GameEvent.System("You don't have that item in your inventory.", GameEvent.MessageLevel.WARNING))
            return
        }

        val result = game.treasureRoomHandler.returnItemToPedestal(
            treasureRoom = treasureRoomComponent,
            playerInventory = playerInventory,
            itemInstanceId = itemInstance.id,
            itemTemplates = templates
        )

        when (result) {
            is TreasureRoomHandler.TreasureRoomResult.Success -> {
                var newState = game.worldState.updatePlayer(
                    game.worldState.player.copy(inventoryComponent = result.playerInventory)
                )
                newState = newState.updateTreasureRoom(spaceId, result.treasureRoomComponent)
                game.worldState = newState

                val pedestalDesc = getPedestalDescription(treasureRoomComponent, itemInstance.templateId)
                game.emitEvent(GameEvent.Narrative("You return the ${result.itemName} to its $pedestalDesc."))

                val barrierType = getBarrierTypeForBiome(treasureRoomComponent.biomeTheme)
                game.emitEvent(GameEvent.Narrative("\nThe $barrierType shimmer and fade, revealing the other treasures once more. You may choose again."))
            }
            is TreasureRoomHandler.TreasureRoomResult.Failure -> {
                game.emitEvent(GameEvent.System(result.reason, GameEvent.MessageLevel.WARNING))
            }
        }
    }

    /**
     * Handle examining treasure room pedestals
     */
    fun handleExaminePedestal(game: EngineGameClient, target: String?) {
        val spaceId = game.worldState.player.currentRoomId
        val treasureRoomComponent = game.worldState.getTreasureRoom(spaceId)

        if (treasureRoomComponent == null) {
            game.emitEvent(GameEvent.System("There are no pedestals or altars here.", GameEvent.MessageLevel.INFO))
            return
        }

        val templates = buildItemTemplatesMap(game, treasureRoomComponent)
        val pedestalInfos = game.treasureRoomHandler.getPedestalInfo(treasureRoomComponent, templates)

        if (treasureRoomComponent.hasBeenLooted) {
            game.emitEvent(GameEvent.Narrative("The treasure room stands empty, its magic spent. Only bare altars remain."))
            return
        }

        val text = buildString {
            appendLine("=== Treasure Room ===")
            if (treasureRoomComponent.currentlyTakenItem != null) {
                val currentTemplate = templates[treasureRoomComponent.currentlyTakenItem]
                val currentName = currentTemplate?.name ?: treasureRoomComponent.currentlyTakenItem
                appendLine("Current choice: $currentName")
                appendLine("(Magical barriers seal the other treasures. Return your choice to swap.)")
            } else {
                appendLine("You may claim one treasure. Choose wisely - the others will be sealed away.")
            }
            appendLine()

            pedestalInfos.sortedBy { it.pedestalIndex }.forEach { info ->
                val stateSymbol = when (info.state) {
                    PedestalState.AVAILABLE -> "✓"
                    PedestalState.LOCKED -> "✗"
                    PedestalState.EMPTY -> "○"
                }
                val stateText = when (info.state) {
                    PedestalState.AVAILABLE -> "Available"
                    PedestalState.LOCKED -> "Locked"
                    PedestalState.EMPTY -> "Empty"
                }

                val rarityColor = when (info.rarity) {
                    Rarity.COMMON -> ""
                    Rarity.UNCOMMON -> "[Uncommon] "
                    Rarity.RARE -> "[RARE] "
                    Rarity.EPIC -> "[EPIC] "
                    Rarity.LEGENDARY -> "[LEGENDARY] "
                }

                // Find the item template to extract stats
                val itemTemplate = treasureRoomComponent.pedestals[info.pedestalIndex].let { pedestal ->
                    templates[pedestal.itemTemplateId]
                }

                // Extract stat bonuses and format them
                val statsText = itemTemplate?.let { template ->
                    val stats = extractItemStats(template)
                    if (stats.isNotEmpty()) " (${stats.joinToString(", ")})" else ""
                } ?: ""

                appendLine("${info.pedestalIndex + 1}. $stateSymbol $rarityColor${info.itemName}$statsText - $stateText")
                appendLine("   ${info.themeDescription}")
                if (info.state == PedestalState.AVAILABLE) {
                    appendLine("   ${info.itemDescription}")
                }
                appendLine()
            }
        }

        game.emitEvent(GameEvent.Narrative(text))
    }

    private fun buildItemTemplatesMap(game: EngineGameClient, treasureRoom: TreasureRoomComponent): Map<String, ItemTemplate> {
        val templates = mutableMapOf<String, ItemTemplate>()
        treasureRoom.pedestals.forEach { pedestal ->
            game.itemTemplateCache[pedestal.itemTemplateId]?.let { templates[it.id] = it }
        }
        return templates
    }

    private fun findItemTemplateByName(
        nameQuery: String,
        templates: Map<String, ItemTemplate>,
        treasureRoom: TreasureRoomComponent
    ): String? {
        return treasureRoom.pedestals
            .firstOrNull { pedestal ->
                val template = templates[pedestal.itemTemplateId]
                template?.name?.lowercase()?.contains(nameQuery.lowercase()) == true
            }
            ?.itemTemplateId
    }

    private fun getAvailableItemNames(
        treasureRoom: TreasureRoomComponent,
        templates: Map<String, ItemTemplate>
    ): List<String> {
        return treasureRoom.pedestals
            .filter { it.state == PedestalState.AVAILABLE }
            .mapNotNull { pedestal -> templates[pedestal.itemTemplateId]?.name }
    }

    private fun getPedestalDescription(treasureRoom: TreasureRoomComponent, itemTemplateId: String): String {
        return treasureRoom.getPedestal(itemTemplateId)?.themeDescription ?: "pedestal"
    }

    private fun getBarrierTypeForBiome(biomeTheme: String): String {
        return when (biomeTheme.lowercase()) {
            "ancient_abyss", "ancient_ruins" -> "shimmering arcane barriers"
            "magma_cave", "magma_caves" -> "walls of molten energy"
            "frozen_depths", "ice_cavern" -> "frozen barriers of solid ice"
            "bone_crypt", "bone_crypts" -> "cages of blackened bone"
            else -> "magical barriers"
        }
    }

    /**
     * Extract stat bonuses from item template properties
     */
    private fun extractItemStats(template: ItemTemplate): List<String> {
        val stats = mutableListOf<String>()

        // Skill bonuses
        template.properties["skill_bonus_strength"]?.toIntOrNull()?.let { bonus ->
            if (bonus > 0) stats.add("STR +$bonus")
        }
        template.properties["skill_bonus_agility"]?.toIntOrNull()?.let { bonus ->
            if (bonus > 0) stats.add("AGI +$bonus")
        }
        template.properties["skill_bonus_endurance"]?.toIntOrNull()?.let { bonus ->
            if (bonus > 0) stats.add("END +$bonus")
        }
        template.properties["skill_bonus_magic"]?.toIntOrNull()?.let { bonus ->
            if (bonus > 0) stats.add("MAG +$bonus")
        }
        template.properties["skill_bonus_wisdom"]?.toIntOrNull()?.let { bonus ->
            if (bonus > 0) stats.add("WIS +$bonus")
        }
        template.properties["skill_bonus_charisma"]?.toIntOrNull()?.let { bonus ->
            if (bonus > 0) stats.add("CHA +$bonus")
        }
        template.properties["skill_bonus_perception"]?.toIntOrNull()?.let { bonus ->
            if (bonus > 0) stats.add("PER +$bonus")
        }

        // Weapon/armor stats
        template.properties["damage"]?.toIntOrNull()?.let { damage ->
            if (damage > 0) stats.add(0, "${damage} dmg")  // Add damage first
        }
        template.properties["defense"]?.toIntOrNull()?.let { defense ->
            if (defense > 0) stats.add(0, "${defense} def")  // Add defense first
        }

        return stats
    }
}
