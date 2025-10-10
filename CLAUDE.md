# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an AI-powered MUD (Multi-User Dungeon) engine written in Kotlin. The system creates a text-based roleplaying game where players navigate rooms and interact with the world through natural language commands. The LLM generates dynamic, variable descriptions for each room and interaction, making every experience unique while maintaining consistency through fixed room traits.

## Commands

### Build and Run
- `./gradlew run` - Build and run the application (main entry point: `com.jcraw.app.AppKt`)
- `./gradlew build` - Build the application only
- `./gradlew check` - Run all checks including tests
- `./gradlew clean` - Clean all build outputs

### Testing
- `./gradlew test` - Run unit tests
- `./gradlew :app:test` - Run tests for the app module
- `./gradlew :utils:test` - Run tests for the utils module

## Project Structure

This is a multi-module Gradle project with the following structure:

### Modules
- **app** - Main application module containing the game entry point
- **utils** - Shared utilities and common code
- **llm** - LLM client implementation for OpenAI integration (currently not included in build)

### Key Architecture Components
- **Perception** - Input parsing and LLM-based intent recognition
- **Reasoning** - LLM-powered generation and game logic resolution
- **Action** - Output narration and response generation
- **Memory** - Vector database for history and structured world state

### Build Configuration
- Uses Gradle with Kotlin DSL
- Version catalog in `gradle/libs.versions.toml` for dependency management
- Convention plugin in `buildSrc` for shared build logic
- Java 24 toolchain
- Kotlin 2.2.0 with coroutines and serialization support

### Dependencies
The project uses kotlinx ecosystem libraries:
- kotlinx-datetime for time handling
- kotlinx-serialization-json for JSON processing
- kotlinx-coroutines-core for async operations
- Ktor client for HTTP requests (in llm module)

## Game Design

### Core Concepts
- **Rooms**: Data classes with traits, exits, and contents - stored as graph structure
- **Dynamic Descriptions**: LLM generates unique room descriptions each time using fixed traits
- **Natural Language Input**: Players use free-form text commands parsed by LLM
- **RAG Integration**: Vector database maintains history for coherent, varied responses

### World State
- Immutable `WorldState` containing room graph and player state
- Room traits stored as simple strings/lists for flexibility
- Player position tracked through room navigation

### LLM Integration
- OpenAI client with configurable models and parameters
- Specialized prompts for different game functions (room descriptions, combat, skill checks)
- ReAct pattern for complex decision making (Observe → Reason → Act)

## Important Notes

- The `llm` module exists but is not currently included in `settings.gradle.kts`
- Main class is `com.jcraw.app.AppKt` as configured in app module
- Project follows KISS principle - avoid overengineering
- Use sealed classes over enums per project guidelines
- Focus on behavior-driven testing, not line coverage
- All LLM calls should use cost-effective models during development