package com.jcraw.mud.core.repository

import com.jcraw.mud.core.crafting.Recipe

/**
 * Repository interface for recipe persistence and queries
 */
interface RecipeRepository {
    /**
     * Save a recipe to the database
     * @return Result indicating success or failure
     */
    fun saveRecipe(recipe: Recipe): Result<Unit>

    /**
     * Save multiple recipes in a batch operation
     * @return Result indicating success or failure
     */
    fun saveRecipes(recipes: List<Recipe>): Result<Unit>

    /**
     * Get a recipe by its ID
     * @return Result with the recipe if found, or failure
     */
    fun getRecipe(id: String): Result<Recipe>

    /**
     * Get all recipes in the database
     * @return Result with list of all recipes
     */
    fun getAllRecipes(): Result<List<Recipe>>

    /**
     * Find recipes by required skill
     * @param skillName The skill name to filter by
     * @return Result with list of matching recipes
     */
    fun findBySkill(skillName: String): Result<List<Recipe>>

    /**
     * Find recipes that the player can potentially craft
     * @param skillName The player's skill name
     * @param skillLevel The player's skill level
     * @param availableItems Map of template IDs to available quantities
     * @return Result with list of viable recipes
     */
    fun findViable(
        skillName: String,
        skillLevel: Int,
        availableItems: Map<String, Int>
    ): Result<List<Recipe>>
}
