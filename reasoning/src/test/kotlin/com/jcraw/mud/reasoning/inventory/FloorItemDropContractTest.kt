package com.jcraw.mud.reasoning.inventory

import com.jcraw.mud.core.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Contract: drop Success → InventoryComponent loses ItemInstance; space gains Entity.Item
 * with templateId/instanceId props + itemsDropped entry; missing target → Failure.
 * MUD-023 — no live LLM.
 */
class FloorItemDropContractTest {

    private lateinit var template: ItemTemplate
    private lateinit var templates: Map<String, ItemTemplate>

    private val spaceId = "room_1"
    private val playerId = "player_1"
    private val instanceId = "inst_dagger_1"

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
        templates = mapOf(template.id to template)
    }

    private fun playerWithItem(
        instance: ItemInstance = ItemInstance(
            id = instanceId,
            templateId = template.id,
            quality = 7,
            quantity = 1
        ),
        equipped: Map<EquipSlot, ItemInstance> = emptyMap()
    ): PlayerState {
        return PlayerState(
            id = playerId,
            name = "Hero",
            currentRoomId = spaceId,
            inventoryComponent = InventoryComponent(
                items = listOf(instance),
                equipped = equipped,
                capacityWeight = 50.0
            )
        )
    }

    private fun worldWithPlayer(player: PlayerState): WorldState {
        val space = SpacePropertiesComponent(
            name = "Test Room",
            description = "A room",
            entities = emptyList(),
            itemsDropped = emptyList()
        )
        return WorldState(
            players = mapOf(player.id to player),
            spaces = mapOf(spaceId to space),
            entities = emptyMap()
        )
    }

    @Test
    fun `drop Success removes ItemInstance from inventoryComponent`() {
        val player = playerWithItem()
        val world = worldWithPlayer(player)

        val result = FloorItemDropApply.apply(world, player, spaceId, "iron dagger", templates)

        assertTrue(result is FloorItemDropApply.Result.Success, "expected Success, got $result")
        val success = result as FloorItemDropApply.Result.Success

        assertEquals(template.id, success.templateId)
        assertEquals(instanceId, success.instanceId)
        assertTrue(
            success.world.player.inventoryComponent.items.none { it.id == instanceId },
            "instance should be removed from V2 inventory"
        )
        assertEquals(0, success.world.player.inventoryComponent.items.size)
    }

    @Test
    fun `drop Success adds floor entity with props and itemsDropped`() {
        val player = playerWithItem()
        val world = worldWithPlayer(player)

        val result = FloorItemDropApply.apply(world, player, spaceId, instanceId, templates)
        assertTrue(result is FloorItemDropApply.Result.Success)
        val success = result as FloorItemDropApply.Result.Success

        val entityId = "drop_$instanceId"
        val entity = success.world.entities[entityId] as? Entity.Item
        assertNotNull(entity, "floor entity should exist")
        assertEquals(template.id, entity!!.properties["templateId"])
        assertEquals(instanceId, entity.properties["instanceId"])
        assertEquals("7", entity.properties["quality"])
        assertTrue(
            success.world.getEntitiesInSpace(spaceId).any { it.id == entityId },
            "entity should be in space"
        )

        val space = success.world.getSpace(spaceId)!!
        assertTrue(space.itemsDropped.any { it.id == instanceId && it.templateId == template.id })
    }

    @Test
    fun `missing item Failure leaves world and inventory unchanged`() {
        val player = playerWithItem()
        val world = worldWithPlayer(player)

        val result = FloorItemDropApply.apply(world, player, spaceId, "no such thing", templates)

        assertTrue(result is FloorItemDropApply.Result.Failure)
        val failure = result as FloorItemDropApply.Result.Failure
        assertTrue(failure.message.contains("don't have", ignoreCase = true))

        assertEquals(1, world.player.inventoryComponent.items.size)
        assertEquals(instanceId, world.player.inventoryComponent.items.single().id)
        assertTrue(world.entities.isEmpty())
        assertTrue(world.getSpace(spaceId)!!.itemsDropped.isEmpty())
    }

    @Test
    fun `equipped drop clears V2 equip map entry`() {
        val instance = ItemInstance(
            id = instanceId,
            templateId = template.id,
            quality = 7,
            quantity = 1
        )
        val player = playerWithItem(
            instance = instance,
            equipped = mapOf(EquipSlot.HANDS_MAIN to instance)
        )
        val world = worldWithPlayer(player)

        val result = FloorItemDropApply.apply(world, player, spaceId, "dagger", templates)
        assertTrue(result is FloorItemDropApply.Result.Success)
        val success = result as FloorItemDropApply.Result.Success

        assertTrue(success.world.player.inventoryComponent.items.none { it.id == instanceId })
        assertFalse(success.world.player.inventoryComponent.equipped.containsKey(EquipSlot.HANDS_MAIN))
        assertTrue(success.world.player.inventoryComponent.equipped.isEmpty())
    }

    @Test
    fun `drop then take round-trip keeps instanceId and templateId`() {
        val player = playerWithItem()
        val world = worldWithPlayer(player)

        val dropResult = FloorItemDropApply.apply(world, player, spaceId, "Iron Dagger", templates)
        assertTrue(dropResult is FloorItemDropApply.Result.Success)
        val dropped = dropResult as FloorItemDropApply.Result.Success

        val floorEntity = dropped.world.entities["drop_$instanceId"] as Entity.Item
        val emptiedPlayer = dropped.world.player

        val takeResult = FloorItemTakeApply.apply(
            dropped.world,
            emptiedPlayer,
            spaceId,
            floorEntity,
            templates
        )
        assertTrue(takeResult is FloorItemTakeApply.Result.Success)
        val taken = takeResult as FloorItemTakeApply.Result.Success

        assertEquals(instanceId, taken.instanceId)
        assertEquals(template.id, taken.templateId)
        val inv = taken.world.player.inventoryComponent
        assertTrue(inv.items.any { it.id == instanceId && it.templateId == template.id })
        assertTrue(taken.world.getEntitiesInSpace(spaceId).none { it.id == floorEntity.id })
        assertTrue(taken.world.getSpace(spaceId)!!.itemsDropped.none { it.id == instanceId })
    }
}
