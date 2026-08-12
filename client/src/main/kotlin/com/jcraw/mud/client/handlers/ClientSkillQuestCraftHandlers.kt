@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList")

package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.core.InventoryComponent
import com.jcraw.mud.core.SkillComponent
import com.jcraw.mud.core.crafting.Recipe
import com.jcraw.mud.reasoning.crafting.CraftingManager

/**
 * Craft orchestrator for [ClientSkillQuestHandlers] facade.
 * Pure-move body only — Intent.Craft remains stubbed on EngineGameClient.
 */
object ClientSkillQuestCraftHandlers {

    fun handleCraft(game: EngineGameClient, target: String) {
        val craftingManager = CraftingManager(game.recipeRepository, game.itemRepository)
        val recipe = resolveRecipe(game, craftingManager, target) ?: return
        val playerInventory = game.worldState.player.inventoryComponent
        if (playerInventory == null) {
            game.emitEvent(GameEvent.System("Inventory system not available.", GameEvent.MessageLevel.WARNING))
            return
        }
        val skillComponent = game.skillManager.getSkillComponent(game.worldState.player.id)
        applyCraftResult(game, craftingManager, skillComponent, playerInventory, recipe)
    }

    private fun resolveRecipe(
        game: EngineGameClient,
        craftingManager: CraftingManager,
        target: String
    ): Recipe? {
        val recipeResult = craftingManager.findRecipe(target)
        if (recipeResult.isFailure) {
            game.emitEvent(GameEvent.System(
                "Failed to find recipe: ${recipeResult.exceptionOrNull()?.message}",
                GameEvent.MessageLevel.WARNING
            ))
            return null
        }
        val recipe = recipeResult.getOrNull()
        if (recipe == null) {
            game.emitEvent(GameEvent.System(
                "No recipe found for '$target'.\nTip: Use 'craft' alone to see available recipes.",
                GameEvent.MessageLevel.WARNING
            ))
            return null
        }
        return recipe
    }

    private fun applyCraftResult(
        game: EngineGameClient,
        craftingManager: CraftingManager,
        skillComponent: SkillComponent,
        playerInventory: InventoryComponent,
        recipe: Recipe
    ) {
        when (val result = craftingManager.craft(skillComponent, playerInventory, recipe)) {
            is CraftingManager.CraftResult.Success ->
                ClientSkillQuestCraftResults.onSuccess(game, playerInventory, recipe, result)
            is CraftingManager.CraftResult.Failure ->
                ClientSkillQuestCraftResults.onFailure(game, playerInventory, recipe, result)
            is CraftingManager.CraftResult.Invalid ->
                game.emitEvent(GameEvent.System(result.message, GameEvent.MessageLevel.WARNING))
        }
    }
}
