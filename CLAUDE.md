# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Current State: MULTI-USER ARCHITECTURE FOUNDATION COMPLETE** - This is an AI-powered MUD (Multi-User Dungeon) engine with modular architecture, procedural dungeon generation, persistent save/load system, **multi-user capable world state**, and a working console-based game loop with turn-based combat, full equipment system (weapons & armor), consumables, D&D-style skill checks, AND RAG-enhanced memory system for contextual narratives. The vision is to create a text-based roleplaying game with dynamic LLM-generated content that remembers and builds on player history.

### What Exists Now
- Complete Gradle multi-module setup with 7 modules
- Core world model: Room, WorldState, PlayerState, Entity hierarchy, CombatState, ItemType
- **Multi-user architecture foundation** - WorldState supports multiple players, per-player combat state ✅
- **PlayerId type** - Unique identifier for each player in the shared world ✅
- Direction enum with bidirectional mapping
- **Stats system (STR, DEX, CON, INT, WIS, CHA)** - D&D-style stats for player & NPCs ✅
- **Sample dungeon with 6 interconnected rooms, entities, rich trait lists, and stat-based NPCs**
- **Intent sealed class hierarchy (Move, Look, Interact, Take, Drop, Talk, Attack, Equip, Use, Check, Persuade, Intimidate, Save, Load, Inventory, Help, Quit)**
- **Working console game loop with text parser**
- **OpenAI LLM client fully integrated with ktor 3.1.0**
- **RoomDescriptionGenerator - LLM-powered vivid room descriptions with RAG context** ✨
- **NPCInteractionGenerator - LLM-powered dynamic NPC dialogue with conversation history** ✨
- **CombatNarrator - LLM-powered atmospheric combat descriptions with fight progression** ✨
- **MemoryManager - RAG system with vector embeddings for contextual retrieval** 💾
- **API key support via local.properties or OPENAI_API_KEY env var**
- **GAME IS FULLY PLAYABLE** - movement, looking, combat, items, LLM descriptions with memory work
- **Item mechanics** - pickup/drop items with take/get/drop/put commands ✅
- **NPC interaction** - talk to NPCs with personality-driven dialogue that remembers past conversations ✅
- **Combat system** - turn-based combat with attack/defend/flee, LLM-narrated, STR modifiers ✅
- **Equipment system** - equip weapons for damage bonuses, armor for defense bonuses ✅
- **Consumables** - use potions/items for healing ✅
- **Skill check system** - D20 + stat modifier vs DC, with critical success/failure ✅
- **Armor system** - equip armor to reduce incoming damage ✅
- **Skill check integration** - Interactive features with skill challenges (locked chests, stuck doors, hidden items, arcane runes) ✅
- **Social skill checks** - Persuasion and intimidation CHA checks for NPCs ✅
- **RAG memory system** - Semantic memory with embeddings and cosine similarity search ✅
- **Procedural dungeon generation** - Generate complete dungeons with rooms, NPCs, items, and loot ✅
- **4 dungeon themes** - Crypt, Castle, Cave, Temple with theme-specific traits ✅
- **Graph-based dungeon layouts** - Connected room networks with entrance and boss rooms ✅
- **Deterministic generation** - Optional seeds for reproducible dungeons ✅
- **Persistent storage** - JSON-based save/load system for game state ✅
- **Save/load commands** - save [name] and load [name] commands in game ✅
- **Pluggable vector stores** - Interface-based design for in-memory or persistent vector stores ✅
- **Multi-user architecture foundation** - WorldState refactored for multiple players, per-player combat ✅

### What Needs to Be Built Next
Remaining tasks organized by priority:
1. **Multi-user implementation** - GameServer, PlayerSession, player visibility, broadcast system
2. **Dynamic quests** - Procedurally generated quest objectives
3. **Persistent memory storage** - Save/load vector embeddings to disk (optional enhancement)
4. Later: More complex quest system, dynamic world events

## Commands

### Build and Development
- `gradle build` - Build the project (requires Java 17 toolchain)
- `gradle check` - Run all checks including tests
- `gradle clean` - Clean all build outputs
- `gradle installDist && app/build/install/app/bin/app` - **Run the game!**
- `gradle :app:build` - Build just the app module

### Testing
- `gradle test` - Run unit tests across all modules
- `gradle :core:test` - Run tests for core module
- `gradle :perception:test` - Run tests for perception module
- `gradle :reasoning:test` - Run tests for reasoning module
- `gradle :memory:test` - Run tests for memory module
- `gradle :action:test` - Run tests for action module
- `gradle :app:test` - Run tests for app module

## Project Structure

Multi-module Gradle project:

### Current Modules
- **core** - World model (Room, WorldState, PlayerState, Entity, Direction)
- **perception** - Input parsing and intent recognition (depends on: core, llm)
- **reasoning** - LLM-powered content generation and game logic (depends on: core, llm, memory)
- **memory** - Vector database integration and state persistence (depends on: core)
- **action** - Output formatting and narration (depends on: core)
- **llm** - OpenAI client and LLM interfaces
- **app** - Main game application and console interface
- **utils** - Shared utilities

### Build Configuration
- Uses Gradle with Kotlin DSL
- Version catalog in `gradle/libs.versions.toml`
- Convention plugin in `buildSrc` for shared build logic
- Java 17 toolchain, Kotlin 2.2.0
- kotlinx ecosystem dependencies configured

## Implementation Notes

### Implemented Architecture
Clean separation following the planned architecture:
- **Core** - Immutable data models and world state
- **Perception** - Input parsing and LLM-based intent recognition
- **Reasoning** - LLM-powered generation and game logic resolution
- **Action** - Output narration and response generation
- **Memory** - Vector database for history and structured world state

### Data Flow
1. User input → **Perception** (parse to Intent)
2. Intent + WorldState → **Reasoning** (generate response + new state)
3. Response → **Action** (format output)
4. All interactions → **Memory** (store for RAG)

### Key Principles
- KISS principle - avoid overengineering
- Use sealed classes over enums
- Focus on behavior-driven testing
- Files under 300-500 lines
- Use GPT4_1Nano for cost savings during development

## Implementation Status

### Completed
✅ Module structure and dependencies
✅ Core data models (Room, WorldState, PlayerState, Entity, Direction, CombatState)
✅ Sample dungeon with 6 interconnected rooms, entities, and rich traits
✅ Immutable state design with helper methods
✅ Java 17 toolchain configuration
✅ Intent sealed class hierarchy with comprehensive tests (including Attack intent)
✅ LLM module with ktor 3.1.0 dependencies - builds successfully
✅ **Console-based game loop - PLAYABLE MVP**
✅ **Text parser converting input to Intent objects**
✅ **Movement, look, inventory, help commands functional**
✅ **LLM-powered room description generation** ✨
✅ **RoomDescriptionGenerator in reasoning module with tests**
✅ **API key configuration via local.properties**
✅ **Item pickup/drop mechanics with Take and Drop intents**
✅ **Commands: take/get/pickup/pick and drop/put**
✅ **NPC dialogue system with Talk intent**
✅ **NPCInteractionGenerator in reasoning module**
✅ **Commands: talk/speak/chat with personality-aware responses**
✅ **Combat system with CombatResolver and CombatNarrator** ⚔️
✅ **Commands: attack/kill/fight/hit to engage and continue combat**
✅ **Turn-based combat with random damage, health tracking, victory/defeat**
✅ **LLM-powered atmospheric combat narratives** ✨
✅ **Equipment system with weapon slots and damage bonuses** ⚔️
✅ **Commands: equip/wield <weapon> to equip items from inventory**
✅ **Consumable items with healing effects** 🧪
✅ **Commands: use/consume/drink/eat <item> to use consumables**
✅ **Sample dungeon updated with weapons (iron sword +5, steel dagger +3) and health potion (+30 HP)**
✅ **Stats system with D&D-style attributes (STR, DEX, CON, INT, WIS, CHA)** 🎲
✅ **SkillCheckResolver with d20 mechanics, difficulty classes, critical success/failure**
✅ **Combat now uses STR modifiers for player and NPC damage**
✅ **Check intent fully implemented (check/test/attempt/try commands)** 🎲
✅ **NPCs have varied stat distributions (Old Guard: wise & hardy, Skeleton King: strong & quick)**
✅ **Comprehensive tests for skill check system**
✅ **Armor system with defense bonuses** 🛡️
✅ **Armor reduces incoming damage in combat**
✅ **Sample dungeon updated with armor (leather armor +2, chainmail +4)**
✅ **Commands: equip/wield/wear <armor> to equip armor from inventory**
✅ **Comprehensive tests for armor system**
✅ **Skill check integration with Feature entities** 🎲
✅ **Interactive challenges: locked chests (DEX), stuck doors (STR), hidden items (WIS), arcane runes (INT)**
✅ **Commands: check/test <feature> to attempt skill checks on interactive features**
✅ **Skill checks show d20 roll, modifier, total vs DC, and critical success/failure**
✅ **Features marked as completed after successful checks**
✅ **Sample dungeon has 4 skill challenges across 3 rooms (corridor, treasury, secret chamber)**
✅ **Persuade and Intimidate intents implemented** 💬
✅ **Commands: persuade/convince <npc> and intimidate/threaten <npc>**
✅ **NPCs can have persuasionChallenge and intimidationChallenge fields**
✅ **Old Guard has persuasion challenge (CHA/DC10) - reveals secrets on success**
✅ **Skeleton King has intimidation challenge (CHA/DC20) - backs down on success**
✅ **Social checks mark NPCs as persuaded/intimidated to prevent re-attempts**
✅ **Memory/RAG system implemented** 💾
✅ **OpenAI embeddings API integration (text-embedding-3-small)**
✅ **InMemoryVectorStore with cosine similarity search**
✅ **MemoryManager for storing and retrieving game events**
✅ **RAG-enhanced room descriptions with historical context**
✅ **RAG-enhanced NPC dialogues with conversation history**
✅ **RAG-enhanced combat narratives showing fight progression**
✅ **Comprehensive tests for memory and vector store**
✅ **Procedural dungeon generation system** 🎲
✅ **4 themed dungeon generators: Crypt, Castle, Cave, Temple**
✅ **RoomGenerator creates rooms with theme-appropriate traits**
✅ **NPCGenerator creates NPCs with varied stats and power levels**
✅ **ItemGenerator creates weapons, armor, consumables, and treasure**
✅ **DungeonLayoutGenerator creates connected room graphs**
✅ **ProceduralDungeonBuilder orchestrates all generators**
✅ **Deterministic generation with optional seed parameter**
✅ **Comprehensive tests for all procedural generation components**
✅ **Interactive dungeon selection menu at game start**
✅ **Persistent storage system with JSON serialization** 💾
✅ **Save and Load intents with commands: save [name] and load [name]**
✅ **PersistenceManager for game state save/load operations**
✅ **VectorStore interface for pluggable memory backends**
✅ **PersistentVectorStore implementation with disk persistence**
✅ **Comprehensive tests for persistence layer (31 tests passing)**
✅ **Save files stored in saves/ directory with human-readable JSON**
✅ **Multi-user architecture foundation** 🌐
✅ **PlayerId type alias for player identification**
✅ **WorldState refactored: players Map instead of single player**
✅ **Per-player combat state (activeCombat moved to PlayerState)**
✅ **Multi-player API methods: getCurrentRoom(playerId), getPlayer(playerId), addPlayer(), removePlayer()**
✅ **Backward compatibility maintained for single-player code**
✅ **All 57 tests passing after multi-user refactoring**

### Current Status: Multi-User Architecture Foundation Complete
✅ All modules building successfully
✅ Game runs with LLM-powered descriptions, NPC dialogue, combat narration, AND RAG memory
✅ Sample dungeon fully navigable with vivid, atmospheric descriptions
✅ Fallback to simple descriptions/narratives if no API key
✅ Item mechanics fully functional - pickup/drop/equip/use all working
✅ NPC interaction working - tested with Old Guard (friendly) and Skeleton King (hostile)
✅ Combat system functional - tested defeating Skeleton King with equipped weapons
✅ Equipment system working - weapons provide damage bonuses in combat
✅ Consumables working - potions restore health (respects max health)
✅ LLM generates personality-driven dialogue and visceral combat descriptions
✅ Skill check system integrated - 4 interactive challenges in dungeon (DEX, STR, WIS, INT checks)
✅ D20 mechanics with stat modifiers, difficulty classes, and critical successes/failures working
✅ Social interaction system - persuasion and intimidation CHA checks for NPCs
✅ Tested persuading Old Guard (DC 10) and intimidating Skeleton King (DC 20)
✅ RAG memory system provides contextual history for all LLM generators
✅ Room descriptions vary based on previous visits
✅ NPC conversations reference past dialogues
✅ Combat narratives build on previous rounds
✅ Procedural generation creates varied dungeons with 4 themes
✅ Can generate dungeons of any size (default 10 rooms)
✅ Dungeons have entrance rooms, boss rooms, and loot distribution
✅ **Persistent storage working** - save/load game state to JSON files
✅ Save files preserve all game state: player stats, inventory, equipped items, combat state, room contents
✅ Load command restores complete game state from disk
✅ **Multi-user architecture foundation** - World state supports multiple concurrent players
✅ Players can be in different rooms simultaneously
✅ Each player has independent combat state
✅ PlayerId system for tracking individual players

### Next Priority
🔄 Multi-user implementation (GameServer, PlayerSession, visibility, broadcasts)
🔄 Dynamic quest generation
🔄 Persistent memory storage (optional - vector embeddings to disk)

## Important Notes

- **Main application**: `com.jcraw.app.AppKt` - fully implemented with LLM and RAG integration
- **Multi-user architecture**:
  - `core/src/main/kotlin/com/jcraw/mud/core/Room.kt` - PlayerId type alias
  - `core/src/main/kotlin/com/jcraw/mud/core/WorldState.kt` - Multi-player world state with players Map
  - `core/src/main/kotlin/com/jcraw/mud/core/PlayerState.kt` - Per-player state including combat
  - Combat state moved from WorldState to PlayerState for independent player battles
  - Backward compatibility: `worldState.player` still works for single-player code
- **LLM Generators** (all RAG-enhanced):
  - `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/RoomDescriptionGenerator.kt`
  - `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/NPCInteractionGenerator.kt`
  - `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/CombatNarrator.kt`
- **Memory/RAG System**:
  - `memory/src/main/kotlin/com/jcraw/mud/memory/MemoryManager.kt` - High-level memory interface
  - `memory/src/main/kotlin/com/jcraw/mud/memory/VectorStoreInterface.kt` - Interface for pluggable vector stores
  - `memory/src/main/kotlin/com/jcraw/mud/memory/VectorStore.kt` - In-memory vector store with cosine similarity
  - `memory/src/main/kotlin/com/jcraw/mud/memory/PersistentVectorStore.kt` - Disk-based vector store with JSON persistence
  - `memory/src/main/kotlin/com/jcraw/mud/memory/MemoryEntry.kt` - Memory data models
  - `llm/src/main/kotlin/com/jcraw/sophia/llm/OpenAIClient.kt` - Embeddings API support
- **Persistence System**:
  - `memory/src/main/kotlin/com/jcraw/mud/memory/PersistenceManager.kt` - Game state save/load manager
  - `memory/src/test/kotlin/com/jcraw/mud/memory/PersistenceManagerTest.kt` - Comprehensive persistence tests
  - `memory/src/test/kotlin/com/jcraw/mud/memory/PersistentVectorStoreTest.kt` - Vector store persistence tests
- **Combat System**:
  - `core/src/main/kotlin/com/jcraw/mud/core/CombatState.kt` - Combat data models
  - `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/CombatResolver.kt` - Combat mechanics
- **Skill Check System**:
  - `core/src/main/kotlin/com/jcraw/mud/core/Entity.kt` - Stats data class
  - `core/src/main/kotlin/com/jcraw/mud/core/SkillCheck.kt` - Skill check models
  - `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/SkillCheckResolver.kt` - D20 resolution logic
- **Armor System**:
  - `core/src/main/kotlin/com/jcraw/mud/core/Entity.kt` - defenseBonus field on Item
  - `core/src/main/kotlin/com/jcraw/mud/core/PlayerState.kt` - equipArmor/unequipArmor/getArmorDefenseBonus
  - `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/CombatResolver.kt` - Damage reduction logic
  - `core/src/test/kotlin/com/jcraw/mud/core/ArmorSystemTest.kt` - Comprehensive tests
- **Procedural Generation System**:
  - `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/procedural/DungeonTheme.kt` - Theme enums with trait pools
  - `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/procedural/RoomGenerator.kt` - Procedural room generation
  - `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/procedural/NPCGenerator.kt` - Procedural NPC generation
  - `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/procedural/ItemGenerator.kt` - Procedural item generation
  - `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/procedural/DungeonLayoutGenerator.kt` - Graph-based layout generation
  - `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/procedural/ProceduralDungeonBuilder.kt` - Main orchestrator
  - `reasoning/src/test/kotlin/com/jcraw/mud/reasoning/procedural/ProceduralGenerationTest.kt` - Comprehensive tests
- All modules integrated into build system and building successfully
- No backward compatibility needed - can wipe and restart data
- Project follows guidelines in `CLAUDE_GUIDELINES.md`
- Requirements are in `docs/requirements.txt`
- Sample dungeon: `core/src/main/kotlin/com/jcraw/mud/core/SampleDungeon.kt`
- Game loop: `app/src/main/kotlin/com/jcraw/app/App.kt`
- Intent system: `perception/src/main/kotlin/com/jcraw/mud/perception/Intent.kt`

## Getting Started (Next Developer)

1. **Set up API key**: Add `openai.api.key=sk-...` to `local.properties` (or set OPENAI_API_KEY env var)
2. **Run the game**: `gradle installDist && app/build/install/app/bin/app`
3. **Choose dungeon type**: At startup, select from:
   - Sample Dungeon (handcrafted, 6 rooms)
   - Procedural Crypt (ancient tombs)
   - Procedural Castle (ruined fortress)
   - Procedural Cave (dark caverns)
   - Procedural Temple (forgotten shrine)
   - Specify room count for procedural dungeons (default: 10)
4. **Commands available**:
   - Movement: `n/s/e/w`, `north/south/east/west`, `go <direction>`
   - Interaction: `look [target]`, `take/get <item>`, `drop/put <item>`, `talk/speak <npc>`, `inventory/i`
   - Combat: `attack/kill/fight/hit <npc>` to start combat, then `attack` to continue
   - Equipment: `equip/wield/wear <item>` to equip weapons or armor
   - Consumables: `use/consume/drink/eat <item>` to use healing potions
   - Skill Checks: `check/test <feature>` to attempt skill checks on interactive features
   - Social: `persuade/convince <npc>` and `intimidate/threaten <npc>` for CHA checks
   - Persistence: `save [name]` to save game (defaults to 'quicksave'), `load [name]` to load game
   - Meta: `help`, `quit`
5. **Dungeon types**:
   - **Sample dungeon**: 6 handcrafted rooms with items (weapons, armor, potions, gold) and NPCs (Old Guard, Skeleton King)
   - **Procedural dungeons**: Dynamically generated with theme-appropriate traits, random NPCs, and distributed loot
6. **LLM features**:
   - Room descriptions dynamically generated using gpt-4o-mini with RAG context
   - NPC dialogue personality-driven (friendly vs hostile, health-aware) with conversation history
   - Combat narratives with visceral, atmospheric descriptions that build on previous rounds
   - Embeddings via text-embedding-3-small for semantic memory retrieval
7. **Item mechanics**: Pick up items, drop them, equip weapons, use consumables
8. **NPC interaction**: Talk to NPCs and get contextual, personality-driven responses that reference past conversations
9. **Combat system**: Turn-based combat with health tracking, weapon damage bonuses, victory/defeat conditions
10. **Equipment system**: Equip weapons to increase damage, equip armor to reduce damage taken
11. **Skill system**: D&D-style stats (STR, DEX, CON, INT, WIS, CHA) with d20 + modifier vs DC
12. **Combat modifiers**: STR affects damage dealt, armor defense reduces damage taken
13. **Armor mechanics**: Chainmail (+4 defense) reduces incoming damage by 4, leather armor (+2) reduces by 2
14. **Skill check challenges**: 4 interactive features in sample dungeon - loose stone (WIS/DC10), locked chest (DEX/DC15), stuck door (STR/DC20), runes (INT/DC15)
15. **Social interactions**: Persuade Old Guard (CHA/DC10) for hints, intimidate Skeleton King (CHA/DC20) to avoid combat
16. **RAG Memory**: All game events stored with embeddings, retrieved contextually for LLM prompts
17. **Procedural generation**: Create dungeons of any size with 4 themes, each with unique traits, NPCs with varied stats, and distributed loot
18. **Persistence**: Save and load game state to/from JSON files - preserves player stats, inventory, equipment, combat state, and world state
19. **Multi-user architecture**: Foundation complete - WorldState supports multiple players with independent states
20. **Next logical step**: Implement GameServer and PlayerSession for actual multi-user gameplay, or add dynamic quest generation

## Multi-User Architecture Details

The foundation for multi-user support is complete. Here's what was built:

### Architecture Changes (Completed)
- **PlayerId type**: `typealias PlayerId = String` for player identification
- **WorldState refactored**: Changed from `player: PlayerState` to `players: Map<PlayerId, PlayerState>`
- **Per-player combat**: `activeCombat` moved from WorldState to PlayerState (each player has independent combat)
- **Multi-player API**:
  - `getCurrentRoom(playerId)` - Get room for specific player
  - `getPlayer(playerId)` - Retrieve player state
  - `addPlayer(playerState)` - Add new player to world
  - `removePlayer(playerId)` - Remove player from world
  - `getPlayersInRoom(roomId)` - Query all players in a room
  - `movePlayer(playerId, direction)` - Move specific player
- **Backward compatibility**: `worldState.player` property still works (returns first player)
- **CombatResolver updated**: Now accepts player parameter for per-player combat resolution

### Remaining Work for Full Multi-User
1. **Entity.Player** - New entity type to represent other players in rooms (for visibility)
2. **GameServer** - Manages shared WorldState and coordinates multiple player sessions
3. **PlayerSession** - Handles individual player I/O and maintains player context
4. **Broadcast system** - Notify players of actions happening in their current room
5. **Network layer** (optional) - For remote connections (could use stdio multiplexing for local multi-user MVP)
6. **Multi-user tests** - Verify concurrent player scenarios work correctly

The feature-complete MVP has LLM-powered descriptions with RAG memory, full item system (pickup/drop/equip/use), NPC dialogue with conversation history, turn-based combat with weapons AND armor, stat-based skill checks (all 6 stats used!), interactive skill challenges, social interaction system with persuasion and intimidation, semantic memory retrieval for contextual narratives, procedural dungeon generation with 4 themes, persistent save/load system, AND multi-user capable architecture!