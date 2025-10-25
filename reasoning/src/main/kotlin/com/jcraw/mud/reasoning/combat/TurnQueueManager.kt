package com.jcraw.mud.reasoning.combat

import com.jcraw.mud.core.WorldState
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.CombatComponent
import com.jcraw.mud.core.ComponentType
import java.util.PriorityQueue

/**
 * Manages the turn order for asynchronous combat using a priority queue.
 *
 * Entities are ordered by their actionTimerEnd value (when they can next act).
 * The queue is maintained in-memory for performance; persistence happens via
 * CombatComponent storage in the database.
 *
 * Thread-safety: This class is NOT thread-safe. External synchronization required.
 */
class TurnQueueManager {
    private data class TurnEntry(
        val entityId: String,
        val timerEnd: Long
    ) : Comparable<TurnEntry> {
        override fun compareTo(other: TurnEntry): Int = timerEnd.compareTo(other.timerEnd)
    }

    private val queue = PriorityQueue<TurnEntry>()
    private val entityTimers = mutableMapOf<String, Long>()

    /**
     * Adds or updates an entity in the turn queue.
     *
     * @param entityId The entity's unique identifier
     * @param timerEnd The game time when this entity can next act
     */
    fun enqueue(entityId: String, timerEnd: Long) {
        require(entityId.isNotBlank()) { "Entity ID cannot be blank" }
        require(timerEnd >= 0) { "Timer end must be non-negative" }

        // Remove old entry if exists
        remove(entityId)

        // Add new entry
        queue.add(TurnEntry(entityId, timerEnd))
        entityTimers[entityId] = timerEnd
    }

    /**
     * Returns and removes the next entity that can act at or before currentTime.
     *
     * @param currentTime The current game time
     * @return The entity ID if one is ready, null otherwise
     */
    fun dequeue(currentTime: Long): String? {
        val entry = queue.peek() ?: return null
        return if (entry.timerEnd <= currentTime) {
            queue.poll()
            entityTimers.remove(entry.entityId)
            entry.entityId
        } else {
            null
        }
    }

    /**
     * Checks the next entity in queue without removing it.
     *
     * @return Pair of (entityId, timerEnd) or null if queue is empty
     */
    fun peek(): Pair<String, Long>? {
        val entry = queue.peek() ?: return null
        return entry.entityId to entry.timerEnd
    }

    /**
     * Removes an entity from the queue (e.g., when entity dies or flees).
     *
     * @param entityId The entity to remove
     * @return true if entity was in queue, false otherwise
     */
    fun remove(entityId: String): Boolean {
        val timer = entityTimers.remove(entityId) ?: return false
        queue.removeIf { it.entityId == entityId }
        return true
    }

    /**
     * Checks if an entity is currently in the queue.
     *
     * @param entityId The entity to check
     * @return true if entity is in queue
     */
    fun contains(entityId: String): Boolean = entityId in entityTimers

    /**
     * Gets the timer end value for an entity.
     *
     * @param entityId The entity to query
     * @return The timer end value, or null if not in queue
     */
    fun getTimerEnd(entityId: String): Long? = entityTimers[entityId]

    /**
     * Clears the entire queue.
     */
    fun clear() {
        queue.clear()
        entityTimers.clear()
    }

    /**
     * Returns the current size of the queue.
     */
    fun size(): Int = queue.size

    /**
     * Rebuilds the queue from WorldState after loading from database.
     *
     * This scans all entities in all rooms for CombatComponents and
     * re-enqueues those with valid actionTimerEnd values.
     *
     * @param worldState The loaded world state
     */
    fun rebuild(worldState: WorldState) {
        clear()

        worldState.rooms.values.forEach { room ->
            room.entities.forEach { entity ->
                // Only NPCs can have CombatComponents
                if (entity is Entity.NPC) {
                    val combatComponent: CombatComponent? = entity.getComponent(ComponentType.COMBAT)
                    if (combatComponent != null && combatComponent.actionTimerEnd > 0) {
                        enqueue(entity.id, combatComponent.actionTimerEnd)
                    }
                }
            }
        }
    }

    /**
     * Returns all entities currently in the queue (for debugging/testing).
     */
    fun getAllEntities(): List<Pair<String, Long>> {
        return entityTimers.map { it.key to it.value }.sortedBy { it.second }
    }
}
