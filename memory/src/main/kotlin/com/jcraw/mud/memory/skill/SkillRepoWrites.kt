@file:Suppress(
    "ReturnCount",
    "MagicNumber",
    "MaxLineLength",
    "TooManyFunctions",
    "LongMethod",
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "LongParameterList",
    "TooGenericExceptionCaught",
    "SwallowedException",
    "ThrowsCount",
    "UnusedParameter"
)

package com.jcraw.mud.memory.skill

import com.jcraw.mud.core.SkillState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Skill write/delete path for [SQLiteSkillRepository] (MUD-034m pure-move).
 */
internal object SkillRepoWrites {

    fun save(
        database: SkillDatabase,
        json: Json,
        entityId: String,
        skillName: String,
        skillState: SkillState
    ): Result<Unit> {
        return try {
            val conn = database.getConnection()
            conn.prepareStatement(insertSql()).use { stmt ->
                bindSave(stmt, json, entityId, skillName, skillState)
                stmt.executeUpdate()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun insertSql(): String = """
                INSERT OR REPLACE INTO skills
                (entity_id, skill_name, level, xp, unlocked, tags, perks, resource_type, temp_buffs)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    """.trimIndent()

    private fun bindSave(
        stmt: java.sql.PreparedStatement,
        json: Json,
        entityId: String,
        skillName: String,
        skillState: SkillState
    ) {
        stmt.setString(1, entityId)
        stmt.setString(2, skillName)
        stmt.setInt(3, skillState.level)
        stmt.setLong(4, skillState.xp)
        stmt.setInt(5, if (skillState.unlocked) 1 else 0)
        stmt.setString(6, json.encodeToString(skillState.tags))
        stmt.setString(7, json.encodeToString(skillState.perks))
        stmt.setString(8, skillState.resourceType)
        stmt.setInt(9, skillState.tempBuffs)
    }

    fun updateXp(
        database: SkillDatabase,
        entityId: String,
        skillName: String,
        newXp: Long,
        newLevel: Int
    ): Result<Unit> {
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

    fun unlockSkill(database: SkillDatabase, entityId: String, skillName: String): Result<Unit> {
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

    fun delete(database: SkillDatabase, entityId: String, skillName: String): Result<Unit> {
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

    fun deleteAllForEntity(database: SkillDatabase, entityId: String): Result<Unit> {
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
}
