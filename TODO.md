# TODO - AI MUD Development

## Immediate Next Steps (Priority Order)

### 1. Intent System (perception module)
- [x] Create sealed class hierarchy for player actions
  - Move(direction: Direction)
  - Look(target: String? = null)
  - Interact(target: String)
  - Inventory
  - Help
  - Quit
  - Invalid(message: String)
- [x] Add Intent.kt to perception module
- [x] Add comprehensive tests for Intent hierarchy

### 2. Fix LLM Dependencies
- [x] Check ktor dependencies in gradle/libs.versions.toml
- [x] Fix unresolved imports in OpenAIClient.kt
- [x] Ensure llm module builds successfully

### 3. Basic Game Loop Skeleton
- [x] Create main game console interface in app module
- [x] Implement basic input/output loop
- [x] Connect to SampleDungeon for testing
- [x] Build and verify game runs successfully

### 4. Simple Perception
- [x] Basic input parser (simple string matching)
- [x] Convert text input to Intent objects
- [x] Handle basic commands like "go north", "look", "inventory"

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
- Sample dungeon with 6 interconnected rooms and entities
- Java 17 toolchain configuration
- Intent sealed class hierarchy with tests (perception module)
- LLM module ktor dependencies fixed and building
- Perception module building with all tests passing
- **Basic playable game loop with console interface**
- **Input parser converting text to Intent objects**
- **Movement, look, inventory, and help commands working**

⚠️ **BLOCKED**
- None currently

🎯 **READY TO START**
- LLM-powered room description generation (next priority)
- Item pickup/drop mechanics
- Interaction system

## Notes

- **Game is now playable!** Run with: `gradle installDist && app/build/install/app/bin/app`
- Or build and run: `gradle :app:build && app/build/install/app/bin/app`
- Sample dungeon ready: `SampleDungeon.createInitialWorldState()`
- Follow KISS principle - start simple, add complexity gradually
- Next step: Integrate LLM for dynamic room descriptions