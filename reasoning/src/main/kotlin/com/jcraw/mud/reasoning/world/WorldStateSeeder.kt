package com.jcraw.mud.reasoning.world

import com.jcraw.mud.core.GraphNodeComponent
import com.jcraw.mud.core.SpacePropertiesComponent
import com.jcraw.mud.core.WorldChunkComponent
import com.jcraw.mud.core.WorldState
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.repository.GraphNodeRepository
import com.jcraw.mud.core.repository.SpacePropertiesRepository
import com.jcraw.mud.core.repository.WorldChunkRepository
import com.jcraw.mud.core.repository.TreasureRoomRepository
import com.jcraw.mud.core.repository.SpaceEntityRepository

/**
 * Loads persisted graph nodes/spaces/chunks/entities into an in-memory WorldState.
 * Shared by console + GUI so both hydrate the world the same way.
 */
class WorldStateSeeder(
    private val worldChunkRepository: WorldChunkRepository,
    private val graphNodeRepository: GraphNodeRepository,
    private val spacePropertiesRepository: SpacePropertiesRepository,
    private val treasureRoomRepository: TreasureRoomRepository,
    private val spaceEntityRepository: SpaceEntityRepository,
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

        val treasureRooms = treasureRoomRepository.findAll().getOrElse {
            onWarning("Failed to load treasure rooms: ${it.message}")
            emptyList()
        }.toMap()

        // Load entities from spaces
        val loadedEntities = mutableMapOf<String, Entity>()
        loadedSpaces.forEach { (spaceId, space) ->
            space.entities.forEach { entityId ->
                spaceEntityRepository.findById(entityId).onSuccess { entity ->
                    if (entity != null) {
                        loadedEntities[entityId] = entity
                    } else {
                        onWarning("Entity $entityId from space $spaceId is null")
                    }
                }.onFailure {
                    onWarning("Failed to load entity $entityId from space $spaceId: ${it.message}")
                }
            }
        }

        return baseState.copy(
            graphNodes = loadedNodes,
            spaces = loadedSpaces,
            chunks = loadedChunks,
            treasureRooms = baseState.treasureRooms + treasureRooms,
            entities = loadedEntities
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
