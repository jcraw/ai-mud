package com.jcraw.mud.reasoning.worldgen

import com.jcraw.mud.core.GraphNodeComponent
import kotlin.random.Random

/**
 * Graph topology generator for V3 world system
 * Generates pre-computed graph structures before content generation
 * Supports multiple layout algorithms: Grid, BSP, FloodFill
 * Uses Kruskal MST for connectivity, adds loops, assigns node types, marks hidden edges
 *
 * Implementation detail: layout / MST / edges / typing pure-moved to sibling objects
 * (MUD-034b) — public API and generate pipeline order unchanged.
 */
class GraphGenerator(
    private val rng: Random,
    private val difficultyLevel: Int = 1
) {
    /**
     * Generate graph topology for a chunk
     *
     * @param chunkId Chunk this graph belongs to
     * @param layout Algorithm configuration
     * @return List of connected graph nodes with typed structure
     */
    fun generate(
        chunkId: String,
        layout: GraphLayout
    ): List<GraphNodeComponent> {
        val nodes = generateInitialNodes(chunkId, layout)
        if (nodes.isEmpty()) {
            throw IllegalStateException("Layout $layout generated no nodes")
        }
        return connectAndTypeNodes(nodes, chunkId, layout)
    }

    private fun generateInitialNodes(
        chunkId: String,
        layout: GraphLayout
    ): List<GraphNodeComponent> {
        return when (layout) {
            is GraphLayout.Grid -> GraphLayoutNodes.generateGridNodes(chunkId, layout)
            is GraphLayout.BSP -> GraphLayoutNodes.generateBSPNodes(chunkId, layout, rng)
            is GraphLayout.FloodFill -> GraphLayoutNodes.generateFloodFillNodes(chunkId, layout, rng)
        }
    }

    private fun connectAndTypeNodes(
        nodes: List<GraphNodeComponent>,
        chunkId: String,
        layout: GraphLayout
    ): List<GraphNodeComponent> {
        val mstEdges = GraphMst.kruskalMST(nodes)
        val loopEdges = GraphMst.addLoopEdges(nodes, mstEdges, layout.loopFrequency, rng)
        val nodesWithEdges = GraphEdgeDirections.buildNodeEdges(nodes, mstEdges + loopEdges)
        val typedNodes = GraphNodeTyping.assignNodeTypes(nodesWithEdges, chunkId, rng)
        return GraphNodeTyping.markHiddenEdges(typedNodes, rng, difficultyLevel)
    }
}
