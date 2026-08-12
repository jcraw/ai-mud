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
import com.jcraw.mud.core.repository.GraphNodeRepository
import com.jcraw.mud.core.repository.SpacePropertiesRepository
import com.jcraw.mud.core.repository.WorldChunkRepository
import com.jcraw.mud.core.world.ChunkIdGenerator
import com.jcraw.mud.core.world.ChunkLevel
import com.jcraw.mud.core.world.GenerationContext
import com.jcraw.mud.core.world.NodeType

/**
 * First combat subzone helpers (MUD-034g pure move).
 * Orchestrator on [DungeonInitializer] host.
 */
internal object DungeonInitializerCombat {

    fun combatSubzoneContext(
        seed: String,
        globalLore: String,
        zoneChunk: WorldChunkComponent,
        zoneId: String
    ): GenerationContext = GenerationContext(
        seed = seed,
        globalLore = globalLore,
        parentChunk = zoneChunk,
        parentChunkId = zoneId,
        level = ChunkLevel.SUBZONE,
        direction = "dungeon entrance"
    )

    fun remapGraphNodes(
        graphNodes: List<GraphNodeComponent>,
        subzoneId: String
    ): List<GraphNodeComponent> {
        val nodeIdMapping = graphNodes.associate { node ->
            node.id to ChunkIdGenerator.generate(ChunkLevel.SPACE, subzoneId)
        }
        return graphNodes.map { node ->
            val newId = nodeIdMapping.getValue(node.id)
            val remappedEdges = node.neighbors.map { edge ->
                edge.copy(targetId = nodeIdMapping[edge.targetId] ?: edge.targetId)
            }
            node.copy(id = newId, chunkId = subzoneId, neighbors = remappedEdges)
        }
    }

    suspend fun generateFallbackCombatSpace(
        worldGenerator: WorldGenerator,
        chunkRepo: WorldChunkRepository,
        spaceRepo: SpacePropertiesRepository,
        graphNodeRepo: GraphNodeRepository,
        subzoneChunk: WorldChunkComponent,
        subzoneId: String
    ): Result<CombatSubzoneResult> {
        val (space, spaceId) = worldGenerator.generateSpace(subzoneChunk, subzoneId)
            .getOrElse { return Result.failure(it) }
        spaceRepo.save(space, spaceId).getOrElse { return Result.failure(it) }
        val fallbackNode = GraphNodeComponent(
            id = spaceId, type = NodeType.Linear, neighbors = emptyList(), chunkId = subzoneId
        )
        graphNodeRepo.save(fallbackNode).getOrElse { return Result.failure(it) }
        chunkRepo.save(subzoneChunk.copy(children = listOf(spaceId)), subzoneId)
            .getOrElse { return Result.failure(it) }
        return Result.success(CombatSubzoneResult(spaceId, subzoneId))
    }

    suspend fun saveRemappedSpaces(
        worldGenerator: WorldGenerator,
        spaceRepo: SpacePropertiesRepository,
        graphNodeRepo: GraphNodeRepository,
        subzoneChunk: WorldChunkComponent,
        remappedNodes: List<GraphNodeComponent>
    ): Result<List<String>> {
        val spaceIds = mutableListOf<String>()
        remappedNodes.forEach { node ->
            graphNodeRepo.save(node).getOrElse { return Result.failure(it) }
            val stub = worldGenerator.generateSpaceStub(node, subzoneChunk)
                .getOrElse { return Result.failure(it) }
            spaceRepo.save(stub, node.id).getOrElse { return Result.failure(it) }
            spaceIds += node.id
        }
        return Result.success(spaceIds)
    }

    suspend fun materializeGraphCombatSpaces(
        worldGenerator: WorldGenerator,
        chunkRepo: WorldChunkRepository,
        spaceRepo: SpacePropertiesRepository,
        graphNodeRepo: GraphNodeRepository,
        subzoneChunk: WorldChunkComponent,
        subzoneId: String,
        graphNodes: List<GraphNodeComponent>
    ): Result<CombatSubzoneResult> {
        val remappedNodes = remapGraphNodes(graphNodes, subzoneId)
        val spaceIds = saveRemappedSpaces(
            worldGenerator, spaceRepo, graphNodeRepo, subzoneChunk, remappedNodes
        ).getOrElse { return Result.failure(it) }
        chunkRepo.save(subzoneChunk.copy(children = spaceIds), subzoneId)
            .getOrElse { return Result.failure(it) }
        val entranceNode = remappedNodes.firstOrNull { it.type is NodeType.Hub }
            ?: remappedNodes.first()
        return Result.success(CombatSubzoneResult(entranceNode.id, subzoneId))
    }
}
