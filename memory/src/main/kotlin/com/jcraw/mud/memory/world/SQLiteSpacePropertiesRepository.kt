package com.jcraw.mud.memory.world

import com.jcraw.mud.core.ItemInstance
import com.jcraw.mud.core.SpacePropertiesComponent
import com.jcraw.mud.core.repository.SpacePropertiesRepository
import com.jcraw.mud.core.world.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * SQLite implementation of SpacePropertiesRepository
 * Manages space properties persistence with complex JSON serialization
 */
class SQLiteSpacePropertiesRepository(
    private val database: WorldDatabase
) : SpacePropertiesRepository {

    private val json = Json { ignoreUnknownKeys = true }

    private fun extractParentIdFromChunkId(chunkId: String): String? {
        if (chunkId == "WORLD_root") return null

        val parts = chunkId.split("_")
        if (parts.size < 3) return null

        // Parent ID is everything between the level prefix and the trailing UUID
        return parts.subList(1, parts.size - 1).joinToString("_")
    }

    override fun save(properties: SpacePropertiesComponent, chunkId: String): Result<Unit> {
        return try {
            val conn = database.getConnection()

            // Ensure chunk exists in world_chunks table (required by FK constraint)
            // Check if chunk already exists
            val checkSql = "SELECT id FROM world_chunks WHERE id = ?"
            val chunkExists = conn.prepareStatement(checkSql).use { stmt ->
                stmt.setString(1, chunkId)
                val rs = stmt.executeQuery()
                rs.next()
            }

            // If chunk doesn't exist, create minimal SPACE chunk
            if (!chunkExists) {
                // Extract parent subzone ID from chunk ID
                // Format: SPACE_{parentId}_{uuid} -> extract everything between first and last underscore
                val parentSubzoneId = extractParentIdFromChunkId(chunkId)

                // Verify parent exists in database (required by FK constraint)
                val parentExists = if (parentSubzoneId != null) {
                    val checkParentSql = "SELECT id FROM world_chunks WHERE id = ?"
                    conn.prepareStatement(checkParentSql).use { stmt ->
                        stmt.setString(1, parentSubzoneId)
                        val rs = stmt.executeQuery()
                        rs.next()
                    }
                } else {
                    false
                }

                if (!parentExists && parentSubzoneId != null) {
                    return Result.failure(Exception("Cannot save space $chunkId: parent subzone $parentSubzoneId does not exist in database"))
                }

                val insertChunkSql = """
                    INSERT INTO world_chunks
                    (id, level, parent_id, children, lore, biome_theme, size_estimate, mob_density, difficulty_level)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()

                conn.prepareStatement(insertChunkSql).use { stmt ->
                    stmt.setString(1, chunkId)
                    stmt.setString(2, "SPACE")
                    if (parentSubzoneId != null && parentExists) {
                        stmt.setString(3, parentSubzoneId)
                    } else {
                        stmt.setNull(3, java.sql.Types.VARCHAR)
                    }
                    stmt.setString(4, "[]") // empty children
                    stmt.setString(5, "") // minimal lore
                    stmt.setString(6, "") // minimal biome_theme
                    stmt.setInt(7, 1) // size_estimate = 1 for space
                    stmt.setDouble(8, 0.0) // mob_density
                    stmt.setInt(9, 1) // difficulty_level
                    stmt.executeUpdate()
                }
            }

            // Now save space properties
            val sql = """
                INSERT OR REPLACE INTO space_properties
                (chunk_id, name, description, exits, brightness, terrain_type, traps, resources, entities, items_dropped, state_flags)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, chunkId)
                stmt.setString(2, properties.name)
                stmt.setString(3, properties.description)
                stmt.setString(4, json.encodeToString(properties.exits))
                stmt.setInt(5, properties.brightness)
                stmt.setString(6, properties.terrainType.name)
                stmt.setString(7, json.encodeToString(properties.traps))
                stmt.setString(8, json.encodeToString(properties.resources))
                stmt.setString(9, json.encodeToString(properties.entities))
                stmt.setString(10, json.encodeToString(properties.itemsDropped))
                stmt.setString(11, json.encodeToString(properties.stateFlags))
                stmt.executeUpdate()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun findByChunkId(chunkId: String): Result<SpacePropertiesComponent?> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM space_properties WHERE chunk_id = ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, chunkId)
                val rs = stmt.executeQuery()

                if (rs.next()) {
                    val properties = SpacePropertiesComponent(
                        name = rs.getString("name"),
                        description = rs.getString("description"),
                        exits = json.decodeFromString<List<ExitData>>(rs.getString("exits")),
                        brightness = rs.getInt("brightness"),
                        terrainType = TerrainType.valueOf(rs.getString("terrain_type")),
                        traps = json.decodeFromString<List<TrapData>>(rs.getString("traps")),
                        resources = json.decodeFromString<List<ResourceNode>>(rs.getString("resources")),
                        entities = json.decodeFromString<List<String>>(rs.getString("entities")),
                        itemsDropped = json.decodeFromString<List<ItemInstance>>(rs.getString("items_dropped")),
                        stateFlags = json.decodeFromString<Map<String, Boolean>>(rs.getString("state_flags"))
                    )
                    Result.success(properties)
                } else {
                    Result.success(null)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun updateDescription(chunkId: String, description: String): Result<Unit> {
        return try {
            val conn = database.getConnection()
            conn.prepareStatement("UPDATE space_properties SET description = ? WHERE chunk_id = ?").use { stmt ->
                stmt.setString(1, description)
                stmt.setString(2, chunkId)
                stmt.executeUpdate()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun updateFlags(chunkId: String, flags: Map<String, Boolean>): Result<Unit> {
        return try {
            val conn = database.getConnection()
            conn.prepareStatement("UPDATE space_properties SET state_flags = ? WHERE chunk_id = ?").use { stmt ->
                stmt.setString(1, json.encodeToString(flags))
                stmt.setString(2, chunkId)
                stmt.executeUpdate()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun addItems(chunkId: String, items: List<ItemInstance>): Result<Unit> {
        return try {
            val conn = database.getConnection()

            // First, fetch existing items
            val existingItems = findByChunkId(chunkId).getOrNull()?.itemsDropped ?: emptyList()

            // Merge with new items
            val updatedItems = existingItems + items

            // Update DB
            conn.prepareStatement("UPDATE space_properties SET items_dropped = ? WHERE chunk_id = ?").use { stmt ->
                stmt.setString(1, json.encodeToString(updatedItems))
                stmt.setString(2, chunkId)
                stmt.executeUpdate()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun delete(chunkId: String): Result<Unit> {
        return try {
            val conn = database.getConnection()
            conn.prepareStatement("DELETE FROM space_properties WHERE chunk_id = ?").use { stmt ->
                stmt.setString(1, chunkId)
                stmt.executeUpdate()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
