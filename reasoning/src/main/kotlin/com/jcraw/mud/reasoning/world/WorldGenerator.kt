package com.jcraw.mud.reasoning.world

import com.jcraw.mud.core.*
import com.jcraw.mud.core.world.*
import com.jcraw.sophia.llm.LLMClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random

/**
 * Primary world generation engine.
 *
 * Handles LLM-driven chunk and space generation with lore inheritance and theme coherence.
 * Uses JSON-structured prompts for consistent, parseable output.
 */
class WorldGenerator(
    private val llmClient: LLMClient,
    private val loreEngine: LoreInheritanceEngine
) {
    companion object {
        private const val MODEL = "gpt-4o-mini"
        private const val TEMPERATURE = 0.7
        private const val MAX_TOKENS = 600

        private const val TRAP_PROBABILITY = 0.15 // 15% chance per space
        private const val RESOURCE_PROBABILITY = 0.05 // 5% chance per space
        private const val HIDDEN_EXIT_PROBABILITY = 0.20 // 20% of exits are hidden
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Generates a world chunk (WORLD, REGION, ZONE, or SUBZONE level).
     *
     * @param context Generation context with seed, lore, parent chunk
     * @return Result with (WorldChunkComponent, chunkId) pair
     */
    suspend fun generateChunk(context: GenerationContext): Result<Pair<WorldChunkComponent, String>> {
        val chunkId = ChunkIdGenerator.generate(context.level, context.parentChunkId)

        // Generate lore variation from parent
        val parentChunk = context.parentChunk
        val lore = if (parentChunk != null) {
            loreEngine.varyLore(
                parentChunk.lore,
                context.level,
                context.direction
            ).getOrElse { return Result.failure(it) }
        } else {
            context.globalLore
        }

        // Generate chunk details via LLM
        val chunkData = generateChunkData(context, lore).getOrElse { return Result.failure(it) }

        val chunk = WorldChunkComponent(
            level = context.level,
            parentId = context.parentChunkId,
            children = emptyList(),
            lore = lore,
            biomeTheme = chunkData.biomeTheme,
            sizeEstimate = chunkData.sizeEstimate,
            mobDensity = chunkData.mobDensity.coerceIn(0.0, 1.0),
            difficultyLevel = chunkData.difficultyLevel.coerceIn(1, 20)
        )

        return Result.success(chunk to chunkId)
    }

    /**
     * Generates a space (room) within a subzone.
     *
     * @param parentSubzone Parent subzone chunk
     * @param parentSubzoneId Entity ID of parent subzone
     * @return Result with (SpacePropertiesComponent, spaceId) pair
     */
    suspend fun generateSpace(
        parentSubzone: WorldChunkComponent,
        parentSubzoneId: String
    ): Result<Pair<SpacePropertiesComponent, String>> {
        val spaceId = ChunkIdGenerator.generate(ChunkLevel.SPACE, parentSubzoneId)

        // Generate space details via LLM
        val spaceData = generateSpaceData(parentSubzone).getOrElse { return Result.failure(it) }

        // Generate traps
        val traps = if (Random.nextDouble() < TRAP_PROBABILITY) {
            listOf(generateTrap(parentSubzone.biomeTheme, parentSubzone.difficultyLevel))
        } else {
            emptyList()
        }

        // Generate resources
        val resources = if (Random.nextDouble() < RESOURCE_PROBABILITY) {
            listOf(generateResource(parentSubzone.biomeTheme))
        } else {
            emptyList()
        }

        // Convert ExitDataJson to ExitData and add hidden exits
        val exits = spaceData.exits.map { exitJson ->
            if (Random.nextDouble() < HIDDEN_EXIT_PROBABILITY) {
                ExitData(
                    targetId = exitJson.targetId,
                    direction = exitJson.direction,
                    description = exitJson.description,
                    conditions = listOf(
                        Condition.SkillCheck(
                            skill = "Perception",
                            difficulty = 10 + parentSubzone.difficultyLevel
                        )
                    ),
                    isHidden = true
                )
            } else {
                ExitData(
                    targetId = exitJson.targetId,
                    direction = exitJson.direction,
                    description = exitJson.description
                )
            }
        }

        val space = SpacePropertiesComponent(
            description = spaceData.description,
            exits = exits,
            brightness = spaceData.brightness.coerceIn(0, 100),
            terrainType = TerrainType.valueOf(spaceData.terrainType),
            traps = traps,
            resources = resources,
            entities = emptyList(), // Populated later via MobSpawner
            itemsDropped = emptyList(),
            stateFlags = emptyMap()
        )

        return Result.success(space to spaceId)
    }

    /**
     * Generates theme-appropriate trap.
     */
    fun generateTrap(theme: String, difficultyLevel: Int): TrapData {
        val trapType = when {
            "forest" in theme.lowercase() -> listOf("bear trap", "pit trap", "snare").random()
            "cave" in theme.lowercase() || "magma" in theme.lowercase() -> listOf("lava pool", "collapsing floor", "gas vent").random()
            "crypt" in theme.lowercase() || "tomb" in theme.lowercase() -> listOf("poison dart", "cursed rune", "arrow trap").random()
            "castle" in theme.lowercase() || "fortress" in theme.lowercase() -> listOf("spike trap", "swinging blade", "falling portcullis").random()
            else -> listOf("pit trap", "spike trap", "poison dart").random()
        }

        val id = "trap_${java.util.UUID.randomUUID().toString().take(8)}"
        val difficulty = (10 + difficultyLevel + Random.nextInt(-2, 3)).coerceIn(5, 25)

        return TrapData(
            id = id,
            type = trapType,
            difficulty = difficulty,
            triggered = false,
            description = "A $trapType lurks here"
        )
    }

    /**
     * Generates theme-appropriate resource node.
     */
    fun generateResource(theme: String): ResourceNode {
        val resourceType = when {
            "forest" in theme.lowercase() -> listOf("wood", "herbs", "berries").random()
            "cave" in theme.lowercase() -> listOf("iron ore", "coal", "crystal").random()
            "magma" in theme.lowercase() -> listOf("obsidian", "sulfur", "fire crystal").random()
            "crypt" in theme.lowercase() -> listOf("bone", "arcane dust", "ancient cloth").random()
            else -> listOf("stone", "wood", "herbs").random()
        }

        val id = "resource_${java.util.UUID.randomUUID().toString().take(8)}"
        val templateId = resourceType.replace(" ", "_").lowercase()

        return ResourceNode(
            id = id,
            templateId = templateId,
            quantity = Random.nextInt(1, 6),
            respawnTime = if (Random.nextBoolean()) 100 + Random.nextInt(50) else null
        )
    }

    // Private helper methods

    private suspend fun generateChunkData(context: GenerationContext, lore: String): Result<ChunkData> {
        val systemPrompt = """
            You are a world-building assistant for a fantasy dungeon MUD.
            Generate chunk data in JSON format only. No additional text.
        """.trimIndent()

        val sizeRange = when (context.level) {
            ChunkLevel.WORLD -> "global"
            ChunkLevel.REGION -> "1000-10000"
            ChunkLevel.ZONE -> "100-500"
            ChunkLevel.SUBZONE -> "5-100"
            ChunkLevel.SPACE -> "1"
        }

        val userContext = """
            Level: ${context.level.name}
            Parent lore: ${context.parentChunk?.lore ?: "None (root level)"}
            Generated lore: $lore
            Size range: $sizeRange spaces
            Direction: ${context.direction ?: "N/A"}

            Generate chunk details matching this lore and level.

            Output JSON only:
            {
              "biomeTheme": "2-4 word theme matching lore",
              "sizeEstimate": ${if (context.level == ChunkLevel.SPACE) 1 else "number in range $sizeRange"},
              "mobDensity": "0.0-1.0 (0=empty, 1=packed)",
              "difficultyLevel": "1-20 (scales with depth)"
            }
        """.trimIndent()

        return try {
            val response = llmClient.chatCompletion(
                modelId = MODEL,
                systemPrompt = systemPrompt,
                userContext = userContext,
                maxTokens = 200,
                temperature = TEMPERATURE
            )

            val content = response.choices.firstOrNull()?.message?.content?.trim()
                ?: return Result.failure(Exception("LLM returned empty response"))

            // Strip markdown code blocks if present
            val jsonContent = content
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val chunkData = json.decodeFromString<ChunkData>(jsonContent)
            Result.success(chunkData)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to generate chunk data: ${e.message}", e))
        }
    }

    private suspend fun generateSpaceData(parentSubzone: WorldChunkComponent): Result<SpaceData> {
        val systemPrompt = """
            You are a world-building assistant for a fantasy dungeon MUD.
            Generate space (room) data in JSON format only. No additional text.
        """.trimIndent()

        val userContext = """
            Theme: ${parentSubzone.biomeTheme}
            Lore: ${parentSubzone.lore}
            Difficulty: ${parentSubzone.difficultyLevel}

            Generate a room/space with:
            - Vivid description (2-3 sentences)
            - 3-6 exits (mix cardinal directions like "north", "south" and descriptive like "climb ladder", "through archway")
            - Brightness (0=pitch black, 50=dim, 100=bright)
            - Terrain type (NORMAL, DIFFICULT, or IMPASSABLE)

            Output JSON only:
            {
              "description": "atmospheric room description",
              "exits": [
                {"direction": "north", "description": "dark passage", "targetId": "PLACEHOLDER"},
                {"direction": "climb ladder", "description": "rusty iron ladder leading up", "targetId": "PLACEHOLDER"}
              ],
              "brightness": 50,
              "terrainType": "NORMAL"
            }
        """.trimIndent()

        return try {
            val response = llmClient.chatCompletion(
                modelId = MODEL,
                systemPrompt = systemPrompt,
                userContext = userContext,
                maxTokens = MAX_TOKENS,
                temperature = TEMPERATURE
            )

            val content = response.choices.firstOrNull()?.message?.content?.trim()
                ?: return Result.failure(Exception("LLM returned empty response"))

            // Strip markdown code blocks if present
            val jsonContent = content
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val spaceData = json.decodeFromString<SpaceData>(jsonContent)
            Result.success(spaceData)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to generate space data: ${e.message}", e))
        }
    }
}

// JSON response data classes
@Serializable
private data class ChunkData(
    val biomeTheme: String,
    val sizeEstimate: Int,
    val mobDensity: Double,
    val difficultyLevel: Int
)

@Serializable
private data class SpaceData(
    val description: String,
    val exits: List<ExitDataJson>,
    val brightness: Int,
    val terrainType: String
)

@Serializable
private data class ExitDataJson(
    val direction: String,
    val description: String,
    val targetId: String
)
