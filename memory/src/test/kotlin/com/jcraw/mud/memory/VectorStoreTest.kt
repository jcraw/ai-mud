package com.jcraw.mud.memory

import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for InMemoryVectorStore focusing on behavior and contracts
 */
class VectorStoreTest {

    @Test
    fun `search returns empty list when store is empty`() {
        val store = InMemoryVectorStore()
        val queryEmbedding = listOf(1.0, 0.0, 0.0)

        val results = store.search(queryEmbedding, k = 5)

        assertTrue(results.isEmpty())
    }

    @Test
    fun `search returns entries sorted by similarity score descending`() {
        val store = InMemoryVectorStore()

        // Add entries with known embeddings
        val identicalEntry = MemoryEntry(
            id = "1",
            content = "Identical vector",
            embedding = listOf(1.0, 0.0, 0.0),
            timestamp = Clock.System.now()
        )
        val similarEntry = MemoryEntry(
            id = "2",
            content = "Similar vector",
            embedding = listOf(0.9, 0.1, 0.0),
            timestamp = Clock.System.now()
        )
        val oppositeEntry = MemoryEntry(
            id = "3",
            content = "Opposite vector",
            embedding = listOf(-1.0, 0.0, 0.0),
            timestamp = Clock.System.now()
        )

        store.add(oppositeEntry)
        store.add(identicalEntry)
        store.add(similarEntry)

        val queryEmbedding = listOf(1.0, 0.0, 0.0)
        val results = store.search(queryEmbedding, k = 3)

        assertEquals(3, results.size)
        assertEquals("1", results[0].entry.id, "Identical vector should be first")
        assertEquals("2", results[1].entry.id, "Similar vector should be second")
        assertEquals("3", results[2].entry.id, "Opposite vector should be last")

        // Verify scores are in descending order
        assertTrue(results[0].score > results[1].score)
        assertTrue(results[1].score > results[2].score)
    }

    @Test
    fun `search respects k parameter`() {
        val store = InMemoryVectorStore()

        // Add 5 entries
        repeat(5) { i ->
            store.add(
                MemoryEntry(
                    id = i.toString(),
                    content = "Entry $i",
                    embedding = listOf(i.toDouble(), 0.0, 0.0),
                    timestamp = Clock.System.now()
                )
            )
        }

        val results = store.search(listOf(1.0, 0.0, 0.0), k = 3)

        assertEquals(3, results.size)
    }

    @Test
    fun `cosine similarity boundary conditions`() {
        val store = InMemoryVectorStore()

        // Identical vectors should have similarity = 1.0
        val identicalEntry = MemoryEntry(
            id = "identical",
            content = "Same direction",
            embedding = listOf(1.0, 2.0, 3.0),
            timestamp = Clock.System.now()
        )
        store.add(identicalEntry)

        val results = store.search(listOf(1.0, 2.0, 3.0), k = 1)
        assertEquals(1.0, results.first().score, 0.0001)
    }

    @Test
    fun `orthogonal vectors have zero similarity`() {
        val store = InMemoryVectorStore()

        val orthogonalEntry = MemoryEntry(
            id = "orthogonal",
            content = "Perpendicular vector",
            embedding = listOf(1.0, 0.0, 0.0),
            timestamp = Clock.System.now()
        )
        store.add(orthogonalEntry)

        val results = store.search(listOf(0.0, 1.0, 0.0), k = 1)
        assertEquals(0.0, results.first().score, 0.0001)
    }

    @Test
    fun `clear removes all entries`() {
        val store = InMemoryVectorStore()

        store.add(
            MemoryEntry(
                id = "1",
                content = "Entry 1",
                embedding = listOf(1.0, 0.0, 0.0),
                timestamp = Clock.System.now()
            )
        )

        assertEquals(1, store.size())

        store.clear()

        assertEquals(0, store.size())
        assertTrue(store.getAll().isEmpty())
    }

    @Test
    fun `metadata is preserved in entries`() {
        val store = InMemoryVectorStore()

        val metadata = mapOf("type" to "combat", "room" to "dungeon_entrance")
        val entry = MemoryEntry(
            id = "1",
            content = "Player defeated skeleton",
            embedding = listOf(1.0, 0.0, 0.0),
            timestamp = Clock.System.now(),
            metadata = metadata
        )

        store.add(entry)

        val results = store.search(listOf(1.0, 0.0, 0.0), k = 1)
        assertEquals(metadata, results.first().entry.metadata)
    }
}
