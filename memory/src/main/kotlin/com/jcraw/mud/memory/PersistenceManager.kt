package com.jcraw.mud.memory

import com.jcraw.mud.core.WorldState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Manages persistence of game state to disk.
 * Handles save/load operations for WorldState.
 */
class PersistenceManager(
    private val saveDirectory: String = "saves"
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    /**
     * Save the current world state to disk
     */
    fun saveGame(worldState: WorldState, saveName: String = "quicksave"): Result<Unit> {
        return try {
            val saveDir = File(saveDirectory)
            saveDir.mkdirs()

            val saveFile = File(saveDir, "$saveName.json")
            val jsonContent = json.encodeToString(worldState)
            saveFile.writeText(jsonContent)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Load a world state from disk
     */
    fun loadGame(saveName: String = "quicksave"): Result<WorldState> {
        return try {
            val saveFile = File(saveDirectory, "$saveName.json")

            if (!saveFile.exists()) {
                return Result.failure(IllegalStateException("Save file not found: ${saveFile.path}"))
            }

            val jsonContent = saveFile.readText()
            val worldState = json.decodeFromString<WorldState>(jsonContent)

            Result.success(worldState)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * List all available save files
     */
    fun listSaves(): List<String> {
        val saveDir = File(saveDirectory)
        if (!saveDir.exists()) return emptyList()

        return saveDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()
    }

    /**
     * Delete a save file
     */
    fun deleteSave(saveName: String): Result<Unit> {
        return try {
            val saveFile = File(saveDirectory, "$saveName.json")
            if (saveFile.exists()) {
                saveFile.delete()
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("Save file not found: ${saveFile.path}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
