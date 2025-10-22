package com.jcraw.mud.reasoning.skill

import com.jcraw.mud.core.SkillComponent
import com.jcraw.mud.core.SkillState
import com.jcraw.mud.core.repository.SkillComponentRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.random.Random

class MockSkillComponentRepository : SkillComponentRepository {
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

class SkillComboResolverTest {

    private val mockRepo = MockSkillComponentRepository()
    private val fixedRng = Random(42) // Deterministic for testing
    private val resolver = SkillComboResolver(mockRepo, fixedRng)

    @Test
    fun `identifySkills recognizes fireball action`() {
        val skills = resolver.identifySkills("cast fireball")

        assertEquals(3, skills.size)
        assertEquals(0.6f, skills["Fire Magic"])
        assertEquals(0.3f, skills["Gesture Casting"])
        assertEquals(0.1f, skills["Magical Projectile Accuracy"])
    }

    @Test
    fun `identifySkills recognizes water magic`() {
        val skills = resolver.identifySkills("cast water spell")

        assertEquals(3, skills.size)
        assertEquals(0.6f, skills["Water Magic"])
        assertEquals(0.3f, skills["Gesture Casting"])
    }

    @Test
    fun `identifySkills recognizes lockpicking`() {
        val skills = resolver.identifySkills("pick the lock")

        assertEquals(2, skills.size)
        assertEquals(0.7f, skills["Lockpicking"])
        assertEquals(0.3f, skills["Agility"])
    }

    @Test
    fun `identifySkills recognizes backstab`() {
        val skills = resolver.identifySkills("backstab the guard")

        assertEquals(2, skills.size)
        assertEquals(0.6f, skills["Backstab"])
        assertEquals(0.4f, skills["Stealth"])
    }

    @Test
    fun `identifySkills returns empty for unknown action`() {
        val skills = resolver.identifySkills("dance a jig")

        assertTrue(skills.isEmpty())
    }

    @Test
    fun `resolveCombo calculates weighted average correctly`() {
        val entityId = "player"
        val component = SkillComponent(
            skills = mapOf(
                "Fire Magic" to SkillState(level = 10, unlocked = true),
                "Gesture Casting" to SkillState(level = 5, unlocked = true),
                "Magical Projectile Accuracy" to SkillState(level = 2, unlocked = true)
            )
        )

        mockRepo.setComponent(entityId, component)

        val skillWeights = mapOf(
            "Fire Magic" to 0.6f,
            "Gesture Casting" to 0.3f,
            "Magical Projectile Accuracy" to 0.1f
        )

        val result = resolver.resolveCombo(entityId, skillWeights, difficulty = 15).getOrThrow()

        // Weighted average: 10*0.6 + 5*0.3 + 2*0.1 = 6 + 1.5 + 0.2 = 7.7 → 7
        assertEquals(7, result.skillLevel)
        assertTrue(result.narrative.contains("Fire Magic"))
        assertTrue(result.narrative.contains("Gesture Casting"))
    }

    @Test
    fun `resolveCombo succeeds with high effective level`() {
        val entityId = "player"
        val component = SkillComponent(
            skills = mapOf(
                "Fire Magic" to SkillState(level = 20, unlocked = true),
                "Gesture Casting" to SkillState(level = 15, unlocked = true)
            )
        )

        mockRepo.setComponent(entityId, component)

        val skillWeights = mapOf(
            "Fire Magic" to 0.7f,
            "Gesture Casting" to 0.3f
        )

        // Weighted: 20*0.7 + 15*0.3 = 14 + 4.5 = 18.5 → 18
        // With any roll >= 1, total = 1+18 = 19, easily beats DC 10
        val result = resolver.resolveCombo(entityId, skillWeights, difficulty = 10).getOrThrow()

        assertTrue(result.success)
        assertEquals(18, result.skillLevel)
    }

    @Test
    fun `resolveCombo fails with low effective level`() {
        val entityId = "player"
        val component = SkillComponent(
            skills = mapOf(
                "Fire Magic" to SkillState(level = 1, unlocked = true)
            )
        )

        mockRepo.setComponent(entityId, component)

        val skillWeights = mapOf("Fire Magic" to 1.0f)

        // Level 1, need DC 25 - very unlikely to succeed
        val results = (1..10).map {
            resolver.resolveCombo(entityId, skillWeights, difficulty = 25).getOrThrow()
        }

        // Most should fail
        val failCount = results.count { !it.success }
        assertTrue(failCount > 5, "Expected most checks to fail with level 1 vs DC 25")
    }

    @Test
    fun `resolveCombo uses 0 for unlocked skills`() {
        val entityId = "player"
        val component = SkillComponent(
            skills = mapOf(
                "Fire Magic" to SkillState(level = 10, unlocked = false) // Not unlocked!
            )
        )

        mockRepo.setComponent(entityId, component)

        val skillWeights = mapOf("Fire Magic" to 1.0f)

        val result = resolver.resolveCombo(entityId, skillWeights, difficulty = 10).getOrThrow()

        // Should use level 0 since not unlocked
        assertEquals(0, result.skillLevel)
    }

    @Test
    fun `resolveCombo handles missing skills as level 0`() {
        val entityId = "player"
        val component = SkillComponent(skills = emptyMap())

        mockRepo.setComponent(entityId, component)

        val skillWeights = mapOf(
            "Fire Magic" to 0.6f,
            "Gesture Casting" to 0.4f
        )

        val result = resolver.resolveCombo(entityId, skillWeights, difficulty = 15).getOrThrow()

        // No skills = level 0
        assertEquals(0, result.skillLevel)
        assertFalse(result.success) // Very unlikely to succeed with 0 skill
    }

    @Test
    fun `resolveAction identifies and resolves in one call`() {
        val entityId = "player"
        val component = SkillComponent(
            skills = mapOf(
                "Fire Magic" to SkillState(level = 15, unlocked = true),
                "Gesture Casting" to SkillState(level = 10, unlocked = true),
                "Magical Projectile Accuracy" to SkillState(level = 5, unlocked = true)
            )
        )

        mockRepo.setComponent(entityId, component)

        val result = resolver.resolveAction(entityId, "cast fireball", difficulty = 15).getOrThrow()

        // Should recognize fireball and resolve combo
        assertTrue(result.skillLevel > 0)
        assertTrue(result.narrative.contains("Fire Magic"))
    }

    @Test
    fun `resolveAction returns failure for unknown action`() {
        val entityId = "player"
        mockRepo.setComponent(entityId, SkillComponent())

        val result = resolver.resolveAction(entityId, "unknown action", difficulty = 10).getOrThrow()

        assertFalse(result.success)
        assertTrue(result.narrative.contains("Could not identify skills"))
    }

    @Test
    fun `resolveCombo requires positive weights`() {
        val entityId = "player"
        mockRepo.setComponent(entityId, SkillComponent())

        val skillWeights = mapOf("Fire Magic" to -0.5f)

        val result = resolver.resolveCombo(entityId, skillWeights, difficulty = 10)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("positive") == true)
    }

    @Test
    fun `resolveCombo requires non-empty weights`() {
        val entityId = "player"
        mockRepo.setComponent(entityId, SkillComponent())

        val result = resolver.resolveCombo(entityId, emptyMap(), difficulty = 10)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("at least one skill") == true)
    }
}
