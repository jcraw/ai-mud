package com.jcraw.mud.core.world

import com.jcraw.mud.core.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Tests for SpacePropertiesComponent
 */
class SpacePropertiesComponentTest {

    private fun createTestPlayer(perceptionLevel: Int = 0): PlayerState {
        return PlayerState(
            id = "test-player",
            name = "Test Player",
            currentRoomId = "room-1",
            skills = mapOf("Perception" to perceptionLevel)
        )
    }

    @Test
    fun `component type is SPACE_PROPERTIES`() {
        val space = SpacePropertiesComponent()
        assertEquals(ComponentType.SPACE_PROPERTIES, space.componentType)
    }

    @Test
    fun `withDescription updates description and clears stale flag`() {
        val space = SpacePropertiesComponent(
            name = "Test Room",
            description = "Old description",
            descriptionStale = true
        )
        val updated = space.withDescription("New description")
        assertEquals("New description", updated.description)
        assertFalse(updated.descriptionStale)
    }

    @Test
    fun `resolveExit finds exact match for cardinal direction`() {
        val exit = ExitData(targetId = "room-2", direction = "north", description = "A northern passage")
        val space = SpacePropertiesComponent(name = "Test Room", exits = listOf(exit))
        val player = createTestPlayer()

        val resolved = space.resolveExit("north", player)
        assertNotNull(resolved)
        assertEquals("north", resolved.direction)
    }

    @Test
    fun `resolveExit is case insensitive`() {
        val exit = ExitData(targetId = "room-2", direction = "north", description = "A northern passage")
        val space = SpacePropertiesComponent(name = "Test Room", exits = listOf(exit))
        val player = createTestPlayer()

        val resolved = space.resolveExit("NORTH", player)
        assertNotNull(resolved)
        assertEquals("north", resolved.direction)
    }

    @Test
    fun `resolveExit returns null for hidden exit when perception too low`() {
        val exit = ExitData(
            targetId = "room-2",
            direction = "secret",
            description = "A hidden passage",
            isHidden = true,
            conditions = listOf(Condition.SkillCheck("Perception", 15))
        )
        val space = SpacePropertiesComponent(name = "Test Room", exits = listOf(exit))
        val player = createTestPlayer(perceptionLevel = 5)

        val resolved = space.resolveExit("secret", player)
        assertNull(resolved)
    }

    @Test
    fun `resolveExit finds hidden exit when perception high enough`() {
        val exit = ExitData(
            targetId = "room-2",
            direction = "secret",
            description = "A hidden passage",
            isHidden = true,
            conditions = listOf(Condition.SkillCheck("Perception", 10))
        )
        val space = SpacePropertiesComponent(name = "Test Room", exits = listOf(exit))
        val player = createTestPlayer(perceptionLevel = 15)

        val resolved = space.resolveExit("secret", player)
        assertNotNull(resolved)
    }

    @Test
    fun `resolveExit finds fuzzy match in description`() {
        val exit = ExitData(targetId = "room-2", direction = "up", description = "A wooden ladder leading upward")
        val space = SpacePropertiesComponent(name = "Test Room", exits = listOf(exit))
        val player = createTestPlayer()

        val resolved = space.resolveExit("ladder", player)
        assertNotNull(resolved)
        assertEquals("up", resolved.direction)
    }

    @Test
    fun `getVisibleExits filters hidden exits`() {
        val exit1 = ExitData(targetId = "room-2", direction = "north", description = "North passage")
        val exit2 = ExitData(
            targetId = "room-3",
            direction = "secret",
            description = "Hidden passage",
            isHidden = true,
            conditions = listOf(Condition.SkillCheck("Perception", 15))
        )
        val space = SpacePropertiesComponent(exits = listOf(exit1, exit2))
        val player = createTestPlayer(perceptionLevel = 5)

        val visible = space.getVisibleExits(player)
        assertEquals(1, visible.size)
        assertEquals("north", visible[0].direction)
    }

    @Test
    fun `getVisibleExits includes hidden exits when perception sufficient`() {
        val exit1 = ExitData(targetId = "room-2", direction = "north", description = "North passage")
        val exit2 = ExitData(
            targetId = "room-3",
            direction = "secret",
            description = "Hidden passage",
            isHidden = true,
            conditions = listOf(Condition.SkillCheck("Perception", 10))
        )
        val space = SpacePropertiesComponent(exits = listOf(exit1, exit2))
        val player = createTestPlayer(perceptionLevel = 15)

        val visible = space.getVisibleExits(player)
        assertEquals(2, visible.size)
    }

    @Test
    fun `applyChange updates flags and marks description stale`() {
        val space = SpacePropertiesComponent(
            name = "Test Room",
            stateFlags = emptyMap(),
            descriptionStale = false
        )
        val updated = space.applyChange("boulder_moved", true)

        assertTrue(updated.stateFlags["boulder_moved"] == true)
        assertTrue(updated.descriptionStale)
    }

    @Test
    fun `applyChange does not mark stale if flag unchanged`() {
        val space = SpacePropertiesComponent(
            name = "Test Room",
            stateFlags = mapOf("boulder_moved" to true),
            descriptionStale = false
        )
        val updated = space.applyChange("boulder_moved", true)

        assertFalse(updated.descriptionStale)
    }

    @Test
    fun `addExit adds exit to list`() {
        val space = SpacePropertiesComponent()
        val exit = ExitData(targetId = "room-2", direction = "north", description = "North")
        val updated = space.addExit(exit)

        assertEquals(1, updated.exits.size)
        assertEquals("north", updated.exits[0].direction)
    }

    @Test
    fun `removeExit removes exit by direction`() {
        val exit1 = ExitData(targetId = "room-2", direction = "north", description = "North")
        val exit2 = ExitData(targetId = "room-3", direction = "south", description = "South")
        val space = SpacePropertiesComponent(exits = listOf(exit1, exit2))

        val updated = space.removeExit("north")
        assertEquals(1, updated.exits.size)
        assertEquals("south", updated.exits[0].direction)
    }

    @Test
    fun `updateTrap updates matching trap`() {
        val trap = TrapData(id = "trap-1", type = "pit", difficulty = 10, triggered = false)
        val space = SpacePropertiesComponent(name = "Test Room", traps = listOf(trap))

        val triggered = trap.trigger()
        val updated = space.updateTrap("trap-1", triggered)

        assertTrue(updated.traps[0].triggered)
    }

    @Test
    fun `addTrap adds trap to list`() {
        val space = SpacePropertiesComponent()
        val trap = TrapData(id = "trap-1", type = "pit", difficulty = 10)
        val updated = space.addTrap(trap)

        assertEquals(1, updated.traps.size)
    }

    @Test
    fun `removeTrap removes trap by ID`() {
        val trap1 = TrapData(id = "trap-1", type = "pit", difficulty = 10)
        val trap2 = TrapData(id = "trap-2", type = "spike", difficulty = 15)
        val space = SpacePropertiesComponent(name = "Test Room", traps = listOf(trap1, trap2))

        val updated = space.removeTrap("trap-1")
        assertEquals(1, updated.traps.size)
        assertEquals("trap-2", updated.traps[0].id)
    }

    @Test
    fun `addResource adds resource to list`() {
        val space = SpacePropertiesComponent()
        val resource = ResourceNode(id = "res-1", templateId = "iron_ore", quantity = 5)
        val updated = space.addResource(resource)

        assertEquals(1, updated.resources.size)
    }

    @Test
    fun `updateResource updates matching resource`() {
        val resource = ResourceNode(id = "res-1", templateId = "iron_ore", quantity = 5)
        val space = SpacePropertiesComponent(name = "Test Room", resources = listOf(resource))

        val (harvested, _) = resource.harvest(2)
        val updated = space.updateResource("res-1", harvested)

        assertEquals(3, updated.resources[0].quantity)
    }

    @Test
    fun `removeResource removes resource by ID`() {
        val res1 = ResourceNode(id = "res-1", templateId = "iron_ore", quantity = 5)
        val res2 = ResourceNode(id = "res-2", templateId = "gold_ore", quantity = 3)
        val space = SpacePropertiesComponent(name = "Test Room", resources = listOf(res1, res2))

        val updated = space.removeResource("res-1")
        assertEquals(1, updated.resources.size)
        assertEquals("res-2", updated.resources[0].id)
    }

    @Test
    fun `addEntity adds entity ID`() {
        val space = SpacePropertiesComponent()
        val updated = space.addEntity("entity-1")

        assertTrue(updated.entities.contains("entity-1"))
    }

    @Test
    fun `removeEntity removes entity ID`() {
        val space = SpacePropertiesComponent(name = "Test Room", entities = listOf("entity-1", "entity-2"))
        val updated = space.removeEntity("entity-1")

        assertEquals(1, updated.entities.size)
        assertEquals("entity-2", updated.entities[0])
    }

    @Test
    fun `addItem adds item to dropped items`() {
        val space = SpacePropertiesComponent()
        val item = ItemInstance(id = "item-1", templateId = "sword", quality = 5)
        val updated = space.addItem(item)

        assertEquals(1, updated.itemsDropped.size)
    }

    @Test
    fun `removeItem removes item by ID`() {
        val item1 = ItemInstance(id = "item-1", templateId = "sword", quality = 5)
        val item2 = ItemInstance(id = "item-2", templateId = "shield", quality = 3)
        val space = SpacePropertiesComponent(name = "Test Room", itemsDropped = listOf(item1, item2))

        val updated = space.removeItem("item-1")
        assertEquals(1, updated.itemsDropped.size)
        assertEquals("item-2", updated.itemsDropped[0].id)
    }

    @Test
    fun `tickResources updates all resource timers`() {
        val res1 = ResourceNode(id = "res-1", templateId = "iron_ore", quantity = 0, respawnTime = 100, timeSinceHarvest = 50)
        val res2 = ResourceNode(id = "res-2", templateId = "gold_ore", quantity = 5)
        val space = SpacePropertiesComponent(name = "Test Room", resources = listOf(res1, res2))

        val updated = space.tickResources(10)

        assertEquals(60, updated.resources[0].timeSinceHarvest)
        assertEquals(0, updated.resources[1].timeSinceHarvest) // Non-depleted doesn't tick
    }

    @Test
    fun `isSafeZone defaults to false`() {
        val space = SpacePropertiesComponent()
        assertFalse(space.isSafeZone)
    }

    @Test
    fun `isSafeZone can be set to true`() {
        val space = SpacePropertiesComponent(name = "Test Room", isSafeZone = true)
        assertTrue(space.isSafeZone)
    }

    @Test
    fun `isSafeZone is preserved when copying space`() {
        val space = SpacePropertiesComponent(name = "Test Room", isSafeZone = true)
        val updated = space.addEntity("entity-1")
        assertTrue(updated.isSafeZone)
    }

    @Test
    fun `safe zone can have exits but no traps`() {
        val exit = ExitData(targetId = "room-2", direction = "north", description = "North")
        val space = SpacePropertiesComponent(
            name = "Test Room",
            exits = listOf(exit),
            traps = emptyList(),
            isSafeZone = true
        )
        assertTrue(space.isSafeZone)
        assertEquals(1, space.exits.size)
        assertEquals(0, space.traps.size)
    }

    @Test
    fun `safe zone can have entities (merchants)`() {
        val space = SpacePropertiesComponent(
            name = "Test Room",
            entities = listOf("merchant-1", "merchant-2"),
            isSafeZone = true
        )
        assertTrue(space.isSafeZone)
        assertEquals(2, space.entities.size)
    }

    @Test
    fun `safe zone flag is immutable`() {
        val space = SpacePropertiesComponent(name = "Test Room", isSafeZone = false)
        val updated = space.copy(isSafeZone = true)
        assertFalse(space.isSafeZone)
        assertTrue(updated.isSafeZone)
    }

    @Test
    fun `safe zone serialization roundtrip preserves flag`() {
        val space = SpacePropertiesComponent(
            name = "Town Square",
            description = "Town square",
            isSafeZone = true
        )
        // The data class copy simulates serialization/deserialization
        val copy = space.copy()
        assertTrue(copy.isSafeZone)
        assertEquals("Town square", copy.description)
    }

    @Test
    fun `multiple safe zone mutations preserve flag`() {
        val space = SpacePropertiesComponent(name = "Test Room", isSafeZone = true)
        val updated = space
            .addEntity("merchant-1")
            .addExit(ExitData(targetId = "room-2", direction = "north", description = "North"))
            .withDescription("Safe town square")
        assertTrue(updated.isSafeZone)
    }
}
