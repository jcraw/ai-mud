package com.jcraw.mud.memory.world

import com.jcraw.mud.core.CorpseData
import com.jcraw.mud.core.InventoryComponent
import com.jcraw.mud.core.ItemInstance
import com.jcraw.mud.core.PlayerId
import com.jcraw.mud.core.repository.CorpseRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * SQLite implementation of CorpseRepository
 * Manages corpse data persistence with JSON serialization for complex fields
 */
class SQLiteCorpseRepository(
    private val database: WorldDatabase
) : CorpseRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun save(corpse: CorpseData): Result<Unit> {
        return try {
            val conn = database.getConnection()
            val sql = """
                INSERT OR REPLACE INTO corpses
                (id, player_id, space_id, inventory, equipment, gold, decay_timer, looted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, corpse.id)
                stmt.setString(2, corpse.playerId)
                stmt.setString(3, corpse.spaceId)
                stmt.setString(4, json.encodeToString(corpse.inventory))
                stmt.setString(5, json.encodeToString(corpse.equipment))
                stmt.setInt(6, corpse.gold)
                stmt.setLong(7, corpse.decayTimer)
                stmt.setInt(8, if (corpse.looted) 1 else 0)
                stmt.executeUpdate()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun findById(id: String): Result<CorpseData?> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM corpses WHERE id = ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, id)
                val rs = stmt.executeQuery()

                if (rs.next()) {
                    val corpse = CorpseData(
                        id = rs.getString("id"),
                        playerId = rs.getString("player_id"),
                        spaceId = rs.getString("space_id"),
                        inventory = json.decodeFromString<InventoryComponent>(rs.getString("inventory")),
                        equipment = json.decodeFromString<List<ItemInstance>>(rs.getString("equipment")),
                        gold = rs.getInt("gold"),
                        decayTimer = rs.getLong("decay_timer"),
                        looted = rs.getInt("looted") == 1
                    )
                    Result.success(corpse)
                } else {
                    Result.success(null)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun findByPlayerId(playerId: PlayerId): Result<List<CorpseData>> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM corpses WHERE player_id = ? ORDER BY decay_timer ASC"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, playerId)
                val rs = stmt.executeQuery()
                val corpses = mutableListOf<CorpseData>()

                while (rs.next()) {
                    val corpse = CorpseData(
                        id = rs.getString("id"),
                        playerId = rs.getString("player_id"),
                        spaceId = rs.getString("space_id"),
                        inventory = json.decodeFromString<InventoryComponent>(rs.getString("inventory")),
                        equipment = json.decodeFromString<List<ItemInstance>>(rs.getString("equipment")),
                        gold = rs.getInt("gold"),
                        decayTimer = rs.getLong("decay_timer"),
                        looted = rs.getInt("looted") == 1
                    )
                    corpses.add(corpse)
                }
                Result.success(corpses)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun findBySpaceId(spaceId: String): Result<List<CorpseData>> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM corpses WHERE space_id = ? ORDER BY decay_timer ASC"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, spaceId)
                val rs = stmt.executeQuery()
                val corpses = mutableListOf<CorpseData>()

                while (rs.next()) {
                    val corpse = CorpseData(
                        id = rs.getString("id"),
                        playerId = rs.getString("player_id"),
                        spaceId = rs.getString("space_id"),
                        inventory = json.decodeFromString<InventoryComponent>(rs.getString("inventory")),
                        equipment = json.decodeFromString<List<ItemInstance>>(rs.getString("equipment")),
                        gold = rs.getInt("gold"),
                        decayTimer = rs.getLong("decay_timer"),
                        looted = rs.getInt("looted") == 1
                    )
                    corpses.add(corpse)
                }
                Result.success(corpses)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun findDecayed(currentTime: Long): Result<List<CorpseData>> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM corpses WHERE decay_timer <= ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, currentTime)
                val rs = stmt.executeQuery()
                val corpses = mutableListOf<CorpseData>()

                while (rs.next()) {
                    val corpse = CorpseData(
                        id = rs.getString("id"),
                        playerId = rs.getString("player_id"),
                        spaceId = rs.getString("space_id"),
                        inventory = json.decodeFromString<InventoryComponent>(rs.getString("inventory")),
                        equipment = json.decodeFromString<List<ItemInstance>>(rs.getString("equipment")),
                        gold = rs.getInt("gold"),
                        decayTimer = rs.getLong("decay_timer"),
                        looted = rs.getInt("looted") == 1
                    )
                    corpses.add(corpse)
                }
                Result.success(corpses)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun markLooted(corpseId: String): Result<Unit> {
        return try {
            val conn = database.getConnection()
            conn.prepareStatement("UPDATE corpses SET looted = 1 WHERE id = ?").use { stmt ->
                stmt.setString(1, corpseId)
                stmt.executeUpdate()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun delete(id: String): Result<Unit> {
        return try {
            val conn = database.getConnection()
            conn.prepareStatement("DELETE FROM corpses WHERE id = ?").use { stmt ->
                stmt.setString(1, id)
                stmt.executeUpdate()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun deleteBySpaceId(spaceId: String): Result<Unit> {
        return try {
            val conn = database.getConnection()
            conn.prepareStatement("DELETE FROM corpses WHERE space_id = ?").use { stmt ->
                stmt.setString(1, spaceId)
                stmt.executeUpdate()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getAll(): Result<Map<String, CorpseData>> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM corpses"

            conn.prepareStatement(sql).use { stmt ->
                val rs = stmt.executeQuery()
                val corpses = mutableMapOf<String, CorpseData>()

                while (rs.next()) {
                    val id = rs.getString("id")
                    val corpse = CorpseData(
                        id = id,
                        playerId = rs.getString("player_id"),
                        spaceId = rs.getString("space_id"),
                        inventory = json.decodeFromString<InventoryComponent>(rs.getString("inventory")),
                        equipment = json.decodeFromString<List<ItemInstance>>(rs.getString("equipment")),
                        gold = rs.getInt("gold"),
                        decayTimer = rs.getLong("decay_timer"),
                        looted = rs.getInt("looted") == 1
                    )
                    corpses[id] = corpse
                }
                Result.success(corpses)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
