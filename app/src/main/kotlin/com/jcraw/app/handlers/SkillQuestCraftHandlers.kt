@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList")

package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.core.InventoryComponent
import com.jcraw.mud.core.SkillComponent
import com.jcraw.mud.core.crafting.Recipe
import com.jcraw.mud.reasoning.crafting.CraftingManager

/**
 * Craft orchestrator for [SkillQuestHandlers] facade.
 * Success/failure bodies live in [SkillQuestCraftResults].
 */
object SkillQuestCraftHandlers {

    fun handleCraft(game: MudGame, target: String) {
        val craftingManager = CraftingManager(game.recipeRepository, game.itemRepository)
        val recipe = resolveRecipe(craftingManager, target) ?: return
        printRecipeHeader(recipe)
        val skillComponent = game.skillManager.getSkillComponent(game.worldState.player.id)
        val inventoryComponent = game.worldState.player.inventoryComponent
        if (skillComponent == null) {
            println("You don't have skills to craft.")
            return
        }
        if (inventoryComponent == null) {
            println("You don't have an inventory to craft with.")
            return
        }
        applyCraftResult(game, craftingManager, skillComponent, inventoryComponent, recipe)
    }

    private fun resolveRecipe(craftingManager: CraftingManager, target: String): Recipe? {
        val recipeResult = craftingManager.findRecipe(target)
        if (recipeResult.isFailure) {
            println("Failed to find recipe: ${recipeResult.exceptionOrNull()?.message}")
            return null
        }
        val recipe = recipeResult.getOrNull()
        if (recipe == null) {
            println("No recipe found for '$target'.")
            println("Tip: Use 'craft' alone to see available recipes.")
            return null
        }
        return recipe
    }

    private fun printRecipeHeader(recipe: Recipe) {
        println("\nAttempting to craft: ${recipe.name}")
        println("Required skill: ${recipe.requiredSkill} (level ${recipe.minSkillLevel})")
        println("Difficulty: DC ${recipe.difficulty}")
        println()
    }

    private fun applyCraftResult(
        game: MudGame,
        craftingManager: CraftingManager,
        skillComponent: SkillComponent,
        inventoryComponent: InventoryComponent,
        recipe: Recipe
    ) {
        when (val result = craftingManager.craft(skillComponent, inventoryComponent, recipe)) {
            is CraftingManager.CraftResult.Success ->
                SkillQuestCraftResults.onSuccess(game, inventoryComponent, recipe, result)
            is CraftingManager.CraftResult.Failure ->
                SkillQuestCraftResults.onFailure(game, inventoryComponent, recipe, result)
            is CraftingManager.CraftResult.Invalid ->
                println("${result.message}")
        }
    }
}
