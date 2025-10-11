# Architecture

This document describes the technical architecture of the AI-MUD engine.

## Module Structure

Multi-module Gradle project with 8 modules:

### Core Modules

- **core** - World model (Room, WorldState, PlayerState, Entity, Direction, Quest)
  - Immutable data models
  - Game state management
  - No external dependencies except kotlinx.serialization

- **perception** - Input parsing and intent recognition
  - Depends on: core, llm
  - Text-to-Intent parsing
  - Command interpretation

- **reasoning** - LLM-powered content generation and game logic
  - Depends on: core, llm, memory
  - Room description generation
  - NPC dialogue generation
  - Combat narration
  - Procedural dungeon generation
  - Quest generation
  - Skill check resolution

- **memory** - Vector database integration and state persistence
  - Depends on: core
  - RAG (Retrieval-Augmented Generation) system
  - Vector embeddings and similarity search
  - JSON-based persistence
  - Pluggable vector store interface

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
- Deterministic with optional seed
- Boss rooms and entrance designation

## File Locations (Important)

### Main Application
- `app/src/main/kotlin/com/jcraw/app/App.kt` - Entry point and game loops

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
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/procedural/NPCGenerator.kt`
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/procedural/ItemGenerator.kt`
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/procedural/DungeonLayoutGenerator.kt`
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/procedural/ProceduralDungeonBuilder.kt`

### Sample Content
- `core/src/main/kotlin/com/jcraw/mud/core/SampleDungeon.kt`

### Intent System
- `perception/src/main/kotlin/com/jcraw/mud/perception/Intent.kt`

## Design Notes

- **No backward compatibility needed** - Can wipe and restart data between versions
- **Project follows guidelines** in `CLAUDE_GUIDELINES.md`
- **Requirements** are in `docs/requirements.txt`
