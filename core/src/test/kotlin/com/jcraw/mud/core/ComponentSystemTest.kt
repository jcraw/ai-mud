package com.jcraw.mud.core

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the component system architecture
 */
class ComponentSystemTest {

    @Nested
    inner class ComponentAttachment {

        @Test
        fun `NPC can have social component attached`() {
            val npc = Entity.NPC(
                id = "guard-1",
                name = "Guard",
                description = "A town guard"
            )

            val social = SocialComponent(
                personality = "gruff",
                traits = listOf("loyal")
            )

            val updated = npc.withComponent(social)

            assertTrue(updated.hasComponent(ComponentType.SOCIAL))
            assertNotNull(updated.getSocialComponent())
            assertEquals("gruff", updated.getSocialComponent()?.personality)
        }

        @Test
        fun `withComponent returns new NPC instance`() {
            val npc = Entity.NPC(
                id = "guard-1",
                name = "Guard",
                description = "A town guard"
            )

            val social = SocialComponent(
                personality = "gruff",
                traits = listOf("loyal")
            )

            val updated = npc.withComponent(social)

            // Original NPC should not have component
            assertFalse(npc.hasComponent(ComponentType.SOCIAL))
            // Updated NPC should have component
            assertTrue(updated.hasComponent(ComponentType.SOCIAL))
        }

        @Test
        fun `component can be removed`() {
            val npc = Entity.NPC(
                id = "guard-1",
                name = "Guard",
                description = "A town guard"
            ).withComponent(
                SocialComponent(
                    personality = "gruff",
                    traits = emptyList()
                )
            )

            val updated = npc.withoutComponent(ComponentType.SOCIAL)

            assertFalse(updated.hasComponent(ComponentType.SOCIAL))
            assertNull(updated.getSocialComponent())
        }

        @Test
        fun `component can be replaced`() {
            val npc = Entity.NPC(
                id = "guard-1",
                name = "Guard",
                description = "A town guard"
            ).withComponent(
                SocialComponent(
                    disposition = 0,
                    personality = "gruff",
                    traits = emptyList()
                )
            )

            val updated = npc.withComponent(
                SocialComponent(
                    disposition = 50,
                    personality = "friendly",
                    traits = listOf("helpful")
                )
            )

            assertEquals(50, updated.getSocialComponent()?.disposition)
            assertEquals("friendly", updated.getSocialComponent()?.personality)
        }

        @Test
        fun `getComponent returns null if component not present`() {
            val npc = Entity.NPC(
                id = "guard-1",
                name = "Guard",
                description = "A town guard"
            )

            assertNull(npc.getSocialComponent())
            assertFalse(npc.hasComponent(ComponentType.SOCIAL))
        }
    }

    @Nested
    inner class SocialComponentBehavior {

        @Test
        fun `disposition tiers are calculated correctly`() {
            assertEquals(DispositionTier.HOSTILE, SocialComponent(disposition = -100, personality = "test").getDispositionTier())
            assertEquals(DispositionTier.HOSTILE, SocialComponent(disposition = -80, personality = "test").getDispositionTier())
            assertEquals(DispositionTier.UNFRIENDLY, SocialComponent(disposition = -50, personality = "test").getDispositionTier())
            assertEquals(DispositionTier.NEUTRAL, SocialComponent(disposition = 0, personality = "test").getDispositionTier())
            assertEquals(DispositionTier.FRIENDLY, SocialComponent(disposition = 50, personality = "test").getDispositionTier())
            assertEquals(DispositionTier.ALLIED, SocialComponent(disposition = 100, personality = "test").getDispositionTier())
        }

        @Test
        fun `disposition changes are clamped to valid range`() {
            val social = SocialComponent(
                disposition = 90,
                personality = "friendly"
            )

            val increased = social.applyDispositionChange(50)
            assertEquals(100, increased.disposition)

            val decreased = social.applyDispositionChange(-200)
            assertEquals(-100, decreased.disposition)
        }

        @Test
        fun `conversation count increments correctly`() {
            val social = SocialComponent(
                disposition = 0,
                personality = "friendly",
                conversationCount = 5
            )

            val updated = social.incrementConversationCount()

            assertEquals(6, updated.conversationCount)
            assertTrue(updated.lastInteractionTime > 0)
        }

        @Test
        fun `knowledge entries can be added`() {
            val social = SocialComponent(
                disposition = 0,
                personality = "scholar",
                knowledgeEntries = listOf("knowledge-1")
            )

            val updated = social.addKnowledge("knowledge-2")

            assertEquals(2, updated.knowledgeEntries.size)
            assertTrue(updated.knowledgeEntries.contains("knowledge-1"))
            assertTrue(updated.knowledgeEntries.contains("knowledge-2"))
        }
    }

    @Nested
    inner class SocialEventApplication {

        @Test
        fun `applySocialEvent creates social component if missing`() {
            val npc = Entity.NPC(
                id = "guard-1",
                name = "Guard",
                description = "A town guard"
            )

            val event = SocialEvent.HelpProvided()
            val updated = npc.applySocialEvent(event)

            assertNotNull(updated.getSocialComponent())
            assertEquals(20, updated.getDisposition())
        }

        @Test
        fun `applySocialEvent updates existing disposition`() {
            val npc = Entity.NPC(
                id = "guard-1",
                name = "Guard",
                description = "A town guard"
            ).withComponent(
                SocialComponent(
                    disposition = 10,
                    personality = "gruff",
                    traits = emptyList()
                )
            )

            val event = SocialEvent.HelpProvided(dispositionDelta = 15)
            val updated = npc.applySocialEvent(event)

            assertEquals(25, updated.getDisposition())
        }

        @Test
        fun `negative social events decrease disposition`() {
            val npc = Entity.NPC(
                id = "guard-1",
                name = "Guard",
                description = "A town guard"
            ).withComponent(
                SocialComponent(
                    disposition = 50,
                    personality = "friendly",
                    traits = emptyList()
                )
            )

            val event = SocialEvent.Threatened()
            val updated = npc.applySocialEvent(event)

            assertEquals(35, updated.getDisposition())
        }

        @Test
        fun `getDisposition returns 0 if no social component`() {
            val npc = Entity.NPC(
                id = "guard-1",
                name = "Guard",
                description = "A town guard"
            )

            assertEquals(0, npc.getDisposition())
        }
    }

    @Nested
    inner class SocialEventTypes {

        @Test
        fun `HelpProvided event has positive disposition delta`() {
            val event = SocialEvent.HelpProvided()
            assertEquals(20, event.dispositionDelta)
            assertTrue(event.dispositionDelta > 0)
        }

        @Test
        fun `Threatened event has negative disposition delta`() {
            val event = SocialEvent.Threatened()
            assertEquals(-15, event.dispositionDelta)
            assertTrue(event.dispositionDelta < 0)
        }

        @Test
        fun `successful Persuaded increases disposition`() {
            val event = SocialEvent.Persuaded(success = true)
            assertEquals(15, event.dispositionDelta)
        }

        @Test
        fun `failed Persuaded slightly decreases disposition`() {
            val event = SocialEvent.Persuaded(success = false)
            assertEquals(-2, event.dispositionDelta)
        }

        @Test
        fun `GiftGiven scales with item value`() {
            val cheapGift = SocialEvent.GiftGiven(itemValue = 10)
            val expensiveGift = SocialEvent.GiftGiven(itemValue = 500)

            assertTrue(cheapGift.dispositionDelta < expensiveGift.dispositionDelta)
            assertEquals(5, cheapGift.dispositionDelta) // min clamped
            assertEquals(30, expensiveGift.dispositionDelta) // max clamped
        }

        @Test
        fun `AttackAttempted sets disposition to hostile`() {
            val event = SocialEvent.AttackAttempted()
            assertEquals(-100, event.dispositionDelta)
        }

        @Test
        fun `ConversationHeld provides small positive delta`() {
            val event = SocialEvent.ConversationHeld()
            assertEquals(1, event.dispositionDelta)
        }
    }

    @Nested
    inner class EmoteSystem {

        @Test
        fun `EmoteType can be found by keyword`() {
            assertEquals(EmoteType.BOW, EmoteType.fromKeyword("bow"))
            assertEquals(EmoteType.WAVE, EmoteType.fromKeyword("wave"))
            assertEquals(EmoteType.LAUGH, EmoteType.fromKeyword("laugh"))
            assertNull(EmoteType.fromKeyword("nonexistent"))
        }

        @Test
        fun `EmoteType keyword matching is case insensitive`() {
            assertEquals(EmoteType.BOW, EmoteType.fromKeyword("BOW"))
            assertEquals(EmoteType.WAVE, EmoteType.fromKeyword("WaVe"))
        }

        @Test
        fun `positive emotes have positive disposition delta`() {
            assertTrue(EmoteType.BOW.dispositionDelta > 0)
            assertTrue(EmoteType.WAVE.dispositionDelta > 0)
            assertTrue(EmoteType.LAUGH.dispositionDelta > 0)
        }

        @Test
        fun `negative emotes have negative disposition delta`() {
            assertTrue(EmoteType.INSULT.dispositionDelta < 0)
            assertTrue(EmoteType.THREATEN.dispositionDelta < 0)
        }
    }

    @Nested
    inner class NPCDispositionHelpers {

        @Test
        fun `isHostileByDisposition returns true for hostile tier`() {
            val npc = Entity.NPC(
                id = "guard-1",
                name = "Guard",
                description = "A town guard",
                isHostile = false
            ).withComponent(
                SocialComponent(
                    disposition = -90,
                    personality = "hostile",
                    traits = emptyList()
                )
            )

            assertTrue(npc.isHostileByDisposition())
        }

        @Test
        fun `isHostileByDisposition respects legacy isHostile flag`() {
            val npc = Entity.NPC(
                id = "guard-1",
                name = "Guard",
                description = "A town guard",
                isHostile = true
            ).withComponent(
                SocialComponent(
                    disposition = 50,
                    personality = "friendly",
                    traits = emptyList()
                )
            )

            assertTrue(npc.isHostileByDisposition())
        }

        @Test
        fun `isHostileByDisposition returns false for friendly NPCs`() {
            val npc = Entity.NPC(
                id = "guard-1",
                name = "Guard",
                description = "A town guard",
                isHostile = false
            ).withComponent(
                SocialComponent(
                    disposition = 50,
                    personality = "friendly",
                    traits = emptyList()
                )
            )

            assertFalse(npc.isHostileByDisposition())
        }
    }

    @Nested
    inner class KnowledgeEntryBehavior {

        @Test
        fun `KnowledgeEntry stores NPC knowledge`() {
            val entry = KnowledgeEntry(
                id = "knowledge-1",
                entityId = "npc-1",
                content = "The ancient ruins lie to the north",
                isCanon = true,
                source = KnowledgeSource.PREDEFINED
            )

            assertEquals("knowledge-1", entry.id)
            assertEquals("npc-1", entry.entityId)
            assertEquals("The ancient ruins lie to the north", entry.content)
            assertTrue(entry.isCanon)
            assertEquals(KnowledgeSource.PREDEFINED, entry.source)
        }

        @Test
        fun `KnowledgeEntry can have tags for categorization`() {
            val entry = KnowledgeEntry(
                id = "knowledge-1",
                entityId = "npc-1",
                content = "The ancient ruins lie to the north",
                isCanon = true,
                source = KnowledgeSource.PREDEFINED,
                tags = mapOf("topic" to "geography", "relevance" to "high")
            )

            assertEquals("geography", entry.tags["topic"])
            assertEquals("high", entry.tags["relevance"])
        }

        @Test
        fun `KnowledgeSource distinguishes knowledge origin`() {
            val predefined = KnowledgeEntry(
                id = "k1",
                entityId = "npc-1",
                content = "Test",
                isCanon = true,
                source = KnowledgeSource.PREDEFINED
            )

            val generated = KnowledgeEntry(
                id = "k2",
                entityId = "npc-1",
                content = "Test",
                isCanon = true,
                source = KnowledgeSource.GENERATED
            )

            val playerTaught = KnowledgeEntry(
                id = "k3",
                entityId = "npc-1",
                content = "Test",
                isCanon = false,
                source = KnowledgeSource.PLAYER_TAUGHT
            )

            assertEquals(KnowledgeSource.PREDEFINED, predefined.source)
            assertEquals(KnowledgeSource.GENERATED, generated.source)
            assertEquals(KnowledgeSource.PLAYER_TAUGHT, playerTaught.source)
        }
    }
}
