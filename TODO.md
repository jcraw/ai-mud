# TODO - AI MUD Development

## Immediate Next Steps (Priority Order)

### 1. Intent System (perception module)
- [ ] Create sealed class hierarchy for player actions
  - Move(direction: Direction)
  - Look(target: String? = null)
  - Interact(target: String)
  - Inventory
  - Help
  - Quit
- [ ] Add Intent.kt to perception module

### 2. Fix LLM Dependencies
- [ ] Check ktor dependencies in gradle/libs.versions.toml
- [ ] Fix unresolved imports in OpenAIClient.kt
- [ ] Ensure llm module builds successfully

### 3. Basic Game Loop Skeleton
- [ ] Create main game console interface in app module
- [ ] Implement basic input/output loop
- [ ] Connect to SampleDungeon for testing

### 4. Simple Perception
- [ ] Basic input parser (start with simple string matching)
- [ ] Convert text input to Intent objects
- [ ] Handle basic commands like "go north", "look", "inventory"

## Later Implementation (MVP Features)

### 5. LLM Integration
- [ ] Room description generation using traits
- [ ] Prompt templates for different scenarios
- [ ] Response generation and narration

### 6. Game Mechanics
- [ ] Movement between rooms
- [ ] Item pickup/drop
- [ ] Basic interaction system

### 7. Enhanced Features
- [ ] Combat system
- [ ] Skill checks
- [ ] Memory/RAG integration
- [ ] More complex parsing

## Current Status

✅ **COMPLETED**
- Multi-module Gradle setup
- Core data models (Room, WorldState, PlayerState, Entity)
- Sample dungeon with 6 interconnected rooms
- Java 17 toolchain configuration

⚠️ **BLOCKED**
- LLM module has dependency issues
- No main game loop yet

🎯 **READY TO START**
- Intent system implementation
- Basic game console interface

## Notes

- Core module builds successfully: `gradle :core:build`
- Sample dungeon ready for testing: `SampleDungeon.createInitialWorldState()`
- Follow KISS principle - start simple, add complexity gradually
- Focus on getting a basic playable demo first