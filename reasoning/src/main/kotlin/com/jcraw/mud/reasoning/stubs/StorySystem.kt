package com.jcraw.mud.reasoning.stubs

/**
 * Quest hint that NPCs can provide
 */
data class QuestHint(
    val questId: String,
    val hint: String,
    val npcId: String
)

/**
 * Story event for narrative tracking
 */
data class StoryEvent(
    val eventType: String,
    val description: String,
    val participants: List<String>, // Entity IDs
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Interface for future :story module
 *
 * TODO: INTEGRATION POINT - Replace with real :story module implementation
 *
 * This stub provides basic interfaces for story/narrative systems until the full
 * story module is implemented.
 */
interface StorySystem {
    /**
     * Get quest hints relevant to an NPC
     *
     * NPCs with high disposition may provide hints about quests
     *
     * @param npcId NPC providing hints
     * @return List of quest hints
     */
    suspend fun getRelevantQuests(npcId: String): List<QuestHint>

    /**
     * Record a story event for narrative tracking
     *
     * @param event The story event to record
     */
    suspend fun recordStoryEvent(event: StoryEvent)

    /**
     * Get world lore about a topic
     *
     * @param topic Topic to query (e.g., "ancient king", "magic system")
     * @return Lore text, or null if no lore exists
     */
    suspend fun getWorldLore(topic: String): String?
}

/**
 * Stub implementation of StorySystem
 *
 * Provides minimal functionality until real story system is ready
 */
class StubStorySystem : StorySystem {

    private val events = mutableListOf<StoryEvent>()

    override suspend fun getRelevantQuests(npcId: String): List<QuestHint> {
        // TODO: INTEGRATION POINT - Replace with real story system
        // Current quest system already exists, so return empty for now
        return emptyList()
    }

    override suspend fun recordStoryEvent(event: StoryEvent) {
        // TODO: INTEGRATION POINT - Replace with real story system
        // For now, just log to console and store in memory
        println("Story event: ${event.eventType} - ${event.description}")
        events.add(event)
    }

    override suspend fun getWorldLore(topic: String): String? {
        // TODO: INTEGRATION POINT - Replace with real story system
        // No world lore database yet
        return null
    }

    /**
     * Get all recorded events (for debugging)
     */
    fun getAllEvents(): List<StoryEvent> = events.toList()

    /**
     * Clear all events (for testing)
     */
    fun clearEvents() {
        events.clear()
    }
}
