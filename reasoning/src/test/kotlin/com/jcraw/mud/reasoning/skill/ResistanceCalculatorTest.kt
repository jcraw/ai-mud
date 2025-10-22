package com.jcraw.mud.reasoning.skill

import com.jcraw.mud.core.SkillComponent
import com.jcraw.mud.core.SkillState
import com.jcraw.mud.core.repository.SkillComponentRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MockSkillComponentRepositoryForResistance : SkillComponentRepository {
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

class ResistanceCalculatorTest {

    private val mockRepo = MockSkillComponentRepositoryForResistance()
    private val calculator = ResistanceCalculator(mockRepo)

    @Test
    fun `calculateReduction with no resistance returns full damage`() {
        val entityId = "player"
        val component = SkillComponent(skills = emptyMap())

        mockRepo.setComponent(entityId, component)

        val result = calculator.calculateReduction(entityId, "Fire", damage = 100).getOrThrow()

        assertEquals(100, result.originalDamage)
        assertEquals(100, result.reducedDamage)
        assertEquals(0, result.reductionAmount)
        assertEquals(0f, result.reductionPercentage)
        assertEquals(0, result.resistanceLevel)
        assertTrue(result.narrative.contains("No resistance"))
    }

    @Test
    fun `calculateReduction with level 20 resistance reduces by 10 percent`() {
        val entityId = "player"
        val component = SkillComponent(
            skills = mapOf(
                "Fire Resistance" to SkillState(level = 20, unlocked = true)
            )
        )

        mockRepo.setComponent(entityId, component)

        val result = calculator.calculateReduction(entityId, "Fire", damage = 100).getOrThrow()

        // Level 20 = 10% reduction (20 / 2 / 100 = 0.1)
        assertEquals(100, result.originalDamage)
        assertEquals(90, result.reducedDamage)
        assertEquals(10, result.reductionAmount)
        assertEquals(0.1f, result.reductionPercentage)
        assertEquals(20, result.resistanceLevel)
        assertTrue(result.narrative.contains("Fire Resistance"))
        assertTrue(result.narrative.contains("10%") || result.narrative.contains("10.0%"))
    }

    @Test
    fun `calculateReduction with level 50 resistance reduces by 25 percent`() {
        val entityId = "player"
        val component = SkillComponent(
            skills = mapOf(
                "Poison Resistance" to SkillState(level = 50, unlocked = true)
            )
        )

        mockRepo.setComponent(entityId, component)

        val result = calculator.calculateReduction(entityId, "Poison", damage = 80).getOrThrow()

        // Level 50 = 25% reduction (50 / 2 / 100 = 0.25)
        assertEquals(80, result.originalDamage)
        assertEquals(60, result.reducedDamage)
        assertEquals(20, result.reductionAmount)
        assertEquals(0.25f, result.reductionPercentage)
        assertEquals(50, result.resistanceLevel)
    }

    @Test
    fun `calculateReduction with level 100 resistance reduces by 50 percent`() {
        val entityId = "player"
        val component = SkillComponent(
            skills = mapOf(
                "Slashing Resistance" to SkillState(level = 100, unlocked = true)
            )
        )

        mockRepo.setComponent(entityId, component)

        val result = calculator.calculateReduction(entityId, "Slashing", damage = 200).getOrThrow()

        // Level 100 = 50% reduction (100 / 2 / 100 = 0.5)
        assertEquals(200, result.originalDamage)
        assertEquals(100, result.reducedDamage)
        assertEquals(100, result.reductionAmount)
        assertEquals(0.5f, result.reductionPercentage)
        assertEquals(100, result.resistanceLevel)
    }

    @Test
    fun `calculateReduction with unlocked resistance returns no reduction`() {
        val entityId = "player"
        val component = SkillComponent(
            skills = mapOf(
                "Fire Resistance" to SkillState(level = 50, unlocked = false) // Not unlocked!
            )
        )

        mockRepo.setComponent(entityId, component)

        val result = calculator.calculateReduction(entityId, "Fire", damage = 100).getOrThrow()

        // Should act as if no resistance
        assertEquals(100, result.reducedDamage)
        assertEquals(0, result.reductionAmount)
        assertEquals(0, result.resistanceLevel)
    }

    @Test
    fun `calculateReduction handles zero damage`() {
        val entityId = "player"
        val component = SkillComponent(
            skills = mapOf(
                "Fire Resistance" to SkillState(level = 20, unlocked = true)
            )
        )

        mockRepo.setComponent(entityId, component)

        val result = calculator.calculateReduction(entityId, "Fire", damage = 0).getOrThrow()

        assertEquals(0, result.originalDamage)
        assertEquals(0, result.reducedDamage)
        assertEquals(0, result.reductionAmount)
    }

    @Test
    fun `calculateReduction with low damage rounds down correctly`() {
        val entityId = "player"
        val component = SkillComponent(
            skills = mapOf(
                "Fire Resistance" to SkillState(level = 20, unlocked = true) // 10% reduction
            )
        )

        mockRepo.setComponent(entityId, component)

        val result = calculator.calculateReduction(entityId, "Fire", damage = 5).getOrThrow()

        // 5 * 0.1 = 0.5, rounds down to 0
        assertEquals(5, result.originalDamage)
        assertEquals(5, result.reducedDamage) // No reduction due to rounding
        assertEquals(0, result.reductionAmount)
    }

    @Test
    fun `calculateMultiTypeReduction handles multiple damage types`() {
        val entityId = "player"
        val component = SkillComponent(
            skills = mapOf(
                "Fire Resistance" to SkillState(level = 20, unlocked = true),
                "Poison Resistance" to SkillState(level = 50, unlocked = true)
            )
        )

        mockRepo.setComponent(entityId, component)

        val damageByType = mapOf(
            "Fire" to 100,
            "Poison" to 80
        )

        val results = calculator.calculateMultiTypeReduction(entityId, damageByType).getOrThrow()

        assertEquals(2, results.size)

        val fireResult = results.find { it.damageType == "Fire" }
        assertNotNull(fireResult)
        assertEquals(90, fireResult?.reducedDamage) // 10% reduction

        val poisonResult = results.find { it.damageType == "Poison" }
        assertNotNull(poisonResult)
        assertEquals(60, poisonResult?.reducedDamage) // 25% reduction
    }

    @Test
    fun `getTotalReducedDamage sums all reduced damages`() {
        val entityId = "player"
        val component = SkillComponent(
            skills = mapOf(
                "Fire Resistance" to SkillState(level = 20, unlocked = true),
                "Poison Resistance" to SkillState(level = 50, unlocked = true)
            )
        )

        mockRepo.setComponent(entityId, component)

        val damageByType = mapOf(
            "Fire" to 100,  // Reduces to 90
            "Poison" to 80  // Reduces to 60
        )

        val totalDamage = calculator.getTotalReducedDamage(entityId, damageByType).getOrThrow()

        assertEquals(150, totalDamage) // 90 + 60
    }

    @Test
    fun `getAllResistances returns all resistance skills`() {
        val entityId = "player"
        val component = SkillComponent(
            skills = mapOf(
                "Fire Resistance" to SkillState(level = 20, unlocked = true),
                "Poison Resistance" to SkillState(level = 50, unlocked = true),
                "Slashing Resistance" to SkillState(level = 10, unlocked = true),
                "Sword Fighting" to SkillState(level = 30, unlocked = true) // Not a resistance
            )
        )

        mockRepo.setComponent(entityId, component)

        val resistances = calculator.getAllResistances(entityId).getOrThrow()

        assertEquals(3, resistances.size)
        assertEquals(20, resistances["Fire"])
        assertEquals(50, resistances["Poison"])
        assertEquals(10, resistances["Slashing"])
        assertNull(resistances["Sword Fighting"])
    }

    @Test
    fun `getAllResistances excludes unlocked resistances`() {
        val entityId = "player"
        val component = SkillComponent(
            skills = mapOf(
                "Fire Resistance" to SkillState(level = 20, unlocked = true),
                "Poison Resistance" to SkillState(level = 50, unlocked = false) // Not unlocked
            )
        )

        mockRepo.setComponent(entityId, component)

        val resistances = calculator.getAllResistances(entityId).getOrThrow()

        assertEquals(1, resistances.size)
        assertEquals(20, resistances["Fire"])
        assertNull(resistances["Poison"])
    }

    @Test
    fun `calculateReduction handles temp buffs correctly`() {
        val entityId = "player"
        val component = SkillComponent(
            skills = mapOf(
                "Fire Resistance" to SkillState(
                    level = 20,
                    unlocked = true,
                    tempBuffs = 10 // Effective level = 30
                )
            )
        )

        mockRepo.setComponent(entityId, component)

        val result = calculator.calculateReduction(entityId, "Fire", damage = 100).getOrThrow()

        // Effective level 30 = 15% reduction
        assertEquals(85, result.reducedDamage)
        assertEquals(15, result.reductionAmount)
        assertEquals(30, result.resistanceLevel) // Should use effective level
    }

    @Test
    fun `calculateReduction handles negative damage gracefully`() {
        val entityId = "player"
        mockRepo.setComponent(entityId, SkillComponent())

        val result = calculator.calculateReduction(entityId, "Fire", damage = -10)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("non-negative") == true)
    }
}
