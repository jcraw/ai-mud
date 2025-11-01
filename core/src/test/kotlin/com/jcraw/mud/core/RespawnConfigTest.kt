package com.jcraw.mud.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RespawnConfigTest {

    @Test
    fun `default config is enabled`() {
        val config = RespawnConfig()
        assertTrue(config.enabled)
    }

    @Test
    fun `default config has difficulty scaling`() {
        val config = RespawnConfig()
        assertEquals(300L, config.getRespawnTime(5))
        assertEquals(500L, config.getRespawnTime(20))
        assertEquals(1000L, config.getRespawnTime(50))
        assertEquals(Long.MAX_VALUE, config.getRespawnTime(80))
    }

    @Test
    fun `getRespawnTime with difficulty 1 returns shallow tier`() {
        val config = RespawnConfig()
        assertEquals(300L, config.getRespawnTime(1))
    }

    @Test
    fun `getRespawnTime with difficulty 10 returns shallow tier`() {
        val config = RespawnConfig()
        assertEquals(300L, config.getRespawnTime(10))
    }

    @Test
    fun `getRespawnTime with difficulty 11 returns mid tier`() {
        val config = RespawnConfig()
        assertEquals(500L, config.getRespawnTime(11))
    }

    @Test
    fun `getRespawnTime with difficulty 30 returns mid tier`() {
        val config = RespawnConfig()
        assertEquals(500L, config.getRespawnTime(30))
    }

    @Test
    fun `getRespawnTime with difficulty 31 returns deep tier`() {
        val config = RespawnConfig()
        assertEquals(1000L, config.getRespawnTime(31))
    }

    @Test
    fun `getRespawnTime with difficulty 60 returns deep tier`() {
        val config = RespawnConfig()
        assertEquals(1000L, config.getRespawnTime(60))
    }

    @Test
    fun `getRespawnTime with difficulty 61 returns boss tier (no respawn)`() {
        val config = RespawnConfig()
        assertEquals(Long.MAX_VALUE, config.getRespawnTime(61))
    }

    @Test
    fun `getRespawnTime with difficulty 100 returns boss tier (no respawn)`() {
        val config = RespawnConfig()
        assertEquals(Long.MAX_VALUE, config.getRespawnTime(100))
    }

    @Test
    fun `getRespawnTime with out-of-range difficulty returns no respawn`() {
        val config = RespawnConfig()
        assertEquals(Long.MAX_VALUE, config.getRespawnTime(200))
        assertEquals(Long.MAX_VALUE, config.getRespawnTime(0))
    }

    @Test
    fun `custom difficulty scaling can be configured`() {
        val customScaling = mapOf(
            1..5 to 100L,
            6..10 to 200L
        )
        val config = RespawnConfig(difficultyScaling = customScaling)

        assertEquals(100L, config.getRespawnTime(3))
        assertEquals(200L, config.getRespawnTime(8))
        assertEquals(Long.MAX_VALUE, config.getRespawnTime(15))
    }

    @Test
    fun `disabled config can be created`() {
        val config = RespawnConfig(enabled = false)
        assertFalse(config.enabled)
    }

    @Test
    fun `config with empty scaling returns no respawn for all difficulties`() {
        val config = RespawnConfig(difficultyScaling = emptyMap())

        assertEquals(Long.MAX_VALUE, config.getRespawnTime(1))
        assertEquals(Long.MAX_VALUE, config.getRespawnTime(50))
        assertEquals(Long.MAX_VALUE, config.getRespawnTime(100))
    }
}
