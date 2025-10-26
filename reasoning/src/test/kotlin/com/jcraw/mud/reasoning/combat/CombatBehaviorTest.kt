package com.jcraw.mud.reasoning.combat

import com.jcraw.mud.core.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

/**
 * Tests for CombatBehavior - counter-attack triggers and combat de-escalation
 */
class CombatBehaviorTest {

    @Test
    fun `triggerCounterAttack sets NPC disposition to -100`() {
        val neutralNpc = Entity.NPC(
            id = "guard1",
            name = "Guard",
            description = "A neutral guard",
            components = mapOf(
                ComponentType.SOCIAL to SocialComponent(
                    disposition = 0,
                    personality = "neutral"
                )
            )
        )

        val room = Room(
            id = "room1",
            name = "Guard Post",
            traits = listOf("secure"),
            entities = listOf(neutralNpc)
        )

        val worldState = WorldState(
            rooms = mapOf("room1" to room),
            players = mapOf("player1" to PlayerState(
                id = "player1",
                name = "Hero",
                currentRoomId = "room1"
            ))
        )

        val turnQueue = TurnQueueManager()

        val updatedState = CombatBehavior.triggerCounterAttack(
            npcId = "guard1",
            roomId = "room1",
            worldState = worldState,
            turnQueue = turnQueue
        )

        val updatedRoom = updatedState.getRoom("room1")
        val updatedNpc = updatedRoom?.entities?.find { it.id == "guard1" } as? Entity.NPC

        assertNotNull(updatedNpc)
        assertEquals(-100, updatedNpc.getDisposition())
    }

    @Test
    fun `triggerCounterAttack creates CombatComponent if not present`() {
        val npcWithoutCombat = Entity.NPC(
            id = "goblin1",
            name = "Goblin",
            description = "A goblin",
            components = mapOf(
                ComponentType.SOCIAL to SocialComponent(
                    disposition = 0,
                    personality = "grumpy"
                )
            )
        )

        val room = Room(
            id = "room1",
            name = "Cave",
            traits = listOf("dark"),
            entities = listOf(npcWithoutCombat)
        )

        val worldState = WorldState(
            rooms = mapOf("room1" to room),
            players = mapOf("player1" to PlayerState(
                id = "player1",
                name = "Hero",
                currentRoomId = "room1"
            ))
        )

        val turnQueue = TurnQueueManager()

        val updatedState = CombatBehavior.triggerCounterAttack(
            npcId = "goblin1",
            roomId = "room1",
            worldState = worldState,
            turnQueue = turnQueue
        )

        val updatedRoom = updatedState.getRoom("room1")
        val updatedNpc = updatedRoom?.entities?.find { it.id == "goblin1" } as? Entity.NPC

        assertNotNull(updatedNpc)
        assertTrue(updatedNpc.hasComponent(ComponentType.COMBAT))
    }

    @Test
    fun `triggerCounterAttack adds NPC to turn queue`() {
        val npc = Entity.NPC(
            id = "bandit1",
            name = "Bandit",
            description = "A bandit",
            components = mapOf(
                ComponentType.SOCIAL to SocialComponent(
                    disposition = 0,
                    personality = "greedy"
                )
            )
        )

        val room = Room(
            id = "room1",
            name = "Forest",
            traits = listOf("trees"),
            entities = listOf(npc)
        )

        val worldState = WorldState(
            rooms = mapOf("room1" to room),
            players = mapOf("player1" to PlayerState(
                id = "player1",
                name = "Hero",
                currentRoomId = "room1"
            )),
            gameTime = 100L
        )

        val turnQueue = TurnQueueManager()

        CombatBehavior.triggerCounterAttack(
            npcId = "bandit1",
            roomId = "room1",
            worldState = worldState,
            turnQueue = turnQueue
        )

        assertTrue(turnQueue.contains("bandit1"))
    }

    @Test
    fun `triggerGroupHostility makes multiple NPCs hostile`() {
        val guard1 = Entity.NPC(
            id = "guard1",
            name = "Guard 1",
            description = "First guard",
            components = mapOf(
                ComponentType.SOCIAL to SocialComponent(
                    disposition = 0,
                    personality = "loyal"
                )
            )
        )

        val guard2 = Entity.NPC(
            id = "guard2",
            name = "Guard 2",
            description = "Second guard",
            components = mapOf(
                ComponentType.SOCIAL to SocialComponent(
                    disposition = 0,
                    personality = "loyal"
                )
            )
        )

        val room = Room(
            id = "room1",
            name = "Barracks",
            traits = listOf("fortified"),
            entities = listOf(guard1, guard2)
        )

        val worldState = WorldState(
            rooms = mapOf("room1" to room),
            players = mapOf("player1" to PlayerState(
                id = "player1",
                name = "Hero",
                currentRoomId = "room1"
            ))
        )

        val turnQueue = TurnQueueManager()

        val updatedState = CombatBehavior.triggerGroupHostility(
            npcIds = listOf("guard1", "guard2"),
            roomId = "room1",
            worldState = worldState,
            turnQueue = turnQueue
        )

        val updatedRoom = updatedState.getRoom("room1")
        val updatedGuard1 = updatedRoom?.entities?.find { it.id == "guard1" } as? Entity.NPC
        val updatedGuard2 = updatedRoom?.entities?.find { it.id == "guard2" } as? Entity.NPC

        assertNotNull(updatedGuard1)
        assertNotNull(updatedGuard2)
        assertEquals(-100, updatedGuard1.getDisposition())
        assertEquals(-100, updatedGuard2.getDisposition())
        assertTrue(turnQueue.contains("guard1"))
        assertTrue(turnQueue.contains("guard2"))
    }

    @Test
    fun `deEscalateCombat improves NPC disposition and removes from queue`() {
        val hostileNpc = Entity.NPC(
            id = "thief1",
            name = "Thief",
            description = "A hostile thief",
            components = mapOf(
                ComponentType.SOCIAL to SocialComponent(
                    disposition = -100,
                    personality = "sneaky"
                ),
                ComponentType.COMBAT to CombatComponent.create()
            )
        )

        val room = Room(
            id = "room1",
            name = "Alley",
            traits = listOf("dark"),
            entities = listOf(hostileNpc)
        )

        val worldState = WorldState(
            rooms = mapOf("room1" to room),
            players = mapOf("player1" to PlayerState(
                id = "player1",
                name = "Hero",
                currentRoomId = "room1"
            ))
        )

        val turnQueue = TurnQueueManager()
        turnQueue.enqueue("thief1", 100L)

        val updatedState = CombatBehavior.deEscalateCombat(
            npcId = "thief1",
            roomId = "room1",
            newDisposition = 0,
            worldState = worldState,
            turnQueue = turnQueue
        )

        val updatedRoom = updatedState.getRoom("room1")
        val updatedNpc = updatedRoom?.entities?.find { it.id == "thief1" } as? Entity.NPC

        assertNotNull(updatedNpc)
        assertEquals(0, updatedNpc.getDisposition())
        assertTrue(!turnQueue.contains("thief1"), "NPC should be removed from turn queue after de-escalation")
    }

    @Test
    fun `deEscalateCombat keeps NPC in queue if still hostile`() {
        val hostileNpc = Entity.NPC(
            id = "orc1",
            name = "Orc",
            description = "A hostile orc",
            components = mapOf(
                ComponentType.SOCIAL to SocialComponent(
                    disposition = -100,
                    personality = "brutal"
                ),
                ComponentType.COMBAT to CombatComponent.create()
            )
        )

        val room = Room(
            id = "room1",
            name = "Stronghold",
            traits = listOf("fortified"),
            entities = listOf(hostileNpc)
        )

        val worldState = WorldState(
            rooms = mapOf("room1" to room),
            players = mapOf("player1" to PlayerState(
                id = "player1",
                name = "Hero",
                currentRoomId = "room1"
            ))
        )

        val turnQueue = TurnQueueManager()
        turnQueue.enqueue("orc1", 100L)

        val updatedState = CombatBehavior.deEscalateCombat(
            npcId = "orc1",
            roomId = "room1",
            newDisposition = -80, // Still hostile
            worldState = worldState,
            turnQueue = turnQueue
        )

        val updatedRoom = updatedState.getRoom("room1")
        val updatedNpc = updatedRoom?.entities?.find { it.id == "orc1" } as? Entity.NPC

        assertNotNull(updatedNpc)
        assertEquals(-80, updatedNpc.getDisposition())
        assertTrue(turnQueue.contains("orc1"), "NPC should remain in turn queue if still hostile")
    }

    @Test
    fun `shouldInitiateCombat returns true for hostile NPC`() {
        val hostileNpc = Entity.NPC(
            id = "enemy1",
            name = "Enemy",
            description = "A hostile enemy",
            components = mapOf(
                ComponentType.SOCIAL to SocialComponent(
                    disposition = -100,
                    personality = "aggressive"
                )
            )
        )

        assertTrue(CombatBehavior.shouldInitiateCombat(hostileNpc))
    }

    @Test
    fun `shouldInitiateCombat returns false for friendly NPC`() {
        val friendlyNpc = Entity.NPC(
            id = "friend1",
            name = "Friend",
            description = "A friendly NPC",
            components = mapOf(
                ComponentType.SOCIAL to SocialComponent(
                    disposition = 50,
                    personality = "kind"
                )
            )
        )

        assertTrue(!CombatBehavior.shouldInitiateCombat(friendlyNpc))
    }
}
