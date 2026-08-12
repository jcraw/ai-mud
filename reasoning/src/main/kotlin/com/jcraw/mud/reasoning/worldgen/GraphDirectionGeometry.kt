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
import com.jcraw.mud.core.world.EdgeData
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Direction geometry helpers for graph edge labeling.
 * Pure extract from GraphGenerator (MUD-034b) — no algorithm change.
 */
internal object GraphDirectionGeometry {

    val PRIMARY_DIRECTIONS = listOf(
        DirectionBucket("east", 0.0),
        DirectionBucket("northeast", 45.0),
        DirectionBucket("north", 90.0),
        DirectionBucket("northwest", 135.0),
        DirectionBucket("west", 180.0),
        DirectionBucket("southwest", 225.0),
        DirectionBucket("south", 270.0),
        DirectionBucket("southeast", 315.0)
    )

    val FALLBACK_DIRECTIONS = listOf(
        "up", "down", "inward", "outward", "ascent", "descent", "forward", "back"
    )

    val OPPOSITES = mapOf(
        "north" to "south", "south" to "north",
        "east" to "west", "west" to "east",
        "northeast" to "southwest", "southwest" to "northeast",
        "northwest" to "southeast", "southeast" to "northwest",
        "up" to "down", "down" to "up",
        "inward" to "outward", "outward" to "inward",
        "ascent" to "descent", "descent" to "ascent",
        "forward" to "back", "back" to "forward"
    )

    fun calculateDirection(from: GraphNodeComponent, to: GraphNodeComponent): String {
        val (angle, _) = calculateAngleAndDistance(from, to)
        if (angle != null) {
            return baseDirectionForAngle(angle)
        }
        return cardinalFromPositions(from.position, to.position)
    }

    fun cardinalFromPositions(
        fromPos: Pair<Int, Int>?,
        toPos: Pair<Int, Int>?
    ): String {
        if (fromPos == null || toPos == null) return "passage"
        val dx = toPos.first - fromPos.first
        val dy = toPos.second - fromPos.second
        return cardinalFromDelta(dx, dy)
    }

    private fun cardinalFromDelta(dx: Int, dy: Int): String {
        if (dy == 0 && dx > 0) return "east"
        if (dy == 0 && dx < 0) return "west"
        if (dx == 0 && dy > 0) return "south"
        if (dx == 0 && dy < 0) return "north"
        return diagonalFromDelta(dx, dy)
    }

    private fun diagonalFromDelta(dx: Int, dy: Int): String {
        if (dx > 0 && dy > 0) return "southeast"
        if (dx < 0 && dy > 0) return "southwest"
        if (dx > 0 && dy < 0) return "northeast"
        if (dx < 0 && dy < 0) return "northwest"
        return "passage"
    }

    fun calculateAngleAndDistance(
        from: GraphNodeComponent,
        to: GraphNodeComponent?
    ): Pair<Double?, Double> {
        val fromPos = from.position
        val toPos = to?.position
        if (fromPos == null || toPos == null) {
            return null to Double.POSITIVE_INFINITY
        }
        val dx = (toPos.first - fromPos.first).toDouble()
        val dy = (toPos.second - fromPos.second).toDouble()
        if (dx == 0.0 && dy == 0.0) {
            return null to 0.0
        }
        // Negate dy so positive angles point north (up) for readability
        val angle = Math.toDegrees(Math.atan2(-dy, dx))
        val normalizedAngle = (angle + 360.0) % 360.0
        val distance = sqrt(dx * dx + dy * dy)
        return normalizedAngle to distance
    }

    fun baseDirectionForAngle(angle: Double): String {
        return PRIMARY_DIRECTIONS.minByOrNull { angularDistance(angle, it.angle) }?.name
            ?: "passage"
    }

    fun getBestDirectionForAngle(angle: Double): String {
        return PRIMARY_DIRECTIONS.minByOrNull { angularDistance(angle, it.angle) }?.name
            ?: "passage-1"
    }

    fun degreesToGameRadians(angleDegrees: Double?): Double? {
        // Convert: 0°=E, 90°=N → 0=E, 3π/2=N, π/2=S
        return angleDegrees?.let { degrees ->
            val radians = Math.toRadians(degrees)
            val flipped = -radians
            (flipped + 2 * Math.PI) % (2 * Math.PI)
        }
    }

    fun pickDirectionForAngle(angle: Double, used: Set<String>): String {
        val ordered = PRIMARY_DIRECTIONS.sortedBy { angularDistance(angle, it.angle) }
        val candidate = ordered.firstOrNull { it.name !in used }
        return candidate?.name ?: pickFallbackDirection(used)
    }

    fun pickFallbackDirection(used: Set<String>): String {
        val pool = PRIMARY_DIRECTIONS.map { it.name } + FALLBACK_DIRECTIONS
        return pool.firstOrNull { it !in used } ?: "passage-${used.size + 1}"
    }

    fun angularDistance(a: Double, b: Double): Double {
        val diff = abs(a - b) % 360.0
        return if (diff > 180) 360.0 - diff else diff
    }

    fun makeEdgeData(
        targetId: String,
        direction: String,
        geometricAngle: Double?,
        fromPosition: Pair<Int, Int>?,
        toPosition: Pair<Int, Int>?
    ): EdgeData {
        return EdgeData(
            targetId = targetId,
            direction = direction,
            geometricAngle = geometricAngle,
            fromPosition = fromPosition,
            toPosition = toPosition
        )
    }

    fun sortedNeighborContexts(
        node: GraphNodeComponent,
        neighbors: List<EdgeData>,
        nodeMap: Map<String, GraphNodeComponent>
    ): List<NeighborContext> {
        return neighbors.map { edge ->
            val (angle, distance) = calculateAngleAndDistance(node, nodeMap[edge.targetId])
            NeighborContext(edge, angle, distance)
        }.sortedWith(
            compareBy<NeighborContext> { it.angle ?: Double.MAX_VALUE }
                .thenBy { it.distance }
        )
    }

    data class NeighborContext(
        val edge: EdgeData,
        val angle: Double?,
        val distance: Double
    )

    data class DirectionBucket(val name: String, val angle: Double)
}
