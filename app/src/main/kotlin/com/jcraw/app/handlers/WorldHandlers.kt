package com.jcraw.app.handlers

import com.jcraw.app.MudGame

/**
 * Handlers for procedural world navigation (World Generation System V2).
 *
 * These handlers support:
 * - Natural language exits (e.g., "climb the ladder", "through the door")
 * - Hidden exits with Perception checks
 * - Exit conditions (skill/item requirements)
 * - Terrain-based movement costs and damage
 * - Scouting exits without moving
 *
 * NOTE: These handlers require world generation system integration in MudGame.
 * Current status: STUB implementations awaiting integration.
 *
 * Required MudGame components (not yet added):
 * - ExitResolver (for three-phase exit resolution)
 * - MovementCostCalculator (for terrain handling)
 * - NavigationState (for tracking player location in hierarchy)
 * - WorldChunkRepository / SpacePropertiesRepository (for loading spaces)
 * - WorldPersistence (for save/load integration)
 *
 * See docs/WORLD_GENERATION.md for complete system documentation.
 */
object WorldHandlers {

    /**
     * Scout/peek in a direction without moving.
     *
     * Shows exit description, condition requirements, and hints about what lies ahead.
     * Does NOT consume movement or advance game time.
     *
     * @param game MudGame instance
     * @param direction Direction or exit description to scout (supports natural language)
     */
    fun handleScout(game: MudGame, direction: String) {
        val exitResolver = game.exitResolver
        if (exitResolver == null) {
            println("\nWorld generation system not available (requires LLM).")
            return
        }

        // Check if player is in a procedurally generated space
        val currentSpaceId = game.worldState.player.currentRoomId
        val currentSpace = game.spacePropertiesRepository.findByChunkId(currentSpaceId)
            .getOrNull()

        if (currentSpace == null) {
            println("\nYou are not currently in a procedurally generated area.")
            println("Use standard movement commands (n/s/e/w) instead.")
            return
        }

        // Resolve the exit
        val result = kotlinx.coroutines.runBlocking {
            exitResolver.resolve(direction, currentSpace, game.worldState.player)
        }

        when (result) {
            is com.jcraw.mud.reasoning.world.ResolveResult.Success -> {
                println("\nYou examine the exit ${result.exit.direction}...")
                println(result.exit.description)

                // Show condition hints if any
                val exitDesc = exitResolver.describeExit(result.exit, game.worldState.player)
                if (exitDesc != "${result.exit.direction}: ${result.exit.description}") {
                    println("\n$exitDesc")
                }

                // Peek at destination space
                val destSpace = game.spacePropertiesRepository.findByChunkId(result.targetId)
                    .getOrNull()
                if (destSpace != null) {
                    println("\nAhead you see: ${destSpace.description}")
                } else {
                    println("\nYou can't quite make out what lies beyond.")
                }
            }
            is com.jcraw.mud.reasoning.world.ResolveResult.Failure -> {
                println("\n${result.reason}")
            }
            is com.jcraw.mud.reasoning.world.ResolveResult.Ambiguous -> {
                println("\nWhich exit did you mean?")
                result.suggestions.forEach { (dir, desc) ->
                    println("  - $dir: $desc")
                }
            }
        }
    }

    /**
     * Travel to an adjacent space using natural language navigation.
     *
     * Supports three-phase exit resolution:
     * 1. Exact match for cardinal directions (n/s/e/w/up/down)
     * 2. Fuzzy match for typos
     * 3. LLM parsing for natural language ("climb the ladder", "through the door")
     *
     * Handles:
     * - Hidden exits (requires Perception skill)
     * - Conditional exits (skill/item requirements)
     * - Terrain movement costs and damage
     * - Navigation state updates (tracks player in hierarchy)
     *
     * @param game MudGame instance
     * @param direction Direction or exit description (supports natural language)
     */
    fun handleTravel(game: MudGame, direction: String) {
        val exitResolver = game.exitResolver
        val movementCalc = game.movementCostCalculator

        if (exitResolver == null) {
            println("\nWorld generation system not available (requires LLM).")
            return
        }

        // Check if player is in a procedurally generated space
        val currentSpaceId = game.worldState.player.currentRoomId
        val currentSpace = game.spacePropertiesRepository.findByChunkId(currentSpaceId)
            .getOrNull()

        if (currentSpace == null) {
            println("\nYou are not currently in a procedurally generated area.")
            println("Use standard movement commands (n/s/e/w) instead.")
            return
        }

        // Resolve exit
        val resolveResult = kotlinx.coroutines.runBlocking {
            exitResolver.resolve(direction, currentSpace, game.worldState.player)
        }

        when (resolveResult) {
            is com.jcraw.mud.reasoning.world.ResolveResult.Success -> {
                // Load destination space
                val destSpace = game.spacePropertiesRepository.findByChunkId(resolveResult.targetId)
                    .getOrNull()

                if (destSpace == null) {
                    println("\nThat exit leads nowhere (destination not found).")
                    return
                }

                // Calculate movement cost
                val cost = movementCalc.calculateCost(destSpace.terrainType, game.worldState.player)

                if (!cost.success) {
                    println("\nThe terrain is impassable.")
                    return
                }

                // Apply terrain damage if needed
                if (cost.damageRisk > 0) {
                    val newPlayer = game.worldState.player.takeDamage(cost.damageRisk)
                    game.worldState = game.worldState.updatePlayer(newPlayer)
                    println("\nYou stumble through difficult terrain, taking ${cost.damageRisk} damage!")
                    println("HP: ${newPlayer.health}/${newPlayer.maxHealth}")

                    if (newPlayer.isDead()) {
                        game.handlePlayerDeath()
                        return
                    }
                }

                // Update navigation state (if initialized)
                if (game.navigationState != null) {
                    val navResult = kotlinx.coroutines.runBlocking {
                        game.navigationState!!.updateLocation(resolveResult.targetId, game.worldChunkRepository)
                    }

                    if (navResult.isSuccess) {
                        game.navigationState = navResult.getOrNull()
                    }
                    // Continue even if nav update fails - don't block movement
                }

                // Update player location
                val updatedPlayer = game.worldState.player.copy(currentRoomId = resolveResult.targetId)
                game.worldState = game.worldState.updatePlayer(updatedPlayer)

                // Advance game time
                game.worldState = game.worldState.advanceTime(cost.ticks.toLong())

                // Describe new location
                println("\nYou travel ${resolveResult.exit.direction}.")
                describeSpace(game, destSpace)
            }
            is com.jcraw.mud.reasoning.world.ResolveResult.Failure -> {
                println("\n${resolveResult.reason}")
            }
            is com.jcraw.mud.reasoning.world.ResolveResult.Ambiguous -> {
                println("\nWhich exit did you mean?")
                resolveResult.suggestions.forEach { (dir, desc) ->
                    println("  - $dir: $desc")
                }
            }
        }
    }

    /**
     * Helper: Describe a procedurally generated space to the player.
     * Similar to describeCurrentRoom() but for SpacePropertiesComponent.
     */
    private fun describeSpace(game: MudGame, space: com.jcraw.mud.core.SpacePropertiesComponent) {
        println("\n${space.description}")

        // Show visible exits
        val visibleExits = game.exitResolver?.getVisibleExits(space, game.worldState.player)
            ?: emptyList()

        if (visibleExits.isNotEmpty()) {
            println("\nExits:")
            visibleExits.forEach { exit ->
                val exitDesc = game.exitResolver?.describeExit(exit, game.worldState.player)
                    ?: "${exit.direction}: ${exit.description}"
                println("  - $exitDesc")
            }
        }

        // Show entities (if any)
        if (space.entities.isNotEmpty()) {
            println("\nYou see:")
            space.entities.forEach { entityId ->
                println("  - Entity: $entityId") // TODO: Look up entity names from world state
            }
        }

        // Show resources
        if (space.resources.isNotEmpty()) {
            println("\nResources:")
            space.resources.forEach { resource ->
                println("  - ${resource.description}")
            }
        }

        // Show traps (if visible)
        if (space.traps.isNotEmpty()) {
            val perceptionLevel = game.worldState.player.getSkillLevel("Perception")
            val visibleTraps = space.traps.filter { trap ->
                // Simple visibility check - high Perception can spot traps
                perceptionLevel >= (trap.difficulty ?: 0) / 2
            }
            if (visibleTraps.isNotEmpty()) {
                println("\nYou notice potential dangers:")
                visibleTraps.forEach { trap ->
                    println("  - ${trap.description}")
                }
            }
        }

        // Show dropped items
        if (space.itemsDropped.isNotEmpty()) {
            println("\nItems on the ground:")
            space.itemsDropped.forEach { item ->
                println("  - ${item.id}") // TODO: Look up item names from templates
            }
        }
    }
}
