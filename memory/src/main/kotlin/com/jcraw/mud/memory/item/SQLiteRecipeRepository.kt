package com.jcraw.mud.memory.item

import com.jcraw.mud.core.crafting.Recipe
import com.jcraw.mud.core.repository.RecipeRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * SQLite implementation of RecipeRepository
 * Manages recipe persistence and queries
 */
class SQLiteRecipeRepository(
    private val database: ItemDatabase
) : RecipeRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun saveRecipe(recipe: Recipe): Result<Unit> {
        return try {
            val conn = database.getConnection()
            val sql = """
                INSERT OR REPLACE INTO recipes
                (id, name, input_items, output_item, required_skill, min_skill_level, required_tools, difficulty)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, recipe.id)
                stmt.setString(2, recipe.name)
                stmt.setString(3, json.encodeToString(recipe.inputItems))
                stmt.setString(4, recipe.outputItem)
                stmt.setString(5, recipe.requiredSkill)
                stmt.setInt(6, recipe.minSkillLevel)
                stmt.setString(7, json.encodeToString(recipe.requiredTools))
                stmt.setInt(8, recipe.difficulty)
                stmt.executeUpdate()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun saveRecipes(recipes: List<Recipe>): Result<Unit> {
        return try {
            val conn = database.getConnection()
            val sql = """
                INSERT OR REPLACE INTO recipes
                (id, name, input_items, output_item, required_skill, min_skill_level, required_tools, difficulty)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                for (recipe in recipes) {
                    stmt.setString(1, recipe.id)
                    stmt.setString(2, recipe.name)
                    stmt.setString(3, json.encodeToString(recipe.inputItems))
                    stmt.setString(4, recipe.outputItem)
                    stmt.setString(5, recipe.requiredSkill)
                    stmt.setInt(6, recipe.minSkillLevel)
                    stmt.setString(7, json.encodeToString(recipe.requiredTools))
                    stmt.setInt(8, recipe.difficulty)
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getRecipe(id: String): Result<Recipe> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM recipes WHERE id = ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, id)
                val rs = stmt.executeQuery()

                if (rs.next()) {
                    val recipe = Recipe(
                        id = rs.getString("id"),
                        name = rs.getString("name"),
                        inputItems = json.decodeFromString<Map<String, Int>>(rs.getString("input_items")),
                        outputItem = rs.getString("output_item"),
                        requiredSkill = rs.getString("required_skill"),
                        minSkillLevel = rs.getInt("min_skill_level"),
                        requiredTools = json.decodeFromString<List<String>>(rs.getString("required_tools")),
                        difficulty = rs.getInt("difficulty")
                    )
                    Result.success(recipe)
                } else {
                    Result.failure(NoSuchElementException("Recipe not found: $id"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getAllRecipes(): Result<List<Recipe>> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM recipes"

            conn.prepareStatement(sql).use { stmt ->
                val rs = stmt.executeQuery()
                val recipes = mutableListOf<Recipe>()

                while (rs.next()) {
                    val recipe = Recipe(
                        id = rs.getString("id"),
                        name = rs.getString("name"),
                        inputItems = json.decodeFromString<Map<String, Int>>(rs.getString("input_items")),
                        outputItem = rs.getString("output_item"),
                        requiredSkill = rs.getString("required_skill"),
                        minSkillLevel = rs.getInt("min_skill_level"),
                        requiredTools = json.decodeFromString<List<String>>(rs.getString("required_tools")),
                        difficulty = rs.getInt("difficulty")
                    )
                    recipes.add(recipe)
                }
                Result.success(recipes)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun findBySkill(skillName: String): Result<List<Recipe>> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM recipes WHERE required_skill = ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, skillName)
                val rs = stmt.executeQuery()
                val recipes = mutableListOf<Recipe>()

                while (rs.next()) {
                    val recipe = Recipe(
                        id = rs.getString("id"),
                        name = rs.getString("name"),
                        inputItems = json.decodeFromString<Map<String, Int>>(rs.getString("input_items")),
                        outputItem = rs.getString("output_item"),
                        requiredSkill = rs.getString("required_skill"),
                        minSkillLevel = rs.getInt("min_skill_level"),
                        requiredTools = json.decodeFromString<List<String>>(rs.getString("required_tools")),
                        difficulty = rs.getInt("difficulty")
                    )
                    recipes.add(recipe)
                }
                Result.success(recipes)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun findViable(
        skillName: String,
        skillLevel: Int,
        availableItems: Map<String, Int>
    ): Result<List<Recipe>> {
        return try {
            val conn = database.getConnection()
            val sql = """
                SELECT * FROM recipes
                WHERE required_skill = ? AND min_skill_level <= ?
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, skillName)
                stmt.setInt(2, skillLevel)
                val rs = stmt.executeQuery()
                val recipes = mutableListOf<Recipe>()

                while (rs.next()) {
                    val recipe = Recipe(
                        id = rs.getString("id"),
                        name = rs.getString("name"),
                        inputItems = json.decodeFromString<Map<String, Int>>(rs.getString("input_items")),
                        outputItem = rs.getString("output_item"),
                        requiredSkill = rs.getString("required_skill"),
                        minSkillLevel = rs.getInt("min_skill_level"),
                        requiredTools = json.decodeFromString<List<String>>(rs.getString("required_tools")),
                        difficulty = rs.getInt("difficulty")
                    )

                    // Filter by available items
                    val hasAllInputs = recipe.inputItems.all { (itemId, requiredQty) ->
                        availableItems.getOrDefault(itemId, 0) >= requiredQty
                    }

                    if (hasAllInputs) {
                        recipes.add(recipe)
                    }
                }
                Result.success(recipes)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
