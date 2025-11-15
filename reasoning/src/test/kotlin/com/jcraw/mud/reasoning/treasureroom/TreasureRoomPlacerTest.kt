package com.jcraw.mud.reasoning.treasureroom

import com.jcraw.mud.core.GraphNodeComponent
import com.jcraw.mud.core.PedestalState
import com.jcraw.mud.core.TreasureRoomType
import com.jcraw.mud.core.world.EdgeData
import com.jcraw.mud.core.world.NodeType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.random.Random

class TreasureRoomPlacerTest {

    private val placer = TreasureRoomPlacer()

    // shouldPlaceTreasureRoom tests
    @Test
    fun `shouldPlaceTreasureRoom returns false for position 0`() {
        val result = placer.shouldPlaceTreasureRoom(
            positionIndex = 0,
            totalPositions = 10,
            alreadyPlaced = false,
            random = Random(42)
        )
        assertFalse(result)
    }

    @Test
    fun `shouldPlaceTreasureRoom returns false if already placed`() {
        val result = placer.shouldPlaceTreasureRoom(
            positionIndex = 1,
            totalPositions = 10,
            alreadyPlaced = true,
            random = Random(42)
        )
        assertFalse(result)
    }

    @Test
    fun `shouldPlaceTreasureRoom returns false for late positions`() {
        val result = placer.shouldPlaceTreasureRoom(
            positionIndex = 5,
            totalPositions = 10,
            alreadyPlaced = false,
            random = Random(42)
        )
        assertFalse(result)
    }

    @Test
    fun `shouldPlaceTreasureRoom uses probability for positions 1-2`() {
        // Test position 1 with low probability (50%)
        var placed = 0
        repeat(100) { seed ->
            if (placer.shouldPlaceTreasureRoom(1, 10, false, Random(seed))) {
                placed++
            }
        }
        // Should be roughly 40-60% placed
        assertTrue(placed in 30..70, "Expected ~50% placement, got $placed/100")

        // Test position 2 with high probability (75%)
        placed = 0
        repeat(100) { seed ->
            if (placer.shouldPlaceTreasureRoom(2, 10, false, Random(seed))) {
                placed++
            }
        }
        // Should be roughly 65-85% placed
        assertTrue(placed in 60..90, "Expected ~75% placement, got $placed/100")
    }

    // selectTreasureRoomNode tests
    @Test
    fun `selectTreasureRoomNode returns null for empty list`() {
        val result = placer.selectTreasureRoomNode(emptyList(), "start")
        assertNull(result)
    }

    @Test
    fun `selectTreasureRoomNode selects DeadEnd at distance 2-3`() {
        val nodes = listOf(
            createNode("start", NodeType.Hub, listOf("n1", "n2")),
            createNode("n1", NodeType.Linear, listOf("n2", "n3")),
            createNode("n2", NodeType.Linear, listOf("n3")),
            createNode("n3", NodeType.DeadEnd, emptyList()),
            createNode("n4", NodeType.Branching, listOf("n5"))
        )

        val result = placer.selectTreasureRoomNode(nodes, "start")
        assertNotNull(result)
        assertEquals("n3", result!!.id)
        assertTrue(result.type is NodeType.DeadEnd)
    }

    @Test
    fun `selectTreasureRoomNode prioritizes DeadEnd over Linear`() {
        val nodes = listOf(
            createNode("start", NodeType.Hub, listOf("n1", "n2")),
            createNode("n1", NodeType.Linear, listOf("n3")),
            createNode("n2", NodeType.Linear, listOf("n4")),
            createNode("n3", NodeType.Linear, emptyList()),
            createNode("n4", NodeType.DeadEnd, emptyList())
        )

        val result = placer.selectTreasureRoomNode(nodes, "start")
        assertNotNull(result)
        assertEquals("n4", result!!.id)
    }

    @Test
    fun `selectTreasureRoomNode excludes Boss and Frontier nodes`() {
        val nodes = listOf(
            createNode("start", NodeType.Hub, listOf("boss", "frontier")),
            createNode("boss", NodeType.Boss, emptyList()),
            createNode("frontier", NodeType.Frontier, emptyList()),
            createNode("valid", NodeType.Linear, emptyList())
        )

        val result = placer.selectTreasureRoomNode(nodes, "start")
        assertNotNull(result)
        assertEquals("valid", result!!.id)
    }

    @Test
    fun `selectTreasureRoomNode returns fallback when no ideal candidates`() {
        val nodes = listOf(
            createNode("start", NodeType.Hub, listOf("nearby")),
            createNode("nearby", NodeType.Linear, emptyList())
        )

        val result = placer.selectTreasureRoomNode(nodes, "start")
        assertNotNull(result)
        assertEquals("nearby", result!!.id)
    }

    // createStarterTreasureRoomComponent tests
    @Test
    fun `createStarterTreasureRoomComponent creates 5 pedestals`() {
        val component = placer.createStarterTreasureRoomComponent("ancient_abyss")
        assertEquals(5, component.pedestals.size)
    }

    @Test
    fun `createStarterTreasureRoomComponent has correct item templates`() {
        val component = placer.createStarterTreasureRoomComponent("ancient_abyss")
        val templateIds = component.pedestals.map { it.itemTemplateId }

        assertTrue("flamebrand_longsword" in templateIds)
        assertTrue("shadowweave_cloak" in templateIds)
        assertTrue("stormcaller_staff" in templateIds)
        assertTrue("titans_band" in templateIds)
        assertTrue("arcane_blade" in templateIds)
    }

    @Test
    fun `createStarterTreasureRoomComponent all pedestals available`() {
        val component = placer.createStarterTreasureRoomComponent("ancient_abyss")
        component.pedestals.forEach { pedestal ->
            assertEquals(PedestalState.AVAILABLE, pedestal.state)
        }
    }

    @Test
    fun `createStarterTreasureRoomComponent sets biome theme`() {
        val component = placer.createStarterTreasureRoomComponent("magma_cave")
        assertEquals("magma_cave", component.biomeTheme)
    }

    @Test
    fun `createStarterTreasureRoomComponent is STARTER type`() {
        val component = placer.createStarterTreasureRoomComponent("ancient_abyss")
        assertEquals(TreasureRoomType.STARTER, component.roomType)
    }

    @Test
    fun `createStarterTreasureRoomComponent not looted initially`() {
        val component = placer.createStarterTreasureRoomComponent("ancient_abyss")
        assertFalse(component.hasBeenLooted)
        assertNull(component.currentlyTakenItem)
    }

    // calculateBFSDistances tests
    @Test
    fun `calculateBFSDistances assigns distance 0 to start node`() {
        val nodes = listOf(createNode("start", NodeType.Hub, emptyList()))
        val distances = placer.calculateBFSDistances("start", nodes)

        assertEquals(0, distances["start"])
    }

    @Test
    fun `calculateBFSDistances calculates correct distances`() {
        val nodes = listOf(
            createNode("start", NodeType.Hub, listOf("n1", "n2")),
            createNode("n1", NodeType.Linear, listOf("n3")),
            createNode("n2", NodeType.Linear, listOf("n3")),
            createNode("n3", NodeType.Linear, listOf("n4")),
            createNode("n4", NodeType.DeadEnd, emptyList())
        )

        val distances = placer.calculateBFSDistances("start", nodes)

        assertEquals(0, distances["start"])
        assertEquals(1, distances["n1"])
        assertEquals(1, distances["n2"])
        assertEquals(2, distances["n3"])
        assertEquals(3, distances["n4"])
    }

    @Test
    fun `calculateBFSDistances handles disconnected nodes`() {
        val nodes = listOf(
            createNode("start", NodeType.Hub, listOf("n1")),
            createNode("n1", NodeType.Linear, emptyList()),
            createNode("n2", NodeType.Linear, emptyList()) // Disconnected
        )

        val distances = placer.calculateBFSDistances("start", nodes)

        assertEquals(0, distances["start"])
        assertEquals(1, distances["n1"])
        assertNull(distances["n2"]) // Unreachable
    }

    // findTreasureRoomCandidates tests
    @Test
    fun `findTreasureRoomCandidates selects nodes at distance 2-3`() {
        val nodes = listOf(
            createNode("start", NodeType.Hub, emptyList()),
            createNode("n1", NodeType.Linear, emptyList()),
            createNode("n2", NodeType.Linear, emptyList()),
            createNode("n3", NodeType.Linear, emptyList()),
            createNode("n4", NodeType.Linear, emptyList())
        )
        val distances = mapOf("start" to 0, "n1" to 1, "n2" to 2, "n3" to 3, "n4" to 4)

        val candidates = placer.findTreasureRoomCandidates(nodes, distances)

        assertEquals(2, candidates.size)
        assertTrue(candidates.any { it.id == "n2" })
        assertTrue(candidates.any { it.id == "n3" })
    }

    @Test
    fun `findTreasureRoomCandidates excludes Boss nodes`() {
        val nodes = listOf(
            createNode("n2", NodeType.Boss, emptyList()),
            createNode("n3", NodeType.Linear, emptyList())
        )
        val distances = mapOf("n2" to 2, "n3" to 3)

        val candidates = placer.findTreasureRoomCandidates(nodes, distances)

        assertEquals(1, candidates.size)
        assertEquals("n3", candidates.first().id)
    }

    @Test
    fun `findTreasureRoomCandidates excludes Frontier nodes`() {
        val nodes = listOf(
            createNode("n2", NodeType.Frontier, emptyList()),
            createNode("n3", NodeType.Linear, emptyList())
        )
        val distances = mapOf("n2" to 2, "n3" to 3)

        val candidates = placer.findTreasureRoomCandidates(nodes, distances)

        assertEquals(1, candidates.size)
        assertEquals("n3", candidates.first().id)
    }

    @Test
    fun `findTreasureRoomCandidates excludes Hub nodes`() {
        val nodes = listOf(
            createNode("n2", NodeType.Hub, emptyList()),
            createNode("n3", NodeType.Linear, emptyList())
        )
        val distances = mapOf("n2" to 2, "n3" to 3)

        val candidates = placer.findTreasureRoomCandidates(nodes, distances)

        assertEquals(1, candidates.size)
        assertEquals("n3", candidates.first().id)
    }

    // getBiomeName tests
    @Test
    fun `getBiomeName maps known themes correctly`() {
        assertEquals("ancient_abyss", TreasureRoomPlacer.getBiomeName("ancient_abyss"))
        assertEquals("ancient_abyss", TreasureRoomPlacer.getBiomeName("abyssal_dungeon"))
        assertEquals("magma_cave", TreasureRoomPlacer.getBiomeName("magma_cave"))
        assertEquals("magma_cave", TreasureRoomPlacer.getBiomeName("volcanic_depths"))
        assertEquals("frozen_depths", TreasureRoomPlacer.getBiomeName("frozen_depths"))
        assertEquals("frozen_depths", TreasureRoomPlacer.getBiomeName("ice_caverns"))
        assertEquals("bone_crypt", TreasureRoomPlacer.getBiomeName("bone_crypt"))
        assertEquals("bone_crypt", TreasureRoomPlacer.getBiomeName("undead_tomb"))
    }

    @Test
    fun `getBiomeName returns default for unknown theme`() {
        assertEquals("ancient_abyss", TreasureRoomPlacer.getBiomeName("unknown_theme"))
    }

    @Test
    fun `getBiomeName is case insensitive`() {
        assertEquals("magma_cave", TreasureRoomPlacer.getBiomeName("MAGMA_CAVE"))
        assertEquals("frozen_depths", TreasureRoomPlacer.getBiomeName("Frozen_Depths"))
    }

    // Helper to create graph nodes
    private fun createNode(id: String, type: NodeType, neighborIds: List<String>): GraphNodeComponent {
        val edges = neighborIds.map { EdgeData(it, "passage", false) }
        return GraphNodeComponent(
            id = id,
            chunkId = "chunk_1",
            type = type,
            neighbors = edges
        )
    }
}
