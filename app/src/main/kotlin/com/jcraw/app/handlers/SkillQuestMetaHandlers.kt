@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList")

package com.jcraw.app.handlers

import com.jcraw.app.MudGame

/**
 * Meta/persistence handlers for [SkillQuestHandlers] facade.
 * Pure extract: save, load, help, quit.
 */
object SkillQuestMetaHandlers {

    private val HELP_TEXT = """
        |Available Commands:
        |  Movement:
        |    go <direction>, n/s/e/w, north/south/east/west, etc.
        |
        |  Actions:
        |    look [target]        - Examine room or specific object
        |    search [target]      - Search for hidden items (skill check)
        |    take/get <item>      - Pick up an item
        |    drop/put <item>      - Drop an item from inventory
        |    give <item> to <npc> - Give an item to an NPC
        |    talk/speak <npc>     - Talk to an NPC
        |    attack/fight <npc>   - Attack an NPC or continue combat
        |    equip/wield <item>   - Equip a weapon or armor from inventory
        |    use/consume <item>   - Use a consumable item (potion, etc.)
        |    check/test <feature> - Attempt a skill check on an interactive feature
        |    interact/harvest/gather <resource> - Harvest resources (ore, herbs, etc.)
        |    craft <recipe>       - Craft an item using a recipe
        |    persuade <npc>       - Attempt to persuade an NPC (CHA check)
        |    intimidate <npc>     - Attempt to intimidate an NPC (CHA check)
        |    inventory, i         - View your inventory and equipped items
        |
        |  Quests:
        |    quests, quest, journal, j - View quest log and available quests
        |    accept <quest_id>    - Accept an available quest
        |    abandon <quest_id>   - Abandon an active quest
        |    claim <quest_id>     - Claim reward for a completed quest
        |
        |  Meta:
        |    save [name]          - Save game (defaults to 'quicksave')
        |    load [name]          - Load game (defaults to 'quicksave')
        |    help, h, ?           - Show this help
        |    quit, exit, q        - Quit game
    """.trimMargin()

    fun handleSave(game: MudGame, saveName: String) {
        val result = game.persistenceManager.saveGame(game.worldState, saveName)
        result.onSuccess {
            println("💾 Game saved as '$saveName'")
        }.onFailure { error ->
            println("❌ Failed to save game: ${error.message}")
        }
    }

    fun handleLoad(game: MudGame, saveName: String) {
        val result = game.persistenceManager.loadGame(saveName)
        result.onSuccess { loadedState ->
            game.worldState = loadedState
            println("📂 Game loaded from '$saveName'")
            game.describeCurrentRoom()
        }.onFailure { error ->
            printLoadFailure(game, error)
        }
    }

    private fun printLoadFailure(game: MudGame, error: Throwable) {
        println("❌ Failed to load game: ${error.message}")
        val saves = game.persistenceManager.listSaves()
        if (saves.isNotEmpty()) {
            println("Available saves: ${saves.joinToString(", ")}")
        } else {
            println("No saved games found.")
        }
    }

    fun handleHelp() {
        println(HELP_TEXT)
    }

    fun handleQuit(game: MudGame) {
        println("Are you sure you want to quit? (y/n)")
        val confirm = readLine()?.trim()?.lowercase()
        if (confirm == "y" || confirm == "yes") {
            game.running = false
        }
    }
}
