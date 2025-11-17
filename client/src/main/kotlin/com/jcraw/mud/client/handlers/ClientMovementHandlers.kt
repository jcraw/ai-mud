package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.*
import com.jcraw.mud.reasoning.QuestAction
import com.jcraw.mud.reasoning.treasureroom.TreasureRoomExitLogic
import kotlinx.coroutines.runBlocking

/**
 * Handles movement, exploration, and searching in the GUI client.
 */
object ClientMovementHandlers {

    fun handleMove(game: EngineGameClient, direction: Direction) {
        val previousSpaceId = game.worldState.player.currentRoomId
        val previousTreasureRoom = game.worldState.getTreasureRoom(previousSpaceId)
        val playerSkills = game.skillManager.getSkillComponent(game.worldState.player.id)
        val newState = game.worldState.movePlayerV3(direction, playerSkills)
        if (newState == null) {
            game.emitEvent(GameEvent.System("You can't go that way.", GameEvent.MessageLevel.WARNING))
            return
        }
        game.worldState = newState
        // Treasure room exit finalization disabled - players can return and swap anytime
        // val treasureExitMessage = finalizeTreasureRoomExit(game, previousSpaceId, previousTreasureRoom)
        game.handlePlayerMovement(direction.displayName, null)
    }

    fun handleLook(game: EngineGameClient, target: String?) {
        // V3-only: Use space-based navigation
        val space = game.worldState.getCurrentSpace()
        if (space == null) {
            game.emitEvent(GameEvent.System("Error: No current space", GameEvent.MessageLevel.ERROR))
            return
        }

        if (target == null) {
            game.describeCurrentRoom()

            // Show ground items
            val groundItems = game.worldState.getEntitiesInSpace(game.worldState.player.currentRoomId)
                .filterIsInstance<Entity.Item>()
                .filter { it.isPickupable }

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

        val lower = target.lowercase()

        // Check entities in space
        val entities = game.worldState.getEntitiesInSpace(game.worldState.player.currentRoomId)
        entities.find { e ->
            e.name.lowercase().contains(lower) || e.id.lowercase().contains(lower)
        }?.let { entity ->
            game.emitEvent(GameEvent.Narrative(entity.description))
            return
        }

        // Check inventory
        game.worldState.player.inventory.find { item ->
            item.name.lowercase().contains(lower) || item.id.lowercase().contains(lower)
        }?.let { item ->
            game.emitEvent(GameEvent.Narrative(item.description))
            return
        }

        // Check equipped weapon
        val equippedWeapon = game.worldState.player.equippedWeapon
        if (equippedWeapon != null &&
            (equippedWeapon.name.lowercase().contains(lower) || equippedWeapon.id.lowercase().contains(lower))) {
            game.emitEvent(GameEvent.Narrative(equippedWeapon.description + " (equipped)"))
            return
        }

        // Check equipped armor
        val equippedArmor = game.worldState.player.equippedArmor
        if (equippedArmor != null &&
            (equippedArmor.name.lowercase().contains(lower) || equippedArmor.id.lowercase().contains(lower))) {
            game.emitEvent(GameEvent.Narrative(equippedArmor.description + " (equipped)"))
            return
        }

        val sceneryDescription = runBlocking {
            game.sceneryGenerator.describeScenery(target, space, space.description)
        }

        if (sceneryDescription != null) {
            game.emitEvent(GameEvent.Narrative(sceneryDescription))
        } else {
            game.emitEvent(GameEvent.System("You don't see that here.", GameEvent.MessageLevel.INFO))
        }
    }

    fun handleSearch(game: EngineGameClient, target: String?) {
        // V3-only: Use space-based navigation
        val space = game.worldState.getCurrentSpace()
        val node = game.worldState.getCurrentGraphNode()
        if (space == null || node == null) {
            game.emitEvent(GameEvent.System("Error: No current space", GameEvent.MessageLevel.ERROR))
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

                // V3: Check entities in space
                val entities = game.worldState.getEntitiesInSpace(game.worldState.player.currentRoomId)
                val hiddenItems = entities.filterIsInstance<Entity.Item>().filter { !it.isPickupable }
                val pickupableItems = entities.filterIsInstance<Entity.Item>().filter { it.isPickupable }

                var foundSomething = false

                if (pickupableItems.isNotEmpty()) {
                    foundSomething = true
                    appendLine("You find the following items:")
                    pickupableItems.forEach { item ->
                        appendLine("  - ${item.name}: ${item.description}")
                    }
                }

                if (hiddenItems.isNotEmpty()) {
                    foundSomething = true
                    appendLine()
                    appendLine("You also notice some interesting features:")
                    hiddenItems.forEach { item ->
                        appendLine("  - ${item.name}: ${item.description}")
                    }
                }

                val hiddenExits = node.neighbors.filter { edge ->
                    val edgeId = edge.edgeId(node.id)
                    edge.hidden && !game.worldState.player.hasRevealedExit(edgeId)
                }
                if (hiddenExits.isNotEmpty()) {
                    foundSomething = true
                    val firstExit = hiddenExits.first()
                    val edgeId = firstExit.edgeId(node.id)
                    val updatedPlayer = game.worldState.player.revealExit(edgeId)
                    game.worldState = game.worldState.updatePlayer(updatedPlayer)

                    appendLine()
                    appendLine("Hidden exits:")
                    hiddenExits.forEach { exit ->
                        appendLine("  - ${exit.direction} (now marked on your map)")
                    }
                }

                if (!foundSomething) {
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

    fun handleTravel(game: EngineGameClient, rawDirection: String) {
        val normalized = rawDirection.trim()
        if (normalized.isEmpty()) {
            game.emitEvent(GameEvent.System("Travel where?", GameEvent.MessageLevel.WARNING))
            return
        }

        Direction.fromString(normalized)?.let {
            handleMove(game, it)
            return
        }

        val previousSpaceId = game.worldState.player.currentRoomId
        val previousTreasureRoom = game.worldState.getTreasureRoom(previousSpaceId)
        val playerSkills = game.skillManager.getSkillComponent(game.worldState.player.id)
        val edgeMove = game.worldState.movePlayerByExit(normalized, playerSkills)
        if (edgeMove != null) {
            game.worldState = edgeMove
            // Treasure room exit finalization disabled - players can return and swap anytime
            // val treasureExitMessage = finalizeTreasureRoomExit(game, previousSpaceId, previousTreasureRoom)
            game.handlePlayerMovement(normalized, null)
            return
        }

        val space = game.worldState.getCurrentSpace()
        if (space == null) {
            game.emitEvent(GameEvent.System("You can't go that way.", GameEvent.MessageLevel.WARNING))
            return
        }

        val resolvedExit = space.resolveExit(normalized, game.worldState.player, playerSkills)
        if (resolvedExit == null) {
            game.emitEvent(GameEvent.System("You can't go that way.", GameEvent.MessageLevel.WARNING))
            return
        }

        val fallback = game.worldState.movePlayerByExit(resolvedExit.direction, playerSkills)
        if (fallback != null) {
            game.worldState = fallback
            val treasureExitMessage = finalizeTreasureRoomExit(game, previousSpaceId, previousTreasureRoom)
            game.handlePlayerMovement(resolvedExit.direction, treasureExitMessage)
            return
        }

        val targetNode = game.ensureGraphNodeLoaded(resolvedExit.targetId)
        if (targetNode == null) {
            game.emitEvent(GameEvent.System("That passage isn't available yet.", GameEvent.MessageLevel.WARNING))
            return
        }

        val targetSpace = game.loadSpace(resolvedExit.targetId)
            ?: game.worldState.getSpace(resolvedExit.targetId)
        if (targetSpace == null) {
            game.emitEvent(GameEvent.System("That passage feels incomplete.", GameEvent.MessageLevel.WARNING))
            return
        }

        val updatedPlayer = game.worldState.player.moveToRoom(resolvedExit.targetId)
        game.worldState = game.worldState
            .updatePlayer(updatedPlayer)
            .updateSpace(resolvedExit.targetId, targetSpace)
            .updateGraphNode(resolvedExit.targetId, targetNode)
        val treasureExitMessage = finalizeTreasureRoomExit(game, previousSpaceId, previousTreasureRoom)
        game.handlePlayerMovement(resolvedExit.direction, treasureExitMessage)
    }

    fun handleScout(game: EngineGameClient, rawDirection: String?) {
        val space = game.worldState.getCurrentSpace()
        if (space == null) {
            game.emitEvent(GameEvent.System("You are not in a known space.", GameEvent.MessageLevel.ERROR))
            return
        }

        val player = game.worldState.player
        val playerSkills = game.skillManager.getSkillComponent(player.id)
        if (rawDirection.isNullOrBlank()) {
            val visible = space.getVisibleExits(player, playerSkills)
            if (visible.isEmpty()) {
                game.emitEvent(GameEvent.Narrative("You don't notice any obvious exits."))
            } else {
                val text = buildString {
                    appendLine("Visible exits:")
                    visible.forEach { exit ->
                        appendLine("  - ${exit.direction}: ${exit.describeWithConditions(player, playerSkills)}")
                    }
                }
                game.emitEvent(GameEvent.Narrative(text))
            }
            return
        }

        val resolved = space.resolveExit(rawDirection, player, playerSkills)
        if (resolved == null) {
            game.emitEvent(GameEvent.System("You can't find any exit matching \"$rawDirection\".", GameEvent.MessageLevel.INFO))
            return
        }

        val description = buildString {
            appendLine("You examine the ${resolved.direction}:")
            appendLine("  ${resolved.description}")
            if (resolved.conditions.isNotEmpty()) {
                val unmet = resolved.conditions.filterNot { it.meetsCondition(player, playerSkills) }
                if (unmet.isNotEmpty()) {
                    appendLine("  Requirements: ${unmet.joinToString(", ") { it.describe() }}")
                }
            }

            val destSpace = game.loadSpace(resolved.targetId) ?: game.worldState.getSpace(resolved.targetId)
            if (destSpace != null && destSpace.description.isNotBlank()) {
                appendLine()
                appendLine("Ahead you sense: ${destSpace.description.lines().first()}")
            }
        }

        game.emitEvent(GameEvent.Narrative(description))
    }

    private fun finalizeTreasureRoomExit(
        game: EngineGameClient,
        previousSpaceId: String,
        previousTreasureRoom: TreasureRoomComponent?
    ): String? {
        val treasureRoom = previousTreasureRoom ?: return null
        val result = TreasureRoomExitLogic.finalizeExit(treasureRoom, game.itemRepository) ?: return null
        game.worldState = game.worldState.updateTreasureRoom(previousSpaceId, result.updatedComponent)
        return result.narration
    }
}
