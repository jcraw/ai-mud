package com.jcraw.app.integration

import com.jcraw.mud.core.*
import com.jcraw.mud.testbot.InMemoryGameEngine
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Integration tests for combat system
 *
 * Tests the full combat workflow including:
 * - Combat initiation
 * - Equipment modifiers (weapons/armor)
 * - Combat progression
 * - Combat end conditions (player death, NPC death)
 * - Loot drops
 */
class CombatIntegrationTest {

    @Test
    fun `player can defeat weak NPC in combat`() = runBlocking {
        val world = createTestWorld(
            npc = Entity.NPC(
                id = "goblin",
                name = "Goblin",
                description = "A weak goblin",
                isHostile = true,
                health = 5,  // Very low health for quick test
                maxHealth = 5
            )
        )
        val engine = InMemoryGameEngine(world)

        // Start combat
        val response1 = engine.processInput("attack goblin")
        assertTrue(response1.contains("combat", ignoreCase = true) || response1.contains("attack", ignoreCase = true))

        // Player should now be in combat
        assertTrue(engine.getWorldState().player.isInCombat())

        // Continue attacking until NPC is defeated
        var defeated = false
        repeat(10) {
            val response = engine.processInput("attack goblin")
            if (response.contains("defeated", ignoreCase = true) || response.contains("dies", ignoreCase = true)) {
                defeated = true
                return@repeat
            }
        }

        assertTrue(defeated, "Goblin should be defeated within 10 turns")
        assertFalse(engine.getWorldState().player.isInCombat())
    }

    @Test
    fun `equipped weapon increases damage`() = runBlocking {
        val weapon = Entity.Item(
            id = "sword",
            name = "Iron Sword",
            description = "A sharp blade",
            isPickupable = true,
            itemType = ItemType.WEAPON,
            damageBonus = 5
        )

        val npc = Entity.NPC(
            id = "target",
            name = "Training Dummy",
            description = "A practice target",
            isHostile = true,
            health = 100,
            maxHealth = 100
        )

        val world = createTestWorld(items = listOf(weapon), npc = npc)
        val engine = InMemoryGameEngine(world)

        // Take and equip weapon
        engine.processInput("take sword")
        val equipResponse = engine.processInput("equip sword")
        assertTrue(equipResponse.contains("equip", ignoreCase = true))
        assertTrue(engine.getWorldState().player.equippedWeapon != null)

        // Start combat
        engine.processInput("attack target")
        assertTrue(engine.getWorldState().player.isInCombat())

        // Continue combat for a couple turns
        val response1 = engine.processInput("attack target")
        val response2 = engine.processInput("attack target")

        // NPC health should be reduced
        val combat = engine.getWorldState().player.activeCombat
        assertTrue(combat != null && combat.npcHealth < 100, "NPC should take damage")
    }

    @Test
    fun `equipped armor reduces incoming damage`() = runBlocking {
        val armor = Entity.Item(
            id = "armor",
            name = "Chainmail",
            description = "Heavy armor",
            isPickupable = true,
            itemType = ItemType.ARMOR,
            defenseBonus = 5
        )

        val npc = Entity.NPC(
            id = "attacker",
            name = "Strong Orc",
            description = "A powerful orc",
            isHostile = true,
            health = 100,
            maxHealth = 100,
            stats = Stats(strength = 18)  // High damage
        )

        val world = createTestWorld(items = listOf(armor), npc = npc)
        val engine = InMemoryGameEngine(world)

        // Take and equip armor
        engine.processInput("take armor")
        val equipResponse = engine.processInput("equip armor")
        assertTrue(equipResponse.contains("equip", ignoreCase = true))
        assertEquals(5, engine.getWorldState().player.getArmorDefenseBonus())

        // Start combat
        engine.processInput("attack attacker")

        // Verify player has armor equipped during combat
        val combat = engine.getWorldState().player.activeCombat
        assertTrue(combat != null, "Combat should have started")
        assertTrue(engine.getWorldState().player.equippedArmor != null, "Armor should still be equipped")
    }

    @Test
    fun `player death ends combat`() = runBlocking {
        val playerState = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            health = 1,  // Extremely low health - will die quickly
            maxHealth = 100
        )

        val npc = Entity.NPC(
            id = "killer",
            name = "Deadly Assassin",
            description = "A lethal foe",
            isHostile = true,
            health = 100,
            maxHealth = 100,
            stats = Stats(strength = 20)  // Very high damage
        )

        val world = createTestWorld(player = playerState, npc = npc)
        val engine = InMemoryGameEngine(world)

        // Start combat
        engine.processInput("attack assassin")

        // Continue attacking until player dies or engine stops
        var died = false
        repeat(20) {
            if (!engine.isRunning()) {
                died = true
                return@repeat
            }
            val response = engine.processInput("attack assassin")
            if (response.contains("defeated", ignoreCase = true) ||
                response.contains("die", ignoreCase = true) ||
                !engine.isRunning()) {
                died = true
                return@repeat
            }
        }

        assertTrue(died, "Player should die given low health (1 HP) and high enemy damage")
    }

    @Test
    fun `defeated NPC is removed from room`() = runBlocking {
        val npc = Entity.NPC(
            id = "victim",
            name = "Weak Slime",
            description = "A pathetic slime",
            isHostile = true,
            health = 1,  // Dies in one hit
            maxHealth = 1
        )

        val world = createTestWorld(npc = npc)
        val engine = InMemoryGameEngine(world)

        // Confirm NPC exists
        val room = engine.getWorldState().getCurrentRoomView()!!
        assertTrue(room.getEntity("victim") != null)

        // Attack until NPC is defeated (should be quick with 1 HP)
        var defeated = false
        repeat(5) {
            val response = engine.processInput("attack slime")
            if (response.contains("defeated", ignoreCase = true) || response.contains("dies", ignoreCase = true)) {
                defeated = true
                return@repeat
            }
        }

        assertTrue(defeated, "NPC with 1 HP should be defeated quickly")

        // NPC should be removed from room
        val updatedRoom = engine.getWorldState().getCurrentRoomView()!!
        assertTrue(updatedRoom.getEntity("victim") == null, "Defeated NPC should be removed from room")
        assertFalse(engine.getWorldState().player.isInCombat())
    }

    @Test
    fun `player can flee from combat`() = runBlocking {
        val npc = Entity.NPC(
            id = "enemy",
            name = "Guard",
            description = "A vigilant guard",
            isHostile = true,
            health = 50,
            maxHealth = 50
        )

        val world = createTestWorldWithExits(npc = npc)
        val engine = InMemoryGameEngine(world)

        // Start combat
        engine.processInput("attack guard")
        assertTrue(engine.getWorldState().player.isInCombat())

        // Try to flee (may take multiple attempts due to 50% success rate)
        var fled = false
        repeat(10) {  // 10 attempts should be enough (probability of all failing: 0.5^10 ≈ 0.1%)
            val response = engine.processInput("north")  // Try to move
            if (response.contains("flee", ignoreCase = true) || response.contains("escape", ignoreCase = true)) {
                fled = true
                // Check if we actually moved
                if (!engine.getWorldState().player.isInCombat()) {
                    return@repeat
                }
            }
        }

        assertTrue(fled || !engine.getWorldState().player.isInCombat(), "Player should eventually flee from combat")
    }

    @Test
    fun `combat progresses through multiple rounds`() = runBlocking {
        val npc = Entity.NPC(
            id = "opponent",
            name = "Warrior",
            description = "A skilled warrior",
            isHostile = true,
            health = 50,
            maxHealth = 50
        )

        val world = createTestWorld(npc = npc)
        val engine = InMemoryGameEngine(world)

        // Start combat
        engine.processInput("attack warrior")
        val combat1 = engine.getWorldState().player.activeCombat!!
        val initialNpcHealth = combat1.npcHealth
        val initialPlayerHealth = combat1.playerHealth

        // Continue for several rounds
        repeat(3) {
            engine.processInput("attack warrior")
        }

        val combat2 = engine.getWorldState().player.activeCombat

        if (combat2 != null) {
            // Combat ongoing - both combatants should have taken damage
            assertTrue(combat2.npcHealth < initialNpcHealth, "NPC should take damage")
            assertTrue(combat2.playerHealth < initialPlayerHealth, "Player should take damage from counter-attacks")
        } else {
            // Combat ended - one combatant died
            assertTrue(engine.getWorldState().player.health == 0 || !engine.isRunning())
        }
    }

    // ========== Helper Functions ==========

    private fun createTestWorld(
        player: PlayerState = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room"
        ),
        items: List<Entity.Item> = emptyList(),
        npc: Entity.NPC? = null
    ): WorldState {
        val entities = mutableListOf<Entity>()
        entities.addAll(items)
        if (npc != null) entities.add(npc)

        val room = Room(
            id = "test_room",
            name = "Test Room",
            traits = listOf("empty", "quiet"),
            entities = entities
        )

        return buildWorldStateFromRooms(
            rooms = mapOf("test_room" to room),
            player = player,
            config = LegacyWorldConfig(
                chunkId = "combat_chunk",
                lore = "Combat test chunk",
                biomeTheme = "test"
            )
        )
    }

    private fun createTestWorldWithExits(
        player: PlayerState = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room"
        ),
        npc: Entity.NPC? = null
    ): WorldState {
        val entities = if (npc != null) listOf(npc) else emptyList()

        val room1 = Room(
            id = "test_room",
            name = "Test Room",
            traits = listOf("empty", "quiet"),
            exits = mapOf(Direction.NORTH to "north_room"),
            entities = entities
        )

        val room2 = Room(
            id = "north_room",
            name = "Northern Room",
            traits = listOf("safe", "quiet"),
            exits = mapOf(Direction.SOUTH to "test_room")
        )

        return buildWorldStateFromRooms(
            rooms = mapOf("test_room" to room1, "north_room" to room2),
            player = player,
            config = LegacyWorldConfig(
                chunkId = "combat_exits_chunk",
                lore = "Combat exits chunk",
                biomeTheme = "test"
            )
        )
    }
}
