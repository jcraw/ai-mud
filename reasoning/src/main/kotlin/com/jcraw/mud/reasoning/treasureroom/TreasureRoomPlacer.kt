package com.jcraw.mud.reasoning.treasureroom

import com.jcraw.mud.core.Pedestal
import com.jcraw.mud.core.PedestalState
import com.jcraw.mud.core.TreasureRoomComponent
import com.jcraw.mud.core.TreasureRoomType
import com.jcraw.mud.core.GraphNodeComponent
import com.jcraw.mud.core.world.NodeType
import kotlin.random.Random

/**
 * Utility class for treasure room placement logic in world generation.
 * Provides early placement constraints, BFS helpers, and starter templates.
 */
class TreasureRoomPlacer {

    /**
     * Determine if a treasure room should be placed at this position (legacy V2 helper).
     */
    fun shouldPlaceTreasureRoom(
        positionIndex: Int,
        totalPositions: Int,
        alreadyPlaced: Boolean,
        random: Random = Random.Default
    ): Boolean {
        val isEarlyPosition = positionIndex in 0..2
        if (!isEarlyPosition || alreadyPlaced) return false

        val probability = when (positionIndex) {
            0 -> 0.0
            1 -> 0.5
            2 -> 0.75
            else -> 1.0
        }

        return random.nextDouble() < probability
    }

    /**
     * Choose a treasure room node for graph-based generation (distance 2-3 from start).
     */
    fun selectTreasureRoomNode(
        nodes: List<GraphNodeComponent>,
        startNodeId: String
    ): GraphNodeComponent? {
        if (nodes.isEmpty()) return null

        val distances = calculateBFSDistances(startNodeId, nodes)
        val candidates = findTreasureRoomCandidates(nodes, distances)
        if (candidates.isEmpty()) {
            return nodes.firstOrNull { node ->
                node.type !is NodeType.Hub && node.type !is NodeType.Boss
            }
        }

        return candidates.sortedWith(
            compareBy<GraphNodeComponent> { candidatePriority(it) }
                .thenBy { distances[it.id] ?: Int.MAX_VALUE }
        ).firstOrNull()
    }

    /**
     * Create treasure room component for starter template with biome-aware pedestals.
     */
    fun createStarterTreasureRoomComponent(
        biomeName: String
    ): TreasureRoomComponent {
        val theme = TreasureRoomDescriptionGenerator.getBiomeTheme(biomeName)
        val pedestals = listOf(
            Pedestal(
                pedestalIndex = 0,
                itemTemplateId = "flamebrand_longsword",
                state = PedestalState.AVAILABLE,
                themeDescription = "${theme.material} altar bearing a warrior's blade"
            ),
            Pedestal(
                pedestalIndex = 1,
                itemTemplateId = "shadowweave_cloak",
                state = PedestalState.AVAILABLE,
                themeDescription = "shadowed ${theme.material} pedestal wreathed in darkness"
            ),
            Pedestal(
                pedestalIndex = 2,
                itemTemplateId = "stormcaller_staff",
                state = PedestalState.AVAILABLE,
                themeDescription = "glowing ${theme.material} shrine pulsing with arcane energy"
            ),
            Pedestal(
                pedestalIndex = 3,
                itemTemplateId = "titans_band",
                state = PedestalState.AVAILABLE,
                themeDescription = "sturdy ${theme.material} stand adorned with fortitude runes"
            ),
            Pedestal(
                pedestalIndex = 4,
                itemTemplateId = "arcane_blade",
                state = PedestalState.AVAILABLE,
                themeDescription = "ornate ${theme.material} dais marked with dual symbols"
            )
        )

        return TreasureRoomComponent(
            roomType = TreasureRoomType.STARTER,
            pedestals = pedestals,
            currentlyTakenItem = null,
            hasBeenLooted = false,
            biomeTheme = biomeName
        )
    }

    /**
     * Calculate BFS distances from starting node (used for placement heuristics).
     */
    fun calculateBFSDistances(
        startNodeId: String,
        nodes: List<GraphNodeComponent>
    ): Map<String, Int> {
        val distances = mutableMapOf<String, Int>()
        val queue = ArrayDeque<Pair<String, Int>>()
        val visited = mutableSetOf<String>()

        queue.add(startNodeId to 0)
        visited.add(startNodeId)
        distances[startNodeId] = 0

        while (queue.isNotEmpty()) {
            val (currentId, currentDist) = queue.removeFirst()
            val currentNode = nodes.firstOrNull { it.id == currentId } ?: continue

            currentNode.neighbors.forEach { edge ->
                val neighborId = edge.targetId
                if (neighborId !in visited) {
                    visited.add(neighborId)
                    val newDist = currentDist + 1
                    distances[neighborId] = newDist
                    queue.add(neighborId to newDist)
                }
            }
        }

        return distances
    }

    /**
     * Find candidate nodes for treasure room placement (distance 2-3, non-frontier).
     */
    fun findTreasureRoomCandidates(
        nodes: List<GraphNodeComponent>,
        distances: Map<String, Int>
    ): List<GraphNodeComponent> {
        return nodes.filter { node ->
            val distance = distances[node.id] ?: Int.MAX_VALUE
            distance in 2..3 &&
                node.type !is NodeType.Boss &&
                node.type !is NodeType.Frontier &&
                node.type !is NodeType.Hub
        }
    }

    private fun candidatePriority(node: GraphNodeComponent): Int {
        return when (node.type) {
            is NodeType.DeadEnd -> 0
            is NodeType.Linear -> 1
            is NodeType.Branching -> 2
            else -> 3
        }
    }

    companion object {
        private val BIOME_MAPPINGS = mapOf(
            "ancient_abyss" to "ancient_abyss",
            "abyssal_dungeon" to "ancient_abyss",
            "magma_cave" to "magma_cave",
            "volcanic_depths" to "magma_cave",
            "frozen_depths" to "frozen_depths",
            "ice_caverns" to "frozen_depths",
            "bone_crypt" to "bone_crypt",
            "undead_tomb" to "bone_crypt",
            "elven_ruins" to "elven_ruins",
            "ancient_forest" to "elven_ruins",
            "dwarven_halls" to "dwarven_halls",
            "mountain_stronghold" to "dwarven_halls"
        )

        fun getBiomeName(dungeonTheme: String): String {
            return BIOME_MAPPINGS[dungeonTheme.lowercase()] ?: "ancient_abyss"
        }
    }
}
