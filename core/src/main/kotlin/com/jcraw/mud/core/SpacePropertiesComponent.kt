package com.jcraw.mud.core

import com.jcraw.mud.core.world.*
import kotlinx.serialization.Serializable

/**
 * Space properties component for atomic room-like spaces
 * Self-contained space logic with exits, traps, resources, entities
 * Description regen tied to state changes via flags
 */
@Serializable
data class SpacePropertiesComponent(
    val name: String = "Unknown Location",
    val description: String = "",
    val exits: List<ExitData> = emptyList(),
    val brightness: Int = 50,
    val terrainType: TerrainType = TerrainType.NORMAL,
    val traps: List<TrapData> = emptyList(),
    val resources: List<ResourceNode> = emptyList(),
    val entities: List<String> = emptyList(), // Entity IDs
    val itemsDropped: List<ItemInstance> = emptyList(),
    val stateFlags: Map<String, Boolean> = emptyMap(),
    val descriptionStale: Boolean = false,
    val isSafeZone: Boolean = false // Safe zones: No mob spawns, no traps, no combat
) : Component {
    override val componentType: ComponentType
        get() = ComponentType.SPACE_PROPERTIES

    /**
     * Update description (to be called with LLM output from generator)
     * Marks description as fresh
     */
    fun withDescription(newDescription: String): SpacePropertiesComponent =
        copy(description = newDescription, descriptionStale = false)

    /**
     * Resolve exit by intent (exact or fuzzy match)
     * Returns matching exit data or null
     */
    fun resolveExit(intent: String, player: PlayerState): ExitData? {
        val normalizedIntent = intent.lowercase().trim()

        // Phase 1: Exact match (cardinal directions)
        val exactMatch = exits.firstOrNull {
            it.direction.lowercase() == normalizedIntent
        }
        if (exactMatch != null) {
            // Check if player can see this exit (hidden check)
            if (exactMatch.isHidden && !canSeeHiddenExit(player, exactMatch)) {
                return null
            }
            return exactMatch
        }

        // Phase 2: Fuzzy match (check description)
        val fuzzyMatch = exits.firstOrNull { exit ->
            exit.description.lowercase().contains(normalizedIntent) ||
            normalizedIntent.contains(exit.direction.lowercase())
        }
        if (fuzzyMatch != null) {
            if (fuzzyMatch.isHidden && !canSeeHiddenExit(player, fuzzyMatch)) {
                return null
            }
            return fuzzyMatch
        }

        // Phase 3 would be LLM parse - that happens in handler/resolver layer
        return null
    }

    /**
     * Check if player can see hidden exit (Perception check)
     */
    private fun canSeeHiddenExit(player: PlayerState, exit: ExitData): Boolean {
        val skillCondition = exit.conditions.firstOrNull { it is Condition.SkillCheck } as? Condition.SkillCheck
        return skillCondition == null || skillCondition.meetsCondition(player)
    }

    /**
     * Get visible exits for player (filters hidden exits by Perception)
     */
    fun getVisibleExits(player: PlayerState): List<ExitData> =
        exits.filter { exit ->
            !exit.isHidden || canSeeHiddenExit(player, exit)
        }

    /**
     * Apply state change
     * Marks description as stale if flags change
     */
    fun applyChange(flag: String, value: Boolean): SpacePropertiesComponent {
        val newFlags = stateFlags + (flag to value)
        val flagsChanged = stateFlags[flag] != value
        return copy(
            stateFlags = newFlags,
            descriptionStale = descriptionStale || flagsChanged
        )
    }

    /**
     * Add exit
     */
    fun addExit(exit: ExitData): SpacePropertiesComponent =
        copy(exits = exits + exit)

    /**
     * Remove exit by direction
     */
    fun removeExit(direction: String): SpacePropertiesComponent =
        copy(exits = exits.filterNot { it.direction == direction })

    /**
     * Update trap (e.g., trigger it)
     */
    fun updateTrap(trapId: String, updatedTrap: TrapData): SpacePropertiesComponent =
        copy(traps = traps.map { if (it.id == trapId) updatedTrap else it })

    /**
     * Add trap
     */
    fun addTrap(trap: TrapData): SpacePropertiesComponent =
        copy(traps = traps + trap)

    /**
     * Remove trap
     */
    fun removeTrap(trapId: String): SpacePropertiesComponent =
        copy(traps = traps.filterNot { it.id == trapId })

    /**
     * Add resource node
     */
    fun addResource(resource: ResourceNode): SpacePropertiesComponent =
        copy(resources = resources + resource)

    /**
     * Update resource node (e.g., after harvest)
     */
    fun updateResource(resourceId: String, updatedResource: ResourceNode): SpacePropertiesComponent =
        copy(resources = resources.map { if (it.id == resourceId) updatedResource else it })

    /**
     * Remove resource node
     */
    fun removeResource(resourceId: String): SpacePropertiesComponent =
        copy(resources = resources.filterNot { it.id == resourceId })

    /**
     * Add entity ID
     */
    fun addEntity(entityId: String): SpacePropertiesComponent =
        copy(entities = entities + entityId)

    /**
     * Remove entity ID
     */
    fun removeEntity(entityId: String): SpacePropertiesComponent =
        copy(entities = entities - entityId)

    /**
     * Add dropped item
     */
    fun addItem(item: ItemInstance): SpacePropertiesComponent =
        copy(itemsDropped = itemsDropped + item)

    /**
     * Remove dropped item
     */
    fun removeItem(itemId: String): SpacePropertiesComponent =
        copy(itemsDropped = itemsDropped.filterNot { it.id == itemId })

    /**
     * Tick resource respawn timers
     */
    fun tickResources(ticks: Int = 1): SpacePropertiesComponent {
        val updatedResources = resources.map { resource ->
            val (updated, _) = resource.tick(ticks)
            updated
        }
        return copy(resources = updatedResources)
    }
}
