package com.jcraw.mud.memory.skill

import com.jcraw.mud.core.*
import org.junit.jupiter.api.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SkillDatabaseTest {

    private lateinit var database: SkillDatabase
    private lateinit var skillRepo: SQLiteSkillRepository
    private lateinit var componentRepo: SQLiteSkillComponentRepository

    @BeforeAll
    fun setup() {
        database = SkillDatabase(":memory:")
        skillRepo = SQLiteSkillRepository(database)
        componentRepo = SQLiteSkillComponentRepository(database)
    }

    @BeforeEach
    fun clearData() {
        database.clearAll()
    }

    @AfterAll
    fun teardown() {
        database.close()
    }

    // ===== SkillRepository Tests =====

    @Test
    fun `save and find skill by entity and skill name`() {
        val skillState = SkillState(
            level = 5,
            xp = 100,
            unlocked = true,
            tags = listOf("combat", "melee"),
            perks = listOf(Perk("Quick Strike", "Fast attack", PerkType.ABILITY)),
            resourceType = null,
            tempBuffs = 0
        )

        skillRepo.save("player1", "Sword Fighting", skillState)

        val result = skillRepo.findByEntityAndSkill("player1", "Sword Fighting")
        assertTrue(result.isSuccess)
        val found = result.getOrNull()
        assertNotNull(found)
        assertEquals(5, found.level)
        assertEquals(100, found.xp)
        assertEquals(true, found.unlocked)
        assertEquals(listOf("combat", "melee"), found.tags)
        assertEquals(1, found.perks.size)
        assertEquals("Quick Strike", found.perks[0].name)
    }

    @Test
    fun `find non-existent skill returns null`() {
        val result = skillRepo.findByEntityAndSkill("player1", "Non-Existent")
        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }

    @Test
    fun `find all skills for entity`() {
        val swordSkill = SkillState(level = 5, xp = 100, unlocked = true, tags = listOf("combat"))
        val fireSkill = SkillState(level = 3, xp = 50, unlocked = true, tags = listOf("magic"))

        skillRepo.save("player1", "Sword Fighting", swordSkill)
        skillRepo.save("player1", "Fire Magic", fireSkill)

        val result = skillRepo.findByEntityId("player1")
        assertTrue(result.isSuccess)
        val skills = result.getOrNull()!!
        assertEquals(2, skills.size)
        assertEquals(5, skills["Sword Fighting"]?.level)
        assertEquals(3, skills["Fire Magic"]?.level)
    }

    @Test
    fun `find skills by tag`() {
        val swordSkill = SkillState(level = 5, unlocked = true, tags = listOf("combat", "melee"))
        val fireSkill = SkillState(level = 3, unlocked = true, tags = listOf("magic", "elemental"))
        val earthSkill = SkillState(level = 2, unlocked = true, tags = listOf("magic", "elemental"))

        skillRepo.save("player1", "Sword Fighting", swordSkill)
        skillRepo.save("player1", "Fire Magic", fireSkill)
        skillRepo.save("player2", "Earth Magic", earthSkill)

        val result = skillRepo.findByTag("elemental")
        assertTrue(result.isSuccess)
        val skills = result.getOrNull()!!
        assertEquals(2, skills.size)
        assertTrue(skills.containsKey("player1" to "Fire Magic"))
        assertTrue(skills.containsKey("player2" to "Earth Magic"))
    }

    @Test
    fun `update XP updates skill in database`() {
        val skillState = SkillState(level = 5, xp = 100, unlocked = true)
        skillRepo.save("player1", "Sword Fighting", skillState)

        skillRepo.updateXp("player1", "Sword Fighting", 250, 6)

        val result = skillRepo.findByEntityAndSkill("player1", "Sword Fighting")
        val updated = result.getOrNull()!!
        assertEquals(6, updated.level)
        assertEquals(250, updated.xp)
    }

    @Test
    fun `unlock skill sets unlocked flag`() {
        val skillState = SkillState(level = 0, xp = 0, unlocked = false)
        skillRepo.save("player1", "Fire Magic", skillState)

        skillRepo.unlockSkill("player1", "Fire Magic")

        val result = skillRepo.findByEntityAndSkill("player1", "Fire Magic")
        val updated = result.getOrNull()!!
        assertEquals(true, updated.unlocked)
    }

    @Test
    fun `delete skill removes it from database`() {
        val skillState = SkillState(level = 5, unlocked = true)
        skillRepo.save("player1", "Sword Fighting", skillState)

        skillRepo.delete("player1", "Sword Fighting")

        val result = skillRepo.findByEntityAndSkill("player1", "Sword Fighting")
        assertNull(result.getOrNull())
    }

    @Test
    fun `delete all skills for entity removes all`() {
        skillRepo.save("player1", "Sword Fighting", SkillState(level = 5, unlocked = true))
        skillRepo.save("player1", "Fire Magic", SkillState(level = 3, unlocked = true))
        skillRepo.save("player2", "Bow Accuracy", SkillState(level = 4, unlocked = true))

        skillRepo.deleteAllForEntity("player1")

        val player1Skills = skillRepo.findByEntityId("player1").getOrNull()!!
        val player2Skills = skillRepo.findByEntityId("player2").getOrNull()!!
        assertEquals(0, player1Skills.size)
        assertEquals(1, player2Skills.size)
    }

    @Test
    fun `log skill event stores event in database`() {
        val event = SkillEvent.XpGained(
            entityId = "player1",
            skillName = "Sword Fighting",
            xpAmount = 100,
            currentXp = 250,
            currentLevel = 5,
            success = true,
            timestamp = 123456789L
        )

        skillRepo.logEvent(event)

        val history = skillRepo.getEventHistory("player1", "Sword Fighting", 10)
        assertTrue(history.isSuccess)
        val events = history.getOrNull()!!
        assertEquals(1, events.size)
        assertTrue(events[0] is SkillEvent.XpGained)
        assertEquals("player1", events[0].entityId)
        assertEquals("Sword Fighting", events[0].skillName)
    }

    @Test
    fun `get event history returns events in reverse chronological order`() {
        val event1 = SkillEvent.XpGained("player1", "Sword Fighting", 50, 50, 1, true, timestamp = 100L)
        val event2 = SkillEvent.LevelUp("player1", "Sword Fighting", 1, 2, false, timestamp = 200L)
        val event3 = SkillEvent.XpGained("player1", "Sword Fighting", 100, 150, 2, true, timestamp = 300L)

        skillRepo.logEvent(event1)
        skillRepo.logEvent(event2)
        skillRepo.logEvent(event3)

        val history = skillRepo.getEventHistory("player1", "Sword Fighting", 10)
        val events = history.getOrNull()!!
        assertEquals(3, events.size)
        assertEquals(300L, events[0].timestamp) // Most recent first
        assertEquals(200L, events[1].timestamp)
        assertEquals(100L, events[2].timestamp)
    }

    @Test
    fun `get event history limits results`() {
        repeat(10) { i ->
            val event = SkillEvent.XpGained("player1", "Sword Fighting", 10, 10L * i, 1, true, timestamp = i.toLong())
            skillRepo.logEvent(event)
        }

        val history = skillRepo.getEventHistory("player1", "Sword Fighting", limit = 5)
        val events = history.getOrNull()!!
        assertEquals(5, events.size)
    }

    @Test
    fun `get event history for entity returns all skills when skillName is null`() {
        skillRepo.logEvent(SkillEvent.XpGained("player1", "Sword Fighting", 50, 50, 1, true, timestamp = 100L))
        skillRepo.logEvent(SkillEvent.XpGained("player1", "Fire Magic", 30, 30, 1, true, timestamp = 200L))

        val history = skillRepo.getEventHistory("player1", skillName = null, limit = 10)
        val events = history.getOrNull()!!
        assertEquals(2, events.size)
    }

    // ===== SkillComponentRepository Tests =====

    @Test
    fun `save and load skill component`() {
        val component = SkillComponent(
            skills = mapOf(
                "Sword Fighting" to SkillState(level = 5, xp = 100, unlocked = true),
                "Fire Magic" to SkillState(level = 3, xp = 50, unlocked = true)
            )
        )

        componentRepo.save("player1", component)

        val result = componentRepo.load("player1")
        assertTrue(result.isSuccess)
        val loaded = result.getOrNull()
        assertNotNull(loaded)
        assertEquals(2, loaded.skills.size)
        assertEquals(5, loaded.skills["Sword Fighting"]?.level)
        assertEquals(3, loaded.skills["Fire Magic"]?.level)
    }

    @Test
    fun `load non-existent component returns null`() {
        val result = componentRepo.load("non-existent")
        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }

    @Test
    fun `delete component removes it from database`() {
        val component = SkillComponent(
            skills = mapOf("Sword Fighting" to SkillState(level = 5, unlocked = true))
        )
        componentRepo.save("player1", component)

        componentRepo.delete("player1")

        val result = componentRepo.load("player1")
        assertNull(result.getOrNull())
    }

    @Test
    fun `find all returns all components`() {
        val component1 = SkillComponent(skills = mapOf("Sword Fighting" to SkillState(level = 5, unlocked = true)))
        val component2 = SkillComponent(skills = mapOf("Fire Magic" to SkillState(level = 3, unlocked = true)))

        componentRepo.save("player1", component1)
        componentRepo.save("player2", component2)

        val result = componentRepo.findAll()
        assertTrue(result.isSuccess)
        val components = result.getOrNull()!!
        assertEquals(2, components.size)
        assertTrue(components.containsKey("player1"))
        assertTrue(components.containsKey("player2"))
    }

    @Test
    fun `save component with complex perks data`() {
        val perk1 = Perk("Quick Strike", "Fast attack", PerkType.ABILITY, mapOf("cooldown" to "3"))
        val perk2 = Perk("+15% Damage", "Passive damage boost", PerkType.PASSIVE, mapOf("damageBonus" to "15"))

        val component = SkillComponent(
            skills = mapOf(
                "Sword Fighting" to SkillState(
                    level = 20,
                    xp = 5000,
                    unlocked = true,
                    tags = listOf("combat", "melee", "weapon"),
                    perks = listOf(perk1, perk2),
                    resourceType = null,
                    tempBuffs = 5
                )
            )
        )

        componentRepo.save("player1", component)

        val result = componentRepo.load("player1")
        val loaded = result.getOrNull()!!
        val skill = loaded.skills["Sword Fighting"]!!
        assertEquals(2, skill.perks.size)
        assertEquals("Quick Strike", skill.perks[0].name)
        assertEquals(PerkType.ABILITY, skill.perks[0].type)
        assertEquals("3", skill.perks[0].effectData["cooldown"])
        assertEquals(5, skill.tempBuffs)
    }

    @Test
    fun `save component with resource type skill`() {
        val component = SkillComponent(
            skills = mapOf(
                "Mana Reserve" to SkillState(
                    level = 15,
                    xp = 3000,
                    unlocked = true,
                    tags = listOf("resource", "magic"),
                    resourceType = "mana"
                )
            )
        )

        componentRepo.save("player1", component)

        val result = componentRepo.load("player1")
        val loaded = result.getOrNull()!!
        val skill = loaded.skills["Mana Reserve"]!!
        assertEquals("mana", skill.resourceType)
        assertEquals(150, loaded.getResourcePoolMax("mana")) // 15 * 10
    }
}
