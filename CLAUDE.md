# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Current State: PLAYABLE MVP** - This is an AI-powered MUD (Multi-User Dungeon) engine with modular architecture, core data models, sample dungeon, and a working console-based game loop. The vision is to create a text-based roleplaying game with dynamic LLM-generated content.

### What Exists Now
- Complete Gradle multi-module setup with 6 modules
- Core world model: Room, WorldState, PlayerState, Entity hierarchy
- Direction enum with bidirectional mapping
- **Sample dungeon with 6 interconnected rooms, entities, and rich trait lists**
- **Intent sealed class hierarchy (Move, Look, Interact, Take, Drop, Talk, Inventory, Help, Quit)**
- **Working console game loop with text parser**
- **OpenAI LLM client fully integrated with ktor 3.1.0**
- **RoomDescriptionGenerator - LLM-powered vivid room descriptions** ✨
- **NPCInteractionGenerator - LLM-powered dynamic NPC dialogue** ✨
- **API key support via local.properties or OPENAI_API_KEY env var**
- **GAME IS FULLY PLAYABLE** - movement, looking, LLM descriptions work
- **Item mechanics** - pickup/drop items with take/get/drop/put commands ✅
- **NPC interaction** - talk to NPCs with personality-driven dialogue ✅

### What Needs to Be Built Next
Remaining tasks organized by priority:
1. **Combat mechanics** - Basic turn-based combat with LLM-narrated attacks/defenses
2. **Item usage system** - Use items, equip weapons, consume potions
3. Later: Skill checks, memory/RAG, procedural generation, multi-user support

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
✅ Core data models (Room, WorldState, PlayerState, Entity, Direction)
✅ Sample dungeon with 6 interconnected rooms, entities, and rich traits
✅ Immutable state design with helper methods
✅ Java 17 toolchain configuration
✅ Intent sealed class hierarchy with comprehensive tests
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

### Current Status: Enhanced MVP with LLM, Items, and NPC Dialogue
✅ All modules building successfully
✅ Game runs with LLM-powered descriptions and NPC dialogue
✅ Sample dungeon fully navigable with vivid, atmospheric descriptions
✅ Fallback to simple descriptions if no API key
✅ Item mechanics fully functional - tested with gold pouch and iron sword
✅ NPC interaction working - tested with Old Guard (friendly) and Skeleton King (hostile)
✅ LLM generates personality-driven dialogue based on NPC disposition and health

### Next Priority
🔄 Combat mechanics (turn-based, LLM-narrated attacks)
🔄 Item usage system (use items, equip weapons)
🔄 Skill checks and stat-based interactions

## Important Notes

- **Main application**: `com.jcraw.app.AppKt` - fully implemented with LLM integration
- **LLM Generators**:
  - `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/RoomDescriptionGenerator.kt`
  - `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/NPCInteractionGenerator.kt`
- All modules integrated into build system and building successfully
- No backward compatibility needed - can wipe and restart data
- Project follows guidelines in `CLAUDE_GUIDELINES.md`
- Requirements are in `docs/requirements.txt`
- Sample dungeon: `core/src/main/kotlin/com/jcraw/mud/core/SampleDungeon.kt`
- Game loop: `app/src/main/kotlin/com/jcraw/app/App.kt`
- Intent system: `perception/src/main/kotlin/com/jcraw/mud/perception/Intent.kt`

## Getting Started (Next Developer)

1. **Set up API key**: Add `openai.api.key=sk-...` to `local.properties` (or set OPENAI_API_KEY env var)
2. **Run the game**: `gradle installDist && app/build/install/app/bin/app` or `./test_game.sh`
3. **Commands work**:
   - Movement: `n/s/e/w`, `north/south/east/west`, `go <direction>`
   - Interaction: `look [target]`, `take/get <item>`, `drop/put <item>`, `talk/speak <npc>`, `inventory/i`
   - Meta: `help`, `quit`
4. **Sample dungeon**: 6 rooms with items (gold pouch, iron sword) and NPCs (Old Guard, Skeleton King)
5. **LLM features**:
   - Room descriptions dynamically generated using gpt-4o-mini
   - NPC dialogue personality-driven (friendly vs hostile, health-aware)
6. **Item mechanics**: Pick up items, drop them, manage inventory
7. **NPC interaction**: Talk to NPCs and get contextual, personality-driven responses
8. **Next logical step**: Implement combat mechanics

The enhanced MVP is complete with LLM-powered descriptions, item mechanics, AND NPC dialogue - focus on combat next.