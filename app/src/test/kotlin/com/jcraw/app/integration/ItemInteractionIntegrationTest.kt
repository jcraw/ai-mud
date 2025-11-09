package com.jcraw.app.integration

import com.jcraw.mud.core.*
import com.jcraw.mud.testbot.InMemoryGameEngine
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for item interaction system
 *
 * Tests the full item workflow including:
 * - Taking items (single and "take all")
 * - Dropping items
 * - Equipping weapons and armor
 * - Using consumables
 * - Inventory management
 */
class ItemInteractionIntegrationTest {

    @Test
    fun `player can take a single item from room`() = runBlocking {
        val item = Entity.Item(
            id = "sword",
            name = "Iron Sword",
            description = "A sturdy blade",
            isPickupable = true,
            itemType = ItemType.WEAPON,
            damageBonus = 3
        )

        val world = createTestWorld(items = listOf(item))
        val engine = InMemoryGameEngine(world)

        // Take the item
        val response = engine.processInput("take sword")
        assertTrue(response.contains("take", ignoreCase = true) || response.contains("pick", ignoreCase = true))

        // Item should be in inventory
        val inventory = engine.getWorldState().player.inventory
        assertTrue(inventory.any { it.id == "sword" }, "Sword should be in player inventory")

        // Item should be removed from room
        val room = engine.getWorldState().getCurrentRoomView()!!
        assertNull(room.getEntity("sword"), "Sword should be removed from room")
    }

    @Test
    fun `player can take all items from room at once`() = runBlocking {
        val items = listOf(
            Entity.Item(
                id = "sword",
                name = "Iron Sword",
                description = "A sturdy blade",
                isPickupable = true,
                itemType = ItemType.WEAPON
            ),
            Entity.Item(
                id = "potion",
                name = "Health Potion",
                description = "A healing elixir",
                isPickupable = true,
                itemType = ItemType.CONSUMABLE,
                healAmount = 20
            ),
            Entity.Item(
                id = "coin",
                name = "Gold Coin",
                description = "Shiny gold",
                isPickupable = true,
                itemType = ItemType.MISC
            )
        )

        val world = createTestWorld(items = items)
        val engine = InMemoryGameEngine(world)

        // Take all items
        val response = engine.processInput("take all")
        assertTrue(response.contains("take", ignoreCase = true) || response.contains("pick", ignoreCase = true))

        // All items should be in inventory
        val inventory = engine.getWorldState().player.inventory
        assertEquals(3, inventory.size, "All 3 items should be in inventory")
        assertTrue(inventory.any { it.id == "sword" })
        assertTrue(inventory.any { it.id == "potion" })
        assertTrue(inventory.any { it.id == "coin" })

        // Room should be empty of items
        val room = engine.getWorldState().getCurrentRoomView()!!
        assertTrue(room.entities.filterIsInstance<Entity.Item>().isEmpty(), "Room should have no items left")
    }

    @Test
    fun `player can drop items from inventory`() = runBlocking {
        val item = Entity.Item(
            id = "rock",
            name = "Heavy Rock",
            description = "A cumbersome stone",
            isPickupable = true,
            itemType = ItemType.MISC
        )

        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            inventory = listOf(item)
        )

        val world = createTestWorld(player = player)
        val engine = InMemoryGameEngine(world)

        // Player starts with rock in inventory
        assertTrue(engine.getWorldState().player.inventory.any { it.id == "rock" })

        // Drop the rock
        val response = engine.processInput("drop rock")
        assertTrue(response.contains("drop", ignoreCase = true))

        // Item should be removed from inventory
        val inventory = engine.getWorldState().player.inventory
        assertTrue(inventory.none { it.id == "rock" }, "Rock should be removed from inventory")

        // Item should be in the room
        val room = engine.getWorldState().getCurrentRoomView()!!
        assertNotNull(room.getEntity("rock"), "Rock should be in the room")
    }

    @Test
    fun `player can drop equipped weapon`() = runBlocking {
        val weapon = Entity.Item(
            id = "axe",
            name = "Battle Axe",
            description = "A fearsome weapon",
            isPickupable = true,
            itemType = ItemType.WEAPON,
            damageBonus = 6
        )

        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            inventory = listOf(weapon),
            equippedWeapon = weapon
        )

        val world = createTestWorld(player = player)
        val engine = InMemoryGameEngine(world)

        // Player starts with weapon equipped
        assertNotNull(engine.getWorldState().player.equippedWeapon)

        // Drop the weapon
        val response = engine.processInput("drop axe")
        assertTrue(response.contains("drop", ignoreCase = true))

        // Weapon should be unequipped and removed from inventory
        val updatedPlayer = engine.getWorldState().player
        assertNull(updatedPlayer.equippedWeapon, "Weapon should be unequipped")
        assertTrue(updatedPlayer.inventory.none { it.id == "axe" }, "Axe should be removed from inventory")

        // Weapon should be in room
        val room = engine.getWorldState().getCurrentRoomView()!!
        assertNotNull(room.getEntity("axe"), "Axe should be in the room")
    }

    @Test
    fun `player can equip weapon from inventory`() = runBlocking {
        val weapon = Entity.Item(
            id = "dagger",
            name = "Steel Dagger",
            description = "A sharp knife",
            isPickupable = true,
            itemType = ItemType.WEAPON,
            damageBonus = 2
        )

        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            inventory = listOf(weapon)
        )

        val world = createTestWorld(player = player)
        val engine = InMemoryGameEngine(world)

        // Player starts with no weapon equipped
        assertNull(engine.getWorldState().player.equippedWeapon)

        // Equip the dagger
        val response = engine.processInput("equip dagger")
        assertTrue(response.contains("equip", ignoreCase = true) || response.contains("wield", ignoreCase = true))

        // Weapon should be equipped
        val equippedWeapon = engine.getWorldState().player.equippedWeapon
        assertNotNull(equippedWeapon, "Weapon should be equipped")
        assertEquals("dagger", equippedWeapon.id)
        assertEquals(2, engine.getWorldState().player.getWeaponDamageBonus())
    }

    @Test
    fun `player can equip armor from inventory`() = runBlocking {
        val armor = Entity.Item(
            id = "shield",
            name = "Wooden Shield",
            description = "A basic shield",
            isPickupable = true,
            itemType = ItemType.ARMOR,
            defenseBonus = 2
        )

        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            inventory = listOf(armor)
        )

        val world = createTestWorld(player = player)
        val engine = InMemoryGameEngine(world)

        // Player starts with no armor equipped
        assertNull(engine.getWorldState().player.equippedArmor)

        // Equip the shield
        val response = engine.processInput("equip shield")
        assertTrue(response.contains("equip", ignoreCase = true) || response.contains("wear", ignoreCase = true))

        // Armor should be equipped
        val equippedArmor = engine.getWorldState().player.equippedArmor
        assertNotNull(equippedArmor, "Armor should be equipped")
        assertEquals("shield", equippedArmor.id)
        assertEquals(2, engine.getWorldState().player.getArmorDefenseBonus())
    }

    @Test
    fun `equipping new weapon replaces old weapon`() = runBlocking {
        val weapon1 = Entity.Item(
            id = "sword",
            name = "Iron Sword",
            description = "A basic sword",
            isPickupable = true,
            itemType = ItemType.WEAPON,
            damageBonus = 3
        )

        val weapon2 = Entity.Item(
            id = "greatsword",
            name = "Great Sword",
            description = "A powerful blade",
            isPickupable = true,
            itemType = ItemType.WEAPON,
            damageBonus = 7
        )

        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            inventory = listOf(weapon1, weapon2),
            equippedWeapon = weapon1
        )

        val world = createTestWorld(player = player)
        val engine = InMemoryGameEngine(world)

        // Player starts with iron sword equipped
        assertEquals("sword", engine.getWorldState().player.equippedWeapon?.id)
        assertEquals(3, engine.getWorldState().player.getWeaponDamageBonus())

        // Equip the greatsword
        val response = engine.processInput("equip greatsword")
        assertTrue(response.contains("equip", ignoreCase = true))

        // Greatsword should be equipped, replacing iron sword
        val equippedWeapon = engine.getWorldState().player.equippedWeapon
        assertNotNull(equippedWeapon)
        assertEquals("greatsword", equippedWeapon.id)
        assertEquals(7, engine.getWorldState().player.getWeaponDamageBonus())

        // Both weapons should still be in inventory
        val inventory = engine.getWorldState().player.inventory
        assertEquals(2, inventory.size)
    }

    @Test
    fun `player can use healing potion from inventory`() = runBlocking {
        val potion = Entity.Item(
            id = "potion",
            name = "Health Potion",
            description = "Restores health",
            isPickupable = true,
            itemType = ItemType.CONSUMABLE,
            healAmount = 30
        )

        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            inventory = listOf(potion),
            health = 50,  // Damaged
            maxHealth = 100
        )

        val world = createTestWorld(player = player)
        val engine = InMemoryGameEngine(world)

        // Player starts damaged
        assertEquals(50, engine.getWorldState().player.health)

        // Use the potion
        val response = engine.processInput("use potion")
        assertTrue(response.contains("use", ignoreCase = true) ||
                   response.contains("health", ignoreCase = true) ||
                   response.contains("heal", ignoreCase = true))

        // Player health should increase (up to 80, or max 100)
        val newHealth = engine.getWorldState().player.health
        assertTrue(newHealth > 50, "Health should increase after using potion (was: $newHealth)")
        assertTrue(newHealth <= 100, "Health should not exceed max health")

        // Potion should be consumed (removed from inventory)
        val inventory = engine.getWorldState().player.inventory
        assertTrue(inventory.none { it.id == "potion" }, "Consumed potion should be removed from inventory")
    }

    @Test
    fun `healing potion cannot exceed max health`() = runBlocking {
        val potion = Entity.Item(
            id = "mega_potion",
            name = "Mega Health Potion",
            description = "Restores massive health",
            isPickupable = true,
            itemType = ItemType.CONSUMABLE,
            healAmount = 1000  // Way more than max health
        )

        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            inventory = listOf(potion),
            health = 90,  // Almost full
            maxHealth = 100
        )

        val world = createTestWorld(player = player)
        val engine = InMemoryGameEngine(world)

        // Use the potion
        engine.processInput("use mega_potion")

        // Health should be capped at max health
        assertEquals(100, engine.getWorldState().player.health, "Health should be capped at max health")
    }

    @Test
    fun `player can use consumable during combat`() = runBlocking {
        val potion = Entity.Item(
            id = "combat_potion",
            name = "Combat Potion",
            description = "Quick healing",
            isPickupable = true,
            itemType = ItemType.CONSUMABLE,
            healAmount = 25
        )

        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            inventory = listOf(potion),
            health = 40,
            maxHealth = 100
        )

        val npc = Entity.NPC(
            id = "enemy",
            name = "Bandit",
            description = "A dangerous foe",
            isHostile = true,
            health = 50,
            maxHealth = 50
        )

        val world = createTestWorld(player = player, npc = npc)
        val engine = InMemoryGameEngine(world)

        // Start combat
        engine.processInput("attack bandit")
        assertTrue(engine.getWorldState().player.isInCombat())

        val healthBefore = engine.getWorldState().player.health

        // Use potion during combat
        val response = engine.processInput("use combat_potion")
        assertTrue(response.contains("potion", ignoreCase = true) || response.contains("heal", ignoreCase = true))

        // Health should increase
        val healthAfter = engine.getWorldState().player.health
        assertTrue(healthAfter > healthBefore, "Health should increase after using potion in combat")

        // Potion should be consumed
        assertTrue(engine.getWorldState().player.inventory.none { it.id == "combat_potion" })
    }

    @Test
    fun `cannot take non-pickupable items`() = runBlocking {
        val fixture = Entity.Item(
            id = "altar",
            name = "Ancient Altar",
            description = "A massive stone altar",
            isPickupable = false,  // Cannot be taken
            itemType = ItemType.MISC
        )

        val world = createTestWorld(items = listOf(fixture))
        val engine = InMemoryGameEngine(world)

        // Try to take the altar
        val response = engine.processInput("take altar")

        // Item should still be in room
        val room = engine.getWorldState().getCurrentRoomView()!!
        assertNotNull(room.getEntity("altar"), "Non-pickupable item should remain in room")

        // Item should not be in inventory
        assertTrue(engine.getWorldState().player.inventory.isEmpty(), "Non-pickupable item should not be in inventory")
    }

    @Test
    fun `inventory command shows all items`() = runBlocking {
        val items = listOf(
            Entity.Item(
                id = "sword",
                name = "Iron Sword",
                description = "A sword",
                isPickupable = true,
                itemType = ItemType.WEAPON
            ),
            Entity.Item(
                id = "potion",
                name = "Health Potion",
                description = "A potion",
                isPickupable = true,
                itemType = ItemType.CONSUMABLE
            )
        )

        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            inventory = items
        )

        val world = createTestWorld(player = player)
        val engine = InMemoryGameEngine(world)

        // Check inventory
        val response = engine.processInput("inventory")

        // Response should mention both items
        assertTrue(response.contains("sword", ignoreCase = true) || response.contains("iron", ignoreCase = true))
        assertTrue(response.contains("potion", ignoreCase = true) || response.contains("health", ignoreCase = true))
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
                chunkId = "test_chunk",
                lore = "Test room chunk",
                biomeTheme = "test"
            )
        )
    }
}
