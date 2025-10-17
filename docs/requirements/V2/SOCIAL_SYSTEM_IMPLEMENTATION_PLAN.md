# Advanced Social System - Implementation Plan V2 (REVISED)

**Version:** 2.1 (Revised)
**Status:** Planning
**Date:** 2025-10-16
**Revision:** Component-based architecture with database storage

## Executive Summary

This document outlines the revised architectural design for an advanced social interaction system using:

- ✅ **Component-based architecture** - Scalable ECS-like pattern for entity attachments
- ✅ **Database storage** - Proper database schema for production use
- ✅ **Future-proof design** - Integration points for upcoming skill and story systems
- ✅ **Modular components** - Clean separation of concerns

**Key Features:**
- **Disposition tracking** - Numeric NPC attitude system (-100 to +100)
- **Dynamic knowledge management** - NPCs with individual knowledge bases and canon generation
- **Emote system** - Non-verbal social interactions
- **Event-driven mechanics** - Actions affect NPC relationships
- **Contextual dialogue** - LLM responses influenced by disposition and history

---

## Table of Contents

1. [Architectural Decisions (REVISED)](#architectural-decisions-revised)
2. [Component System Design](#component-system-design)
3. [Database Schema](#database-schema)
4. [Data Model Design](#data-model-design)
5. [Component Design](#component-design)
6. [Integration Points](#integration-points)
7. [Implementation Phases](#implementation-phases)
8. [Testing Strategy](#testing-strategy)

---

## Architectural Decisions (REVISED)

### Decision 1: Component-Based Entity Architecture ✅

**Question:** How should we extend entities without bloating data classes?

**Decision:** Implement component-based architecture (ECS-like pattern).

**Rationale:**
1. **Scalability** - Each system adds components, not fields to Entity
2. **Separation of Concerns** - Social data in SocialComponent, combat in CombatComponent, etc.
3. **Flexibility** - Components can attach to any entity type (NPC, Player, Feature)
4. **Future-Proof** - Easy to add new systems (trading, crafting, reputation, etc.)
5. **Clean Code** - Entity.NPC doesn't become bloated with 50+ fields

**Design Pattern:**

```kotlin
// Entity has generic component attachment
interface Entity {
    val id: String
    val components: Map<ComponentType, Component>

    fun <T : Component> getComponent(type: ComponentType): T?
    fun withComponent(component: Component): Entity
}

// Components are tagged by type
enum class ComponentType {
    SOCIAL,
    COMBAT,
    TRADING,
    SKILL,
    STORY,
    // ... future component types
}

// Each component is a sealed interface
sealed interface Component {
    val componentType: ComponentType
}
```

**Example:**

```kotlin
val guard = Entity.NPC(
    id = "guard-1",
    name = "Old Guard",
    components = mapOf(
        ComponentType.SOCIAL to SocialComponent(
            disposition = 0,
            personality = "gruff veteran"
        ),
        ComponentType.COMBAT to CombatComponent(
            health = 50,
            stats = Stats(...)
        )
    )
)

// Get social component
val social = guard.getComponent<SocialComponent>(ComponentType.SOCIAL)
val disposition = social?.disposition ?: 0

// Add/update component
val updatedGuard = guard.withComponent(
    social.copy(disposition = disposition + 10)
)
```

### Decision 2: Database Storage ✅

**Question:** How should we persist game state?

**Decision:** Use proper database (SQLite/PostgreSQL) with component tables.

**Rationale:**
1. **Production Ready** - JSON files don't scale for real game
2. **Querying** - SQL enables complex queries (find all NPCs with disposition > 50)
3. **Atomicity** - Database transactions ensure data consistency
4. **Performance** - Indexes for fast lookups
5. **Migration Support** - Schema versioning for upgrades

**Technology Choice:**

**Option A: SQLite (Recommended for initial implementation)**
- Embedded database, zero configuration
- Single file storage
- ACID compliance
- Great for single-player and local multi-player
- Kotlin library: `org.xerial:sqlite-jdbc`

**Option B: PostgreSQL (Future upgrade for networked multi-player)**
- Client-server architecture
- Better concurrency
- Advanced features (JSONB, full-text search)
- Kotlin library: `org.postgresql:postgresql` + Exposed ORM

**Initial Implementation: SQLite with migration path to PostgreSQL**

### Decision 3: Component Repository Pattern ✅

**Question:** How should components interact with database?

**Decision:** Use Repository pattern with interface abstraction.

**Design:**

```kotlin
interface ComponentRepository<T : Component> {
    suspend fun save(entityId: String, component: T)
    suspend fun load(entityId: String): T?
    suspend fun delete(entityId: String)
    suspend fun query(predicate: (T) -> Boolean): List<Pair<String, T>>
}

class SocialComponentRepository(private val db: Database) : ComponentRepository<SocialComponent> {
    override suspend fun save(entityId: String, component: SocialComponent) {
        // SQL INSERT/UPDATE
    }

    override suspend fun load(entityId: String): SocialComponent? {
        // SQL SELECT
    }

    override suspend fun query(predicate: (SocialComponent) -> Boolean): List<Pair<String, SocialComponent>> {
        // SQL WHERE with predicate
    }
}
```

**Benefits:**
- Abstracts database implementation
- Easy to swap SQLite → PostgreSQL
- Easy to add caching layer
- Easy to test with in-memory mock

### Decision 4: Future System Stubs ✅

**Question:** How should we integrate with future skill and story systems?

**Decision:** Define stub interfaces with clear integration points.

**Design:**

```kotlin
// Stub for future :skill module
interface SkillSystem {
    suspend fun checkSkill(
        playerId: String,
        skillName: String,
        difficulty: Int
    ): SkillCheckResult

    suspend fun getSkillLevel(playerId: String, skillName: String): Int
}

// Stub for future :story module
interface StorySystem {
    suspend fun getRelevantQuests(npcId: String): List<QuestHint>
    suspend fun recordStoryEvent(event: StoryEvent)
    suspend fun getWorldLore(topic: String): String?
}

// Stub implementations for current use
class StubSkillSystem : SkillSystem {
    override suspend fun checkSkill(playerId: String, skillName: String, difficulty: Int): SkillCheckResult {
        // TODO: Replace with real skill system
        val roll = Random.nextInt(1, 21)
        return SkillCheckResult(success = roll >= difficulty, roll = roll)
    }

    override suspend fun getSkillLevel(playerId: String, skillName: String): Int {
        // TODO: Replace with real skill system
        return 0
    }
}

class StubStorySystem : StorySystem {
    override suspend fun getRelevantQuests(npcId: String): List<QuestHint> {
        // TODO: Replace with real story system
        return emptyList()
    }

    override suspend fun recordStoryEvent(event: StoryEvent) {
        // TODO: Replace with real story system
        println("Story event: $event")
    }

    override suspend fun getWorldLore(topic: String): String? {
        // TODO: Replace with real story system
        return null
    }
}
```

**Integration Points Marked:**
- All stub implementations have `// TODO: INTEGRATION POINT - Replace with :skill module`
- Easy to find with grep
- Clear contracts defined
- Systems can be developed independently

---

## Component System Design

### Core Component Architecture

```kotlin
// core/src/main/kotlin/com/jcraw/mud/core/Component.kt

package com.jcraw.mud.core

import kotlinx.serialization.Serializable

/**
 * Marker interface for all entity components
 * Components add behavior/data to entities without bloating entity classes
 */
sealed interface Component {
    val componentType: ComponentType
}

/**
 * Component type discriminator for storage and lookup
 */
@Serializable
enum class ComponentType {
    SOCIAL,
    COMBAT,
    TRADING,
    SKILL,
    STORY,
    REPUTATION,
    // Future component types can be added here
}

/**
 * Component attachment support for entities
 */
interface ComponentHost {
    val id: String
    val components: Map<ComponentType, Component>

    /**
     * Get component of specific type, or null if not present
     */
    fun <T : Component> getComponent(type: ComponentType): T? {
        @Suppress("UNCHECKED_CAST")
        return components[type] as? T
    }

    /**
     * Check if entity has component of type
     */
    fun hasComponent(type: ComponentType): Boolean {
        return components.containsKey(type)
    }

    /**
     * Create copy of entity with component added/replaced
     * Immutable operation - returns new entity
     */
    fun withComponent(component: Component): ComponentHost

    /**
     * Create copy of entity with component removed
     */
    fun withoutComponent(type: ComponentType): ComponentHost
}
```

### SocialComponent Definition

```kotlin
// core/src/main/kotlin/com/jcraw/mud/core/SocialComponent.kt

package com.jcraw.mud.core

import kotlinx.serialization.Serializable

/**
 * Social interaction component for NPCs
 * Tracks disposition, personality, knowledge, and relationship history
 */
@Serializable
data class SocialComponent(
    val disposition: Int = 0, // -100 (hostile) to +100 (allied)
    val personality: String, // e.g., "gruff warrior", "wise scholar"
    val traits: List<String> = emptyList(), // ["honorable", "greedy", "cowardly"]
    val knowledgeEntries: List<String> = emptyList(), // IDs of knowledge in DB
    val conversationCount: Int = 0, // Track interaction frequency
    val lastInteractionTime: Long = 0L, // For time-based disposition decay (future)
    override val componentType: ComponentType = ComponentType.SOCIAL
) : Component {

    /**
     * Get disposition tier for behavior lookup
     */
    fun getDispositionTier(): DispositionTier = when {
        disposition >= 75 -> DispositionTier.ALLIED
        disposition >= 25 -> DispositionTier.FRIENDLY
        disposition >= -25 -> DispositionTier.NEUTRAL
        disposition >= -75 -> DispositionTier.UNFRIENDLY
        else -> DispositionTier.HOSTILE
    }

    /**
     * Apply disposition change, clamped to -100..100
     */
    fun applyDispositionChange(delta: Int): SocialComponent {
        val newDisposition = (disposition + delta).coerceIn(-100, 100)
        return copy(disposition = newDisposition)
    }

    /**
     * Increment conversation counter and update last interaction time
     */
    fun incrementConversationCount(): SocialComponent {
        return copy(
            conversationCount = conversationCount + 1,
            lastInteractionTime = System.currentTimeMillis()
        )
    }

    /**
     * Add knowledge entry reference
     */
    fun addKnowledge(knowledgeId: String): SocialComponent {
        return copy(knowledgeEntries = knowledgeEntries + knowledgeId)
    }
}

@Serializable
enum class DispositionTier {
    ALLIED,    // >= 75
    FRIENDLY,  // >= 25
    NEUTRAL,   // >= -25
    UNFRIENDLY, // >= -75
    HOSTILE    // < -75
}
```

### Updated Entity.NPC with Components

```kotlin
// core/src/main/kotlin/com/jcraw/mud/core/Entity.kt

@Serializable
data class NPC(
    override val id: String,
    override val name: String,
    override val description: String,
    val isHostile: Boolean = false,
    val health: Int = 100,
    val maxHealth: Int = 100,
    val stats: Stats = Stats(),
    val properties: Map<String, String> = emptyMap(),

    // Social interaction (legacy - will be migrated to SocialComponent)
    val persuasionChallenge: SkillChallenge? = null,
    val intimidationChallenge: SkillChallenge? = null,
    val hasBeenPersuaded: Boolean = false,
    val hasBeenIntimidated: Boolean = false,

    // Component system
    override val components: Map<ComponentType, Component> = emptyMap()
) : Entity(), ComponentHost {

    override fun withComponent(component: Component): NPC {
        return copy(components = components + (component.componentType to component))
    }

    override fun withoutComponent(type: ComponentType): NPC {
        return copy(components = components - type)
    }

    /**
     * Helper to get social component with fallback
     */
    fun getSocialComponent(): SocialComponent? {
        return getComponent(ComponentType.SOCIAL)
    }

    /**
     * Get current disposition (0 if no social component)
     */
    fun getDisposition(): Int {
        return getSocialComponent()?.disposition ?: 0
    }

    /**
     * Apply social event to NPC
     */
    fun applySocialEvent(event: SocialEvent): NPC {
        val social = getSocialComponent() ?: SocialComponent(
            personality = "ordinary",
            traits = emptyList()
        )
        return withComponent(social.applyDispositionChange(event.dispositionDelta))
    }

    /**
     * Check if NPC should be hostile based on disposition
     */
    fun isHostileByDisposition(): Boolean {
        return getSocialComponent()?.getDispositionTier() == DispositionTier.HOSTILE || isHostile
    }
}
```

---

## Database Schema

### SQLite Schema Design

```sql
-- schema.sql

-- ============================================================================
-- ENTITIES TABLE
-- ============================================================================
-- Core entity data (shared across all entity types)
CREATE TABLE IF NOT EXISTS entities (
    id TEXT PRIMARY KEY,
    entity_type TEXT NOT NULL, -- 'NPC', 'Item', 'Feature', 'Player'
    name TEXT NOT NULL,
    description TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE INDEX idx_entities_type ON entities(entity_type);

-- ============================================================================
-- COMPONENTS TABLE
-- ============================================================================
-- Component data (polymorphic - stores JSON for flexibility)
CREATE TABLE IF NOT EXISTS components (
    entity_id TEXT NOT NULL,
    component_type TEXT NOT NULL, -- 'SOCIAL', 'COMBAT', 'TRADING', etc.
    component_data TEXT NOT NULL, -- JSON serialized component
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    PRIMARY KEY (entity_id, component_type),
    FOREIGN KEY (entity_id) REFERENCES entities(id) ON DELETE CASCADE
);

CREATE INDEX idx_components_type ON components(component_type);

-- ============================================================================
-- SOCIAL COMPONENTS TABLE (Denormalized for querying)
-- ============================================================================
-- Separate table for social components to enable SQL queries on disposition
CREATE TABLE IF NOT EXISTS social_components (
    entity_id TEXT PRIMARY KEY,
    disposition INTEGER NOT NULL DEFAULT 0,
    personality TEXT NOT NULL,
    conversation_count INTEGER NOT NULL DEFAULT 0,
    last_interaction_time INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY (entity_id) REFERENCES entities(id) ON DELETE CASCADE,
    CHECK (disposition >= -100 AND disposition <= 100)
);

CREATE INDEX idx_social_disposition ON social_components(disposition);
CREATE INDEX idx_social_last_interaction ON social_components(last_interaction_time);

-- ============================================================================
-- SOCIAL TRAITS TABLE (Many-to-Many)
-- ============================================================================
CREATE TABLE IF NOT EXISTS social_traits (
    entity_id TEXT NOT NULL,
    trait TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    PRIMARY KEY (entity_id, trait),
    FOREIGN KEY (entity_id) REFERENCES social_components(entity_id) ON DELETE CASCADE
);

CREATE INDEX idx_social_traits_trait ON social_traits(trait);

-- ============================================================================
-- KNOWLEDGE ENTRIES TABLE
-- ============================================================================
-- Knowledge that NPCs possess (with RAG vector embeddings)
CREATE TABLE IF NOT EXISTS knowledge_entries (
    id TEXT PRIMARY KEY,
    entity_id TEXT NOT NULL, -- Which NPC knows this
    content TEXT NOT NULL, -- The actual knowledge text
    is_canon BOOLEAN NOT NULL DEFAULT 0, -- Official game lore?
    source TEXT NOT NULL, -- 'PREDEFINED', 'GENERATED', 'PLAYER_TAUGHT', 'OBSERVED'
    created_at INTEGER NOT NULL,
    FOREIGN KEY (entity_id) REFERENCES entities(id) ON DELETE CASCADE
);

CREATE INDEX idx_knowledge_entity ON knowledge_entries(entity_id);
CREATE INDEX idx_knowledge_canon ON knowledge_entries(is_canon);
CREATE INDEX idx_knowledge_source ON knowledge_entries(source);

-- ============================================================================
-- KNOWLEDGE TAGS TABLE (Many-to-Many)
-- ============================================================================
-- Tags for categorizing knowledge (e.g., topic="magic", relevance="high")
CREATE TABLE IF NOT EXISTS knowledge_tags (
    knowledge_id TEXT NOT NULL,
    tag_key TEXT NOT NULL,
    tag_value TEXT NOT NULL,
    PRIMARY KEY (knowledge_id, tag_key),
    FOREIGN KEY (knowledge_id) REFERENCES knowledge_entries(id) ON DELETE CASCADE
);

CREATE INDEX idx_knowledge_tags_key ON knowledge_tags(tag_key);
CREATE INDEX idx_knowledge_tags_value ON knowledge_tags(tag_value);

-- ============================================================================
-- VECTOR EMBEDDINGS TABLE (For RAG)
-- ============================================================================
-- Vector embeddings for semantic search
-- Note: SQLite doesn't have native vector support, but we can use extension
-- or store as BLOB and do similarity search in application layer
CREATE TABLE IF NOT EXISTS vector_embeddings (
    id TEXT PRIMARY KEY,
    knowledge_id TEXT NOT NULL,
    embedding BLOB NOT NULL, -- Serialized float array
    model TEXT NOT NULL, -- Which embedding model was used
    created_at INTEGER NOT NULL,
    FOREIGN KEY (knowledge_id) REFERENCES knowledge_entries(id) ON DELETE CASCADE
);

CREATE INDEX idx_embeddings_knowledge ON vector_embeddings(knowledge_id);

-- ============================================================================
-- SOCIAL EVENTS LOG TABLE
-- ============================================================================
-- History of social interactions (for analytics and debugging)
CREATE TABLE IF NOT EXISTS social_events_log (
    id TEXT PRIMARY KEY,
    entity_id TEXT NOT NULL,
    player_id TEXT NOT NULL,
    event_type TEXT NOT NULL, -- 'HELP_PROVIDED', 'THREATENED', etc.
    disposition_delta INTEGER NOT NULL,
    description TEXT NOT NULL,
    occurred_at INTEGER NOT NULL,
    FOREIGN KEY (entity_id) REFERENCES entities(id) ON DELETE CASCADE
);

CREATE INDEX idx_events_entity ON social_events_log(entity_id);
CREATE INDEX idx_events_player ON social_events_log(player_id);
CREATE INDEX idx_events_time ON social_events_log(occurred_at);

-- ============================================================================
-- WORLD STATE TABLE
-- ============================================================================
-- Current world state snapshot
CREATE TABLE IF NOT EXISTS world_state (
    id TEXT PRIMARY KEY,
    current_room_id TEXT NOT NULL,
    game_time INTEGER NOT NULL,
    state_data TEXT NOT NULL, -- JSON serialized WorldState
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

-- ============================================================================
-- PLAYER STATE TABLE
-- ============================================================================
-- Player-specific state
CREATE TABLE IF NOT EXISTS player_state (
    player_id TEXT PRIMARY KEY,
    current_room_id TEXT NOT NULL,
    health INTEGER NOT NULL,
    max_health INTEGER NOT NULL,
    state_data TEXT NOT NULL, -- JSON serialized PlayerState
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
```

### Database Repository Interfaces

```kotlin
// core/src/main/kotlin/com/jcraw/mud/core/repository/Repository.kt

package com.jcraw.mud.core.repository

import com.jcraw.mud.core.*

/**
 * Generic repository interface for component storage
 */
interface ComponentRepository<T : Component> {
    suspend fun save(entityId: String, component: T)
    suspend fun load(entityId: String): T?
    suspend fun delete(entityId: String)
    suspend fun exists(entityId: String): Boolean
}

/**
 * Repository for social components with query support
 */
interface SocialComponentRepository : ComponentRepository<SocialComponent> {
    /**
     * Find all NPCs with disposition in range
     */
    suspend fun findByDispositionRange(min: Int, max: Int): List<Pair<String, SocialComponent>>

    /**
     * Find all NPCs with specific trait
     */
    suspend fun findByTrait(trait: String): List<Pair<String, SocialComponent>>

    /**
     * Update only disposition (optimized)
     */
    suspend fun updateDisposition(entityId: String, newDisposition: Int)
}

/**
 * Repository for knowledge entries with RAG support
 */
interface KnowledgeRepository {
    suspend fun save(entry: KnowledgeEntry): String // Returns knowledge ID
    suspend fun load(knowledgeId: String): KnowledgeEntry?
    suspend fun delete(knowledgeId: String)

    /**
     * Find knowledge by entity (NPC) ID
     */
    suspend fun findByEntity(entityId: String): List<KnowledgeEntry>

    /**
     * Semantic search using vector embeddings
     */
    suspend fun search(query: String, k: Int = 5, entityId: String? = null): List<KnowledgeEntry>

    /**
     * Get all canon knowledge
     */
    suspend fun getCanonKnowledge(): List<KnowledgeEntry>

    /**
     * Store vector embedding for knowledge
     */
    suspend fun saveEmbedding(knowledgeId: String, embedding: List<Double>, model: String)
}

/**
 * Repository for social events (logging/analytics)
 */
interface SocialEventRepository {
    suspend fun log(
        entityId: String,
        playerId: String,
        event: SocialEvent,
        timestamp: Long = System.currentTimeMillis()
    )

    suspend fun getHistory(entityId: String, limit: Int = 100): List<SocialEventLog>
    suspend fun getPlayerHistory(playerId: String, limit: Int = 100): List<SocialEventLog>
}

data class SocialEventLog(
    val id: String,
    val entityId: String,
    val playerId: String,
    val eventType: String,
    val dispositionDelta: Int,
    val description: String,
    val occurredAt: Long
)
```

---

## Data Model Design

### SocialEvent (Sealed Class)

```kotlin
// core/src/main/kotlin/com/jcraw/mud/core/SocialEvent.kt

package com.jcraw.mud.core

import kotlinx.serialization.Serializable

/**
 * Events that affect NPC disposition
 * Each event has a disposition delta that gets applied
 */
@Serializable
sealed class SocialEvent {
    abstract val dispositionDelta: Int
    abstract val description: String
    abstract val eventType: String

    @Serializable
    data class HelpProvided(
        override val dispositionDelta: Int = 20,
        override val description: String = "You helped this NPC"
    ) : SocialEvent() {
        override val eventType: String = "HELP_PROVIDED"
    }

    @Serializable
    data class HelpRefused(
        override val dispositionDelta: Int = -10,
        override val description: String = "You refused to help"
    ) : SocialEvent() {
        override val eventType: String = "HELP_REFUSED"
    }

    @Serializable
    data class Threatened(
        override val dispositionDelta: Int = -15,
        override val description: String = "You threatened this NPC"
    ) : SocialEvent() {
        override val eventType: String = "THREATENED"
    }

    @Serializable
    data class Intimidated(
        val success: Boolean,
        override val description: String = if (success) "You successfully intimidated this NPC" else "You failed to intimidate this NPC"
    ) : SocialEvent() {
        override val dispositionDelta: Int = if (success) -20 else -5
        override val eventType: String = "INTIMIDATED"
    }

    @Serializable
    data class Persuaded(
        val success: Boolean,
        override val description: String = if (success) "You successfully persuaded this NPC" else "You failed to persuade this NPC"
    ) : SocialEvent() {
        override val dispositionDelta: Int = if (success) 15 else -2
        override val eventType: String = "PERSUADED"
    }

    @Serializable
    data class GiftGiven(
        val itemValue: Int,
        override val description: String = "You gave a gift"
    ) : SocialEvent() {
        override val dispositionDelta: Int = (itemValue / 10).coerceIn(5, 30)
        override val eventType: String = "GIFT_GIVEN"
    }

    @Serializable
    data class EmotePerformed(
        val emoteType: EmoteType,
        override val description: String
    ) : SocialEvent() {
        override val dispositionDelta: Int = emoteType.dispositionDelta
        override val eventType: String = "EMOTE_PERFORMED"
    }

    @Serializable
    data class QuestCompleted(
        val questId: String,
        override val dispositionDelta: Int = 30,
        override val description: String = "You completed a quest for this NPC"
    ) : SocialEvent() {
        override val eventType: String = "QUEST_COMPLETED"
    }

    @Serializable
    data class QuestFailed(
        val questId: String,
        override val dispositionDelta: Int = -25,
        override val description: String = "You failed a quest for this NPC"
    ) : SocialEvent() {
        override val eventType: String = "QUEST_FAILED"
    }

    @Serializable
    data class AttackAttempted(
        override val dispositionDelta: Int = -100,
        override val description: String = "You attacked this NPC"
    ) : SocialEvent() {
        override val eventType: String = "ATTACK_ATTEMPTED"
    }

    @Serializable
    data class ConversationHeld(
        override val dispositionDelta: Int = 1,
        override val description: String = "You had a conversation"
    ) : SocialEvent() {
        override val eventType: String = "CONVERSATION_HELD"
    }
}

/**
 * Types of emotes players can perform
 */
@Serializable
enum class EmoteType(val dispositionDelta: Int, val keywords: List<String>) {
    BOW(5, listOf("bow", "curtsy", "kneel")),
    WAVE(2, listOf("wave", "greet", "hello")),
    NOD(1, listOf("nod", "agree")),
    SHAKE_HEAD(-1, listOf("shake head", "disagree")),
    LAUGH(3, listOf("laugh", "chuckle", "smile")),
    INSULT(-10, listOf("insult", "mock", "taunt")),
    THREATEN(-15, listOf("threaten", "menace", "glare"));

    companion object {
        fun fromKeyword(keyword: String): EmoteType? {
            return values().find { emote ->
                emote.keywords.any { it.equals(keyword, ignoreCase = true) }
            }
        }
    }
}
```

### KnowledgeEntry

```kotlin
// core/src/main/kotlin/com/jcraw/mud/core/KnowledgeEntry.kt

package com.jcraw.mud.core

import kotlinx.serialization.Serializable

/**
 * Represents a piece of knowledge known by an NPC
 * Stored in database with vector embeddings for RAG retrieval
 */
@Serializable
data class KnowledgeEntry(
    val id: String,
    val entityId: String, // Which NPC knows this
    val content: String, // The actual knowledge text
    val isCanon: Boolean, // True if this is official lore
    val source: KnowledgeSource,
    val timestamp: Long = System.currentTimeMillis(),
    val tags: Map<String, String> = emptyMap() // For categorization
)

@Serializable
enum class KnowledgeSource {
    PREDEFINED,    // Written by developers, always canon
    GENERATED,     // LLM-generated in response to question, becomes canon
    PLAYER_TAUGHT, // Player told NPC something, may or may not be canon
    OBSERVED       // NPC witnessed an event
}
```

---

## Component Design

### DispositionManager

```kotlin
// reasoning/src/main/kotlin/com/jcraw/mud/reasoning/DispositionManager.kt

package com.jcraw.mud.reasoning

import com.jcraw.mud.core.*
import com.jcraw.mud.core.repository.SocialComponentRepository
import com.jcraw.mud.core.repository.SocialEventRepository

/**
 * Manages NPC disposition and social event application
 */
class DispositionManager(
    private val socialRepo: SocialComponentRepository,
    private val eventRepo: SocialEventRepository
) {

    /**
     * Apply social event to NPC, persisting changes to database
     */
    suspend fun applyEvent(
        npc: Entity.NPC,
        playerId: String,
        event: SocialEvent
    ): Entity.NPC {
        val social = npc.getSocialComponent() ?: SocialComponent(
            personality = "ordinary",
            traits = emptyList()
        )

        val updated = social.applyDispositionChange(event.dispositionDelta)

        // Save to database
        socialRepo.save(npc.id, updated)

        // Log event
        eventRepo.log(npc.id, playerId, event)

        return npc.withComponent(updated)
    }

    /**
     * Determine if NPC should provide quest hints based on disposition
     */
    fun shouldProvideQuestHints(npc: Entity.NPC): Boolean {
        val tier = npc.getSocialComponent()?.getDispositionTier() ?: DispositionTier.NEUTRAL
        return tier == DispositionTier.ALLIED || tier == DispositionTier.FRIENDLY
    }

    /**
     * Get dialogue tone instruction for LLM based on disposition
     */
    fun getDialogueTone(npc: Entity.NPC): String {
        return when (npc.getSocialComponent()?.getDispositionTier()) {
            DispositionTier.ALLIED -> "extremely friendly, helpful, and warm. Offer hints and secrets willingly."
            DispositionTier.FRIENDLY -> "friendly and helpful. Be accommodating."
            DispositionTier.NEUTRAL -> "neutral and professional. Neither helpful nor rude."
            DispositionTier.UNFRIENDLY -> "cold and curt. Give short, unhelpful responses."
            DispositionTier.HOSTILE -> "hostile and threatening. Refuse to help."
            null -> "neutral and professional."
        }
    }

    /**
     * Calculate price modifier for trading based on disposition
     * Future enhancement: adjust shop prices
     */
    fun getPriceModifier(npc: Entity.NPC): Double {
        return when (npc.getSocialComponent()?.getDispositionTier()) {
            DispositionTier.ALLIED -> 0.7    // 30% discount
            DispositionTier.FRIENDLY -> 0.85  // 15% discount
            DispositionTier.NEUTRAL -> 1.0    // Normal price
            DispositionTier.UNFRIENDLY -> 1.15 // 15% markup
            DispositionTier.HOSTILE -> 1.5    // 50% markup
            null -> 1.0
        }
    }
}
```

---

## Implementation Phases (REVISED)

### Phase 1: Component System Foundation (3-4 hours)

**Deliverables:**
1. ✅ Create `core/Component.kt` - Base component interfaces
2. ✅ Update `Entity.kt` to implement `ComponentHost`
3. ✅ Create `SocialComponent.kt` with full implementation
4. ✅ Create `SocialEvent.kt` sealed class hierarchy
5. ✅ Create `KnowledgeEntry.kt` data class
6. ✅ Write unit tests for component system

**Testing:**
- Test component attachment/detachment
- Test type-safe component retrieval
- Test immutability of withComponent operations
- Test disposition clamping and tier calculation
- Test social event delta application

### Phase 2: Database Layer (4-5 hours)

**Deliverables:**
1. ✅ Create `schema.sql` with all tables
2. ✅ Add SQLite dependency to build.gradle.kts
3. ✅ Create repository interfaces in `core/repository/`
4. ✅ Implement SQLite repositories in new `persistence` module
5. ✅ Create database migration system
6. ✅ Write repository unit tests with in-memory DB

**Testing:**
- Test CRUD operations for all repositories
- Test transaction handling
- Test constraint enforcement (disposition bounds, foreign keys)
- Test query performance with indexes
- Test concurrent access

### Phase 3: Core Business Logic (4-5 hours)

**Deliverables:**
1. ✅ Create `DispositionManager.kt`
2. ✅ Create `EmoteHandler.kt`
3. ✅ Create `NPCKnowledgeManager.kt`
4. ✅ Create stub implementations for SkillSystem and StorySystem
5. ✅ Write unit tests with mocked repositories

**Testing:**
- Test DispositionManager event application
- Test EmoteHandler narrative generation
- Test NPCKnowledgeManager RAG search
- Test canon knowledge generation
- Mock LLM and database for deterministic tests

### Phase 4-10: (Similar to original plan but adapted for component system)

---

## Summary of Changes

### ✅ What Changed

1. **Component-based architecture** - NPCs use component system instead of direct fields
2. **Database storage** - SQLite with proper schema instead of JSON files
3. **Repository pattern** - Clean separation of data access
4. **Future-proof stubs** - Interfaces for skill and story systems
5. **Scalable design** - Easy to add new component types

### ✅ What Stayed the Same

1. **Disposition mechanics** - Same -100 to +100 scale
2. **Knowledge management** - RAG with canon generation
3. **Emote system** - Same emote types and effects
4. **Event-driven changes** - Same social events
5. **Testing strategy** - Comprehensive unit and integration tests

**This design should scale much better for future systems!** 🚀
