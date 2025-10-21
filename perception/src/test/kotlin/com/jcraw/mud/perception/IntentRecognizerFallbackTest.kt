package com.jcraw.mud.perception

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class IntentRecognizerFallbackTest {

    private val recognizer = IntentRecognizer(llmClient = null)

    @Test
    fun `say command without target produces Say intent`() = runBlocking {
        val intent = recognizer.parseIntent("say whats inside?")

        assertTrue(intent is Intent.Say)
        intent as Intent.Say
        assertEquals("whats inside?", intent.message)
        assertEquals(null, intent.npcTarget)
    }

    @Test
    fun `say command with explicit target extracts npc`() = runBlocking {
        val intent = recognizer.parseIntent("say to the guard what's beyond the gate?")

        assertTrue(intent is Intent.Say)
        intent as Intent.Say
        assertEquals("what's beyond the gate?", intent.message)
        assertEquals("guard", intent.npcTarget)
    }
}
