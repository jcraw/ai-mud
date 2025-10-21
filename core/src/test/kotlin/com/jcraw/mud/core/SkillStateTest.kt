package com.jcraw.mud.core

import kotlin.test.*

class SkillStateTest {

    // XP Calculation Tests

    @Test
    fun `calculateXpToNext for early levels uses quadratic formula`() {
        val skill = SkillState(level = 1)
        // Next level is 2, so XP = 100 * 2^2 = 400
        assertEquals(400L, skill.calculateXpToNext(1))
    }

    @Test
    fun `calculateXpToNext for level 10`() {
        val skill = SkillState(level = 10)
        // Next level is 11, so XP = 100 * 11^2 = 12,100
        assertEquals(12100L, skill.calculateXpToNext(10))
    }

    @Test
    fun `calculateXpToNext for level 50`() {
        val skill = SkillState(level = 50)
        // Next level is 51, so XP = 100 * 51^2 = 260,100
        assertEquals(260100L, skill.calculateXpToNext(50))
    }

    @Test
    fun `calculateXpToNext for level 100`() {
        val skill = SkillState(level = 100)
        // Next level is 101, which triggers exponential scaling
        // 100 * 101^2 * (101/100)^1.5 = 1,020,100 * 1.0150... ≈ 1,035,402
        val result = skill.calculateXpToNext(100)
        assertTrue(result > 1_020_100L) // Should be scaled up from base
    }

    @Test
    fun `calculateXpToNext for level 101 uses exponential scaling`() {
        val skill = SkillState(level = 101)
        // Next level is 102, so base = 100 * 102^2 = 1,040,400
        // Scaled by (102/100)^1.5 ≈ 1.0303
        val result = skill.calculateXpToNext(101)
        assertTrue(result > 1_040_400L)
    }

    @Test
    fun `calculateXpToNext for level 200 has massive XP requirement`() {
        val skill = SkillState(level = 200)
        // Next level is 201, base = 100 * 201^2 = 4,040,100
        // Scaled by (201/100)^1.5 ≈ 2.854
        val result = skill.calculateXpToNext(200)
        assertTrue(result > 10_000_000L) // Should be in the millions
    }

    // XP Granting and Leveling Tests

    @Test
    fun `addXp increases XP without leveling if below threshold`() {
        val skill = SkillState(level = 1, xp = 0)
        val updated = skill.addXp(100)

        assertEquals(1, updated.level)
        assertEquals(100L, updated.xp)
    }

    @Test
    fun `addXp levels up when XP exceeds threshold`() {
        val skill = SkillState(level = 1, xp = 0)
        // Need 400 XP to reach level 2
        val updated = skill.addXp(500)

        assertEquals(2, updated.level)
        assertEquals(100L, updated.xp) // 500 - 400 = 100 XP towards level 3
    }

    @Test
    fun `addXp handles multiple level-ups in one grant`() {
        val skill = SkillState(level = 1, xp = 0)
        // Level 1→2 needs 400, 2→3 needs 900, 3→4 needs 1600
        // Total for 3 levels: 400 + 900 + 1600 = 2900
        val updated = skill.addXp(3000)

        assertEquals(4, updated.level)
        assertEquals(100L, updated.xp) // 3000 - 2900 = 100 XP towards level 5
    }

    @Test
    fun `addXp with zero amount does nothing`() {
        val skill = SkillState(level = 5, xp = 123)
        val updated = skill.addXp(0)

        assertEquals(5, updated.level)
        assertEquals(123L, updated.xp)
    }

    @Test
    fun `addXp with negative amount throws exception`() {
        val skill = SkillState()
        assertFailsWith<IllegalArgumentException> {
            skill.addXp(-100)
        }
    }

    // Unlock Tests

    @Test
    fun `unlock sets unlocked to true`() {
        val skill = SkillState(unlocked = false)
        val updated = skill.unlock()

        assertTrue(updated.unlocked)
    }

    @Test
    fun `unlock on already unlocked skill is idempotent`() {
        val skill = SkillState(unlocked = true)
        val updated = skill.unlock()

        assertTrue(updated.unlocked)
    }

    // Effective Level Tests

    @Test
    fun `getEffectiveLevel without buffs returns base level`() {
        val skill = SkillState(level = 10, tempBuffs = 0)
        assertEquals(10, skill.getEffectiveLevel())
    }

    @Test
    fun `getEffectiveLevel with buffs returns sum`() {
        val skill = SkillState(level = 10, tempBuffs = 5)
        assertEquals(15, skill.getEffectiveLevel())
    }

    @Test
    fun `applyBuff increases temporary buffs`() {
        val skill = SkillState(level = 10, tempBuffs = 0)
        val updated = skill.applyBuff(3)

        assertEquals(3, updated.tempBuffs)
        assertEquals(13, updated.getEffectiveLevel())
    }

    @Test
    fun `clearBuffs resets tempBuffs to zero`() {
        val skill = SkillState(level = 10, tempBuffs = 5)
        val updated = skill.clearBuffs()

        assertEquals(0, updated.tempBuffs)
        assertEquals(10, updated.getEffectiveLevel())
    }

    // Perk Tests

    @Test
    fun `addPerk adds perk to list`() {
        val skill = SkillState(perks = emptyList())
        val perk = Perk("Quick Strike", "Attack faster", PerkType.ABILITY)
        val updated = skill.addPerk(perk)

        assertEquals(1, updated.perks.size)
        assertEquals("Quick Strike", updated.perks[0].name)
    }

    @Test
    fun `isAtPerkMilestone returns true for level 10, 20, 30`() {
        assertTrue(SkillState(level = 10).isAtPerkMilestone())
        assertTrue(SkillState(level = 20).isAtPerkMilestone())
        assertTrue(SkillState(level = 30).isAtPerkMilestone())
    }

    @Test
    fun `isAtPerkMilestone returns false for non-milestone levels`() {
        assertFalse(SkillState(level = 0).isAtPerkMilestone())
        assertFalse(SkillState(level = 5).isAtPerkMilestone())
        assertFalse(SkillState(level = 11).isAtPerkMilestone())
        assertFalse(SkillState(level = 15).isAtPerkMilestone())
    }

    @Test
    fun `getPerkMilestonesEarned returns correct count`() {
        assertEquals(0, SkillState(level = 0).getPerkMilestonesEarned())
        assertEquals(0, SkillState(level = 9).getPerkMilestonesEarned())
        assertEquals(1, SkillState(level = 10).getPerkMilestonesEarned())
        assertEquals(1, SkillState(level = 19).getPerkMilestonesEarned())
        assertEquals(2, SkillState(level = 20).getPerkMilestonesEarned())
        assertEquals(5, SkillState(level = 50).getPerkMilestonesEarned())
    }

    @Test
    fun `hasPendingPerkChoice returns true when milestones exceed perks`() {
        val skill = SkillState(level = 20, perks = emptyList())
        // Level 20 = 2 milestones, 0 perks chosen
        assertTrue(skill.hasPendingPerkChoice())
    }

    @Test
    fun `hasPendingPerkChoice returns false when perks match milestones`() {
        val perk1 = Perk("Perk 1", "First perk", PerkType.PASSIVE)
        val perk2 = Perk("Perk 2", "Second perk", PerkType.ABILITY)
        val skill = SkillState(level = 20, perks = listOf(perk1, perk2))
        // Level 20 = 2 milestones, 2 perks chosen
        assertFalse(skill.hasPendingPerkChoice())
    }

    // Immutability Tests

    @Test
    fun `addXp returns new instance and does not modify original`() {
        val original = SkillState(level = 1, xp = 0)
        val updated = original.addXp(100)

        assertEquals(0L, original.xp) // Original unchanged
        assertEquals(100L, updated.xp) // New instance updated
        assertNotSame(original, updated)
    }

    @Test
    fun `unlock returns new instance and does not modify original`() {
        val original = SkillState(unlocked = false)
        val updated = original.unlock()

        assertFalse(original.unlocked) // Original unchanged
        assertTrue(updated.unlocked) // New instance updated
        assertNotSame(original, updated)
    }

    @Test
    fun `applyBuff returns new instance and does not modify original`() {
        val original = SkillState(tempBuffs = 0)
        val updated = original.applyBuff(5)

        assertEquals(0, original.tempBuffs) // Original unchanged
        assertEquals(5, updated.tempBuffs) // New instance updated
        assertNotSame(original, updated)
    }

    // Edge Cases

    @Test
    fun `xpToNext property calculates for current level`() {
        val skill = SkillState(level = 5)
        // Next level is 6, so XP = 100 * 6^2 = 3600
        assertEquals(3600L, skill.xpToNext)
    }

    @Test
    fun `skill with resource type stores it correctly`() {
        val skill = SkillState(resourceType = "mana")
        assertEquals("mana", skill.resourceType)
    }

    @Test
    fun `skill with tags stores them correctly`() {
        val skill = SkillState(tags = listOf("combat", "melee", "weapon"))
        assertEquals(3, skill.tags.size)
        assertTrue("combat" in skill.tags)
    }
}
