package com.jcraw.mud.reasoning.treasureroom

import com.jcraw.mud.core.*
import com.jcraw.mud.core.repository.ItemRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TreasureRoomExitLogicTest {

    private val fakeRepository = object : ItemRepository {
        override fun findTemplateById(templateId: String): Result<ItemTemplate?> =
            Result.success(
                ItemTemplate(
                    id = templateId,
                    name = "Test Relic",
                    type = ItemType.WEAPON,
                    description = "Relic used for testing"
                )
            )

        override fun findAllTemplates(): Result<Map<String, ItemTemplate>> = Result.success(emptyMap())
        override fun findTemplatesByType(type: ItemType): Result<List<ItemTemplate>> = Result.success(emptyList())
        override fun findTemplatesByRarity(rarity: Rarity): Result<List<ItemTemplate>> = Result.success(emptyList())
        override fun saveTemplate(template: ItemTemplate): Result<Unit> = Result.failure(UnsupportedOperationException())
        override fun saveTemplates(templates: List<ItemTemplate>): Result<Unit> = Result.failure(UnsupportedOperationException())
        override fun deleteTemplate(templateId: String): Result<Unit> = Result.failure(UnsupportedOperationException())
        override fun findInstanceById(instanceId: String): Result<ItemInstance?> = Result.success(null)
        override fun findInstancesByTemplate(templateId: String): Result<List<ItemInstance>> = Result.success(emptyList())
        override fun saveInstance(instance: ItemInstance): Result<Unit> = Result.failure(UnsupportedOperationException())
        override fun deleteInstance(instanceId: String): Result<Unit> = Result.failure(UnsupportedOperationException())
        override fun findAllInstances(): Result<Map<String, ItemInstance>> = Result.success(emptyMap())
    }

    private fun starterComponent(): TreasureRoomComponent {
        val pedestals = listOf(
            Pedestal("item_a", PedestalState.AVAILABLE, "stone altar", 0),
            Pedestal("item_b", PedestalState.AVAILABLE, "stone altar", 1)
        )
        return TreasureRoomComponent(
            roomType = TreasureRoomType.STARTER,
            pedestals = pedestals,
            currentlyTakenItem = "item_a",
            biomeTheme = "ancient_abyss"
        )
    }

    @Test
    fun `finalizeExit marks room looted and builds narration`() {
        val component = starterComponent()
        val result = TreasureRoomExitLogic.finalizeExit(component, fakeRepository)

        assertNotNull(result)
        assertTrue(result!!.updatedComponent.hasBeenLooted)
        assertTrue(result.updatedComponent.pedestals.all { it.state == PedestalState.EMPTY })
        assertTrue(result.narration.contains("Test Relic"))
    }

    @Test
    fun `finalizeExit returns null when nothing is held`() {
        val component = starterComponent().copy(currentlyTakenItem = null)
        val result = TreasureRoomExitLogic.finalizeExit(component, fakeRepository)
        assertNull(result)
    }

    @Test
    fun `finalizeExit returns null when already looted`() {
        val component = starterComponent().copy(hasBeenLooted = true)
        val result = TreasureRoomExitLogic.finalizeExit(component, fakeRepository)
        assertNull(result)
    }
}
