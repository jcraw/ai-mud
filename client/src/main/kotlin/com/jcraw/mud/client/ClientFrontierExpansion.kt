@file:Suppress("ReturnCount")

package com.jcraw.mud.client

import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.core.GraphNodeComponent
import com.jcraw.mud.core.WorldChunkComponent
import com.jcraw.mud.core.world.ChunkLevel
import com.jcraw.mud.core.world.EdgeData
import com.jcraw.mud.core.world.GenerationContext
import com.jcraw.mud.core.world.NodeType
import com.jcraw.mud.reasoning.world.ChunkGenerationResult
import com.jcraw.mud.reasoning.world.WorldGenerator
import kotlinx.coroutines.runBlocking

/**
 * Frontier chunk expansion for [EngineGameClient]. Pure extract — no behavior change.
 */
object ClientFrontierExpansion {

    fun maybeExpandFrontier(game: EngineGameClient, currentNode: GraphNodeComponent) {
        if (currentNode.type !is NodeType.Frontier) return
        val chunk = loadFrontierChunk(game, currentNode.chunkId) ?: return
        if (hasGeneratedExit(game, currentNode)) return
        val generator = game.worldGenerator ?: return
        expandWithGenerator(game, currentNode, chunk, generator)
    }

    private fun loadFrontierChunk(game: EngineGameClient, chunkId: String) =
        game.worldState.getChunk(chunkId) ?: runBlocking {
            game.worldChunkRepository.findById(chunkId).getOrNull()
        }?.also { game.worldState = game.worldState.addChunk(chunkId, it) }

    private fun hasGeneratedExit(game: EngineGameClient, currentNode: GraphNodeComponent): Boolean =
        currentNode.neighbors.any { edge -> game.worldState.getGraphNode(edge.targetId) != null }

    private fun expandWithGenerator(
        game: EngineGameClient,
        currentNode: GraphNodeComponent,
        chunk: WorldChunkComponent,
        generator: WorldGenerator
    ) {
        runBlocking {
            generator.generateChunk(frontierContext(currentNode, chunk)).onSuccess { genResult ->
                applyGeneratedFrontier(game, currentNode, genResult, generator)
            }.onFailure { error ->
                game.emitEvent(
                    GameEvent.System(
                        "Frontier generation failed: ${error.message}",
                        GameEvent.MessageLevel.WARNING
                    )
                )
            }
        }
    }

    private fun frontierContext(
        currentNode: GraphNodeComponent,
        chunk: WorldChunkComponent
    ) = GenerationContext(
        seed = (currentNode.chunkId.hashCode().toLong() + System.currentTimeMillis()).toString(),
        globalLore = chunk.lore,
        parentChunk = chunk,
        parentChunkId = chunk.parentId,
        level = ChunkLevel.SUBZONE,
        direction = "frontier_expansion"
    )

    private fun applyGeneratedFrontier(
        game: EngineGameClient,
        currentNode: GraphNodeComponent,
        genResult: ChunkGenerationResult,
        generator: WorldGenerator
    ) {
        game.worldChunkRepository.save(genResult.chunk, genResult.chunkId)
        game.worldState = game.worldState.addChunk(genResult.chunkId, genResult.chunk)
        seedGeneratedNodes(game, genResult, generator)
        linkFrontierToHub(game, currentNode, genResult)
    }

    private fun seedGeneratedNodes(
        game: EngineGameClient,
        genResult: ChunkGenerationResult,
        generator: WorldGenerator
    ) {
        genResult.graphNodes.forEach { node ->
            game.graphNodeRepository.save(node)
            game.worldState = game.worldState.updateGraphNode(node.id, node)
            generator.generateSpaceStub(node, genResult.chunk).onSuccess { space ->
                game.spacePropertiesRepository.save(space, node.id)
                game.worldState = game.worldState.updateSpace(node.id, space)
            }.onFailure {
                game.emitEvent(
                    GameEvent.System(
                        "Failed to seed space ${node.id}: ${it.message}",
                        GameEvent.MessageLevel.WARNING
                    )
                )
            }
        }
    }

    private fun linkFrontierToHub(
        game: EngineGameClient,
        currentNode: GraphNodeComponent,
        genResult: ChunkGenerationResult
    ) {
        val hubNode = genResult.graphNodes.find { it.type is NodeType.Hub } ?: return
        val newEdge = EdgeData(
            targetId = hubNode.id,
            direction = "frontier passage",
            hidden = false
        )
        val updatedFrontier = currentNode.copy(neighbors = currentNode.neighbors + newEdge)
        game.graphNodeRepository.update(updatedFrontier)
        game.worldState = game.worldState.updateGraphNode(updatedFrontier.id, updatedFrontier)
        game.emitEvent(
            GameEvent.System("A new horizon opens beyond the frontier.", GameEvent.MessageLevel.INFO)
        )
    }
}
