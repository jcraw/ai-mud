package com.jcraw.mud.reasoning.combat

import com.jcraw.mud.core.*
import com.jcraw.mud.core.repository.ItemRepository
import com.jcraw.mud.reasoning.loot.LootGenerator
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

    // Mock ItemRepository that returns no templates
    private val mockItemRepository = object : ItemRepository {
        override fun findTemplateById(templateId: String): Result<ItemTemplate?> = Result.success(null)
        override fun findAllTemplates(): Result<Map<String, ItemTemplate>> = Result.success(emptyMap())
        override fun findTemplatesByType(type: ItemType): Result<List<ItemTemplate>> = Result.success(emptyList())
        override fun findTemplatesByRarity(rarity: Rarity): Result<List<ItemTemplate>> = Result.success(emptyList())
        override fun saveTemplate(template: ItemTemplate): Result<Unit> = Result.success(Unit)
        override fun saveTemplates(templates: List<ItemTemplate>): Result<Unit> = Result.success(Unit)
        override fun deleteTemplate(templateId: String): Result<Unit> = Result.success(Unit)
        override fun findInstanceById(instanceId: String): Result<ItemInstance?> = Result.success(null)
        override fun findInstancesByTemplate(templateId: String): Result<List<ItemInstance>> = Result.success(emptyList())
        override fun saveInstance(instance: ItemInstance): Result<Unit> = Result.success(Unit)
        override fun deleteInstance(instanceId: String): Result<Unit> = Result.success(Unit)
        override fun findAllInstances(): Result<Map<String, ItemInstance>> = Result.success(emptyMap())
    }

    private val mockLootGenerator = LootGenerator(mockItemRepository)
    private val deathHandler = DeathHandler(mockLootGenerator)

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
    fun `handleDeath creates corpse from dead Player`() {
        // TODO: Update when InventoryComponent is integrated for players
        // Currently creates empty corpse - player inventory transfer not yet implemented

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
            maxHealth = 40
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
        assertTrue(playerDeath.corpse.contents.isEmpty()) // Empty until InventoryComponent integration

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
    fun `handleDeath returns null for features`() {
        val feature = Entity.Feature(
            id = "feature1",
            name = "Boulder",
            description = "A large boulder"
        )

        val room = Room(
            id = "room1",
            name = "Cave",
            traits = listOf("dark"),
            entities = listOf(feature)
        )

        val worldState = WorldState(
            rooms = mapOf("room1" to room),
            players = emptyMap()
        )

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
    fun `shouldDie returns false for features and corpses`() {
        val feature = Entity.Feature(id = "feature1", name = "Door", description = "A door")
        val corpse = Entity.Corpse(id = "corpse1", name = "Corpse", description = "A corpse")

        assertFalse(deathHandler.shouldDie(feature))
        assertFalse(deathHandler.shouldDie(corpse))
    }
}
