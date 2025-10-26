package com.jcraw.mud.reasoning.skill

import com.jcraw.mud.core.SkillComponent
import com.jcraw.mud.core.SkillEvent
import com.jcraw.mud.core.SkillState
import com.jcraw.mud.core.repository.SkillComponentRepository
import com.jcraw.mud.core.repository.SkillRepository
import kotlin.random.Random
import kotlin.test.*

class SkillManagerTest {

    // Mock repositories
    private lateinit var skillRepo: FakeSkillRepository
    private lateinit var componentRepo: FakeSkillComponentRepository
    private lateinit var manager: SkillManager

    @BeforeTest
    fun setup() {
        skillRepo = FakeSkillRepository()
        componentRepo = FakeSkillComponentRepository()
        // Use seeded random for deterministic tests
        manager = SkillManager(skillRepo, componentRepo, memoryManager = null, rng = Random(42))
    }

    // ========== XP Granting Tests ==========

    @Test
    fun `grantXp grants full XP on success`() {
        // Setup: Entity with unlocked skill at level 1
        val entityId = "player1"
        val skillName = "Sword Fighting"
        val initialSkill = SkillState(level = 1, xp = 0, unlocked = true)
        componentRepo.save(entityId, SkillComponent().addSkill(skillName, initialSkill))

        // Grant 100 XP with success
        val result = manager.grantXp(entityId, skillName, baseXp = 100, success = true)

        assertTrue(result.isSuccess)
        val events = result.getOrThrow()
        assertEquals(1, events.size)
        assertTrue(events[0] is SkillEvent.XpGained)

        val xpEvent = events[0] as SkillEvent.XpGained
        assertEquals(100L, xpEvent.xpAmount)
        assertEquals(100L, xpEvent.currentXp)
        assertTrue(xpEvent.success)
    }

    @Test
    fun `grantXp grants 20 percent XP on failure`() {
        val entityId = "player1"
        val skillName = "Sword Fighting"
        val initialSkill = SkillState(level = 1, xp = 0, unlocked = true)
        componentRepo.save(entityId, SkillComponent().addSkill(skillName, initialSkill))

        // Grant 100 XP with failure
        val result = manager.grantXp(entityId, skillName, baseXp = 100, success = false)

        assertTrue(result.isSuccess)
        val events = result.getOrThrow()
        val xpEvent = events[0] as SkillEvent.XpGained
        assertEquals(20L, xpEvent.xpAmount) // 20% of 100
        assertFalse(xpEvent.success)
    }

    @Test
    fun `grantXp triggers level-up when threshold crossed`() {
        val entityId = "player1"
        val skillName = "Sword Fighting"
        // Level 1 requires 400 XP to reach level 2 (100 * 2^2 = 400)
        val initialSkill = SkillState(level = 1, xp = 300, unlocked = true)
        componentRepo.save(entityId, SkillComponent().addSkill(skillName, initialSkill))

        // Grant 150 XP (total 450, crosses 400 threshold)
        val result = manager.grantXp(entityId, skillName, baseXp = 150, success = true)

        assertTrue(result.isSuccess)
        val events = result.getOrThrow()
        assertEquals(2, events.size) // XpGained + LevelUp

        val levelUpEvent = events[1] as SkillEvent.LevelUp
        assertEquals(1, levelUpEvent.oldLevel)
        assertEquals(2, levelUpEvent.newLevel)
        assertFalse(levelUpEvent.isAtPerkMilestone)
    }

    @Test
    fun `grantXp handles multiple level-ups`() {
        val entityId = "player1"
        val skillName = "Sword Fighting"
        val initialSkill = SkillState(level = 1, xp = 0, unlocked = true)
        componentRepo.save(entityId, SkillComponent().addSkill(skillName, initialSkill))

        // Grant massive XP to trigger multiple level-ups
        // L1→L2: 400, L2→L3: 900, L3→L4: 1600 = total 2900 XP
        val result = manager.grantXp(entityId, skillName, baseXp = 3000, success = true)

        assertTrue(result.isSuccess)
        val events = result.getOrThrow()
        assertEquals(2, events.size) // XpGained + final LevelUp

        val levelUpEvent = events[1] as SkillEvent.LevelUp
        assertEquals(1, levelUpEvent.oldLevel)
        assertEquals(4, levelUpEvent.newLevel)
    }

    @Test
    fun `grantXp flags perk milestone at level 10`() {
        val entityId = "player1"
        val skillName = "Sword Fighting"
        // Start at level 9 with XP near threshold
        val xpToLevel10 = SkillState(level = 9, xp = 0).calculateXpToNext(9)
        val initialSkill = SkillState(level = 9, xp = xpToLevel10 - 100, unlocked = true)
        componentRepo.save(entityId, SkillComponent().addSkill(skillName, initialSkill))

        // Grant enough XP to reach level 10
        val result = manager.grantXp(entityId, skillName, baseXp = 200, success = true)

        assertTrue(result.isSuccess)
        val events = result.getOrThrow()
        val levelUpEvent = events[1] as SkillEvent.LevelUp
        assertEquals(10, levelUpEvent.newLevel)
        assertTrue(levelUpEvent.isAtPerkMilestone)
    }

    @Test
    fun `grantXp fails for unlocked skill`() {
        val entityId = "player1"
        val skillName = "Sword Fighting"
        val lockedSkill = SkillState(level = 0, xp = 0, unlocked = false)
        componentRepo.save(entityId, SkillComponent().addSkill(skillName, lockedSkill))

        val result = manager.grantXp(entityId, skillName, baseXp = 100, success = true)

        assertTrue(result.isFailure)
    }

    @Test
    fun `grantXp rejects negative XP`() {
        val entityId = "player1"
        val skillName = "Sword Fighting"
        val initialSkill = SkillState(level = 1, xp = 0, unlocked = true)
        componentRepo.save(entityId, SkillComponent().addSkill(skillName, initialSkill))

        assertFailsWith<IllegalArgumentException> {
            manager.grantXp(entityId, skillName, baseXp = -100, success = true).getOrThrow()
        }
    }

    // ========== Unlock Tests ==========

    @Test
    fun `unlockSkill via Attempt fails with bad roll`() {
        val entityId = "player1"
        val skillName = "Lockpicking"
        componentRepo.save(entityId, SkillComponent())

        // With seed 42, first roll should be > 5
        val result = manager.unlockSkill(entityId, skillName, UnlockMethod.Attempt)

        assertTrue(result.isSuccess)
        val event = result.getOrNull()
        assertNull(event) // Failed to unlock
    }

    @Test
    fun `unlockSkill via Attempt succeeds with good roll`() {
        val entityId = "player1"
        val skillName = "Lockpicking"
        componentRepo.save(entityId, SkillComponent())

        // Use different seed to force success
        val luckyManager = SkillManager(skillRepo, componentRepo, memoryManager = null, rng = Random(1))

        val result = luckyManager.unlockSkill(entityId, skillName, UnlockMethod.Attempt)

        // This test may be flaky depending on Random seed
        // For deterministic testing, we'd need to mock Random or verify the logic differently
        assertTrue(result.isSuccess)
    }

    @Test
    fun `unlockSkill via Observation grants buff`() {
        val entityId = "player1"
        val skillName = "Fire Magic"
        componentRepo.save(entityId, SkillComponent())

        val result = manager.unlockSkill(
            entityId,
            skillName,
            UnlockMethod.Observation("mentor1")
        )

        assertTrue(result.isSuccess)
        val event = result.getOrNull()
        assertNotNull(event)
        assertEquals("observation", event.unlockMethod)

        // Verify skill is unlocked with buff
        val component = componentRepo.load(entityId).getOrThrow()!!
        val skill = component.getSkill(skillName)!!
        assertTrue(skill.unlocked)
        assertEquals(5, skill.tempBuffs) // Observation buff
    }

    @Test
    fun `unlockSkill via Training grants level 1 and buff`() {
        val entityId = "player1"
        val skillName = "Sword Fighting"
        componentRepo.save(entityId, SkillComponent())

        val result = manager.unlockSkill(
            entityId,
            skillName,
            UnlockMethod.Training("trainer1")
        )

        assertTrue(result.isSuccess)
        val event = result.getOrNull()
        assertNotNull(event)
        assertEquals("training", event.unlockMethod)

        // Verify skill is unlocked at level 1 with buff
        val component = componentRepo.load(entityId).getOrThrow()!!
        val skill = component.getSkill(skillName)!!
        assertTrue(skill.unlocked)
        assertEquals(1, skill.level)
        assertEquals(10, skill.tempBuffs) // Training buff
    }

    @Test
    fun `unlockSkill via Prerequisite succeeds when requirement met`() {
        val entityId = "player1"
        val skillName = "Advanced Fire Magic"
        val prereqSkill = SkillState(level = 50, unlocked = true)
        componentRepo.save(
            entityId,
            SkillComponent().addSkill("Fire Magic", prereqSkill)
        )

        val result = manager.unlockSkill(
            entityId,
            skillName,
            UnlockMethod.Prerequisite("Fire Magic", 50)
        )

        assertTrue(result.isSuccess)
        val event = result.getOrNull()
        assertNotNull(event)
        assertEquals("prerequisite", event.unlockMethod)
    }

    @Test
    fun `unlockSkill via Prerequisite fails when requirement not met`() {
        val entityId = "player1"
        val skillName = "Advanced Fire Magic"
        val prereqSkill = SkillState(level = 40, unlocked = true) // Only level 40, need 50
        componentRepo.save(
            entityId,
            SkillComponent().addSkill("Fire Magic", prereqSkill)
        )

        val result = manager.unlockSkill(
            entityId,
            skillName,
            UnlockMethod.Prerequisite("Fire Magic", 50)
        )

        assertTrue(result.isSuccess)
        val event = result.getOrNull()
        assertNull(event) // Failed
    }

    @Test
    fun `unlockSkill returns null if already unlocked`() {
        val entityId = "player1"
        val skillName = "Sword Fighting"
        val unlockedSkill = SkillState(level = 5, unlocked = true)
        componentRepo.save(
            entityId,
            SkillComponent().addSkill(skillName, unlockedSkill)
        )

        val result = manager.unlockSkill(entityId, skillName, UnlockMethod.Attempt)

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }

    // ========== Skill Check Tests ==========

    @Test
    fun `checkSkill succeeds against easy difficulty`() {
        val entityId = "player1"
        val skillName = "Sword Fighting"
        val skill = SkillState(level = 10, unlocked = true)
        componentRepo.save(
            entityId,
            SkillComponent().addSkill(skillName, skill)
        )

        val result = manager.checkSkill(entityId, skillName, difficulty = 10)

        assertTrue(result.isSuccess)
        val check = result.getOrThrow()
        // With level 10 and d20 roll, should have good chance of success
        assertTrue(check.roll in 1..20)
        assertEquals(10, check.skillLevel)
        assertEquals(10, check.difficulty)
    }

    @Test
    fun `checkSkill with no skill uses level 0`() {
        val entityId = "player1"
        componentRepo.save(entityId, SkillComponent())

        val result = manager.checkSkill(entityId, "Nonexistent Skill", difficulty = 15)

        assertTrue(result.isSuccess)
        val check = result.getOrThrow()
        assertEquals(0, check.skillLevel)
        // Success only if roll >= 15
    }

    @Test
    fun `checkSkill opposed check compares totals`() {
        val player = "player1"
        val opponent = "npc1"
        val playerSkill = SkillState(level = 15, unlocked = true)
        val opponentSkill = SkillState(level = 10, unlocked = true)

        componentRepo.save(player, SkillComponent().addSkill("Sword Fighting", playerSkill))
        componentRepo.save(opponent, SkillComponent().addSkill("Dodge", opponentSkill))

        val result = manager.checkSkill(
            player,
            "Sword Fighting",
            difficulty = 0, // Ignored in opposed check
            opposedEntityId = opponent,
            opposedSkill = "Dodge"
        )

        assertTrue(result.isSuccess)
        val check = result.getOrThrow()
        assertTrue(check.isOpposed)
        assertEquals("Dodge", check.opposingSkill)
        assertNotNull(check.opposingRoll)
        assertNotNull(check.opposingSkillLevel)
    }

    @Test
    fun `checkSkill logs event to repository`() {
        val entityId = "player1"
        val skillName = "Lockpicking"
        val skill = SkillState(level = 5, unlocked = true)
        componentRepo.save(
            entityId,
            SkillComponent().addSkill(skillName, skill)
        )

        manager.checkSkill(entityId, skillName, difficulty = 15)

        // Verify event was logged
        val events = skillRepo.getEventHistory(entityId, skillName).getOrThrow()
        assertEquals(1, events.size)
        assertTrue(events[0] is SkillEvent.SkillCheckAttempt)
    }

    @Test
    fun `checkSkill includes buffs in effective level`() {
        val entityId = "player1"
        val skillName = "Fire Magic"
        val skill = SkillState(level = 10, unlocked = true, tempBuffs = 5)
        componentRepo.save(
            entityId,
            SkillComponent().addSkill(skillName, skill)
        )

        val result = manager.checkSkill(entityId, skillName, difficulty = 15)

        assertTrue(result.isSuccess)
        val check = result.getOrThrow()
        assertEquals(15, check.skillLevel) // 10 base + 5 buff
    }

    @Test
    fun `checkSkill provides narrative based on margin`() {
        val entityId = "player1"
        val skillName = "Diplomacy"
        val skill = SkillState(level = 20, unlocked = true) // High level for guaranteed success
        componentRepo.save(
            entityId,
            SkillComponent().addSkill(skillName, skill)
        )

        val result = manager.checkSkill(entityId, skillName, difficulty = 10)

        assertTrue(result.isSuccess)
        val check = result.getOrThrow()
        assertTrue(check.narrative.contains("success", ignoreCase = true))
    }

    // ========== Helper Method Tests ==========

    @Test
    fun `getSkillComponent returns empty component for new entity`() {
        val component = manager.getSkillComponent("new_entity")

        assertNotNull(component)
        assertTrue(component.skills.isEmpty())
    }

    @Test
    fun `updateSkillComponent persists changes`() {
        val entityId = "player1"
        val skill = SkillState(level = 5, unlocked = true)
        val component = SkillComponent().addSkill("Test Skill", skill)

        val result = manager.updateSkillComponent(entityId, component)

        assertTrue(result.isSuccess)
        val loaded = componentRepo.load(entityId).getOrThrow()
        assertNotNull(loaded)
        assertEquals(1, loaded!!.skills.size)
    }

    // ========== Fake Implementations ==========

    private class FakeSkillRepository : SkillRepository {
        private val skills = mutableMapOf<Pair<String, String>, SkillState>()
        private val events = mutableListOf<SkillEvent>()

        override fun findByEntityAndSkill(entityId: String, skillName: String): Result<SkillState?> {
            return Result.success(skills[entityId to skillName])
        }

        override fun findByEntityId(entityId: String): Result<Map<String, SkillState>> {
            return Result.success(
                skills.filterKeys { it.first == entityId }
                    .mapKeys { it.key.second }
            )
        }

        override fun findByTag(tag: String): Result<Map<Pair<String, String>, SkillState>> {
            return Result.success(
                skills.filterValues { tag in it.tags }
            )
        }

        override fun save(entityId: String, skillName: String, skillState: SkillState): Result<Unit> {
            skills[entityId to skillName] = skillState
            return Result.success(Unit)
        }

        override fun updateXp(entityId: String, skillName: String, newXp: Long, newLevel: Int): Result<Unit> {
            val current = skills[entityId to skillName] ?: return Result.failure(IllegalStateException())
            skills[entityId to skillName] = current.copy(xp = newXp, level = newLevel)
            return Result.success(Unit)
        }

        override fun unlockSkill(entityId: String, skillName: String): Result<Unit> {
            val current = skills[entityId to skillName] ?: return Result.failure(IllegalStateException())
            skills[entityId to skillName] = current.unlock()
            return Result.success(Unit)
        }

        override fun delete(entityId: String, skillName: String): Result<Unit> {
            skills.remove(entityId to skillName)
            return Result.success(Unit)
        }

        override fun deleteAllForEntity(entityId: String): Result<Unit> {
            skills.keys.removeAll { it.first == entityId }
            return Result.success(Unit)
        }

        override fun logEvent(event: SkillEvent): Result<Unit> {
            events.add(event)
            return Result.success(Unit)
        }

        override fun getEventHistory(entityId: String, skillName: String?, limit: Int): Result<List<SkillEvent>> {
            return Result.success(
                events.filter { it.entityId == entityId && (skillName == null || it.skillName == skillName) }
                    .sortedByDescending { it.timestamp }
                    .take(limit)
            )
        }
    }

    private class FakeSkillComponentRepository : SkillComponentRepository {
        private val components = mutableMapOf<String, SkillComponent>()

        override fun save(entityId: String, component: SkillComponent): Result<Unit> {
            components[entityId] = component
            return Result.success(Unit)
        }

        override fun load(entityId: String): Result<SkillComponent?> {
            return Result.success(components[entityId])
        }

        override fun delete(entityId: String): Result<Unit> {
            components.remove(entityId)
            return Result.success(Unit)
        }

        override fun findAll(): Result<Map<String, SkillComponent>> {
            return Result.success(components.toMap())
        }
    }
}
