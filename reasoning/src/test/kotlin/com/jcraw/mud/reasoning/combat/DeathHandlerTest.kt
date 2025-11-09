package com.jcraw.mud.reasoning.combat

import com.jcraw.mud.core.*
import com.jcraw.mud.core.repository.ItemRepository
import com.jcraw.mud.reasoning.loot.LootGenerator
import com.jcraw.mud.reasoning.loot.LootTableRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for DeathHandler - entity death, corpse creation, and loot drops.
 */
class DeathHandlerTest {

    // Mock ItemRepository that returns no templates (used for baseline tests)
    private val emptyItemRepository = object : ItemRepository {
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

    private val baselineDeathHandler = DeathHandler(LootGenerator(emptyItemRepository))

    @Test
    fun `handleDeath creates corpse from dead NPC`() {
        val npc = Entity.NPC(
            id = "goblin1",
            name = "Goblin",
            description = "An angry goblin",
            health = 0,
            maxHealth = 20
        )

        val worldState = createWorldState(listOf(npc))

        val result = baselineDeathHandler.handleDeath("goblin1", worldState)

        assertNotNull(result)
        assertTrue(result is DeathHandler.DeathResult.NPCDeath)

        val npcDeath = result as DeathHandler.DeathResult.NPCDeath
        assertEquals("Corpse of Goblin", npcDeath.corpse.name)
        assertEquals(100, npcDeath.corpse.decayTimer)
        assertTrue(npcDeath.corpse.contents.isEmpty())

        val updatedSpace = npcDeath.updatedWorld.getSpace(TEST_SPACE_ID)
        assertNotNull(updatedSpace)
        assertTrue(updatedSpace.itemsDropped.isEmpty())

        val entities = npcDeath.updatedWorld.getEntitiesInSpace(TEST_SPACE_ID)
        assertTrue(entities.any { it is Entity.Corpse && it.name == "Corpse of Goblin" })
        assertTrue(entities.none { it.id == "goblin1" })
    }

    @Test
    fun `handleDeath creates corpse from dead Player`() {
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
            currentRoomId = TEST_SPACE_ID,
            health = 0,
            maxHealth = 40
        )

        val worldState = createWorldState(
            entities = listOf(playerEntity),
            players = mapOf(playerState.id to playerState)
        )

        val result = baselineDeathHandler.handleDeath("player_entity1", worldState)

        assertNotNull(result)
        assertTrue(result is DeathHandler.DeathResult.PlayerDeath)

        val playerDeath = result as DeathHandler.DeathResult.PlayerDeath
        assertEquals("Corpse of Hero", playerDeath.corpse.name)
        assertEquals(200, playerDeath.corpse.decayTimer)
        assertTrue(playerDeath.corpse.contents.isEmpty())

        val updatedSpace = playerDeath.updatedWorld.getSpace(TEST_SPACE_ID)
        assertNotNull(updatedSpace)
        assertTrue(updatedSpace.itemsDropped.isEmpty())
        val entities = playerDeath.updatedWorld.getEntitiesInSpace(TEST_SPACE_ID)
        assertTrue(entities.any { it is Entity.Corpse && it.name == "Corpse of Hero" })
    }

    @Test
    fun `handleDeath returns null for nonexistent entity`() {
        val worldState = createWorldState(emptyList())
        val result = baselineDeathHandler.handleDeath("nonexistent", worldState)
        assertNull(result)
    }

    @Test
    fun `handleDeath returns null for features`() {
        val feature = Entity.Feature(
            id = "feature1",
            name = "Boulder",
            description = "A large boulder"
        )
        val worldState = createWorldState(listOf(feature))
        assertNull(baselineDeathHandler.handleDeath("feature1", worldState))
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

        assertTrue(baselineDeathHandler.shouldDie(deadNpc))
        assertTrue(baselineDeathHandler.shouldDie(dyingNpc))
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

        assertFalse(baselineDeathHandler.shouldDie(aliveNpc))
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

        assertTrue(baselineDeathHandler.shouldDie(deadPlayer))
        assertTrue(baselineDeathHandler.shouldDie(dyingPlayer))
    }

    @Test
    fun `shouldDie returns false for Player with positive health`() {
        val alivePlayer = Entity.Player(
            id = "player1",
            name = "Hero",
            description = "Alive hero",
            playerId = "p1",
            health = 10,
            maxHealth = 40
        )

        assertFalse(baselineDeathHandler.shouldDie(alivePlayer))
    }

    @Test
    fun `shouldDie returns false for non-living entities`() {
        val feature = Entity.Feature(
            id = "feature1",
            name = "Boulder",
            description = "A sturdy boulder"
        )
        val corpse = Entity.Corpse(
            id = "corpse1",
            name = "Old Corpse",
            description = "Long dead",
            contents = emptyList(),
            goldAmount = 0
        )

        assertFalse(baselineDeathHandler.shouldDie(feature))
        assertFalse(baselineDeathHandler.shouldDie(corpse))
    }

    @Test
    fun `NPC death drops loot into space and corpse`() {
        val itemRepository = seededItemRepository()
        val lootGenerator = LootGenerator(itemRepository)
        val lootDeathHandler = DeathHandler(lootGenerator)

        val npc = Entity.NPC(
            id = "goblin_looter",
            name = "Goblin Looter",
            description = "Carries trinkets.",
            health = 0,
            maxHealth = 30,
            lootTableId = "goblin_common",
            goldDrop = 8
        )

        val worldState = createWorldState(listOf(npc))
        val result = lootDeathHandler.handleDeath(npc.id, worldState)
        assertNotNull(result)
        assertTrue(result is DeathHandler.DeathResult.NPCDeath)

        val updatedWorld = (result as DeathHandler.DeathResult.NPCDeath).updatedWorld
        val space = updatedWorld.getSpace(TEST_SPACE_ID)
        assertNotNull(space)
        assertTrue(space.itemsDropped.isNotEmpty())
        assertTrue(space.itemsDropped.any { it.templateId == GOLD_TEMPLATE_ID })

        val lootEntities = updatedWorld.getEntitiesInSpace(TEST_SPACE_ID).filterIsInstance<Entity.Item>()
        assertTrue(lootEntities.isNotEmpty())
        assertTrue(lootEntities.any { it.properties["templateId"] == GOLD_TEMPLATE_ID })

        val corpse = updatedWorld.getEntitiesInSpace(TEST_SPACE_ID).filterIsInstance<Entity.Corpse>().firstOrNull()
        assertNotNull(corpse)
        assertTrue(corpse.contents.isNotEmpty())
        assertTrue(corpse.goldAmount >= 8)
    }

    private fun createWorldState(
        entities: List<Entity>,
        players: Map<PlayerId, PlayerState> = emptyMap()
    ): WorldState {
        val chunk = WorldChunkComponent(
            level = ChunkLevel.SUBZONE,
            parentId = null,
            children = emptyList(),
            lore = "Test chunk",
            biomeTheme = "Test biome",
            sizeEstimate = 1,
            mobDensity = 0.2,
            difficultyLevel = 1
        )
        val graphNode = GraphNodeComponent(
            id = TEST_SPACE_ID,
            type = NodeType.Linear,
            chunkId = TEST_CHUNK_ID
        )
        val space = SpacePropertiesComponent(
            name = "Test Space",
            description = "Test description",
            entities = entities.map { it.id }
        )
        val entityMap = entities.associateBy { it.id }

        return WorldState(
            graphNodes = mapOf(TEST_SPACE_ID to graphNode),
            spaces = mapOf(TEST_SPACE_ID to space),
            chunks = mapOf(TEST_CHUNK_ID to chunk),
            entities = entityMap,
            players = players
        )
    }

    private fun seededItemRepository(): ItemRepository {
        val templates = listOf(
            ItemTemplate(
                id = "rusty_sword",
                name = "Rusty Sword",
                type = ItemType.WEAPON,
                properties = mapOf("damage" to "5"),
                rarity = Rarity.COMMON,
                description = "An old blade."
            ),
            ItemTemplate(
                id = "leather_armor",
                name = "Leather Armor",
                type = ItemType.ARMOR,
                properties = mapOf("defense" to "3"),
                rarity = Rarity.COMMON,
                description = "Basic protection."
            ),
            ItemTemplate(
                id = "health_potion",
                name = "Health Potion",
                type = ItemType.CONSUMABLE,
                properties = mapOf("healing" to "15"),
                rarity = Rarity.COMMON,
                description = "Restores health."
            ),
            ItemTemplate(
                id = "iron_ore",
                name = "Iron Ore",
                type = ItemType.RESOURCE,
                properties = mapOf("weight" to "2"),
                rarity = Rarity.COMMON,
                description = "Useful for smithing."
            ),
            ItemTemplate(
                id = GOLD_TEMPLATE_ID,
                name = "Gold Coin",
                type = ItemType.RESOURCE,
                properties = mapOf("value" to "1"),
                rarity = Rarity.COMMON,
                description = "A shiny coin."
            )
        ).associateBy { it.id }

        return object : ItemRepository {
            override fun findTemplateById(templateId: String): Result<ItemTemplate?> =
                Result.success(templates[templateId])

            override fun findAllTemplates(): Result<Map<String, ItemTemplate>> = Result.success(templates)
            override fun findTemplatesByType(type: ItemType): Result<List<ItemTemplate>> =
                Result.success(templates.values.filter { it.type == type })

            override fun findTemplatesByRarity(rarity: Rarity): Result<List<ItemTemplate>> =
                Result.success(templates.values.filter { it.rarity == rarity })

            override fun saveTemplate(template: ItemTemplate): Result<Unit> = Result.success(Unit)
            override fun saveTemplates(templates: List<ItemTemplate>): Result<Unit> = Result.success(Unit)
            override fun deleteTemplate(templateId: String): Result<Unit> = Result.success(Unit)
            override fun findInstanceById(instanceId: String): Result<ItemInstance?> = Result.success(null)
            override fun findInstancesByTemplate(templateId: String): Result<List<ItemInstance>> = Result.success(emptyList())
            override fun saveInstance(instance: ItemInstance): Result<Unit> = Result.success(Unit)
            override fun deleteInstance(instanceId: String): Result<Unit> = Result.success(Unit)
            override fun findAllInstances(): Result<Map<String, ItemInstance>> = Result.success(emptyMap())
        }
    }

    companion object {
        private const val TEST_SPACE_ID = "space_1"
        private const val TEST_CHUNK_ID = "chunk_1"
    }
}
