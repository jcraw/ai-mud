package com.jcraw.mud.memory.world

import com.jcraw.mud.core.GraphNodeComponent
import com.jcraw.mud.core.WorldChunkComponent
import com.jcraw.mud.core.world.ChunkLevel
import com.jcraw.mud.core.world.Condition
import com.jcraw.mud.core.world.EdgeData
import com.jcraw.mud.core.world.NodeType
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.io.File

/**
 * Integration tests for SQLiteGraphNodeRepository
 * Tests CRUD operations, JSON serialization, edge manipulation, and error handling
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SQLiteGraphNodeRepositoryTest {
    private lateinit var database: WorldDatabase
    private lateinit var repository: SQLiteGraphNodeRepository
    private lateinit var chunkRepository: SQLiteWorldChunkRepository
    private val testDbPath = "test_graph_nodes.db"

    @BeforeAll
    fun setup() {
        database = WorldDatabase(testDbPath)
        repository = SQLiteGraphNodeRepository(database)
        chunkRepository = SQLiteWorldChunkRepository(database)
    }

    @AfterAll
    fun cleanup() {
        database.close()
        File(testDbPath).delete()
    }

    @BeforeEach
    fun resetDatabase() {
        database.clearAll()
        // Create test chunks for foreign key constraint
        createTestChunk("chunk1")
        createTestChunk("chunk2")
    }

    private fun createTestChunk(id: String) {
        val chunk = WorldChunkComponent(
            level = ChunkLevel.SUBZONE,
            parentId = null,
            children = emptyList(),
            lore = "Test chunk",
            biomeTheme = "Test",
            sizeEstimate = 10,
            mobDensity = 0.0,
            difficultyLevel = 1
        )
        chunkRepository.save(chunk, id).getOrThrow()
    }

    // === Save/Load Tests ===

    @Test
    fun `save and find node by ID`() {
        val node = GraphNodeComponent(
            id = "node1",
            chunkId = "chunk1",
            position = Pair(5, 10),
            type = NodeType.Hub,
            neighbors = listOf(
                EdgeData(targetId = "node2", direction = "north"),
                EdgeData(targetId = "node3", direction = "south")
            )
        )

        repository.save(node).getOrThrow()
        val loaded = repository.findById("node1").getOrThrow()

        assertNotNull(loaded)
        assertEquals(node.id, loaded?.id)
        assertEquals(node.chunkId, loaded?.chunkId)
        assertEquals(node.position, loaded?.position)
        assertEquals(node.type, loaded?.type)
        assertEquals(2, loaded?.neighbors?.size)
    }

    @Test
    fun `find nonexistent node returns null`() {
        val loaded = repository.findById("nonexistent").getOrThrow()
        assertNull(loaded)
    }

    @Test
    fun `save node with null position`() {
        val node = GraphNodeComponent(
            id = "node1",
            chunkId = "chunk1",
            position = null,
            type = NodeType.Linear,
            neighbors = emptyList()
        )

        repository.save(node).getOrThrow()
        val loaded = repository.findById("node1").getOrThrow()

        assertNotNull(loaded)
        assertNull(loaded?.position)
    }

    @Test
    fun `save node with all NodeType variants`() {
        val nodeTypes = listOf(
            NodeType.Hub,
            NodeType.Linear,
            NodeType.Branching,
            NodeType.DeadEnd,
            NodeType.Boss,
            NodeType.Frontier,
            NodeType.Questable
        )

        nodeTypes.forEachIndexed { index, type ->
            val node = GraphNodeComponent(
                id = "node_$index",
                chunkId = "chunk1",
                position = Pair(index, index),
                type = type,
                neighbors = emptyList()
            )

            repository.save(node).getOrThrow()
            val loaded = repository.findById("node_$index").getOrThrow()

            assertEquals(type, loaded?.type)
        }
    }

    @Test
    fun `update node overwrites existing`() {
        val original = GraphNodeComponent(
            id = "node1",
            chunkId = "chunk1",
            position = Pair(0, 0),
            type = NodeType.Linear,
            neighbors = emptyList()
        )

        repository.save(original).getOrThrow()

        val updated = original.copy(
            type = NodeType.Hub,
            neighbors = listOf(EdgeData(targetId = "node2", direction = "north"))
        )
        repository.save(updated).getOrThrow()

        val loaded = repository.findById("node1").getOrThrow()
        assertEquals(NodeType.Hub, loaded?.type)
        assertEquals(1, loaded?.neighbors?.size)
    }

    // === JSON Serialization Tests ===

    @Test
    fun `neighbors list serializes correctly`() {
        val node = GraphNodeComponent(
            id = "node1",
            chunkId = "chunk1",
            position = Pair(0, 0),
            type = NodeType.Branching,
            neighbors = listOf(
                EdgeData(targetId = "node2", direction = "north"),
                EdgeData(targetId = "node3", direction = "south"),
                EdgeData(targetId = "node4", direction = "east", hidden = true)
            )
        )

        repository.save(node).getOrThrow()
        val loaded = repository.findById("node1").getOrThrow()

        assertEquals(3, loaded?.neighbors?.size)
        assertTrue(loaded?.neighbors?.any { it.targetId == "node2" && it.direction == "north" } == true)
        assertTrue(loaded?.neighbors?.any { it.targetId == "node3" && it.direction == "south" } == true)
        assertTrue(loaded?.neighbors?.any { it.targetId == "node4" && it.hidden } == true)
    }

    @Test
    fun `empty neighbors list persists correctly`() {
        val node = GraphNodeComponent(
            id = "node1",
            chunkId = "chunk1",
            position = Pair(0, 0),
            type = NodeType.DeadEnd,
            neighbors = emptyList()
        )

        repository.save(node).getOrThrow()
        val loaded = repository.findById("node1").getOrThrow()

        assertNotNull(loaded)
        assertTrue(loaded?.neighbors?.isEmpty() == true)
    }

    @Test
    fun `edge with conditions serializes correctly`() {
        val node = GraphNodeComponent(
            id = "node1",
            chunkId = "chunk1",
            position = Pair(0, 0),
            type = NodeType.Hub,
            neighbors = listOf(
                EdgeData(
                    targetId = "node2",
                    direction = "hidden passage",
                    hidden = true,
                    conditions = listOf(Condition.SkillCheck("Perception", 15))
                )
            )
        )

        repository.save(node).getOrThrow()
        val loaded = repository.findById("node1").getOrThrow()

        val edge = loaded?.neighbors?.firstOrNull()
        assertNotNull(edge)
        assertTrue(edge?.hidden == true)
        assertEquals(1, edge?.conditions?.size)
        assertTrue(edge?.conditions?.first() is Condition.SkillCheck)
    }

    // === findByChunk Tests ===

    @Test
    fun `findByChunk returns all nodes in chunk`() {
        val node1 = GraphNodeComponent(
            id = "node1",
            chunkId = "chunk1",
            position = Pair(0, 0),
            type = NodeType.Hub,
            neighbors = emptyList()
        )

        val node2 = GraphNodeComponent(
            id = "node2",
            chunkId = "chunk1",
            position = Pair(1, 0),
            type = NodeType.Linear,
            neighbors = emptyList()
        )

        val node3 = GraphNodeComponent(
            id = "node3",
            chunkId = "chunk2",
            position = Pair(0, 0),
            type = NodeType.Boss,
            neighbors = emptyList()
        )

        repository.save(node1).getOrThrow()
        repository.save(node2).getOrThrow()
        repository.save(node3).getOrThrow()

        val chunk1Nodes = repository.findByChunk("chunk1").getOrThrow()
        assertEquals(2, chunk1Nodes.size)
        assertTrue(chunk1Nodes.any { it.id == "node1" })
        assertTrue(chunk1Nodes.any { it.id == "node2" })
        assertFalse(chunk1Nodes.any { it.id == "node3" })
    }

    @Test
    fun `findByChunk returns empty list for nonexistent chunk`() {
        val nodes = repository.findByChunk("nonexistent").getOrThrow()
        assertTrue(nodes.isEmpty())
    }

    // === Update Tests ===

    @Test
    fun `update existing node succeeds`() {
        val node = GraphNodeComponent(
            id = "node1",
            chunkId = "chunk1",
            position = Pair(0, 0),
            type = NodeType.Linear,
            neighbors = emptyList()
        )

        repository.save(node).getOrThrow()

        val updated = node.copy(type = NodeType.Branching)
        repository.update(updated).getOrThrow()

        val loaded = repository.findById("node1").getOrThrow()
        assertEquals(NodeType.Branching, loaded?.type)
    }

    @Test
    fun `update nonexistent node fails`() {
        val node = GraphNodeComponent(
            id = "nonexistent",
            chunkId = "chunk1",
            position = Pair(0, 0),
            type = NodeType.Hub,
            neighbors = emptyList()
        )

        val result = repository.update(node)
        assertTrue(result.isFailure)
    }

    // === Delete Tests ===

    @Test
    fun `delete removes node`() {
        val node = GraphNodeComponent(
            id = "node1",
            chunkId = "chunk1",
            position = Pair(0, 0),
            type = NodeType.Hub,
            neighbors = emptyList()
        )

        repository.save(node).getOrThrow()
        repository.delete("node1").getOrThrow()

        val loaded = repository.findById("node1").getOrThrow()
        assertNull(loaded)
    }

    @Test
    fun `delete nonexistent node succeeds`() {
        val result = repository.delete("nonexistent")
        assertTrue(result.isSuccess)
    }

    // === Edge Manipulation Tests ===

    @Test
    fun `addEdge adds new edge to node`() {
        val node = GraphNodeComponent(
            id = "node1",
            chunkId = "chunk1",
            position = Pair(0, 0),
            type = NodeType.Hub,
            neighbors = listOf(EdgeData(targetId = "node2", direction = "north"))
        )

        repository.save(node).getOrThrow()

        val newEdge = EdgeData(targetId = "node3", direction = "south")
        repository.addEdge("node1", newEdge).getOrThrow()

        val loaded = repository.findById("node1").getOrThrow()
        assertEquals(2, loaded?.neighbors?.size)
        assertTrue(loaded?.neighbors?.any { it.targetId == "node3" } == true)
    }

    @Test
    fun `addEdge fails for nonexistent node`() {
        val edge = EdgeData(targetId = "node2", direction = "north")
        val result = repository.addEdge("nonexistent", edge)
        assertTrue(result.isFailure)
    }

    @Test
    fun `addEdge fails for duplicate edge`() {
        val node = GraphNodeComponent(
            id = "node1",
            chunkId = "chunk1",
            position = Pair(0, 0),
            type = NodeType.Hub,
            neighbors = listOf(EdgeData(targetId = "node2", direction = "north"))
        )

        repository.save(node).getOrThrow()

        val duplicateEdge = EdgeData(targetId = "node2", direction = "north")
        val result = repository.addEdge("node1", duplicateEdge)
        assertTrue(result.isFailure)
    }

    @Test
    fun `removeEdge removes edge from node`() {
        val node = GraphNodeComponent(
            id = "node1",
            chunkId = "chunk1",
            position = Pair(0, 0),
            type = NodeType.Hub,
            neighbors = listOf(
                EdgeData(targetId = "node2", direction = "north"),
                EdgeData(targetId = "node3", direction = "south")
            )
        )

        repository.save(node).getOrThrow()
        repository.removeEdge("node1", "node2").getOrThrow()

        val loaded = repository.findById("node1").getOrThrow()
        assertEquals(1, loaded?.neighbors?.size)
        assertFalse(loaded?.neighbors?.any { it.targetId == "node2" } == true)
        assertTrue(loaded?.neighbors?.any { it.targetId == "node3" } == true)
    }

    @Test
    fun `removeEdge fails for nonexistent node`() {
        val result = repository.removeEdge("nonexistent", "node2")
        assertTrue(result.isFailure)
    }

    @Test
    fun `removeEdge fails for nonexistent edge`() {
        val node = GraphNodeComponent(
            id = "node1",
            chunkId = "chunk1",
            position = Pair(0, 0),
            type = NodeType.Hub,
            neighbors = listOf(EdgeData(targetId = "node2", direction = "north"))
        )

        repository.save(node).getOrThrow()

        val result = repository.removeEdge("node1", "node99")
        assertTrue(result.isFailure)
    }

    // === getAll Tests ===

    @Test
    fun `getAll returns all nodes`() {
        val node1 = GraphNodeComponent(
            id = "node1",
            chunkId = "chunk1",
            position = Pair(0, 0),
            type = NodeType.Hub,
            neighbors = emptyList()
        )

        val node2 = GraphNodeComponent(
            id = "node2",
            chunkId = "chunk1",
            position = Pair(1, 0),
            type = NodeType.Linear,
            neighbors = emptyList()
        )

        repository.save(node1).getOrThrow()
        repository.save(node2).getOrThrow()

        val all = repository.getAll().getOrThrow()
        assertEquals(2, all.size)
        assertTrue(all.containsKey("node1"))
        assertTrue(all.containsKey("node2"))
    }

    @Test
    fun `getAll returns empty map when no nodes exist`() {
        val all = repository.getAll().getOrThrow()
        assertTrue(all.isEmpty())
    }

    // === Edge Cases ===

    @Test
    fun `node with max position values persists correctly`() {
        val node = GraphNodeComponent(
            id = "node1",
            chunkId = "chunk1",
            position = Pair(Int.MAX_VALUE, Int.MAX_VALUE),
            type = NodeType.Hub,
            neighbors = emptyList()
        )

        repository.save(node).getOrThrow()
        val loaded = repository.findById("node1").getOrThrow()

        assertEquals(Pair(Int.MAX_VALUE, Int.MAX_VALUE), loaded?.position)
    }

    @Test
    fun `node with min position values persists correctly`() {
        val node = GraphNodeComponent(
            id = "node1",
            chunkId = "chunk1",
            position = Pair(Int.MIN_VALUE, Int.MIN_VALUE),
            type = NodeType.Hub,
            neighbors = emptyList()
        )

        repository.save(node).getOrThrow()
        val loaded = repository.findById("node1").getOrThrow()

        assertEquals(Pair(Int.MIN_VALUE, Int.MIN_VALUE), loaded?.position)
    }

    @Test
    fun `node with many edges persists correctly`() {
        val manyEdges = (1..20).map { EdgeData(targetId = "node$it", direction = "dir$it") }
        val node = GraphNodeComponent(
            id = "node1",
            chunkId = "chunk1",
            position = Pair(0, 0),
            type = NodeType.Hub,
            neighbors = manyEdges
        )

        repository.save(node).getOrThrow()
        val loaded = repository.findById("node1").getOrThrow()

        assertEquals(20, loaded?.neighbors?.size)
    }

    @Test
    fun `node with complex edge directions persists correctly`() {
        val node = GraphNodeComponent(
            id = "node1",
            chunkId = "chunk1",
            position = Pair(0, 0),
            type = NodeType.Hub,
            neighbors = listOf(
                EdgeData(targetId = "node2", direction = "through the hidden passage"),
                EdgeData(targetId = "node3", direction = "up the rickety ladder"),
                EdgeData(targetId = "node4", direction = "down into darkness")
            )
        )

        repository.save(node).getOrThrow()
        val loaded = repository.findById("node1").getOrThrow()

        assertEquals(3, loaded?.neighbors?.size)
        assertTrue(loaded?.neighbors?.any { it.direction == "through the hidden passage" } == true)
    }
}
