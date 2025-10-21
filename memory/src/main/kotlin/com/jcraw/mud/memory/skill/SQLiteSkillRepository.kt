package com.jcraw.mud.memory.skill

import com.jcraw.mud.core.Perk
import com.jcraw.mud.core.SkillEvent
import com.jcraw.mud.core.SkillState
import com.jcraw.mud.core.repository.SkillRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * SQLite implementation of SkillRepository
 * Manages denormalized skill table and event log
 */
class SQLiteSkillRepository(
    private val database: SkillDatabase
) : SkillRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun findByEntityAndSkill(entityId: String, skillName: String): Result<SkillState?> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM skills WHERE entity_id = ? AND skill_name = ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, entityId)
                stmt.setString(2, skillName)
                val rs = stmt.executeQuery()

                if (rs.next()) {
                    val skillState = SkillState(
                        level = rs.getInt("level"),
                        xp = rs.getLong("xp"),
                        unlocked = rs.getInt("unlocked") == 1,
                        tags = json.decodeFromString(rs.getString("tags") ?: "[]"),
                        perks = json.decodeFromString(rs.getString("perks") ?: "[]"),
                        resourceType = rs.getString("resource_type"),
                        tempBuffs = rs.getInt("temp_buffs")
                    )
                    Result.success(skillState)
                } else {
                    Result.success(null)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun findByEntityId(entityId: String): Result<Map<String, SkillState>> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM skills WHERE entity_id = ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, entityId)
                val rs = stmt.executeQuery()

                val skills = mutableMapOf<String, SkillState>()
                while (rs.next()) {
                    val skillName = rs.getString("skill_name")
                    val skillState = SkillState(
                        level = rs.getInt("level"),
                        xp = rs.getLong("xp"),
                        unlocked = rs.getInt("unlocked") == 1,
                        tags = json.decodeFromString(rs.getString("tags") ?: "[]"),
                        perks = json.decodeFromString(rs.getString("perks") ?: "[]"),
                        resourceType = rs.getString("resource_type"),
                        tempBuffs = rs.getInt("temp_buffs")
                    )
                    skills[skillName] = skillState
                }
                Result.success(skills)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun findByTag(tag: String): Result<Map<Pair<String, String>, SkillState>> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM skills WHERE tags LIKE ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, "%\"$tag\"%")
                val rs = stmt.executeQuery()

                val skills = mutableMapOf<Pair<String, String>, SkillState>()
                while (rs.next()) {
                    val entityId = rs.getString("entity_id")
                    val skillName = rs.getString("skill_name")
                    val skillState = SkillState(
                        level = rs.getInt("level"),
                        xp = rs.getLong("xp"),
                        unlocked = rs.getInt("unlocked") == 1,
                        tags = json.decodeFromString(rs.getString("tags") ?: "[]"),
                        perks = json.decodeFromString(rs.getString("perks") ?: "[]"),
                        resourceType = rs.getString("resource_type"),
                        tempBuffs = rs.getInt("temp_buffs")
                    )
                    skills[entityId to skillName] = skillState
                }
                Result.success(skills)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun save(entityId: String, skillName: String, skillState: SkillState): Result<Unit> {
        return try {
            val conn = database.getConnection()
            val sql = """
                INSERT OR REPLACE INTO skills
                (entity_id, skill_name, level, xp, unlocked, tags, perks, resource_type, temp_buffs)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, entityId)
                stmt.setString(2, skillName)
                stmt.setInt(3, skillState.level)
                stmt.setLong(4, skillState.xp)
                stmt.setInt(5, if (skillState.unlocked) 1 else 0)
                stmt.setString(6, json.encodeToString(skillState.tags))
                stmt.setString(7, json.encodeToString(skillState.perks))
                stmt.setString(8, skillState.resourceType)
                stmt.setInt(9, skillState.tempBuffs)
                stmt.executeUpdate()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun updateXp(entityId: String, skillName: String, newXp: Long, newLevel: Int): Result<Unit> {
        return try {
            val conn = database.getConnection()
            val sql = """
                UPDATE skills
                SET xp = ?, level = ?
                WHERE entity_id = ? AND skill_name = ?
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, newXp)
                stmt.setInt(2, newLevel)
                stmt.setString(3, entityId)
                stmt.setString(4, skillName)
                stmt.executeUpdate()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun unlockSkill(entityId: String, skillName: String): Result<Unit> {
        return try {
            val conn = database.getConnection()
            val sql = """
                UPDATE skills
                SET unlocked = 1
                WHERE entity_id = ? AND skill_name = ?
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, entityId)
                stmt.setString(2, skillName)
                stmt.executeUpdate()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun delete(entityId: String, skillName: String): Result<Unit> {
        return try {
            val conn = database.getConnection()
            val sql = "DELETE FROM skills WHERE entity_id = ? AND skill_name = ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, entityId)
                stmt.setString(2, skillName)
                stmt.executeUpdate()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun deleteAllForEntity(entityId: String): Result<Unit> {
        return try {
            val conn = database.getConnection()
            val sql = "DELETE FROM skills WHERE entity_id = ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, entityId)
                stmt.executeUpdate()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun logEvent(event: SkillEvent): Result<Unit> {
        return try {
            val conn = database.getConnection()
            val sql = """
                INSERT INTO skill_events_log
                (entity_id, skill_name, event_type, event_data, timestamp)
                VALUES (?, ?, ?, ?, ?)
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, event.entityId)
                stmt.setString(2, event.skillName)
                stmt.setString(3, event.eventType)
                stmt.setString(4, json.encodeToString(SkillEvent.serializer(), event))
                stmt.setLong(5, event.timestamp)
                stmt.executeUpdate()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getEventHistory(
        entityId: String,
        skillName: String?,
        limit: Int
    ): Result<List<SkillEvent>> {
        return try {
            val conn = database.getConnection()
            val sql = if (skillName != null) {
                """
                SELECT * FROM skill_events_log
                WHERE entity_id = ? AND skill_name = ?
                ORDER BY timestamp DESC
                LIMIT ?
                """.trimIndent()
            } else {
                """
                SELECT * FROM skill_events_log
                WHERE entity_id = ?
                ORDER BY timestamp DESC
                LIMIT ?
                """.trimIndent()
            }

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, entityId)
                if (skillName != null) {
                    stmt.setString(2, skillName)
                    stmt.setInt(3, limit)
                } else {
                    stmt.setInt(2, limit)
                }

                val rs = stmt.executeQuery()
                val events = mutableListOf<SkillEvent>()

                while (rs.next()) {
                    val eventData = rs.getString("event_data")
                    val event = json.decodeFromString(SkillEvent.serializer(), eventData)
                    events.add(event)
                }

                Result.success(events)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
