package com.jcraw.mud.core

import kotlin.test.*

class BossFlagsTest {

    @Test
    fun `isValid returns true for valid boss designation`() {
        val boss = BossDesignation(
            isBoss = true,
            bossTitle = "Abyssal Lord",
            victoryFlag = "abyssal_lord_defeated"
        )

        assertTrue(boss.isValid())
    }

    @Test
    fun `isValid returns false when isBoss is false`() {
        val boss = BossDesignation(
            isBoss = false,
            bossTitle = "Abyssal Lord",
            victoryFlag = "abyssal_lord_defeated"
        )

        assertFalse(boss.isValid())
    }

    @Test
    fun `isValid returns false when bossTitle is blank`() {
        val boss = BossDesignation(
            isBoss = true,
            bossTitle = "",
            victoryFlag = "abyssal_lord_defeated"
        )

        assertFalse(boss.isValid())
    }

    @Test
    fun `isValid returns false when victoryFlag is blank`() {
        val boss = BossDesignation(
            isBoss = true,
            bossTitle = "Abyssal Lord",
            victoryFlag = ""
        )

        assertFalse(boss.isValid())
    }

    @Test
    fun `displayName returns boss title when valid`() {
        val boss = BossDesignation(
            isBoss = true,
            bossTitle = "Dragon King",
            victoryFlag = "dragon_king_defeated"
        )

        assertEquals("Dragon King", boss.displayName())
    }

    @Test
    fun `displayName returns empty string when not a boss`() {
        val boss = BossDesignation(
            isBoss = false,
            bossTitle = "Dragon King",
            victoryFlag = ""
        )

        assertEquals("", boss.displayName())
    }

    @Test
    fun `create factory method creates valid boss`() {
        val boss = BossDesignation.create("Lich King", "lich_king_defeated")

        assertTrue(boss.isBoss)
        assertEquals("Lich King", boss.bossTitle)
        assertEquals("lich_king_defeated", boss.victoryFlag)
        assertTrue(boss.isValid())
    }

    @Test
    fun `NONE constant is not a boss`() {
        val none = BossDesignation.NONE

        assertFalse(none.isBoss)
        assertFalse(none.isValid())
        assertEquals("", none.displayName())
    }
}
