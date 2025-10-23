package com.jcraw.app.integration

import com.jcraw.mud.core.*
import com.jcraw.mud.reasoning.procedural.*
import com.jcraw.mud.testbot.InMemoryGameEngine
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/**
 * Integration tests for procedural dungeon generation
 *
 * Tests the full procedural generation workflow including:
 * - All 4 dungeon themes (Crypt, Castle, Cave, Temple)
 * - Room connectivity and navigation
 * - NPC generation (boss, hostile, friendly)
 * - Item distribution (weapons, armor, consumables, treasures)
 * - Quest generation based on dungeon state
 */
class ProceduralDungeonIntegrationTest {

    // ========== Theme Generation Tests ==========

    @Test
    fun `crypt theme generates complete dungeon`() {
            val config = DungeonConfig(
                theme = DungeonTheme.CRYPT,
                roomCount = 8,
                seed = 1000L
            )
            val world = ProceduralDungeonBuilder(config).generateDungeon()

            // Basic structure
            assertEquals(8, world.rooms.size, "Should generate requested number of rooms")
            assertNotNull(world.player, "Player should exist")
            assertTrue(world.rooms.containsKey(world.player.currentRoomId),
                "Player should be in a valid room")

            // Theme verification - rooms should have crypt-appropriate traits
            val roomTraits = world.rooms.values.flatMap { it.traits }
            val hasCryptTraits = roomTraits.any { trait ->
                DungeonTheme.CRYPT.roomTraitPool.contains(trait)
            }
            assertTrue(hasCryptTraits, "Rooms should have crypt-themed traits")
        }

        @Test
        fun `castle theme generates complete dungeon`() {
            val config = DungeonConfig(
                theme = DungeonTheme.CASTLE,
                roomCount = 10,
                seed = 2000L
            )
            val world = ProceduralDungeonBuilder(config).generateDungeon()

            assertEquals(10, world.rooms.size)
            assertNotNull(world.player)

            // Theme verification
            val roomTraits = world.rooms.values.flatMap { it.traits }
            val hasCastleTraits = roomTraits.any { trait ->
                DungeonTheme.CASTLE.roomTraitPool.contains(trait)
            }
            assertTrue(hasCastleTraits, "Rooms should have castle-themed traits")
        }

        @Test
        fun `cave theme generates complete dungeon`() {
            val config = DungeonConfig(
                theme = DungeonTheme.CAVE,
                roomCount = 6,
                seed = 3000L
            )
            val world = ProceduralDungeonBuilder(config).generateDungeon()

            assertEquals(6, world.rooms.size)
            assertNotNull(world.player)

            // Theme verification
            val roomTraits = world.rooms.values.flatMap { it.traits }
            val hasCaveTraits = roomTraits.any { trait ->
                DungeonTheme.CAVE.roomTraitPool.contains(trait)
            }
            assertTrue(hasCaveTraits, "Rooms should have cave-themed traits")
        }

        @Test
        fun `temple theme generates complete dungeon`() {
            val config = DungeonConfig(
                theme = DungeonTheme.TEMPLE,
                roomCount = 7,
                seed = 4000L
            )
            val world = ProceduralDungeonBuilder(config).generateDungeon()

            assertEquals(7, world.rooms.size)
            assertNotNull(world.player)

            // Theme verification
            val roomTraits = world.rooms.values.flatMap { it.traits }
            val hasTempleTraits = roomTraits.any { trait ->
                DungeonTheme.TEMPLE.roomTraitPool.contains(trait)
            }
            assertTrue(hasTempleTraits, "Rooms should have temple-themed traits")
        }

    // ========== Room Connectivity Tests ==========

    @Test
    fun `all rooms are reachable from entrance`() {
            val config = DungeonConfig(
                theme = DungeonTheme.CRYPT,
                roomCount = 12,
                seed = 5000L
            )
            val world = ProceduralDungeonBuilder(config).generateDungeon()

            // Player starts at entrance
            val entranceId = world.player.currentRoomId

            // BFS to find all reachable rooms
            val reachable = mutableSetOf<String>()
            val toVisit = mutableListOf(entranceId)

            while (toVisit.isNotEmpty()) {
                val currentId = toVisit.removeAt(0)
                if (currentId in reachable) continue

                reachable.add(currentId)
                val room = world.rooms[currentId]
                assertNotNull(room, "Room $currentId should exist")

                // Add all connected rooms to visit list
                toVisit.addAll(room.exits.values)
            }

            assertEquals(world.rooms.size, reachable.size,
                "All rooms should be reachable from entrance")
        }

        @Test
        fun `room connections are bidirectional`() {
            val config = DungeonConfig(
                theme = DungeonTheme.CASTLE,
                roomCount = 8,
                seed = 6000L
            )
            val world = ProceduralDungeonBuilder(config).generateDungeon()

            // Check every room's exits
            world.rooms.values.forEach { room ->
                room.exits.forEach { (direction, targetId) ->
                    val targetRoom = world.rooms[targetId]
                    assertNotNull(targetRoom, "Exit target room should exist")

                    // Target room should have a reverse connection back
                    val hasReverseConnection = targetRoom.exits.values.contains(room.id)
                    assertTrue(hasReverseConnection,
                        "Connection from ${room.name} to ${targetRoom.name} should be bidirectional")
                }
            }
        }

        @Test
        fun `can navigate between rooms in game`() = runBlocking {
            val config = DungeonConfig(
                theme = DungeonTheme.CAVE,
                roomCount = 5,
                seed = 7000L
            )
            val world = ProceduralDungeonBuilder(config).generateDungeon()
            val engine = InMemoryGameEngine(world)

            // Look at current room
            val initialResponse = engine.processInput("look")
            assertTrue(initialResponse.isNotBlank(), "Should describe current room")

            // Try to move in a valid direction
            val currentRoom = world.getCurrentRoom()
            assertNotNull(currentRoom)

            if (currentRoom.exits.isNotEmpty()) {
                val (direction, targetId) = currentRoom.exits.entries.first()
                val directionCmd = direction.name.lowercase()

                val moveResponse = engine.processInput(directionCmd)
                val newState = engine.getWorldState()

                assertEquals(targetId, newState.player.currentRoomId,
                    "Player should move to target room")
            }
        }

    // ========== NPC Generation Tests ==========

    @Test
    fun `dungeon contains at least one boss NPC`() {
            val config = DungeonConfig(
                theme = DungeonTheme.CRYPT,
                roomCount = 10,
                seed = 8000L
            )
            val world = ProceduralDungeonBuilder(config).generateDungeon()

            // Find boss NPCs (characterized by high health and hostile)
            val allNpcs = world.rooms.values.flatMap { room ->
                room.entities.filterIsInstance<Entity.NPC>()
            }

            val bossNpcs = allNpcs.filter { npc ->
                npc.isHostile && npc.maxHealth >= 80  // Boss health threshold
            }

            assertTrue(bossNpcs.isNotEmpty(), "Dungeon should contain at least one boss NPC")
        }

        @Test
        fun `hostile NPCs can be fought in combat`() = runBlocking {
            val config = DungeonConfig(
                theme = DungeonTheme.CASTLE,
                roomCount = 8,
                seed = 9000L
            )
            val world = ProceduralDungeonBuilder(config).generateDungeon()
            val engine = InMemoryGameEngine(world)

            // Find a room with a hostile NPC
            val roomWithHostile = world.rooms.values.find { room ->
                room.entities.any { entity ->
                    entity is Entity.NPC && entity.isHostile
                }
            }

            if (roomWithHostile != null) {
                // Navigate to that room (we'll just set the player there directly)
                val hostileNpc = roomWithHostile.entities
                    .filterIsInstance<Entity.NPC>()
                    .first { it.isHostile }

                // Move player to the room with hostile NPC
                var currentWorld = engine.getWorldState()
                while (currentWorld.player.currentRoomId != roomWithHostile.id) {
                    val currentRoom = currentWorld.getCurrentRoom()!!
                    if (currentRoom.exits.isEmpty()) break

                    val (direction, _) = currentRoom.exits.entries.first()
                    engine.processInput(direction.name.lowercase())
                    currentWorld = engine.getWorldState()

                    if (currentWorld.player.currentRoomId == roomWithHostile.id) break
                }

                // Try to attack the hostile NPC
                val attackResponse = engine.processInput("attack ${hostileNpc.name}")

                // Combat should start
                val afterAttack = engine.getWorldState()
                assertTrue(afterAttack.player.isInCombat() || attackResponse.contains("attack"),
                    "Should be able to initiate combat with hostile NPC")
            }
        }

        @Test
        fun `friendly NPCs can be talked to`() = runBlocking {
            val config = DungeonConfig(
                theme = DungeonTheme.TEMPLE,
                roomCount = 10,
                seed = 10000L
            )
            val world = ProceduralDungeonBuilder(config).generateDungeon()
            val engine = InMemoryGameEngine(world)

            // Find a room with a friendly NPC
            val roomWithFriendly = world.rooms.values.find { room ->
                room.entities.any { entity ->
                    entity is Entity.NPC && !entity.isHostile
                }
            }

            if (roomWithFriendly != null) {
                val friendlyNpc = roomWithFriendly.entities
                    .filterIsInstance<Entity.NPC>()
                    .first { !it.isHostile }

                // Move player to the room with friendly NPC
                var currentWorld = engine.getWorldState()
                while (currentWorld.player.currentRoomId != roomWithFriendly.id) {
                    val currentRoom = currentWorld.getCurrentRoom()!!
                    if (currentRoom.exits.isEmpty()) break

                    val (direction, _) = currentRoom.exits.entries.first()
                    engine.processInput(direction.name.lowercase())
                    currentWorld = engine.getWorldState()

                    if (currentWorld.player.currentRoomId == roomWithFriendly.id) break
                }

                // Try to talk to the friendly NPC
                val talkResponse = engine.processInput("talk ${friendlyNpc.name}")

                assertTrue(talkResponse.isNotBlank(),
                    "Should be able to talk to friendly NPC")
            }
        }

    // ========== Item Distribution Tests ==========

    @Test
    fun `dungeon contains weapons`() {
            val config = DungeonConfig(
                theme = DungeonTheme.CRYPT,
                roomCount = 10,
                seed = 11000L
            )
            val world = ProceduralDungeonBuilder(config).generateDungeon()

            val allItems = world.rooms.values.flatMap { room ->
                room.entities.filterIsInstance<Entity.Item>()
            }

            val weapons = allItems.filter { it.itemType == ItemType.WEAPON }
            assertTrue(weapons.isNotEmpty(), "Dungeon should contain at least one weapon")

            // Weapons should have damage bonuses
            weapons.forEach { weapon ->
                assertTrue(weapon.damageBonus > 0,
                    "${weapon.name} should have positive damage bonus")
            }
        }

        @Test
        fun `dungeon contains armor`() {
            val config = DungeonConfig(
                theme = DungeonTheme.CASTLE,
                roomCount = 10,
                seed = 12000L
            )
            val world = ProceduralDungeonBuilder(config).generateDungeon()

            val allItems = world.rooms.values.flatMap { room ->
                room.entities.filterIsInstance<Entity.Item>()
            }

            val armor = allItems.filter { it.itemType == ItemType.ARMOR }
            assertTrue(armor.isNotEmpty(), "Dungeon should contain at least one armor piece")

            // Armor should have defense bonuses
            armor.forEach { armorItem ->
                assertTrue(armorItem.defenseBonus > 0,
                    "${armorItem.name} should have positive defense bonus")
            }
        }

        @Test
        fun `dungeon contains consumables`() {
            val config = DungeonConfig(
                theme = DungeonTheme.CAVE,
                roomCount = 10,
                seed = 13000L
            )
            val world = ProceduralDungeonBuilder(config).generateDungeon()

            val allItems = world.rooms.values.flatMap { room ->
                room.entities.filterIsInstance<Entity.Item>()
            }

            val consumables = allItems.filter { it.itemType == ItemType.CONSUMABLE }
            assertTrue(consumables.isNotEmpty(),
                "Dungeon should contain at least one consumable")

            // Consumables should have heal amounts
            consumables.forEach { consumable ->
                assertTrue(consumable.healAmount > 0,
                    "${consumable.name} should have positive heal amount")
            }
        }

        @Test
        fun `items can be picked up and used in game`() = runBlocking {
            val config = DungeonConfig(
                theme = DungeonTheme.TEMPLE,
                roomCount = 8,
                seed = 14000L
            )
            val world = ProceduralDungeonBuilder(config).generateDungeon()
            val engine = InMemoryGameEngine(world)

            // Find a room with a pickupable item
            val roomWithItem = world.rooms.values.find { room ->
                room.entities.any { entity ->
                    entity is Entity.Item && entity.isPickupable
                }
            }

            if (roomWithItem != null) {
                val item = roomWithItem.entities
                    .filterIsInstance<Entity.Item>()
                    .first { it.isPickupable }

                // Move player to that room
                var currentWorld = engine.getWorldState()
                while (currentWorld.player.currentRoomId != roomWithItem.id) {
                    val currentRoom = currentWorld.getCurrentRoom()!!
                    if (currentRoom.exits.isEmpty()) break

                    val (direction, _) = currentRoom.exits.entries.first()
                    engine.processInput(direction.name.lowercase())
                    currentWorld = engine.getWorldState()

                    if (currentWorld.player.currentRoomId == roomWithItem.id) break
                }

                // Try to pick up the item
                engine.processInput("take ${item.name}")
                val afterPickup = engine.getWorldState()

                assertTrue(afterPickup.player.inventory.any { it.id == item.id },
                    "Item should be in player inventory after pickup")
            }
        }

        @Test
        fun `items are distributed across multiple rooms`() {
            val config = DungeonConfig(
                theme = DungeonTheme.CRYPT,
                roomCount = 12,
                seed = 15000L
            )
            val world = ProceduralDungeonBuilder(config).generateDungeon()

            // Count rooms with items
            val roomsWithItems = world.rooms.values.count { room ->
                room.entities.any { it is Entity.Item }
            }

            assertTrue(roomsWithItems >= 2,
                "Items should be distributed across multiple rooms")
        }

    // ========== Quest Generation Tests ==========

    @Test
    fun `can generate quests for crypt dungeon`() {
            val config = DungeonConfig(
                theme = DungeonTheme.CRYPT,
                roomCount = 10,
                seed = 16000L
            )
            val world = ProceduralDungeonBuilder(config).generateDungeon()
            val questGen = QuestGenerator(seed = 16000L)

            val quests = questGen.generateQuestPool(world, DungeonTheme.CRYPT, count = 3)

            assertEquals(3, quests.size, "Should generate requested number of quests")
            quests.forEach { quest ->
                assertTrue(quest.objectives.isNotEmpty(),
                    "Quest ${quest.title} should have at least one objective")
                assertNotNull(quest.reward, "Quest should have a reward")
            }
        }

        @Test
        fun `can generate kill quests when hostile NPCs exist`() {
            val config = DungeonConfig(
                theme = DungeonTheme.CASTLE,
                roomCount = 10,
                seed = 17000L
            )
            val world = ProceduralDungeonBuilder(config).generateDungeon()

            // Verify hostile NPCs exist
            val hostileNpcs = world.rooms.values.flatMap { room ->
                room.entities.filterIsInstance<Entity.NPC>().filter { it.isHostile }
            }

            if (hostileNpcs.isNotEmpty()) {
                val questGen = QuestGenerator(seed = 17000L)
                val quests = questGen.generateQuestPool(world, DungeonTheme.CASTLE, count = 5)

                // Should have at least some kill quests
                val killQuests = quests.filter { quest ->
                    quest.objectives.any { it is QuestObjective.KillEnemy }
                }

                assertTrue(killQuests.isNotEmpty(),
                    "Should generate kill quests when hostile NPCs exist")
            }
        }

        @Test
        fun `can generate collect quests when items exist`() {
            val config = DungeonConfig(
                theme = DungeonTheme.CAVE,
                roomCount = 10,
                seed = 18000L
            )
            val world = ProceduralDungeonBuilder(config).generateDungeon()

            // Verify items exist
            val items = world.rooms.values.flatMap { room ->
                room.entities.filterIsInstance<Entity.Item>()
                    .filter { it.isPickupable && it.itemType != ItemType.CONSUMABLE }
            }

            if (items.isNotEmpty()) {
                val questGen = QuestGenerator(seed = 18000L)
                val quests = questGen.generateQuestPool(world, DungeonTheme.CAVE, count = 5)

                // Should have at least some collect quests
                val collectQuests = quests.filter { quest ->
                    quest.objectives.any { it is QuestObjective.CollectItem }
                }

                assertTrue(collectQuests.isNotEmpty(),
                    "Should generate collect quests when items exist")
            }
        }

        @Test
        fun `can generate explore quests for all dungeons`() {
            val config = DungeonConfig(
                theme = DungeonTheme.TEMPLE,
                roomCount = 8,
                seed = 19000L
            )
            val world = ProceduralDungeonBuilder(config).generateDungeon()
            val questGen = QuestGenerator(seed = 19000L)

            // Explore quests should always be possible (rooms always exist)
            val quests = questGen.generateQuestPool(world, DungeonTheme.TEMPLE, count = 5)

            val exploreQuests = quests.filter { quest ->
                quest.objectives.any { it is QuestObjective.ExploreRoom }
            }

            assertTrue(exploreQuests.isNotEmpty(),
                "Should be able to generate explore quests for any dungeon")
        }

    // ========== Dungeon Determinism Tests ==========

    @Test
    fun `same seed generates identical dungeon layouts`() {
            val seed = 99999L
            val config = DungeonConfig(
                theme = DungeonTheme.CRYPT,
                roomCount = 8,
                seed = seed
            )

            val world1 = ProceduralDungeonBuilder(config).generateDungeon()
            val world2 = ProceduralDungeonBuilder(config).generateDungeon()

            // Same number of rooms
            assertEquals(world1.rooms.size, world2.rooms.size,
                "Same seed should generate same number of rooms")

            // Same room IDs
            assertEquals(world1.rooms.keys, world2.rooms.keys,
                "Same seed should generate same room IDs")

            // Same room names
            world1.rooms.keys.forEach { roomId ->
                assertEquals(world1.rooms[roomId]?.name, world2.rooms[roomId]?.name,
                    "Room names should match for same seed")
            }

            // Same room connections
            world1.rooms.keys.forEach { roomId ->
                assertEquals(world1.rooms[roomId]?.exits, world2.rooms[roomId]?.exits,
                    "Room exits should match for same seed")
            }
        }

        @Test
        fun `different seeds generate different dungeons`() {
            val config1 = DungeonConfig(
                theme = DungeonTheme.CASTLE,
                roomCount = 8,
                seed = 1111L
            )

            val config2 = DungeonConfig(
                theme = DungeonTheme.CASTLE,
                roomCount = 8,
                seed = 2222L
            )

            val world1 = ProceduralDungeonBuilder(config1).generateDungeon()
            val world2 = ProceduralDungeonBuilder(config2).generateDungeon()

            // Different room names (high probability)
            val allRoomNames1 = world1.rooms.values.map { it.name }.toSet()
            val allRoomNames2 = world2.rooms.values.map { it.name }.toSet()

            // At least some room names should differ
            val differentNames = allRoomNames1 != allRoomNames2
            assertTrue(differentNames,
                "Different seeds should likely generate different room names")
        }
}
