package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.core.*
import com.jcraw.mud.reasoning.pickpocket.PickpocketHandler

/**
 * Handlers for pickpocketing: stealing from and placing items on NPCs
 * Integrates PickpocketHandler (reasoning) with game state management
 */
object PickpocketHandlers {

    /**
     * Handle pickpocket intent - stealing gold or items from NPCs
     *
     * @param game Current game instance
     * @param action "steal" or "pickpocket"
     * @param npcTarget Target NPC name
     * @param itemTarget Optional item name (null = steal gold)
     */
    fun handleSteal(
        game: MudGame,
        action: String,
        npcTarget: String,
        itemTarget: String?
    ) {
        val spaceId = game.worldState.player.currentRoomId

        // Find target NPC in space
        val targetNpc = findNpcInSpace(game, spaceId, npcTarget)
        if (targetNpc == null) {
            println("You don't see $npcTarget here.")
            return
        }

        // Get player inventory
        val playerInventory = game.worldState.player.inventoryComponent
        if (playerInventory == null) {
            println("You don't have an inventory.")
            return
        }

        // Get player skills
        val playerSkills = game.skillManager.getSkillComponent(game.worldState.player.id)
        if (playerSkills == null) {
            println("You don't have skills to attempt this.")
            return
        }

        // Build template map for lookups
        val templates = buildTemplateMap(game, playerInventory, targetNpc)

        // Create pickpocket handler
        val pickpocketHandler = PickpocketHandler(game.itemRepository)

        // Attempt to steal
        val result = pickpocketHandler.stealFromNPC(
            playerInventory = playerInventory,
            playerSkills = playerSkills,
            targetNpc = targetNpc,
            itemTarget = itemTarget,
            templates = templates
        )

        // Handle result
        when (result) {
            is PickpocketHandler.PickpocketResult.Success -> {
                handleStealSuccess(game, result, targetNpc, spaceId)
            }
            is PickpocketHandler.PickpocketResult.Caught -> {
                handleCaught(game, result, targetNpc, spaceId, "steal from")
            }
            is PickpocketHandler.PickpocketResult.Failure -> {
                println(result.reason)
            }
        }
    }

    /**
     * Handle place intent - sneaking items into NPC's inventory
     *
     * @param game Current game instance
     * @param npcTarget Target NPC name
     * @param itemTarget Item name to place
     */
    fun handlePlace(
        game: MudGame,
        npcTarget: String,
        itemTarget: String
    ) {
        val spaceId = game.worldState.player.currentRoomId

        // Find target NPC in space
        val targetNpc = findNpcInSpace(game, spaceId, npcTarget)
        if (targetNpc == null) {
            println("You don't see $npcTarget here.")
            return
        }

        // Get player inventory
        val playerInventory = game.worldState.player.inventoryComponent
        if (playerInventory == null) {
            println("You don't have an inventory.")
            return
        }

        // Get player skills
        val playerSkills = game.skillManager.getSkillComponent(game.worldState.player.id)
        if (playerSkills == null) {
            println("You don't have skills to attempt this.")
            return
        }

        // Find item in player's inventory
        val item = playerInventory.items.find { instance ->
            val templateResult = game.itemRepository.findTemplateById(instance.templateId)
            templateResult.getOrNull()?.name?.equals(itemTarget, ignoreCase = true) == true ||
            templateResult.getOrNull()?.name?.lowercase()?.contains(itemTarget.lowercase()) == true
        }

        if (item == null) {
            println("You don't have '$itemTarget' to place.")
            return
        }

        // Build template map for lookups
        val templates = buildTemplateMap(game, playerInventory, targetNpc)

        // Create pickpocket handler
        val pickpocketHandler = PickpocketHandler(game.itemRepository)

        // Attempt to place item
        val result = pickpocketHandler.placeItemOnNPC(
            playerInventory = playerInventory,
            playerSkills = playerSkills,
            targetNpc = targetNpc,
            instanceId = item.id,
            templates = templates
        )

        // Handle result
        when (result) {
            is PickpocketHandler.PickpocketResult.Success -> {
                handlePlaceSuccess(game, result, targetNpc, spaceId)
            }
            is PickpocketHandler.PickpocketResult.Caught -> {
                handleCaught(game, result, targetNpc, spaceId, "plant something on")
            }
            is PickpocketHandler.PickpocketResult.Failure -> {
                println(result.reason)
            }
        }
    }

    /**
     * Handle successful steal
     */
    private fun handleStealSuccess(
        game: MudGame,
        result: PickpocketHandler.PickpocketResult.Success,
        originalNpc: Entity.NPC,
        spaceId: String
    ) {
        // Update player inventory
        val updatedPlayer = game.worldState.player.copy(inventoryComponent = result.playerInventory)

        // Update world state with updated NPC
        val newState = game.worldState
            .updatePlayer(updatedPlayer)
            .replaceEntityInSpace(spaceId, originalNpc.id, result.targetNpc)

        game.worldState = newState

        // Narrate the success
        val rollInfo = "(rolled ${result.roll} + skill = ${result.total} vs DC ${result.dc})"
        println("You deftly ${result.action} ${result.itemName} from ${originalNpc.name}. $rollInfo")
    }

    /**
     * Handle successful place
     */
    private fun handlePlaceSuccess(
        game: MudGame,
        result: PickpocketHandler.PickpocketResult.Success,
        originalNpc: Entity.NPC,
        spaceId: String
    ) {
        // Update player inventory
        val updatedPlayer = game.worldState.player.copy(inventoryComponent = result.playerInventory)

        // Update world state with updated NPC
        val newState = game.worldState
            .updatePlayer(updatedPlayer)
            .replaceEntityInSpace(spaceId, originalNpc.id, result.targetNpc)

        game.worldState = newState

        // Narrate the success
        val rollInfo = "(rolled ${result.roll} + skill = ${result.total} vs DC ${result.dc})"
        println("You secretly ${result.action} ${result.itemName} in ${originalNpc.name}'s belongings. $rollInfo")
    }

    /**
     * Handle getting caught pickpocketing
     */
    private fun handleCaught(
        game: MudGame,
        result: PickpocketHandler.PickpocketResult.Caught,
        originalNpc: Entity.NPC,
        spaceId: String,
        attemptedAction: String
    ) {
        // Update world state with updated NPC (with disposition penalty and wariness)
        val newState = game.worldState
            .replaceEntityInSpace(spaceId, originalNpc.id, result.targetNpc)

        game.worldState = newState

        // Narrate the failure
        val rollInfo = "(rolled ${result.roll} + skill = ${result.total} vs DC ${result.dc})"
        println()
        println("${originalNpc.name} catches you trying to $attemptedAction them! $rollInfo")
        println("Their disposition toward you drops by ${result.dispositionDelta}.")
        println("${originalNpc.name} becomes wary and alert (+20 Perception for 10 turns).")
    }

    /**
     * Find an NPC in the current space
     */
    private fun findNpcInSpace(game: MudGame, spaceId: String, npcTarget: String): Entity.NPC? {
        return game.worldState.getEntitiesInSpace(spaceId)
            .filterIsInstance<Entity.NPC>()
            .find { npc ->
                npc.name.lowercase().contains(npcTarget.lowercase()) ||
                npc.id.lowercase().contains(npcTarget.lowercase())
            }
    }

    /**
     * Build a map of item templates for lookups
     */
    private fun buildTemplateMap(
        game: MudGame,
        playerInventory: InventoryComponent,
        targetNpc: Entity.NPC
    ): Map<String, ItemTemplate> {
        val templates = mutableMapOf<String, ItemTemplate>()

        // Get templates for player items
        playerInventory.items.forEach { instance ->
            val result = game.itemRepository.findTemplateById(instance.templateId)
            result.getOrNull()?.let { templates[it.id] = it }
        }

        // Get templates for NPC items
        val npcInventory = targetNpc.getComponent<InventoryComponent>(ComponentType.INVENTORY)
        npcInventory?.items?.forEach { instance ->
            val result = game.itemRepository.findTemplateById(instance.templateId)
            result.getOrNull()?.let { templates[it.id] = it }
        }

        return templates
    }
}
