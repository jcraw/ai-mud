@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package com.jcraw.mud.core.world

import com.jcraw.mud.core.GraphNodeComponent
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * MUD-015 property tests for [GraphNodeComponent] pure invariants.
 *
 * Laws (hard fail): G1–G4 + removeEdge present/throw (S2 contract).
 * Soft: no distribution/variance checks; see docs/PBT.md.
 *
 * Coexists with example suite [GraphNodeComponentTest] — does not weaken it.
 */
class GraphNodeComponentPropertyTest {

    @Test
    fun `G1 law - degree equals neighbors size`() {
        runBlocking {
            checkAll(config, arbAnyNode()) { node ->
                assertEquals(node.neighbors.size, node.degree())
            }
        }
    }

    @Test
    fun `G2 law - successful addEdge increases degree and leaves original unchanged`() {
        runBlocking {
            checkAll(config, arbNodeWithRoomForEdge()) { node ->
                val usedDirs = node.neighbors.map { it.direction }.toSet()
                val newDir = DIRECTIONS.first { it !in usedDirs }
                val edge = EdgeData(targetId = "fresh-target", direction = newDir)
                val beforeDegree = node.degree()
                val beforeNeighbors = node.neighbors

                val updated = node.addEdge(edge)

                assertEquals(beforeDegree + 1, updated.degree())
                assertEquals(beforeDegree, node.degree())
                assertEquals(beforeNeighbors, node.neighbors)
                assertTrue(updated.neighbors.contains(edge))
            }
        }
    }

    @Test
    fun `G3 law - well-formed construction validates true`() {
        runBlocking {
            checkAll(config, arbWellFormedNode()) { node ->
                assertTrue(node.validate(), "expected validate() for $node")
            }
        }
    }

    @Test
    fun `G4 law - type degree rule violations validate false`() {
        runBlocking {
            checkAll(config, arbDegreeRuleViolation()) { node ->
                assertFalse(
                    node.validate(),
                    "expected invalidate for type=${node.type} degree=${node.degree()}"
                )
            }
        }
    }

    @Test
    fun `S2 law - removeEdge present decreases degree and missing throws`() {
        runBlocking {
            checkAll(config, arbNodeWithAtLeastOneEdge()) { node ->
                val targetId = node.neighbors.first().targetId
                val edgesToTarget = node.neighbors.count { it.targetId == targetId }
                val before = node.degree()

                val updated = node.removeEdge(targetId)
                assertEquals(before - edgesToTarget, updated.degree())
                assertEquals(before, node.degree()) // immutability
                assertTrue(updated.neighbors.none { it.targetId == targetId })
            }

            checkAll(config, arbAnyNode(), arbNonBlankId()) { node, missingId ->
                // Ensure target is actually absent
                if (node.neighbors.none { it.targetId == missingId }) {
                    assertFailsWith<IllegalArgumentException> {
                        node.removeEdge(missingId)
                    }
                }
            }
        }
    }

    companion object {
        /** Fixed seed for MUD-015 — reproducible CI (see docs/PBT.md). */
        const val PBT_SEED = 15_015L
        const val PBT_ITERATIONS = 100

        private val config = PropTestConfig(seed = PBT_SEED, iterations = PBT_ITERATIONS)

        private val DIRECTIONS = listOf(
            "north", "south", "east", "west", "up", "down",
            "northeast", "northwest", "southeast", "southwest", "in", "out"
        )

        private val ALL_NODE_TYPES = listOf(
            NodeType.Hub,
            NodeType.Linear,
            NodeType.Branching,
            NodeType.DeadEnd,
            NodeType.TreasureRoom,
            NodeType.Boss,
            NodeType.Frontier,
            NodeType.Questable
        )

        private fun arbNonBlankId(): Arb<String> =
            Arb.string(minSize = 1, maxSize = 12, codepoints = Codepoint.alphanumeric())

        /** Unique (targetId, direction) edges via distinct direction slots. */
        private fun arbUniqueEdges(min: Int, max: Int): Arb<List<EdgeData>> {
            val hi = max.coerceAtMost(DIRECTIONS.size)
            val lo = min.coerceIn(0, hi)
            return Arb.int(lo..hi).flatMap { count ->
                Arb.list(arbNonBlankId(), count..count).map { targets ->
                    targets.mapIndexed { i, targetId ->
                        EdgeData(targetId = targetId, direction = DIRECTIONS[i])
                    }
                }
            }
        }

        private fun arbAnyNode(): Arb<GraphNodeComponent> =
            Arb.element(ALL_NODE_TYPES).flatMap { type ->
                arbNonBlankId().flatMap { id ->
                    arbNonBlankId().flatMap { chunkId ->
                        arbUniqueEdges(0, 8).map { edges ->
                            GraphNodeComponent(
                                id = id,
                                type = type,
                                neighbors = edges,
                                chunkId = chunkId
                            )
                        }
                    }
                }
            }

        /** Leaves at least one free direction for successful [GraphNodeComponent.addEdge]. */
        private fun arbNodeWithRoomForEdge(): Arb<GraphNodeComponent> =
            Arb.element(ALL_NODE_TYPES).flatMap { type ->
                arbNonBlankId().flatMap { id ->
                    arbNonBlankId().flatMap { chunkId ->
                        arbUniqueEdges(0, DIRECTIONS.size - 1).map { edges ->
                            GraphNodeComponent(
                                id = id,
                                type = type,
                                neighbors = edges,
                                chunkId = chunkId
                            )
                        }
                    }
                }
            }

        private fun arbNodeWithAtLeastOneEdge(): Arb<GraphNodeComponent> =
            Arb.element(ALL_NODE_TYPES).flatMap { type ->
                arbNonBlankId().flatMap { id ->
                    arbNonBlankId().flatMap { chunkId ->
                        arbUniqueEdges(1, 6).map { edges ->
                            GraphNodeComponent(
                                id = id,
                                type = type,
                                neighbors = edges,
                                chunkId = chunkId
                            )
                        }
                    }
                }
            }

        /**
         * Non-blank id/chunk, no duplicate edges, type-consistent degree.
         * DeadEnd deg=1, Linear deg=2, Hub deg∈[3,8], free types any deg∈[0,8].
         */
        private fun arbWellFormedNode(): Arb<GraphNodeComponent> =
            Arb.element(ALL_NODE_TYPES).flatMap { type ->
                val degreeArb = when (type) {
                    is NodeType.DeadEnd -> Arb.int(1..1)
                    is NodeType.Linear -> Arb.int(2..2)
                    is NodeType.Hub -> Arb.int(3..8)
                    else -> Arb.int(0..8)
                }
                degreeArb.flatMap { degree ->
                    arbNonBlankId().flatMap { id ->
                        arbNonBlankId().flatMap { chunkId ->
                            arbUniqueEdges(degree, degree).map { edges ->
                                GraphNodeComponent(
                                    id = id,
                                    type = type,
                                    neighbors = edges,
                                    chunkId = chunkId
                                )
                            }
                        }
                    }
                }
            }

        /**
         * Typed generators that violate DeadEnd/Linear/Hub degree rules only.
         * (G4 — blank id/chunk and dups covered by example tests.)
         */
        private fun arbDegreeRuleViolation(): Arb<GraphNodeComponent> {
            data class Violation(val type: NodeType, val degree: Int)

            val cases = listOf(
                // DeadEnd must be exactly 1
                Violation(NodeType.DeadEnd, 0),
                Violation(NodeType.DeadEnd, 2),
                Violation(NodeType.DeadEnd, 3),
                Violation(NodeType.DeadEnd, 5),
                // Linear must be exactly 2
                Violation(NodeType.Linear, 0),
                Violation(NodeType.Linear, 1),
                Violation(NodeType.Linear, 3),
                Violation(NodeType.Linear, 4),
                // Hub must be >= 3
                Violation(NodeType.Hub, 0),
                Violation(NodeType.Hub, 1),
                Violation(NodeType.Hub, 2)
            )

            return Arb.element(cases).flatMap { case ->
                arbNonBlankId().flatMap { id ->
                    arbNonBlankId().flatMap { chunkId ->
                        arbUniqueEdges(case.degree, case.degree).map { edges ->
                            GraphNodeComponent(
                                id = id,
                                type = case.type,
                                neighbors = edges,
                                chunkId = chunkId
                            )
                        }
                    }
                }
            }
        }
    }
}
