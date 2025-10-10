package com.jcraw.mud.memory

import kotlinx.datetime.Clock
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.*

class PersistentVectorStoreTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `should persist entries to disk`() {
        val storePath = File(tempDir, "vector_store.json").path
        val store = PersistentVectorStore(storePath)

        val entry = MemoryEntry(
            id = "test1",
            content = "Test content",
            embedding = listOf(0.1, 0.2, 0.3),
            timestamp = Clock.System.now()
        )

        store.add(entry)

        // Verify file exists
        val file = File(storePath)
        assertTrue(file.exists())

        // Verify content
        val fileContent = file.readText()
        assertTrue(fileContent.contains("Test content"))
    }

    @Test
    fun `should load entries from disk on initialization`() {
        val storePath = File(tempDir, "vector_store.json").path

        // Create store and add entry
        val store1 = PersistentVectorStore(storePath)
        val entry = MemoryEntry(
            id = "test1",
            content = "Persisted content",
            embedding = listOf(0.5, 0.5, 0.5),
            timestamp = Clock.System.now()
        )
        store1.add(entry)

        // Create new store instance - should load from disk
        val store2 = PersistentVectorStore(storePath)

        assertEquals(1, store2.size())
        val loaded = store2.getAll()
        assertEquals("Persisted content", loaded[0].content)
    }

    @Test
    fun `should search loaded entries correctly`() {
        val storePath = File(tempDir, "vector_store.json").path

        // Create store and add entries
        val store1 = PersistentVectorStore(storePath)
        store1.add(MemoryEntry(
            id = "1",
            content = "First",
            embedding = listOf(1.0, 0.0, 0.0),
            timestamp = Clock.System.now()
        ))
        store1.add(MemoryEntry(
            id = "2",
            content = "Second",
            embedding = listOf(0.9, 0.1, 0.0),
            timestamp = Clock.System.now()
        ))

        // Load and search
        val store2 = PersistentVectorStore(storePath)
        val results = store2.search(listOf(1.0, 0.0, 0.0), 2)

        assertEquals(2, results.size)
        assertEquals("First", results[0].entry.content)
        assertTrue(results[0].score > results[1].score)
    }

    @Test
    fun `should handle clear operation`() {
        val storePath = File(tempDir, "vector_store.json").path
        val store = PersistentVectorStore(storePath)

        store.add(MemoryEntry(
            id = "test",
            content = "Content",
            embedding = listOf(0.1, 0.2, 0.3),
            timestamp = Clock.System.now()
        ))

        assertEquals(1, store.size())

        store.clear()

        assertEquals(0, store.size())

        // Verify persistence
        val store2 = PersistentVectorStore(storePath)
        assertEquals(0, store2.size())
    }

    @Test
    fun `should handle missing file gracefully`() {
        val storePath = File(tempDir, "nonexistent.json").path
        val store = PersistentVectorStore(storePath)

        // Should start with empty store
        assertEquals(0, store.size())
    }

    @Test
    fun `should create parent directories if needed`() {
        val storePath = File(tempDir, "nested/dir/vector_store.json").path
        val store = PersistentVectorStore(storePath)

        store.add(MemoryEntry(
            id = "test",
            content = "Content",
            embedding = listOf(0.1, 0.2, 0.3),
            timestamp = Clock.System.now()
        ))

        // Verify parent directories were created
        val file = File(storePath)
        assertTrue(file.exists())
        assertTrue(file.parentFile.exists())
    }

    @Test
    fun `should preserve metadata across persistence`() {
        val storePath = File(tempDir, "vector_store.json").path

        val store1 = PersistentVectorStore(storePath)
        store1.add(MemoryEntry(
            id = "test",
            content = "Content",
            embedding = listOf(0.1, 0.2, 0.3),
            timestamp = Clock.System.now(),
            metadata = mapOf("type" to "combat", "room" to "dungeon")
        ))

        val store2 = PersistentVectorStore(storePath)
        val entries = store2.getAll()

        assertEquals(1, entries.size)
        assertEquals(mapOf("type" to "combat", "room" to "dungeon"), entries[0].metadata)
    }
}
