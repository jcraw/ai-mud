package com.jcraw.mud.memory.world

import com.jcraw.mud.core.WorldChunkComponent
import com.jcraw.mud.core.repository.WorldChunkRepository
import com.jcraw.mud.core.world.ChunkLevel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * SQLite implementation of WorldChunkRepository
 * Manages world chunk persistence with JSON serialization
 */
class SQLiteWorldChunkRepository(
    private val database: WorldDatabase
) : WorldChunkRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun save(chunk: WorldChunkComponent, id: String): Result<Unit> {
        return try {
            val conn = database.getConnection()
            val sql = """
                INSERT OR REPLACE INTO world_chunks
                (id, level, parent_id, children, lore, biome_theme, size_estimate, mob_density, difficulty_level, adjacency)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, id)
                stmt.setString(2, chunk.level.name)
                if (chunk.parentId != null) {
                    stmt.setString(3, chunk.parentId)
                } else {
                    stmt.setNull(3, java.sql.Types.VARCHAR)
                }
                stmt.setString(4, json.encodeToString(chunk.children))
                stmt.setString(5, chunk.lore)
                stmt.setString(6, chunk.biomeTheme)
                stmt.setInt(7, chunk.sizeEstimate)
                stmt.setDouble(8, chunk.mobDensity)
                stmt.setInt(9, chunk.difficultyLevel)
                stmt.setString(10, json.encodeToString(chunk.adjacency))
                stmt.executeUpdate()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun findById(id: String): Result<WorldChunkComponent?> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM world_chunks WHERE id = ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, id)
                val rs = stmt.executeQuery()

                if (rs.next()) {
                    val chunk = WorldChunkComponent(
                        level = ChunkLevel.valueOf(rs.getString("level")),
                        parentId = rs.getString("parent_id"),
                        children = json.decodeFromString<List<String>>(rs.getString("children")),
                        lore = rs.getString("lore"),
                        biomeTheme = rs.getString("biome_theme"),
                        sizeEstimate = rs.getInt("size_estimate"),
                        mobDensity = rs.getDouble("mob_density"),
                        difficultyLevel = rs.getInt("difficulty_level"),
                        adjacency = json.decodeFromString<Map<String, String>>(rs.getString("adjacency"))
                    )
                    Result.success(chunk)
                } else {
                    Result.success(null)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun findByParent(parentId: String): Result<List<Pair<String, WorldChunkComponent>>> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM world_chunks WHERE parent_id = ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, parentId)
                val rs = stmt.executeQuery()
                val chunks = mutableListOf<Pair<String, WorldChunkComponent>>()

                while (rs.next()) {
                    val id = rs.getString("id")
                    val chunk = WorldChunkComponent(
                        level = ChunkLevel.valueOf(rs.getString("level")),
                        parentId = rs.getString("parent_id"),
                        children = json.decodeFromString<List<String>>(rs.getString("children")),
                        lore = rs.getString("lore"),
                        biomeTheme = rs.getString("biome_theme"),
                        sizeEstimate = rs.getInt("size_estimate"),
                        mobDensity = rs.getDouble("mob_density"),
                        difficultyLevel = rs.getInt("difficulty_level"),
                        adjacency = json.decodeFromString<Map<String, String>>(rs.getString("adjacency"))
                    )
                    chunks.add(id to chunk)
                }
                Result.success(chunks)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun findAdjacent(currentId: String, direction: String): Result<WorldChunkComponent?> {
        return try {
            val normalized = direction.trim().lowercase()
            val currentChunk = findById(currentId).getOrThrow() ?: return Result.success(null)
            val targetId = currentChunk.adjacency[normalized] ?: return Result.success(null)
            findById(targetId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun delete(id: String): Result<Unit> {
        return try {
            val conn = database.getConnection()
            conn.prepareStatement("DELETE FROM world_chunks WHERE id = ?").use { stmt ->
                stmt.setString(1, id)
                stmt.executeUpdate()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getAll(): Result<Map<String, WorldChunkComponent>> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM world_chunks"

            conn.prepareStatement(sql).use { stmt ->
                val rs = stmt.executeQuery()
                val chunks = mutableMapOf<String, WorldChunkComponent>()

                while (rs.next()) {
                    val id = rs.getString("id")
                    val chunk = WorldChunkComponent(
                        level = ChunkLevel.valueOf(rs.getString("level")),
                        parentId = rs.getString("parent_id"),
                        children = json.decodeFromString<List<String>>(rs.getString("children")),
                        lore = rs.getString("lore"),
                        biomeTheme = rs.getString("biome_theme"),
                        sizeEstimate = rs.getInt("size_estimate"),
                        mobDensity = rs.getDouble("mob_density"),
                        difficultyLevel = rs.getInt("difficulty_level"),
                        adjacency = json.decodeFromString<Map<String, String>>(rs.getString("adjacency"))
                    )
                    chunks[id] = chunk
                }
                Result.success(chunks)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
