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

package com.jcraw.mud.memory.combat

import com.jcraw.mud.core.CombatComponent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Combat component CRUD for [SQLiteCombatRepository] (MUD-034m pure-move).
 */
internal object CombatRepoComponents {

    fun findByEntityId(
        database: CombatDatabase,
        json: Json,
        entityId: String
    ): Result<CombatComponent?> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT component_data FROM combat_components WHERE entity_id = ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, entityId)
                val rs = stmt.executeQuery()

                if (rs.next()) {
                    val componentData = rs.getString("component_data")
                    val component = json.decodeFromString<CombatComponent>(componentData)
                    Result.success(component)
                } else {
                    Result.success(null)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun save(
        database: CombatDatabase,
        json: Json,
        entityId: String,
        component: CombatComponent
    ): Result<Unit> {
        return try {
            insertComponent(database, json, entityId, component)
            // Also update status_effects table
            CombatRepoEffects.saveStatusEffects(database, entityId, component.statusEffects)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun insertComponent(
        database: CombatDatabase,
        json: Json,
        entityId: String,
        component: CombatComponent
    ) {
        val conn = database.getConnection()
        val sql = """
                INSERT OR REPLACE INTO combat_components
                (entity_id, component_data, current_hp, max_hp, action_timer_end, position)
                VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent()

        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, entityId)
            stmt.setString(2, json.encodeToString(component))
            stmt.setInt(3, component.currentHp)
            stmt.setInt(4, component.maxHp)
            stmt.setLong(5, component.actionTimerEnd)
            stmt.setString(6, component.position.name)
            stmt.executeUpdate()
        }
    }

    fun delete(database: CombatDatabase, entityId: String): Result<Unit> {
        return try {
            val conn = database.getConnection()

            // Delete from combat_components
            conn.prepareStatement("DELETE FROM combat_components WHERE entity_id = ?").use { stmt ->
                stmt.setString(1, entityId)
                stmt.executeUpdate()
            }

            // Delete from status_effects
            conn.prepareStatement("DELETE FROM status_effects WHERE entity_id = ?").use { stmt ->
                stmt.setString(1, entityId)
                stmt.executeUpdate()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun updateHp(database: CombatDatabase, json: Json, entityId: String, newHp: Int): Result<Unit> {
        return try {
            // Load current component
            val currentResult = findByEntityId(database, json, entityId)
            if (currentResult.isFailure || currentResult.getOrNull() == null) {
                return Result.failure(IllegalStateException("Entity $entityId has no combat component"))
            }

            // Update HP and save
            val updated = currentResult.getOrNull()!!.copy(currentHp = newHp)
            save(database, json, entityId, updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun findAll(database: CombatDatabase, json: Json): Result<Map<String, CombatComponent>> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT entity_id, component_data FROM combat_components"

            conn.prepareStatement(sql).use { stmt ->
                val rs = stmt.executeQuery()

                val components = mutableMapOf<String, CombatComponent>()
                while (rs.next()) {
                    val entityId = rs.getString("entity_id")
                    val componentData = rs.getString("component_data")
                    val component = json.decodeFromString<CombatComponent>(componentData)
                    components[entityId] = component
                }
                Result.success(components)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
