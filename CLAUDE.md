# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Current State: Core Foundation + Sample Dungeon Complete** - This is an AI-powered MUD (Multi-User Dungeon) engine with modular architecture, core data models, and sample dungeon implemented. The vision is to create a text-based roleplaying game with dynamic LLM-generated content.

### What Exists Now
- Complete Gradle multi-module setup with 6 modules
- Core world model: Room, WorldState, PlayerState, Entity hierarchy
- Direction enum with bidirectional mapping
- **Sample dungeon with 6 interconnected rooms and rich trait lists**
- **SampleDungeon.kt with initial world state factory**
- OpenAI LLM client integrated into build system (needs dependency fixes)
- Requirements documentation

### What Needs to Be Built Next
Remaining tasks organized by priority:
1. **Intent system** - Sealed classes for player actions (Move, Look, Interact, etc.)
2. **LLM service fixes** - Fix dependency issues in llm module
3. **Basic perception** - Input parsing and intent recognition
4. **Game loop skeleton** - Console interface and main game logic
5. Later: Dynamic content generation, memory systems, advanced features

## Commands

### Build and Development
- `gradle build` - Build the project (requires Java 17 toolchain)
- `gradle check` - Run all checks including tests
- `gradle clean` - Clean all build outputs
- `gradle run` - Will run main game when implemented

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
✅ Sample dungeon with 6 interconnected rooms and rich traits
✅ Immutable state design with helper methods
✅ Java 17 toolchain configuration

### Current Status: LLM Dependencies Need Fixing
⚠️ LLM module has unresolved dependencies (ktor client)
⚠️ Core module builds successfully

### Next Priority
🔄 Intent sealed classes for player actions
🔄 Fix LLM service dependencies
🔄 Basic perception module implementation

## Important Notes

- Main class configured as `com.jcraw.app.AppKt` but not implemented yet
- All modules integrated into build system
- Core foundation complete - ready for game logic implementation
- No backward compatibility needed - can wipe and restart data
- Project follows guidelines in `CLAUDE_GUIDELINES.md`
- Requirements are in `docs/requirements.txt`
- Sample dungeon available in `core/src/main/kotlin/com/jcraw/mud/core/SampleDungeon.kt`

## Getting Started (Next Developer)

1. **Current working state**: Core module builds (`gradle :core:build`)
2. **Sample dungeon ready**: Use `SampleDungeon.createInitialWorldState()` for testing
3. **Next logical step**: Create Intent sealed classes in perception module
4. **Known issue**: LLM module dependencies need fixing (ktor client imports)

The foundation is solid - we have data models and sample content. Focus on the intent system next.