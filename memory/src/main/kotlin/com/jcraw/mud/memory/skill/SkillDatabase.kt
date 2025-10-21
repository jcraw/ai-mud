package com.jcraw.mud.memory.skill

import java.sql.Connection
import java.sql.DriverManager

/**
 * SQLite database for skill system persistence
 * Manages schema creation and connection lifecycle
 *
 * Database schema:
 * - skill_components: JSON-serialized SkillComponent per entity
 * - skills: Denormalized table for fast queries (entity_id, skill_name, level, xp, etc.)
 * - skill_events_log: Historical log of skill events (unlock, xp gain, level up, perk choice)
 */
class SkillDatabase(
    private val dbPath: String = "skills.db"
) {
    private var connection: Connection? = null

    /**
     * Get or create database connection
     */
    fun getConnection(): Connection {
        if (connection == null || connection?.isClosed == true) {
            connection = DriverManager.getConnection("jdbc:sqlite:$dbPath")
            initializeSchema()
        }
        return connection!!
    }

    /**
     * Close database connection
     */
    fun close() {
        connection?.close()
        connection = null
    }

    /**
     * Initialize database schema if tables don't exist
     */
    private fun initializeSchema() {
        val conn = connection ?: return

        conn.createStatement().use { stmt ->
            // Skill components table (JSON-serialized SkillComponent)
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS skill_components (
                    entity_id TEXT PRIMARY KEY,
                    component_data TEXT NOT NULL
                )
                """.trimIndent()
            )

            // Skills table (denormalized for fast queries)
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS skills (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    entity_id TEXT NOT NULL,
                    skill_name TEXT NOT NULL,
                    level INTEGER NOT NULL DEFAULT 0,
                    xp INTEGER NOT NULL DEFAULT 0,
                    unlocked INTEGER NOT NULL DEFAULT 0,
                    tags TEXT,
                    perks TEXT,
                    resource_type TEXT,
                    temp_buffs INTEGER NOT NULL DEFAULT 0,
                    UNIQUE(entity_id, skill_name)
                )
                """.trimIndent()
            )

            // Skill events log table
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS skill_events_log (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    entity_id TEXT NOT NULL,
                    skill_name TEXT NOT NULL,
                    event_type TEXT NOT NULL,
                    event_data TEXT NOT NULL,
                    timestamp INTEGER NOT NULL
                )
                """.trimIndent()
            )

            // Create indices for common queries
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_skills_entity ON skills(entity_id)")
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_skills_name ON skills(skill_name)")
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_skills_tags ON skills(tags)")
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_events_entity ON skill_events_log(entity_id)")
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_events_skill ON skill_events_log(entity_id, skill_name)")
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_events_timestamp ON skill_events_log(timestamp)")
        }
    }

    /**
     * Clear all data from database (for testing)
     */
    fun clearAll() {
        val conn = getConnection()
        conn.createStatement().use { stmt ->
            stmt.execute("DELETE FROM skill_components")
            stmt.execute("DELETE FROM skills")
            stmt.execute("DELETE FROM skill_events_log")
        }
    }
}
