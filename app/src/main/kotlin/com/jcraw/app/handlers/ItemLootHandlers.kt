@file:Suppress(
    "ReturnCount",
    "MagicNumber",
    "MaxLineLength",
    "TooManyFunctions",
    "LongMethod",
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "LongParameterList"
)

package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.core.Entity

/**
 * Loot / loot-all entrypoints for [ItemHandlers] facade (app-only; pure-move).
 * Live Intent.LootCorpse still routes via CorpseHandlers — left untouched.
 */
object ItemLootHandlers {

    fun handleLoot(game: MudGame, corpseTarget: String, itemTarget: String?) {
        val spaceId = game.worldState.player.currentRoomId
        val corpse = findCorpse(game, spaceId, corpseTarget)
        if (corpse == null) {
            println("There's no corpse here by that name.")
            return
        }
        if (itemTarget == null) {
            ItemLootList.listCorpseContents(game, corpse)
            return
        }
        if (itemTarget.lowercase() == "gold" || itemTarget.lowercase() == "coins") {
            ItemLootTake.lootGold(game, spaceId, corpse)
            return
        }
        ItemLootTake.lootItem(game, spaceId, corpse, itemTarget)
    }

    fun handleLootAll(game: MudGame, corpseTarget: String) {
        val spaceId = game.worldState.player.currentRoomId
        val corpse = findCorpse(game, spaceId, corpseTarget)
        if (corpse == null) {
            println("There's no corpse here by that name.")
            return
        }
        if (corpse.contents.isEmpty() && corpse.goldAmount == 0) {
            println("The corpse is empty.")
            return
        }
        ItemLootAll.lootAll(game, spaceId, corpse)
    }

    private fun findCorpse(game: MudGame, spaceId: String, corpseTarget: String): Entity.Corpse? =
        game.worldState.getEntitiesInSpace(spaceId)
            .filterIsInstance<Entity.Corpse>()
            .find { entity ->
                entity.name.lowercase().contains(corpseTarget.lowercase()) ||
                    entity.id.lowercase().contains(corpseTarget.lowercase())
            }
}
