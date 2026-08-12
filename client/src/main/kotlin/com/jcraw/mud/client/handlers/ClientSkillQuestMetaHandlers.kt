@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList")

package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.GameEvent

/**
 * Meta/persistence handlers for [ClientSkillQuestHandlers] facade.
 * Pure extract: save, load, help, quit.
 */
object ClientSkillQuestMetaHandlers {

    private val HELP_TEXT = """
        |Available Commands:
        |  Movement: north, south, east, west (or n, s, e, w)
        |  Actions: look [target], take <item>, drop <item>, talk <npc>
        |  Combat: attack <npc>
        |  Equipment: equip <item>, use <item>
        |  Skills: check <feature>, persuade <npc>, intimidate <npc>
        |  Quests: quests, accept <id>, claim <id>
        |  Meta: inventory/i, save [name], load [name], help, quit
    """.trimMargin()

    fun handleSave(game: EngineGameClient, saveName: String) {
        val result = game.persistenceManager.saveGame(game.worldState, saveName)
        result.onSuccess {
            game.emitEvent(GameEvent.System("💾 Game saved as '$saveName'", GameEvent.MessageLevel.INFO))
        }.onFailure { error ->
            game.emitEvent(GameEvent.System("❌ Failed to save game: ${error.message}", GameEvent.MessageLevel.ERROR))
        }
    }

    fun handleLoad(game: EngineGameClient, saveName: String) {
        val result = game.persistenceManager.loadGame(saveName)
        result.onSuccess { loadedState ->
            game.worldState = loadedState
            game.emitEvent(GameEvent.System("📂 Game loaded from '$saveName'", GameEvent.MessageLevel.INFO))
            game.describeCurrentRoom()
        }.onFailure { error ->
            emitLoadFailure(game, error)
        }
    }

    private fun emitLoadFailure(game: EngineGameClient, error: Throwable) {
        game.emitEvent(GameEvent.System("❌ Failed to load game: ${error.message}", GameEvent.MessageLevel.ERROR))
        val saves = game.persistenceManager.listSaves()
        if (saves.isNotEmpty()) {
            game.emitEvent(GameEvent.System("Available saves: ${saves.joinToString(", ")}", GameEvent.MessageLevel.INFO))
        } else {
            game.emitEvent(GameEvent.System("No saved games found.", GameEvent.MessageLevel.INFO))
        }
    }

    fun handleHelp(game: EngineGameClient) {
        game.emitEvent(GameEvent.System(HELP_TEXT, GameEvent.MessageLevel.INFO))
    }

    fun handleQuit(game: EngineGameClient) {
        game.emitEvent(GameEvent.System("Goodbye!", GameEvent.MessageLevel.INFO))
        game.running = false
    }
}
