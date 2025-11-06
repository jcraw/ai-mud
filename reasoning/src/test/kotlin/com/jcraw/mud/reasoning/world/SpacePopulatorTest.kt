package com.jcraw.mud.reasoning.world

import com.jcraw.mud.core.*
import com.jcraw.mud.core.repository.ItemRepository
import com.jcraw.mud.core.world.*
import kotlin.test.*

/**
 * Tests for SpacePopulator content orchestration.
 * Validates coordinated trap, resource, and mob placement.
 */
class SpacePopulatorTest {

    private class MockItemRepository : ItemRepository {
        override fun saveTemplate(template: ItemTemplate): Result<Unit> = Result.success(Unit)
        override fun saveTemplates(templates: List<ItemTemplate>): Result<Unit> = Result.success(Unit)
        override fun getTemplate(id: String): Result<ItemTemplate?> = Result.success(null)
        override fun getAllTemplates(): Result<List<ItemTemplate>> = Result.success(emptyList())
        override fun getTemplatesByType(type: ItemType): Result<List<ItemTemplate>> = Result.success(emptyList())
        override fun getTemplatesByRarity(rarity: Rarity): Result<List<ItemRarity>> = Result.success(emptyList())
        override fun updateTemplate(template: ItemTemplate): Result<Unit> = Result.success(Unit)
        override fun deleteTemplate(id: String): Result<Unit> = Result.success(Unit)
        override fun saveInstance(instance: ItemInstance): Result<Unit> = Result.success(Unit)
        override fun getInstance(id: String): Result<ItemInstance?> = Result.success(null)
        override fun getInstancesByTemplate(templateId: String): Result<List<ItemInstance>> = Result.success(emptyList())
        override fun updateInstance(instance: ItemInstance): Result<Unit> = Result.success(Unit)
        override fun deleteInstance(id: String): Result<Unit> = Result.success(Unit)
    }

    private fun createPopulator(): SpacePopulator {
        val mockItemRepo = MockItemRepository()
        val trapGen = TrapGenerator()
        val resourceGen = ResourceGenerator(mockItemRepo)
        val mobSpawner = MobSpawner()
        return SpacePopulator(trapGen, resourceGen, mobSpawner)
    }

    private fun createEmptySpace(): SpacePropertiesComponent {
        return SpacePropertiesComponent(
            name = "Empty Space",
            description = "An empty space.",
            exits = emptyList(),
            brightness = 5,
            terrainType = TerrainType.NORMAL,
            traps = emptyList(),
            resources = emptyList(),
            entities = emptyList(),
            itemsDropped = emptyList(),
            stateFlags = emptyMap()
        )
    }

    @Test
    fun `populate adds mobs based on density`() {
        val populator = createPopulator()
        val space = createEmptySpace()

        val populated = populator.populate(space, "dark forest", 5, mobDensity = 0.5, spaceSize = 10)

        // mobDensity 0.5 * spaceSize 10 = 5 mobs
        assertEquals(5, populated.entities.size)
    }

    @Test
    fun `populate may add traps`() {
        val populator = createPopulator()
        val space = createEmptySpace()

        // Run multiple times to check trap generation (15% probability)
        var foundTraps = false
        repeat(50) {
            val populated = populator.populate(space, "dark forest", 5, mobDensity = 0.0)
            if (populated.traps.isNotEmpty()) {
                foundTraps = true
            }
        }

        assertTrue(foundTraps, "Should occasionally generate traps with 15% probability")
    }

    @Test
    fun `populate may add resources`() {
        val populator = createPopulator()
        val space = createEmptySpace()

        // Run multiple times to check resource generation (5% probability)
        var foundResources = false
        repeat(100) {
            val populated = populator.populate(space, "dark forest", 5, mobDensity = 0.0)
            if (populated.resources.isNotEmpty()) {
                foundResources = true
            }
        }

        assertTrue(foundResources, "Should occasionally generate resources with 5% probability")
    }

    @Test
    fun `populate preserves existing content`() {
        val populator = createPopulator()
        val existingTrap = TrapData("trap1", "bear trap", 10, false, "A trap")
        val space = createEmptySpace().copy(traps = listOf(existingTrap))

        val populated = populator.populate(space, "dark forest", 5, mobDensity = 0.5)

        // Should preserve existing trap
        assertTrue(populated.traps.contains(existingTrap))
    }

    @Test
    fun `populate returns same space instance with additions`() {
        val populator = createPopulator()
        val space = createEmptySpace()

        val populated = populator.populate(space, "dark forest", 5, mobDensity = 0.5)

        // Should preserve original fields
        assertEquals(space.description, populated.description)
        assertEquals(space.brightness, populated.brightness)
        assertEquals(space.terrain, populated.terrain)
    }

    @Test
    fun `populateWithEntities returns both space and mob list`() {
        val populator = createPopulator()
        val space = createEmptySpace()

        val (updatedSpace, mobs) = populator.populateWithEntities(
            space, "dark forest", 5, mobDensity = 0.5, spaceSize = 10
        )

        // Should return 5 mobs (0.5 * 10)
        assertEquals(5, mobs.size)
        // Space should have 5 entity IDs
        assertEquals(5, updatedSpace.entities.size)
        // Entity IDs should match mob IDs
        assertEquals(mobs.map { it.id }.toSet(), updatedSpace.entities.toSet())
    }

    @Test
    fun `populateWithEntities mob IDs match space entity list`() {
        val populator = createPopulator()
        val space = createEmptySpace()

        val (updatedSpace, mobs) = populator.populateWithEntities(
            space, "dark forest", 5, mobDensity = 0.3, spaceSize = 10
        )

        mobs.forEach { mob ->
            assertTrue(updatedSpace.entities.contains(mob.id), "Mob ID ${mob.id} should be in entity list")
        }
    }

    @Test
    fun `repopulate clears existing entities and spawns new mobs`() {
        val populator = createPopulator()
        val space = createEmptySpace().copy(entities = listOf("old_npc_1", "old_npc_2"))

        val (updatedSpace, newMobs) = populator.repopulate(
            space, "dark forest", 5, mobDensity = 0.5, spaceSize = 10
        )

        // Should have 5 new mobs (0.5 * 10)
        assertEquals(5, newMobs.size)
        // Old entity IDs should be gone
        assertFalse(updatedSpace.entities.contains("old_npc_1"))
        assertFalse(updatedSpace.entities.contains("old_npc_2"))
        // New entity IDs should match new mobs
        assertEquals(newMobs.map { it.id }.toSet(), updatedSpace.entities.toSet())
    }

    @Test
    fun `repopulate preserves traps and resources`() {
        val populator = createPopulator()
        val trap = TrapData("trap1", "bear trap", 10, false, "A trap")
        val resource = ResourceNode("res1", "wood", 5, null, "Wood pile", 0)
        val space = createEmptySpace().copy(
            traps = listOf(trap),
            resources = listOf(resource),
            entities = listOf("old_mob")
        )

        val (updatedSpace, _) = populator.repopulate(space, "dark forest", 5, mobDensity = 0.2)

        // Traps and resources should be preserved
        assertEquals(listOf(trap), updatedSpace.traps)
        assertEquals(listOf(resource), updatedSpace.resources)
    }

    @Test
    fun `calculateMobCount formula is correct`() {
        val populator = createPopulator()

        assertEquals(0, populator.calculateMobCount(0.0, 10))
        assertEquals(2, populator.calculateMobCount(0.2, 10))
        assertEquals(5, populator.calculateMobCount(0.5, 10))
        assertEquals(10, populator.calculateMobCount(1.0, 10))
        assertEquals(20, populator.calculateMobCount(1.0, 20))
    }

    @Test
    fun `calculateMobCount never returns negative`() {
        val populator = createPopulator()

        val count = populator.calculateMobCount(-0.5, 10)
        assertTrue(count >= 0, "Mob count should never be negative")
    }

    @Test
    fun `clearDynamicContent removes generated content`() {
        val populator = createPopulator()
        val trap = TrapData("trap1", "bear trap", 10, false, "A trap")
        val resource = ResourceNode("res1", "wood", 5, null, "Wood pile", 0)
        val space = createEmptySpace().copy(
            traps = listOf(trap),
            resources = listOf(resource),
            entities = listOf("npc1", "npc2")
        )

        val cleared = populator.clearDynamicContent(space)

        assertTrue(cleared.traps.isEmpty())
        assertTrue(cleared.resources.isEmpty())
        assertTrue(cleared.entities.isEmpty())
    }

    @Test
    fun `clearDynamicContent preserves state flags and items`() {
        val populator = createPopulator()
        val space = createEmptySpace().copy(
            stateFlags = mapOf("flag1" to true),
            itemsDropped = listOf("item1"),
            traps = listOf(TrapData("trap1", "bear trap", 10, false, "A trap"))
        )

        val cleared = populator.clearDynamicContent(space)

        // Flags and items should be preserved
        assertEquals(mapOf("flag1" to true), cleared.stateFlags)
        assertEquals(listOf("item1"), cleared.itemsDropped)
        // Dynamic content should be cleared
        assertTrue(cleared.traps.isEmpty())
    }

    @Test
    fun `populate works with zero mob density`() {
        val populator = createPopulator()
        val space = createEmptySpace()

        val populated = populator.populate(space, "dark forest", 5, mobDensity = 0.0)

        assertEquals(0, populated.entities.size)
    }

    @Test
    fun `populate works with high mob density`() {
        val populator = createPopulator()
        val space = createEmptySpace()

        val populated = populator.populate(space, "dark forest", 5, mobDensity = 2.0, spaceSize = 10)

        assertEquals(20, populated.entities.size)
    }

    @Test
    fun `populate with different themes`() {
        val populator = createPopulator()
        val space = createEmptySpace()
        val themes = listOf(
            "dark forest", "magma cave", "ancient crypt", "frozen wasteland"
        )

        themes.forEach { theme ->
            val populated = populator.populate(space, theme, 5, mobDensity = 0.3)
            // Should populate without errors
            assertTrue(populated.entities.size > 0)
        }
    }

    @Test
    fun `populate with different difficulties`() {
        val populator = createPopulator()
        val space = createEmptySpace()
        val difficulties = listOf(1, 5, 10, 15, 20)

        difficulties.forEach { difficulty ->
            val populated = populator.populate(space, "dark forest", difficulty, mobDensity = 0.2)
            // Should populate without errors
            assertEquals(2, populated.entities.size)
        }
    }
}
