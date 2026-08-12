package com.jcraw.mud.reasoning.inventory

import com.jcraw.mud.core.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Contract: give Success → InventoryComponent loses ItemInstance; missing → Failure.
 * MUD-024 — no live LLM; no V1 inventory write.
 */
class GiveItemContractTest {

    private lateinit var template: ItemTemplate
    private lateinit var templates: Map<String, ItemTemplate>

    private val spaceId = "room_1"
    private val playerId = "player_1"
    private val instanceId = "inst_quest_token_1"

    @BeforeEach
    fun setUp() {
        template = ItemTemplate(
            id = "quest_token",
            name = "Quest Token",
            type = ItemType.QUEST,
            properties = mapOf("weight" to "0.1"),
            rarity = Rarity.COMMON,
            description = "A token for delivery"
        )
        templates = mapOf(template.id to template)
    }

    private fun playerWithItem(
        instance: ItemInstance = ItemInstance(
            id = instanceId,
            templateId = template.id,
            quality = 5,
            quantity = 1
        )
    ): PlayerState {
        return PlayerState(
            id = playerId,
            name = "Hero",
            currentRoomId = spaceId,
            inventoryComponent = InventoryComponent(
                items = listOf(instance),
                capacityWeight = 50.0
            )
        )
    }

    private fun worldWithPlayer(player: PlayerState): WorldState {
        return WorldState(
            players = mapOf(player.id to player),
            spaces = mapOf(
                spaceId to SpacePropertiesComponent(
                    name = "Test Room",
                    description = "A room",
                    entities = emptyList()
                )
            ),
            entities = emptyMap()
        )
    }

    @Test
    fun `give Success removes ItemInstance from inventoryComponent`() {
        val player = playerWithItem()
        val world = worldWithPlayer(player)

        val result = GiveItemApply.apply(world, player, "quest token", templates)

        assertTrue(result is GiveItemApply.Result.Success, "expected Success, got $result")
        val success = result as GiveItemApply.Result.Success

        assertEquals(template.id, success.templateId)
        assertEquals(instanceId, success.instanceId)
        assertEquals("Quest Token", success.itemName)
        assertTrue(
            success.world.player.inventoryComponent.items.none { it.id == instanceId },
            "instance should be removed from V2 inventory"
        )
        assertEquals(0, success.world.player.inventoryComponent.items.size)
        // No V1 inventory write
        assertTrue(success.world.player.inventory.isEmpty())
    }

    @Test
    fun `give resolves by instance id`() {
        val player = playerWithItem()
        val world = worldWithPlayer(player)

        val result = GiveItemApply.apply(world, player, instanceId, templates)
        assertTrue(result is GiveItemApply.Result.Success)
        val success = result as GiveItemApply.Result.Success
        assertEquals(instanceId, success.instanceId)
    }

    @Test
    fun `missing item Failure leaves world and inventory unchanged`() {
        val player = playerWithItem()
        val world = worldWithPlayer(player)

        val result = GiveItemApply.apply(world, player, "no such thing", templates)

        assertTrue(result is GiveItemApply.Result.Failure)
        val failure = result as GiveItemApply.Result.Failure
        assertTrue(failure.message.contains("don't have", ignoreCase = true))

        assertEquals(1, world.player.inventoryComponent.items.size)
        assertEquals(instanceId, world.player.inventoryComponent.items.single().id)
    }

    @Test
    fun `give clears V2 equip map entry when item was equipped`() {
        val instance = ItemInstance(
            id = instanceId,
            templateId = template.id,
            quality = 5,
            quantity = 1
        )
        val weaponTemplate = template.copy(
            id = "iron_dagger",
            name = "Iron Dagger",
            type = ItemType.WEAPON,
            equipSlot = EquipSlot.HANDS_MAIN
        )
        val weaponInstance = instance.copy(templateId = weaponTemplate.id)
        val player = PlayerState(
            id = playerId,
            name = "Hero",
            currentRoomId = spaceId,
            inventoryComponent = InventoryComponent(
                items = listOf(weaponInstance),
                equipped = mapOf(EquipSlot.HANDS_MAIN to weaponInstance),
                capacityWeight = 50.0
            )
        )
        val world = worldWithPlayer(player)
        val tpls = mapOf(weaponTemplate.id to weaponTemplate)

        val result = GiveItemApply.apply(world, player, "dagger", tpls)
        assertTrue(result is GiveItemApply.Result.Success)
        val success = result as GiveItemApply.Result.Success

        assertTrue(success.world.player.inventoryComponent.items.none { it.id == instanceId })
        assertFalse(success.world.player.inventoryComponent.equipped.containsKey(EquipSlot.HANDS_MAIN))
    }
}
