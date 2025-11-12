package com.jcraw.mud.reasoning.worldgen

import kotlinx.serialization.Serializable

/**
 * Algorithm selection for graph topology generation
 * Sealed class enables exhaustive when statements and type-safe configuration
 * Each layout algorithm produces different structural patterns for world navigation
 *
 * @property loopFrequency Controls density of loop edges (0.0-1.0)
 *           0.0 = minimum loops (MST only), 0.5 = default, 1.0 = maximum loops
 */
@Serializable
sealed class GraphLayout {
    /**
     * Loop generation frequency multiplier
     * Scales the number of extra edges added beyond minimum spanning tree
     * Range: 0.0 (sparse, tree-like) to 1.0 (dense, many loops)
     * Default: 0.5 (balanced - targets avg degree ~3.0)
     */
    abstract val loopFrequency: Double
    /**
     * Grid layout - Rectangular graph with regular connectivity
     * Best for: Dungeons, buildings, structured environments
     * Properties:
     * - Regular NxM node arrangement
     * - Cardinal direction edges (N/S/E/W)
     * - Predictable navigation
     *
     * @param width Number of nodes horizontally
     * @param height Number of nodes vertically
     */
    @Serializable
    data class Grid(
        val width: Int,
        val height: Int,
        override val loopFrequency: Double = 0.5
    ) : GraphLayout() {
        init {
            require(width > 0) { "Grid width must be positive, got $width" }
            require(height > 0) { "Grid height must be positive, got $height" }
            require(width * height <= 100) { "Grid too large: ${width}x${height} = ${width * height} nodes (max 100)" }
            require(loopFrequency in 0.0..1.0) { "Grid loopFrequency must be 0.0-1.0, got $loopFrequency" }
        }

        /**
         * Calculate total node count for this grid
         */
        fun nodeCount(): Int = width * height

        override fun toString(): String = "Grid(${width}x${height})"
    }

    /**
     * Binary Space Partitioning layout - Recursive room subdivision
     * Best for: Buildings, dungeon floors, hierarchical spaces
     * Properties:
     * - Creates rectangular rooms via recursive splits
     * - Natural corridors between rooms
     * - Hierarchical structure
     *
     * @param minRoomSize Minimum room dimension (stops subdivision)
     * @param maxDepth Maximum recursion depth
     */
    @Serializable
    data class BSP(
        val minRoomSize: Int = 3,
        val maxDepth: Int = 4,
        override val loopFrequency: Double = 0.5
    ) : GraphLayout() {
        init {
            require(minRoomSize >= 2) { "BSP minRoomSize must be >= 2, got $minRoomSize" }
            require(maxDepth in 1..6) { "BSP maxDepth must be 1-6, got $maxDepth" }
            require(loopFrequency in 0.0..1.0) { "BSP loopFrequency must be 0.0-1.0, got $loopFrequency" }
        }

        /**
         * Estimate node count for this BSP configuration
         * Actual count varies due to random splits
         */
        fun estimateNodeCount(): Int = (1 shl maxDepth) + (minRoomSize * 2)

        override fun toString(): String = "BSP(minRoom=$minRoomSize, depth=$maxDepth)"
    }

    /**
     * Flood-fill layout - Organic growth from seed points
     * Best for: Caves, natural formations, irregular spaces
     * Properties:
     * - Irregular, organic connectivity
     * - Variable node positions
     * - Natural-looking cave systems
     *
     * @param nodeCount Target number of nodes to generate
     * @param density Connection density (0.0-1.0, higher = more connections)
     */
    @Serializable
    data class FloodFill(
        val nodeCount: Int,
        val density: Double = 0.4,
        override val loopFrequency: Double = 0.5
    ) : GraphLayout() {
        init {
            require(nodeCount in 5..100) { "FloodFill nodeCount must be 5-100, got $nodeCount" }
            require(density in 0.1..1.0) { "FloodFill density must be 0.1-1.0, got $density" }
            require(loopFrequency in 0.0..1.0) { "FloodFill loopFrequency must be 0.0-1.0, got $loopFrequency" }
        }

        override fun toString(): String = "FloodFill(nodes=$nodeCount, density=$density)"
    }

    companion object {
        /**
         * Select appropriate layout for biome theme
         * Heuristic mapping for world generation
         */
        fun forBiome(biomeTheme: String): GraphLayout {
            return when {
                biomeTheme.contains("dungeon", ignoreCase = true) -> Grid(6, 6)
                biomeTheme.contains("building", ignoreCase = true) -> BSP(minRoomSize = 4, maxDepth = 3)
                biomeTheme.contains("tower", ignoreCase = true) -> Grid(3, 8)
                biomeTheme.contains("cave", ignoreCase = true) -> FloodFill(nodeCount = 20, density = 0.3)
                biomeTheme.contains("mine", ignoreCase = true) -> FloodFill(nodeCount = 25, density = 0.35)
                biomeTheme.contains("temple", ignoreCase = true) -> BSP(minRoomSize = 5, maxDepth = 3)
                biomeTheme.contains("forest", ignoreCase = true) -> FloodFill(nodeCount = 30, density = 0.4)
                biomeTheme.contains("ruins", ignoreCase = true) -> BSP(minRoomSize = 3, maxDepth = 4)
                else -> Grid(5, 5) // Default fallback
            }
        }

        /**
         * Create layout for target node count
         * Chooses most appropriate algorithm
         */
        fun forNodeCount(targetCount: Int): GraphLayout {
            return when {
                targetCount <= 10 -> Grid(3, 3)
                targetCount <= 25 -> Grid(5, 5)
                targetCount <= 50 -> BSP(minRoomSize = 4, maxDepth = 4)
                else -> FloodFill(nodeCount = targetCount.coerceIn(5, 100), density = 0.4)
            }
        }
    }
}
