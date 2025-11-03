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

    @Test
    fun `buy command with quantity and merchant parsed as trade`() = runBlocking {
        val intent = recognizer.parseIntent("buy 2 health potions from alara")

        assertTrue(intent is Intent.Trade)
        intent as Intent.Trade
        assertEquals("buy", intent.action)
        assertEquals("health potions", intent.target)
        assertEquals(2, intent.quantity)
        assertEquals("alara", intent.merchantTarget)
    }

    @Test
    fun `sell command with merchant parsed as trade`() = runBlocking {
        val intent = recognizer.parseIntent("sell rusty sword to the smith")

        assertTrue(intent is Intent.Trade)
        intent as Intent.Trade
        assertEquals("sell", intent.action)
        assertEquals("rusty sword", intent.target)
        assertEquals(1, intent.quantity)
        assertEquals("smith", intent.merchantTarget)
    }

    @Test
    fun `list stock command without merchant parsed as trade`() = runBlocking {
        val intent = recognizer.parseIntent("list stock")

        assertTrue(intent is Intent.Trade)
        intent as Intent.Trade
        assertEquals("list", intent.action)
        assertEquals("stock", intent.target)
        assertEquals(1, intent.quantity)
        assertEquals(null, intent.merchantTarget)
    }

    @Test
    fun `list stock with merchant parsed as trade`() = runBlocking {
        val intent = recognizer.parseIntent("list stock from alara")

        assertTrue(intent is Intent.Trade)
        intent as Intent.Trade
        assertEquals("list", intent.action)
        assertEquals("stock", intent.target)
        assertEquals(1, intent.quantity)
        assertEquals("alara", intent.merchantTarget)
    }
}
