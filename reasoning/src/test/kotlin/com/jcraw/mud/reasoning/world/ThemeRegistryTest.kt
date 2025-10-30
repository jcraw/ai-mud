package com.jcraw.mud.reasoning.world

import kotlin.test.*

/**
 * Tests for ThemeRegistry theme profile matching.
 * Validates exact matching, semantic matching, and edge cases.
 */
class ThemeRegistryTest {

    @Test
    fun `getProfile returns profile for exact match`() {
        val profile = ThemeRegistry.getProfile("dark forest")
        assertNotNull(profile)
        assertEquals("damp, shadowy, overgrown", profile.ambiance)
        assertTrue(profile.traps.contains("bear trap"))
        assertTrue(profile.resources.contains("wood"))
        assertTrue(profile.mobArchetypes.contains("wolf"))
    }

    @Test
    fun `getProfile is case insensitive`() {
        val lower = ThemeRegistry.getProfile("magma cave")
        val upper = ThemeRegistry.getProfile("MAGMA CAVE")
        val mixed = ThemeRegistry.getProfile("Magma Cave")

        assertNotNull(lower)
        assertNotNull(upper)
        assertNotNull(mixed)
        assertEquals(lower.ambiance, upper.ambiance)
        assertEquals(lower.ambiance, mixed.ambiance)
    }

    @Test
    fun `getProfile handles whitespace`() {
        val profile = ThemeRegistry.getProfile("  ancient crypt  ")
        assertNotNull(profile)
        assertEquals("cold, silent, oppressive", profile.ambiance)
    }

    @Test
    fun `getProfile returns null for non-existent theme`() {
        val profile = ThemeRegistry.getProfile("nonexistent theme")
        assertNull(profile)
    }

    @Test
    fun `getProfile returns null for empty string`() {
        val profile = ThemeRegistry.getProfile("")
        assertNull(profile)
    }

    @Test
    fun `getProfileSemantic exact match passthrough`() {
        val profile = ThemeRegistry.getProfileSemantic("frozen wasteland")
        assertNotNull(profile)
        assertEquals("freezing, desolate, windswept", profile.ambiance)
    }

    @Test
    fun `getProfileSemantic matches forest keywords`() {
        assertEquals("damp, shadowy, overgrown", ThemeRegistry.getProfileSemantic("deep forest")?.ambiance)
        assertEquals("damp, shadowy, overgrown", ThemeRegistry.getProfileSemantic("wooden grove")?.ambiance)
        assertEquals("damp, shadowy, overgrown", ThemeRegistry.getProfileSemantic("tree sanctuary")?.ambiance)
    }

    @Test
    fun `getProfileSemantic matches magma keywords`() {
        assertEquals("scorching, smoky, unstable", ThemeRegistry.getProfileSemantic("lava chamber")?.ambiance)
        assertEquals("scorching, smoky, unstable", ThemeRegistry.getProfileSemantic("volcanic cave")?.ambiance)
        assertEquals("scorching, smoky, unstable", ThemeRegistry.getProfileSemantic("fire cavern")?.ambiance)
    }

    @Test
    fun `getProfileSemantic matches crypt keywords`() {
        assertEquals("cold, silent, oppressive", ThemeRegistry.getProfileSemantic("tomb chamber")?.ambiance)
        assertEquals("cold, silent, oppressive", ThemeRegistry.getProfileSemantic("undead lair")?.ambiance)
        assertEquals("cold, silent, oppressive", ThemeRegistry.getProfileSemantic("grave site")?.ambiance)
    }

    @Test
    fun `getProfileSemantic matches frozen keywords`() {
        assertEquals("freezing, desolate, windswept", ThemeRegistry.getProfileSemantic("ice cavern")?.ambiance)
        assertEquals("freezing, desolate, windswept", ThemeRegistry.getProfileSemantic("snowy pass")?.ambiance)
        assertEquals("freezing, desolate, windswept", ThemeRegistry.getProfileSemantic("frost tundra")?.ambiance)
    }

    @Test
    fun `getProfileSemantic matches castle keywords`() {
        assertEquals("echoing, crumbling, haunted", ThemeRegistry.getProfileSemantic("old fortress")?.ambiance)
        assertEquals("echoing, crumbling, haunted", ThemeRegistry.getProfileSemantic("ancient keep")?.ambiance)
    }

    @Test
    fun `getProfileSemantic matches swamp keywords`() {
        assertEquals("murky, fetid, humid", ThemeRegistry.getProfileSemantic("bogland")?.ambiance)
        assertEquals("murky, fetid, humid", ThemeRegistry.getProfileSemantic("marshland")?.ambiance)
    }

    @Test
    fun `getProfileSemantic matches desert keywords`() {
        assertEquals("hot, arid, windblown", ThemeRegistry.getProfileSemantic("sandy ruins")?.ambiance)
        assertEquals("hot, arid, windblown", ThemeRegistry.getProfileSemantic("dune valley")?.ambiance)
    }

    @Test
    fun `getProfileSemantic matches lake keywords`() {
        assertEquals("damp, echoing, bioluminescent", ThemeRegistry.getProfileSemantic("underground lake")?.ambiance)
        assertEquals("damp, echoing, bioluminescent", ThemeRegistry.getProfileSemantic("water cavern")?.ambiance)
    }

    @Test
    fun `getProfileSemantic returns null for unmatched theme`() {
        val profile = ThemeRegistry.getProfileSemantic("space station")
        assertNull(profile)
    }

    @Test
    fun `getAllThemeNames returns all 8 themes`() {
        val themes = ThemeRegistry.getAllThemeNames()
        assertEquals(8, themes.size)
        assertTrue(themes.contains("dark forest"))
        assertTrue(themes.contains("magma cave"))
        assertTrue(themes.contains("ancient crypt"))
        assertTrue(themes.contains("frozen wasteland"))
        assertTrue(themes.contains("abandoned castle"))
        assertTrue(themes.contains("swamp"))
        assertTrue(themes.contains("desert ruins"))
        assertTrue(themes.contains("underground lake"))
    }

    @Test
    fun `getDefaultProfile returns dark forest`() {
        val profile = ThemeRegistry.getDefaultProfile()
        assertEquals("damp, shadowy, overgrown", profile.ambiance)
        assertTrue(profile.traps.contains("bear trap"))
    }

    @Test
    fun `all profiles have non-empty content`() {
        ThemeRegistry.getAllThemeNames().forEach { themeName ->
            val profile = ThemeRegistry.getProfile(themeName)
            assertNotNull(profile, "Profile for $themeName should not be null")
            assertTrue(profile.traps.isNotEmpty(), "$themeName should have traps")
            assertTrue(profile.resources.isNotEmpty(), "$themeName should have resources")
            assertTrue(profile.mobArchetypes.isNotEmpty(), "$themeName should have mobs")
            assertTrue(profile.ambiance.isNotBlank(), "$themeName should have ambiance")
        }
    }
}
