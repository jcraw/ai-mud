package com.jcraw.mud.memory.world

import com.jcraw.mud.core.repository.WorldSeedRepository

/**
 * SQLite implementation of WorldSeedRepository
 * Manages world seed persistence (singleton pattern with id=1)
 */
class SQLiteWorldSeedRepository(
    private val database: WorldDatabase
) : WorldSeedRepository {

    override fun save(seed: String, globalLore: String): Result<Unit> {
        return try {
            val conn = database.getConnection()
            val sql = """
                INSERT OR REPLACE INTO world_seed
                (id, seed_string, global_lore)
                VALUES (1, ?, ?)
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, seed)
                stmt.setString(2, globalLore)
                stmt.executeUpdate()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun get(): Result<Pair<String, String>?> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT seed_string, global_lore FROM world_seed WHERE id = 1"

            conn.prepareStatement(sql).use { stmt ->
                val rs = stmt.executeQuery()

                if (rs.next()) {
                    val seed = rs.getString("seed_string")
                    val lore = rs.getString("global_lore")
                    Result.success(seed to lore)
                } else {
                    Result.success(null)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
