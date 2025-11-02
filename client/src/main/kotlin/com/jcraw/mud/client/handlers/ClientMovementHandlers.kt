package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.*
import com.jcraw.mud.reasoning.QuestAction
import kotlinx.coroutines.runBlocking

/**
 * Handles movement, exploration, and searching in the GUI client.
 */
object ClientMovementHandlers {

    fun handleMove(game: EngineGameClient, direction: Direction) {
        val currentSpaceId = game.worldState.player.currentRoomId
        val currentSpace = runBlocking {
            game.spacePropertiesRepository.findByChunkId(currentSpaceId)
        }.getOrElse { error ->
            game.emitEvent(
                GameEvent.System(
                    "Failed to load current space: ${error.message}",
                    GameEvent.MessageLevel.ERROR
                )
            )
            return
        }

        if (currentSpace != null) {
            handleSpaceMovement(game, direction, currentSpace)
            return
        }

        val newState = game.worldState.movePlayer(direction)

        if (newState == null) {
            game.emitEvent(GameEvent.System("You can't go that way.", GameEvent.MessageLevel.WARNING))
            return
        }

        game.worldState = newState
        game.emitEvent(GameEvent.Narrative("You move ${direction.displayName}."))

        val room = game.worldState.getCurrentRoom()
        if (room != null) {
            game.trackQuests(QuestAction.VisitedRoom(room.id))
        }

        game.describeCurrentRoom()
    }

    fun handleLook(game: EngineGameClient, target: String?) {
        if (target == null) {
            game.describeCurrentRoom()

            // Also list pickupable items on the ground
            val room = game.worldState.getCurrentRoom() ?: return
            val groundItems = room.entities.filterIsInstance<Entity.Item>().filter { it.isPickupable }
            if (groundItems.isNotEmpty()) {
                val itemsList = buildString {
                    appendLine()
                    appendLine("Items on the ground:")
                    groundItems.forEach { item ->
                        appendLine("  - ${item.name}")
                    }
                }
                game.emitEvent(GameEvent.Narrative(itemsList))
            } else {
                game.emitEvent(GameEvent.Narrative("\nYou don't see any items here."))
            }
            return
        }

        val room = game.worldState.getCurrentRoom() ?: return

        // First check room entities
        val roomEntity = room.entities.find { e ->
            e.name.lowercase().contains(target.lowercase()) ||
            e.id.lowercase().contains(target.lowercase())
        }

        if (roomEntity != null) {
            game.emitEvent(GameEvent.Narrative(roomEntity.description))
            return
        }

        // Then check inventory (including equipped items)
        val inventoryItem = game.worldState.player.inventory.find { item ->
            item.name.lowercase().contains(target.lowercase()) ||
            item.id.lowercase().contains(target.lowercase())
        }

        if (inventoryItem != null) {
            game.emitEvent(GameEvent.Narrative(inventoryItem.description))
            return
        }

        // Check equipped weapon
        val equippedWeapon = game.worldState.player.equippedWeapon
        if (equippedWeapon != null &&
            (equippedWeapon.name.lowercase().contains(target.lowercase()) ||
             equippedWeapon.id.lowercase().contains(target.lowercase()))) {
            game.emitEvent(GameEvent.Narrative(equippedWeapon.description + " (equipped)"))
            return
        }

        // Check equipped armor
        val equippedArmor = game.worldState.player.equippedArmor
        if (equippedArmor != null &&
            (equippedArmor.name.lowercase().contains(target.lowercase()) ||
             equippedArmor.id.lowercase().contains(target.lowercase()))) {
            game.emitEvent(GameEvent.Narrative(equippedArmor.description + " (equipped)"))
            return
        }

        // Finally try scenery
        val roomDescription = if (game.descriptionGenerator != null) {
            runBlocking { game.descriptionGenerator.generateDescription(room) }
        } else {
            room.traits.joinToString(". ") + "."
        }

        val sceneryDescription = runBlocking {
            game.sceneryGenerator.describeScenery(target, room, roomDescription)
        }

        if (sceneryDescription != null) {
            game.emitEvent(GameEvent.Narrative(sceneryDescription))
        } else {
            game.emitEvent(GameEvent.System("You don't see that here.", GameEvent.MessageLevel.INFO))
        }
    }

    private fun handleSpaceMovement(
        game: EngineGameClient,
        direction: Direction,
        currentSpace: SpacePropertiesComponent
    ) {
        val player = game.worldState.player
        val exit = currentSpace.resolveExit(direction.displayName, player)

        if (exit == null) {
            game.emitEvent(
                GameEvent.System(
                    "No exit leading ${direction.displayName}.",
                    GameEvent.MessageLevel.WARNING
                )
            )
            return
        }

        if (!exit.meetsConditions(player)) {
            game.emitEvent(
                GameEvent.System(
                    "You can't travel that way yet: ${exit.describeWithConditions(player)}",
                    GameEvent.MessageLevel.INFO
                )
            )
            return
        }

        val destinationSpace = runBlocking {
            game.spacePropertiesRepository.findByChunkId(exit.targetId)
        }.getOrElse { error ->
            game.emitEvent(
                GameEvent.System(
                    "Failed to load destination: ${error.message}",
                    GameEvent.MessageLevel.ERROR
                )
            )
            return
        }

        if (destinationSpace == null) {
            game.emitEvent(
                GameEvent.System(
                    "The path fades into unfinished space (${exit.targetId}).",
                    GameEvent.MessageLevel.ERROR
                )
            )
            return
        }

        game.navigationState?.let { nav ->
            val updatedNav = runBlocking {
                nav.updateLocation(exit.targetId, game.worldChunkRepository)
            }.getOrElse { error ->
                game.emitEvent(
                    GameEvent.System(
                        "Navigation failed: ${error.message}",
                        GameEvent.MessageLevel.ERROR
                    )
                )
                return
            }
            game.navigationState = updatedNav
        }

        val updatedPlayer = player.copy(currentRoomId = exit.targetId)
        game.worldState = game.worldState.updatePlayer(updatedPlayer)

        game.emitEvent(GameEvent.Narrative("You move ${direction.displayName}."))
        game.trackQuests(QuestAction.VisitedRoom(exit.targetId))
        game.describeCurrentRoom()
    }

    fun handleSearch(game: EngineGameClient, target: String?) {
        val room = game.worldState.getCurrentRoom() ?: return

        val searchMessage = "You search the area carefully${if (target != null) ", focusing on the $target" else ""}..."

        // Perform a Wisdom (Perception) skill check to find hidden items
        val result = game.skillCheckResolver.checkPlayer(
            game.worldState.player,
            StatType.WISDOM,
            Difficulty.MEDIUM
        )

        val narrative = buildString {
            appendLine(searchMessage)
            appendLine()
            appendLine("Rolling Perception check...")
            appendLine("d20 roll: ${result.roll} + WIS modifier: ${result.modifier} = ${result.total} vs DC ${result.dc}")
            appendLine()

            if (result.isCriticalSuccess) {
                appendLine("🎲 CRITICAL SUCCESS! (Natural 20)")
            } else if (result.isCriticalFailure) {
                appendLine("💀 CRITICAL FAILURE! (Natural 1)")
            }

            if (result.success) {
                appendLine("✅ Success!")
                appendLine()

                // Find items in the room
                val hiddenItems = room.entities.filterIsInstance<Entity.Item>().filter { !it.isPickupable }
                val pickupableItems = room.entities.filterIsInstance<Entity.Item>().filter { it.isPickupable }

                if (hiddenItems.isNotEmpty() || pickupableItems.isNotEmpty()) {
                    if (pickupableItems.isNotEmpty()) {
                        appendLine("You find the following items:")
                        pickupableItems.forEach { item ->
                            appendLine("  - ${item.name}: ${item.description}")
                        }
                    }
                    if (hiddenItems.isNotEmpty()) {
                        appendLine()
                        appendLine("You also notice some interesting features:")
                        hiddenItems.forEach { item ->
                            appendLine("  - ${item.name}: ${item.description}")
                        }
                    }
                } else {
                    appendLine("You don't find anything hidden here.")
                }
            } else {
                appendLine("❌ Failure!")
                appendLine("You don't find anything of interest.")
            }
        }

        game.emitEvent(GameEvent.Narrative(narrative))
    }

    fun handleInteract(game: EngineGameClient, target: String) {
        game.emitEvent(GameEvent.System("Interaction system not yet implemented. (Target: $target)", GameEvent.MessageLevel.INFO))
    }
}
