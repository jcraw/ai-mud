package com.jcraw.mud.memory.world

import com.jcraw.mud.core.RespawnComponent
import com.jcraw.mud.core.repository.RespawnRepository

/**
 * SQLite implementation of RespawnRepository
 * Manages respawn component persistence with direct field storage
 */
class SQLiteRespawnRepository(
    private val database: WorldDatabase
) : RespawnRepository {

    override fun save(component: RespawnComponent, entityId: String, spaceId: String): Result<Unit> {
        return try {
            val conn = database.getConnection()
            val sql = """
                INSERT OR REPLACE INTO respawn_components
                (entity_id, space_id, respawn_turns, last_killed, original_entity_id)
                VALUES (?, ?, ?, ?, ?)
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, entityId)
                stmt.setString(2, spaceId)
                stmt.setLong(3, component.respawnTurns)
                stmt.setLong(4, component.lastKilled)
                stmt.setString(5, component.originalEntityId)
                stmt.executeUpdate()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun findByEntityId(entityId: String): Result<Pair<RespawnComponent, String>?> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM respawn_components WHERE entity_id = ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, entityId)
                val rs = stmt.executeQuery()

                if (rs.next()) {
                    val component = RespawnComponent(
                        respawnTurns = rs.getLong("respawn_turns"),
                        lastKilled = rs.getLong("last_killed"),
                        originalEntityId = rs.getString("original_entity_id")
                    )
                    val spaceId = rs.getString("space_id")
                    Result.success(component to spaceId)
                } else {
                    Result.success(null)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun findBySpaceId(spaceId: String): Result<List<Triple<String, String, RespawnComponent>>> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM respawn_components WHERE space_id = ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, spaceId)
                val rs = stmt.executeQuery()
                val results = mutableListOf<Triple<String, String, RespawnComponent>>()

                while (rs.next()) {
                    val entityId = rs.getString("entity_id")
                    val space = rs.getString("space_id")
                    val component = RespawnComponent(
                        respawnTurns = rs.getLong("respawn_turns"),
                        lastKilled = rs.getLong("last_killed"),
                        originalEntityId = rs.getString("original_entity_id")
                    )
                    results.add(Triple(entityId, space, component))
                }
                Result.success(results)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun findReadyToRespawn(currentTime: Long): Result<List<Triple<String, String, RespawnComponent>>> {
        return try {
            val conn = database.getConnection()
            val sql = """
                SELECT * FROM respawn_components
                WHERE last_killed > 0 AND (? - last_killed) >= respawn_turns
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, currentTime)
                val rs = stmt.executeQuery()
                val results = mutableListOf<Triple<String, String, RespawnComponent>>()

                while (rs.next()) {
                    val entityId = rs.getString("entity_id")
                    val spaceId = rs.getString("space_id")
                    val component = RespawnComponent(
                        respawnTurns = rs.getLong("respawn_turns"),
                        lastKilled = rs.getLong("last_killed"),
                        originalEntityId = rs.getString("original_entity_id")
                    )
                    results.add(Triple(entityId, spaceId, component))
                }
                Result.success(results)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun markKilled(entityId: String, gameTime: Long): Result<Unit> {
        return try {
            val conn = database.getConnection()
            conn.prepareStatement("UPDATE respawn_components SET last_killed = ? WHERE entity_id = ?").use { stmt ->
                stmt.setLong(1, gameTime)
                stmt.setString(2, entityId)
                stmt.executeUpdate()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun resetTimer(entityId: String): Result<Unit> {
        return try {
            val conn = database.getConnection()
            conn.prepareStatement("UPDATE respawn_components SET last_killed = 0 WHERE entity_id = ?").use { stmt ->
                stmt.setString(1, entityId)
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
            conn.prepareStatement("DELETE FROM respawn_components WHERE entity_id = ?").use { stmt ->
                stmt.setString(1, entityId)
                stmt.executeUpdate()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getAll(): Result<Map<String, Pair<String, RespawnComponent>>> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM respawn_components"

            conn.prepareStatement(sql).use { stmt ->
                val rs = stmt.executeQuery()
                val results = mutableMapOf<String, Pair<String, RespawnComponent>>()

                while (rs.next()) {
                    val entityId = rs.getString("entity_id")
                    val spaceId = rs.getString("space_id")
                    val component = RespawnComponent(
                        respawnTurns = rs.getLong("respawn_turns"),
                        lastKilled = rs.getLong("last_killed"),
                        originalEntityId = rs.getString("original_entity_id")
                    )
                    results[entityId] = spaceId to component
                }
                Result.success(results)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
