# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Current State: PLAYABLE MVP** - This is an AI-powered MUD (Multi-User Dungeon) engine with modular architecture, core data models, sample dungeon, and a working console-based game loop. The vision is to create a text-based roleplaying game with dynamic LLM-generated content.

### What Exists Now
- Complete Gradle multi-module setup with 6 modules
- Core world model: Room, WorldState, PlayerState, Entity hierarchy
- Direction enum with bidirectional mapping
- **Sample dungeon with 6 interconnected rooms, entities, and rich trait lists**
- **Intent sealed class hierarchy (Move, Look, Interact, Inventory, Help, Quit)**
- **Working console game loop with text parser**
- **OpenAI LLM client fully integrated with ktor 3.1.0**
- **RoomDescriptionGenerator - LLM-powered vivid room descriptions** ✨
- **API key support via local.properties or OPENAI_API_KEY env var**
- **GAME IS FULLY PLAYABLE** - movement, looking, LLM descriptions work

### What Needs to Be Built Next
Remaining tasks organized by priority:
1. **Item mechanics** - Pickup/drop items, manage inventory
2. **Interaction system** - Use items, talk to NPCs, trigger events
3. Later: Combat, skill checks, memory/RAG, advanced generation

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

### Current Status: Enhanced MVP with LLM
✅ All modules building successfully
✅ Game runs with LLM-powered descriptions
✅ Sample dungeon fully navigable with vivid, atmospheric descriptions
✅ Fallback to simple trait descriptions if no API key

### Next Priority
🔄 Item pickup/drop mechanics
🔄 Enhanced NPC interaction system
🔄 Combat mechanics

## Important Notes

- **Main application**: `com.jcraw.app.AppKt` - fully implemented and working with LLM integration
- **LLM Generator**: `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/RoomDescriptionGenerator.kt`
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
3. **Commands work**: `n` (north), `look`, `help`, `inventory`, `quit`
4. **Sample dungeon**: 6 rooms with items (gold pouch, iron sword) and NPCs (skeleton king)
5. **LLM descriptions**: Room descriptions are dynamically generated using gpt-4o-mini
6. **Next logical step**: Implement item pickup/drop mechanics

The enhanced MVP is complete with LLM-powered descriptions - focus on item mechanics next.