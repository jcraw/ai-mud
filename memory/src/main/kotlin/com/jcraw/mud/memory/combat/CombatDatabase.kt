package com.jcraw.mud.memory.combat

import java.sql.Connection
import java.sql.DriverManager

/**
 * SQLite database for combat system persistence
 * Manages schema creation and connection lifecycle
 *
 * Database schema:
 * - combat_components: JSON-serialized CombatComponent per entity
 * - status_effects: Denormalized table for fast queries (entity_id, type, magnitude, duration)
 * - combat_events_log: Historical log of combat events (damage, healing, deaths, etc.)
 * - corpses: Persistent corpses with item contents and decay timers
 */
class CombatDatabase(
    private val dbPath: String = "combat.db"
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
            // Combat components table (JSON-serialized CombatComponent)
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS combat_components (
                    entity_id TEXT PRIMARY KEY,
                    component_data TEXT NOT NULL,
                    current_hp INTEGER NOT NULL,
                    max_hp INTEGER NOT NULL,
                    action_timer_end INTEGER NOT NULL DEFAULT 0,
                    position TEXT NOT NULL DEFAULT 'FRONT'
                )
                """.trimIndent()
            )

            // Status effects table (denormalized for fast queries)
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS status_effects (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    entity_id TEXT NOT NULL,
                    effect_type TEXT NOT NULL,
                    magnitude INTEGER NOT NULL,
                    duration INTEGER NOT NULL,
                    source TEXT NOT NULL,
                    UNIQUE(entity_id, effect_type, source)
                )
                """.trimIndent()
            )

            // Combat events log table
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS combat_events_log (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    game_time INTEGER NOT NULL,
                    event_type TEXT NOT NULL,
                    source_entity_id TEXT NOT NULL,
                    target_entity_id TEXT,
                    event_data TEXT NOT NULL,
                    timestamp INTEGER NOT NULL
                )
                """.trimIndent()
            )

            // Corpses table
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS corpses (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    location_room_id TEXT NOT NULL,
                    contents TEXT NOT NULL,
                    decay_timer INTEGER NOT NULL,
                    created_at INTEGER NOT NULL
                )
                """.trimIndent()
            )

            // Create indices for common queries
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_combat_timer ON combat_components(action_timer_end)")
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_combat_hp ON combat_components(current_hp)")
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_status_entity ON status_effects(entity_id)")
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_status_type ON status_effects(effect_type)")
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_status_duration ON status_effects(duration)")
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_events_entity ON combat_events_log(source_entity_id)")
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_events_target ON combat_events_log(target_entity_id)")
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_events_time ON combat_events_log(game_time)")
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_events_timestamp ON combat_events_log(timestamp)")
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_corpses_room ON corpses(location_room_id)")
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_corpses_decay ON corpses(decay_timer)")
        }
    }

    /**
     * Clear all data from database (for testing)
     */
    fun clearAll() {
        val conn = getConnection()
        conn.createStatement().use { stmt ->
            stmt.execute("DELETE FROM combat_components")
            stmt.execute("DELETE FROM status_effects")
            stmt.execute("DELETE FROM combat_events_log")
            stmt.execute("DELETE FROM corpses")
        }
    }
}
