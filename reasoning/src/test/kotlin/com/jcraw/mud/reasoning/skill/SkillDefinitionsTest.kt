package com.jcraw.mud.reasoning.skill

import com.jcraw.mud.core.SkillState
import kotlin.test.*

class SkillDefinitionsTest {

    @Test
    fun `skill catalog has expected count`() {
        val count = SkillDefinitions.getSkillCount()
        // Expected: 6 stats + 6 combat + 5 rogue + 7 elemental + 3 advanced + 4 resource + 3 resistance + 2 other = 36 skills
        assertTrue(count >= 36, "Expected at least 36 skills, got $count")
    }

    @Test
    fun `all predefined skills are accessible by name`() {
        // Test accessing some key skills
        assertNotNull(SkillDefinitions.getSkill("Strength"))
        assertNotNull(SkillDefinitions.getSkill("Fire Magic"))
        assertNotNull(SkillDefinitions.getSkill("Sword Fighting"))
        assertNotNull(SkillDefinitions.getSkill("Stealth"))
        assertNotNull(SkillDefinitions.getSkill("Mana Reserve"))
        assertNotNull(SkillDefinitions.getSkill("Fire Resistance"))
        assertNotNull(SkillDefinitions.getSkill("Diplomacy"))
    }

    @Test
    fun `skillExists returns correct boolean`() {
        assertTrue(SkillDefinitions.skillExists("Strength"))
        assertTrue(SkillDefinitions.skillExists("Fire Magic"))
        assertFalse(SkillDefinitions.skillExists("NonExistentSkill"))
        assertFalse(SkillDefinitions.skillExists(""))
    }

    @Test
    fun `skill definition has expected structure`() {
        val skill = SkillDefinitions.getSkill("Fire Magic")
        assertNotNull(skill)
        assertEquals("Fire Magic", skill.name)
        assertTrue(skill.description.isNotBlank())
        assertTrue(skill.tags.isNotEmpty())
        assertTrue(skill.tags.contains("magic"))
        assertTrue(skill.baseUnlockChance in 1..10)
    }

    @Test
    fun `tag filtering returns correct skills`() {
        val magicSkills = SkillDefinitions.getSkillsByTag("magic")
        assertTrue(magicSkills.size >= 10, "Expected at least 10 magic skills")
        assertTrue(magicSkills.any { it.name == "Fire Magic" })
        assertTrue(magicSkills.any { it.name == "Water Magic" })

        val combatSkills = SkillDefinitions.getSkillsByTag("combat")
        assertTrue(combatSkills.isNotEmpty())
        assertTrue(combatSkills.any { it.name == "Sword Fighting" })

        val statSkills = SkillDefinitions.getSkillsByTag("stat")
        assertEquals(6, statSkills.size, "Expected exactly 6 core stats")
    }

    @Test
    fun `prerequisite validation works correctly`() {
        val backstab = SkillDefinitions.getSkill("Backstab")
        assertNotNull(backstab)
        assertTrue(backstab.prerequisites.containsKey("Stealth"))
        assertEquals(5, backstab.prerequisites["Stealth"])

        // Test prerequisite checking
        val noSkills = emptyMap<String, SkillState>()
        assertFalse(backstab.prerequisitesMet(noSkills), "Should require Stealth")

        val lowStealth = mapOf(
            "Stealth" to SkillState(level = 3, unlocked = true)
        )
        assertFalse(backstab.prerequisitesMet(lowStealth), "Stealth level too low")

        val highStealth = mapOf(
            "Stealth" to SkillState(level = 10, unlocked = true)
        )
        assertTrue(backstab.prerequisitesMet(highStealth), "Stealth requirement met")

        val lockedStealth = mapOf(
            "Stealth" to SkillState(level = 10, unlocked = false)
        )
        assertFalse(backstab.prerequisitesMet(lockedStealth), "Stealth must be unlocked")
    }

    @Test
    fun `advanced skills have prerequisites`() {
        val summoning = SkillDefinitions.getSkill("Summoning")
        assertNotNull(summoning)
        assertTrue(summoning.prerequisites.isNotEmpty(), "Summoning should have prerequisites")

        val necromancy = SkillDefinitions.getSkill("Necromancy")
        assertNotNull(necromancy)
        assertTrue(necromancy.prerequisites.isNotEmpty(), "Necromancy should have prerequisites")

        val elementalAffinity = SkillDefinitions.getSkill("Elemental Affinity")
        assertNotNull(elementalAffinity)
        assertTrue(elementalAffinity.prerequisites.isNotEmpty(), "Elemental Affinity should have prerequisites")
        assertTrue(elementalAffinity.prerequisites.values.any { it >= 50 }, "Should require high-level skill")
    }

    @Test
    fun `resource skills have resourceType set`() {
        val manaReserve = SkillDefinitions.getSkill("Mana Reserve")
        assertNotNull(manaReserve)
        assertEquals("mana", manaReserve.resourceType)

        val chiReserve = SkillDefinitions.getSkill("Chi Reserve")
        assertNotNull(chiReserve)
        assertEquals("chi", chiReserve.resourceType)
    }

    @Test
    fun `toSkillState creates valid initial state`() {
        val skill = SkillDefinitions.getSkill("Strength")
        assertNotNull(skill)

        val skillState = skill.toSkillState()
        assertEquals(0, skillState.level)
        assertEquals(0L, skillState.xp)
        assertFalse(skillState.unlocked)
        assertEquals(skill.tags, skillState.tags)
        assertTrue(skillState.perks.isEmpty())
        assertEquals(skill.resourceType, skillState.resourceType)
    }

    @Test
    fun `category getters return correct skills`() {
        val coreStats = SkillDefinitions.getCoreStats()
        assertEquals(6, coreStats.size)
        assertTrue(coreStats.all { it.tags.contains("stat") })

        val combatSkills = SkillDefinitions.getCombatSkills()
        assertEquals(6, combatSkills.size)

        val rogueSkills = SkillDefinitions.getRogueSkills()
        assertEquals(5, rogueSkills.size)

        val elementalMagic = SkillDefinitions.getElementalMagic()
        assertEquals(7, elementalMagic.size)

        val advancedMagic = SkillDefinitions.getAdvancedMagic()
        assertEquals(3, advancedMagic.size)

        val resourceSkills = SkillDefinitions.getResourceSkills()
        assertEquals(4, resourceSkills.size)

        val resistanceSkills = SkillDefinitions.getResistanceSkills()
        assertEquals(3, resistanceSkills.size)
    }

    @Test
    fun `all archetypes have valid starter skills`() {
        val archetypes = StarterSkillSets.getArchetypes()
        assertEquals(5, archetypes.size)
        assertTrue(archetypes.contains("Warrior"))
        assertTrue(archetypes.contains("Rogue"))
        assertTrue(archetypes.contains("Mage"))
        assertTrue(archetypes.contains("Cleric"))
        assertTrue(archetypes.contains("Bard"))
    }

    @Test
    fun `starter skill sets are not empty`() {
        assertTrue(StarterSkillSets.warrior.isNotEmpty())
        assertTrue(StarterSkillSets.rogue.isNotEmpty())
        assertTrue(StarterSkillSets.mage.isNotEmpty())
        assertTrue(StarterSkillSets.cleric.isNotEmpty())
        assertTrue(StarterSkillSets.bard.isNotEmpty())
    }

    @Test
    fun `starter skills reference valid skill definitions`() {
        val allStarterSkills = listOf(
            StarterSkillSets.warrior,
            StarterSkillSets.rogue,
            StarterSkillSets.mage,
            StarterSkillSets.cleric,
            StarterSkillSets.bard
        ).flatten()

        // All starter skills should exist in skill definitions
        allStarterSkills.forEach { skillName ->
            assertTrue(
                SkillDefinitions.skillExists(skillName),
                "Starter skill '$skillName' does not exist in skill definitions"
            )
        }
    }

    @Test
    fun `warrior starter skills match archetype`() {
        val skills = StarterSkillSets.warrior
        assertTrue(skills.contains("Strength"), "Warrior should start with Strength")
        assertTrue(skills.any { it.contains("Armor") }, "Warrior should start with armor skill")
        assertTrue(skills.any {
            val def = SkillDefinitions.getSkill(it)
            def?.tags?.contains("combat") == true
        }, "Warrior should start with combat skill")
    }

    @Test
    fun `rogue starter skills match archetype`() {
        val skills = StarterSkillSets.rogue
        assertTrue(skills.contains("Agility"), "Rogue should start with Agility")
        assertTrue(skills.contains("Stealth"), "Rogue should start with Stealth")
        assertTrue(skills.contains("Lockpicking"), "Rogue should start with Lockpicking")
    }

    @Test
    fun `mage starter skills match archetype`() {
        val skills = StarterSkillSets.mage
        assertTrue(skills.contains("Intelligence"), "Mage should start with Intelligence")
        assertTrue(skills.any {
            val def = SkillDefinitions.getSkill(it)
            def?.tags?.contains("magic") == true
        }, "Mage should start with magic skill")
        assertTrue(skills.contains("Mana Reserve"), "Mage should start with Mana Reserve")
    }

    @Test
    fun `cleric starter skills match archetype`() {
        val skills = StarterSkillSets.cleric
        assertTrue(skills.contains("Wisdom"), "Cleric should start with Wisdom")
        assertTrue(skills.any {
            val def = SkillDefinitions.getSkill(it)
            def?.tags?.contains("magic") == true
        }, "Cleric should start with magic skill")
    }

    @Test
    fun `bard starter skills match archetype`() {
        val skills = StarterSkillSets.bard
        assertTrue(skills.contains("Charisma"), "Bard should start with Charisma")
        assertTrue(skills.contains("Diplomacy"), "Bard should start with Diplomacy")
    }

    @Test
    fun `getStarterSkills handles invalid archetype`() {
        val skills = StarterSkillSets.getStarterSkills("NonExistent")
        assertTrue(skills.isEmpty())
    }

    @Test
    fun `getStarterSkills is case insensitive`() {
        val lowercase = StarterSkillSets.getStarterSkills("warrior")
        val uppercase = StarterSkillSets.getStarterSkills("WARRIOR")
        val mixed = StarterSkillSets.getStarterSkills("WaRrIoR")

        assertEquals(lowercase, uppercase)
        assertEquals(lowercase, mixed)
    }

    @Test
    fun `createStarterSkillStates creates unlocked level 1 skills`() {
        val skillStates = StarterSkillSets.createStarterSkillStates("Warrior")

        assertTrue(skillStates.isNotEmpty())

        skillStates.forEach { (skillName, state) ->
            assertTrue(state.unlocked, "$skillName should be unlocked")
            assertEquals(1, state.level, "$skillName should be level 1")
            assertEquals(0L, state.xp, "$skillName should have 0 XP")

            // Verify skill definition exists
            val definition = SkillDefinitions.getSkill(skillName)
            assertNotNull(definition, "Skill definition should exist for $skillName")
            assertEquals(definition.tags, state.tags)
            assertEquals(definition.resourceType, state.resourceType)
        }
    }

    @Test
    fun `all starter sets have reasonable size`() {
        // Each archetype should have 3-5 starter skills
        assertTrue(StarterSkillSets.warrior.size in 3..5)
        assertTrue(StarterSkillSets.rogue.size in 3..5)
        assertTrue(StarterSkillSets.mage.size in 3..5)
        assertTrue(StarterSkillSets.cleric.size in 3..5)
        assertTrue(StarterSkillSets.bard.size in 3..5)
    }

    @Test
    fun `no duplicate skills in catalog`() {
        val allSkillNames = SkillDefinitions.allSkills.keys.toList()
        val uniqueSkillNames = allSkillNames.distinct()
        assertEquals(allSkillNames.size, uniqueSkillNames.size, "Skill catalog should not have duplicates")
    }

    @Test
    fun `all skills have non-empty tags`() {
        SkillDefinitions.allSkills.values.forEach { skill ->
            assertTrue(skill.tags.isNotEmpty(), "Skill '${skill.name}' should have at least one tag")
        }
    }

    @Test
    fun `all skills have non-blank description`() {
        SkillDefinitions.allSkills.values.forEach { skill ->
            assertTrue(skill.description.isNotBlank(), "Skill '${skill.name}' should have a description")
        }
    }

    @Test
    fun `baseUnlockChance is within reasonable range`() {
        SkillDefinitions.allSkills.values.forEach { skill ->
            assertTrue(
                skill.baseUnlockChance in 1..10,
                "Skill '${skill.name}' unlock chance should be 1-10%, got ${skill.baseUnlockChance}%"
            )
        }
    }
}
