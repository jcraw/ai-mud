package com.jcraw.mud.reasoning.world

import com.jcraw.mud.core.GraphNodeComponent
import com.jcraw.mud.core.SpacePropertiesComponent
import com.jcraw.mud.core.WorldChunkComponent
import com.jcraw.mud.core.WorldState
import com.jcraw.mud.core.repository.GraphNodeRepository
import com.jcraw.mud.core.repository.SpacePropertiesRepository
import com.jcraw.mud.core.repository.WorldChunkRepository

/**
 * Loads persisted graph nodes/spaces/chunks into an in-memory WorldState.
 * Shared by console + GUI so both hydrate the world the same way.
 */
class WorldStateSeeder(
    private val worldChunkRepository: WorldChunkRepository,
    private val graphNodeRepository: GraphNodeRepository,
    private val spacePropertiesRepository: SpacePropertiesRepository,
    private val worldGenerator: WorldGenerator?
) {
    fun seedWorldState(
        baseState: WorldState,
        startingSpaceId: String,
        onWarning: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ): WorldState {
        val loadedChunks = worldChunkRepository.getAll().getOrElse {
            onError("Failed to load world chunks: ${it.message}")
            emptyMap()
        }

        var loadedNodes = graphNodeRepository.getAll().getOrElse {
            onError("Failed to load graph nodes: ${it.message}")
            emptyMap()
        }

        if (loadedNodes.isEmpty()) {
            val fallback = graphNodeRepository.findById(startingSpaceId).getOrElse {
                onError("Failed to load starting graph node $startingSpaceId: ${it.message}")
                null
            }
            if (fallback != null) {
                loadedNodes = mapOf(startingSpaceId to fallback)
            }
        }

        val loadedSpaces = mutableMapOf<String, SpacePropertiesComponent>()
        loadedNodes.forEach { (nodeId, node) ->
            val parentChunk = loadedChunks[node.chunkId] ?: worldChunkRepository.findById(node.chunkId).getOrElse {
                onWarning("Failed to load parent chunk ${node.chunkId} for $nodeId: ${it.message}")
                null
            }
            val space = loadOrCreateSpace(nodeId, node, parentChunk, onWarning, onError)
            if (space != null) {
                loadedSpaces[nodeId] = space
            }
        }

        if (!loadedSpaces.containsKey(startingSpaceId)) {
            val fallback = spacePropertiesRepository.findByChunkId(startingSpaceId).getOrElse {
                onError("Failed to load starting space $startingSpaceId: ${it.message}")
                null
            }
            if (fallback != null) {
                loadedSpaces[startingSpaceId] = fallback
            } else {
                onError("No space data found for starting room $startingSpaceId")
            }
        }

        return baseState.copy(
            graphNodes = loadedNodes,
            spaces = loadedSpaces,
            chunks = loadedChunks
        )
    }

    private fun loadOrCreateSpace(
        spaceId: String,
        node: GraphNodeComponent,
        parentChunk: WorldChunkComponent?,
        onWarning: (String) -> Unit,
        onError: (String) -> Unit
    ): SpacePropertiesComponent? {
        val existing = spacePropertiesRepository.findByChunkId(spaceId).getOrElse {
            onError("Failed to read space $spaceId: ${it.message}")
            null
        }
        if (existing != null) {
            return existing
        }

        val generator = worldGenerator
        if (generator == null) {
            onWarning("World generator unavailable; cannot synthesize missing space $spaceId")
            return null
        }

        val chunk = parentChunk ?: worldChunkRepository.findById(node.chunkId).getOrElse {
            onWarning("Failed to load parent chunk ${node.chunkId} for $spaceId: ${it.message}")
            null
        } ?: return null

        val stub = generator.generateSpaceStub(node, chunk).getOrElse {
            onWarning("Failed to generate fallback space $spaceId: ${it.message}")
            return null
        }

        spacePropertiesRepository.save(stub, spaceId).onFailure {
            onWarning("Failed to persist generated space $spaceId: ${it.message}")
        }

        return stub
    }
}
