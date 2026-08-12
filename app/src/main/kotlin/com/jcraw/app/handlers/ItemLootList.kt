@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength")

package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.core.Entity

/**
 * List corpse contents (loot without item target).
 */
internal object ItemLootList {

    fun listCorpseContents(game: MudGame, corpse: Entity.Corpse) {
        if (corpse.contents.isEmpty() && corpse.goldAmount == 0) {
            println("The corpse is empty.")
        } else {
            println("${corpse.name} contains:")
            corpse.contents.forEach { instance ->
                val templateResult = game.itemRepository.findTemplateById(instance.templateId)
                templateResult.onSuccess { template ->
                    if (template != null) {
                        val extra = formatItemInfo(instance, template)
                        println("  - ${template.name}$extra")
                    } else {
                        println("  - Unknown item (${instance.id})")
                    }
                }.onFailure {
                    println("  - Unknown item (${instance.id})")
                }
            }
            if (corpse.goldAmount > 0) {
                println("  - ${corpse.goldAmount} gold")
            }
        }
    }
}
