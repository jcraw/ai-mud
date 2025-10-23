package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.core.Direction
import com.jcraw.mud.core.Entity
import com.jcraw.mud.reasoning.QuestAction
import kotlinx.coroutines.runBlocking

/**
 * Handlers for movement, exploration, and searching actions.
 */
object MovementHandlers {

    fun handleMove(game: MudGame, direction: Direction) {
        // Check if in combat - must flee first
        if (game.worldState.player.isInCombat()) {
            println("\nYou attempt to flee from combat...")

            val result = game.combatResolver.attemptFlee(game.worldState)
            println(result.narrative)

            if (result.playerFled) {
                // Flee successful - update state and move
                game.worldState = game.worldState.updatePlayer(game.worldState.player.endCombat())

                val newState = game.worldState.movePlayer(direction)
                if (newState == null) {
                    println("You can't go that way.")
                    return
                }

                game.worldState = newState
                println("You move ${direction.displayName}.")

                // Track room exploration for quests
                val room = game.worldState.getCurrentRoom()
                if (room != null) {
                    game.trackQuests(QuestAction.VisitedRoom(room.id))
                }

                game.describeCurrentRoom()
            } else if (result.playerDied) {
                // Player died trying to flee
                println("\nYou have been defeated! Game over.")
                println("\nPress any key to play again...")
                readLine()  // Wait for any input

                // Restart the game
                game.worldState = game.initialWorldState
                println("\n" + "=" * 60)
                println("  Restarting Adventure...")
                println("=" * 60)
                game.printWelcome()
                game.describeCurrentRoom()
            } else {
                // Failed to flee - update combat state and stay in place
                val combatState = result.newCombatState
                if (combatState != null) {
                    // Sync player's actual health with combat state
                    val updatedPlayer = game.worldState.player
                        .updateCombat(combatState)
                        .copy(health = combatState.playerHealth)
                    game.worldState = game.worldState.updatePlayer(updatedPlayer)
                }
                game.describeCurrentRoom()
            }
            return
        }

        // Normal movement (not in combat)
        val newState = game.worldState.movePlayer(direction)

        if (newState == null) {
            println("You can't go that way.")
            return
        }

        game.worldState = newState
        println("You move ${direction.displayName}.")

        // Track room exploration for quests
        val room = game.worldState.getCurrentRoom()
        if (room != null) {
            game.trackQuests(QuestAction.VisitedRoom(room.id))
        }

        game.describeCurrentRoom()
    }

    fun handleLook(game: MudGame, target: String?) {
        if (target == null) {
            // Look at room - describeCurrentRoom already shows all entities including items
            game.describeCurrentRoom()
        } else {
            // Look at specific entity
            val room = game.worldState.getCurrentRoom() ?: return
            val entity = room.entities.find { e ->
                e.name.lowercase().contains(target.lowercase()) ||
                e.id.lowercase().contains(target.lowercase())
            }

            if (entity != null) {
                println(entity.description)
            } else {
                // Try to describe scenery (non-entity objects like walls, floor, etc.)
                val roomDescription = game.generateRoomDescription(room)
                val sceneryDescription = runBlocking {
                    game.sceneryGenerator.describeScenery(target, room, roomDescription)
                }

                if (sceneryDescription != null) {
                    println(sceneryDescription)
                } else {
                    println("You don't see that here.")
                }
            }
        }
    }

    fun handleSearch(game: MudGame, target: String?) {
        val room = game.worldState.getCurrentRoom() ?: return

        println("\nYou search the area carefully${if (target != null) ", focusing on the $target" else ""}...")

        // Perform a Wisdom (Perception) skill check to find hidden items
        val result = game.skillCheckResolver.checkPlayer(
            game.worldState.player,
            com.jcraw.mud.core.StatType.WISDOM,
            com.jcraw.mud.core.Difficulty.MEDIUM  // DC 15 for finding hidden items
        )

        // Display roll details
        println("\nRolling Perception check...")
        println("d20 roll: ${result.roll} + WIS modifier: ${result.modifier} = ${result.total} vs DC ${result.dc}")

        if (result.isCriticalSuccess) {
            println("\n🎲 CRITICAL SUCCESS! (Natural 20)")
        } else if (result.isCriticalFailure) {
            println("\n💀 CRITICAL FAILURE! (Natural 1)")
        }

        if (result.success) {
            println("\n✅ Success!")

            // Find hidden items in the room
            val hiddenItems = room.entities.filterIsInstance<Entity.Item>().filter { !it.isPickupable }
            val pickupableItems = room.entities.filterIsInstance<Entity.Item>().filter { it.isPickupable }

            if (hiddenItems.isNotEmpty() || pickupableItems.isNotEmpty()) {
                if (pickupableItems.isNotEmpty()) {
                    println("You find the following items:")
                    pickupableItems.forEach { item ->
                        println("  - ${item.name}: ${item.description}")
                    }
                }
                if (hiddenItems.isNotEmpty()) {
                    println("\nYou also notice some interesting features:")
                    hiddenItems.forEach { item ->
                        println("  - ${item.name}: ${item.description}")
                    }
                }
            } else {
                println("You don't find anything hidden here.")
            }
        } else {
            println("\n❌ Failure!")
            println("You don't find anything of interest.")
        }
    }
}

// String repetition helper
private operator fun String.times(n: Int): String = repeat(n)
