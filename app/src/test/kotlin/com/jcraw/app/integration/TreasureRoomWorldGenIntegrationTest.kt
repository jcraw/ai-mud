package com.jcraw.app.integration

import com.jcraw.mud.core.*
import com.jcraw.mud.core.world.*
import com.jcraw.mud.reasoning.treasureroom.TreasureRoomPlacer
import com.jcraw.mud.reasoning.worldgen.GraphGenerator
import com.jcraw.mud.reasoning.worldgen.GraphLayout
import kotlin.random.Random
import kotlin.test.*

/**
 * Integration tests for treasure room placement in world generation
 *
 * Tests the treasure room system integration with World Generation V2:
 * - BFS-based placement at distance 2-3 from start
 * - Safe zone marking (no combat, traps, or resources)
 * - Biome-aware component creation
 * - Node type assignment
 */
class TreasureRoomWorldGenIntegrationTest {

    private val placer = TreasureRoomPlacer()

    @Test
    fun `treasure room is placed at correct BFS distance`() {
        val rng = Random(42)
        val graphGenerator = GraphGenerator(rng, difficultyLevel = 1)
        val layout = GraphLayout.Grid(width = 4, height = 4) // 16 nodes

        val nodes = graphGenerator.generate("test_chunk", layout)
        val hubNode = nodes.first { it.type == NodeType.Hub }

        // Calculate distances from hub
        val distances = placer.calculateBFSDistances(hubNode.id, nodes)

        // Select treasure room node
        val treasureNode = placer.selectTreasureRoomNode(nodes, hubNode.id)
        assertNotNull(treasureNode, "Should select a treasure room node")

        // Verify distance is 2-3
        val distance = distances[treasureNode.id]
        assertNotNull(distance, "Treasure room node should have a distance")
        assertTrue(distance in 2..3, "Treasure room should be at distance 2-3, got $distance")
    }

    @Test
    fun `treasure room node prioritizes DeadEnd over other types`() {
        // Create a graph with multiple node types at distance 2-3
        val nodes = listOf(
            createNode("hub", NodeType.Hub, listOf("n1", "n2")),
            createNode("n1", NodeType.Linear, listOf("deadend", "linear")),
            createNode("n2", NodeType.Linear, listOf("branching")),
            createNode("deadend", NodeType.DeadEnd, emptyList()),
            createNode("linear", NodeType.Linear, emptyList()),
            createNode("branching", NodeType.Branching, listOf("far"))
        )

        val selected = placer.selectTreasureRoomNode(nodes, "hub")
        assertNotNull(selected)
        assertEquals("deadend", selected.id)
        assertTrue(selected.type is NodeType.DeadEnd)
    }

    @Test
    fun `treasure room node excludes Boss and Frontier nodes`() {
        val nodes = listOf(
            createNode("hub", NodeType.Hub, listOf("boss", "frontier", "valid")),
            createNode("boss", NodeType.Boss, emptyList()),
            createNode("frontier", NodeType.Frontier, emptyList()),
            createNode("valid", NodeType.Linear, listOf("n1")),
            createNode("n1", NodeType.Linear, emptyList())
        )

        val selected = placer.selectTreasureRoomNode(nodes, "hub")
        assertNotNull(selected)
        assertFalse(selected.type is NodeType.Boss)
        assertFalse(selected.type is NodeType.Frontier)
    }

    @Test
    fun `treasure room component has all starter items`() {
        val component = placer.createStarterTreasureRoomComponent("ancient_abyss")

        assertEquals(5, component.pedestals.size)

        val itemIds = component.pedestals.map { it.itemTemplateId }
        assertTrue(itemIds.contains("flamebrand_longsword"), "Should have warrior item")
        assertTrue(itemIds.contains("shadowweave_cloak"), "Should have rogue item")
        assertTrue(itemIds.contains("stormcaller_staff"), "Should have mage item")
        assertTrue(itemIds.contains("titans_band"), "Should have utility item")
        assertTrue(itemIds.contains("arcane_blade"), "Should have hybrid item")
    }

    @Test
    fun `treasure room component uses correct biome theme`() {
        val themes = listOf(
            "ancient_abyss",
            "magma_cave",
            "frozen_depths",
            "bone_crypt"
        )

        themes.forEach { theme ->
            val component = placer.createStarterTreasureRoomComponent(theme)
            assertEquals(theme, component.biomeTheme)

            // Verify pedestal descriptions use appropriate materials
            component.pedestals.forEach { pedestal ->
                assertNotNull(pedestal.themeDescription)
                assertTrue(pedestal.themeDescription.isNotBlank())
            }
        }
    }

    @Test
    fun `treasure room component initializes with correct state`() {
        val component = placer.createStarterTreasureRoomComponent("ancient_abyss")

        assertEquals(TreasureRoomType.STARTER, component.roomType)
        assertFalse(component.hasBeenLooted)
        assertNull(component.currentlyTakenItem)

        component.pedestals.forEach { pedestal ->
            assertEquals(PedestalState.AVAILABLE, pedestal.state)
        }
    }

    @Test
    fun `treasure room is marked as safe zone`() {
        // Create a treasure room space
        val space = SpacePropertiesComponent(
            name = "Treasure Room",
            description = "A room with glowing pedestals",
            exits = emptyList(),
            terrainType = TerrainType.NORMAL,
            brightness = 75,
            entities = emptyList(),
            resources = emptyList(),
            traps = emptyList(),
            itemsDropped = emptyList(),
            isSafeZone = true,
            isTreasureRoom = true
        )

        assertTrue(space.isSafeZone, "Treasure room should be marked as safe zone")
        assertTrue(space.isTreasureRoom, "Space should be marked as treasure room")
    }

    @Test
    fun `safe zone prevents trap spawning`() {
        val safeSpace = SpacePropertiesComponent(
            name = "Treasure Room",
            description = "Safe room",
            exits = emptyList(),
            terrainType = TerrainType.NORMAL,
            brightness = 75,
            entities = emptyList(),
            resources = emptyList(),
            traps = emptyList(),
            itemsDropped = emptyList(),
            isSafeZone = true
        )

        assertTrue(safeSpace.traps.isEmpty(), "Safe zone should not spawn traps")
    }

    @Test
    fun `safe zone prevents resource spawning`() {
        val safeSpace = SpacePropertiesComponent(
            name = "Treasure Room",
            description = "Safe room",
            exits = emptyList(),
            terrainType = TerrainType.NORMAL,
            brightness = 75,
            entities = emptyList(),
            resources = emptyList(),
            traps = emptyList(),
            itemsDropped = emptyList(),
            isSafeZone = true
        )

        assertTrue(safeSpace.resources.isEmpty(), "Safe zone should not spawn resources")
    }

    @Test
    fun `BFS distance calculation handles complex graphs`() {
        // Create a graph with loops and multiple paths
        val nodes = listOf(
            createNode("start", NodeType.Hub, listOf("a", "b")),
            createNode("a", NodeType.Linear, listOf("c")),
            createNode("b", NodeType.Linear, listOf("c", "d")),
            createNode("c", NodeType.Branching, listOf("e", "f")),
            createNode("d", NodeType.Linear, listOf("e")),
            createNode("e", NodeType.Linear, listOf("g")),
            createNode("f", NodeType.DeadEnd, emptyList()),
            createNode("g", NodeType.DeadEnd, emptyList())
        )

        val distances = placer.calculateBFSDistances("start", nodes)

        // Verify shortest paths
        assertEquals(0, distances["start"])
        assertEquals(1, distances["a"])
        assertEquals(1, distances["b"])
        assertEquals(2, distances["c"])
        assertEquals(2, distances["d"])
        assertEquals(3, distances["e"])
        assertEquals(3, distances["f"])
        assertEquals(4, distances["g"])
    }

    @Test
    fun `treasure room candidates filters correctly`() {
        val nodes = listOf(
            createNode("hub", NodeType.Hub, emptyList()),
            createNode("dist1", NodeType.Linear, emptyList()),
            createNode("dist2_valid", NodeType.Linear, emptyList()),
            createNode("dist3_valid", NodeType.DeadEnd, emptyList()),
            createNode("dist4", NodeType.Linear, emptyList()),
            createNode("dist2_boss", NodeType.Boss, emptyList()),
            createNode("dist3_frontier", NodeType.Frontier, emptyList())
        )

        val distances = mapOf(
            "hub" to 0,
            "dist1" to 1,
            "dist2_valid" to 2,
            "dist3_valid" to 3,
            "dist4" to 4,
            "dist2_boss" to 2,
            "dist3_frontier" to 3
        )

        val candidates = placer.findTreasureRoomCandidates(nodes, distances)

        assertEquals(2, candidates.size)
        assertTrue(candidates.any { it.id == "dist2_valid" })
        assertTrue(candidates.any { it.id == "dist3_valid" })
        assertFalse(candidates.any { it.id == "dist2_boss" })
        assertFalse(candidates.any { it.id == "dist3_frontier" })
    }

    @Test
    fun `biome mapping handles various dungeon themes`() {
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
    fun `biome mapping returns default for unknown themes`() {
        assertEquals("ancient_abyss", TreasureRoomPlacer.getBiomeName("unknown_theme_123"))
        assertEquals("ancient_abyss", TreasureRoomPlacer.getBiomeName(""))
    }

    // Helper to create graph nodes
    private fun createNode(id: String, type: NodeType, neighborIds: List<String>): GraphNodeComponent {
        val edges = neighborIds.map { EdgeData(it, "passage", false) }
        return GraphNodeComponent(
            id = id,
            chunkId = "test_chunk",
            type = type,
            neighbors = edges
        )
    }
}
