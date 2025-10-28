package com.jcraw.mud.reasoning.crafting

import com.jcraw.mud.core.*
import com.jcraw.mud.core.crafting.Recipe
import com.jcraw.mud.core.repository.ItemRepository
import com.jcraw.mud.core.repository.RecipeRepository
import com.jcraw.mud.core.skills.Skill
import java.util.UUID
import kotlin.random.Random

/**
 * Manages crafting operations including recipe matching, skill checks, and item creation
 */
class CraftingManager(
    private val recipeRepository: RecipeRepository,
    private val itemRepository: ItemRepository,
    private val random: Random = Random.Default
) {
    /**
     * Result of a crafting attempt
     */
    sealed class CraftResult {
        data class Success(val craftedItem: ItemInstance, val message: String) : CraftResult()
        data class Failure(val message: String, val inputsLost: Map<String, Int>) : CraftResult()
        data class Invalid(val message: String) : CraftResult()
    }

    /**
     * Find a recipe by name or recipe ID
     */
    fun findRecipe(recipeName: String): Result<Recipe?> {
        // Try to find by exact ID first
        recipeRepository.getRecipe(recipeName).onSuccess { recipe ->
            return Result.success(recipe)
        }

        // Search all recipes by name (case-insensitive partial match)
        return recipeRepository.getAllRecipes().map { recipes ->
            recipes.find { it.name.equals(recipeName, ignoreCase = true) }
                ?: recipes.find { it.name.contains(recipeName, ignoreCase = true) }
        }
    }

    /**
     * Get all viable recipes for a player
     */
    fun getViableRecipes(player: Entity.Player): Result<List<Recipe>> {
        val inventory = player.components[ComponentType.INVENTORY] as? InventoryComponent
            ?: return Result.success(emptyList())

        // Get available items as map of template IDs to quantities
        val availableItems = inventory.items.groupBy { it.templateId }
            .mapValues { (_, instances) -> instances.sumOf { it.quantity } }

        // Get skills and check each skill for viable recipes
        val viableRecipes = mutableListOf<Recipe>()
        player.skills.forEach { (skillName, skill) ->
            recipeRepository.findViable(skillName, skill.level, availableItems)
                .onSuccess { recipes -> viableRecipes.addAll(recipes) }
        }

        return Result.success(viableRecipes.distinct())
    }

    /**
     * Attempt to craft an item using a recipe
     */
    fun craft(
        player: Entity.Player,
        recipe: Recipe
    ): CraftResult {
        val inventory = player.components[ComponentType.INVENTORY] as? InventoryComponent
            ?: return CraftResult.Invalid("You don't have an inventory")

        // Check skill level requirement
        val skill = player.skills[recipe.requiredSkill]
        if (skill == null || !recipe.meetsSkillRequirement(skill.level)) {
            return CraftResult.Invalid(
                "Your ${recipe.requiredSkill} level (${skill?.level ?: 0}) is too low. " +
                "Required: ${recipe.minSkillLevel}"
            )
        }

        // Check for required tools
        val availableToolTags = inventory.items
            .mapNotNull { instance ->
                itemRepository.findTemplateById(instance.templateId).getOrNull()?.tags
            }
            .flatten()
            .toSet()

        if (!recipe.hasRequiredTools(availableToolTags)) {
            return CraftResult.Invalid(
                "You need one of these tools: ${recipe.requiredTools.joinToString(", ")}"
            )
        }

        // Check for required input items
        val availableItems = inventory.items.groupBy { it.templateId }
            .mapValues { (_, instances) -> instances.sumOf { it.quantity } }

        val missingItems = mutableListOf<String>()
        recipe.inputItems.forEach { (templateId, requiredQty) ->
            val available = availableItems.getOrDefault(templateId, 0)
            if (available < requiredQty) {
                itemRepository.findTemplateById(templateId).onSuccess { template ->
                    missingItems.add("$requiredQty ${template?.name ?: templateId} (have $available)")
                }
            }
        }

        if (missingItems.isNotEmpty()) {
            return CraftResult.Invalid(
                "Missing materials: ${missingItems.joinToString(", ")}"
            )
        }

        // Perform skill check
        val skillCheck = performSkillCheck(skill, recipe.difficulty)

        return if (skillCheck.success) {
            // Success - consume inputs and create output
            val consumedInputs = consumeInputs(inventory, recipe.inputItems)
            val quality = calculateQuality(skill.level)
            val outputItem = createCraftedItem(recipe.outputItem, quality)

            CraftResult.Success(
                outputItem,
                "You successfully craft ${outputItem.templateId}! " +
                "Skill check: ${skillCheck.total} vs DC ${recipe.difficulty} (rolled ${skillCheck.roll})"
            )
        } else {
            // Failure - consume 50% of inputs
            val lostInputs = recipe.inputItems.mapValues { (_, qty) -> qty / 2 }
            consumeInputs(inventory, lostInputs)

            CraftResult.Failure(
                "You fail to craft ${recipe.name}. Some materials are wasted. " +
                "Skill check: ${skillCheck.total} vs DC ${recipe.difficulty} (rolled ${skillCheck.roll})",
                lostInputs
            )
        }
    }

    /**
     * Perform a skill check for crafting
     */
    private fun performSkillCheck(skill: Skill, dc: Int): SkillCheckResult {
        val roll = random.nextInt(1, 21) // d20
        val modifier = skill.getModifier()
        val total = roll + modifier
        return SkillCheckResult(
            roll = roll,
            modifier = modifier,
            total = total,
            success = total >= dc
        )
    }

    /**
     * Calculate output quality based on skill level
     * Quality ranges from 1-10, with higher skill producing better quality
     */
    private fun calculateQuality(skillLevel: Int): Int {
        return (skillLevel / 10).coerceIn(1, 10)
    }

    /**
     * Create a crafted item instance
     */
    private fun createCraftedItem(templateId: String, quality: Int): ItemInstance {
        // Get template to determine if it needs charges
        val template = itemRepository.findTemplateById(templateId).getOrNull()
        val charges = when (template?.type) {
            ItemType.CONSUMABLE -> template.properties["max_charges"]?.toIntOrNull() ?: 1
            ItemType.TOOL -> template.properties["max_charges"]?.toIntOrNull() ?: 10
            ItemType.SPELL_BOOK, ItemType.SKILL_BOOK -> 1
            else -> null
        }

        return ItemInstance(
            id = UUID.randomUUID().toString(),
            templateId = templateId,
            quality = quality,
            charges = charges,
            quantity = 1
        )
    }

    /**
     * Consume input items from inventory
     * @return Map of consumed item template IDs to quantities
     */
    private fun consumeInputs(
        inventory: InventoryComponent,
        inputs: Map<String, Int>
    ): Map<String, Int> {
        val consumed = mutableMapOf<String, Int>()

        inputs.forEach { (templateId, qty) ->
            var remaining = qty
            val itemsToRemove = mutableListOf<ItemInstance>()

            for (item in inventory.items) {
                if (item.templateId == templateId && remaining > 0) {
                    if (item.quantity <= remaining) {
                        // Remove entire stack
                        itemsToRemove.add(item)
                        remaining -= item.quantity
                        consumed[templateId] = consumed.getOrDefault(templateId, 0) + item.quantity
                    } else {
                        // Reduce stack
                        val reduced = item.reduceQuantity(remaining)
                        if (reduced != null) {
                            itemsToRemove.add(item)
                            // Will be replaced by reduced version
                        }
                        consumed[templateId] = consumed.getOrDefault(templateId, 0) + remaining
                        remaining = 0
                    }
                }
            }

            // This should update inventory (handled by caller)
        }

        return consumed
    }

    /**
     * Skill check result
     */
    private data class SkillCheckResult(
        val roll: Int,
        val modifier: Int,
        val total: Int,
        val success: Boolean
    )
}
