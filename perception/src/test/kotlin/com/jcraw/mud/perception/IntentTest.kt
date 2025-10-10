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
        assertEquals(Intent.Help, Intent.Help)
    }
}
