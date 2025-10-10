package com.jcraw.mud.reasoning

import com.jcraw.mud.core.*
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Tests for SkillCheckResolver
 * Focuses on behavior: modifiers are applied correctly, DCs work, criticals trigger
 */
class SkillCheckResolverTest {

    @Test
    fun `should succeed with high stat against easy DC`() {
        // Fixed roll of 10 + high STR modifier (+5) = 15, vs DC 10 (Easy)
        val resolver = SkillCheckResolver(FixedRandom(10))
        val stats = Stats(strength = 20) // +5 modifier

        val player = PlayerState("player1", "Test", "room1", stats = stats)
        val result = resolver.checkPlayer(player, StatType.STRENGTH, Difficulty.EASY)

        assertTrue(result.success, "Should succeed with high stat")
        assertEquals(10, result.roll)
        assertEquals(5, result.modifier)
        assertEquals(15, result.total)
        assertEquals(10, result.dc)
        assertEquals(5, result.margin)
    }

    @Test
    fun `should fail with low stat against hard DC`() {
        // Fixed roll of 10 + low INT modifier (-2) = 8, vs DC 20 (Hard)
        val resolver = SkillCheckResolver(FixedRandom(10))
        val stats = Stats(intelligence = 6) // -2 modifier

        val player = PlayerState("player1", "Test", "room1", stats = stats)
        val result = resolver.checkPlayer(player, StatType.INTELLIGENCE, Difficulty.HARD)

        assertFalse(result.success, "Should fail with low stat")
        assertEquals(10, result.roll)
        assertEquals(-2, result.modifier)
        assertEquals(8, result.total)
        assertEquals(20, result.dc)
        assertEquals(-12, result.margin)
    }

    @Test
    fun `should detect critical success on natural 20`() {
        val resolver = SkillCheckResolver(FixedRandom(20))
        val player = PlayerState("player1", "Test", "room1")

        val result = resolver.checkPlayer(player, StatType.DEXTERITY, Difficulty.MEDIUM)

        assertTrue(result.isCriticalSuccess, "Natural 20 should be critical success")
        assertFalse(result.isCriticalFailure)
        assertEquals(20, result.roll)
    }

    @Test
    fun `should detect critical failure on natural 1`() {
        val resolver = SkillCheckResolver(FixedRandom(1))
        val player = PlayerState("player1", "Test", "room1")

        val result = resolver.checkPlayer(player, StatType.WISDOM, Difficulty.EASY)

        assertTrue(result.isCriticalFailure, "Natural 1 should be critical failure")
        assertFalse(result.isCriticalSuccess)
        assertEquals(1, result.roll)
    }

    @Test
    fun `should apply correct modifiers for all stats`() {
        val resolver = SkillCheckResolver(FixedRandom(10))
        val stats = Stats(
            strength = 16,     // +3
            dexterity = 14,    // +2
            constitution = 12, // +1
            intelligence = 10, // +0
            wisdom = 8,        // -1
            charisma = 6       // -2
        )

        val player = PlayerState("player1", "Test", "room1", stats = stats)

        val strResult = resolver.checkPlayer(player, StatType.STRENGTH, Difficulty.MEDIUM)
        assertEquals(3, strResult.modifier, "STR 16 should give +3")

        val dexResult = resolver.checkPlayer(player, StatType.DEXTERITY, Difficulty.MEDIUM)
        assertEquals(2, dexResult.modifier, "DEX 14 should give +2")

        val conResult = resolver.checkPlayer(player, StatType.CONSTITUTION, Difficulty.MEDIUM)
        assertEquals(1, conResult.modifier, "CON 12 should give +1")

        val intResult = resolver.checkPlayer(player, StatType.INTELLIGENCE, Difficulty.MEDIUM)
        assertEquals(0, intResult.modifier, "INT 10 should give +0")

        val wisResult = resolver.checkPlayer(player, StatType.WISDOM, Difficulty.MEDIUM)
        assertEquals(-1, wisResult.modifier, "WIS 8 should give -1")

        val chaResult = resolver.checkPlayer(player, StatType.CHARISMA, Difficulty.MEDIUM)
        assertEquals(-2, chaResult.modifier, "CHA 6 should give -2")
    }

    @Test
    fun `should handle NPC skill checks`() {
        val resolver = SkillCheckResolver(FixedRandom(12))
        val npc = Entity.NPC(
            id = "npc1",
            name = "Test NPC",
            description = "A test NPC",
            stats = Stats(strength = 18) // +4 modifier
        )

        val result = resolver.checkNPC(npc, StatType.STRENGTH, Difficulty.MEDIUM)

        assertTrue(result.success, "NPC should succeed with high STR")
        assertEquals(12, result.roll)
        assertEquals(4, result.modifier)
        assertEquals(16, result.total)
    }

    @Test
    fun `opposed check should compare rolls correctly`() {
        // Player rolls 15 (10+5), NPC rolls 12 (10+2)
        val resolver = SkillCheckResolver(AlternatingRandom(listOf(15, 12)))
        val player = PlayerState("player1", "Test", "room1", stats = Stats(strength = 20)) // +5
        val npc = Entity.NPC("npc1", "NPC", "Test", stats = Stats(strength = 14)) // +2

        val playerWins = resolver.opposedCheck(player, npc, StatType.STRENGTH, StatType.STRENGTH)

        assertTrue(playerWins, "Player should win with higher total")
    }

    @Test
    fun `should handle edge case at exact DC`() {
        // Roll of 10 + modifier 5 = 15, exactly matching DC 15
        val resolver = SkillCheckResolver(FixedRandom(10))
        val player = PlayerState("player1", "Test", "room1", stats = Stats(dexterity = 20)) // +5

        val result = resolver.checkPlayer(player, StatType.DEXTERITY, Difficulty.MEDIUM)

        assertTrue(result.success, "Should succeed when total equals DC")
        assertEquals(15, result.total)
        assertEquals(15, result.dc)
        assertEquals(0, result.margin)
    }

    /**
     * Test helper - deterministic random that always returns the same value
     */
    private class FixedRandom(private val value: Int) : Random() {
        override fun nextBits(bitCount: Int): Int = value
        override fun nextInt(from: Int, until: Int): Int = value
    }

    /**
     * Test helper - alternates between values in a list
     */
    private class AlternatingRandom(private val values: List<Int>) : Random() {
        private var index = 0
        override fun nextBits(bitCount: Int): Int = nextValue()
        override fun nextInt(from: Int, until: Int): Int = nextValue()
        private fun nextValue(): Int {
            val value = values[index % values.size]
            index++
            return value
        }
    }
}
