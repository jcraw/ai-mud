package com.jcraw.mud.reasoning

import com.jcraw.mud.core.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * Tests for CombatResolver damage calculations and combat mechanics
 *
 * Focus: Behavioral tests for damage calculation, combat state transitions, and equipment modifiers
 *
 * Note: Random damage rolls make exact damage values unpredictable, so we test:
 * - Combat state transitions (initiate, end)
 * - Damage modifiers (weapons, armor, stats)
 * - Combat end conditions (death, flee)
 * - Relative damage amounts (with bonuses > without bonuses)
 */
class CombatResolverTest {

    private val combatResolver = CombatResolver()

    // Test fixtures
    private val weakGoblin = Entity.NPC(
        id = "goblin",
        name = "Goblin",
        description = "A weak goblin",
        isHostile = true,
        health = 10,
        maxHealth = 10,
        stats = Stats(strength = 8) // -1 STR modifier
    )

    private val strongOrc = Entity.NPC(
        id = "orc",
        name = "Orc",
        description = "A strong orc",
        isHostile = true,
        health = 50,
        maxHealth = 50,
        stats = Stats(strength = 18) // +4 STR modifier
    )

    private val sword = Entity.Item(
        id = "sword",
        name = "Iron Sword",
        description = "A sturdy sword",
        itemType = ItemType.WEAPON,
        damageBonus = 5
    )

    private val greatSword = Entity.Item(
        id = "greatsword",
        name = "Great Sword",
        description = "A massive two-handed sword",
        itemType = ItemType.WEAPON,
        damageBonus = 10
    )

    private val leatherArmor = Entity.Item(
        id = "leather",
        name = "Leather Armor",
        description = "Light leather armor",
        itemType = ItemType.ARMOR,
        defenseBonus = 3
    )

    private val plateArmor = Entity.Item(
        id = "plate",
        name = "Plate Armor",
        description = "Heavy plate armor",
        itemType = ItemType.ARMOR,
        defenseBonus = 6
    )

    private fun createTestWorld(player: PlayerState, vararg entities: Entity): WorldState {
        val room = Room(
            id = "test_room",
            name = "Test Room",
            traits = listOf("test"),
            entities = entities.toList()
        )

        return WorldState(
            rooms = mapOf("test_room" to room),
            players = mapOf(player.id to player)
        )
    }

    // ========== Combat Initiation Tests ==========

    @Test
    fun `initiating combat creates combat state`() {
        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            health = 100
        )
        val world = createTestWorld(player, weakGoblin)

        val result = combatResolver.initiateCombat(world, player, "goblin")

        assertNotNull(result)
        assertNotNull(result?.newCombatState)
        assertEquals("goblin", result?.newCombatState?.combatantNpcId)
        assertEquals(100, result?.newCombatState?.playerHealth)
        assertEquals(10, result?.newCombatState?.npcHealth)
        assertTrue(result?.newCombatState?.isPlayerTurn == true)
        assertFalse(result?.playerDied == true)
        assertFalse(result?.npcDied == true)
    }

    @Test
    fun `initiating combat with non-existent NPC returns null`() {
        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room"
        )
        val world = createTestWorld(player)

        val result = combatResolver.initiateCombat(world, player, "nonexistent")

        assertNull(result)
    }

    // ========== Player Attack Tests ==========

    @Test
    fun `player attack reduces NPC health`() {
        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            health = 100,
            activeCombat = CombatState(
                combatantNpcId = "goblin",
                playerHealth = 100,
                npcHealth = 50,
                isPlayerTurn = true
            )
        )
        val world = createTestWorld(player, weakGoblin)

        val result = combatResolver.executePlayerAttack(world, player)

        assertNotNull(result.newCombatState)
        assertTrue(result.newCombatState!!.npcHealth < 50, "NPC should take damage")
        assertFalse(result.npcDied, "Goblin should not die from one hit at 50 HP")
    }

    @Test
    fun `player attack when not in combat returns appropriate message`() {
        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            activeCombat = null
        )
        val world = createTestWorld(player)

        val result = combatResolver.executePlayerAttack(world, player)

        assertEquals("You are not in combat.", result.narrative)
        assertNull(result.newCombatState)
    }

    @Test
    fun `defeating NPC ends combat`() {
        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            health = 100,
            stats = Stats(strength = 20), // +5 STR modifier for high damage
            activeCombat = CombatState(
                combatantNpcId = "goblin",
                playerHealth = 100,
                npcHealth = 1, // One hit away from death
                isPlayerTurn = true
            )
        )
        val world = createTestWorld(player, weakGoblin)

        val result = combatResolver.executePlayerAttack(world, player)

        assertNull(result.newCombatState, "Combat should end when NPC dies")
        assertTrue(result.npcDied)
        assertFalse(result.playerDied)
    }

    @Test
    fun `player dying ends combat`() {
        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            health = 100,
            stats = Stats(strength = 8), // Low damage
            activeCombat = CombatState(
                combatantNpcId = "orc",
                playerHealth = 3, // Low health, will likely die from orc counter-attack
                npcHealth = 50,
                isPlayerTurn = true
            )
        )
        val world = createTestWorld(player, strongOrc)

        // Multiple attacks until player dies
        var result: CombatResult
        var currentPlayer = player
        var attempts = 0
        val maxAttempts = 10

        do {
            result = combatResolver.executePlayerAttack(world, currentPlayer)
            if (result.playerDied || result.newCombatState == null) break

            // Update player for next attack
            currentPlayer = currentPlayer.updateCombat(result.newCombatState)
            attempts++
        } while (attempts < maxAttempts)

        assertTrue(attempts < maxAttempts, "Player should eventually die or combat should end")
        assertNull(result.newCombatState, "Combat should end when player dies")
    }

    // ========== Equipment Modifier Tests ==========

    @Test
    fun `equipped weapon increases player damage`() {
        // Create two players, one with weapon, one without
        val playerNoWeapon = PlayerState(
            id = "player1",
            name = "Unarmed Hero",
            currentRoomId = "test_room",
            health = 100,
            stats = Stats(strength = 10), // 0 STR modifier
            activeCombat = CombatState(
                combatantNpcId = "goblin",
                playerHealth = 100,
                npcHealth = 100,
                isPlayerTurn = true
            )
        )

        val playerWithSword = playerNoWeapon.copy(
            id = "player2",
            equippedWeapon = sword
        )

        val world = createTestWorld(playerNoWeapon, weakGoblin)

        // Sample multiple attacks to get average damage
        var totalDamageNoWeapon = 0
        var totalDamageWithWeapon = 0
        val samples = 20

        repeat(samples) {
            val resultNoWeapon = combatResolver.executePlayerAttack(world, playerNoWeapon)
            val resultWithWeapon = combatResolver.executePlayerAttack(world, playerWithSword)

            totalDamageNoWeapon += resultNoWeapon.playerDamage ?: 0
            totalDamageWithWeapon += resultWithWeapon.playerDamage ?: 0
        }

        val avgDamageNoWeapon = totalDamageNoWeapon / samples
        val avgDamageWithWeapon = totalDamageWithWeapon / samples

        // With +5 weapon bonus, average should be noticeably higher
        assertTrue(
            avgDamageWithWeapon > avgDamageNoWeapon,
            "Weapon (+5) should increase average damage: $avgDamageWithWeapon > $avgDamageNoWeapon"
        )
    }

    @Test
    fun `equipped armor reduces incoming damage`() {
        // Create two players, one with armor, one without
        val playerNoArmor = PlayerState(
            id = "player1",
            name = "Unarmored Hero",
            currentRoomId = "test_room",
            health = 100,
            stats = Stats(strength = 10),
            activeCombat = CombatState(
                combatantNpcId = "orc",
                playerHealth = 100,
                npcHealth = 100,
                isPlayerTurn = true
            )
        )

        val playerWithArmor = playerNoArmor.copy(
            id = "player2",
            equippedArmor = plateArmor
        )

        val world = createTestWorld(playerNoArmor, strongOrc)

        // Sample multiple attacks to get average NPC damage
        var totalDamageNoArmor = 0
        var totalDamageWithArmor = 0
        val samples = 20

        repeat(samples) {
            val resultNoArmor = combatResolver.executePlayerAttack(world, playerNoArmor)
            val resultWithArmor = combatResolver.executePlayerAttack(world, playerWithArmor)

            totalDamageNoArmor += resultNoArmor.npcDamage ?: 0
            totalDamageWithArmor += resultWithArmor.npcDamage ?: 0
        }

        val avgDamageNoArmor = totalDamageNoArmor / samples
        val avgDamageWithArmor = totalDamageWithArmor / samples

        // With +6 armor defense, average should be noticeably lower
        assertTrue(
            avgDamageWithArmor < avgDamageNoArmor,
            "Armor (+6 defense) should reduce average incoming damage: $avgDamageWithArmor < $avgDamageNoArmor"
        )
    }

    @Test
    fun `STR modifier affects player damage`() {
        val weakPlayer = PlayerState(
            id = "player1",
            name = "Weak Hero",
            currentRoomId = "test_room",
            health = 100,
            stats = Stats(strength = 8), // -1 STR modifier
            activeCombat = CombatState(
                combatantNpcId = "goblin",
                playerHealth = 100,
                npcHealth = 100,
                isPlayerTurn = true
            )
        )

        val strongPlayer = weakPlayer.copy(
            id = "player2",
            stats = Stats(strength = 18) // +4 STR modifier
        )

        val world = createTestWorld(weakPlayer, weakGoblin)

        // Sample multiple attacks
        var totalDamageWeak = 0
        var totalDamageStrong = 0
        val samples = 20

        repeat(samples) {
            val resultWeak = combatResolver.executePlayerAttack(world, weakPlayer)
            val resultStrong = combatResolver.executePlayerAttack(world, strongPlayer)

            totalDamageWeak += resultWeak.playerDamage ?: 0
            totalDamageStrong += resultStrong.playerDamage ?: 0
        }

        val avgDamageWeak = totalDamageWeak / samples
        val avgDamageStrong = totalDamageStrong / samples

        // 5 point STR difference = 5 point modifier difference
        assertTrue(
            avgDamageStrong > avgDamageWeak,
            "High STR (+4) should deal more damage than low STR (-1): $avgDamageStrong > $avgDamageWeak"
        )
    }

    @Test
    fun `damage is always at least 1`() {
        // Create player with very low damage potential
        val weakPlayer = PlayerState(
            id = "player1",
            name = "Very Weak Hero",
            currentRoomId = "test_room",
            health = 100,
            stats = Stats(strength = 3), // -4 STR modifier (very weak)
            activeCombat = CombatState(
                combatantNpcId = "goblin",
                playerHealth = 100,
                npcHealth = 100,
                isPlayerTurn = true
            )
        )

        val world = createTestWorld(weakPlayer, weakGoblin)

        // Even with low stats, minimum damage should be 1
        repeat(20) {
            val result = combatResolver.executePlayerAttack(world, weakPlayer)
            assertTrue(
                (result.playerDamage ?: 0) >= 1,
                "Player damage should always be at least 1"
            )
            assertTrue(
                (result.npcDamage ?: 0) >= 1,
                "NPC damage should always be at least 1"
            )
        }
    }

    // ========== Combat Flow Tests ==========

    @Test
    fun `combat progresses through multiple rounds`() {
        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            health = 100,
            stats = Stats(strength = 12),
            activeCombat = CombatState(
                combatantNpcId = "orc",
                playerHealth = 100,
                npcHealth = 50,
                isPlayerTurn = true,
                turnCount = 0
            )
        )
        val world = createTestWorld(player, strongOrc)

        var currentPlayer = player
        var rounds = 0
        val maxRounds = 50

        // Fight until combat ends
        while (currentPlayer.activeCombat != null && rounds < maxRounds) {
            val result = combatResolver.executePlayerAttack(world, currentPlayer)

            val combatState = result.newCombatState
            if (combatState != null) {
                // Combat continues
                currentPlayer = currentPlayer.updateCombat(combatState)
                assertTrue(combatState.playerHealth >= 0)
                assertTrue(combatState.npcHealth >= 0)
            } else {
                // Combat ended
                currentPlayer = currentPlayer.endCombat()
                assertTrue(result.playerDied || result.npcDied || result.playerFled)
                break
            }

            rounds++
        }

        assertTrue(rounds < maxRounds, "Combat should resolve within reasonable rounds")
        assertNull(currentPlayer.activeCombat, "Combat should be null after it ends")
    }

    @Test
    fun `NPC counter-attack occurs after player attack`() {
        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            health = 100,
            activeCombat = CombatState(
                combatantNpcId = "goblin",
                playerHealth = 100,
                npcHealth = 50,
                isPlayerTurn = true
            )
        )
        val world = createTestWorld(player, weakGoblin)

        val result = combatResolver.executePlayerAttack(world, player)

        // Both damages should be recorded
        assertNotNull(result.playerDamage, "Player should deal damage")
        assertNotNull(result.npcDamage, "NPC should counter-attack")
        assertTrue(result.playerDamage!! > 0)
        assertTrue(result.npcDamage!! > 0)
    }

    // ========== Flee Tests ==========

    @Test
    fun `fleeing eventually succeeds or player dies`() {
        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            health = 100,
            activeCombat = CombatState(
                combatantNpcId = "goblin",
                playerHealth = 100,
                npcHealth = 50,
                isPlayerTurn = true
            )
        )
        val world = createTestWorld(player, weakGoblin)

        var currentPlayer = player
        var fleeAttempts = 0
        val maxAttempts = 20
        var fleeSucceeded = false

        // Try to flee multiple times (50% chance each time)
        while (fleeAttempts < maxAttempts) {
            val result = combatResolver.attemptFlee(world, currentPlayer)

            if (result.playerFled) {
                fleeSucceeded = true
                assertNull(result.newCombatState, "Combat should end on successful flee")
                break
            }

            if (result.playerDied) {
                assertNull(result.newCombatState, "Combat should end on death")
                break
            }

            if (result.newCombatState != null) {
                currentPlayer = currentPlayer.updateCombat(result.newCombatState)
            }

            fleeAttempts++
        }

        // With 50% flee chance, should succeed within 20 attempts (probability of all failing: 0.5^20 ≈ 0.0001%)
        assertTrue(fleeSucceeded || currentPlayer.activeCombat == null, "Should flee or die within 20 attempts")
    }

    @Test
    fun `attempting to flee when not in combat returns appropriate message`() {
        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            activeCombat = null
        )
        val world = createTestWorld(player)

        val result = combatResolver.attemptFlee(world, player)

        assertEquals("You are not in combat.", result.narrative)
        assertNull(result.newCombatState)
    }
}
