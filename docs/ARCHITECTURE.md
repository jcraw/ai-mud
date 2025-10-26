# Architecture

This document describes the technical architecture of the AI-MUD engine.

## Module Structure

Multi-module Gradle project with 9 modules:

### Core Modules

- **core** - World model (Room, WorldState, PlayerState, Entity, Direction, Quest, SocialComponent)
  - Immutable data models
  - Game state management
  - Component system for extensible entity behaviors
  - No external dependencies except kotlinx.serialization

- **perception** - Input parsing and intent recognition
  - Depends on: core, llm
  - Text-to-Intent parsing
  - Command interpretation

- **reasoning** - LLM-powered content generation and game logic
  - Depends on: core, llm, memory
  - Room description generation
  - NPC dialogue generation (disposition-aware)
  - Combat narration
  - Procedural dungeon generation
  - Quest generation
  - Skill check resolution
  - Social system logic (disposition tracking, emotes, knowledge management)

- **memory** - Vector database integration and state persistence
  - Depends on: core
  - RAG (Retrieval-Augmented Generation) system
  - Vector embeddings and similarity search
  - JSON-based persistence
  - Pluggable vector store interface
  - Social system database (SQLite) with knowledge/event/component persistence

- **action** - Output formatting and narration
  - Depends on: core
  - Response generation

- **llm** - OpenAI client and LLM interfaces
  - No dependencies on other modules
  - OpenAI API integration (chat completion + embeddings)
  - ktor 3.1.0 HTTP client

- **app** - Main game application and console interface
  - Depends on all modules
  - Game loop implementation
  - Multi-user server (GameServer, PlayerSession)
  - Single-player game (MudGame)

- **testbot** - Automated testing system
  - Depends on: core, llm, perception, reasoning, memory, action
  - LLM-powered test generation
  - ReAct loop for autonomous testing

- **client** - Compose Multiplatform GUI client
  - Depends on: core, perception, reasoning, memory, action, llm
  - Desktop GUI with unidirectional data flow
  - EngineGameClient wraps complete game engine

- **utils** - Shared utilities
  - No dependencies

### Build Configuration
- Gradle with Kotlin DSL
- Version catalog in `gradle/libs.versions.toml`
- Convention plugin in `buildSrc` for shared build logic
- Java 17 toolchain
- Kotlin 2.2.0
- kotlinx ecosystem dependencies configured

## Implemented Architecture

Clean separation following SOLID principles:

### Layer Responsibilities

1. **Core** - Immutable data models and world state
   - All game entities (Room, Player, NPC, Item, Feature)
   - State transitions are pure functions
   - No I/O or side effects

2. **Perception** - Input parsing and LLM-based intent recognition
   - Converts raw text to structured Intent objects
   - Pattern matching and command aliases

3. **Reasoning** - LLM-powered generation and game logic resolution
   - Processes intents and generates responses
   - Updates world state
   - Generates dynamic content (descriptions, dialogue, narration)
   - Procedural generation (dungeons, quests)

4. **Action** - Output narration and response generation
   - Formats responses for display

5. **Memory** - Vector database for history and structured world state
   - Stores game events with embeddings
   - Semantic search for contextual retrieval
   - Persistence to disk

## Data Flow

```
User Input
    ↓
Perception (text → Intent)
    ↓
Reasoning (Intent + WorldState → Response + NewState)
    ↓
Action (format output)
    ↓
Memory (store for RAG)
```

### Single-Player Flow
1. User types command
2. App.parseInput() → Intent
3. App.processIntent() → calls appropriate handler
4. Handler updates WorldState
5. Response displayed to user

### Multi-User Flow
1. User types command
2. PlayerSession receives input
3. GameServer.processIntent() with player ID
4. Handler updates shared WorldState (thread-safe with Mutex)
5. GameEvent broadcast to relevant players
6. Response sent to player's session

## Key Principles

- **KISS principle** - Avoid overengineering, minimal abstractions
- **Sealed classes over enums** - Better type safety and exhaustive when expressions
- **Behavior-driven testing** - Focus on contracts, not implementation
- **Files under 300-500 lines** - Maintain readability
- **GPT4_1Nano for development** - Cost savings during development
- **Immutable state** - All state transitions return new copies
- **Thread-safe mutations** - Kotlin coroutines with Mutex for shared state

## Component Details

### World State Management
- `WorldState` - Top-level game state
  - Map of rooms
  - Map of players (multi-user support)
  - Available quests
  - Game properties
- `PlayerState` - Per-player state
  - Health, stats, inventory
  - Equipped items
  - Active combat
  - Active quests
  - Experience and gold
- `Room` - Location data
  - Name, description, traits
  - Entities (NPCs, items, features, players)
  - Exits to other rooms

### Combat System
- Turn-based with d20 mechanics
- STR modifiers for damage
- Armor provides defense bonus
- Per-player combat state
- LLM-narrated combat events

### Skill System
- D&D-style stats: STR, DEX, CON, INT, WIS, CHA
- d20 + modifier vs Difficulty Class
- Critical success (nat 20) and failure (nat 1)
- Skill challenges on features and NPCs

### Quest System
- Procedurally generated based on world state
- 6 objective types: Kill, Collect, Explore, Talk, UseSkill, Deliver
- Quest lifecycle: ACTIVE → COMPLETED → CLAIMED
- Rewards: XP, gold, items
- Theme-appropriate quest generation

### RAG (Memory) System
- Stores game events with text embeddings
- Cosine similarity search for relevant context
- Enhances LLM prompts with historical context
- Pluggable vector store (in-memory or persistent)

### Procedural Generation
- 4 themed dungeons: Crypt, Castle, Cave, Temple
- Graph-based room layout
- Theme-appropriate traits, NPCs, and items
- NPCs generated with SocialComponents (personality, traits, disposition)
- Deterministic with optional seed
- Boss rooms and entrance designation

### Social System
- Component-based architecture for extensible NPC behaviors
- Disposition tracking (-100 to +100) with 5 tiers (ALLIED, FRIENDLY, NEUTRAL, UNFRIENDLY, HOSTILE)
- Emote system with 7 emote types (smile, wave, nod, shrug, laugh, cry, bow)
- Question/answer system with persistent knowledge base
- Quest completion grants +15 disposition to quest giver
- Disposition-aware dialogue generation
- SQLite database for persistence (knowledge entries, social events, social components)
- Procedurally generated NPC personalities and traits based on dungeon theme
- See [Social System Documentation](./SOCIAL_SYSTEM.md) for complete details

### Combat Narration Caching System
- **Vector DB caching** for optimized LLM performance
- **Three-tier approach**:
  1. **Pre-generated variants** - Offline generation of 50+ narration variants per scenario
  2. **Semantic search** - Runtime matching of combat context to cached narrations
  3. **LLM fallback** - Live generation for unique scenarios with automatic caching
- **NarrationVariantGenerator** (`memory/combat/NarrationVariantGenerator.kt`)
  - Pre-generates variants for common scenarios (melee, ranged, spell, critical, death, status effects)
  - Tags variants with metadata (weapon type, damage tier, outcome) for semantic search
  - Uses LLM offline to create diverse, vivid combat descriptions
  - Stores in vector DB with embeddings for fast retrieval
- **NarrationMatcher** (`memory/combat/NarrationMatcher.kt`)
  - CombatContext data class captures combat situation (scenario, weapon, damage, outcome)
  - Semantic search via MemoryManager to find best matching cached narration
  - Helper methods: determineDamageTier(), determineScenario() for context classification
  - Weapon category matching for flexible retrieval (e.g., "dagger" matches "blade" variants)
- **CombatNarrator** (`reasoning/CombatNarrator.kt`)
  - narrateAction() - New method for single action narration with caching
  - Flow: Try cache → LLM fallback on miss → Store response in cache
  - Equipment-aware descriptions (includes actual weapon/armor names)
  - Maintains backward compatibility with existing narrateCombatRound()
- **Performance benefits**:
  - Target >60% cache hit rate reduces LLM calls
  - Cached narratives return in <50ms vs. 1-3sec for live LLM
  - Cost savings: $0 for cached vs. ~$0.001 per LLM generation
  - Growing cache improves over time as more scenarios are stored

## File Locations (Important)

### Main Application
- `app/src/main/kotlin/com/jcraw/app/App.kt` - Entry point and game loops
- `app/src/main/kotlin/com/jcraw/app/handlers/` - Intent handlers (~661 lines total)
  - `MovementHandlers.kt` - Navigation and exploration (~173 lines)
  - `ItemHandlers.kt` - Inventory and equipment (~350 lines)
  - `CombatHandlers.kt` - Combat system (~138 lines)

### Multi-User Architecture
- `core/src/main/kotlin/com/jcraw/mud/core/Room.kt` - PlayerId type alias
- `core/src/main/kotlin/com/jcraw/mud/core/WorldState.kt` - Multi-player world state
- `core/src/main/kotlin/com/jcraw/mud/core/PlayerState.kt` - Per-player state
- `app/src/main/kotlin/com/jcraw/app/GameServer.kt` - Thread-safe game server
- `app/src/main/kotlin/com/jcraw/app/PlayerSession.kt` - Player I/O handling
- `app/src/main/kotlin/com/jcraw/app/GameEvent.kt` - Event broadcasting

### LLM Generators (RAG-enhanced)
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/RoomDescriptionGenerator.kt`
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/NPCInteractionGenerator.kt`
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/CombatNarrator.kt`

### Memory/RAG System
- `memory/src/main/kotlin/com/jcraw/mud/memory/MemoryManager.kt`
- `memory/src/main/kotlin/com/jcraw/mud/memory/VectorStoreInterface.kt`
- `memory/src/main/kotlin/com/jcraw/mud/memory/VectorStore.kt` - In-memory
- `memory/src/main/kotlin/com/jcraw/mud/memory/PersistentVectorStore.kt` - Disk-based
- `memory/src/main/kotlin/com/jcraw/mud/memory/MemoryEntry.kt`
- `llm/src/main/kotlin/com/jcraw/sophia/llm/OpenAIClient.kt`

### Persistence
- `memory/src/main/kotlin/com/jcraw/mud/memory/PersistenceManager.kt`

### Combat System
- `core/src/main/kotlin/com/jcraw/mud/core/CombatState.kt`
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/CombatResolver.kt`

### Skill System
- `core/src/main/kotlin/com/jcraw/mud/core/Entity.kt` - Stats
- `core/src/main/kotlin/com/jcraw/mud/core/SkillCheck.kt`
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/SkillCheckResolver.kt`

### Quest System
- `core/src/main/kotlin/com/jcraw/mud/core/Quest.kt` - Data models
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/procedural/QuestGenerator.kt`
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/QuestTracker.kt`

### Procedural Generation
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/procedural/DungeonTheme.kt`
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/procedural/RoomGenerator.kt`
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/procedural/NPCGenerator.kt` - Creates NPCs with SocialComponents
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/procedural/ItemGenerator.kt`
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/procedural/DungeonLayoutGenerator.kt`
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/procedural/ProceduralDungeonBuilder.kt`

### Social System
- `core/src/main/kotlin/com/jcraw/mud/core/Component.kt` - Component system foundation
- `core/src/main/kotlin/com/jcraw/mud/core/SocialComponent.kt` - Social component data model
- `core/src/main/kotlin/com/jcraw/mud/core/SocialEvent.kt` - Social event types
- `memory/src/main/kotlin/com/jcraw/mud/memory/social/SocialDatabase.kt` - SQLite database
- `memory/src/main/kotlin/com/jcraw/mud/memory/social/KnowledgeRepository.kt` - Knowledge persistence
- `memory/src/main/kotlin/com/jcraw/mud/memory/social/SocialEventRepository.kt` - Event persistence
- `memory/src/main/kotlin/com/jcraw/mud/memory/social/SocialComponentRepository.kt` - Component persistence
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/social/DispositionManager.kt` - Disposition tracking
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/social/EmoteHandler.kt` - Emote processing
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/social/NPCKnowledgeManager.kt` - Knowledge queries
- `perception/src/main/kotlin/com/jcraw/mud/perception/Intent.kt` - Emote and AskQuestion intents

### Sample Content
- `core/src/main/kotlin/com/jcraw/mud/core/SampleDungeon.kt`

### Intent System
- `perception/src/main/kotlin/com/jcraw/mud/perception/Intent.kt` - 22+ intent types including Emote and AskQuestion

### Client (GUI)
- `client/src/main/kotlin/com/jcraw/mud/client/Main.kt` - Entry point
- `client/src/main/kotlin/com/jcraw/mud/client/GameViewModel.kt` - State management
- `client/src/main/kotlin/com/jcraw/mud/client/EngineGameClient.kt` - Engine integration (~311 lines)
- `client/src/main/kotlin/com/jcraw/mud/client/handlers/` - Intent handlers (~1400 lines total)
  - `ClientMovementHandlers.kt` - Navigation and exploration (~250 lines)
  - `ClientItemHandlers.kt` - Inventory and equipment (~300 lines)
  - `ClientCombatHandlers.kt` - Combat system (~140 lines)
  - `ClientSocialHandlers.kt` - Social interactions (~230 lines)
  - `ClientSkillQuestHandlers.kt` - Skills, quests, persistence, meta-commands (~480 lines)
- `client/src/main/kotlin/com/jcraw/mud/client/ui/` - UI screens

### Test Bot
- `testbot/src/main/kotlin/com/jcraw/mud/testbot/OutputValidator.kt` - Main orchestrator (~65 lines)
- `testbot/src/main/kotlin/com/jcraw/mud/testbot/validation/` - Validation logic (~1050 lines total)
  - `CodeValidationRules.kt` - Deterministic code validation (~680 lines)
  - `ValidationPrompts.kt` - LLM prompt builders (~460 lines)
  - `ValidationParsers.kt` - Response parsing (~40 lines)

## Design Notes

- **No backward compatibility needed** - Can wipe and restart data between versions
- **Project follows guidelines** in `CLAUDE_GUIDELINES.md`
- **Requirements** are in `docs/requirements.txt`
