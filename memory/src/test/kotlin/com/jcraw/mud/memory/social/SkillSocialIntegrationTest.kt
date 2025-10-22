package com.jcraw.mud.memory.social

import com.jcraw.mud.core.*
import com.jcraw.mud.core.repository.SkillComponentRepository
import com.jcraw.mud.core.repository.SkillRepository
import com.jcraw.mud.memory.skill.SkillDatabase
import com.jcraw.mud.reasoning.DispositionManager
import com.jcraw.mud.reasoning.skill.SkillManager
import com.jcraw.mud.reasoning.skill.UnlockMethod
import kotlin.random.Random
import kotlin.test.*

/**
 * Integration tests for skill system integration with social system
 *
 * Tests Phase 8 of skill system implementation:
 * - Persuasion using Diplomacy skill
 * - Intimidation using Charisma skill
 * - XP granted on social checks
 * - Training with friendly NPCs
 * - Disposition-based training buffs
 */
class SkillSocialIntegrationTest {

    private lateinit var socialDb: SocialDatabase
    private lateinit var skillDb: SkillDatabase
    private lateinit var dispositionManager: DispositionManager
    private lateinit var skillManager: SkillManager

    @BeforeTest
    fun setup() {
        // Initialize in-memory databases
        socialDb = SocialDatabase(":memory:")
        skillDb = SkillDatabase(":memory:")

        // Create repositories
        val socialRepo = socialDb.getSocialComponentRepository()
        val eventRepo = socialDb.getSocialEventRepository()
        val skillRepo = skillDb.getSkillRepository()
        val componentRepo = skillDb.getSkillComponentRepository()

        // Create managers with fixed random for deterministic tests
        skillManager = SkillManager(skillRepo, componentRepo, Random(42))
        dispositionManager = DispositionManager(socialRepo, eventRepo, skillManager)
    }

    @AfterTest
    fun teardown() {
        socialDb.close()
        skillDb.close()
    }

    @Test
    fun `persuasion uses Diplomacy skill`() {
        val playerId = "player1"
        val npc = Entity.NPC(
            id = "npc1",
            name = "Friendly Guard",
            description = "A friendly guard",
            components = listOf(
                SocialComponent(
                    personality = "friendly",
                    traits = listOf("helpful"),
                    disposition = 50 // FRIENDLY tier
                )
            )
        )

        // Unlock Diplomacy skill first
        skillManager.unlockSkill(playerId, "Diplomacy", UnlockMethod.Training).getOrThrow()

        // Attempt persuasion
        val (success, result, updatedNpc) = dispositionManager.attemptPersuasion(playerId, npc, difficulty = 10)

        assertNotNull(result, "Skill check result should not be null")
        assertEquals("Diplomacy", result.narrative.substringAfter("with ").substringBefore("!").substringBefore("."))

        // Check that disposition changed
        val newDisposition = dispositionManager.getDisposition(updatedNpc)
        if (success) {
            assertTrue(newDisposition > 50, "Successful persuasion should increase disposition")
        } else {
            assertTrue(newDisposition < 50, "Failed persuasion should decrease disposition")
        }
    }

    @Test
    fun `intimidation uses Charisma skill`() {
        val playerId = "player2"
        val npc = Entity.NPC(
            id = "npc2",
            name = "Bandit",
            description = "A hostile bandit",
            components = listOf(
                SocialComponent(
                    personality = "aggressive",
                    traits = listOf("cowardly"),
                    disposition = -20 // UNFRIENDLY tier
                )
            )
        )

        // Unlock Charisma skill first
        skillManager.unlockSkill(playerId, "Charisma", UnlockMethod.Training).getOrThrow()

        // Attempt intimidation
        val (success, result, updatedNpc) = dispositionManager.attemptIntimidation(playerId, npc, difficulty = 10)

        assertNotNull(result, "Skill check result should not be null")
        // Charisma should be mentioned in the narrative
        assertTrue(result.narrative.contains("Charisma", ignoreCase = true))

        // Check that disposition changed
        val newDisposition = dispositionManager.getDisposition(updatedNpc)
        if (success) {
            assertTrue(newDisposition > -20, "Successful intimidation should increase disposition")
        } else {
            assertTrue(newDisposition < -20, "Failed intimidation should decrease disposition")
        }
    }

    @Test
    fun `persuasion grants Diplomacy XP on success`() {
        val playerId = "player3"
        val npc = Entity.NPC(
            id = "npc3",
            name = "Merchant",
            description = "A wealthy merchant",
            components = listOf(
                SocialComponent(
                    personality = "greedy",
                    traits = listOf("wealthy"),
                    disposition = 30 // NEUTRAL tier
                )
            )
        )

        // Unlock Diplomacy and set to level 10 to make success likely
        skillManager.unlockSkill(playerId, "Diplomacy", UnlockMethod.Training).getOrThrow()
        repeat(10) {
            skillManager.grantXp(playerId, "Diplomacy", 1000, success = true).getOrThrow()
        }

        val skillBefore = skillManager.getSkillComponent(playerId).getSkill("Diplomacy")!!
        val xpBefore = skillBefore.xp

        // Attempt persuasion
        dispositionManager.attemptPersuasion(playerId, npc, difficulty = 10)

        // Check XP increased
        val skillAfter = skillManager.getSkillComponent(playerId).getSkill("Diplomacy")!!
        assertTrue(skillAfter.xp > xpBefore, "Diplomacy XP should increase after persuasion attempt")
    }

    @Test
    fun `intimidation grants Charisma XP on failure`() {
        val playerId = "player4"
        val npc = Entity.NPC(
            id = "npc4",
            name = "Warrior",
            description = "A tough warrior",
            components = listOf(
                SocialComponent(
                    personality = "brave",
                    traits = listOf("fearless"),
                    disposition = 0 // NEUTRAL tier
                )
            )
        )

        // Unlock Charisma at level 1 to make failure likely
        skillManager.unlockSkill(playerId, "Charisma", UnlockMethod.Training).getOrThrow()

        val skillBefore = skillManager.getSkillComponent(playerId).getSkill("Charisma")!!
        val xpBefore = skillBefore.xp

        // Attempt intimidation with high difficulty
        dispositionManager.attemptIntimidation(playerId, npc, difficulty = 30)

        // Check XP increased (even on failure, you get 20% XP)
        val skillAfter = skillManager.getSkillComponent(playerId).getSkill("Charisma")!!
        assertTrue(skillAfter.xp > xpBefore, "Charisma XP should increase even on failed intimidation")
    }

    @Test
    fun `friendly NPCs allow training`() {
        val npc = Entity.NPC(
            id = "npc5",
            name = "Friendly Mentor",
            description = "A wise mentor",
            components = listOf(
                SocialComponent(
                    personality = "wise",
                    traits = listOf("patient", "knowledgeable"),
                    disposition = 60 // FRIENDLY tier
                )
            )
        )

        assertTrue(dispositionManager.canTrainPlayer(npc), "Friendly NPC should allow training")
    }

    @Test
    fun `hostile NPCs refuse training`() {
        val npc = Entity.NPC(
            id = "npc6",
            name = "Hostile Enemy",
            description = "An angry enemy",
            components = listOf(
                SocialComponent(
                    personality = "hostile",
                    traits = listOf("angry"),
                    disposition = -80 // HOSTILE tier
                )
            )
        )

        assertFalse(dispositionManager.canTrainPlayer(npc), "Hostile NPC should refuse training")
    }

    @Test
    fun `training with friendly NPC unlocks skill at level 1`() {
        val playerId = "player6"
        val npc = Entity.NPC(
            id = "npc7",
            name = "Sword Master",
            description = "A master swordsman",
            components = listOf(
                SocialComponent(
                    personality = "disciplined",
                    traits = listOf("skilled"),
                    disposition = 70 // FRIENDLY tier
                )
            )
        )

        // Train a new skill
        val result = dispositionManager.trainSkillWithNPC(playerId, npc, "Sword Fighting")

        assertTrue(result.isSuccess, "Training should succeed with friendly NPC")
        val message = result.getOrThrow()
        assertTrue(message.contains("unlocked", ignoreCase = true), "Message should mention unlock")

        // Verify skill is unlocked
        val skill = skillManager.getSkillComponent(playerId).getSkill("Sword Fighting")
        assertNotNull(skill, "Sword Fighting should be unlocked")
        assertTrue(skill.unlocked, "Skill should be marked as unlocked")
        assertEquals(1, skill.level, "Training unlock should grant level 1")
    }

    @Test
    fun `training with allied NPC grants 2_5x XP multiplier`() {
        val playerId = "player7"
        val npc = Entity.NPC(
            id = "npc8",
            name = "Close Friend",
            description = "Your close friend",
            components = listOf(
                SocialComponent(
                    personality = "loyal",
                    traits = listOf("devoted"),
                    disposition = 90 // ALLIED tier
                )
            )
        )

        // Unlock skill first
        skillManager.unlockSkill(playerId, "Stealth", UnlockMethod.Attempt).getOrThrow()
        val skillBefore = skillManager.getSkillComponent(playerId).getSkill("Stealth")!!
        val xpBefore = skillBefore.xp

        // Train with allied NPC
        val result = dispositionManager.trainSkillWithNPC(playerId, npc, "Stealth")

        assertTrue(result.isSuccess, "Training should succeed")
        val message = result.getOrThrow()
        assertTrue(message.contains("2.5x multiplier"), "Message should mention 2.5x multiplier for ALLIED")

        // Verify XP increased by 2.5x (base 100 XP * 2.5 = 250)
        val skillAfter = skillManager.getSkillComponent(playerId).getSkill("Stealth")!!
        assertEquals(xpBefore + 250, skillAfter.xp, "XP should increase by 250 (100 base * 2.5)")
    }

    @Test
    fun `training with friendly NPC grants 2_0x XP multiplier`() {
        val playerId = "player8"
        val npc = Entity.NPC(
            id = "npc9",
            name = "Friendly Teacher",
            description = "A friendly teacher",
            components = listOf(
                SocialComponent(
                    personality = "helpful",
                    traits = listOf("patient"),
                    disposition = 55 // FRIENDLY tier
                )
            )
        )

        // Unlock skill first
        skillManager.unlockSkill(playerId, "Lockpicking", UnlockMethod.Attempt).getOrThrow()
        val skillBefore = skillManager.getSkillComponent(playerId).getSkill("Lockpicking")!!
        val xpBefore = skillBefore.xp

        // Train with friendly NPC
        val result = dispositionManager.trainSkillWithNPC(playerId, npc, "Lockpicking")

        assertTrue(result.isSuccess, "Training should succeed")
        val message = result.getOrThrow()
        assertTrue(message.contains("2.0x multiplier"), "Message should mention 2.0x multiplier for FRIENDLY")

        // Verify XP increased by 2.0x (base 100 XP * 2.0 = 200)
        val skillAfter = skillManager.getSkillComponent(playerId).getSkill("Lockpicking")!!
        assertEquals(xpBefore + 200, skillAfter.xp, "XP should increase by 200 (100 base * 2.0)")
    }

    @Test
    fun `training with neutral NPC fails`() {
        val playerId = "player9"
        val npc = Entity.NPC(
            id = "npc10",
            name = "Neutral Stranger",
            description = "A neutral stranger",
            components = listOf(
                SocialComponent(
                    personality = "indifferent",
                    traits = listOf("aloof"),
                    disposition = 10 // NEUTRAL tier
                )
            )
        )

        // Attempt training
        val result = dispositionManager.trainSkillWithNPC(playerId, npc, "Fire Magic")

        assertTrue(result.isFailure, "Training should fail with neutral NPC")
        val error = result.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error.message!!.contains("not friendly enough"), "Error should mention disposition")
    }

    @Test
    fun `persuasion failure reduces disposition`() {
        val playerId = "player10"
        val npc = Entity.NPC(
            id = "npc11",
            name = "Skeptical Guard",
            description = "A skeptical guard",
            components = listOf(
                SocialComponent(
                    personality = "suspicious",
                    traits = listOf("distrustful"),
                    disposition = 20 // NEUTRAL tier
                )
            )
        )

        // Unlock Diplomacy at low level to make failure more likely
        skillManager.unlockSkill(playerId, "Diplomacy", UnlockMethod.Attempt).getOrThrow()

        val dispositionBefore = dispositionManager.getDisposition(npc)

        // Attempt persuasion with very high difficulty
        val (success, _, updatedNpc) = dispositionManager.attemptPersuasion(playerId, npc, difficulty = 50)

        if (!success) {
            val dispositionAfter = dispositionManager.getDisposition(updatedNpc)
            assertTrue(dispositionAfter < dispositionBefore, "Failed persuasion should reduce disposition")
        }
    }

    @Test
    fun `intimidation failure reduces disposition more than persuasion`() {
        val playerId = "player11"
        val npc = Entity.NPC(
            id = "npc12",
            name = "Brave Knight",
            description = "A brave knight",
            components = listOf(
                SocialComponent(
                    personality = "brave",
                    traits = listOf("courageous"),
                    disposition = 0 // NEUTRAL tier
                )
            )
        )

        // Unlock Charisma at low level
        skillManager.unlockSkill(playerId, "Charisma", UnlockMethod.Attempt).getOrThrow()

        val dispositionBefore = dispositionManager.getDisposition(npc)

        // Attempt intimidation with very high difficulty
        val (success, _, updatedNpc) = dispositionManager.attemptIntimidation(playerId, npc, difficulty = 50)

        if (!success) {
            val dispositionAfter = dispositionManager.getDisposition(updatedNpc)
            // Intimidation failure is -10, persuasion failure is -5
            val change = dispositionAfter - dispositionBefore
            assertEquals(-10, change, "Failed intimidation should reduce disposition by 10")
        }
    }

    @Test
    fun `training unknown skill fails`() {
        val playerId = "player12"
        val npc = Entity.NPC(
            id = "npc13",
            name = "Mentor",
            description = "A mentor",
            components = listOf(
                SocialComponent(
                    personality = "wise",
                    traits = listOf("knowledgeable"),
                    disposition = 70 // FRIENDLY tier
                )
            )
        )

        // Attempt to train non-existent skill
        val result = dispositionManager.trainSkillWithNPC(playerId, npc, "Flying")

        assertTrue(result.isFailure, "Training unknown skill should fail")
        val error = result.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error is IllegalArgumentException, "Should throw IllegalArgumentException for unknown skill")
        assertTrue(error.message!!.contains("Unknown skill"), "Error message should mention unknown skill")
    }

    @Test
    fun `persuasion without SkillManager returns failure`() {
        val playerId = "player13"
        val npc = Entity.NPC(
            id = "npc14",
            name = "Guard",
            description = "A guard",
            components = listOf(SocialComponent(personality = "neutral", traits = emptyList()))
        )

        // Create DispositionManager without SkillManager
        val managerWithoutSkills = DispositionManager(
            socialDb.getSocialComponentRepository(),
            socialDb.getSocialEventRepository(),
            skillManager = null
        )

        val (success, result, _) = managerWithoutSkills.attemptPersuasion(playerId, npc)

        assertFalse(success, "Persuasion without SkillManager should fail")
        assertNull(result, "Result should be null without SkillManager")
    }

    @Test
    fun `disposition tier determines training multiplier`() {
        // Test all disposition tiers
        val allied = Entity.NPC(
            id = "allied",
            name = "Ally",
            description = "Ally",
            components = listOf(SocialComponent(personality = "friendly", traits = emptyList(), disposition = 80))
        )
        assertEquals(2.5, dispositionManager.getTrainingMultiplier(allied), "ALLIED should give 2.5x")

        val friendly = Entity.NPC(
            id = "friendly",
            name = "Friend",
            description = "Friend",
            components = listOf(SocialComponent(personality = "friendly", traits = emptyList(), disposition = 55))
        )
        assertEquals(2.0, dispositionManager.getTrainingMultiplier(friendly), "FRIENDLY should give 2.0x")

        val neutral = Entity.NPC(
            id = "neutral",
            name = "Neutral",
            description = "Neutral",
            components = listOf(SocialComponent(personality = "neutral", traits = emptyList(), disposition = 0))
        )
        assertEquals(0.0, dispositionManager.getTrainingMultiplier(neutral), "NEUTRAL should give 0x")

        val unfriendly = Entity.NPC(
            id = "unfriendly",
            name = "Unfriendly",
            description = "Unfriendly",
            components = listOf(SocialComponent(personality = "hostile", traits = emptyList(), disposition = -40))
        )
        assertEquals(0.0, dispositionManager.getTrainingMultiplier(unfriendly), "UNFRIENDLY should give 0x")

        val hostile = Entity.NPC(
            id = "hostile",
            name = "Hostile",
            description = "Hostile",
            components = listOf(SocialComponent(personality = "hostile", traits = emptyList(), disposition = -80))
        )
        assertEquals(0.0, dispositionManager.getTrainingMultiplier(hostile), "HOSTILE should give 0x")
    }
}
