package com.jcraw.mud.reasoning.inventory

import com.jcraw.mud.core.EquipSlot
import com.jcraw.mud.core.InventoryComponent
import com.jcraw.mud.core.ItemInstance
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.ItemType
import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.Rarity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Contract: equip Success → equipped[slot].id == instanceId; missing/non-equip → Failure,
 * equipped unchanged. MUD-037 — no live LLM; no V1 inventory write.
 */
class EquipItemContractTest {

    private lateinit var weapon: ItemTemplate
    private lateinit var potion: ItemTemplate
    private lateinit var templates: Map<String, ItemTemplate>

    private val playerId = "player_1"
    private val spaceId = "room_1"
    private val instanceId = "inst_dagger_1"

    @BeforeEach
    fun setUp() {
        weapon = ItemTemplate(
            id = "iron_dagger",
            name = "Iron Dagger",
            type = ItemType.WEAPON,
            properties = mapOf("weight" to "1.0", "damage" to "5"),
            rarity = Rarity.COMMON,
            description = "A simple iron dagger",
            equipSlot = EquipSlot.HANDS_MAIN
        )
        potion = ItemTemplate(
            id = "health_potion",
            name = "Health Potion",
            type = ItemType.CONSUMABLE,
            properties = mapOf("weight" to "0.2", "healing" to "20"),
            rarity = Rarity.COMMON,
            description = "A red vial",
            equipSlot = null
        )
        templates = mapOf(weapon.id to weapon, potion.id to potion)
    }

    private fun playerWith(
        instance: ItemInstance = ItemInstance(
            id = instanceId,
            templateId = weapon.id,
            quality = 5,
            quantity = 1
        ),
        equipped: Map<EquipSlot, ItemInstance> = emptyMap()
    ): PlayerState = PlayerState(
        id = playerId,
        name = "Hero",
        currentRoomId = spaceId,
        inventoryComponent = InventoryComponent(
            items = listOf(instance),
            equipped = equipped,
            capacityWeight = 50.0
        )
    )

    @Test
    fun `equip Success sets equipped slot to instance id`() {
        val player = playerWith()

        val result = EquipItemApply.apply(player, "dagger", templates)

        assertTrue(result is EquipItemApply.Result.Success, "expected Success, got $result")
        val success = result as EquipItemApply.Result.Success
        assertEquals(instanceId, success.instanceId)
        assertEquals(EquipSlot.HANDS_MAIN, success.slot)
        assertEquals("Iron Dagger", success.itemName)
        assertEquals(instanceId, success.player.inventoryComponent.equipped[EquipSlot.HANDS_MAIN]?.id)
        assertTrue(success.player.inventory.isEmpty())
    }

    @Test
    fun `equip resolves by instance id`() {
        val player = playerWith()

        val result = EquipItemApply.apply(player, instanceId, templates)
        assertTrue(result is EquipItemApply.Result.Success)
        val success = result as EquipItemApply.Result.Success
        assertEquals(instanceId, success.player.inventoryComponent.equipped[EquipSlot.HANDS_MAIN]?.id)
    }

    @Test
    fun `missing item Failure leaves equipped unchanged`() {
        val player = playerWith()
        val before = player.inventoryComponent.equipped

        val result = EquipItemApply.apply(player, "no such thing", templates)

        assertTrue(result is EquipItemApply.Result.Failure)
        assertTrue(before.isEmpty())
        assertTrue(player.inventoryComponent.equipped.isEmpty())
        assertSame(before, player.inventoryComponent.equipped)
    }

    @Test
    fun `non-equip item Failure leaves equipped unchanged`() {
        val potionInst = ItemInstance(
            id = "inst_potion_1",
            templateId = potion.id,
            quality = 5,
            quantity = 1
        )
        val player = playerWith(instance = potionInst)
        val before = player.inventoryComponent.equipped

        val result = EquipItemApply.apply(player, "potion", templates)

        assertTrue(result is EquipItemApply.Result.Failure)
        val failure = result as EquipItemApply.Result.Failure
        assertTrue(failure.message.contains("can't equip", ignoreCase = true))
        assertTrue(player.inventoryComponent.equipped.isEmpty())
        assertSame(before, player.inventoryComponent.equipped)
    }
}
