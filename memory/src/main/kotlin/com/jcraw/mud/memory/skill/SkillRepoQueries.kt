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
import kotlinx.serialization.json.Json

/**
 * Skill query path for [SQLiteSkillRepository] (MUD-034m pure-move).
 */
internal object SkillRepoQueries {

    fun findByEntityAndSkill(
        database: SkillDatabase,
        json: Json,
        entityId: String,
        skillName: String
    ): Result<SkillState?> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM skills WHERE entity_id = ? AND skill_name = ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, entityId)
                stmt.setString(2, skillName)
                val rs = stmt.executeQuery()

                if (rs.next()) {
                    Result.success(SkillRepoMapping.skillStateFrom(rs, json))
                } else {
                    Result.success(null)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun findByEntityId(
        database: SkillDatabase,
        json: Json,
        entityId: String
    ): Result<Map<String, SkillState>> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM skills WHERE entity_id = ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, entityId)
                val rs = stmt.executeQuery()

                val skills = mutableMapOf<String, SkillState>()
                while (rs.next()) {
                    val name = rs.getString("skill_name")
                    skills[name] = SkillRepoMapping.skillStateFrom(rs, json)
                }
                Result.success(skills)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun findByTag(
        database: SkillDatabase,
        json: Json,
        tag: String
    ): Result<Map<Pair<String, String>, SkillState>> {
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
                    skills[entityId to skillName] = SkillRepoMapping.skillStateFrom(rs, json)
                }
                Result.success(skills)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
