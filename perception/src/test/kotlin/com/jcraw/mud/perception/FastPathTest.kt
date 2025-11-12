package com.jcraw.mud.perception

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import com.jcraw.mud.core.Direction
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FastPathTest {
    @Test
    fun `test cardinal direction fast path without LLM`() = runBlocking {
        val recognizer = IntentRecognizer(llmClient = null)
        
        // Test pure directions
        val northIntent = recognizer.parseIntent("north")
        assertTrue(northIntent is Intent.Move)
        assertEquals(Direction.NORTH, (northIntent as Intent.Move).direction)
        
        // Test "go direction"
        val goNorthIntent = recognizer.parseIntent("go north")
        assertTrue(goNorthIntent is Intent.Move)
        assertEquals(Direction.NORTH, (goNorthIntent as Intent.Move).direction)
        
        // Test abbreviations
        val nIntent = recognizer.parseIntent("n")
        assertTrue(nIntent is Intent.Move)
        assertEquals(Direction.NORTH, (nIntent as Intent.Move).direction)
        
        println("✅ All fast path tests passed - no LLM calls were made!")
    }
}
