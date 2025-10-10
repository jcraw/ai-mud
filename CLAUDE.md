# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Current State: FEATURE-COMPLETE MVP WITH RAG MEMORY** - This is an AI-powered MUD (Multi-User Dungeon) engine with modular architecture, core data models, sample dungeon, and a working console-based game loop with turn-based combat, full equipment system (weapons & armor), consumables, D&D-style skill checks, AND RAG-enhanced memory system for contextual narratives. The vision is to create a text-based roleplaying game with dynamic LLM-generated content that remembers and builds on player history.

### What Exists Now
- Complete Gradle multi-module setup with 7 modules
- Core world model: Room, WorldState, PlayerState, Entity hierarchy, CombatState, ItemType
- Direction enum with bidirectional mapping
- **Stats system (STR, DEX, CON, INT, WIS, CHA)** - D&D-style stats for player & NPCs ✅
- **Sample dungeon with 6 interconnected rooms, entities, rich trait lists, and stat-based NPCs**
- **Intent sealed class hierarchy (Move, Look, Interact, Take, Drop, Talk, Attack, Equip, Use, Check, Persuade, Intimidate, Inventory, Help, Quit)**
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

### What Needs to Be Built Next
Remaining tasks organized by priority:
1. **Procedural content generation** - Generate rooms, NPCs, quests dynamically
2. **Multi-user support** - Multiple players in shared world
3. **Persistent storage** - Save/load game state and memory to disk
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

### Current Status: Feature-Complete MVP with RAG Memory System
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

### Next Priority
🔄 Procedural content generation
🔄 Multi-user support
🔄 Persistent storage for memory (currently in-memory only)

## Important Notes

- **Main application**: `com.jcraw.app.AppKt` - fully implemented with LLM and RAG integration
- **LLM Generators** (all RAG-enhanced):
  - `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/RoomDescriptionGenerator.kt`
  - `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/NPCInteractionGenerator.kt`
  - `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/CombatNarrator.kt`
- **Memory/RAG System**:
  - `memory/src/main/kotlin/com/jcraw/mud/memory/MemoryManager.kt` - High-level memory interface
  - `memory/src/main/kotlin/com/jcraw/mud/memory/VectorStore.kt` - In-memory vector store with cosine similarity
  - `memory/src/main/kotlin/com/jcraw/mud/memory/MemoryEntry.kt` - Memory data models
  - `llm/src/main/kotlin/com/jcraw/sophia/llm/OpenAIClient.kt` - Embeddings API support
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
- All modules integrated into build system and building successfully
- No backward compatibility needed - can wipe and restart data
- Project follows guidelines in `CLAUDE_GUIDELINES.md`
- Requirements are in `docs/requirements.txt`
- Sample dungeon: `core/src/main/kotlin/com/jcraw/mud/core/SampleDungeon.kt`
- Game loop: `app/src/main/kotlin/com/jcraw/app/App.kt`
- Intent system: `perception/src/main/kotlin/com/jcraw/mud/perception/Intent.kt`

## Getting Started (Next Developer)

1. **Set up API key**: Add `openai.api.key=sk-...` to `local.properties` (or set OPENAI_API_KEY env var)
2. **Run the game**: `gradle installDist && app/build/install/app/bin/app` or `./test_combat.sh`
3. **Commands available**:
   - Movement: `n/s/e/w`, `north/south/east/west`, `go <direction>`
   - Interaction: `look [target]`, `take/get <item>`, `drop/put <item>`, `talk/speak <npc>`, `inventory/i`
   - Combat: `attack/kill/fight/hit <npc>` to start combat, then `attack` to continue
   - Equipment: `equip/wield/wear <item>` to equip weapons or armor
   - Consumables: `use/consume/drink/eat <item>` to use healing potions
   - Skill Checks: `check/test <feature>` to attempt skill checks on interactive features
   - Social: `persuade/convince <npc>` and `intimidate/threaten <npc>` for CHA checks
   - Meta: `help`, `quit`
4. **Sample dungeon**: 6 rooms with items (weapons, armor, potions, gold) and NPCs (Old Guard, Skeleton King)
5. **LLM features**:
   - Room descriptions dynamically generated using gpt-4o-mini with RAG context
   - NPC dialogue personality-driven (friendly vs hostile, health-aware) with conversation history
   - Combat narratives with visceral, atmospheric descriptions that build on previous rounds
   - Embeddings via text-embedding-3-small for semantic memory retrieval
6. **Item mechanics**: Pick up items, drop them, equip weapons, use consumables
7. **NPC interaction**: Talk to NPCs and get contextual, personality-driven responses that reference past conversations
8. **Combat system**: Turn-based combat with health tracking, weapon damage bonuses, victory/defeat conditions
9. **Equipment system**: Equip weapons to increase damage, equip armor to reduce damage taken
10. **Skill system**: D&D-style stats (STR, DEX, CON, INT, WIS, CHA) with d20 + modifier vs DC
11. **Combat modifiers**: STR affects damage dealt, armor defense reduces damage taken
12. **Armor mechanics**: Chainmail (+4 defense) reduces incoming damage by 4, leather armor (+2) reduces by 2
13. **Skill check challenges**: 4 interactive features - loose stone (WIS/DC10), locked chest (DEX/DC15), stuck door (STR/DC20), runes (INT/DC15)
14. **Social interactions**: Persuade Old Guard (CHA/DC10) for hints, intimidate Skeleton King (CHA/DC20) to avoid combat
15. **RAG Memory**: All game events stored with embeddings, retrieved contextually for LLM prompts
16. **Next logical step**: Procedural content generation or persistent memory storage

The feature-complete MVP has LLM-powered descriptions with RAG memory, full item system (pickup/drop/equip/use), NPC dialogue with conversation history, turn-based combat with weapons AND armor, stat-based skill checks (all 6 stats used!), interactive skill challenges, social interaction system with persuasion and intimidation, AND semantic memory retrieval for contextual narratives.