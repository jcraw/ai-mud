package com.jcraw.mud.core

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Shared database configuration for all clients (console, GUI, Discord, etc).
 * Ensures all clients use the same persistent data directory.
 */
object DatabaseConfig {
    /**
     * Root data directory for all persistent storage.
     * This is relative to the working directory when the application is launched.
     */
    private val DATA_DIR: Path = Paths.get("data")

    /**
     * Initialize the data directory (create if doesn't exist).
     * This should be called once at application startup.
     */
    fun init() {
        if (!Files.exists(DATA_DIR)) {
            Files.createDirectories(DATA_DIR)
        }
    }

    /**
     * Get the absolute path for a database file.
     */
    private fun getPath(filename: String): String {
        return DATA_DIR.resolve(filename).toString()
    }

    /**
     * World generation database (chunks, spaces, entities, seeds).
     */
    val WORLD_DB: String get() = getPath("world.db")

    /**
     * Item system database (templates, instances, inventories, recipes, trading).
     */
    val ITEMS_DB: String get() = getPath("items.db")

    /**
     * Social system database (dispositions, knowledge, social events).
     */
    val SOCIAL_DB: String get() = getPath("social.db")

    /**
     * Skill system database (skills, skill components, progression).
     */
    val SKILLS_DB: String get() = getPath("skills.db")
}
