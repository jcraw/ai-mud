package com.jcraw.mud.perception

import com.jcraw.mud.core.Direction
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Test direction parsing for all valid direction inputs.
 * This test specifically validates the bug where diagonal directions
 * like "northwest" fail to parse correctly.
 */
class DirectionParsingTest {

    @Test
    fun `Direction fromString should parse all valid directions`() {
        // Cardinal directions
        assertEquals(Direction.NORTH, Direction.fromString("north"))
        assertEquals(Direction.SOUTH, Direction.fromString("south"))
        assertEquals(Direction.EAST, Direction.fromString("east"))
        assertEquals(Direction.WEST, Direction.fromString("west"))

        // Vertical directions
        assertEquals(Direction.UP, Direction.fromString("up"))
        assertEquals(Direction.DOWN, Direction.fromString("down"))

        // Diagonal directions - THIS IS WHERE THE BUG OCCURS
        assertEquals(Direction.NORTHEAST, Direction.fromString("northeast"))
        assertEquals(Direction.NORTHWEST, Direction.fromString("northwest"))
        assertEquals(Direction.SOUTHEAST, Direction.fromString("southeast"))
        assertEquals(Direction.SOUTHWEST, Direction.fromString("southwest"))

        // Case insensitive
        assertEquals(Direction.NORTHWEST, Direction.fromString("NorthWest"))
        assertEquals(Direction.NORTHWEST, Direction.fromString("NORTHWEST"))
    }

    @Test
    fun `Fallback parser should handle standalone diagonal directions`() = runBlocking {
        val recognizer = IntentRecognizer(llmClient = null)  // Force fallback mode

        // Test all diagonal directions as standalone commands
        val nwIntent = recognizer.parseIntent("northwest")
        assertIs<Intent.Move>(nwIntent)
        assertEquals(Direction.NORTHWEST, (nwIntent as Intent.Move).direction)

        val neIntent = recognizer.parseIntent("northeast")
        assertIs<Intent.Move>(neIntent)
        assertEquals(Direction.NORTHEAST, (neIntent as Intent.Move).direction)

        val seIntent = recognizer.parseIntent("southeast")
        assertIs<Intent.Move>(seIntent)
        assertEquals(Direction.SOUTHEAST, (seIntent as Intent.Move).direction)

        val swIntent = recognizer.parseIntent("southwest")
        assertIs<Intent.Move>(swIntent)
        assertEquals(Direction.SOUTHWEST, (swIntent as Intent.Move).direction)
    }

    @Test
    fun `Fallback parser should handle 'go' with diagonal directions`() = runBlocking {
        val recognizer = IntentRecognizer(llmClient = null)  // Force fallback mode

        val nwIntent = recognizer.parseIntent("go northwest")
        assertIs<Intent.Move>(nwIntent)
        assertEquals(Direction.NORTHWEST, (nwIntent as Intent.Move).direction)

        val neIntent = recognizer.parseIntent("go northeast")
        assertIs<Intent.Move>(neIntent)
        assertEquals(Direction.NORTHEAST, (neIntent as Intent.Move).direction)
    }

    @Test
    fun `Fallback parser should handle diagonal abbreviations`() = runBlocking {
        val recognizer = IntentRecognizer(llmClient = null)  // Force fallback mode

        val nwIntent = recognizer.parseIntent("nw")
        assertIs<Intent.Move>(nwIntent)
        assertEquals(Direction.NORTHWEST, (nwIntent as Intent.Move).direction)

        val neIntent = recognizer.parseIntent("ne")
        assertIs<Intent.Move>(neIntent)
        assertEquals(Direction.NORTHEAST, (neIntent as Intent.Move).direction)

        val seIntent = recognizer.parseIntent("se")
        assertIs<Intent.Move>(seIntent)
        assertEquals(Direction.SOUTHEAST, (seIntent as Intent.Move).direction)

        val swIntent = recognizer.parseIntent("sw")
        assertIs<Intent.Move>(swIntent)
        assertEquals(Direction.SOUTHWEST, (swIntent as Intent.Move).direction)
    }

    @Test
    fun `Fallback parser should handle cardinal directions`() = runBlocking {
        val recognizer = IntentRecognizer(llmClient = null)  // Force fallback mode

        // Standalone
        val northIntent = recognizer.parseIntent("north")
        assertIs<Intent.Move>(northIntent)
        assertEquals(Direction.NORTH, (northIntent as Intent.Move).direction)

        // With 'go'
        val goNorthIntent = recognizer.parseIntent("go north")
        assertIs<Intent.Move>(goNorthIntent)
        assertEquals(Direction.NORTH, (goNorthIntent as Intent.Move).direction)

        // Abbreviation
        val nIntent = recognizer.parseIntent("n")
        assertIs<Intent.Move>(nIntent)
        assertEquals(Direction.NORTH, (nIntent as Intent.Move).direction)
    }
}
