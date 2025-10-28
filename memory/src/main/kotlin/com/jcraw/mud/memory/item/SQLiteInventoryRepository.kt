package com.jcraw.mud.memory.item

import com.jcraw.mud.core.EquipSlot
import com.jcraw.mud.core.InventoryComponent
import com.jcraw.mud.core.ItemInstance
import com.jcraw.mud.core.repository.InventoryRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * SQLite implementation of InventoryRepository
 * Manages inventory component persistence
 */
class SQLiteInventoryRepository(
    private val database: ItemDatabase
) : InventoryRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun findByEntityId(entityId: String): Result<InventoryComponent?> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM inventories WHERE entity_id = ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, entityId)
                val rs = stmt.executeQuery()

                if (rs.next()) {
                    val items = json.decodeFromString<List<ItemInstance>>(rs.getString("items"))
                    val equipped = json.decodeFromString<Map<EquipSlot, ItemInstance>>(rs.getString("equipped"))
                    val gold = rs.getInt("gold")
                    val capacityWeight = rs.getDouble("capacity_weight")

                    val inventory = InventoryComponent(
                        items = items,
                        equipped = equipped,
                        gold = gold,
                        capacityWeight = capacityWeight
                    )
                    Result.success(inventory)
                } else {
                    Result.success(null)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun save(entityId: String, inventory: InventoryComponent): Result<Unit> {
        return try {
            val conn = database.getConnection()
            val sql = """
                INSERT OR REPLACE INTO inventories
                (entity_id, items, equipped, gold, capacity_weight)
                VALUES (?, ?, ?, ?, ?)
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, entityId)
                stmt.setString(2, json.encodeToString(inventory.items))
                stmt.setString(3, json.encodeToString(inventory.equipped))
                stmt.setInt(4, inventory.gold)
                stmt.setDouble(5, inventory.capacityWeight)
                stmt.executeUpdate()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun delete(entityId: String): Result<Unit> {
        return try {
            val conn = database.getConnection()
            conn.prepareStatement("DELETE FROM inventories WHERE entity_id = ?").use { stmt ->
                stmt.setString(1, entityId)
                stmt.executeUpdate()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun updateGold(entityId: String, newGold: Int): Result<Unit> {
        return try {
            // Load current inventory
            val currentResult = findByEntityId(entityId)
            if (currentResult.isFailure || currentResult.getOrNull() == null) {
                return Result.failure(IllegalStateException("Entity $entityId has no inventory"))
            }

            // Update gold and save
            val updated = currentResult.getOrNull()!!.copy(gold = newGold)
            save(entityId, updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun updateCapacity(entityId: String, newCapacity: Double): Result<Unit> {
        return try {
            // Load current inventory
            val currentResult = findByEntityId(entityId)
            if (currentResult.isFailure || currentResult.getOrNull() == null) {
                return Result.failure(IllegalStateException("Entity $entityId has no inventory"))
            }

            // Update capacity and save
            val updated = currentResult.getOrNull()!!.copy(capacityWeight = newCapacity)
            save(entityId, updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun findAll(): Result<Map<String, InventoryComponent>> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM inventories"

            conn.prepareStatement(sql).use { stmt ->
                val rs = stmt.executeQuery()
                val inventories = mutableMapOf<String, InventoryComponent>()

                while (rs.next()) {
                    val entityId = rs.getString("entity_id")
                    val items = json.decodeFromString<List<ItemInstance>>(rs.getString("items"))
                    val equipped = json.decodeFromString<Map<EquipSlot, ItemInstance>>(rs.getString("equipped"))
                    val gold = rs.getInt("gold")
                    val capacityWeight = rs.getDouble("capacity_weight")

                    val inventory = InventoryComponent(
                        items = items,
                        equipped = equipped,
                        gold = gold,
                        capacityWeight = capacityWeight
                    )
                    inventories[entityId] = inventory
                }
                Result.success(inventories)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
