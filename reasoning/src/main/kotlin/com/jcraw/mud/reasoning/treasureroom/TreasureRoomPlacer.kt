package com.jcraw.mud.reasoning.treasureroom

import com.jcraw.mud.core.*
import com.jcraw.mud.core.world.GraphNodeComponent
import kotlin.random.Random

/**
 * Utility class for treasure room placement logic in world generation
 * Provides early placement constraints and helper methods
 *
 * Integration Design:
 * - For V2: WorldGenerator/SpacePopulator should call shouldPlaceTreasureRoom during SUBZONE space generation
 * - For V3: WorldGenerator should call during graph content population
 * - Biome theming: Use getBiomeName to map dungeon theme to treasure room biome
 *
 * Example V2 Integration:
 * ```
 * val placer = TreasureRoomPlacer()
 * if (placer.shouldPlaceTreasureRoom(spaceIndex, totalSpaces, hasExistingTreasureRoom)) {
 *     val component = placer.createStarterTreasureRoomComponent(
 *         biomeName = TreasureRoomPlacer.getBiomeName(dungeonTheme)
 *     )
 *     space = space.copy(components = space.components + component)
 * }
 * ```
 */
class TreasureRoomPlacer {

    /**
     * Determine if a treasure room should be placed at this position
     * Uses early placement constraint (first 2-3 positions)
     *
     * @param positionIndex 0-based position in generation sequence
     * @param totalPositions Total number of positions being generated
     * @param alreadyPlaced True if chunk already has a treasure room (one per chunk/dungeon)
     * @param random Random instance for placement probability
     * @return True if treasure room should be placed here
     */
    fun shouldPlaceTreasureRoom(
        positionIndex: Int,
        totalPositions: Int,
        alreadyPlaced: Boolean,
        random: Random = Random.Default
    ): Boolean {
        // Early placement constraint: only first 2-3 positions
        val isEarlyPosition = positionIndex in 0..2

        if (!isEarlyPosition || alreadyPlaced) {
            return false
        }

        // Probability-based placement in early positions
        // Position 0: 0% (starting room should be normal)
        // Position 1: 50% chance
        // Position 2: 75% chance
        // Position 3: 100% chance (if we got here)
        val probability = when (positionIndex) {
            0 -> 0.0
            1 -> 0.5
            2 -> 0.75
            else -> 1.0
        }

        return random.nextDouble() < probability
    }

    /**
     * Create treasure room component for a space (hardcoded starter template)
     * For production use, load from TreasureRoomRepository.findBySpaceId
     *
     * @param biomeName Biome/theme name (e.g., "ancient_abyss", "magma_cave")
     * @return TreasureRoomComponent configured with starter pedestals
     */
    fun createStarterTreasureRoomComponent(
        biomeName: String
    ): TreasureRoomComponent {
        // Hardcoded starter treasure room pedestals
        // In production, these should be loaded from treasure_room_templates.json via repository
        val pedestals = listOf(
            Pedestal(
                pedestalIndex = 0,
                itemTemplateId = "flamebrand_longsword",
                state = PedestalState.AVAILABLE,
                themeDescription = "$biomeMaterial altar bearing a warrior's blade"
            ),
            Pedestal(
                pedestalIndex = 1,
                itemTemplateId = "shadowweave_cloak",
                state = PedestalState.AVAILABLE,
                themeDescription = "shadowed $biomeMaterial pedestal wreathed in darkness"
            ),
            Pedestal(
                pedestalIndex = 2,
                itemTemplateId = "stormcaller_staff",
                state = PedestalState.AVAILABLE,
                themeDescription = "glowing $biomeMaterial shrine pulsing with arcane energy"
            ),
            Pedestal(
                pedestalIndex = 3,
                itemTemplateId = "titans_band",
                state = PedestalState.AVAILABLE,
                themeDescription = "sturdy $biomeMaterial stand adorned with fortitude runes"
            ),
            Pedestal(
                pedestalIndex = 4,
                itemTemplateId = "arcane_blade",
                state = PedestalState.AVAILABLE,
                themeDescription = "ornate $biomeMaterial dais marked with dual symbols"
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
     * Calculate BFS distance from starting node (for V3 graph-based placement)
     *
     * @param startNodeId Starting node ID
     * @param nodes All graph nodes in the chunk
     * @return Map of nodeId to BFS distance from start
     */
    fun calculateBFSDistances(
        startNodeId: String,
        nodes: List<GraphNodeComponent>
    ): Map<String, Int> {
        val distances = mutableMapOf<String, Int>()
        val queue = ArrayDeque<Pair<String, Int>>()
        val visited = mutableSetOf<String>()

        queue.add(Pair(startNodeId, 0))
        visited.add(startNodeId)
        distances[startNodeId] = 0

        while (queue.isNotEmpty()) {
            val (currentId, currentDist) = queue.removeFirst()
            val currentNode = nodes.firstOrNull { it.id == currentId } ?: continue

            // Process neighbors via EdgeData
            currentNode.neighbors.forEach { edge ->
                val neighborId = edge.targetId
                if (neighborId !in visited) {
                    visited.add(neighborId)
                    val newDist = currentDist + 1
                    distances[neighborId] = newDist
                    queue.add(Pair(neighborId, newDist))
                }
            }
        }

        return distances
    }

    /**
     * Find suitable treasure room node candidates in graph (V3)
     *
     * @param nodes All graph nodes in the chunk
     * @param startNodeId Starting node ID
     * @return List of node IDs suitable for treasure room placement (distance 2-3 from start)
     */
    fun findTreasureRoomCandidates(
        nodes: List<GraphNodeComponent>,
        startNodeId: String
    ): List<String> {
        val distances = calculateBFSDistances(startNodeId, nodes)

        return nodes
            .filter { node ->
                // Distance 2-3 from start
                val distance = distances[node.id] ?: Int.MAX_VALUE
                distance in 2..3
            }
            .map { it.id }
    }

    private val biomeMaterial: String
        get() = "weathered stone" // Default fallback

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

        /**
         * Get treasure room biome name from dungeon theme
         * Falls back to "ancient_abyss" if theme not recognized
         */
        fun getBiomeName(dungeonTheme: String): String {
            return BIOME_MAPPINGS[dungeonTheme.lowercase()] ?: "ancient_abyss"
        }
    }
}
     */
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

        /**
         * Get treasure room biome name from dungeon theme
         * Falls back to "ancient_abyss" if theme not recognized
         */
        fun getBiomeName(dungeonTheme: String): String {
            return BIOME_MAPPINGS[dungeonTheme.lowercase()] ?: "ancient_abyss"
        }
    }
}
