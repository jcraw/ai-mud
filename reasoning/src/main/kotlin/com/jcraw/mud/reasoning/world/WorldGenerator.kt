package com.jcraw.mud.reasoning.world

import com.jcraw.mud.core.*
import com.jcraw.mud.core.world.*
import com.jcraw.mud.memory.MemoryManager
import com.jcraw.mud.reasoning.worldgen.GraphGenerator
import com.jcraw.mud.reasoning.worldgen.GraphValidator
import com.jcraw.mud.reasoning.worldgen.GraphLayout
import com.jcraw.mud.reasoning.worldgen.ValidationResult
import com.jcraw.sophia.llm.LLMClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random

/**
 * Primary world generation engine.
 *
 * V3 UPDATE: Now generates graph topology at SUBZONE level before content generation.
 * Handles LLM-driven chunk and space generation with lore inheritance and theme coherence.
 * Uses JSON-structured prompts for consistent, parseable output.
 */
class WorldGenerator(
    private val llmClient: LLMClient,
    private val loreEngine: LoreInheritanceEngine,
    private val graphGenerator: GraphGenerator? = null,
    private val graphValidator: GraphValidator? = null,
    private val memoryManager: MemoryManager? = null
) {
    companion object {
        private const val MODEL = "gpt-4o-mini"
        private const val TEMPERATURE = 0.7
        private const val MAX_TOKENS = 600
        private const val ROOT_BIOME = "abyssal_dungeon"

        private const val TRAP_PROBABILITY = 0.15 // 15% chance per space
        private const val RESOURCE_PROBABILITY = 0.05 // 5% chance per space
        private const val HIDDEN_EXIT_PROBABILITY = 0.20 // 20% of exits are hidden
    }

    private val json = Json { ignoreUnknownKeys = true }

    // Track generated space names per chunk to avoid duplicates
    private val generatedNamesPerChunk = mutableMapOf<String, MutableSet<String>>()

    /**
     * Generates a world chunk (WORLD, REGION, ZONE, or SUBZONE level).
     *
     * V3 UPDATE: At SUBZONE level, generates graph topology before content.
     * Graph nodes are returned for caller to persist to GraphNodeRepository.
     *
     * @param context Generation context with seed, lore, parent chunk
     * @return Result with ChunkGenerationResult (chunk, chunkId, graphNodes)
     */
    suspend fun generateChunk(context: GenerationContext): Result<ChunkGenerationResult> {
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

        val resolvedBiomeTheme = resolveBiomeTheme(context, chunkData.biomeTheme)

        var chunk = WorldChunkComponent(
            level = context.level,
            parentId = context.parentChunkId,
            children = emptyList(),
            lore = lore,
            biomeTheme = resolvedBiomeTheme,
            sizeEstimate = chunkData.sizeEstimate,
            mobDensity = chunkData.mobDensity.coerceIn(0.0, 1.0),
            difficultyLevel = chunkData.difficultyLevel.coerceIn(1, 20)
        )

        // V3: Generate graph topology at SUBZONE level
        val graphNodes = if (context.level == ChunkLevel.SUBZONE && graphGenerator != null && graphValidator != null) {
            generateGraphTopology(chunkId, chunk).getOrElse { return Result.failure(it) }
        } else {
            emptyList()
        }

        chunk = enforceRootBiome(chunkId, chunk, context)

        context.parentChunk?.let { parent ->
            logPromptCascade(chunkId, chunk, parent)
        }

        cacheChunkLoreEntry(chunkId, chunk)

        return Result.success(ChunkGenerationResult(chunk, chunkId, graphNodes))
    }

    /**
     * Generates graph topology for a SUBZONE chunk.
     * Uses GraphGenerator with layout based on biome theme.
     * Validates graph before returning.
     *
     * @param chunkId ID of the chunk
     * @param chunk The chunk component
     * @return Result with list of GraphNodeComponents
     */
    private fun generateGraphTopology(
        chunkId: String,
        chunk: WorldChunkComponent
    ): Result<List<GraphNodeComponent>> {
        require(graphGenerator != null) { "GraphGenerator required for V3 generation" }
        require(graphValidator != null) { "GraphValidator required for V3 generation" }

        // Select layout algorithm based on biome theme
        val layout = GraphLayout.forBiome(chunk.biomeTheme)

        // Generate graph with seeded RNG for reproducibility
        val seed = chunkId.hashCode().toLong()
        val rng = Random(seed)
        val generator = GraphGenerator(rng, chunk.difficultyLevel)

        // Generate graph topology
        val graphNodes = try {
            generator.generate(chunkId, layout)
        } catch (e: Exception) {
            return Result.failure(Exception("Graph generation failed for chunk $chunkId: ${e.message}", e))
        }

        // Validate graph structure
        val validation = graphValidator.validate(graphNodes)
        if (validation is ValidationResult.Failure) {
            return Result.failure(
                Exception("Graph validation failed for chunk $chunkId: ${validation.reasons.joinToString(", ")}")
            )
        }

        return Result.success(graphNodes)
    }

    /**
     * Generates a space (room) within a subzone.
     *
     * V2 METHOD: Generates full space content immediately via LLM.
     * For V3 graph-based generation, use generateSpaceStub() instead.
     *
     * @param parentSubzone Parent subzone chunk
     * @param parentSubzoneId Entity ID of parent subzone
     * @return Result with (SpacePropertiesComponent, spaceId) pair
     */
    suspend fun generateSpace(
        parentSubzone: WorldChunkComponent,
        parentSubzoneId: String,
        directionHint: String? = null
    ): Result<Pair<SpacePropertiesComponent, String>> {
        val spaceId = ChunkIdGenerator.generate(ChunkLevel.SPACE, parentSubzoneId)

        // Generate space details via LLM
        val spaceData = generateSpaceData(parentSubzone, directionHint).getOrElse { return Result.failure(it) }

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
            terrainType = parseTerrainType(spaceData.terrainType),
            traps = traps,
            resources = resources,
            entities = emptyList(), // Populated later via MobSpawner
            itemsDropped = emptyList(),
            stateFlags = emptyMap()
        )

        cacheSpaceDescription(spaceId, space.name, space.description, parentSubzone)

        return Result.success(space to spaceId)
    }

    /**
     * V3: Generates a space stub with empty description for lazy-fill.
     * Description will be filled on-demand when player enters via fillSpaceContent().
     * Exits come from GraphNodeComponent neighbors instead of LLM generation.
     *
     * @param graphNode The graph node defining connectivity
     * @param chunk The parent chunk for theme/difficulty
     * @return Result with SpacePropertiesComponent stub
     */
    fun generateSpaceStub(
        graphNode: GraphNodeComponent,
        chunk: WorldChunkComponent
    ): Result<SpacePropertiesComponent> {
        // Convert GraphNodeComponent edges to ExitData
        val exits = graphNode.neighbors.map { edge ->
            ExitData(
                targetId = edge.targetId,
                direction = edge.direction,
                description = "", // Lazy-fill
                conditions = edge.conditions,
                isHidden = edge.hidden
            )
        }

        // Probabilistic trap/resource generation (same as V2)
        val traps = if (Random.nextDouble() < TRAP_PROBABILITY) {
            listOf(generateTrap(chunk.biomeTheme, chunk.difficultyLevel))
        } else {
            emptyList()
        }

        val resources = if (Random.nextDouble() < RESOURCE_PROBABILITY) {
            listOf(generateResource(chunk.biomeTheme))
        } else {
            emptyList()
        }

        val space = SpacePropertiesComponent(
            description = "", // LAZY-FILL: Will be generated on first visit
            exits = exits,
            brightness = 50, // Default brightness, can be adjusted by node type
            terrainType = TerrainType.NORMAL,
            traps = traps,
            resources = resources,
            entities = emptyList(), // Populated later via MobSpawner
            itemsDropped = emptyList(),
            stateFlags = emptyMap()
        )

        return Result.success(space)
    }

    /**
     * V3: Fills space content on-demand (lazy-fill).
     * Generates name and description, updates brightness/terrain based on node type.
     * Called when player enters a space for the first time.
     *
     * @param currentSpace The space stub to fill
     * @param graphNode The graph node defining structure
     * @param chunk The parent chunk for theme/lore
     * @return Result with updated SpacePropertiesComponent
     */
    suspend fun fillSpaceContent(
        currentSpace: SpacePropertiesComponent,
        graphNode: GraphNodeComponent,
        chunk: WorldChunkComponent
    ): Result<SpacePropertiesComponent> {
        // Skip if already filled
        if (currentSpace.description.isNotEmpty()) {
            return Result.success(currentSpace)
        }

        // Generate name and description using LLM based on node type and neighbors
        val (name, description) = generateNodeNameAndDescription(graphNode, chunk, graphNode.chunkId).getOrElse { return Result.failure(it) }

        // Track generated name to avoid duplicates
        generatedNamesPerChunk.getOrPut(graphNode.chunkId) { mutableSetOf() }.add(name)

        // Determine brightness and terrain based on node type
        val (brightness, terrain) = determineNodeProperties(graphNode.type, chunk)

        val filled = currentSpace.copy(
            name = name,
            description = description,
            brightness = brightness,
            terrainType = terrain
        )

        cacheSpaceDescription(graphNode.id, filled.name, filled.description, chunk)

        return Result.success(filled)
    }

    /**
     * V3: Generate name and description for a graph node.
     * Uses node type, neighbors, and chunk lore to create contextual content.
     * Avoids duplicate names by checking previously generated names in the chunk.
     * Returns Pair<name, description>
     */
    private suspend fun generateNodeNameAndDescription(
        node: GraphNodeComponent,
        chunk: WorldChunkComponent,
        chunkId: String
    ): Result<Pair<String, String>> {
        val nodeTypeDescription = when (node.type) {
            is com.jcraw.mud.core.world.NodeType.Hub -> "safe zone or gathering point"
            is com.jcraw.mud.core.world.NodeType.Linear -> "corridor or passage"
            is com.jcraw.mud.core.world.NodeType.Branching -> "junction or crossroads"
            is com.jcraw.mud.core.world.NodeType.DeadEnd -> "dead-end chamber"
            is com.jcraw.mud.core.world.NodeType.Boss -> "ominous boss chamber"
            is com.jcraw.mud.core.world.NodeType.Frontier -> "unexplored frontier"
            is com.jcraw.mud.core.world.NodeType.Questable -> "significant quest location"
        }

        val visibleExitDirections = node.neighbors.filterNot { it.hidden }.map { it.direction }
        val exitDirections = if (visibleExitDirections.isEmpty()) {
            "None (visible paths are concealed or require discovery)"
        } else {
            visibleExitDirections.joinToString(", ")
        }
        val hiddenHint = if (node.neighbors.any { it.hidden }) {
            "\nHidden exits: ${node.neighbors.count { it.hidden }} (hint at secrets without naming directions)"
        } else {
            ""
        }

        // Get existing names in this chunk to avoid duplicates
        val existingNames = generatedNamesPerChunk[chunkId] ?: emptySet()
        val existingNamesContext = if (existingNames.isNotEmpty()) {
            "\nExisting space names in this area (avoid duplicates): ${existingNames.joinToString(", ")}"
        } else {
            ""
        }

        val systemPrompt = """
            You are a world-building assistant for a fantasy dungeon MUD.
            Generate atmospheric space names and descriptions in JSON format only.
        """.trimIndent()

        val userContext = """
            Theme: ${chunk.biomeTheme}
            Lore: ${chunk.lore}
            Node Type: $nodeTypeDescription
            Exits: $exitDirections$hiddenHint$existingNamesContext

            Generate a vivid name and description for this space.
            Name should be 2-4 words, evocative and thematic (e.g., "Treacherous Alley", "Crystal Cavern").
            IMPORTANT: The name must be unique and different from any existing names listed above.
            Description should be 2-3 sentences reflecting the node type and available exits.
            Keep exit mentions directional and atmospheric; do not invent specific destinations (towns, villages, etc.) unless explicitly referenced in the lore or exit list.

            Output JSON only:
            {
              "name": "space name",
              "description": "vivid 2-3 sentence description"
            }
        """.trimIndent()

        return try {
            val response = llmClient.chatCompletion(
                modelId = MODEL,
                systemPrompt = systemPrompt,
                userContext = userContext,
                maxTokens = 250,
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

            @Serializable
            data class SpaceNameAndDescription(val name: String, val description: String)

            val data = json.decodeFromString<SpaceNameAndDescription>(jsonContent)
            Result.success(data.name to data.description)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to generate node name and description: ${e.message}", e))
        }
    }

    /**
     * V3: Determine brightness and terrain based on node type.
     * Heuristic defaults that match node purpose.
     */
    private fun determineNodeProperties(
        nodeType: com.jcraw.mud.core.world.NodeType,
        chunk: WorldChunkComponent
    ): Pair<Int, TerrainType> {
        return when (nodeType) {
            is com.jcraw.mud.core.world.NodeType.Hub -> 70 to TerrainType.NORMAL // Well-lit, safe
            is com.jcraw.mud.core.world.NodeType.Linear -> 40 to TerrainType.NORMAL // Dim passages
            is com.jcraw.mud.core.world.NodeType.Branching -> 50 to TerrainType.NORMAL // Moderate light
            is com.jcraw.mud.core.world.NodeType.DeadEnd -> 30 to TerrainType.DIFFICULT // Dark, challenging
            is com.jcraw.mud.core.world.NodeType.Boss -> 60 to TerrainType.NORMAL // Dramatic lighting
            is com.jcraw.mud.core.world.NodeType.Frontier -> 20 to TerrainType.DIFFICULT // Unexplored, rough
            is com.jcraw.mud.core.world.NodeType.Questable -> 55 to TerrainType.NORMAL // Interesting, accessible
        }
    }

    private fun parseTerrainType(raw: String): TerrainType {
        val normalized = raw.trim().uppercase()
        return runCatching { TerrainType.valueOf(normalized) }
            .getOrElse {
                println("[WARN] Unknown terrain type '$raw', defaulting to NORMAL")
                TerrainType.NORMAL
            }
    }

    /**
     * Determine final biome theme for the child chunk.
     * Defaults to parent's biome unless a surface breakout flag is present.
     */
    private fun resolveBiomeTheme(
        context: GenerationContext,
        requestedTheme: String
    ): String {
        val parent = context.parentChunk ?: return requestedTheme.ifBlank { ROOT_BIOME }
        val parentTheme = parent.biomeTheme.ifBlank { ROOT_BIOME }
        val breakout = hasSurfaceShift(context)

        val newTheme = when {
            breakout -> "surface_wilderness"
            parentTheme.isNotBlank() -> parentTheme
            requestedTheme.isNotBlank() -> requestedTheme
            else -> ROOT_BIOME
        }

        val parentId = context.parentChunkId ?: "UNKNOWN_PARENT"
        println("[INHERIT] Child ${context.level.name} from parent $parentId (biome='${parentTheme}'): Setting to '$newTheme'")

        return newTheme
    }

    private fun hasSurfaceShift(context: GenerationContext): Boolean {
        val hint = context.direction?.lowercase() ?: return false
        return hint.contains("surface_shift")
    }

    private fun enforceRootBiome(
        chunkId: String,
        chunk: WorldChunkComponent,
        context: GenerationContext
    ): WorldChunkComponent {
        if (chunk.level != ChunkLevel.WORLD || context.parentChunk != null) {
            return chunk
        }

        val adjusted = chunk.copy(biomeTheme = ROOT_BIOME)
        val overrideNote = if (chunk.biomeTheme != ROOT_BIOME) " (overrode '${chunk.biomeTheme}')" else ""
        println("[GEN] Root WORLD chunk ID=$chunkId: biomeTheme='${adjusted.biomeTheme}', parent=null$overrideNote")
        return adjusted
    }

    private fun logPromptCascade(
        chunkId: String,
        chunk: WorldChunkComponent,
        parentChunk: WorldChunkComponent
    ) {
        val parentTheme = parentChunk.biomeTheme.ifBlank { ROOT_BIOME }
        println("[PROMPT] For chunk $chunkId (${chunk.level}): biome='${chunk.biomeTheme}', inheriting from '$parentTheme'")
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
        val parentLore = context.parentChunk?.lore ?: "None (root level)"
        val parentTheme = context.parentChunk?.biomeTheme?.takeIf { it.isNotBlank() } ?: "None (root level)"
        val inheritanceDirective = if (context.parentChunk != null) {
            "Inherit from parent: $parentTheme (${parentLore.take(200)}) but vary toward deeper grit, claustrophobia, and abyssal descent."
        } else {
            "Inherit from parent: None (root level) — establish the primordial abyssal tone."
        }
        val dungeonConstraints =
            "Strictly underground abyssal dungeon—enclosed stone/cavern motifs only; no surface elements like trees, sky, or foliage unless explicitly magical anomalies. Emphasize vertical descent, increasing darkness/peril with depth."

        val userContext = """
            Seed: ${context.seed}
            Level: ${context.level.name}
            Parent lore: $parentLore
            Parent theme: $parentTheme
            $inheritanceDirective
            Generated lore: $lore
            Size range: $sizeRange spaces
            Direction: ${context.direction ?: "N/A"}

            $dungeonConstraints

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

    private suspend fun generateSpaceData(
        parentSubzone: WorldChunkComponent,
        directionHint: String?
    ): Result<SpaceData> {
        val systemPrompt = """
            You are a world-building assistant for a fantasy dungeon MUD.
            Generate space (room) data in JSON format only. No additional text.
        """.trimIndent()

        val userContext = """
            Theme: ${parentSubzone.biomeTheme}
            Lore: ${parentSubzone.lore}
            Difficulty: ${parentSubzone.difficultyLevel}
            Direction Hint: ${directionHint ?: "unspecified"}

            Generate a room/space with:
            - Vivid description (2-3 sentences)
            - 3-6 exits (mix cardinal directions like "north", "south" and descriptive like "climb ladder", "through archway")
            - If Direction Hint references vertical movement (e.g., "down", "climb ladder"), ensure at least one exit that fits that motion
            - Describe how this space lies ${directionHint ?: "relative to its parent"} so navigation text stays coherent
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

    /**
     * Cache chunk-level lore into the vector store for fast recall.
     */
    private suspend fun cacheChunkLoreEntry(chunkId: String, chunk: WorldChunkComponent) {
        val manager = memoryManager ?: return
        if (chunk.lore.isBlank() && chunk.biomeTheme.isBlank()) return

        val metadata = mapOf(
            "type" to "chunk_lore",
            "chunkId" to chunkId,
            "chunkLevel" to chunk.level.name
        )
        val mobDensityFormatted = String.format("%.2f", chunk.mobDensity)
        val content = """
            Chunk ${chunk.level.name} [$chunkId]
            Theme: ${chunk.biomeTheme.ifBlank { "unspecified" }}
            Difficulty: ${chunk.difficultyLevel}
            Mob density: $mobDensityFormatted
            Lore: ${chunk.lore.take(600)}
        """.trimIndent()

        manager.remember(content, metadata)
    }

    /**
     * Cache generated space descriptions for reuse on re-entry.
     */
    private suspend fun cacheSpaceDescription(
        spaceId: String,
        name: String,
        description: String,
        parentChunk: WorldChunkComponent
    ) {
        val manager = memoryManager ?: return
        if (description.isBlank()) return

        val metadata = mapOf(
            "type" to "space_description",
            "chunkId" to spaceId,
            "chunkLevel" to ChunkLevel.SPACE.name,
            "parentChunkLevel" to parentChunk.level.name,
            "parentTheme" to parentChunk.biomeTheme
        )
        val content = """
            Space [$spaceId] - ${name.ifBlank { "Unnamed space" }}
            Parent chunk: ${parentChunk.level.name} (${parentChunk.biomeTheme.ifBlank { "unspecified theme" }})
            Description: ${description.take(600)}
        """.trimIndent()

        manager.remember(content, metadata)
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

/**
 * Result of chunk generation including graph topology for V3
 *
 * @param chunk The generated chunk component
 * @param chunkId The entity ID for the chunk
 * @param graphNodes List of graph nodes (empty if not SUBZONE or V2 mode)
 */
data class ChunkGenerationResult(
    val chunk: WorldChunkComponent,
    val chunkId: String,
    val graphNodes: List<GraphNodeComponent> = emptyList()
)
