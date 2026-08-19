package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.TreasureRoomComponent
import com.jcraw.mud.reasoning.treasureroom.TreasurePedestalSupport as PedestalPures

/**
 * GUI template lookup + status emit (MUD-039). Extra [EngineGameClient.getItemTemplate] fallback stays here.
 */
internal object ClientTreasurePedestalSupport {

    fun buildItemTemplatesMap(
        game: EngineGameClient,
        treasureRoom: TreasureRoomComponent
    ): Map<String, ItemTemplate> =
        PedestalPures.buildItemTemplatesMap(treasureRoom) { id ->
            game.itemRepository.findTemplateById(id).getOrNull() ?: game.getItemTemplate(id)
        }

    fun emitStatusUpdate(game: EngineGameClient, spaceId: String) {
        val player = game.worldState.player
        game.emitEvent(
            GameEvent.StatusUpdate(
                hp = player.health,
                maxHp = player.maxHealth,
                location = game.worldState.getSpace(spaceId)?.name ?: spaceId
            )
        )
    }
}
