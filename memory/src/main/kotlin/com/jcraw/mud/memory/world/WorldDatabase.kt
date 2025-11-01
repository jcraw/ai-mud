package com.jcraw.mud.memory.world

import java.sql.Connection
import java.sql.DriverManager

/**
 * SQLite database for world generation system persistence
 * Manages schema creation and connection lifecycle
 *
 * Database schema:
 * - world_seed: Global seed and lore for world generation (singleton)
 * - world_chunks: Hierarchical world chunks (WORLD/REGION/ZONE/SUBZONE/SPACE levels)
 * - space_properties: Detailed space properties (descriptions, exits, content)
 * - respawn_components: Mob respawn timers and regeneration state
 * - corpses: Player death corpses with inventory/equipment (Dark Souls-style)
 */
class WorldDatabase(
    private val dbPath: String = "world.db"
) {
    private var connection: Connection? = null

    /**
     * Get or create database connection
     */
    fun getConnection(): Connection {
        if (connection == null || connection?.isClosed == true) {
            connection = DriverManager.getConnection("jdbc:sqlite:$dbPath")
            connection!!.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
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
            // World seed table (singleton for global world state)
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS world_seed (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    seed_string TEXT NOT NULL,
                    global_lore TEXT NOT NULL
                )
                """.trimIndent()
            )

            // World chunks table (hierarchical structure)
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS world_chunks (
                    id TEXT PRIMARY KEY,
                    level TEXT NOT NULL,
                    parent_id TEXT,
                    children TEXT NOT NULL,
                    lore TEXT NOT NULL,
                    biome_theme TEXT NOT NULL,
                    size_estimate INTEGER NOT NULL,
                    mob_density REAL NOT NULL,
                    difficulty_level INTEGER NOT NULL,
                    FOREIGN KEY (parent_id) REFERENCES world_chunks(id)
                )
                """.trimIndent()
            )

            // Space properties table (detailed space data)
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS space_properties (
                    chunk_id TEXT PRIMARY KEY,
                    description TEXT NOT NULL,
                    exits TEXT NOT NULL,
                    brightness INTEGER NOT NULL,
                    terrain_type TEXT NOT NULL,
                    traps TEXT NOT NULL,
                    resources TEXT NOT NULL,
                    entities TEXT NOT NULL,
                    items_dropped TEXT NOT NULL,
                    state_flags TEXT NOT NULL,
                    FOREIGN KEY (chunk_id) REFERENCES world_chunks(id)
                )
                """.trimIndent()
            )

            // Respawn components table (mob respawn timers)
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS respawn_components (
                    entity_id TEXT PRIMARY KEY,
                    space_id TEXT NOT NULL,
                    respawn_turns INTEGER NOT NULL,
                    last_killed INTEGER NOT NULL,
                    original_entity_id TEXT NOT NULL
                )
                """.trimIndent()
            )

            // Corpses table (player death handling)
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS corpses (
                    id TEXT PRIMARY KEY,
                    player_id TEXT NOT NULL,
                    space_id TEXT NOT NULL,
                    inventory TEXT NOT NULL,
                    equipment TEXT NOT NULL,
                    gold INTEGER NOT NULL,
                    decay_timer INTEGER NOT NULL,
                    looted INTEGER NOT NULL
                )
                """.trimIndent()
            )

            // Create indices for common queries
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_chunks_parent ON world_chunks(parent_id)")
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_chunks_level ON world_chunks(level)")
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_space_chunk ON space_properties(chunk_id)")
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_respawn_space ON respawn_components(space_id)")
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_corpse_space ON corpses(space_id)")
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_corpse_player ON corpses(player_id)")
        }
    }

    /**
     * Clear all data from database (for testing/no backward compatibility)
     */
    fun clearAll() {
        val conn = getConnection()
        conn.createStatement().use { stmt ->
            stmt.execute("DELETE FROM corpses")
            stmt.execute("DELETE FROM respawn_components")
            stmt.execute("DELETE FROM space_properties")
            stmt.execute("DELETE FROM world_chunks")
            stmt.execute("DELETE FROM world_seed")
        }
    }
}
