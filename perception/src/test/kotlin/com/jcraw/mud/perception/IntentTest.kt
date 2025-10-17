package com.jcraw.mud.perception

import com.jcraw.mud.core.Direction
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for Intent sealed class hierarchy.
 * Focus: serialization contracts and type hierarchy behavior.
 */
class IntentTest {

    @Test
    fun `Move intent contains direction`() {
        val intent = Intent.Move(Direction.NORTH)
        assertEquals(Direction.NORTH, intent.direction)
    }

    @Test
    fun `Look intent can have null target for room description`() {
        val roomLook = Intent.Look(null)
        assertEquals(null, roomLook.target)
    }

    @Test
    fun `Look intent can target specific entity`() {
        val targetLook = Intent.Look("ancient_statue")
        assertEquals("ancient_statue", targetLook.target)
    }

    @Test
    fun `Interact intent requires target`() {
        val interact = Intent.Interact("lever")
        assertEquals("lever", interact.target)
    }

    @Test
    fun `Take intent requires target`() {
        val take = Intent.Take("sword")
        assertEquals("sword", take.target)
    }

    @Test
    fun `Drop intent requires target`() {
        val drop = Intent.Drop("pouch")
        assertEquals("pouch", drop.target)
    }

    @Test
    fun `Talk intent requires target`() {
        val talk = Intent.Talk("skeleton")
        assertEquals("skeleton", talk.target)
    }

    @Test
    fun `Emote intent can have null target for general emotes`() {
        val emote = Intent.Emote("smile", null)
        assertEquals("smile", emote.emoteType)
        assertEquals(null, emote.target)
    }

    @Test
    fun `Emote intent can target specific NPC`() {
        val emote = Intent.Emote("wave", "guard")
        assertEquals("wave", emote.emoteType)
        assertEquals("guard", emote.target)
    }

    @Test
    fun `AskQuestion intent requires NPC and topic`() {
        val ask = Intent.AskQuestion("merchant", "wares")
        assertEquals("merchant", ask.npcTarget)
        assertEquals("wares", ask.topic)
    }

    @Test
    fun `Invalid intent contains error message`() {
        val invalid = Intent.Invalid("Unknown command: foobar")
        assertTrue(invalid.message.contains("foobar"))
    }

    @Test
    fun `Intent hierarchy is sealed - all types are Intent`() {
        val intents: List<Intent> = listOf(
            Intent.Move(Direction.SOUTH),
            Intent.Look(),
            Intent.Interact("door"),
            Intent.Inventory,
            Intent.Take("item"),
            Intent.Drop("item"),
            Intent.Talk("npc"),
            Intent.Emote("smile", null),
            Intent.AskQuestion("guard", "castle"),
            Intent.Help,
            Intent.Quit,
            Intent.Invalid("test")
        )

        assertTrue(intents.all { it is Intent })
    }

    @Test
    fun `Move intent serializes correctly`() {
        val intent = Intent.Move(Direction.EAST)
        val json = Json.encodeToString(intent)
        assertTrue(json.contains("EAST"))
    }

    @Test
    fun `Look intent with target serializes correctly`() {
        val intent = Intent.Look("chest")
        val json = Json.encodeToString(intent)
        assertTrue(json.contains("chest"))
    }

    @Test
    fun `Take intent serializes correctly`() {
        val intent = Intent.Take("gold_pouch")
        val json = Json.encodeToString(intent)
        assertTrue(json.contains("gold_pouch"))
    }

    @Test
    fun `Drop intent serializes correctly`() {
        val intent = Intent.Drop("iron_sword")
        val json = Json.encodeToString(intent)
        assertTrue(json.contains("iron_sword"))
    }

    @Test
    fun `Talk intent serializes correctly`() {
        val intent = Intent.Talk("skeleton_king")
        val json = Json.encodeToString(intent)
        assertTrue(json.contains("skeleton_king"))
    }

    @Test
    fun `Emote intent with target serializes correctly`() {
        val intent = Intent.Emote("wave", "guard")
        val json = Json.encodeToString(intent)
        assertTrue(json.contains("wave"))
        assertTrue(json.contains("guard"))
    }

    @Test
    fun `Emote intent without target serializes correctly`() {
        val intent = Intent.Emote("smile", null)
        val json = Json.encodeToString(intent)
        assertTrue(json.contains("smile"))
    }

    @Test
    fun `AskQuestion intent serializes correctly`() {
        val intent = Intent.AskQuestion("merchant", "wares")
        val json = Json.encodeToString(intent)
        assertTrue(json.contains("merchant"))
        assertTrue(json.contains("wares"))
    }

    @Test
    fun `Singleton intents are distinct objects`() {
        val help1 = Intent.Help
        val help2 = Intent.Help
        assertTrue(help1 === help2) // Same object reference

        val inventory1 = Intent.Inventory
        val inventory2 = Intent.Inventory
        assertTrue(inventory1 === inventory2)
    }

    @Test
    fun `Intent equality works as expected`() {
        assertEquals(Intent.Move(Direction.NORTH), Intent.Move(Direction.NORTH))
        assertEquals(Intent.Look("target"), Intent.Look("target"))
        assertEquals(Intent.Take("item"), Intent.Take("item"))
        assertEquals(Intent.Drop("item"), Intent.Drop("item"))
        assertEquals(Intent.Talk("npc"), Intent.Talk("npc"))
        assertEquals(Intent.Emote("smile", null), Intent.Emote("smile", null))
        assertEquals(Intent.Emote("wave", "guard"), Intent.Emote("wave", "guard"))
        assertEquals(Intent.AskQuestion("merchant", "wares"), Intent.AskQuestion("merchant", "wares"))
        assertEquals(Intent.Help, Intent.Help)
    }
}
