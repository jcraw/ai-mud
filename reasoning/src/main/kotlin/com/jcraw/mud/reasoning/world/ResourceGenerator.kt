package com.jcraw.mud.reasoning.world

import com.jcraw.mud.core.repository.ItemRepository
import com.jcraw.mud.core.world.ResourceNode
import com.jcraw.mud.llm.LLMService
import java.util.UUID
import kotlin.random.Random

/**
 * Generates resource nodes based on theme and difficulty.
 * Ties resources to ItemTemplate system for gathering integration.
 */
class ResourceGenerator(
    private val itemRepository: ItemRepository,
    private val llmService: LLMService? = null
) {
    // Resource name to template ID mapping (flexible for future templates)
    private val resourceTemplateMap = mapOf(
        "wood" to "wood",
        "herbs" to "healing_herb",
        "mushroom" to "mushroom",
        "berries" to "berries",
        "obsidian" to "obsidian",
        "sulfur" to "sulfur",
        "magma crystal" to "magma_crystal",
        "fire ore" to "fire_ore",
        "bone" to "bone",
        "arcane dust" to "arcane_dust",
        "ancient coin" to "gold_coin",
        "grave flower" to "grave_flower",
        "ice shard" to "ice_shard",
        "frost herb" to "frost_herb",
        "frozen timber" to "frozen_wood",
        "glacier stone" to "glacier_stone",
        "iron scrap" to "iron_ore",
        "old tapestry" to "tapestry",
        "rusted armor" to "scrap_metal",
        "ancient tome" to "ancient_book",
        "swamp weed" to "swamp_weed",
        "leech" to "leech",
        "murky water" to "murky_water",
        "peat" to "peat",
        "sand glass" to "sand_glass",
        "desert herb" to "desert_herb",
        "ancient pottery" to "pottery_shard",
        "dried wood" to "dried_wood",
        "cave fish" to "cave_fish",
        "luminous algae" to "luminous_algae",
        "water crystal" to "water_crystal",
        "wet stone" to "wet_stone"
    )

    /**
     * Generate a resource node for the given theme and difficulty level.
     * Selects resource from ThemeRegistry and scales quantity with difficulty.
     */
    fun generate(theme: String, difficulty: Int): ResourceNode {
        val profile = ThemeRegistry.getProfileSemantic(theme)
            ?: ThemeRegistry.getDefaultProfile()

        // Select random resource from profile
        val resourceName = profile.resources.random()

        // Map to template ID (fallback to resource name if not in map)
        val templateId = resourceTemplateMap[resourceName] ?: resourceName.replace(" ", "_").lowercase()

        // Validate template exists (optional, depends on item system state)
        // For now, proceed with templateId regardless

        // Calculate quantity based on difficulty (harder areas = more yield)
        val baseQuantity = Random.nextInt(1, 5)
        val difficultyBonus = (difficulty / 5).coerceAtMost(3)
        val quantity = baseQuantity + difficultyBonus

        // Determine respawn time (deep dungeons have renewable resources)
        val respawnTime = if (difficulty < 5) {
            null // Low difficulty areas: no respawn (finite)
        } else {
            100 + difficulty * 10 // Higher difficulty: longer respawn
        }

        // Generate unique ID
        val nodeId = "resource_${UUID.randomUUID()}"

        // Generate description (use LLM if available, fallback to simple description)
        val description = if (llmService != null) {
            generateNodeDescription(resourceName, theme)
        } else {
            "A patch of $resourceName in the $theme."
        }

        return ResourceNode(
            id = nodeId,
            templateId = templateId,
            quantity = quantity,
            respawnTime = respawnTime,
            description = description,
            timeSinceHarvest = 0
        )
    }

    /**
     * Generate vivid resource node description using LLM.
     * Creates 1 sentence description for immersion.
     */
    fun generateNodeDescription(resourceName: String, theme: String): String {
        if (llmService == null) {
            return "A patch of $resourceName in the $theme."
        }

        val prompt = """
            Describe a $resourceName resource node in a $theme setting.
            Be vivid but concise (1 sentence).
            Focus on visual details and harvestability.
        """.trimIndent()

        return try {
            val response = llmService.complete(
                prompt = prompt,
                model = "gpt-4o-mini",
                temperature = 0.7
            )
            response.trim()
        } catch (e: Exception) {
            // Fallback on LLM failure
            "A patch of $resourceName in the $theme."
        }
    }

    /**
     * Generate resources for a space.
     * Probability-based generation (~5% base chance per call).
     */
    fun generateResourcesForSpace(
        theme: String,
        difficulty: Int,
        resourceProbability: Double = 0.05
    ): List<ResourceNode> {
        val resources = mutableListOf<ResourceNode>()

        // Roll for resource generation
        if (Random.nextDouble() < resourceProbability) {
            resources.add(generate(theme, difficulty))

            // Small chance for second resource in rich areas (difficulty > 8)
            if (difficulty > 8 && Random.nextDouble() < 0.03) {
                resources.add(generate(theme, difficulty))
            }
        }

        return resources
    }
}
