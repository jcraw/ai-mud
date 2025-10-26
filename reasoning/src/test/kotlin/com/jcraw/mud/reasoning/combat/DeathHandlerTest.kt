package com.jcraw.mud.reasoning.combat

import com.jcraw.mud.core.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Tests for DeathHandler - entity death and corpse creation
 */
class DeathHandlerTest {

    private val deathHandler = DeathHandler()

    @Test
    fun `handleDeath creates corpse from dead NPC`() {
        val npc = Entity.NPC(
            id = "goblin1",
            name = "Goblin",
            description = "An angry goblin",
            health = 0,
            maxHealth = 20
        )

        val room = Room(
            id = "room1",
            name = "Cave",
            traits = listOf("dark"),
            entities = listOf(npc)
        )

        val worldState = WorldState(
            rooms = mapOf("room1" to room),
            players = emptyMap()
        )

        val result = deathHandler.handleDeath("goblin1", worldState)

        assertNotNull(result)
        assertTrue(result is DeathHandler.DeathResult.NPCDeath)

        val npcDeath = result as DeathHandler.DeathResult.NPCDeath
        assertEquals("Corpse of Goblin", npcDeath.corpse.name)
        assertEquals(100, npcDeath.corpse.decayTimer)
        assertTrue(npcDeath.corpse.contents.isEmpty()) // NPCs don't carry items yet

        // Verify NPC removed from room
        val updatedRoom = npcDeath.updatedWorld.rooms["room1"]
        assertNotNull(updatedRoom)
        assertNull(updatedRoom.getEntity("goblin1"))

        // Verify corpse added to room
        val corpse = updatedRoom.entities.filterIsInstance<Entity.Corpse>().firstOrNull()
        assertNotNull(corpse)
        assertEquals("Corpse of Goblin", corpse.name)
    }

    @Test
    fun `handleDeath creates corpse from dead Player with inventory`() {
        val sword = Entity.Item(
            id = "sword1",
            name = "Iron Sword",
            description = "A sturdy sword",
            itemType = ItemType.WEAPON,
            damageBonus = 5
        )

        val potion = Entity.Item(
            id = "potion1",
            name = "Health Potion",
            description = "Restores health",
            itemType = ItemType.CONSUMABLE,
            healAmount = 20
        )

        val playerEntity = Entity.Player(
            id = "player_entity1",
            name = "Hero",
            description = "A brave hero",
            playerId = "player1",
            health = 0,
            maxHealth = 40
        )

        val playerState = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "room1",
            health = 0,
            maxHealth = 40,
            inventory = listOf(potion),
            equippedWeapon = sword
        )

        val room = Room(
            id = "room1",
            name = "Dungeon",
            traits = listOf("dark"),
            entities = listOf(playerEntity)
        )

        val worldState = WorldState(
            rooms = mapOf("room1" to room),
            players = mapOf("player1" to playerState)
        )

        val result = deathHandler.handleDeath("player_entity1", worldState)

        assertNotNull(result)
        assertTrue(result is DeathHandler.DeathResult.PlayerDeath)

        val playerDeath = result as DeathHandler.DeathResult.PlayerDeath
        assertEquals("Corpse of Hero", playerDeath.corpse.name)
        assertEquals(200, playerDeath.corpse.decayTimer) // Player corpses last longer
        assertEquals(2, playerDeath.corpse.contents.size) // Sword + Potion
        assertTrue(playerDeath.corpse.contents.any { it.id == "sword1" })
        assertTrue(playerDeath.corpse.contents.any { it.id == "potion1" })

        // Verify player entity removed from room
        val updatedRoom = playerDeath.updatedWorld.rooms["room1"]
        assertNotNull(updatedRoom)
        assertNull(updatedRoom.getEntity("player_entity1"))

        // Verify corpse added to room
        val corpse = updatedRoom.entities.filterIsInstance<Entity.Corpse>().firstOrNull()
        assertNotNull(corpse)
        assertEquals("Corpse of Hero", corpse.name)
    }

    @Test
    fun `handleDeath returns null for nonexistent entity`() {
        val room = Room(
            id = "room1",
            name = "Cave",
            traits = listOf("dark"),
            entities = emptyList()
        )

        val worldState = WorldState(
            rooms = mapOf("room1" to room),
            players = emptyMap()
        )

        val result = deathHandler.handleDeath("nonexistent", worldState)

        assertNull(result)
    }

    @Test
    fun `handleDeath returns null for items and features`() {
        val item = Entity.Item(
            id = "item1",
            name = "Torch",
            description = "A burning torch"
        )

        val feature = Entity.Feature(
            id = "feature1",
            name = "Boulder",
            description = "A large boulder"
        )

        val room = Room(
            id = "room1",
            name = "Cave",
            traits = listOf("dark"),
            entities = listOf(item, feature)
        )

        val worldState = WorldState(
            rooms = mapOf("room1" to room),
            players = emptyMap()
        )

        assertNull(deathHandler.handleDeath("item1", worldState))
        assertNull(deathHandler.handleDeath("feature1", worldState))
    }

    @Test
    fun `shouldDie returns true for NPC with health less than or equal to 0`() {
        val deadNpc = Entity.NPC(
            id = "npc1",
            name = "Goblin",
            description = "Dead goblin",
            health = 0,
            maxHealth = 20
        )

        val dyingNpc = Entity.NPC(
            id = "npc2",
            name = "Troll",
            description = "Dying troll",
            health = -5,
            maxHealth = 50
        )

        assertTrue(deathHandler.shouldDie(deadNpc))
        assertTrue(deathHandler.shouldDie(dyingNpc))
    }

    @Test
    fun `shouldDie returns false for NPC with positive health`() {
        val aliveNpc = Entity.NPC(
            id = "npc1",
            name = "Goblin",
            description = "Healthy goblin",
            health = 10,
            maxHealth = 20
        )

        assertFalse(deathHandler.shouldDie(aliveNpc))
    }

    @Test
    fun `shouldDie returns true for Player with health less than or equal to 0`() {
        val deadPlayer = Entity.Player(
            id = "player1",
            name = "Hero",
            description = "Dead hero",
            playerId = "p1",
            health = 0,
            maxHealth = 40
        )

        val dyingPlayer = Entity.Player(
            id = "player2",
            name = "Warrior",
            description = "Dying warrior",
            playerId = "p2",
            health = -10,
            maxHealth = 50
        )

        assertTrue(deathHandler.shouldDie(deadPlayer))
        assertTrue(deathHandler.shouldDie(dyingPlayer))
    }

    @Test
    fun `shouldDie returns false for Player with positive health`() {
        val alivePlayer = Entity.Player(
            id = "player1",
            name = "Hero",
            description = "Healthy hero",
            playerId = "p1",
            health = 30,
            maxHealth = 40
        )

        assertFalse(deathHandler.shouldDie(alivePlayer))
    }

    @Test
    fun `shouldDie returns false for items and features`() {
        val item = Entity.Item(id = "item1", name = "Sword", description = "A sword")
        val feature = Entity.Feature(id = "feature1", name = "Door", description = "A door")
        val corpse = Entity.Corpse(id = "corpse1", name = "Corpse", description = "A corpse")

        assertFalse(deathHandler.shouldDie(item))
        assertFalse(deathHandler.shouldDie(feature))
        assertFalse(deathHandler.shouldDie(corpse))
    }

    @Test
    fun `handleDeath creates corpse with both equipped weapon and armor`() {
        val sword = Entity.Item(
            id = "sword1",
            name = "Steel Sword",
            description = "A steel sword",
            itemType = ItemType.WEAPON,
            damageBonus = 8
        )

        val armor = Entity.Item(
            id = "armor1",
            name = "Chainmail",
            description = "Metal armor",
            itemType = ItemType.ARMOR,
            defenseBonus = 5
        )

        val potion = Entity.Item(
            id = "potion1",
            name = "Potion",
            description = "A potion",
            itemType = ItemType.CONSUMABLE
        )

        val playerEntity = Entity.Player(
            id = "player_entity1",
            name = "Knight",
            description = "A brave knight",
            playerId = "player1",
            health = 0,
            maxHealth = 50
        )

        val playerState = PlayerState(
            id = "player1",
            name = "Knight",
            currentRoomId = "room1",
            health = 0,
            maxHealth = 50,
            inventory = listOf(potion),
            equippedWeapon = sword,
            equippedArmor = armor
        )

        val room = Room(
            id = "room1",
            name = "Battlefield",
            traits = listOf("bloody"),
            entities = listOf(playerEntity)
        )

        val worldState = WorldState(
            rooms = mapOf("room1" to room),
            players = mapOf("player1" to playerState)
        )

        val result = deathHandler.handleDeath("player_entity1", worldState) as? DeathHandler.DeathResult.PlayerDeath

        assertNotNull(result)
        assertEquals(3, result.corpse.contents.size) // Sword + Armor + Potion
        assertTrue(result.corpse.contents.any { it.id == "sword1" })
        assertTrue(result.corpse.contents.any { it.id == "armor1" })
        assertTrue(result.corpse.contents.any { it.id == "potion1" })
    }

    @Test
    fun `handleDeath creates empty corpse when player has no items`() {
        val playerEntity = Entity.Player(
            id = "player_entity1",
            name = "Peasant",
            description = "A poor peasant",
            playerId = "player1",
            health = 0,
            maxHealth = 20
        )

        val playerState = PlayerState(
            id = "player1",
            name = "Peasant",
            currentRoomId = "room1",
            health = 0,
            maxHealth = 20,
            inventory = emptyList(),
            equippedWeapon = null,
            equippedArmor = null
        )

        val room = Room(
            id = "room1",
            name = "Village",
            traits = listOf("peaceful"),
            entities = listOf(playerEntity)
        )

        val worldState = WorldState(
            rooms = mapOf("room1" to room),
            players = mapOf("player1" to playerState)
        )

        val result = deathHandler.handleDeath("player_entity1", worldState) as? DeathHandler.DeathResult.PlayerDeath

        assertNotNull(result)
        assertTrue(result.corpse.contents.isEmpty())
        assertEquals(200, result.corpse.decayTimer)
    }
}
