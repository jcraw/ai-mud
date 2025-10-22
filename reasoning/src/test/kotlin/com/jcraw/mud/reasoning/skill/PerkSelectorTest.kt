package com.jcraw.mud.reasoning.skill

import com.jcraw.mud.core.Perk
import com.jcraw.mud.core.PerkType
import com.jcraw.mud.core.SkillComponent
import com.jcraw.mud.core.SkillEvent
import com.jcraw.mud.core.SkillState
import com.jcraw.mud.core.repository.SkillComponentRepository
import kotlin.test.*

class PerkSelectorTest {

    private lateinit var componentRepo: FakeSkillComponentRepository
    private lateinit var perkSelector: PerkSelector

    @BeforeTest
    fun setup() {
        componentRepo = FakeSkillComponentRepository()
        perkSelector = PerkSelector(componentRepo)
    }

    // ========== Perk Choice Retrieval Tests ==========

    @Test
    fun `getPerkChoices returns 2 perks at milestone level`() {
        val choices = perkSelector.getPerkChoices("Sword Fighting", level = 10)

        assertEquals(2, choices.size)
        assertEquals("Quick Strike", choices[0].name)
        assertEquals("Feint", choices[1].name)
        assertEquals(PerkType.ABILITY, choices[0].type)
    }

    @Test
    fun `getPerkChoices returns different perks at different milestones`() {
        val level10Choices = perkSelector.getPerkChoices("Sword Fighting", level = 10)
        val level20Choices = perkSelector.getPerkChoices("Sword Fighting", level = 20)

        assertNotEquals(level10Choices[0].name, level20Choices[0].name)
        assertEquals("Blade Mastery", level20Choices[0].name)
        assertEquals(PerkType.PASSIVE, level20Choices[0].type)
    }

    @Test
    fun `getPerkChoices returns empty list for non-milestone level`() {
        val choices = perkSelector.getPerkChoices("Sword Fighting", level = 9)

        assertTrue(choices.isEmpty())
    }

    @Test
    fun `getPerkChoices returns empty list for zero level`() {
        val choices = perkSelector.getPerkChoices("Sword Fighting", level = 0)

        assertTrue(choices.isEmpty())
    }

    @Test
    fun `getPerkChoices returns empty list for skill without perks`() {
        val choices = perkSelector.getPerkChoices("Nonexistent Skill", level = 10)

        assertTrue(choices.isEmpty())
    }

    // ========== Perk Selection Tests ==========

    @Test
    fun `selectPerk adds perk to skill state`() {
        // Setup: Entity with Sword Fighting at level 10, no perks chosen
        val entityId = "player1"
        val skillName = "Sword Fighting"
        val skillState = SkillState(level = 10, unlocked = true)
        componentRepo.save(entityId, SkillComponent().addSkill(skillName, skillState))

        // Get available perks and choose the first one
        val choices = perkSelector.getPerkChoices(skillName, level = 10)
        val chosenPerk = choices[0] // "Quick Strike"

        val event = perkSelector.selectPerk(entityId, skillName, chosenPerk)

        assertNotNull(event)
        assertTrue(event is SkillEvent.PerkUnlocked)
        assertEquals(entityId, event.entityId)
        assertEquals(skillName, event.skillName)
        assertEquals(chosenPerk, event.perk)

        // Verify perk was added to skill state
        val component = componentRepo.load(entityId).getOrThrow()!!
        val updatedSkill = component.getSkill(skillName)!!
        assertEquals(1, updatedSkill.perks.size)
        assertEquals("Quick Strike", updatedSkill.perks[0].name)
    }

    @Test
    fun `selectPerk rejects invalid perk choice`() {
        val entityId = "player1"
        val skillName = "Sword Fighting"
        val skillState = SkillState(level = 10, unlocked = true)
        componentRepo.save(entityId, SkillComponent().addSkill(skillName, skillState))

        // Try to select a perk from a different skill/milestone
        val invalidPerk = Perk(
            name = "Invalid Perk",
            description = "This perk doesn't exist for this skill/level",
            type = PerkType.PASSIVE
        )

        val event = perkSelector.selectPerk(entityId, skillName, invalidPerk)

        assertNull(event)
    }

    @Test
    fun `selectPerk rejects perk when no pending choice`() {
        val entityId = "player1"
        val skillName = "Sword Fighting"
        // Skill at level 10 with perk already chosen
        val existingPerk = Perk("Quick Strike", "...", PerkType.ABILITY)
        val skillState = SkillState(level = 10, unlocked = true, perks = listOf(existingPerk))
        componentRepo.save(entityId, SkillComponent().addSkill(skillName, skillState))

        // Try to choose another perk
        val choices = perkSelector.getPerkChoices(skillName, level = 10)
        val event = perkSelector.selectPerk(entityId, skillName, choices[1])

        assertNull(event) // Should fail because perk already chosen for this milestone
    }

    @Test
    fun `selectPerk returns null for non-existent skill`() {
        val entityId = "player1"
        componentRepo.save(entityId, SkillComponent())

        val fakePerk = Perk("Test", "...", PerkType.ABILITY)
        val event = perkSelector.selectPerk(entityId, "Nonexistent Skill", fakePerk)

        assertNull(event)
    }

    // ========== Pending Perk Detection Tests ==========

    @Test
    fun `hasPendingPerkChoice returns true at milestone without chosen perk`() {
        val entityId = "player1"
        val skillName = "Fire Magic"
        val skillState = SkillState(level = 10, unlocked = true) // No perks yet
        componentRepo.save(entityId, SkillComponent().addSkill(skillName, skillState))

        val hasPending = perkSelector.hasPendingPerkChoice(entityId, skillName)

        assertTrue(hasPending)
    }

    @Test
    fun `hasPendingPerkChoice returns false when perk already chosen`() {
        val entityId = "player1"
        val skillName = "Fire Magic"
        val existingPerk = Perk("Fireball Volley", "...", PerkType.ABILITY)
        val skillState = SkillState(level = 10, unlocked = true, perks = listOf(existingPerk))
        componentRepo.save(entityId, SkillComponent().addSkill(skillName, skillState))

        val hasPending = perkSelector.hasPendingPerkChoice(entityId, skillName)

        assertFalse(hasPending)
    }

    @Test
    fun `hasPendingPerkChoice returns false at non-milestone level`() {
        val entityId = "player1"
        val skillName = "Sword Fighting"
        val skillState = SkillState(level = 9, unlocked = true)
        componentRepo.save(entityId, SkillComponent().addSkill(skillName, skillState))

        val hasPending = perkSelector.hasPendingPerkChoice(entityId, skillName)

        assertFalse(hasPending)
    }

    @Test
    fun `hasPendingPerkChoice handles multiple milestones`() {
        val entityId = "player1"
        val skillName = "Sword Fighting"
        // Level 20 with only 1 perk (should have 2 perks by now)
        val perk1 = Perk("Quick Strike", "...", PerkType.ABILITY)
        val skillState = SkillState(level = 20, unlocked = true, perks = listOf(perk1))
        componentRepo.save(entityId, SkillComponent().addSkill(skillName, skillState))

        val hasPending = perkSelector.hasPendingPerkChoice(entityId, skillName)

        assertTrue(hasPending) // Should have 2 perks, only has 1
    }

    // ========== Get All Pending Perks Tests ==========

    @Test
    fun `getAllPendingPerkChoices returns all skills with pending perks`() {
        val entityId = "player1"
        val skill1 = SkillState(level = 10, unlocked = true) // Pending at 10
        val perk = Perk("Quick Strike", "...", PerkType.ABILITY)
        val skill2 = SkillState(level = 20, unlocked = true, perks = listOf(perk)) // Pending at 20
        val skill3 = SkillState(level = 5, unlocked = true) // No pending

        val component = SkillComponent()
            .addSkill("Sword Fighting", skill1)
            .addSkill("Fire Magic", skill2)
            .addSkill("Lockpicking", skill3)

        componentRepo.save(entityId, component)

        val pendingPerks = perkSelector.getAllPendingPerkChoices(entityId)

        assertEquals(2, pendingPerks.size)
        assertTrue(pendingPerks.containsKey("Sword Fighting"))
        assertTrue(pendingPerks.containsKey("Fire Magic"))
        assertEquals(2, pendingPerks["Sword Fighting"]?.size)
        assertEquals(2, pendingPerks["Fire Magic"]?.size)
    }

    @Test
    fun `getAllPendingPerkChoices returns empty map for entity with no pending perks`() {
        val entityId = "player1"
        val perk = Perk("Quick Strike", "...", PerkType.ABILITY)
        val skill = SkillState(level = 10, unlocked = true, perks = listOf(perk))
        componentRepo.save(entityId, SkillComponent().addSkill("Sword Fighting", skill))

        val pendingPerks = perkSelector.getAllPendingPerkChoices(entityId)

        assertTrue(pendingPerks.isEmpty())
    }

    // ========== Helper Method Tests ==========

    @Test
    fun `hasPerks returns true for skills with defined perks`() {
        assertTrue(perkSelector.hasPerks("Sword Fighting"))
        assertTrue(perkSelector.hasPerks("Fire Magic"))
        assertTrue(perkSelector.hasPerks("Stealth"))
    }

    @Test
    fun `hasPerks returns false for skills without defined perks`() {
        assertFalse(perkSelector.hasPerks("Nonexistent Skill"))
        assertFalse(perkSelector.hasPerks("Undefined Combat Skill"))
    }

    @Test
    fun `getMilestoneNumber returns correct milestone`() {
        assertEquals(1, perkSelector.getMilestoneNumber(10))
        assertEquals(2, perkSelector.getMilestoneNumber(20))
        assertEquals(3, perkSelector.getMilestoneNumber(30))
        assertEquals(10, perkSelector.getMilestoneNumber(100))
    }

    @Test
    fun `getMilestoneNumber returns zero for non-milestone levels`() {
        assertEquals(0, perkSelector.getMilestoneNumber(0))
        assertEquals(0, perkSelector.getMilestoneNumber(9))
        assertEquals(0, perkSelector.getMilestoneNumber(11))
        assertEquals(0, perkSelector.getMilestoneNumber(15))
    }

    @Test
    fun `getSkillComponent returns component for entity`() {
        val entityId = "player1"
        val skill = SkillState(level = 5, unlocked = true)
        componentRepo.save(entityId, SkillComponent().addSkill("Test Skill", skill))

        val component = perkSelector.getSkillComponent(entityId)

        assertNotNull(component)
        assertEquals(1, component.skills.size)
    }

    // ========== Fake Implementation ==========

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
