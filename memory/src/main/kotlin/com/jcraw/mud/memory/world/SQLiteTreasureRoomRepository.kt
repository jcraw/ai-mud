package com.jcraw.mud.memory.world

import com.jcraw.mud.core.Pedestal
import com.jcraw.mud.core.PedestalState
import com.jcraw.mud.core.TreasureRoomComponent
import com.jcraw.mud.core.TreasureRoomType
import com.jcraw.mud.core.repository.TreasureRoomRepository
import java.util.UUID

/**
 * SQLite implementation of TreasureRoomRepository
 * Manages treasure room and pedestal persistence with two-table design
 */
class SQLiteTreasureRoomRepository(
    private val database: WorldDatabase
) : TreasureRoomRepository {

    override fun save(component: TreasureRoomComponent, spaceId: String): Result<Unit> {
        return try {
            val conn = database.getConnection()

            // Save treasure room main record
            val roomSql = """
                INSERT OR REPLACE INTO treasure_rooms
                (space_id, room_type, biome_theme, currently_taken_item, has_been_looted)
                VALUES (?, ?, ?, ?, ?)
            """.trimIndent()

            conn.prepareStatement(roomSql).use { stmt ->
                stmt.setString(1, spaceId)
                stmt.setString(2, component.roomType.name)
                stmt.setString(3, component.biomeTheme)
                stmt.setString(4, component.currentlyTakenItem)
                stmt.setInt(5, if (component.hasBeenLooted) 1 else 0)
                stmt.executeUpdate()
            }

            // Delete existing pedestals for this room (will be replaced)
            val deletePedestalsSql = "DELETE FROM pedestals WHERE treasure_room_id = ?"
            conn.prepareStatement(deletePedestalsSql).use { stmt ->
                stmt.setString(1, spaceId)
                stmt.executeUpdate()
            }

            // Insert pedestals
            val pedestalSql = """
                INSERT INTO pedestals
                (id, treasure_room_id, item_template_id, state, pedestal_index, theme_description)
                VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent()

            conn.prepareStatement(pedestalSql).use { stmt ->
                for (pedestal in component.pedestals) {
                    stmt.setString(1, UUID.randomUUID().toString())
                    stmt.setString(2, spaceId)
                    stmt.setString(3, pedestal.itemTemplateId)
                    stmt.setString(4, pedestal.state.name)
                    stmt.setInt(5, pedestal.pedestalIndex)
                    stmt.setString(6, pedestal.themeDescription)
                    stmt.executeUpdate()
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun findBySpaceId(spaceId: String): Result<TreasureRoomComponent?> {
        return try {
            val conn = database.getConnection()

            // Load treasure room main record
            val roomSql = "SELECT * FROM treasure_rooms WHERE space_id = ?"
            val roomData = conn.prepareStatement(roomSql).use { stmt ->
                stmt.setString(1, spaceId)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    Triple(
                        TreasureRoomType.valueOf(rs.getString("room_type")),
                        rs.getString("biome_theme"),
                        Pair(
                            rs.getString("currently_taken_item"),
                            rs.getInt("has_been_looted") == 1
                        )
                    )
                } else {
                    return Result.success(null)
                }
            }

            // Load pedestals
            val pedestalSql = "SELECT * FROM pedestals WHERE treasure_room_id = ? ORDER BY pedestal_index"
            val pedestals = conn.prepareStatement(pedestalSql).use { stmt ->
                stmt.setString(1, spaceId)
                val rs = stmt.executeQuery()
                val list = mutableListOf<Pedestal>()
                while (rs.next()) {
                    list.add(
                        Pedestal(
                            itemTemplateId = rs.getString("item_template_id"),
                            state = PedestalState.valueOf(rs.getString("state")),
                            themeDescription = rs.getString("theme_description"),
                            pedestalIndex = rs.getInt("pedestal_index")
                        )
                    )
                }
                list
            }

            val component = TreasureRoomComponent(
                roomType = roomData.first,
                pedestals = pedestals,
                currentlyTakenItem = roomData.third.first,
                hasBeenLooted = roomData.third.second,
                biomeTheme = roomData.second
            )

            Result.success(component)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun updateCurrentlyTakenItem(spaceId: String, itemTemplateId: String?): Result<Unit> {
        return try {
            val conn = database.getConnection()
            val sql = "UPDATE treasure_rooms SET currently_taken_item = ? WHERE space_id = ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, itemTemplateId)
                stmt.setString(2, spaceId)
                val updated = stmt.executeUpdate()
                if (updated == 0) {
                    return Result.failure(Exception("Treasure room not found: $spaceId"))
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun markAsLooted(spaceId: String): Result<Unit> {
        return try {
            val conn = database.getConnection()

            // Update treasure room
            val roomSql = "UPDATE treasure_rooms SET has_been_looted = 1 WHERE space_id = ?"
            conn.prepareStatement(roomSql).use { stmt ->
                stmt.setString(1, spaceId)
                val updated = stmt.executeUpdate()
                if (updated == 0) {
                    return Result.failure(Exception("Treasure room not found: $spaceId"))
                }
            }

            // Update all pedestals to EMPTY
            val pedestalSql = "UPDATE pedestals SET state = 'EMPTY' WHERE treasure_room_id = ?"
            conn.prepareStatement(pedestalSql).use { stmt ->
                stmt.setString(1, spaceId)
                stmt.executeUpdate()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun updatePedestalState(spaceId: String, itemTemplateId: String, newState: String): Result<Unit> {
        return try {
            val conn = database.getConnection()
            val sql = """
                UPDATE pedestals
                SET state = ?
                WHERE treasure_room_id = ? AND item_template_id = ?
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, newState)
                stmt.setString(2, spaceId)
                stmt.setString(3, itemTemplateId)
                val updated = stmt.executeUpdate()
                if (updated == 0) {
                    return Result.failure(Exception("Pedestal not found: $itemTemplateId in room $spaceId"))
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun delete(spaceId: String): Result<Unit> {
        return try {
            val conn = database.getConnection()

            // Delete pedestals first (FK constraint)
            val deletePedestalsSql = "DELETE FROM pedestals WHERE treasure_room_id = ?"
            conn.prepareStatement(deletePedestalsSql).use { stmt ->
                stmt.setString(1, spaceId)
                stmt.executeUpdate()
            }

            // Delete treasure room
            val deleteRoomSql = "DELETE FROM treasure_rooms WHERE space_id = ?"
            conn.prepareStatement(deleteRoomSql).use { stmt ->
                stmt.setString(1, spaceId)
                stmt.executeUpdate()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun findAll(): Result<List<Pair<String, TreasureRoomComponent>>> {
        return try {
            val conn = database.getConnection()

            // Get all space IDs
            val spaceIdsSql = "SELECT space_id FROM treasure_rooms"
            val spaceIds = conn.prepareStatement(spaceIdsSql).use { stmt ->
                val rs = stmt.executeQuery()
                val ids = mutableListOf<String>()
                while (rs.next()) {
                    ids.add(rs.getString("space_id"))
                }
                ids
            }

            // Load each treasure room
            val results = spaceIds.mapNotNull { spaceId ->
                findBySpaceId(spaceId).getOrNull()?.let { spaceId to it }
            }

            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
