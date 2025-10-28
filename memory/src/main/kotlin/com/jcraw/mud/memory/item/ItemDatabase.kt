package com.jcraw.mud.memory.item

import java.sql.Connection
import java.sql.DriverManager

/**
 * SQLite database for item system persistence
 * Manages schema creation and connection lifecycle
 *
 * Database schema:
 * - item_templates: Item template definitions loaded from JSON
 * - item_instances: Individual item instances with quality/charges/quantity
 * - inventories: Entity inventory state (items, equipped, gold, capacity)
 */
class ItemDatabase(
    private val dbPath: String = "items.db"
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
            // Item templates table (template definitions)
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS item_templates (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    type TEXT NOT NULL,
                    tags TEXT NOT NULL,
                    properties TEXT NOT NULL,
                    rarity TEXT NOT NULL DEFAULT 'COMMON',
                    description TEXT NOT NULL,
                    equip_slot TEXT
                )
                """.trimIndent()
            )

            // Item instances table (actual item instances)
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS item_instances (
                    id TEXT PRIMARY KEY,
                    template_id TEXT NOT NULL,
                    quality INTEGER NOT NULL DEFAULT 5,
                    charges INTEGER,
                    quantity INTEGER NOT NULL DEFAULT 1,
                    FOREIGN KEY (template_id) REFERENCES item_templates(id)
                )
                """.trimIndent()
            )

            // Inventories table (entity inventory state)
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS inventories (
                    entity_id TEXT PRIMARY KEY,
                    items TEXT NOT NULL,
                    equipped TEXT NOT NULL,
                    gold INTEGER NOT NULL DEFAULT 0,
                    capacity_weight REAL NOT NULL DEFAULT 50.0
                )
                """.trimIndent()
            )

            // Recipes table (crafting recipes)
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS recipes (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    input_items TEXT NOT NULL,
                    output_item TEXT NOT NULL,
                    required_skill TEXT NOT NULL,
                    min_skill_level INTEGER NOT NULL,
                    required_tools TEXT,
                    difficulty INTEGER NOT NULL
                )
                """.trimIndent()
            )

            // Create indices for common queries
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_templates_type ON item_templates(type)")
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_templates_rarity ON item_templates(rarity)")
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_instances_template ON item_instances(template_id)")
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_instances_quality ON item_instances(quality)")
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_recipes_skill ON recipes(required_skill)")
        }
    }

    /**
     * Clear all data from database (for testing)
     */
    fun clearAll() {
        val conn = getConnection()
        conn.createStatement().use { stmt ->
            stmt.execute("DELETE FROM inventories")
            stmt.execute("DELETE FROM item_instances")
            stmt.execute("DELETE FROM item_templates")
            stmt.execute("DELETE FROM recipes")
        }
    }
}
