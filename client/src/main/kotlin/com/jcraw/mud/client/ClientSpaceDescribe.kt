package com.jcraw.mud.client

import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.core.SpacePropertiesComponent
import com.jcraw.mud.core.TreasureRoomComponent

/**
 * Space description / treasure status text for [EngineGameClient]. Pure extract.
 */
object ClientSpaceDescribe {

    fun describeWorldV2Space(
        game: EngineGameClient,
        space: SpacePropertiesComponent,
        spaceId: String
    ) {
        val narrativeText = buildString {
            appendLine("\n${space.description}")
            if (space.isTreasureRoom) appendTreasureRoomStatus(game, this, spaceId)
            appendGraphExits(game, this, spaceId)
            appendEntities(game, this, space)
            appendResources(this, space)
            appendDroppedItems(this, space)
        }
        game.emitEvent(GameEvent.Narrative(narrativeText))
        game.emitEvent(
            GameEvent.StatusUpdate(
                hp = game.worldState.player.health,
                maxHp = game.worldState.player.maxHealth,
                location = game.worldState.getSpace(spaceId)?.name ?: spaceId
            )
        )
    }

    private fun appendGraphExits(game: EngineGameClient, builder: StringBuilder, spaceId: String) {
        val graphNode = game.worldState.getGraphNode(spaceId) ?: return
        val visibleEdges = graphNode.getVisibleEdges(game.worldState.player.revealedExits)
        if (visibleEdges.isEmpty()) return
        builder.appendLine("\nExits:")
        visibleEdges.forEach { edge -> builder.appendLine("  - ${edge.direction}") }
    }

    private fun appendEntities(
        game: EngineGameClient,
        builder: StringBuilder,
        space: SpacePropertiesComponent
    ) {
        if (space.entities.isEmpty()) return
        builder.appendLine("\nYou see:")
        space.entities.forEach { entityId ->
            val entity = ClientSpaceContent.loadEntity(game, entityId)
            val name = when (entity) {
                is Entity.NPC -> entity.name
                else -> SpaceEntitySupport.getStub(entityId).displayName
            }
            builder.appendLine("  - $name")
        }
    }

    private fun appendResources(builder: StringBuilder, space: SpacePropertiesComponent) {
        if (space.resources.isEmpty()) return
        builder.appendLine("\nResources:")
        space.resources.forEach { resource ->
            val desc = resource.description.ifBlank { resource.templateId }
            builder.appendLine("  - $desc (quantity ${resource.quantity})")
        }
    }

    private fun appendDroppedItems(builder: StringBuilder, space: SpacePropertiesComponent) {
        if (space.itemsDropped.isEmpty()) return
        builder.appendLine("\nItems on the ground:")
        space.itemsDropped.forEach { item ->
            builder.appendLine("  - ${item.templateId} (x${item.quantity})")
        }
    }

    fun appendTreasureRoomStatus(game: EngineGameClient, builder: StringBuilder, spaceId: String) {
        val treasureRoom = game.worldState.getTreasureRoom(spaceId)
        builder.appendLine()
        builder.appendLine(treasureStatusLine(game, treasureRoom))
    }

    private fun treasureStatusLine(
        game: EngineGameClient,
        treasureRoom: TreasureRoomComponent?
    ): String {
        val takenItem = treasureRoom?.currentlyTakenItem
        return when {
            treasureRoom == null -> "(Treasure room data failed to load.)"
            treasureRoom.hasBeenLooted ->
                "Only dust-coated pedestals remain; the room has been looted."
            takenItem == null ->
                "Five pedestals hum with magic. Claim one treasure via " +
                    "'examine pedestals'—the others will seal away."
            else -> {
                val itemName = game.itemRepository.findTemplateById(takenItem)
                    .getOrNull()?.name ?: takenItem
                "The other treasures are sealed while you hold the $itemName. " +
                    "Return it to swap your choice."
            }
        }
    }
}
