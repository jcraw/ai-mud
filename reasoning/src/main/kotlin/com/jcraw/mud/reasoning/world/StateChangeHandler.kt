package com.jcraw.mud.reasoning.world

import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.SpacePropertiesComponent
import com.jcraw.mud.core.world.WorldAction
import com.jcraw.sophia.llm.LLMClient

/**
 * Handles player-initiated world modifications and description regeneration.
 * Applies WorldActions to SpacePropertiesComponent immutably.
 */
class StateChangeHandler(
    private val llmClient: LLMClient
) {
    /**
     * Applies a world action to a space, updating its state immutably.
     * Returns Result<SpacePropertiesComponent> with the updated space.
     */
    fun applyChange(
        space: SpacePropertiesComponent,
        action: WorldAction,
        player: PlayerState
    ): Result<SpacePropertiesComponent> = runCatching {
        when (action) {
            is WorldAction.DestroyObstacle -> {
                space.copy(
                    stateFlags = space.stateFlags + (action.flag to true)
                )
            }

            is WorldAction.TriggerTrap -> {
                val updatedTraps = space.traps.map { trap ->
                    if (trap.id == action.trapId) {
                        trap.copy(triggered = true)
                    } else {
                        trap
                    }
                }
                space.copy(traps = updatedTraps)
            }

            is WorldAction.HarvestResource -> {
                val updatedResources = space.resources.filterNot { it.id == action.nodeId }
                space.copy(resources = updatedResources)
            }

            is WorldAction.PlaceItem -> {
                space.copy(
                    itemsDropped = space.itemsDropped + action.item
                )
            }

            is WorldAction.RemoveItem -> {
                val updatedItems = space.itemsDropped.filterNot { it.id == action.itemId }
                space.copy(itemsDropped = updatedItems)
            }

            is WorldAction.UnlockExit -> {
                val updatedExits = space.exits.map { exit ->
                    if (exit.direction.equals(action.exitDirection, ignoreCase = true)) {
                        // Remove all conditions (exit now unlocked)
                        exit.copy(conditions = emptyList(), isHidden = false)
                    } else {
                        exit
                    }
                }
                space.copy(exits = updatedExits)
            }

            is WorldAction.SetFlag -> {
                space.copy(
                    stateFlags = space.stateFlags + (action.flag to action.value)
                )
            }
        }
    }

    /**
     * Determines if description should be regenerated based on flag changes.
     * Returns true if any flag has changed.
     */
    fun shouldRegenDescription(
        oldFlags: Map<String, Boolean>,
        newFlags: Map<String, Boolean>
    ): Boolean {
        return oldFlags != newFlags
    }

    /**
     * Regenerates space description based on state changes using LLM.
     * Returns new description string incorporating the changes.
     */
    suspend fun regenDescription(
        space: SpacePropertiesComponent,
        oldFlags: Map<String, Boolean>,
        lore: String
    ): Result<String> = runCatching {
        val changedFlags = buildChangedFlagsDescription(oldFlags, space.stateFlags)

        val systemPrompt = "You are a game master describing room changes. Output 2-4 sentences only."

        val userContext = buildString {
            append("Regenerate room description based on state changes.\n\n")
            append("Original description: ${space.description}\n\n")
            append("State changes: $changedFlags\n\n")
            append("Lore context: $lore\n\n")
            append("Output: Updated room description (2-4 sentences) that incorporates the changes ")
            append("while maintaining the overall theme and atmosphere. Focus on what's different now.")
        }

        val response = llmClient.chatCompletion(
            modelId = "gpt-4o-mini",
            systemPrompt = systemPrompt,
            userContext = userContext,
            maxTokens = 200,
            temperature = 0.7
        )

        response.choices.firstOrNull()?.message?.content?.trim()
            ?: throw Exception("LLM returned empty description")
    }

    /**
     * Builds a human-readable description of changed flags for the LLM prompt.
     */
    private fun buildChangedFlagsDescription(
        oldFlags: Map<String, Boolean>,
        newFlags: Map<String, Boolean>
    ): String {
        val changes = mutableListOf<String>()

        // Find new or modified flags
        newFlags.forEach { (flag, value) ->
            val oldValue = oldFlags[flag]
            if (oldValue != value) {
                changes.add("$flag = $value")
            }
        }

        // Find removed flags
        oldFlags.forEach { (flag, _) ->
            if (flag !in newFlags) {
                changes.add("$flag removed")
            }
        }

        return if (changes.isEmpty()) {
            "No changes"
        } else {
            changes.joinToString(", ")
        }
    }
}
