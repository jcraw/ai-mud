package com.jcraw.mud.reasoning.procedural

import com.jcraw.mud.core.ComponentType
import com.jcraw.mud.core.SocialComponent
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import kotlin.random.Random

/**
 * Tests for NPCGenerator with SocialComponent generation
 */
class NPCGeneratorTest {

    @Nested
    inner class HostileNPCGeneration {

        @Test
        fun `hostile NPC has SocialComponent attached`() {
            val generator = NPCGenerator(DungeonTheme.CRYPT, Random(42))
            val npc = generator.generateHostileNPC("test_hostile", powerLevel = 1)

            assertTrue(npc.hasComponent(ComponentType.SOCIAL), "Hostile NPC should have SocialComponent")
        }

        @Test
        fun `hostile NPC has negative disposition`() {
            val generator = NPCGenerator(DungeonTheme.CASTLE, Random(42))
            val npc = generator.generateHostileNPC("test_hostile", powerLevel = 2)

            val social = npc.getComponent<SocialComponent>(ComponentType.SOCIAL)
            assertNotNull(social)
            assertTrue(social!!.disposition < 0, "Hostile NPC disposition should be negative")
            assertTrue(social.disposition >= -75, "Hostile NPC disposition should be >= -75")
        }

        @Test
        fun `hostile NPC has personality matching dungeon theme`() {
            val generator = NPCGenerator(DungeonTheme.CRYPT, Random(42))
            val npc = generator.generateHostileNPC("test_hostile", powerLevel = 1)

            val social = npc.getComponent<SocialComponent>(ComponentType.SOCIAL)
            assertNotNull(social)
            assertFalse(social!!.personality.isBlank(), "Hostile NPC should have personality")
        }

        @Test
        fun `hostile NPC has 1 to 3 traits`() {
            val generator = NPCGenerator(DungeonTheme.CAVE, Random(42))
            val npc = generator.generateHostileNPC("test_hostile", powerLevel = 1)

            val social = npc.getComponent<SocialComponent>(ComponentType.SOCIAL)
            assertNotNull(social)
            assertTrue(social!!.traits.isNotEmpty(), "Hostile NPC should have traits")
            assertTrue(social.traits.size <= 3, "Hostile NPC should have at most 3 traits")
        }

        @Test
        fun `hostile NPCs with same seed generate consistently`() {
            val generator1 = NPCGenerator(DungeonTheme.TEMPLE, Random(999))
            val npc1 = generator1.generateHostileNPC("test_hostile", powerLevel = 2)

            val generator2 = NPCGenerator(DungeonTheme.TEMPLE, Random(999))
            val npc2 = generator2.generateHostileNPC("test_hostile", powerLevel = 2)

            val social1 = npc1.getComponent<SocialComponent>(ComponentType.SOCIAL)
            val social2 = npc2.getComponent<SocialComponent>(ComponentType.SOCIAL)

            assertEquals(social1!!.disposition, social2!!.disposition, "Same seed should generate same disposition")
            assertEquals(social1.personality, social2.personality, "Same seed should generate same personality")
            assertEquals(social1.traits, social2.traits, "Same seed should generate same traits")
        }
    }

    @Nested
    inner class FriendlyNPCGeneration {

        @Test
        fun `friendly NPC has SocialComponent attached`() {
            val generator = NPCGenerator(DungeonTheme.CASTLE, Random(42))
            val npc = generator.generateFriendlyNPC("test_friendly", powerLevel = 1)

            assertTrue(npc.hasComponent(ComponentType.SOCIAL), "Friendly NPC should have SocialComponent")
        }

        @Test
        fun `friendly NPC has positive disposition`() {
            val generator = NPCGenerator(DungeonTheme.CAVE, Random(42))
            val npc = generator.generateFriendlyNPC("test_friendly", powerLevel = 1)

            val social = npc.getComponent<SocialComponent>(ComponentType.SOCIAL)
            assertNotNull(social)
            assertTrue(social!!.disposition >= 25, "Friendly NPC disposition should be >= 25")
            assertTrue(social.disposition < 100, "Friendly NPC disposition should be < 100")
        }

        @Test
        fun `friendly NPC has personality matching dungeon theme`() {
            val generator = NPCGenerator(DungeonTheme.TEMPLE, Random(42))
            val npc = generator.generateFriendlyNPC("test_friendly", powerLevel = 1)

            val social = npc.getComponent<SocialComponent>(ComponentType.SOCIAL)
            assertNotNull(social)
            assertFalse(social!!.personality.isBlank(), "Friendly NPC should have personality")
        }

        @Test
        fun `friendly NPC has 1 to 3 traits`() {
            val generator = NPCGenerator(DungeonTheme.CRYPT, Random(42))
            val npc = generator.generateFriendlyNPC("test_friendly", powerLevel = 1)

            val social = npc.getComponent<SocialComponent>(ComponentType.SOCIAL)
            assertNotNull(social)
            assertTrue(social!!.traits.isNotEmpty(), "Friendly NPC should have traits")
            assertTrue(social.traits.size <= 3, "Friendly NPC should have at most 3 traits")
        }

        @Test
        fun `friendly NPCs with same seed generate consistently`() {
            val generator1 = NPCGenerator(DungeonTheme.CASTLE, Random(777))
            val npc1 = generator1.generateFriendlyNPC("test_friendly", powerLevel = 1)

            val generator2 = NPCGenerator(DungeonTheme.CASTLE, Random(777))
            val npc2 = generator2.generateFriendlyNPC("test_friendly", powerLevel = 1)

            val social1 = npc1.getComponent<SocialComponent>(ComponentType.SOCIAL)
            val social2 = npc2.getComponent<SocialComponent>(ComponentType.SOCIAL)

            assertEquals(social1!!.disposition, social2!!.disposition, "Same seed should generate same disposition")
            assertEquals(social1.personality, social2.personality, "Same seed should generate same personality")
            assertEquals(social1.traits, social2.traits, "Same seed should generate same traits")
        }
    }

    @Nested
    inner class BossNPCGeneration {

        @Test
        fun `boss NPC has SocialComponent attached`() {
            val generator = NPCGenerator(DungeonTheme.CRYPT, Random(42))
            val npc = generator.generateBoss("test_boss")

            assertTrue(npc.hasComponent(ComponentType.SOCIAL), "Boss NPC should have SocialComponent")
        }

        @Test
        fun `boss NPC has very hostile disposition`() {
            val generator = NPCGenerator(DungeonTheme.CASTLE, Random(42))
            val npc = generator.generateBoss("test_boss")

            val social = npc.getComponent<SocialComponent>(ComponentType.SOCIAL)
            assertNotNull(social)
            assertTrue(social!!.disposition <= -75, "Boss disposition should be <= -75 (HOSTILE tier)")
        }

        @Test
        fun `boss NPC has personality matching dungeon theme`() {
            val generator = NPCGenerator(DungeonTheme.TEMPLE, Random(42))
            val npc = generator.generateBoss("test_boss")

            val social = npc.getComponent<SocialComponent>(ComponentType.SOCIAL)
            assertNotNull(social)
            assertFalse(social!!.personality.isBlank(), "Boss NPC should have personality")
        }

        @Test
        fun `boss NPC has 2 to 4 traits`() {
            val generator = NPCGenerator(DungeonTheme.CAVE, Random(42))
            val npc = generator.generateBoss("test_boss")

            val social = npc.getComponent<SocialComponent>(ComponentType.SOCIAL)
            assertNotNull(social)
            assertTrue(social!!.traits.size >= 2, "Boss should have at least 2 traits")
            assertTrue(social.traits.size <= 4, "Boss should have at most 4 traits")
        }

        @Test
        fun `boss NPCs with same seed generate consistently`() {
            val generator1 = NPCGenerator(DungeonTheme.CRYPT, Random(555))
            val npc1 = generator1.generateBoss("test_boss")

            val generator2 = NPCGenerator(DungeonTheme.CRYPT, Random(555))
            val npc2 = generator2.generateBoss("test_boss")

            val social1 = npc1.getComponent<SocialComponent>(ComponentType.SOCIAL)
            val social2 = npc2.getComponent<SocialComponent>(ComponentType.SOCIAL)

            assertEquals(social1!!.disposition, social2!!.disposition, "Same seed should generate same disposition")
            assertEquals(social1.personality, social2.personality, "Same seed should generate same personality")
            assertEquals(social1.traits, social2.traits, "Same seed should generate same traits")
        }
    }

    @Nested
    inner class ThemeVariety {

        @Test
        fun `each dungeon theme generates different personalities`() {
            val themes = listOf(DungeonTheme.CRYPT, DungeonTheme.CASTLE, DungeonTheme.CAVE, DungeonTheme.TEMPLE)
            val personalities = mutableSetOf<String>()

            themes.forEach { theme ->
                val generator = NPCGenerator(theme, Random(42))
                val npc = generator.generateHostileNPC("test_${theme.name}", powerLevel = 1)
                val social = npc.getComponent<SocialComponent>(ComponentType.SOCIAL)
                personalities.add(social!!.personality)
            }

            // With 4 themes and deterministic seed, we should get variety
            assertTrue(personalities.size > 1, "Different themes should generate different personalities")
        }

        @Test
        fun `hostile and friendly NPCs have different disposition ranges`() {
            val generator = NPCGenerator(DungeonTheme.CASTLE, Random(42))
            val hostile = generator.generateHostileNPC("hostile", powerLevel = 1)
            val friendly = generator.generateFriendlyNPC("friendly", powerLevel = 1)

            val hostileSocial = hostile.getComponent<SocialComponent>(ComponentType.SOCIAL)
            val friendlySocial = friendly.getComponent<SocialComponent>(ComponentType.SOCIAL)

            assertTrue(
                hostileSocial!!.disposition < friendlySocial!!.disposition,
                "Hostile NPC should have lower disposition than friendly NPC"
            )
        }
    }

    @Nested
    inner class RandomRoomNPCGeneration {

        @Test
        fun `room NPC can be null, hostile, or friendly`() {
            val generator = NPCGenerator(DungeonTheme.CRYPT, Random(42))
            val results = mutableSetOf<String>()

            // Generate 100 NPCs to ensure we hit all cases
            repeat(100) { i ->
                val npc = generator.generateRoomNPC("room_$i", powerLevel = 1)
                results.add(
                    when {
                        npc == null -> "null"
                        npc.isHostile -> "hostile"
                        else -> "friendly"
                    }
                )
            }

            // Should have variety (not all the same type)
            assertTrue(results.size > 1, "Room generation should produce variety")
        }

        @Test
        fun `room NPCs have SocialComponent when present`() {
            val generator = NPCGenerator(DungeonTheme.CASTLE, Random(42))

            // Generate NPCs until we get a non-null one
            val npc = generateSequence(0) { it + 1 }
                .map { generator.generateRoomNPC("room_$it", powerLevel = 1) }
                .filterNotNull()
                .first()

            assertTrue(npc.hasComponent(ComponentType.SOCIAL), "Room NPC should have SocialComponent")
        }
    }
}
