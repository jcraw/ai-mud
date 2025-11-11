package com.jcraw.mud.reasoning.combat

import com.jcraw.mud.core.*
import com.jcraw.mud.core.world.NodeType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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

        val worldState = createWorldState(listOf(neutralNpc))

        val turnQueue = TurnQueueManager()

        val updatedState = CombatBehavior.triggerCounterAttack(
            npcId = "guard1",
            spaceId = TEST_SPACE_ID,
            worldState = worldState,
            turnQueue = turnQueue
        )

        val updatedNpc = updatedState.getEntity("guard1") as? Entity.NPC

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

        val worldState = createWorldState(listOf(npcWithoutCombat))

        val turnQueue = TurnQueueManager()

        val updatedState = CombatBehavior.triggerCounterAttack(
            npcId = "goblin1",
            spaceId = TEST_SPACE_ID,
            worldState = worldState,
            turnQueue = turnQueue
        )

        val updatedNpc = updatedState.getEntity("goblin1") as? Entity.NPC

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

        val worldState = createWorldState(listOf(npc), gameTime = 100L)

        val turnQueue = TurnQueueManager()

        CombatBehavior.triggerCounterAttack(
            npcId = "bandit1",
            spaceId = TEST_SPACE_ID,
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

        val worldState = createWorldState(listOf(guard1, guard2))

        val turnQueue = TurnQueueManager()

        val updatedState = CombatBehavior.triggerGroupHostility(
            npcIds = listOf("guard1", "guard2"),
            spaceId = TEST_SPACE_ID,
            worldState = worldState,
            turnQueue = turnQueue
        )

        val updatedGuard1 = updatedState.getEntity("guard1") as? Entity.NPC
        val updatedGuard2 = updatedState.getEntity("guard2") as? Entity.NPC

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

        val worldState = createWorldState(listOf(hostileNpc))

        val turnQueue = TurnQueueManager()
        turnQueue.enqueue("thief1", 100L)

        val updatedState = CombatBehavior.deEscalateCombat(
            npcId = "thief1",
            spaceId = TEST_SPACE_ID,
            newDisposition = 0,
            worldState = worldState,
            turnQueue = turnQueue
        )

        val updatedNpc = updatedState.getEntity("thief1") as? Entity.NPC

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

        val worldState = createWorldState(listOf(hostileNpc))

        val turnQueue = TurnQueueManager()
        turnQueue.enqueue("orc1", 100L)

        val updatedState = CombatBehavior.deEscalateCombat(
            npcId = "orc1",
            spaceId = TEST_SPACE_ID,
            newDisposition = -80, // Still hostile
            worldState = worldState,
            turnQueue = turnQueue
        )

        val updatedNpc = updatedState.getEntity("orc1") as? Entity.NPC

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

private const val TEST_SPACE_ID = "space_test"
private const val TEST_CHUNK_ID = "chunk_test"

private fun createWorldState(
    npcs: List<Entity.NPC>,
    spaceId: SpaceId = TEST_SPACE_ID,
    gameTime: Long = 0L
): WorldState {
    val player = PlayerState(
        id = "player1",
        name = "Hero",
        currentRoomId = spaceId
    )

    val space = SpacePropertiesComponent(
        name = "Test Space",
        description = "A test arena",
        entities = npcs.map { it.id }
    )

    val graphNode = GraphNodeComponent(
        id = spaceId,
        type = NodeType.Hub,
        chunkId = TEST_CHUNK_ID
    )

    return WorldState(
        graphNodes = mapOf(spaceId to graphNode),
        spaces = mapOf(spaceId to space),
        entities = npcs.associateBy { it.id },
        players = mapOf(player.id to player),
        gameTime = gameTime
    )
}
