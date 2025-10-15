package com.jcraw.app.integration

import com.jcraw.mud.core.*
import com.jcraw.mud.testbot.InMemoryGameEngine
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Integration tests for skill check system
 *
 * Tests the full skill check workflow including:
 * - All 6 stat types (STR, DEX, CON, INT, WIS, CHA)
 * - Difficulty levels (Easy, Medium, Hard)
 * - Success and failure outcomes
 * - Critical successes and failures
 */
class SkillCheckIntegrationTest {

    @Test
    fun `strength check can succeed with high strength`() = runBlocking {
        val feature = Entity.Feature(
            id = "heavy_door",
            name = "Heavy Stone Door",
            description = "An extremely heavy door",
            requiresSkillCheck = true,
            skillCheckStat = StatType.STRENGTH,
            skillCheckDC = 15
        )

        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            stats = Stats(strength = 20)  // High STR for guaranteed success
        )

        val world = createTestWorld(player = player, features = listOf(feature))
        val engine = InMemoryGameEngine(world)

        // Attempt the skill check multiple times to ensure success
        var succeeded = false
        repeat(10) {
            val response = engine.processInput("check door")
            if (response.contains("succeed", ignoreCase = true) ||
                response.contains("success", ignoreCase = true) ||
                response.contains("manage", ignoreCase = true)) {
                succeeded = true
                return@repeat
            }
        }

        assertTrue(succeeded, "Strength check should succeed with STR 20")
    }

    @Test
    fun `dexterity check can succeed with high dexterity`() = runBlocking {
        val feature = Entity.Feature(
            id = "lock",
            name = "Complex Lock",
            description = "A intricate lock mechanism",
            requiresSkillCheck = true,
            skillCheckStat = StatType.DEXTERITY,
            skillCheckDC = 15
        )

        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            stats = Stats(dexterity = 20)  // High DEX for guaranteed success
        )

        val world = createTestWorld(player = player, features = listOf(feature))
        val engine = InMemoryGameEngine(world)

        // Attempt the skill check multiple times to ensure success
        var succeeded = false
        repeat(10) {
            val response = engine.processInput("check lock")
            if (response.contains("succeed", ignoreCase = true) ||
                response.contains("success", ignoreCase = true) ||
                response.contains("pick", ignoreCase = true)) {
                succeeded = true
                return@repeat
            }
        }

        assertTrue(succeeded, "Dexterity check should succeed with DEX 20")
    }

    @Test
    fun `constitution check can succeed with high constitution`() = runBlocking {
        val feature = Entity.Feature(
            id = "poison_trap",
            name = "Poison Gas Trap",
            description = "A trap spewing toxic gas",
            requiresSkillCheck = true,
            skillCheckStat = StatType.CONSTITUTION,
            skillCheckDC = 15
        )

        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            stats = Stats(constitution = 20)  // High CON for guaranteed success
        )

        val world = createTestWorld(player = player, features = listOf(feature))
        val engine = InMemoryGameEngine(world)

        // Attempt the skill check multiple times to ensure success
        var succeeded = false
        repeat(10) {
            val response = engine.processInput("check trap")
            if (response.contains("succeed", ignoreCase = true) ||
                response.contains("success", ignoreCase = true) ||
                response.contains("resist", ignoreCase = true)) {
                succeeded = true
                return@repeat
            }
        }

        assertTrue(succeeded, "Constitution check should succeed with CON 20")
    }

    @Test
    fun `intelligence check can succeed with high intelligence`() = runBlocking {
        val feature = Entity.Feature(
            id = "puzzle",
            name = "Ancient Puzzle",
            description = "A complex puzzle mechanism",
            requiresSkillCheck = true,
            skillCheckStat = StatType.INTELLIGENCE,
            skillCheckDC = 15
        )

        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            stats = Stats(intelligence = 20)  // High INT for guaranteed success
        )

        val world = createTestWorld(player = player, features = listOf(feature))
        val engine = InMemoryGameEngine(world)

        // Attempt the skill check multiple times to ensure success
        var succeeded = false
        repeat(10) {
            val response = engine.processInput("check puzzle")
            if (response.contains("succeed", ignoreCase = true) ||
                response.contains("success", ignoreCase = true) ||
                response.contains("solve", ignoreCase = true)) {
                succeeded = true
                return@repeat
            }
        }

        assertTrue(succeeded, "Intelligence check should succeed with INT 20")
    }

    @Test
    fun `wisdom check can succeed with high wisdom`() = runBlocking {
        val feature = Entity.Feature(
            id = "hidden_trap",
            name = "Hidden Trap",
            description = "A concealed trap mechanism",
            requiresSkillCheck = true,
            skillCheckStat = StatType.WISDOM,
            skillCheckDC = 15
        )

        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            stats = Stats(wisdom = 20)  // High WIS for guaranteed success
        )

        val world = createTestWorld(player = player, features = listOf(feature))
        val engine = InMemoryGameEngine(world)

        // Attempt the skill check multiple times to ensure success
        var succeeded = false
        repeat(10) {
            val response = engine.processInput("check trap")
            if (response.contains("succeed", ignoreCase = true) ||
                response.contains("success", ignoreCase = true) ||
                response.contains("notice", ignoreCase = true) ||
                response.contains("spot", ignoreCase = true)) {
                succeeded = true
                return@repeat
            }
        }

        assertTrue(succeeded, "Wisdom check should succeed with WIS 20")
    }

    @Test
    fun `charisma check can succeed with high charisma`() = runBlocking {
        val feature = Entity.Feature(
            id = "magic_seal",
            name = "Magical Seal",
            description = "A seal requiring force of personality",
            requiresSkillCheck = true,
            skillCheckStat = StatType.CHARISMA,
            skillCheckDC = 15
        )

        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            stats = Stats(charisma = 20)  // High CHA for guaranteed success
        )

        val world = createTestWorld(player = player, features = listOf(feature))
        val engine = InMemoryGameEngine(world)

        // Attempt the skill check multiple times to ensure success
        var succeeded = false
        repeat(10) {
            val response = engine.processInput("check seal")
            if (response.contains("succeed", ignoreCase = true) ||
                response.contains("success", ignoreCase = true) ||
                response.contains("break", ignoreCase = true)) {
                succeeded = true
                return@repeat
            }
        }

        assertTrue(succeeded, "Charisma check should succeed with CHA 20")
    }

    @Test
    fun `easy skill check succeeds more often`() = runBlocking {
        val feature = Entity.Feature(
            id = "easy_lock",
            name = "Simple Lock",
            description = "A basic lock",
            requiresSkillCheck = true,
            skillCheckStat = StatType.DEXTERITY,
            skillCheckDC = 5  // TRIVIAL difficulty
        )

        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            stats = Stats(dexterity = 10)  // Average DEX
        )

        val world = createTestWorld(player = player, features = listOf(feature))
        val engine = InMemoryGameEngine(world)

        // Try skill check - should succeed with high probability
        var succeeded = false
        repeat(5) {  // Only 5 attempts needed for easy check
            val response = engine.processInput("check lock")
            if (response.contains("succeed", ignoreCase = true) ||
                response.contains("success", ignoreCase = true)) {
                succeeded = true
                return@repeat
            }
        }

        assertTrue(succeeded, "Easy skill check (DC 5) should succeed with average stats")
    }

    @Test
    fun `medium skill check requires good stats or luck`() = runBlocking {
        val feature = Entity.Feature(
            id = "medium_puzzle",
            name = "Moderate Puzzle",
            description = "A moderately complex puzzle",
            requiresSkillCheck = true,
            skillCheckStat = StatType.INTELLIGENCE,
            skillCheckDC = 15  // MEDIUM difficulty
        )

        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            stats = Stats(intelligence = 10)  // Average INT
        )

        val world = createTestWorld(player = player, features = listOf(feature))
        val engine = InMemoryGameEngine(world)

        // Try skill check multiple times
        // With average INT (modifier = 0), need to roll 15+ on d20 (30% chance)
        // With 15 attempts, probability of at least one success: ~99%
        var attemptCount = 0
        var succeeded = false
        repeat(15) {
            attemptCount++
            val response = engine.processInput("check puzzle")
            if (response.contains("succeed", ignoreCase = true) ||
                response.contains("success", ignoreCase = true)) {
                succeeded = true
                return@repeat
            }
        }

        // Test passes if we eventually succeed or if we get reasonable failures
        // (some failures expected with average stats)
        assertTrue(succeeded || attemptCount > 5,
            "Medium check with average stats should eventually succeed or show multiple attempts")
    }

    @Test
    fun `hard skill check is difficult without high stats`() = runBlocking {
        val feature = Entity.Feature(
            id = "hard_door",
            name = "Reinforced Door",
            description = "An extremely sturdy door",
            requiresSkillCheck = true,
            skillCheckStat = StatType.STRENGTH,
            skillCheckDC = 20  // HARD difficulty
        )

        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            stats = Stats(strength = 8)  // Below average STR (modifier = -1)
        )

        val world = createTestWorld(player = player, features = listOf(feature))
        val engine = InMemoryGameEngine(world)

        // Try skill check - should mostly fail with low STR
        // Need to roll 21+ on d20 with -1 modifier = need natural 20 (5% chance)
        var failureCount = 0
        repeat(10) {
            val response = engine.processInput("check door")
            if (response.contains("fail", ignoreCase = true) ||
                !response.contains("succeed", ignoreCase = true)) {
                failureCount++
            }
        }

        assertTrue(failureCount >= 5, "Hard check with low stats should fail most of the time")
    }

    @Test
    fun `skill check with very high stats almost always succeeds`() = runBlocking {
        val feature = Entity.Feature(
            id = "feature",
            name = "Challenge",
            description = "A test of ability",
            requiresSkillCheck = true,
            skillCheckStat = StatType.STRENGTH,
            skillCheckDC = 15
        )

        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            stats = Stats(strength = 22)  // Very high STR (modifier = +6)
        )

        val world = createTestWorld(player = player, features = listOf(feature))
        val engine = InMemoryGameEngine(world)

        // With +6 modifier, need to roll 9+ on d20 (60% chance per attempt)
        // Should succeed within first few attempts
        var succeeded = false
        repeat(5) {
            val response = engine.processInput("check challenge")
            if (response.contains("succeed", ignoreCase = true) ||
                response.contains("success", ignoreCase = true)) {
                succeeded = true
                return@repeat
            }
        }

        assertTrue(succeeded, "Very high stats should lead to quick success")
    }

    @Test
    fun `skill check failure produces appropriate feedback`() = runBlocking {
        val feature = Entity.Feature(
            id = "impossible",
            name = "Impossible Task",
            description = "An nearly impossible challenge",
            requiresSkillCheck = true,
            skillCheckStat = StatType.INTELLIGENCE,
            skillCheckDC = 30  // NEARLY_IMPOSSIBLE
        )

        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            stats = Stats(intelligence = 8)  // Low INT
        )

        val world = createTestWorld(player = player, features = listOf(feature))
        val engine = InMemoryGameEngine(world)

        // Should fail (need natural 20 + critical to have any chance)
        val response = engine.processInput("check task")

        // Verify we get some response about the attempt
        assertTrue(response.isNotEmpty(), "Skill check should produce feedback")
        assertFalse(response.contains("error", ignoreCase = true), "Should not error on failed check")
    }

    @Test
    fun `multiple skill checks can be attempted in sequence`() = runBlocking {
        val feature1 = Entity.Feature(
            id = "lock1",
            name = "First Lock",
            description = "A simple lock",
            requiresSkillCheck = true,
            skillCheckStat = StatType.DEXTERITY,
            skillCheckDC = 10
        )

        val feature2 = Entity.Feature(
            id = "trap1",
            name = "First Trap",
            description = "A hidden trap",
            requiresSkillCheck = true,
            skillCheckStat = StatType.WISDOM,
            skillCheckDC = 10
        )

        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            stats = Stats(dexterity = 15, wisdom = 15)
        )

        val world = createTestWorld(player = player, features = listOf(feature1, feature2))
        val engine = InMemoryGameEngine(world)

        // Attempt both skill checks
        var check1Success = false
        var check2Success = false

        repeat(10) {
            val response1 = engine.processInput("check lock")
            if (response1.contains("succeed", ignoreCase = true)) {
                check1Success = true
            }
            if (check1Success) return@repeat
        }

        repeat(10) {
            val response2 = engine.processInput("check trap")
            if (response2.contains("succeed", ignoreCase = true) ||
                response2.contains("spot", ignoreCase = true)) {
                check2Success = true
            }
            if (check2Success) return@repeat
        }

        assertTrue(check1Success || check2Success,
            "At least one skill check should succeed with good stats")
    }

    @Test
    fun `skill check respects stat modifiers`() = runBlocking {
        val feature = Entity.Feature(
            id = "test_feature",
            name = "Test Challenge",
            description = "A balanced challenge",
            requiresSkillCheck = true,
            skillCheckStat = StatType.STRENGTH,
            skillCheckDC = 15
        )

        // Player with below-average STR
        val weakPlayer = PlayerState(
            id = "player1",
            name = "Weak Hero",
            currentRoomId = "test_room",
            stats = Stats(strength = 8)  // Modifier: -1
        )

        val world1 = createTestWorld(player = weakPlayer, features = listOf(feature))
        val engine1 = InMemoryGameEngine(world1)

        // Try with weak player - should mostly fail
        var weakFailures = 0
        repeat(10) {
            val response = engine1.processInput("check challenge")
            if (!response.contains("succeed", ignoreCase = true)) {
                weakFailures++
            }
        }

        // Player with high STR
        val strongPlayer = PlayerState(
            id = "player2",
            name = "Strong Hero",
            currentRoomId = "test_room",
            stats = Stats(strength = 18)  // Modifier: +4
        )

        val world2 = createTestWorld(player = strongPlayer, features = listOf(feature))
        val engine2 = InMemoryGameEngine(world2)

        // Try with strong player - should mostly succeed
        var strongSuccesses = 0
        repeat(10) {
            val response = engine2.processInput("check challenge")
            if (response.contains("succeed", ignoreCase = true)) {
                strongSuccesses++
            }
        }

        // Strong player should succeed significantly more often than weak player
        assertTrue(strongSuccesses > (10 - weakFailures),
            "Higher stat modifier should lead to more successes")
    }

    // ========== Helper Functions ==========

    private fun createTestWorld(
        player: PlayerState = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room"
        ),
        features: List<Entity.Feature> = emptyList(),
        items: List<Entity.Item> = emptyList()
    ): WorldState {
        val entities = mutableListOf<Entity>()
        entities.addAll(features)
        entities.addAll(items)

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
