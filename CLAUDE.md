# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Current State: Foundation Complete** - This is an AI-powered MUD (Multi-User Dungeon) engine with modular architecture and core data models implemented. The vision is to create a text-based roleplaying game with dynamic LLM-generated content.

### What Exists Now
- Complete Gradle multi-module setup with 6 modules
- Core world model: Room, WorldState, PlayerState, Entity hierarchy
- Direction enum with bidirectional mapping
- OpenAI LLM client integrated into build system
- Requirements documentation and comprehensive TODO list

### What Needs to Be Built
42 remaining tasks organized into these areas:
- LLM integration and prompt engineering
- Natural language input parsing with intent recognition
- Dynamic content generation with RAG
- Game mechanics (exploration, skills, combat)
- Console-based user interface
- Memory systems and state persistence
- Testing infrastructure and error handling

## Commands

### Build and Development
- `gradle build` - Build the project (requires Java 24 toolchain)
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
- Java 24 toolchain, Kotlin 2.2.0
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

### Completed (Day 1/14)
✅ Module structure and dependencies
✅ Core data models (Room, WorldState, PlayerState, Entity, Direction)
✅ LLM client integration
✅ Immutable state design with helper methods

### Next Priority
🔄 Sample dungeon creation with room traits
🔄 Intent sealed classes for player actions
🔄 LLM service interfaces and prompt templates

## Important Notes

- Main class configured as `com.jcraw.app.AppKt` but not implemented yet
- All modules integrated into build system
- Core foundation complete - ready for game logic implementation
- No backward compatibility needed - can wipe and restart data
- Project follows guidelines in `CLAUDE_GUIDELINES.md`
- Requirements are in `docs/requirements.txt`
- 42 remaining tasks tracked in TODO system