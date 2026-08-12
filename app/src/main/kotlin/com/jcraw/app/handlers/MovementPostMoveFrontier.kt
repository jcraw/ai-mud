@file:Suppress(
    "ReturnCount",
    "MagicNumber",
    "MaxLineLength",
    "TooManyFunctions",
    "LongMethod",
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "LongParameterList"
)

package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.core.GraphNodeComponent
import com.jcraw.mud.core.WorldChunkComponent
import com.jcraw.mud.core.world.ChunkLevel
import com.jcraw.mud.core.world.EdgeData
import com.jcraw.mud.core.world.GenerationContext
import com.jcraw.mud.core.world.NodeType
import com.jcraw.mud.reasoning.world.ChunkGenerationResult
import kotlinx.coroutines.runBlocking

/**
 * Frontier chunk expansion fragment for [MovementPostMoveHandlers].
 */
internal object MovementPostMoveFrontier {

    fun tryExpandFrontier(game: MudGame) {
        val frontierNode = game.worldState.getCurrentGraphNode() ?: return
        if (frontierNode.type !is NodeType.Frontier) return
        val chunk = game.worldState.getChunk(frontierNode.chunkId) ?: return
        if (game.worldGenerator == null || game.graphNodeRepository == null) return
        val hasGeneratedExit = frontierNode.neighbors.any { edge ->
            game.worldState.getGraphNode(edge.targetId) != null
        }
        if (hasGeneratedExit) return
        runBlocking {
            expandFrontierChunk(game, frontierNode, chunk)
        }
    }

    private suspend fun expandFrontierChunk(
        game: MudGame,
        frontierNode: GraphNodeComponent,
        chunk: WorldChunkComponent
    ) {
        val context = GenerationContext(
            seed = (frontierNode.chunkId.hashCode().toLong() + System.currentTimeMillis()).toString(),
            globalLore = chunk.lore,
            parentChunk = chunk,
            parentChunkId = chunk.parentId,
            level = ChunkLevel.SUBZONE,
            direction = "frontier_expansion"
        )
        val result = game.worldGenerator?.generateChunk(context)
        result?.onSuccess { genResult ->
            persistGeneratedChunk(game, genResult)
            linkFrontierToHub(game, frontierNode, genResult.graphNodes)
        }?.onFailure { error ->
            println("(Frontier generation failed: ${error.message})")
        }
    }

    private fun persistGeneratedChunk(game: MudGame, genResult: ChunkGenerationResult) {
        game.worldChunkRepository.save(genResult.chunk, genResult.chunkId)
        game.worldState = game.worldState.addChunk(genResult.chunkId, genResult.chunk)
        genResult.graphNodes.forEach { node ->
            game.graphNodeRepository!!.save(node)
            game.worldState = game.worldState.updateGraphNode(node.id, node)
            val spaceStub = game.worldGenerator?.generateSpaceStub(node, genResult.chunk)
            spaceStub?.onSuccess { space ->
                val nodeSpaceId = "${genResult.chunkId}_node_${node.id}"
                game.spacePropertiesRepository.save(space, nodeSpaceId)
                game.worldState = game.worldState.updateSpace(nodeSpaceId, space)
            }
        }
    }

    private fun linkFrontierToHub(
        game: MudGame,
        frontierNode: GraphNodeComponent,
        graphNodes: List<GraphNodeComponent>
    ) {
        val hubNode = graphNodes.find { it.type is NodeType.Hub } ?: return
        val edgeDirection = "frontier passage"
        val newEdge = EdgeData(
            targetId = hubNode.id,
            direction = edgeDirection,
            hidden = false
        )
        val updatedFrontier = frontierNode.copy(
            neighbors = frontierNode.neighbors + newEdge
        )
        game.graphNodeRepository!!.update(updatedFrontier)
        game.worldState = game.worldState.updateGraphNode(updatedFrontier.id, updatedFrontier)
        println("\n🗺️  You've discovered a new frontier! A passage opens before you...")
    }
}
