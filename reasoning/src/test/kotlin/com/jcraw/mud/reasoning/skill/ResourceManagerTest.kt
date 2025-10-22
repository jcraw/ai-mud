package com.jcraw.mud.reasoning.skill

import com.jcraw.mud.core.SkillComponent
import com.jcraw.mud.core.SkillState
import com.jcraw.mud.core.repository.SkillComponentRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MockSkillComponentRepositoryForResources : SkillComponentRepository {
    private val components = mutableMapOf<String, SkillComponent>()

    override fun save(entityId: String, component: SkillComponent): Result<Unit> {
        components[entityId] = component
        return Result.success(Unit)
    }

    override fun load(entityId: String): Result<SkillComponent?> {
        return Result.success(components[entityId] ?: SkillComponent())
    }

    override fun delete(entityId: String): Result<Unit> {
        components.remove(entityId)
        return Result.success(Unit)
    }

    override fun findAll(): Result<Map<String, SkillComponent>> {
        return Result.success(components.toMap())
    }

    fun setComponent(entityId: String, component: SkillComponent) {
        components[entityId] = component
    }
}

class ResourceManagerTest {

    private val mockRepo = MockSkillComponentRepositoryForResources()
    private lateinit var manager: ResourceManager

    @BeforeEach
    fun setup() {
        manager = ResourceManager(mockRepo)
    }

    @Test
    fun `getResourcePool calculates max from skill level`() {
        val entityId = "player"
        val component = SkillComponent(
            skills = mapOf(
                "Mana Reserve" to SkillState(
                    level = 20,
                    unlocked = true,
                    resourceType = "mana"
                )
            )
        )

        mockRepo.setComponent(entityId, component)

        val pool = manager.getResourcePool(entityId, "mana").getOrThrow()

        // Max = level * 10 = 20 * 10 = 200
        assertEquals("mana", pool.resourceType)
        assertEquals(200, pool.max)
        assertEquals(200, pool.current) // Defaults to max
    }

    @Test
    fun `getResourcePool returns 0 max for missing skill`() {
        val entityId = "player"
        val component = SkillComponent(skills = emptyMap())

        mockRepo.setComponent(entityId, component)

        val pool = manager.getResourcePool(entityId, "mana").getOrThrow()

        assertEquals(0, pool.max)
        assertEquals(0, pool.current)
    }

    @Test
    fun `getResourcePool returns 0 for unlocked skill`() {
        val entityId = "player"
        val component = SkillComponent(
            skills = mapOf(
                "Mana Reserve" to SkillState(
                    level = 20,
                    unlocked = false, // Not unlocked!
                    resourceType = "mana"
                )
            )
        )

        mockRepo.setComponent(entityId, component)

        val pool = manager.getResourcePool(entityId, "mana").getOrThrow()

        assertEquals(0, pool.max) // Not unlocked = no pool
    }

    @Test
    fun `consumeResource succeeds with sufficient resources`() {
        val entityId = "player"
        val component = SkillComponent(
            skills = mapOf(
                "Mana Reserve" to SkillState(level = 10, unlocked = true, resourceType = "mana")
            )
        )

        mockRepo.setComponent(entityId, component)

        // Initial pool: 100/100
        val initialPool = manager.getResourcePool(entityId, "mana").getOrThrow()
        assertEquals(100, initialPool.current)

        // Consume 30 mana
        val success = manager.consumeResource(entityId, "mana", 30).getOrThrow()
        assertTrue(success)

        // Should now be 70/100
        val updatedPool = manager.getResourcePool(entityId, "mana").getOrThrow()
        assertEquals(70, updatedPool.current)
        assertEquals(100, updatedPool.max)
    }

    @Test
    fun `consumeResource fails with insufficient resources`() {
        val entityId = "player"
        val component = SkillComponent(
            skills = mapOf(
                "Mana Reserve" to SkillState(level = 10, unlocked = true, resourceType = "mana")
            )
        )

        mockRepo.setComponent(entityId, component)

        // Consume more than available
        val success = manager.consumeResource(entityId, "mana", 150).getOrThrow()
        assertFalse(success)

        // Pool should be unchanged
        val pool = manager.getResourcePool(entityId, "mana").getOrThrow()
        assertEquals(100, pool.current)
    }

    @Test
    fun `consumeResource can consume exact amount`() {
        val entityId = "player"
        val component = SkillComponent(
            skills = mapOf(
                "Mana Reserve" to SkillState(level = 5, unlocked = true, resourceType = "mana")
            )
        )

        mockRepo.setComponent(entityId, component)

        // Pool is 50/50, consume exactly 50
        val success = manager.consumeResource(entityId, "mana", 50).getOrThrow()
        assertTrue(success)

        val pool = manager.getResourcePool(entityId, "mana").getOrThrow()
        assertEquals(0, pool.current) // Empty
    }

    @Test
    fun `regenerateResource restores based on flow skill`() {
        val entityId = "player"
        val component = SkillComponent(
            skills = mapOf(
                "Mana Reserve" to SkillState(level = 20, unlocked = true, resourceType = "mana"),
                "Mana Flow" to SkillState(level = 10, unlocked = true)
            )
        )

        mockRepo.setComponent(entityId, component)

        // Consume some mana first
        manager.consumeResource(entityId, "mana", 100).getOrThrow()

        // Current: 100/200
        val beforeRegen = manager.getResourcePool(entityId, "mana").getOrThrow()
        assertEquals(100, beforeRegen.current)

        // Regen: flow level * 2 = 10 * 2 = 20
        val regenAmount = manager.regenerateResource(entityId, "mana").getOrThrow()
        assertEquals(20, regenAmount)

        // Current should be 120/200
        val afterRegen = manager.getResourcePool(entityId, "mana").getOrThrow()
        assertEquals(120, afterRegen.current)
    }

    @Test
    fun `regenerateResource does not exceed max`() {
        val entityId = "player"
        val component = SkillComponent(
            skills = mapOf(
                "Mana Reserve" to SkillState(level = 10, unlocked = true, resourceType = "mana"),
                "Mana Flow" to SkillState(level = 10, unlocked = true) // Regen 20/turn
            )
        )

        mockRepo.setComponent(entityId, component)

        // Consume only 10 mana (90/100)
        manager.consumeResource(entityId, "mana", 10).getOrThrow()

        // Regen 20, but only 10 needed to reach max
        val regenAmount = manager.regenerateResource(entityId, "mana").getOrThrow()
        assertEquals(10, regenAmount)

        val pool = manager.getResourcePool(entityId, "mana").getOrThrow()
        assertEquals(100, pool.current) // Capped at max
    }

    @Test
    fun `regenerateResource returns 0 with no flow skill`() {
        val entityId = "player"
        val component = SkillComponent(
            skills = mapOf(
                "Mana Reserve" to SkillState(level = 10, unlocked = true, resourceType = "mana")
                // No Mana Flow skill!
            )
        )

        mockRepo.setComponent(entityId, component)

        manager.consumeResource(entityId, "mana", 50).getOrThrow()

        val regenAmount = manager.regenerateResource(entityId, "mana").getOrThrow()
        assertEquals(0, regenAmount)

        // Pool should be unchanged
        val pool = manager.getResourcePool(entityId, "mana").getOrThrow()
        assertEquals(50, pool.current)
    }

    @Test
    fun `restoreResourceFull restores to max`() {
        val entityId = "player"
        val component = SkillComponent(
            skills = mapOf(
                "Chi Reserve" to SkillState(level = 15, unlocked = true, resourceType = "chi")
            )
        )

        mockRepo.setComponent(entityId, component)

        // Consume all chi
        manager.consumeResource(entityId, "chi", 150).getOrThrow()

        val beforeRestore = manager.getResourcePool(entityId, "chi").getOrThrow()
        assertEquals(0, beforeRestore.current)

        // Restore to full
        manager.restoreResourceFull(entityId, "chi").getOrThrow()

        val afterRestore = manager.getResourcePool(entityId, "chi").getOrThrow()
        assertEquals(150, afterRestore.current)
        assertEquals(150, afterRestore.max)
    }

    @Test
    fun `getAllResourcePools returns all resource types`() {
        val entityId = "player"
        val component = SkillComponent(
            skills = mapOf(
                "Mana Reserve" to SkillState(level = 10, unlocked = true, resourceType = "mana"),
                "Chi Reserve" to SkillState(level = 15, unlocked = true, resourceType = "chi"),
                "Sword Fighting" to SkillState(level = 20, unlocked = true) // Not a resource
            )
        )

        mockRepo.setComponent(entityId, component)

        val pools = manager.getAllResourcePools(entityId).getOrThrow()

        assertEquals(2, pools.size)
        assertTrue(pools.any { it.resourceType == "mana" && it.max == 100 })
        assertTrue(pools.any { it.resourceType == "chi" && it.max == 150 })
    }

    @Test
    fun `clearResourcesForEntity removes tracking`() {
        val entityId = "player"
        val component = SkillComponent(
            skills = mapOf(
                "Mana Reserve" to SkillState(level = 10, unlocked = true, resourceType = "mana")
            )
        )

        mockRepo.setComponent(entityId, component)

        // Consume some mana
        manager.consumeResource(entityId, "mana", 50).getOrThrow()

        val beforeClear = manager.getResourcePool(entityId, "mana").getOrThrow()
        assertEquals(50, beforeClear.current)

        // Clear tracking
        manager.clearResourcesForEntity(entityId)

        // Should reset to max
        val afterClear = manager.getResourcePool(entityId, "mana").getOrThrow()
        assertEquals(100, afterClear.current) // Reset to max
    }

    @Test
    fun `ResourcePool hasEnough checks availability`() {
        val pool = ResourcePool("mana", current = 50, max = 100)

        assertTrue(pool.hasEnough(30))
        assertTrue(pool.hasEnough(50))
        assertFalse(pool.hasEnough(51))
    }

    @Test
    fun `ResourcePool consume reduces current`() {
        val pool = ResourcePool("mana", current = 100, max = 100)

        val newPool = pool.consume(30)
        assertNotNull(newPool)
        assertEquals(70, newPool?.current)

        val emptyPool = pool.consume(101)
        assertNull(emptyPool) // Cannot consume more than available
    }

    @Test
    fun `ResourcePool regenerate increases current`() {
        val pool = ResourcePool("mana", current = 50, max = 100)

        val regenPool = pool.regenerate(30)
        assertEquals(80, regenPool.current)

        // Cannot exceed max
        val fullPool = regenPool.regenerate(50)
        assertEquals(100, fullPool.current)
    }

    @Test
    fun `ResourcePool percentageRemaining calculates correctly`() {
        val fullPool = ResourcePool("mana", current = 100, max = 100)
        assertEquals(1.0f, fullPool.percentageRemaining())

        val halfPool = ResourcePool("mana", current = 50, max = 100)
        assertEquals(0.5f, halfPool.percentageRemaining())

        val emptyPool = ResourcePool("mana", current = 0, max = 100)
        assertEquals(0.0f, emptyPool.percentageRemaining())

        val noMaxPool = ResourcePool("mana", current = 0, max = 0)
        assertEquals(0.0f, noMaxPool.percentageRemaining()) // Edge case
    }
}
