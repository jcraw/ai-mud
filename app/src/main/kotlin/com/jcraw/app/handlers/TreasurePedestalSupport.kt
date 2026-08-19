package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.TreasureRoomComponent
import com.jcraw.mud.reasoning.treasureroom.TreasurePedestalSupport as PedestalPures

/** Console template lookup wrapper (MUD-039). Pures live in reasoning. */
internal object TreasurePedestalSupport {

    fun buildItemTemplatesMap(game: MudGame, treasureRoom: TreasureRoomComponent): Map<String, ItemTemplate> =
        PedestalPures.buildItemTemplatesMap(treasureRoom) { id ->
            game.itemRepository.findTemplateById(id).getOrNull()
        }
}
