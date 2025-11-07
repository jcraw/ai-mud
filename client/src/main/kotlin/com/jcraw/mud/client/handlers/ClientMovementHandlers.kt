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
        // V3-only: Use graph-based navigation
        val newState = game.worldState.movePlayerV3(direction)

        if (newState == null) {
            game.emitEvent(GameEvent.System("You can't go that way.", GameEvent.MessageLevel.WARNING))
            return
        }

        game.worldState = newState
        game.emitEvent(GameEvent.Narrative("You move ${direction.displayName}."))

        // TODO: Lazy-fill content generation (needs WorldGenerator integration)
        // TODO: Frontier traversal (needs chunk cascade implementation)

        // Track quest and describe the new location
        game.trackQuests(QuestAction.VisitedRoom(game.worldState.player.currentRoomId))
        game.describeCurrentRoom()
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

        // Check scenery (Note: sceneryGenerator currently only supports Room-based scenery)
        game.emitEvent(GameEvent.System("You don't see that here.", GameEvent.MessageLevel.INFO))
    }

    fun handleSearch(game: EngineGameClient, target: String?) {
        // V3-only: Use space-based navigation
        val space = game.worldState.getCurrentSpace()
        if (space == null) {
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

                // TODO: Check for hidden exits and reveal them (needs GraphNodeComponent integration)

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
}
