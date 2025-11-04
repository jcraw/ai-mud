package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.client.SpaceEntitySupport
import com.jcraw.mud.core.*
import com.jcraw.mud.reasoning.QuestAction
import kotlinx.coroutines.runBlocking

/**
 * Handles movement, exploration, and searching in the GUI client.
 */
object ClientMovementHandlers {

    fun handleMove(game: EngineGameClient, direction: Direction) {
        val currentSpace = game.currentSpace()
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
        val room = game.worldState.getCurrentRoom()
        val space = if (room == null) game.currentSpace() else null

        if (target == null) {
            game.describeCurrentRoom()

            if (room != null) {
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
            } else if (space != null) {
                val groundItems = space.itemsDropped
                val narrative = if (groundItems.isNotEmpty()) {
                    buildString {
                        appendLine()
                        appendLine("Items on the ground:")
                        groundItems.forEach { item ->
                            appendLine("  - ${item.templateId} (x${item.quantity})")
                        }
                    }
                } else {
                    "\nYou don't see any items here."
                }
                game.emitEvent(GameEvent.Narrative(narrative))
            }
            return
        }

        if (room != null) {
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
            return
        }

        val currentSpace = space ?: run {
            game.emitEvent(GameEvent.System("You don't see that here.", GameEvent.MessageLevel.INFO))
            return
        }

        val lower = target.lowercase()

        currentSpace.entities.firstOrNull { entityId ->
            val entity = game.loadEntity(entityId) as? Entity.NPC
            val name = entity?.name ?: SpaceEntitySupport.getStub(entityId).displayName
            name.lowercase().contains(lower) || entityId.lowercase().contains(lower)
        }?.let { entityId ->
            val entity = game.loadEntity(entityId) as? Entity.NPC
            if (entity != null) {
                game.emitEvent(GameEvent.Narrative(entity.description))
            } else {
                val stub = SpaceEntitySupport.getStub(entityId)
                game.emitEvent(GameEvent.Narrative(stub.description))
            }
            return
        }

        currentSpace.exits.firstOrNull { exit ->
            exit.direction.lowercase() == lower ||
            exit.description.lowercase().contains(lower)
        }?.let { exit ->
            val description = exit.describeWithConditions(game.worldState.player)
            game.emitEvent(GameEvent.Narrative("Exit ${exit.direction}: $description"))
            return
        }

        currentSpace.resources.firstOrNull { resource ->
            resource.description.lowercase().contains(lower) ||
            resource.templateId.lowercase().contains(lower)
        }?.let { resource ->
            val desc = resource.description.ifBlank { "Resource node containing ${resource.templateId} (quantity ${resource.quantity})." }
            game.emitEvent(GameEvent.Narrative(desc))
            return
        }

        currentSpace.traps.firstOrNull { trap ->
            trap.description.lowercase().contains(lower) ||
            trap.type.lowercase().contains(lower)
        }?.let { trap ->
            val status = if (trap.triggered) "It has already been triggered." else "It looks dangerous (DC ${trap.difficulty})."
            val desc = if (trap.description.isNotBlank()) trap.description else "A ${trap.type} trap." 
            game.emitEvent(GameEvent.Narrative("$desc $status"))
            return
        }

        currentSpace.itemsDropped.firstOrNull { item ->
            item.id.lowercase().contains(lower) || item.templateId.lowercase().contains(lower)
        }?.let { item ->
            game.emitEvent(GameEvent.Narrative("It looks like ${item.templateId} (quantity ${item.quantity})."))
            return
        }

        game.emitEvent(GameEvent.System("You don't see that here.", GameEvent.MessageLevel.INFO))
    }

    private fun handleSpaceMovement(
        game: EngineGameClient,
        direction: Direction,
        currentSpace: SpacePropertiesComponent
    ) {
        val player = game.worldState.player
        var activeSpace = currentSpace
        var exit = activeSpace.resolveExit(direction.displayName, player)

        if (exit != null && exit.targetId == "PLACEHOLDER") {
            val linker = game.exitLinker
            if (linker == null) {
                game.emitEvent(
                    GameEvent.System(
                        "This exit hasn't been generated yet and the world generator is unavailable.",
                        GameEvent.MessageLevel.ERROR
                    )
                )
                return
            }

            val spaceChunk = game.worldChunkRepository.findById(player.currentRoomId).getOrElse { error ->
                game.emitEvent(
                    GameEvent.System(
                        "Failed to inspect current location: ${error.message}",
                        GameEvent.MessageLevel.ERROR
                    )
                )
                return
            } ?: run {
                game.emitEvent(
                    GameEvent.System(
                        "Current space metadata missing. Navigation aborted.",
                        GameEvent.MessageLevel.ERROR
                    )
                )
                return
            }

            val parentSubzoneId = spaceChunk.parentId ?: run {
                game.emitEvent(
                    GameEvent.System(
                        "Current space has no parent subzone. Navigation aborted.",
                        GameEvent.MessageLevel.ERROR
                    )
                )
                return
            }

            val parentSubzone = game.worldChunkRepository.findById(parentSubzoneId).getOrElse { error ->
                game.emitEvent(
                    GameEvent.System(
                        "Failed to load parent subzone: ${error.message}",
                        GameEvent.MessageLevel.ERROR
                    )
                )
                return
            } ?: run {
                game.emitEvent(
                    GameEvent.System(
                        "Parent subzone not found for ${parentSubzoneId}.",
                        GameEvent.MessageLevel.ERROR
                    )
                )
                return
            }

            val linkedSpace = runBlocking {
                linker.linkExits(player.currentRoomId, activeSpace, parentSubzoneId, parentSubzone)
            }.getOrElse { error ->
                game.emitEvent(
                    GameEvent.System(
                        "Failed to generate adjacent space: ${error.message}",
                        GameEvent.MessageLevel.ERROR
                    )
                )
                return
            }

            activeSpace = linkedSpace
            exit = activeSpace.resolveExit(direction.displayName, player)

            if (exit == null || exit.targetId == "PLACEHOLDER") {
                game.emitEvent(
                    GameEvent.System(
                        "The way ${direction.displayName} still leads into unfinished space.",
                        GameEvent.MessageLevel.WARNING
                    )
                )
                return
            }
        }

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
        val room = game.worldState.getCurrentRoom()
        val space = if (room == null) game.currentSpace() else null

        if (room == null && space == null) {
            return
        }

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

                if (room != null) {
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
                } else if (space != null) {
                    var foundSomething = false
                    if (space.itemsDropped.isNotEmpty()) {
                        foundSomething = true
                        appendLine("You gather the following items:")
                        space.itemsDropped.forEach { item ->
                            appendLine("  - ${item.templateId} (x${item.quantity})")
                        }
                    }
                    val resources = space.resources.filter { !it.isDepleted() }
                    if (resources.isNotEmpty()) {
                        foundSomething = true
                        appendLine()
                        appendLine("You notice harvestable resources:")
                        resources.forEach { resource ->
                            val desc = resource.description.ifBlank { resource.templateId }
                            appendLine("  - $desc (quantity ${resource.quantity})")
                        }
                    }
                    if (space.traps.isNotEmpty()) {
                        foundSomething = true
                        appendLine()
                        appendLine("You detect potential traps:")
                        space.traps.forEach { trap ->
                            val desc = if (trap.description.isNotBlank()) trap.description else trap.type
                            appendLine("  - $desc (DC ${trap.difficulty})")
                        }
                    }
                    if (!foundSomething) {
                        appendLine("You don't find anything hidden here.")
                    }
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
