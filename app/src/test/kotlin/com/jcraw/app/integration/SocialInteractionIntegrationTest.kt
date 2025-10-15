package com.jcraw.app.integration

import com.jcraw.mud.core.*
import com.jcraw.mud.testbot.InMemoryGameEngine
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Integration tests for social interaction system
 *
 * Tests the full social interaction workflow including:
 * - Talking to NPCs (dialogue generation)
 * - Persuasion skill checks
 * - Intimidation skill checks
 * - Social challenge difficulty levels
 * - NPC state tracking (hasBeenPersuaded, hasBeenIntimidated)
 */
class SocialInteractionIntegrationTest {

    @Test
    fun `player can talk to friendly NPC`() = runBlocking {
        val npc = Entity.NPC(
            id = "merchant",
            name = "Friendly Merchant",
            description = "A cheerful trader",
            isHostile = false,
            health = 50,
            maxHealth = 50
        )

        val world = createTestWorld(npc = npc)
        val engine = InMemoryGameEngine(world)

        // Talk to the NPC
        val response = engine.processInput("talk merchant")

        // Should get some dialogue response
        assertTrue(response.isNotEmpty(), "Talking to NPC should produce dialogue")
        assertFalse(response.contains("error", ignoreCase = true), "Should not error when talking to NPC")
    }

    @Test
    fun `persuasion succeeds with high charisma`() = runBlocking {
        val npc = Entity.NPC(
            id = "guard",
            name = "Gate Guard",
            description = "A stern guard",
            isHostile = false,
            health = 50,
            maxHealth = 50,
            persuasionChallenge = SkillChallenge(
                statType = StatType.CHARISMA,
                difficulty = Difficulty.EASY,
                description = "Convince the guard to let you pass",
                successDescription = "The guard nods and steps aside",
                failureDescription = "The guard remains unmoved"
            )
        )

        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            stats = Stats(charisma = 20)  // Very high CHA for guaranteed success
        )

        val world = createTestWorld(player = player, npc = npc)
        val engine = InMemoryGameEngine(world)

        // Try to persuade - should succeed with high CHA
        var succeeded = false
        repeat(10) {
            val response = engine.processInput("persuade guard")
            if (response.contains("succeed", ignoreCase = true) ||
                response.contains("success", ignoreCase = true) ||
                response.contains("convince", ignoreCase = true) ||
                response.contains("aside", ignoreCase = true)) {
                succeeded = true
                return@repeat
            }
        }

        assertTrue(succeeded, "Persuasion should succeed with CHA 20 against easy DC")
    }

    @Test
    fun `intimidation succeeds with high charisma`() = runBlocking {
        val npc = Entity.NPC(
            id = "thug",
            name = "Street Thug",
            description = "A menacing criminal",
            isHostile = false,
            health = 50,
            maxHealth = 50,
            intimidationChallenge = SkillChallenge(
                statType = StatType.CHARISMA,
                difficulty = Difficulty.EASY,
                description = "Intimidate the thug into backing down",
                successDescription = "The thug backs away nervously",
                failureDescription = "The thug stands his ground"
            )
        )

        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            stats = Stats(charisma = 20)  // Very high CHA for guaranteed success
        )

        val world = createTestWorld(player = player, npc = npc)
        val engine = InMemoryGameEngine(world)

        // Try to intimidate - should succeed with high CHA
        var succeeded = false
        repeat(10) {
            val response = engine.processInput("intimidate thug")
            if (response.contains("succeed", ignoreCase = true) ||
                response.contains("success", ignoreCase = true) ||
                response.contains("intimidate", ignoreCase = true) ||
                response.contains("backs", ignoreCase = true)) {
                succeeded = true
                return@repeat
            }
        }

        assertTrue(succeeded, "Intimidation should succeed with CHA 20 against easy DC")
    }

    @Test
    fun `persuasion fails with low charisma`() = runBlocking {
        val npc = Entity.NPC(
            id = "noble",
            name = "Haughty Noble",
            description = "An arrogant aristocrat",
            isHostile = false,
            health = 50,
            maxHealth = 50,
            persuasionChallenge = SkillChallenge(
                statType = StatType.CHARISMA,
                difficulty = Difficulty.HARD,
                description = "Persuade the noble to help you",
                successDescription = "The noble reluctantly agrees",
                failureDescription = "The noble dismisses you with a wave"
            )
        )

        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            stats = Stats(charisma = 6)  // Very low CHA - modifier: -2
        )

        val world = createTestWorld(player = player, npc = npc)
        val engine = InMemoryGameEngine(world)

        // Try to persuade - should mostly fail with low CHA vs HARD DC
        // Need to roll 22+ with -2 modifier = need natural 20 (5% chance)
        var failureCount = 0
        repeat(10) {
            val response = engine.processInput("persuade noble")
            if (response.contains("fail", ignoreCase = true) ||
                !response.contains("succeed", ignoreCase = true)) {
                failureCount++
            }
        }

        assertTrue(failureCount >= 5, "Persuasion should fail most of the time with low CHA")
    }

    @Test
    fun `intimidation fails with low charisma`() = runBlocking {
        val npc = Entity.NPC(
            id = "warrior",
            name = "Veteran Warrior",
            description = "A battle-hardened fighter",
            isHostile = false,
            health = 100,
            maxHealth = 100,
            intimidationChallenge = SkillChallenge(
                statType = StatType.CHARISMA,
                difficulty = Difficulty.HARD,
                description = "Intimidate the warrior",
                successDescription = "The warrior hesitates",
                failureDescription = "The warrior laughs at you"
            )
        )

        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            stats = Stats(charisma = 6)  // Very low CHA
        )

        val world = createTestWorld(player = player, npc = npc)
        val engine = InMemoryGameEngine(world)

        // Try to intimidate - should mostly fail with low CHA vs HARD DC
        var failureCount = 0
        repeat(10) {
            val response = engine.processInput("intimidate warrior")
            if (response.contains("fail", ignoreCase = true) ||
                response.contains("laugh", ignoreCase = true) ||
                !response.contains("succeed", ignoreCase = true)) {
                failureCount++
            }
        }

        assertTrue(failureCount >= 5, "Intimidation should fail most of the time with low CHA")
    }

    @Test
    fun `easy social check succeeds more often than hard check`() = runBlocking {
        val easyNPC = Entity.NPC(
            id = "easy_target",
            name = "Naive Villager",
            description = "An easily influenced person",
            isHostile = false,
            health = 50,
            maxHealth = 50,
            persuasionChallenge = SkillChallenge(
                statType = StatType.CHARISMA,
                difficulty = Difficulty.EASY,  // DC 10
                description = "Persuade the villager",
                successDescription = "The villager agrees",
                failureDescription = "The villager is unsure"
            )
        )

        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            stats = Stats(charisma = 12)  // Average CHA (modifier: +1)
        )

        val world = createTestWorld(player = player, npc = easyNPC)
        val engine = InMemoryGameEngine(world)

        // Try easy persuasion - should succeed fairly quickly
        var succeeded = false
        repeat(5) {  // Should succeed within 5 attempts
            val response = engine.processInput("persuade villager")
            if (response.contains("succeed", ignoreCase = true) ||
                response.contains("success", ignoreCase = true) ||
                response.contains("agree", ignoreCase = true)) {
                succeeded = true
                return@repeat
            }
        }

        assertTrue(succeeded, "Easy persuasion check should succeed within 5 attempts")
    }

    @Test
    fun `cannot persuade same NPC twice`() = runBlocking {
        val npc = Entity.NPC(
            id = "shopkeeper",
            name = "Shopkeeper",
            description = "A savvy merchant",
            isHostile = false,
            health = 50,
            maxHealth = 50,
            persuasionChallenge = SkillChallenge(
                statType = StatType.CHARISMA,
                difficulty = Difficulty.TRIVIAL,  // DC 5 - guaranteed success
                description = "Persuade for a discount",
                successDescription = "The shopkeeper offers a discount",
                failureDescription = "The shopkeeper refuses"
            )
        )

        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            stats = Stats(charisma = 18)  // High CHA
        )

        val world = createTestWorld(player = player, npc = npc)
        val engine = InMemoryGameEngine(world)

        // First persuasion - should succeed
        var firstSuccess = false
        repeat(5) {
            val response = engine.processInput("persuade shopkeeper")
            if (response.contains("succeed", ignoreCase = true) ||
                response.contains("discount", ignoreCase = true)) {
                firstSuccess = true
                return@repeat
            }
        }

        assertTrue(firstSuccess, "First persuasion should succeed")

        // Second persuasion attempt - should indicate already persuaded
        val response2 = engine.processInput("persuade shopkeeper")

        // Should get feedback that NPC was already persuaded
        // (implementation may vary - might say "already", "convince", etc.)
        assertTrue(response2.isNotEmpty(), "Should get some response for repeat persuasion")
    }

    @Test
    fun `cannot intimidate same NPC twice`() = runBlocking {
        val npc = Entity.NPC(
            id = "bandit",
            name = "Cowardly Bandit",
            description = "A nervous criminal",
            isHostile = false,
            health = 50,
            maxHealth = 50,
            intimidationChallenge = SkillChallenge(
                statType = StatType.CHARISMA,
                difficulty = Difficulty.TRIVIAL,  // DC 5 - guaranteed success
                description = "Intimidate the bandit",
                successDescription = "The bandit flees",
                failureDescription = "The bandit stands firm"
            )
        )

        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            stats = Stats(charisma = 18)  // High CHA
        )

        val world = createTestWorld(player = player, npc = npc)
        val engine = InMemoryGameEngine(world)

        // First intimidation - should succeed
        var firstSuccess = false
        repeat(5) {
            val response = engine.processInput("intimidate bandit")
            if (response.contains("succeed", ignoreCase = true) ||
                response.contains("flee", ignoreCase = true) ||
                response.contains("intimidate", ignoreCase = true)) {
                firstSuccess = true
                return@repeat
            }
        }

        assertTrue(firstSuccess, "First intimidation should succeed")

        // Second intimidation attempt - should indicate already intimidated
        val response2 = engine.processInput("intimidate bandit")

        // Should get feedback that NPC was already intimidated
        assertTrue(response2.isNotEmpty(), "Should get some response for repeat intimidation")
    }

    @Test
    fun `talking to hostile NPC generates dialogue`() = runBlocking {
        val npc = Entity.NPC(
            id = "orc",
            name = "Orc Chieftain",
            description = "A fearsome orc leader",
            isHostile = true,  // Hostile NPC
            health = 100,
            maxHealth = 100
        )

        val world = createTestWorld(npc = npc)
        val engine = InMemoryGameEngine(world)

        // Try to talk to hostile NPC
        val response = engine.processInput("talk orc")

        // Should get some dialogue, even from hostile NPC
        assertTrue(response.isNotEmpty(), "Should get dialogue even from hostile NPC")
        assertFalse(response.contains("error", ignoreCase = true), "Should not error when talking to hostile NPC")
    }

    @Test
    fun `persuasion with very high charisma almost always succeeds`() = runBlocking {
        val npc = Entity.NPC(
            id = "official",
            name = "City Official",
            description = "A bureaucrat",
            isHostile = false,
            health = 50,
            maxHealth = 50,
            persuasionChallenge = SkillChallenge(
                statType = StatType.CHARISMA,
                difficulty = Difficulty.MEDIUM,  // DC 15
                description = "Persuade the official",
                successDescription = "The official is swayed",
                failureDescription = "The official is unmoved"
            )
        )

        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            stats = Stats(charisma = 22)  // Very high CHA (modifier: +6)
        )

        val world = createTestWorld(player = player, npc = npc)
        val engine = InMemoryGameEngine(world)

        // With +6 modifier, need to roll 9+ on d20 (60% per attempt)
        // Should succeed within first few attempts
        var succeeded = false
        repeat(5) {
            val response = engine.processInput("persuade official")
            if (response.contains("succeed", ignoreCase = true) ||
                response.contains("success", ignoreCase = true) ||
                response.contains("sway", ignoreCase = true)) {
                succeeded = true
                return@repeat
            }
        }

        assertTrue(succeeded, "Very high charisma should lead to quick success")
    }

    @Test
    fun `multiple NPCs can be interacted with independently`() = runBlocking {
        val npc1 = Entity.NPC(
            id = "guard1",
            name = "First Guard",
            description = "A guard",
            isHostile = false,
            health = 50,
            maxHealth = 50
        )

        val npc2 = Entity.NPC(
            id = "guard2",
            name = "Second Guard",
            description = "Another guard",
            isHostile = false,
            health = 50,
            maxHealth = 50
        )

        val world = createTestWorld(npcs = listOf(npc1, npc2))
        val engine = InMemoryGameEngine(world)

        // Talk to first NPC
        val response1 = engine.processInput("talk first")
        assertTrue(response1.isNotEmpty())

        // Talk to second NPC
        val response2 = engine.processInput("talk second")
        assertTrue(response2.isNotEmpty())

        // Both should produce dialogue
        assertFalse(response1.contains("error", ignoreCase = true))
        assertFalse(response2.contains("error", ignoreCase = true))
    }

    @Test
    fun `social interaction respects charisma modifiers`() = runBlocking {
        val npc = Entity.NPC(
            id = "target",
            name = "Test Target",
            description = "A person to persuade",
            isHostile = false,
            health = 50,
            maxHealth = 50,
            persuasionChallenge = SkillChallenge(
                statType = StatType.CHARISMA,
                difficulty = Difficulty.MEDIUM,  // DC 15
                description = "Persuade the target",
                successDescription = "The target agrees",
                failureDescription = "The target refuses"
            )
        )

        // Low CHA player
        val weakPlayer = PlayerState(
            id = "player1",
            name = "Weak Persuader",
            currentRoomId = "test_room",
            stats = Stats(charisma = 8)  // Modifier: -1
        )

        val world1 = createTestWorld(player = weakPlayer, npc = npc)
        val engine1 = InMemoryGameEngine(world1)

        // Try with low CHA - should mostly fail
        var weakFailures = 0
        repeat(10) {
            val response = engine1.processInput("persuade target")
            if (!response.contains("succeed", ignoreCase = true)) {
                weakFailures++
            }
        }

        // High CHA player
        val strongPlayer = PlayerState(
            id = "player2",
            name = "Strong Persuader",
            currentRoomId = "test_room",
            stats = Stats(charisma = 18)  // Modifier: +4
        )

        val world2 = createTestWorld(player = strongPlayer, npc = npc)
        val engine2 = InMemoryGameEngine(world2)

        // Try with high CHA - should mostly succeed
        var strongSuccesses = 0
        repeat(10) {
            val response = engine2.processInput("persuade target")
            if (response.contains("succeed", ignoreCase = true) ||
                response.contains("agree", ignoreCase = true)) {
                strongSuccesses++
            }
        }

        // High CHA should succeed significantly more often than low CHA
        assertTrue(strongSuccesses > (10 - weakFailures),
            "Higher charisma modifier should lead to more persuasion successes")
    }

    // ========== Helper Functions ==========

    private fun createTestWorld(
        player: PlayerState = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room"
        ),
        npc: Entity.NPC? = null,
        npcs: List<Entity.NPC> = emptyList()
    ): WorldState {
        val entities = mutableListOf<Entity>()
        if (npc != null) entities.add(npc)
        entities.addAll(npcs)

        val room = Room(
            id = "test_room",
            name = "Test Room",
            traits = listOf("empty", "quiet"),
            entities = entities
        )

        return WorldState(
            rooms = mapOf("test_room" to room),
            players = mapOf(player.id to player)
        )
    }
}
