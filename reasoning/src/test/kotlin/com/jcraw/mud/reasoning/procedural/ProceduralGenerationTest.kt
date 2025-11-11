package com.jcraw.mud.reasoning.procedural

import com.jcraw.mud.core.Direction
import com.jcraw.mud.core.ItemType
import com.jcraw.mud.core.getRoomViews
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for procedural generation components
 * Focus on behavior and contracts, not line coverage
 */
class ProceduralGenerationTest {

    // Fixed seed for deterministic tests
    private val testRandom = Random(42)

    @Test
    fun `RoomGenerator should create rooms with theme-appropriate traits`() {
        val generator = RoomGenerator(DungeonTheme.CRYPT, testRandom)
        val room = generator.generateRoom("test_room")

        // Room should have an ID, name, and traits from the theme
        assertEquals("test_room", room.id)
        assertTrue(room.name.isNotBlank(), "Room should have a name")
        assertTrue(room.traits.isNotEmpty(), "Room should have traits")

        // Traits should be from the crypt theme pool
        room.traits.forEach { trait ->
            assertTrue(
                DungeonTheme.CRYPT.roomTraitPool.contains(trait),
                "Trait '$trait' should be from CRYPT theme"
            )
        }
    }

    @Test
    fun `RoomGenerator should respect exit connections`() {
        val generator = RoomGenerator(DungeonTheme.CASTLE, testRandom)
        val exits = mapOf(
            Direction.NORTH to "room_1",
            Direction.SOUTH to "room_2"
        )

        val room = generator.generateRoom("test_room", exits = exits)

        assertEquals(exits, room.exits, "Room should preserve exit connections")
    }

    @Test
    fun `ItemGenerator should create weapons with damage bonuses`() {
        val generator = ItemGenerator(DungeonTheme.CAVE, testRandom)
        val weapon = generator.generateWeapon("test_weapon")

        assertEquals(ItemType.WEAPON, weapon.itemType)
        assertTrue(weapon.damageBonus > 0, "Weapon should have positive damage bonus")
        assertTrue(weapon.isUsable, "Weapon should be usable")
        assertTrue(weapon.isPickupable, "Weapon should be pickupable")
    }

    @Test
    fun `ItemGenerator should create armor with defense bonuses`() {
        val generator = ItemGenerator(DungeonTheme.TEMPLE, testRandom)
        val armor = generator.generateArmor("test_armor")

        assertEquals(ItemType.ARMOR, armor.itemType)
        assertTrue(armor.defenseBonus > 0, "Armor should have positive defense bonus")
        assertTrue(armor.isUsable, "Armor should be usable")
        assertTrue(armor.isPickupable, "Armor should be pickupable")
    }

    @Test
    fun `ItemGenerator should create consumables with heal amounts`() {
        val generator = ItemGenerator(DungeonTheme.CRYPT, testRandom)
        val consumable = generator.generateConsumable("test_potion")

        assertEquals(ItemType.CONSUMABLE, consumable.itemType)
        assertTrue(consumable.healAmount > 0, "Consumable should heal")
        assertTrue(consumable.isConsumable, "Should be marked as consumable")
        assertTrue(consumable.isUsable, "Consumable should be usable")
    }

    @Test
    fun `NPCGenerator should create hostile NPCs with combat stats`() {
        val generator = NPCGenerator(DungeonTheme.CRYPT, testRandom)
        val npc = generator.generateHostileNPC("test_enemy", powerLevel = 2)

        assertTrue(npc.isHostile, "NPC should be hostile")
        assertTrue(npc.health > 0, "NPC should have positive health")
        assertEquals(npc.health, npc.maxHealth, "NPC should start at full health")

        // Stats should be in reasonable range for power level 2
        assertTrue(npc.stats.strength >= 8, "Stats should be reasonable")
        assertTrue(npc.stats.strength <= 20, "Stats should not be excessive")
    }

    @Test
    fun `NPCGenerator should create friendly NPCs`() {
        val generator = NPCGenerator(DungeonTheme.CASTLE, testRandom)
        val npc = generator.generateFriendlyNPC("test_friend", powerLevel = 1)

        assertFalse(npc.isHostile, "NPC should not be hostile")
        assertTrue(npc.health > 0, "NPC should have positive health")
    }

    @Test
    fun `NPCGenerator boss should be more powerful than regular NPCs`() {
        val generator = NPCGenerator(DungeonTheme.CAVE, testRandom)
        val regularNPC = generator.generateHostileNPC("regular", powerLevel = 1)
        val boss = generator.generateBoss("boss")

        // Boss should have significantly more health
        assertTrue(boss.maxHealth > regularNPC.maxHealth * 1.5,
            "Boss should have more health than regular NPC")

        // Boss stats should be higher
        val bossAvgStat = (boss.stats.strength + boss.stats.dexterity +
                boss.stats.constitution + boss.stats.intelligence +
                boss.stats.wisdom + boss.stats.charisma) / 6.0

        val regularAvgStat = (regularNPC.stats.strength + regularNPC.stats.dexterity +
                regularNPC.stats.constitution + regularNPC.stats.intelligence +
                regularNPC.stats.wisdom + regularNPC.stats.charisma) / 6.0

        assertTrue(bossAvgStat > regularAvgStat,
            "Boss should have higher average stats than regular NPC")
    }

    @Test
    fun `DungeonLayoutGenerator should create connected graph`() {
        val generator = DungeonLayoutGenerator(testRandom)
        val layout = generator.generateLayout(roomCount = 8)

        assertEquals(8, layout.size, "Should generate requested number of rooms")

        // Should have exactly one entrance
        val entrances = layout.filter { it.isEntrance }
        assertEquals(1, entrances.size, "Should have exactly one entrance")

        // Should have exactly one boss room
        val bossRooms = layout.filter { it.isBoss }
        assertEquals(1, bossRooms.size, "Should have exactly one boss room")

        // All rooms should be reachable from entrance (graph connectivity test)
        val reachable = mutableSetOf<String>()
        val toVisit = mutableListOf(entrances.first().id)

        while (toVisit.isNotEmpty()) {
            val current = toVisit.removeAt(0)
            if (current in reachable) continue

            reachable.add(current)
            val node = layout.find { it.id == current }!!
            toVisit.addAll(node.connections.values)
        }

        assertEquals(layout.size, reachable.size,
            "All rooms should be reachable from entrance")
    }

    @Test
    fun `DungeonLayoutGenerator should create bidirectional connections`() {
        val generator = DungeonLayoutGenerator(testRandom)
        val layout = generator.generateLayout(roomCount = 6)

        // Every connection should be bidirectional
        layout.forEach { node ->
            node.connections.forEach { (direction, targetId) ->
                val targetNode = layout.find { it.id == targetId }
                assertNotNull(targetNode, "Connected room should exist")

                // Target should have reverse connection
                val hasReverseConnection = targetNode.connections.values.contains(node.id)
                assertTrue(hasReverseConnection,
                    "Connection from ${node.id} to $targetId should be bidirectional")
            }
        }
    }

    @Test
    fun `ProceduralDungeonBuilder should create complete WorldState`() {
        val config = DungeonConfig(
            theme = DungeonTheme.CRYPT,
            roomCount = 5,
            seed = 12345L
        )
        val builder = ProceduralDungeonBuilder(config)
        val worldState = builder.generateDungeon()

        // Should have 5 rooms
        assertEquals(5, worldState.getRoomViews().size, "Should have requested number of rooms")

        // Player should exist and be in a valid room
        assertNotNull(worldState.player, "Player should exist")
        assertTrue(worldState.getRoomViews().containsKey(worldState.player.currentRoomId),
            "Player should be in a valid room")

        // Entrance room should exist
        val entranceRoom = worldState.getRoomViews()[worldState.player.currentRoomId]
        assertNotNull(entranceRoom, "Entrance room should exist")

        // Boss room should exist
        val bossRoom = worldState.getRoomViews().values.find { room ->
            room.entities.any { entity ->
                entity is com.jcraw.mud.core.Entity.NPC &&
                        entity.isHostile &&
                        entity.maxHealth >= 80  // Boss health threshold
            }
        }
        assertNotNull(bossRoom, "Boss room should exist with powerful NPC")
    }

    @Test
    fun `ProceduralDungeonBuilder with seed should be deterministic`() {
        val seed = 99999L

        val world1 = ProceduralDungeonBuilder(
            DungeonConfig(theme = DungeonTheme.CASTLE, roomCount = 6, seed = seed)
        ).generateDungeon()

        val world2 = ProceduralDungeonBuilder(
            DungeonConfig(theme = DungeonTheme.CASTLE, roomCount = 6, seed = seed)
        ).generateDungeon()

        // Same seed should produce identical layouts
        assertEquals(
            expected = world1.spaces.size,
            actual = world2.spaces.size,
            message = "Same seed should produce same number of rooms"
        )

        // Room IDs should match
        assertEquals(
            expected = world1.spaces.keys,
            actual = world2.spaces.keys,
            message = "Same seed should produce same room IDs"
        )

        // Room names should match
        world1.spaces.keys.forEach { spaceId ->
            assertEquals(
                expected = world1.spaces[spaceId]?.name,
                actual = world2.spaces[spaceId]?.name,
                message = "Room names should match for same seed"
            )
        }
    }

    @Test
    fun `ProceduralDungeonBuilder should distribute loot across dungeon`() {
        val config = DungeonConfig(
            theme = DungeonTheme.CAVE,
            roomCount = 10,
            seed = 777L
        )
        val builder = ProceduralDungeonBuilder(config)
        val worldState = builder.generateDungeon()

        // Count total items in dungeon
        val totalItems = worldState.getRoomViews().values.sumOf { room ->
            room.entities.count { it is com.jcraw.mud.core.Entity.Item }
        }

        // Should have at least a few items distributed across the dungeon
        assertTrue(totalItems >= 3, "Dungeon should have multiple items")

        // Should have at least one weapon
        val hasWeapon = worldState.getRoomViews().values.any { room ->
            room.entities.any { entity ->
                entity is com.jcraw.mud.core.Entity.Item &&
                        entity.itemType == ItemType.WEAPON
            }
        }
        assertTrue(hasWeapon, "Dungeon should contain at least one weapon")
    }
}
