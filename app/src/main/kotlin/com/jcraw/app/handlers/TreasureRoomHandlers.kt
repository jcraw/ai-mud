package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.core.*
import com.jcraw.mud.reasoning.treasureroom.TreasureRoomHandler

/**
 * Handlers for treasure room interactions: take, return, examine
 */
object TreasureRoomHandlers {

    /**
     * Handle taking an item from a treasure room pedestal
     */
    fun handleTakeTreasure(game: MudGame, itemTarget: String) {
        // Get current space
        val spaceId = game.worldState.player.currentRoomId

        // Find treasure room component
        val treasureRoomComponent = game.worldState.getTreasureRoom(spaceId)

        if (treasureRoomComponent == null) {
            println("This isn't a treasure room. Use 'take' for regular items.")
            return
        }

        // Get player inventory component
        val playerInventory = game.worldState.player.inventoryComponent
        if (playerInventory == null) {
            println("Inventory system not available (V2 Item System required)")
            return
        }

        // Build item templates map
        val templates = buildItemTemplatesMap(game, treasureRoomComponent)

        // Find item template ID by name
        val itemTemplateId = findItemTemplateByName(itemTarget, templates, treasureRoomComponent)
        if (itemTemplateId == null) {
            println("That item is not on any pedestal in this room.")
            println("Available items: ${getAvailableItemNames(treasureRoomComponent, templates).joinToString(", ")}")
            return
        }

        // Call treasure room handler
        val result = game.treasureRoomHandler.takeItemFromPedestal(
            treasureRoom = treasureRoomComponent,
            playerInventory = playerInventory,
            itemTemplateId = itemTemplateId,
            itemTemplates = templates
        )

        // Handle result
        when (result) {
            is TreasureRoomHandler.TreasureRoomResult.Success -> {
                // Update world state with new components
                var newState = game.worldState.updatePlayer(
                    game.worldState.player.copy(inventoryComponent = result.playerInventory)
                )

                // Update treasure room component
                newState = newState.updateTreasureRoom(spaceId, result.treasureRoomComponent)

                game.worldState = newState

                // Print success message with atmospheric description
                println("You take the ${result.itemName} from its ${getPedestalDescription(treasureRoomComponent, itemTemplateId)}.")

                // If other pedestals are now locked, describe barriers descending
                if (result.treasureRoomComponent.currentlyTakenItem != null) {
                    val barrierType = getBarrierTypeForBiome(treasureRoomComponent.biomeTheme)
                    println("\nAs you claim the ${result.itemName}, $barrierType descend over the other pedestals, sealing them away.")
                    println("You may return the ${result.itemName} to swap your choice, but once you leave this room with it, your decision is final.")
                }
            }
            is TreasureRoomHandler.TreasureRoomResult.Failure -> {
                println(result.reason)
            }
        }
    }

    /**
     * Handle returning an item to a treasure room pedestal
     */
    fun handleReturnTreasure(game: MudGame, itemTarget: String) {
        // Get current space
        val spaceId = game.worldState.player.currentRoomId

        // Find treasure room component
        val treasureRoomComponent = game.worldState.getTreasureRoom(spaceId)

        if (treasureRoomComponent == null) {
            println("This isn't a treasure room.")
            return
        }

        // Get player inventory component
        val playerInventory = game.worldState.player.inventoryComponent
        if (playerInventory == null) {
            println("Inventory system not available (V2 Item System required)")
            return
        }

        // Build item templates map
        val templates = buildItemTemplatesMap(game, treasureRoomComponent)

        // Find item instance in player inventory by name
        val itemInstance = playerInventory.items.find { instance ->
            val template = templates[instance.templateId]
            template?.name?.lowercase()?.contains(itemTarget.lowercase()) == true
        }

        if (itemInstance == null) {
            println("You don't have that item in your inventory.")
            return
        }

        // Call treasure room handler
        val result = game.treasureRoomHandler.returnItemToPedestal(
            treasureRoom = treasureRoomComponent,
            playerInventory = playerInventory,
            itemInstanceId = itemInstance.id,
            itemTemplates = templates
        )

        // Handle result
        when (result) {
            is TreasureRoomHandler.TreasureRoomResult.Success -> {
                // Update world state with new components
                var newState = game.worldState.updatePlayer(
                    game.worldState.player.copy(inventoryComponent = result.playerInventory)
                )

                // Update treasure room component
                newState = newState.updateTreasureRoom(spaceId, result.treasureRoomComponent)

                game.worldState = newState

                // Print success message with atmospheric description
                println("You return the ${result.itemName} to its ${getPedestalDescription(treasureRoomComponent, itemInstance.templateId)}.")

                // Describe barriers lifting
                val barrierType = getBarrierTypeForBiome(treasureRoomComponent.biomeTheme)
                println("\nThe $barrierType shimmer and fade, revealing the other treasures once more. You may choose again.")
            }
            is TreasureRoomHandler.TreasureRoomResult.Failure -> {
                println(result.reason)
            }
        }
    }

    /**
     * Handle examining treasure room pedestals
     */
    fun handleExaminePedestal(game: MudGame, target: String?) {
        // Get current space
        val spaceId = game.worldState.player.currentRoomId

        // Find treasure room component
        val treasureRoomComponent = game.worldState.getTreasureRoom(spaceId)

        if (treasureRoomComponent == null) {
            println("There are no pedestals or altars here.")
            return
        }

        // Build item templates map
        val templates = buildItemTemplatesMap(game, treasureRoomComponent)

        // Get pedestal info
        val pedestalInfos = game.treasureRoomHandler.getPedestalInfo(treasureRoomComponent, templates)

        // Print treasure room status
        if (treasureRoomComponent.hasBeenLooted) {
            println("The treasure room stands empty, its magic spent. Only bare altars remain.")
            return
        }

        // Print header
        println("=== Treasure Room ===")
        if (treasureRoomComponent.currentlyTakenItem != null) {
            val currentTemplate = templates[treasureRoomComponent.currentlyTakenItem]
            val currentName = currentTemplate?.name ?: treasureRoomComponent.currentlyTakenItem
            println("Current choice: ${currentName}")
            println("(Magical barriers seal the other treasures. Return your choice to swap.)")
        } else {
            println("You may claim one treasure. Choose wisely - the others will be sealed away.")
        }
        println()

        // Print each pedestal
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

            println("${info.pedestalIndex + 1}. $stateSymbol $rarityColor${info.itemName} - $stateText")
            println("   ${info.themeDescription}")
            if (info.state == PedestalState.AVAILABLE) {
                println("   ${info.itemDescription}")
            }
            println()
        }
    }

    /**
     * Build item templates map for treasure room items
     */
    private fun buildItemTemplatesMap(game: MudGame, treasureRoom: TreasureRoomComponent): Map<String, ItemTemplate> {
        val templates = mutableMapOf<String, ItemTemplate>()
        treasureRoom.pedestals.forEach { pedestal ->
            val result = game.itemRepository.findTemplateById(pedestal.itemTemplateId)
            result.getOrNull()?.let { templates[it.id] = it }
        }
        return templates
    }

    /**
     * Find item template ID by partial name match
     */
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

    /**
     * Get available item names for error message
     */
    private fun getAvailableItemNames(
        treasureRoom: TreasureRoomComponent,
        templates: Map<String, ItemTemplate>
    ): List<String> {
        return treasureRoom.pedestals
            .filter { it.state == PedestalState.AVAILABLE }
            .mapNotNull { pedestal -> templates[pedestal.itemTemplateId]?.name }
    }

    /**
     * Get pedestal description for an item
     */
    private fun getPedestalDescription(treasureRoom: TreasureRoomComponent, itemTemplateId: String): String {
        return treasureRoom.getPedestal(itemTemplateId)?.themeDescription ?: "pedestal"
    }

    /**
     * Get barrier type based on dungeon biome theme
     */
    private fun getBarrierTypeForBiome(biomeTheme: String): String {
        return when (biomeTheme.lowercase()) {
            "ancient_abyss", "ancient_ruins" -> "shimmering arcane barriers"
            "magma_cave", "magma_caves" -> "walls of molten energy"
            "frozen_depths", "ice_cavern" -> "frozen barriers of solid ice"
            "bone_crypt", "bone_crypts" -> "cages of blackened bone"
            else -> "magical barriers"
        }
    }
}
