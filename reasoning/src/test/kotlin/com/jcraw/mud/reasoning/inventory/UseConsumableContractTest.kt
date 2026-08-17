package com.jcraw.mud.reasoning.inventory

import com.jcraw.mud.core.InventoryComponent
import com.jcraw.mud.core.ItemInstance
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.ItemType
import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.Rarity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Contract: use Success → item gone or qty−1 and health += min(heal, missing HP);
 * missing / non-consumable → Failure, player health+items unchanged.
 * MUD-037 — no live LLM.
 */
class UseConsumableContractTest {

    private lateinit var potion: ItemTemplate
    private lateinit var dagger: ItemTemplate
    private lateinit var templates: Map<String, ItemTemplate>

    private val playerId = "player_1"
    private val spaceId = "room_1"
    private val instanceId = "inst_potion_1"
    private val healAmount = 30

    @BeforeEach
    fun setUp() {
        potion = ItemTemplate(
            id = "health_potion",
            name = "Health Potion",
            type = ItemType.CONSUMABLE,
            properties = mapOf("weight" to "0.2", "healing" to healAmount.toString()),
            rarity = Rarity.COMMON,
            description = "A red vial"
        )
        dagger = ItemTemplate(
            id = "iron_dagger",
            name = "Iron Dagger",
            type = ItemType.WEAPON,
            properties = mapOf("weight" to "1.0"),
            rarity = Rarity.COMMON,
            description = "A blade"
        )
        templates = mapOf(potion.id to potion, dagger.id to dagger)
    }

    private fun playerWith(
        quantity: Int = 1,
        health: Int = 50,
        maxHealth: Int = 100,
        templateId: String = potion.id,
        id: String = instanceId
    ): PlayerState = PlayerState(
        id = playerId,
        name = "Hero",
        currentRoomId = spaceId,
        health = health,
        maxHealth = maxHealth,
        inventoryComponent = InventoryComponent(
            items = listOf(
                ItemInstance(
                    id = id,
                    templateId = templateId,
                    quality = 5,
                    quantity = quantity
                )
            ),
            capacityWeight = 50.0
        )
    )

    @Test
    fun `use Success removes last item and heals by missing HP cap`() {
        val player = playerWith(quantity = 1, health = 50, maxHealth = 100)
        val expectedHeal = minOf(healAmount, player.maxHealth - player.health)

        val result = UseConsumableApply.apply(player, "potion", templates)

        assertTrue(result is UseConsumableApply.Result.Success, "expected Success, got $result")
        val success = result as UseConsumableApply.Result.Success
        assertEquals(expectedHeal, success.healedAmount)
        assertEquals(player.health + expectedHeal, success.player.health)
        assertTrue(success.player.inventoryComponent.items.none { it.id == instanceId })
        assertEquals(0, success.player.inventoryComponent.items.size)
    }

    @Test
    fun `use Success decrements quantity when stack remains`() {
        val player = playerWith(quantity = 3, health = 90, maxHealth = 100)
        val expectedHeal = minOf(healAmount, player.maxHealth - player.health)

        val result = UseConsumableApply.apply(player, instanceId, templates)

        assertTrue(result is UseConsumableApply.Result.Success)
        val success = result as UseConsumableApply.Result.Success
        assertEquals(expectedHeal, success.healedAmount)
        assertEquals(100, success.player.health)
        val remaining = success.player.inventoryComponent.items.single()
        assertEquals(instanceId, remaining.id)
        assertEquals(2, remaining.quantity)
    }

    @Test
    fun `missing item Failure leaves health and items unchanged`() {
        val player = playerWith()
        val itemsBefore = player.inventoryComponent.items
        val healthBefore = player.health

        val result = UseConsumableApply.apply(player, "no such thing", templates)

        assertTrue(result is UseConsumableApply.Result.Failure)
        assertEquals(healthBefore, player.health)
        assertEquals(itemsBefore, player.inventoryComponent.items)
        assertEquals(1, player.inventoryComponent.items.size)
    }

    @Test
    fun `non-consumable Failure leaves health and items unchanged`() {
        val player = playerWith(templateId = dagger.id, id = "inst_dagger_1")
        val itemsBefore = player.inventoryComponent.items
        val healthBefore = player.health

        val result = UseConsumableApply.apply(player, "dagger", templates)

        assertTrue(result is UseConsumableApply.Result.Failure)
        assertEquals(healthBefore, player.health)
        assertEquals(itemsBefore, player.inventoryComponent.items)
        assertEquals(1, player.inventoryComponent.items.single().quantity)
    }
}
