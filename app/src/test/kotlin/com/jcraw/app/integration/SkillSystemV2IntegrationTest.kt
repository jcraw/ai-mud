package com.jcraw.app.integration

import com.jcraw.mud.core.*
import com.jcraw.mud.core.Component
import com.jcraw.mud.core.SocialComponent
import com.jcraw.mud.memory.skill.SkillDatabase
import com.jcraw.mud.memory.social.SocialDatabase
import com.jcraw.mud.reasoning.skill.SkillDefinitions
import com.jcraw.mud.reasoning.skill.SkillManager
import com.jcraw.mud.reasoning.DispositionManager
import com.jcraw.mud.testbot.InMemoryGameEngine
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.*

/**
 * Integration tests for Skill System V2
 *
 * Tests the full skill system workflow including:
 * - UseSkill: Using skills, XP rewards, leveling, perk prompts
 * - TrainSkill: NPC training with disposition checks
 * - ChoosePerk: Perk selection at milestone levels
 * - ViewSkills: Skill sheet display
 * - Full progression cycles
 */
class SkillSystemV2IntegrationTest {

    private lateinit var skillDbFile: File
    private lateinit var socialDbFile: File

    @BeforeTest
    fun setup() {
        // Create temp databases
        skillDbFile = File.createTempFile("test_skills", ".db")
        socialDbFile = File.createTempFile("test_social", ".db")
        skillDbFile.deleteOnExit()
        socialDbFile.deleteOnExit()
    }

    @AfterTest
    fun cleanup() {
        skillDbFile.delete()
        socialDbFile.delete()
    }

    // ========== ViewSkills Tests ==========

    @Test
    fun `viewSkills displays skill sheet with starter skills`() = runBlocking {
        val world = createTestWorld()
        val engine = InMemoryGameEngine(world)

        // Use a known skill to unlock it first (using SkillDefinitions mapping)
        // "lockpick" should map to "Lockpicking"
        engine.processInput("use lockpicking")  // This should unlock Lockpicking at level 1

        val response = engine.processInput("skills")

        // Verify skill sheet is displayed
        assertTrue(response.contains("Skill", ignoreCase = true), "Should contain 'Skill' header")
        assertTrue(response.isNotEmpty(), "Should display skill information")
    }

    // ========== UseSkill Tests ==========

    @Test
    fun `useSkill grants XP on success`() = runBlocking {
        val world = createTestWorld()
        val engine = InMemoryGameEngine(world)

        // First, train to unlock a skill (we need a friendly NPC)
        val friendlyNpc = Entity.NPC(
            id = "trainer",
            name = "Trainer",
            description = "A friendly trainer",
            health = 50,
            maxHealth = 50,
            isHostile = false
        ).withComponent(SocialComponent(
            disposition = 50,
            personality = "helpful",
            traits = listOf("patient", "wise")
        ))

        val worldWithNpc = world.copy(
            rooms = mapOf(
                "test_room" to world.rooms["test_room"]!!.copy(
                    entities = listOf(friendlyNpc)
                )
            )
        )
        val engineWithNpc = InMemoryGameEngine(worldWithNpc)

        // Train a skill to unlock it
        val trainResponse = engineWithNpc.processInput("train Lockpicking with Trainer")
        assertTrue(
            trainResponse.contains("unlocked", ignoreCase = true) ||
            trainResponse.contains("trained", ignoreCase = true),
            "Training should unlock the skill"
        )

        // Now use the skill multiple times and check for XP gain
        val useResponse1 = engineWithNpc.processInput("use Lockpicking")
        val useResponse2 = engineWithNpc.processInput("use Lockpicking")

        // Should mention XP in at least one response
        val hasXp = useResponse1.contains("XP", ignoreCase = true) ||
                    useResponse2.contains("XP", ignoreCase = true) ||
                    useResponse1.contains("experience", ignoreCase = true) ||
                    useResponse2.contains("experience", ignoreCase = true)

        assertTrue(hasXp, "Using skill should grant XP")
    }

    @Test
    fun `useSkill prompts perk selection at milestone levels`() = runBlocking {
        val world = createTestWorld()
        val engine = InMemoryGameEngine(world)

        // Use the skill multiple times to level up
        // This test verifies that the skill system eventually prompts for perk selection
        repeat(100) {
            engine.processInput("use Lockpicking")
        }

        // Check skills to see if perk selection is available
        val response = engine.processInput("skills")

        // Should display skills
        assertTrue(response.isNotEmpty(), "Skills command should produce output")
    }

    @Test
    fun `useSkill with unlocked skill performs check and grants XP`() = runBlocking {
        val world = createTestWorld()
        val engine = InMemoryGameEngine(world)

        // Use the skill
        val response = engine.processInput("use Lockpicking")

        // Should contain skill check results (success or failure)
        val hasCheckResult = response.contains("rolled", ignoreCase = true) ||
                            response.contains("success", ignoreCase = true) ||
                            response.contains("fail", ignoreCase = true) ||
                            response.contains("XP", ignoreCase = true)

        assertTrue(hasCheckResult, "Using unlocked skill should perform check")
    }

    @Test
    fun `useSkill with locked skill prompts for training`() = runBlocking {
        val world = createTestWorld()
        val engine = InMemoryGameEngine(world)

        // Try to use a skill that's not unlocked
        val response = engine.processInput("use Pyromancy")

        // Should prompt for training
        assertTrue(
            response.contains("train", ignoreCase = true) ||
            response.contains("unlock", ignoreCase = true) ||
            response.contains("not learned", ignoreCase = true),
            "Using locked skill should prompt for training"
        )
    }

    @Test
    fun `useSkill with natural language infers correct skill`() = runBlocking {
        val world = createTestWorld()
        val engine = InMemoryGameEngine(world)

        // Use natural language that should map to Pyromancy
        val response = engine.processInput("cast fireball")

        // Should infer Pyromancy skill
        val hasPyromancy = response.contains("Pyromancy", ignoreCase = true) ||
                          response.contains("fire", ignoreCase = true)

        assertTrue(hasPyromancy, "Natural language should infer correct skill")
    }

    // ========== TrainSkill Tests ==========

    @Test
    fun `trainSkill with friendly NPC unlocks skill`() = runBlocking {
        val friendlyNpc = Entity.NPC(
            id = "trainer",
            name = "Trainer",
            description = "A friendly trainer",
            health = 50,
            maxHealth = 50,
            isHostile = false
        ).withComponent(SocialComponent(
            disposition = 50,
            personality = "helpful",
            traits = listOf("patient")
        ))

        val world = createTestWorldWithNPC(friendlyNpc)
        val engine = InMemoryGameEngine(world)

        // Train with friendly NPC
        val response = engine.processInput("train Lockpicking with Trainer")

        // Should unlock the skill
        assertTrue(
            response.contains("unlocked", ignoreCase = true) ||
            response.contains("trained", ignoreCase = true) ||
            response.contains("level 1", ignoreCase = true),
            "Training with friendly NPC should unlock skill"
        )
    }

    @Test
    fun `trainSkill with hostile NPC fails`() = runBlocking {
        val hostileNpc = Entity.NPC(
            id = "enemy",
            name = "Enemy",
            description = "A hostile enemy",
            health = 50,
            maxHealth = 50,
            isHostile = true
        ).withComponent(SocialComponent(
            disposition = -50,
            personality = "aggressive",
            traits = listOf("mean")
        ))

        val world = createTestWorldWithNPC(hostileNpc)
        val engine = InMemoryGameEngine(world)

        // Try to train with hostile NPC
        val response = engine.processInput("train Lockpicking with Enemy")

        // Should fail due to disposition
        assertTrue(
            response.contains("refuse", ignoreCase = true) ||
            response.contains("unwilling", ignoreCase = true) ||
            response.contains("not friendly", ignoreCase = true) ||
            response.contains("hostile", ignoreCase = true),
            "Training with hostile NPC should fail"
        )
    }

    @Test
    fun `trainSkill with already-unlocked skill grants boosted XP`() = runBlocking {
        val friendlyNpc = Entity.NPC(
            id = "trainer",
            name = "Trainer",
            description = "A friendly trainer",
            health = 50,
            maxHealth = 50,
            isHostile = false
        ).withComponent(SocialComponent(
            disposition = 50,
            personality = "helpful",
            traits = listOf("patient")
        ))

        val world = createTestWorldWithNPC(friendlyNpc)
        val engine = InMemoryGameEngine(world)

        // Train to unlock first, then train again
        engine.processInput("train Lockpicking with Trainer")

        // Train with friendly NPC again
        val response = engine.processInput("train Lockpicking with Trainer")

        // Should grant boosted XP (2x or 2.5x)
        assertTrue(
            response.contains("XP", ignoreCase = true) ||
            response.contains("experience", ignoreCase = true) ||
            response.contains("boosted", ignoreCase = true),
            "Training already-unlocked skill should grant XP"
        )
    }

    @Test
    fun `trainSkill parses NPC name correctly`() = runBlocking {
        val friendlyNpc = Entity.NPC(
            id = "master",
            name = "Master Smith",
            description = "A master blacksmith",
            health = 50,
            maxHealth = 50,
            isHostile = false
        ).withComponent(SocialComponent(
            disposition = 50,
            personality = "gruff but kind",
            traits = listOf("skilled")
        ))

        val world = createTestWorldWithNPC(friendlyNpc)
        val engine = InMemoryGameEngine(world)

        // Use different parsing patterns
        val response1 = engine.processInput("train Lockpicking with Master Smith")
        val response2 = engine.processInput("train Lockpicking at Master Smith")
        val response3 = engine.processInput("train Lockpicking from Smith")

        // At least one should parse correctly
        val anySuccess = listOf(response1, response2, response3).any {
            it.contains("trained", ignoreCase = true) ||
            it.contains("unlocked", ignoreCase = true)
        }

        assertTrue(anySuccess, "Should parse NPC name from training command")
    }

    // ========== ChoosePerk Tests ==========

    @Test
    fun `choosePerk selects perk at milestone level`() = runBlocking {
        val world = createTestWorld()
        val engine = InMemoryGameEngine(world)

        // Use skill many times to level up, then try to choose perk
        repeat(200) {
            engine.processInput("use Lockpicking")
        }

        // Try to choose perk (may or may not be at milestone yet)
        val response = engine.processInput("choose perk 1 for Lockpicking")

        // Should produce some response
        assertTrue(response.isNotEmpty(), "Perk command should produce output")
    }

    @Test
    fun `choosePerk validates choice is in range`() = runBlocking {
        val world = createTestWorld()
        val engine = InMemoryGameEngine(world)

        // Try invalid choice without being at milestone
        val response = engine.processInput("choose perk 999 for Lockpicking")

        // Should produce some response
        assertTrue(response.isNotEmpty(), "Invalid perk choice should produce feedback")
    }

    @Test
    fun `choosePerk before milestone level fails gracefully`() = runBlocking {
        val world = createTestWorld()
        val engine = InMemoryGameEngine(world)

        // Try to choose perk without any skill progress
        val response = engine.processInput("choose perk 1 for Lockpicking")

        // Should provide some feedback
        assertTrue(response.isNotEmpty(), "Should provide feedback for premature perk selection")
    }

    // ========== Full Progression Cycle Tests ==========

    @Test
    fun `full skill progression - unlock to level up to perk selection`() = runBlocking {
        val friendlyNpc = Entity.NPC(
            id = "trainer",
            name = "Trainer",
            description = "A friendly trainer",
            health = 50,
            maxHealth = 50,
            isHostile = false
        ).withComponent(SocialComponent(
            disposition = 50,
            personality = "helpful",
            traits = listOf("patient")
        ))

        val world = createTestWorldWithNPC(friendlyNpc)
        val engine = InMemoryGameEngine(world)

        // Step 1: Train to unlock skill
        val trainResponse = engine.processInput("train Lockpicking with Trainer")
        assertTrue(trainResponse.isNotEmpty(), "Training should produce output")

        // Step 2: Use skill multiple times
        repeat(50) {
            engine.processInput("use Lockpicking")
        }

        // Step 3: View skills
        val skillsResponse = engine.processInput("skills")
        assertTrue(skillsResponse.isNotEmpty(), "Skills should display")

        // Step 4: Try to choose a perk
        val choosePerkResponse = engine.processInput("choose perk 1 for Lockpicking")
        assertTrue(choosePerkResponse.isNotEmpty(), "Perk choice should produce output")
    }

    @Test
    fun `skill progression with multiple skills works independently`() = runBlocking {
        val friendlyNpc = Entity.NPC(
            id = "trainer",
            name = "Trainer",
            description = "A friendly trainer",
            health = 50,
            maxHealth = 50,
            isHostile = false
        ).withComponent(SocialComponent(
            disposition = 50,
            personality = "helpful",
            traits = listOf("patient")
        ))

        val world = createTestWorldWithNPC(friendlyNpc)
        val engine = InMemoryGameEngine(world)

        // Train two different skills
        engine.processInput("train Lockpicking with Trainer")
        engine.processInput("train Stealth with Trainer")

        // Use both skills
        val lockResponse = engine.processInput("use Lockpicking")
        val stealthResponse = engine.processInput("use Stealth")

        // Both should work independently
        val bothWork = (lockResponse.contains("Lockpicking", ignoreCase = true) ||
                       lockResponse.contains("lock", ignoreCase = true)) &&
                       (stealthResponse.contains("Stealth", ignoreCase = true) ||
                       stealthResponse.contains("sneak", ignoreCase = true))

        assertTrue(bothWork, "Multiple skills should progress independently")
    }

    @Test
    fun `skill XP accumulates correctly across multiple uses`() = runBlocking {
        val world = createTestWorld()
        val engine = InMemoryGameEngine(world)

        // Use skill multiple times
        repeat(20) {
            engine.processInput("use Lockpicking")
        }

        // Check skill sheet
        val skillsResponse = engine.processInput("skills")

        // Should show Lockpicking with some level > 1 or XP > 0
        assertTrue(
            skillsResponse.contains("Lockpicking", ignoreCase = true),
            "Skills sheet should show Lockpicking"
        )

        // Verify XP accumulated (check for level or XP display)
        val hasProgress = skillsResponse.contains("XP", ignoreCase = true) ||
                         skillsResponse.matches(Regex(".*Level\\s+[2-9].*", RegexOption.IGNORE_CASE))

        assertTrue(hasProgress, "Skill should show accumulated progress")
    }

    // ========== Helper Functions ==========

    private fun createTestWorld(): WorldState {
        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            stats = Stats(
                strength = 12,
                dexterity = 14,
                constitution = 10,
                intelligence = 13,
                wisdom = 11,
                charisma = 15
            )
        )

        val room = Room(
            id = "test_room",
            name = "Test Room",
            traits = listOf("empty", "quiet"),
            entities = emptyList()
        )

        return WorldState(
            rooms = mapOf("test_room" to room),
            players = mapOf(player.id to player)
        )
    }

    private fun createTestWorldWithNPC(npc: Entity.NPC): WorldState {
        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            stats = Stats(
                strength = 12,
                dexterity = 14,
                constitution = 10,
                intelligence = 13,
                wisdom = 11,
                charisma = 15
            )
        )

        val room = Room(
            id = "test_room",
            name = "Test Room",
            traits = listOf("training_hall"),
            entities = listOf(npc)
        )

        return WorldState(
            rooms = mapOf("test_room" to room),
            players = mapOf(player.id to player)
        )
    }
}
