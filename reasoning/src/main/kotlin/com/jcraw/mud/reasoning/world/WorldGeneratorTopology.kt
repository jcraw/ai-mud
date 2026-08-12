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

import com.jcraw.mud.core.GraphNodeComponent
import com.jcraw.mud.core.WorldChunkComponent
import com.jcraw.mud.reasoning.worldgen.GraphGenerator
import com.jcraw.mud.reasoning.worldgen.GraphLayout
import com.jcraw.mud.reasoning.worldgen.GraphValidator
import com.jcraw.mud.reasoning.worldgen.ValidationResult
import kotlin.random.Random

/**
 * Graph topology generation for SUBZONE chunks (MUD-034g pure move).
 * Preserves seeded RNG order.
 */
internal object WorldGeneratorTopology {

    fun generateNodes(
        graphGenerator: GraphGenerator,
        chunkId: String,
        chunk: WorldChunkComponent
    ): Result<List<GraphNodeComponent>> {
        // Select layout algorithm based on biome theme
        val layout = GraphLayout.forBiome(chunk.biomeTheme)

        // Generate graph with seeded RNG for reproducibility
        val seed = chunkId.hashCode().toLong()
        val rng = Random(seed)
        val generator = GraphGenerator(rng, chunk.difficultyLevel)

        return try {
            Result.success(generator.generate(chunkId, layout))
        } catch (e: Exception) {
            Result.failure(Exception("Graph generation failed for chunk $chunkId: ${e.message}", e))
        }
    }

    /**
     * Generates graph topology for a SUBZONE chunk.
     * Uses GraphGenerator with layout based on biome theme.
     * Validates graph before returning.
     */
    fun generateGraphTopology(
        graphGenerator: GraphGenerator,
        graphValidator: GraphValidator,
        chunkId: String,
        chunk: WorldChunkComponent
    ): Result<List<GraphNodeComponent>> {
        val graphNodes = generateNodes(graphGenerator, chunkId, chunk)
            .getOrElse { return Result.failure(it) }

        // Validate graph structure
        val validation = graphValidator.validate(graphNodes)
        if (validation is ValidationResult.Failure) {
            return Result.failure(
                Exception("Graph validation failed for chunk $chunkId: ${validation.reasons.joinToString(", ")}")
            )
        }

        return Result.success(graphNodes)
    }
}
