package com.jcraw.mud.reasoning.inventory

import com.jcraw.mud.core.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Contract: floor take Success → InventoryComponent gains ItemInstance with templateId;
 * entity removed from space; itemsDropped cleared; overweight Failure leaves world unchanged.
 * MUD-019 — no live LLM.
 */
class FloorItemTakeContractTest {

    private lateinit var template: ItemTemplate
    private lateinit var heavyTemplate: ItemTemplate
    private lateinit var templates: Map<String, ItemTemplate>

    private val spaceId = "room_1"
    private val playerId = "player_1"

    @BeforeEach
    fun setUp() {
        template = ItemTemplate(
            id = "iron_dagger",
            name = "Iron Dagger",
            type = ItemType.WEAPON,
            properties = mapOf("weight" to "1.0", "damage" to "5"),
            rarity = Rarity.COMMON,
            description = "A simple iron dagger",
            equipSlot = EquipSlot.HANDS_MAIN
        )
        heavyTemplate = ItemTemplate(
            id = "anvil",
            name = "Anvil",
            type = ItemType.MISC,
            properties = mapOf("weight" to "100.0"),
            rarity = Rarity.COMMON,
            description = "Too heavy",
            equipSlot = null
        )
        templates = mapOf(template.id to template, heavyTemplate.id to heavyTemplate)
    }

    private fun floorItem(
        templateId: String = template.id,
        instanceId: String = "inst_dagger_1",
        name: String = template.name,
        weightInProps: Boolean = true
    ): Entity.Item {
        val props = buildMap {
            put("templateId", templateId)
            put("instanceId", instanceId)
            put("quality", "7")
            put("quantity", "1")
            if (weightInProps) put("weight", "1.0")
        }
        return Entity.Item(
            id = "drop_$instanceId",
            name = name,
            description = "On the floor",
            isPickupable = true,
            itemType = ItemType.WEAPON,
            properties = props
        )
    }

    private fun worldWithFloorItem(
        item: Entity.Item,
        capacityWeight: Double = 50.0,
        itemsDropped: List<ItemInstance> = emptyList()
    ): Pair<WorldState, PlayerState> {
        val player = PlayerState(
            id = playerId,
            name = "Hero",
            currentRoomId = spaceId,
            inventoryComponent = InventoryComponent(capacityWeight = capacityWeight)
        )
        val space = SpacePropertiesComponent(
            name = "Test Room",
            description = "A room",
            entities = listOf(item.id),
            itemsDropped = itemsDropped
        )
        val world = WorldState(
            players = mapOf(player.id to player),
            spaces = mapOf(spaceId to space),
            entities = mapOf(item.id to item)
        )
        return world to player
    }

    @Test
    fun `take Success adds ItemInstance with templateId to inventoryComponent`() {
        val item = floorItem()
        val (world, player) = worldWithFloorItem(item)

        val result = FloorItemTakeApply.apply(world, player, spaceId, item, templates)

        assertTrue(result is FloorItemTakeApply.Result.Success, "expected Success, got $result")
        val success = result as FloorItemTakeApply.Result.Success

        assertEquals(template.id, success.templateId)
        assertEquals("inst_dagger_1", success.instanceId)

        val inv = success.world.player.inventoryComponent
        assertEquals(1, inv.items.size)
        val instance = inv.items.single()
        assertEquals(template.id, instance.templateId)
        assertEquals("inst_dagger_1", instance.id)
        assertEquals(7, instance.quality)
        assertEquals(1, instance.quantity)
    }

    @Test
    fun `take Success removes entity from space and clears itemsDropped`() {
        val item = floorItem()
        val dropped = ItemInstance(
            id = "inst_dagger_1",
            templateId = template.id,
            quality = 7,
            quantity = 1
        )
        val (world, player) = worldWithFloorItem(item, itemsDropped = listOf(dropped))

        val result = FloorItemTakeApply.apply(world, player, spaceId, item, templates)
        assertTrue(result is FloorItemTakeApply.Result.Success)
        val success = result as FloorItemTakeApply.Result.Success

        assertTrue(
            success.world.getEntitiesInSpace(spaceId).none { it.id == item.id },
            "floor entity should be gone from space"
        )
        assertNull(success.world.entities[item.id], "entity removed from global map")
        val space = success.world.getSpace(spaceId)!!
        assertTrue(space.itemsDropped.none { it.id == "inst_dagger_1" })
    }

    @Test
    fun `overweight Failure leaves inventory empty and world unchanged`() {
        val item = floorItem(
            templateId = heavyTemplate.id,
            instanceId = "inst_anvil",
            name = heavyTemplate.name
        )
        val (world, player) = worldWithFloorItem(item, capacityWeight = 10.0)

        val result = FloorItemTakeApply.apply(world, player, spaceId, item, templates)

        assertTrue(result is FloorItemTakeApply.Result.Failure)
        val failure = result as FloorItemTakeApply.Result.Failure
        assertTrue(failure.message.contains("weight", ignoreCase = true) || failure.message.contains("carry"))

        // World unchanged: entity still present, inventory empty
        assertEquals(0, world.player.inventoryComponent.items.size)
        assertNotNull(world.entities[item.id])
        assertTrue(world.getEntitiesInSpace(spaceId).any { it.id == item.id })
    }

    @Test
    fun `name-match resolves legacy floor item without templateId property`() {
        val legacy = Entity.Item(
            id = "legacy_dagger",
            name = "Iron Dagger",
            description = "Legacy drop",
            isPickupable = true,
            itemType = ItemType.WEAPON,
            properties = emptyMap()
        )
        val (world, player) = worldWithFloorItem(legacy)

        val result = FloorItemTakeApply.apply(world, player, spaceId, legacy, templates)

        assertTrue(result is FloorItemTakeApply.Result.Success)
        val success = result as FloorItemTakeApply.Result.Success
        assertEquals(template.id, success.templateId)
        assertTrue(success.world.player.inventoryComponent.items.any { it.templateId == template.id })
    }

    @Test
    fun `no template Failure does not write V1 or V2 inventory`() {
        val unknown = Entity.Item(
            id = "mystery",
            name = "Mysterious Blob",
            description = "No template",
            isPickupable = true,
            properties = emptyMap()
        )
        val (world, player) = worldWithFloorItem(unknown)

        val result = FloorItemTakeApply.apply(world, player, spaceId, unknown, templates)

        assertTrue(result is FloorItemTakeApply.Result.Failure)
        assertTrue(world.player.inventoryComponent.items.isEmpty())
        assertTrue(world.player.inventory.isEmpty())
        assertNotNull(world.entities[unknown.id])
    }
}
