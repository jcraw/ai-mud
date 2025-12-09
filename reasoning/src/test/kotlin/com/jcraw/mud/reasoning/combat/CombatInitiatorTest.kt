package com.jcraw.mud.reasoning.combat

import com.jcraw.mud.core.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Tests for CombatInitiator - disposition-based hostility detection
 */
class CombatInitiatorTest {

    @Test
    fun `checkForHostileEntities returns hostile NPCs with disposition below -75`() {
        // Create NPC with hostile disposition
        val hostileNpc = Entity.NPC(
            id = "goblin1",
            name = "Goblin",
            description = "An angry goblin",
            components = mapOf(
                ComponentType.SOCIAL to SocialComponent(
                    disposition = -80,
                    personality = "aggressive"
                )
            )
        )

        val space = SpacePropertiesComponent(
            name = "Cave",
            entities = listOf("goblin1")
        )

        val worldState = WorldState(
            spaces = mapOf("room1" to space),
            entities = mapOf("goblin1" to hostileNpc),
            players = mapOf("player1" to PlayerState(
                id = "player1",
                name = "Hero",
                currentRoomId = "room1"
            ))
        )

        val hostileIds = CombatInitiator.checkForHostileEntities("room1", worldState)

        assertEquals(1, hostileIds.size)
        assertEquals("goblin1", hostileIds[0])
    }

    @Test
    fun `checkForHostileEntities ignores friendly NPCs`() {
        val friendlyNpc = Entity.NPC(
            id = "merchant1",
            name = "Merchant",
            description = "A friendly merchant",
            components = mapOf(
                ComponentType.SOCIAL to SocialComponent(
                    disposition = 50,
                    personality = "friendly"
                )
            )
        )

        val space = SpacePropertiesComponent(
            name = "Shop",
            entities = listOf("merchant1")
        )

        val worldState = WorldState(
            spaces = mapOf("room1" to space),
            entities = mapOf("merchant1" to friendlyNpc),
            players = mapOf("player1" to PlayerState(
                id = "player1",
                name = "Hero",
                currentRoomId = "room1"
            ))
        )

        val hostileIds = CombatInitiator.checkForHostileEntities("room1", worldState)

        assertTrue(hostileIds.isEmpty())
    }

    @Test
    fun `checkForHostileEntities detects multiple hostile NPCs`() {
        val goblin1 = Entity.NPC(
            id = "goblin1",
            name = "Goblin 1",
            description = "A goblin",
            components = mapOf(
                ComponentType.SOCIAL to SocialComponent(
                    disposition = -90,
                    personality = "aggressive"
                )
            )
        )

        val goblin2 = Entity.NPC(
            id = "goblin2",
            name = "Goblin 2",
            description = "Another goblin",
            components = mapOf(
                ComponentType.SOCIAL to SocialComponent(
                    disposition = -80,
                    personality = "aggressive"
                )
            )
        )

        val space = SpacePropertiesComponent(
            name = "Goblin Den",
            entities = listOf("goblin1", "goblin2")
        )

        val worldState = WorldState(
            spaces = mapOf("room1" to space),
            entities = mapOf("goblin1" to goblin1, "goblin2" to goblin2),
            players = mapOf("player1" to PlayerState(
                id = "player1",
                name = "Hero",
                currentRoomId = "room1"
            ))
        )

        val hostileIds = CombatInitiator.checkForHostileEntities("room1", worldState)

        assertEquals(2, hostileIds.size)
        assertTrue(hostileIds.contains("goblin1"))
        assertTrue(hostileIds.contains("goblin2"))
    }

    @Test
    fun `isHostile returns true for NPCs with disposition below -75`() {
        val hostileNpc = Entity.NPC(
            id = "enemy1",
            name = "Enemy",
            description = "A hostile enemy",
            components = mapOf(
                ComponentType.SOCIAL to SocialComponent(
                    disposition = -100,
                    personality = "violent"
                )
            )
        )

        assertTrue(CombatInitiator.isHostile(hostileNpc))
    }

    @Test
    fun `isHostile returns false for NPCs with disposition at -75`() {
        val neutralNpc = Entity.NPC(
            id = "guard1",
            name = "Guard",
            description = "A wary guard",
            components = mapOf(
                ComponentType.SOCIAL to SocialComponent(
                    disposition = -75,
                    personality = "cautious"
                )
            )
        )

        assertFalse(CombatInitiator.isHostile(neutralNpc))
    }

    @Test
    fun `isHostile uses legacy isHostile flag as fallback`() {
        val legacyHostileNpc = Entity.NPC(
            id = "monster1",
            name = "Monster",
            description = "A hostile monster",
            isHostile = true
        )

        assertTrue(CombatInitiator.isHostile(legacyHostileNpc))
    }

    @Test
    fun `hasHostileEntitiesInCurrentRoom returns true when hostile NPCs present`() {
        val hostileNpc = Entity.NPC(
            id = "bandit1",
            name = "Bandit",
            description = "A hostile bandit",
            components = mapOf(
                ComponentType.SOCIAL to SocialComponent(
                    disposition = -90,
                    personality = "aggressive"
                )
            )
        )

        val space = SpacePropertiesComponent(
            name = "Forest Path",
            entities = listOf("bandit1")
        )

        val worldState = WorldState(
            spaces = mapOf("room1" to space),
            entities = mapOf("bandit1" to hostileNpc),
            players = mapOf("player1" to PlayerState(
                id = "player1",
                name = "Hero",
                currentRoomId = "room1"
            ))
        )

        assertTrue(CombatInitiator.hasHostileEntitiesInCurrentRoom(worldState))
    }

    @Test
    fun `hasHostileEntitiesInCurrentRoom returns false when no hostile NPCs`() {
        val friendlyNpc = Entity.NPC(
            id = "villager1",
            name = "Villager",
            description = "A friendly villager",
            components = mapOf(
                ComponentType.SOCIAL to SocialComponent(
                    disposition = 30,
                    personality = "helpful"
                )
            )
        )

        val space = SpacePropertiesComponent(
            name = "Village",
            entities = listOf("villager1")
        )

        val worldState = WorldState(
            spaces = mapOf("room1" to space),
            entities = mapOf("villager1" to friendlyNpc),
            players = mapOf("player1" to PlayerState(
                id = "player1",
                name = "Hero",
                currentRoomId = "room1"
            ))
        )

        assertFalse(CombatInitiator.hasHostileEntitiesInCurrentRoom(worldState))
    }
}
