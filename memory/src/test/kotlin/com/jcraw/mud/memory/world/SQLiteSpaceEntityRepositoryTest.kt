package com.jcraw.mud.memory.world

import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.Stats
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SQLiteSpaceEntityRepositoryTest {
    private lateinit var database: WorldDatabase
    private lateinit var repository: SQLiteSpaceEntityRepository
    private val dbPath = "test_space_entities.db"

    @BeforeTest
    fun setUp() {
        database = WorldDatabase(dbPath)
        repository = SQLiteSpaceEntityRepository(database)
        database.clearAll()
    }

    @AfterTest
    fun tearDown() {
        database.close()
        java.io.File(dbPath).delete()
    }

    @Test
    fun `save and retrieve npc`() {
        val npc = sampleMerchant("npc_test")
        repository.save(npc).getOrThrow()

        val loaded = repository.findById(npc.id).getOrThrow()
        assertNotNull(loaded)
        val loadedNpc = loaded as? Entity.NPC
        assertNotNull(loadedNpc)
        assertEquals(npc.name, loadedNpc.name)
        assertEquals(npc.description, loadedNpc.description)
    }

    @Test
    fun `saveAll stores batch`() {
        val merchants = listOf(
            sampleMerchant("npc_batch_1"),
            sampleMerchant("npc_batch_2")
        )

        repository.saveAll(merchants).getOrThrow()

        merchants.forEach { merchant ->
            val loaded = repository.findById(merchant.id).getOrThrow() as? Entity.NPC
            assertNotNull(loaded)
            assertEquals(merchant.name, loaded.name)
        }
    }

    @Test
    fun `delete removes entity`() {
        val npc = sampleMerchant("npc_delete")
        repository.save(npc).getOrThrow()

        repository.delete(npc.id).getOrThrow()

        val loaded = repository.findById(npc.id).getOrThrow()
        assertNull(loaded)
    }

    private fun sampleMerchant(id: String): Entity.NPC =
        Entity.NPC(
            id = id,
            name = "Sample Merchant",
            description = "A friendly merchant",
            isHostile = false,
            health = 40,
            maxHealth = 40,
            stats = Stats(intelligence = 12, charisma = 14)
        )
}
