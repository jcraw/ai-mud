@file:Suppress(
    "ReturnCount",
    "MagicNumber",
    "MaxLineLength",
    "TooManyFunctions",
    "LongMethod",
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "LongParameterList",
    "UnusedParameter",
    "TooGenericExceptionCaught",
    "TooGenericExceptionThrown",
    "SwallowedException",
    "WildcardImport",
    "MayBeConst",
    "ImplicitDefaultLocale",
    "ForbiddenComment",
    "UnusedPrivateProperty",
)

package com.jcraw.mud.reasoning.world

import com.jcraw.mud.core.*
import com.jcraw.mud.core.world.*
import com.jcraw.mud.memory.MemoryManager
import com.jcraw.mud.reasoning.worldgen.GraphGenerator
import com.jcraw.mud.reasoning.worldgen.GraphValidator
import com.jcraw.sophia.llm.LLMClient

/**
 * Primary world generation engine.
 *
 * V3 UPDATE: Now generates graph topology at SUBZONE level before content generation.
 * Handles LLM-driven chunk and space generation with lore inheritance and theme coherence.
 * Uses JSON-structured prompts for consistent, parseable output.
 *
 * Thin facade — topology/biome/LLM/fill/trap/cache extracted (MUD-034g).
 */
class WorldGenerator(
    private val llmClient: LLMClient,
    private val loreEngine: LoreInheritanceEngine,
    private val graphGenerator: GraphGenerator? = null,
    private val graphValidator: GraphValidator? = null,
    private val memoryManager: MemoryManager? = null
) {
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
        val chunkData = WorldGeneratorLlmChunk.generateChunkData(llmClient, context, lore)
            .getOrElse { return Result.failure(it) }

        val resolvedBiomeTheme = WorldGeneratorBiome.resolveBiomeTheme(context, chunkData.biomeTheme)

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
            WorldGeneratorTopology.generateGraphTopology(graphGenerator, graphValidator, chunkId, chunk)
                .getOrElse { return Result.failure(it) }
        } else {
            emptyList()
        }

        chunk = WorldGeneratorBiome.enforceRootBiome(chunkId, chunk, context)

        context.parentChunk?.let { parent ->
            WorldGeneratorBiome.logPromptCascade(chunkId, chunk, parent)
        }

        WorldGeneratorCache.cacheChunkLoreEntry(memoryManager, chunkId, chunk)

        return Result.success(ChunkGenerationResult(chunk, chunkId, graphNodes))
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
        val spaceData = WorldGeneratorLlmSpace.generateSpaceData(llmClient, parentSubzone, directionHint)
            .getOrElse { return Result.failure(it) }

        // RNG order preserved inside assemble: traps → resources → exit hidden rolls
        val space = WorldGeneratorSpaceBuild.assembleV2Space(spaceData, parentSubzone)

        WorldGeneratorCache.cacheSpaceDescription(
            memoryManager, spaceId, space.name, space.description, parentSubzone
        )

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
        val space = WorldGeneratorSpaceBuild.assembleSpaceStub(graphNode, chunk)
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
        val userContext = WorldGeneratorNodeContent.composeUserContext(
            graphNode, chunk, graphNode.chunkId, generatedNamesPerChunk
        )
        val (name, description) = WorldGeneratorNodeContent.requestNameDescription(llmClient, userContext)
            .getOrElse { return Result.failure(it) }

        // Track generated name to avoid duplicates
        generatedNamesPerChunk.getOrPut(graphNode.chunkId) { mutableSetOf() }.add(name)

        // Determine brightness and terrain based on node type
        val (brightness, terrain) = WorldGeneratorNodeProps.determineNodeProperties(graphNode.type, chunk)

        val filled = currentSpace.copy(
            name = name,
            description = description,
            brightness = brightness,
            terrainType = terrain
        )

        WorldGeneratorCache.cacheSpaceDescription(
            memoryManager, graphNode.id, filled.name, filled.description, chunk
        )

        return Result.success(filled)
    }

    /**
     * Generates theme-appropriate trap.
     */
    fun generateTrap(theme: String, difficultyLevel: Int): TrapData {
        return WorldGeneratorTrapResource.generateTrap(theme, difficultyLevel)
    }

    /**
     * Generates theme-appropriate resource node.
     */
    fun generateResource(theme: String): ResourceNode {
        return WorldGeneratorTrapResource.generateResource(theme)
    }
}
