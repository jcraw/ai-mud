# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Current State: Early Development** - This is intended to be an AI-powered MUD (Multi-User Dungeon) engine, but is currently just a skeleton project with basic LLM integration. The vision is to create a text-based roleplaying game with dynamic LLM-generated content.

### What Exists Now
- Basic Gradle multi-module setup
- OpenAI LLM client (copied from another project, not yet integrated)
- Requirements documentation
- Empty app and utils modules

### What Needs to Be Built
See `docs/requirements.txt` for the full vision. Key components to implement:
- Room graph and world state management
- Natural language input parsing
- Dynamic content generation
- Game mechanics (exploration, skills, combat)
- Console-based user interface

## Commands

### Build and Development
- `./gradlew build` - Build the project
- `./gradlew check` - Run all checks including tests
- `./gradlew clean` - Clean all build outputs
- `./gradlew run` - Configured but no main logic exists yet

### Testing
- `./gradlew test` - Run unit tests (none exist yet)
- `./gradlew :app:test` - Run tests for app module
- `./gradlew :utils:test` - Run tests for utils module

## Project Structure

Multi-module Gradle project:

### Current Modules
- **app** - Empty, will contain main game application
- **utils** - Empty, will contain shared utilities
- **llm** - Contains OpenAI client (not included in `settings.gradle.kts`)

### Build Configuration
- Uses Gradle with Kotlin DSL
- Version catalog in `gradle/libs.versions.toml`
- Convention plugin in `buildSrc` for shared build logic
- Java 24 toolchain, Kotlin 2.2.0
- kotlinx ecosystem dependencies configured

## Implementation Notes

### Architecture Plan (from requirements)
The eventual architecture should have:
- **Perception** - Input parsing and LLM-based intent recognition
- **Reasoning** - LLM-powered generation and game logic resolution
- **Action** - Output narration and response generation
- **Memory** - Vector database for history and structured world state

### Key Principles
- KISS principle - avoid overengineering
- Use sealed classes over enums
- Focus on behavior-driven testing
- Files under 300-500 lines
- Use GPT4_1Nano for cost savings during development

## Important Notes

- Main class configured as `com.jcraw.app.AppKt` but doesn't exist yet
- `llm` module has working OpenAI client but isn't included in build
- No backward compatibility needed - can wipe and restart data
- Project follows guidelines in `CLAUDE_GUIDELINES.md`
- Requirements are in `docs/requirements.txt`