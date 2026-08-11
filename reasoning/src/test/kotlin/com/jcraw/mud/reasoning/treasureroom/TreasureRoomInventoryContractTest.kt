package com.jcraw.mud.reasoning.treasureroom

import com.jcraw.mud.core.*
import com.jcraw.mud.reasoning.TestItemRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Contract: take Success → InventoryComponent gains ItemInstance with pedestal templateId;
 * apply/updatePlayer → world.player.inventoryComponent size +1 and pedestals locked.
 * MUD-007 — no live LLM.
 */
class TreasureRoomInventoryContractTest {

    private lateinit var templateA: ItemTemplate
    private lateinit var templateB: ItemTemplate
    private lateinit var templates: Map<String, ItemTemplate>
    private lateinit var repository: TestItemRepository
    private lateinit var handler: TreasureRoomHandler

    @BeforeEach
    fun setUp() {
        templateA = ItemTemplate(
            id = "flamebrand_longsword",
            name = "Flamebrand Longsword",
            type = ItemType.WEAPON,
            properties = mapOf("weight" to "3.0", "damage" to "12"),
            rarity = Rarity.RARE,
            description = "A blazing longsword",
            equipSlot = EquipSlot.HANDS_MAIN
        )
        templateB = ItemTemplate(
            id = "shadowweave_cloak",
            name = "Shadowweave Cloak",
            type = ItemType.ARMOR,
            properties = mapOf("weight" to "1.5", "defense" to "4"),
            rarity = Rarity.RARE,
            description = "A shadowy cloak",
            equipSlot = EquipSlot.CHEST
        )
        templates = mapOf(templateA.id to templateA, templateB.id to templateB)
        repository = TestItemRepository(initialTemplates = templates)
        handler = TreasureRoomHandler(repository)
    }

    private fun starterRoom(): TreasureRoomComponent = TreasureRoomComponent(
        roomType = TreasureRoomType.STARTER,
        pedestals = listOf(
            Pedestal(templateA.id, PedestalState.AVAILABLE, "stone altar", 0),
            Pedestal(templateB.id, PedestalState.AVAILABLE, "stone altar", 1)
        ),
        currentlyTakenItem = null,
        biomeTheme = "ancient_abyss"
    )

    @Test
    fun `take Success adds ItemInstance with pedestal templateId to inventory`() {
        val inventory = InventoryComponent()
        val room = starterRoom()

        val result = handler.takeItemFromPedestal(
            treasureRoom = room,
            playerInventory = inventory,
            itemTemplateId = templateA.id,
            itemTemplates = templates
        )

        assertTrue(result is TreasureRoomHandler.TreasureRoomResult.Success)
        val success = result as TreasureRoomHandler.TreasureRoomResult.Success

        assertEquals(1, success.playerInventory.items.size)
        val instance = success.playerInventory.items.single()
        assertEquals(templateA.id, instance.templateId)
        assertEquals(1, instance.quantity)
        assertEquals(templateA.name, success.itemName)
        assertEquals("took", success.action)

        // Pedestals: taken pedestal stays AVAILABLE for return; others LOCKED
        assertEquals(templateA.id, success.treasureRoomComponent.currentlyTakenItem)
        val takenPedestal = success.treasureRoomComponent.pedestals.first { it.itemTemplateId == templateA.id }
        val otherPedestal = success.treasureRoomComponent.pedestals.first { it.itemTemplateId == templateB.id }
        assertEquals(PedestalState.AVAILABLE, takenPedestal.state)
        assertEquals(PedestalState.LOCKED, otherPedestal.state)
    }

    @Test
    fun `applySuccess puts item on world player inventoryComponent and locks pedestals`() {
        val spaceId = "treasure_room_1"
        val player = PlayerState(
            id = "player_ui",
            name = "Hero",
            currentRoomId = spaceId,
            inventoryComponent = InventoryComponent()
        )
        val room = starterRoom()
        val world = WorldState(
            players = mapOf(player.id to player),
            treasureRooms = mapOf(spaceId to room)
        )

        val result = handler.takeItemFromPedestal(
            treasureRoom = room,
            playerInventory = player.inventoryComponent,
            itemTemplateId = templateA.id,
            itemTemplates = templates
        )
        assertTrue(result is TreasureRoomHandler.TreasureRoomResult.Success)
        val success = result as TreasureRoomHandler.TreasureRoomResult.Success

        val updated = TreasureRoomStateApply.applySuccess(
            world = world,
            spaceId = spaceId,
            player = player,
            success = success
        )

        // Inventory contract
        val inv = updated.player.inventoryComponent
        assertEquals(1, inv.items.size, "world player inventory should gain one item")
        assertEquals(templateA.id, inv.items.single().templateId)

        // Equip-name match finds instance (same rule as handlers)
        val matchByName = inv.items.find { instance ->
            val t = templates[instance.templateId]
            t != null && (
                t.name.lowercase().contains("flamebrand") ||
                    instance.templateId.lowercase().contains("flamebrand")
                )
        }
        assertNotNull(matchByName, "equip-style name match should find the taken item")

        // Treasure room locked
        val tr = updated.getTreasureRoom(spaceId)!!
        assertEquals(templateA.id, tr.currentlyTakenItem)
        assertEquals(PedestalState.LOCKED, tr.pedestals.first { it.itemTemplateId == templateB.id }.state)
    }

    @Test
    fun `return Success removes item and unlocks pedestals after apply`() {
        val spaceId = "treasure_room_1"
        val emptyInv = InventoryComponent()
        val room = starterRoom()

        val take = handler.takeItemFromPedestal(room, emptyInv, templateA.id, templates)
            as TreasureRoomHandler.TreasureRoomResult.Success
        val afterTakeInv = take.playerInventory
        val afterTakeRoom = take.treasureRoomComponent
        val instanceId = afterTakeInv.items.single().id

        val player = PlayerState(
            id = "player_ui",
            name = "Hero",
            currentRoomId = spaceId,
            inventoryComponent = afterTakeInv
        )
        val world = WorldState(
            players = mapOf(player.id to player),
            treasureRooms = mapOf(spaceId to afterTakeRoom)
        )

        val ret = handler.returnItemToPedestal(
            treasureRoom = afterTakeRoom,
            playerInventory = afterTakeInv,
            itemInstanceId = instanceId,
            itemTemplates = templates
        )
        assertTrue(ret is TreasureRoomHandler.TreasureRoomResult.Success)
        val success = ret as TreasureRoomHandler.TreasureRoomResult.Success

        val updated = TreasureRoomStateApply.applySuccess(world, spaceId, player, success)

        assertTrue(updated.player.inventoryComponent.items.isEmpty())
        val tr = updated.getTreasureRoom(spaceId)!!
        assertNull(tr.currentlyTakenItem)
        assertTrue(tr.pedestals.all { it.state == PedestalState.AVAILABLE })
    }
}
