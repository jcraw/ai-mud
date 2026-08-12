@file:Suppress(
    "TooManyFunctions",
    "MagicNumber",
    "ReturnCount",
    "UnusedParameter",
    "LoopWithTooManyJumpStatements",
    "LongParameterList",
)

package com.jcraw.mud.reasoning.worldgen

import com.jcraw.mud.core.GraphNodeComponent
import com.jcraw.mud.core.world.NodeType
import kotlin.random.Random

/**
 * Layout node placement for [GraphGenerator]: grid / BSP / flood-fill.
 * Pure extract — no algorithm change. Preserves RNG call order.
 */
internal object GraphLayoutNodes {

    fun generateGridNodes(
        chunkId: String,
        layout: GraphLayout.Grid
    ): List<GraphNodeComponent> {
        val nodes = mutableListOf<GraphNodeComponent>()
        for (y in 0 until layout.height) {
            for (x in 0 until layout.width) {
                val nodeId = "$chunkId:grid_${x}_${y}"
                nodes.add(
                    GraphNodeComponent(
                        id = nodeId,
                        position = x to y,
                        type = NodeType.Linear, // Temporary, will reassign
                        chunkId = chunkId
                    )
                )
            }
        }
        return nodes
    }

    fun generateBSPNodes(
        chunkId: String,
        layout: GraphLayout.BSP,
        rng: Random
    ): List<GraphNodeComponent> {
        val rooms = mutableListOf<BSPRoom>()
        val root = BSPRoom(0, 0, 50, 50)
        subdivideRoom(root, layout.minRoomSize, layout.maxDepth, 0, rooms, rng)
        return rooms.mapIndexed { index, room -> roomToNode(chunkId, index, room) }
    }

    private fun roomToNode(chunkId: String, index: Int, room: BSPRoom): GraphNodeComponent {
        val centerX = room.x + room.width / 2
        val centerY = room.y + room.height / 2
        return GraphNodeComponent(
            id = "$chunkId:bsp_$index",
            position = centerX to centerY,
            type = NodeType.Linear, // Temporary
            chunkId = chunkId
        )
    }

    private fun subdivideRoom(
        room: BSPRoom,
        minSize: Int,
        maxDepth: Int,
        depth: Int,
        output: MutableList<BSPRoom>,
        rng: Random
    ) {
        if (depth >= maxDepth || room.width < minSize * 2 || room.height < minSize * 2) {
            output.add(room)
            return
        }
        if (shouldSplitHorizontal(room, rng)) {
            splitHorizontal(room, minSize, maxDepth, depth, output, rng)
        } else {
            splitVertical(room, minSize, maxDepth, depth, output, rng)
        }
    }

    private fun shouldSplitHorizontal(room: BSPRoom, rng: Random): Boolean {
        return if (room.width > room.height) {
            rng.nextBoolean()
        } else {
            room.height > room.width
        }
    }

    private fun splitHorizontal(
        room: BSPRoom,
        minSize: Int,
        maxDepth: Int,
        depth: Int,
        output: MutableList<BSPRoom>,
        rng: Random
    ) {
        val splitY = room.y + minSize + rng.nextInt(room.height - minSize * 2 + 1)
        val top = BSPRoom(room.x, room.y, room.width, splitY - room.y)
        val bottom = BSPRoom(room.x, splitY, room.width, room.y + room.height - splitY)
        subdivideRoom(top, minSize, maxDepth, depth + 1, output, rng)
        subdivideRoom(bottom, minSize, maxDepth, depth + 1, output, rng)
    }

    private fun splitVertical(
        room: BSPRoom,
        minSize: Int,
        maxDepth: Int,
        depth: Int,
        output: MutableList<BSPRoom>,
        rng: Random
    ) {
        val splitX = room.x + minSize + rng.nextInt(room.width - minSize * 2 + 1)
        val left = BSPRoom(room.x, room.y, splitX - room.x, room.height)
        val right = BSPRoom(splitX, room.y, room.x + room.width - splitX, room.height)
        subdivideRoom(left, minSize, maxDepth, depth + 1, output, rng)
        subdivideRoom(right, minSize, maxDepth, depth + 1, output, rng)
    }

    fun generateFloodFillNodes(
        chunkId: String,
        layout: GraphLayout.FloodFill,
        rng: Random
    ): List<GraphNodeComponent> {
        val nodes = mutableListOf<GraphNodeComponent>()
        val occupied = mutableSetOf<Pair<Int, Int>>()
        val startPos = 0 to 0
        occupied.add(startPos)
        nodes.add(floodNode(chunkId, 0, startPos))
        val candidates = mutableListOf(startPos)
        var nodeIndex = 1
        while (nodes.size < layout.nodeCount && candidates.isNotEmpty()) {
            val current = candidates.removeAt(rng.nextInt(candidates.size))
            nodeIndex = expandFloodNeighbors(
                chunkId, layout, rng, current, nodes, occupied, candidates, nodeIndex
            )
        }
        return nodes
    }

    private fun floodNode(chunkId: String, index: Int, pos: Pair<Int, Int>): GraphNodeComponent {
        return GraphNodeComponent(
            id = "$chunkId:flood_$index",
            position = pos,
            type = NodeType.Linear, // Temporary
            chunkId = chunkId
        )
    }

    private fun expandFloodNeighbors(
        chunkId: String,
        layout: GraphLayout.FloodFill,
        rng: Random,
        current: Pair<Int, Int>,
        nodes: MutableList<GraphNodeComponent>,
        occupied: MutableSet<Pair<Int, Int>>,
        candidates: MutableList<Pair<Int, Int>>,
        startIndex: Int
    ): Int {
        var nodeIndex = startIndex
        val neighbors = cardinalNeighbors(current).filter { it !in occupied }
        val takeCount = (neighbors.size * layout.density).toInt().coerceAtLeast(1)
        for (neighbor in neighbors.shuffled(rng).take(takeCount)) {
            if (nodes.size >= layout.nodeCount) break
            occupied.add(neighbor)
            nodes.add(floodNode(chunkId, nodeIndex, neighbor))
            nodeIndex++
            candidates.add(neighbor)
        }
        return nodeIndex
    }

    private fun cardinalNeighbors(current: Pair<Int, Int>): List<Pair<Int, Int>> {
        return listOf(
            current.first + 1 to current.second,
            current.first - 1 to current.second,
            current.first to current.second + 1,
            current.first to current.second - 1
        )
    }
}

/** BSP room representation (layout extract). */
internal data class BSPRoom(val x: Int, val y: Int, val width: Int, val height: Int)
