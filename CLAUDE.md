# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Current State: GUI CLIENT WITH REAL ENGINE INTEGRATION COMPLETE** - This is an AI-powered MUD (Multi-User Dungeon) engine with modular architecture, procedural dungeon generation, dynamic quest system, persistent save/load system, fully functional multi-user game server, fully integrated Compose Multiplatform GUI client, and working console-based game loop with turn-based combat, full equipment system (weapons & armor), consumables, D&D-style skill checks, and RAG-enhanced memory system for contextual narratives.

**Quick Start**:
- Console: `gradle installDist && app/build/install/app/bin/app`
- GUI Client: `gradle :client:run`

For complete documentation, see:
- **[Getting Started Guide](docs/GETTING_STARTED.md)** - Setup, commands, gameplay features
- **[Client UI Guide](docs/CLIENT_UI.md)** - Graphical client documentation
- **[Architecture Documentation](docs/ARCHITECTURE.md)** - Module structure, data flow, component details
- **[Social System Documentation](docs/SOCIAL_SYSTEM.md)** - Social interactions, disposition, knowledge system
- **[Testing Strategy](docs/TESTING.md)** - Comprehensive testing guide, test organization
- **[Multi-User Architecture](docs/MULTI_USER.md)** - Multi-player system details

## What's Implemented

### Core Systems ✅
- **10 Gradle modules**: core, perception, reasoning, memory, action, llm, app, testbot, client, utils
- **World model**: Room, WorldState, PlayerState, Entity hierarchy, CombatState, Direction
- **Multi-user architecture**: Multiple concurrent players with thread-safe state management
- **Intent system**: 24+ intent types for player actions
- **Working game loop**: Console-based with text parser and LLM integration

### Game Features ✅
- **Combat system**: Turn-based with STR modifiers, weapon bonuses, armor defense
- **Death & respawn**: "Press any key to play again" prompt with full game restart
- **Equipment system**: Weapons (+damage) and armor (+defense)
- **Consumables**: Healing potions and other usable items
- **Skill checks**: D&D-style (d20 + modifier vs DC) with all 6 stats
- **Social interactions**: Emotes (7 types), persuasion, intimidation, NPC questions, disposition tracking
- **Quest system**: Procedurally generated with 6 objective types and automatic progress tracking
- **Persistence**: JSON-based save/load for game state
- **Procedural generation**: 4 themed dungeons (Crypt, Castle, Cave, Temple)
- **Skill System V2**: ✅ **Phases 1-11 COMPLETE** - Use-based progression, infinite growth, perks, resources, social integration

### AI/LLM Features ✅
- **RAG memory system**: Vector embeddings with semantic search
- **Dynamic descriptions**: Room descriptions that build on history
- **NPC dialogue**: Personality-driven with conversation memory and disposition awareness
- **Combat narration**: Equipment-aware, concise combat descriptions

### Multi-User Features ✅
- **GameServer**: Thread-safe shared world state
- **PlayerSession**: Individual player I/O and event handling
- **Event broadcasting**: Players see actions in their room
- **Mode selection**: Single-player or multi-user at startup

### UI Client ✅
- **Compose Multiplatform**: Desktop GUI with fantasy theme
- **Real engine integration**: EngineGameClient wraps complete MudGame engine
- **Character selection**: 5 pre-made templates (Warrior, Rogue, Mage, Cleric, Bard)
- **Full gameplay**: All game systems work through GUI
- **Unidirectional flow**: Immutable UiState with StateFlow/ViewModel pattern

### Testing ✅
- **~640 tests** across all modules, 100% pass rate
- **Test bot**: Automated LLM-powered testing with 11 scenarios
- **InMemoryGameEngine**: Headless engine for automated testing
- See [Testing Strategy](docs/TESTING.md) for details

## What's Next

Priority tasks:
1. **Code refactoring** - Refactor 3 large files (1961, 1507, 1146 lines) into smaller modules (<350 lines each)
   - See **[Large Files Refactoring Plan](docs/refactoring/LARGE_FILES_REFACTORING_PLAN.md)** for detailed execution plan
   - Phase 1: OutputValidator.kt → 4 files ✅ **COMPLETE**
   - Phase 2: App.kt → 8 files ⏳ **IN PROGRESS** (5/8 files extracted: Movement, Item, Combat, Social handlers complete)
   - Phase 3: EngineGameClient.kt → 6 files
2. **Network layer** (optional) - TCP/WebSocket support for remote multi-player
3. **Persistent memory storage** (optional) - Save/load vector embeddings to disk
4. **Polish & Enhancement Ideas**:
   - More quest objective types (Escort, Defend, Craft, etc.)
   - Character progression system (leveling, skill trees)
   - More dungeon themes and procedural variations
   - Save/load for GUI client
   - Multiplayer lobby system

## Commands

See [Getting Started Guide](docs/GETTING_STARTED.md) for complete command reference.

**Quick reference**:
- **Movement**: n/s/e/w, go <direction>, go to <room name>
- **Interaction**: look, take, drop, talk, inventory
- **Combat**: attack <npc>
- **Equipment**: equip <item>
- **Consumables**: use <item>
- **Skill checks**: check <feature>, persuade <npc>, intimidate <npc>
- **Social**: smile/wave/nod/bow [at <npc>], ask <npc> about <topic>
- **Skills**: skills, use <skill>, train <skill> with <npc>, choose perk <1-2> for <skill>
- **Quests**: quests, accept <id>, claim <id>
- **Persistence**: save [name], load [name]
- **Meta**: help, quit

## Project Structure

### Modules
- **core** - World model (Room, WorldState, PlayerState, Entity, Direction, Quest, GameClient, GameEvent)
- **perception** - Input parsing and intent recognition
- **reasoning** - LLM-powered content generation and game logic
- **memory** - Vector database integration and state persistence
- **action** - Output formatting and narration
- **llm** - OpenAI client and LLM interfaces
- **app** - Main game application and console interface
- **client** - Compose Multiplatform GUI client
- **testbot** - Automated testing system with LLM-powered validation
- **utils** - Shared utilities

### Key Files
- **Console app**: `app/src/main/kotlin/com/jcraw/app/App.kt`
- **Intent handlers**: `app/src/main/kotlin/com/jcraw/app/handlers/` (MovementHandlers, ItemHandlers, CombatHandlers, SocialHandlers)
- **GUI client**: `client/src/main/kotlin/com/jcraw/mud/client/Main.kt`
- **Game server**: `app/src/main/kotlin/com/jcraw/app/GameServer.kt`
- **Sample dungeon**: `core/src/main/kotlin/com/jcraw/mud/core/SampleDungeon.kt`

See [Architecture Documentation](docs/ARCHITECTURE.md) for complete module details and file locations.

## Architecture

### Data Flow
```
User Input
    ↓
Perception (text → Intent)
    ↓
Reasoning (Intent + WorldState → Response + NewState)
    ↓
Action (format output)
    ↓
Memory (store for RAG)
```

### Key Principles
- **KISS principle** - Avoid overengineering
- **Sealed classes over enums** - Better type safety
- **Immutable state** - All state transitions return new copies
- **Thread-safe mutations** - Kotlin coroutines with Mutex
- **Behavior-driven testing** - Focus on contracts
- **Files under 300-500 lines** - Maintain readability

## Getting Started

1. **Set up API key**: Add `openai.api.key=sk-...` to `local.properties` (or set `OPENAI_API_KEY` env var)
   - Game works without API key in fallback mode with simpler descriptions
2. **Run the game**: `gradle installDist && app/build/install/app/bin/app`
3. **Choose game mode**: Single-player or multi-user (local) at startup
4. **Choose dungeon**: Sample (6 rooms) or procedural (4 themes, customizable size)
5. **Play**: See [Getting Started Guide](docs/GETTING_STARTED.md) for full command reference

## Important Notes

- **No backward compatibility needed** - Can wipe and restart data between versions
- **API key optional** - Game works without OpenAI API key (fallback mode)
- **Java 17 required** - Uses Java 17 toolchain
- **All modules building** - ~640 tests passing across all modules
- **Project guidelines**: See `CLAUDE_GUIDELINES.md`
- **Requirements**: See `docs/requirements.txt`

## Documentation

### User Guides
- **[Getting Started](docs/GETTING_STARTED.md)** - Setup, commands, gameplay walkthrough
- **[Client UI](docs/CLIENT_UI.md)** - GUI client documentation and usage

### Technical Documentation
- **[Architecture](docs/ARCHITECTURE.md)** - Module structure, data flow, file locations
- **[Testing Strategy](docs/TESTING.md)** - Comprehensive testing guide, test organization
- **[Multi-User](docs/MULTI_USER.md)** - Multi-player architecture details

### System Documentation
- **[Social System](docs/SOCIAL_SYSTEM.md)** - Social interactions, disposition, knowledge system
- **[Skill System Implementation Plan](docs/requirements/V2/SKILL_SYSTEM_IMPLEMENTATION_PLAN.md)** - Complete architecture and roadmap for V2 skill system

### Refactoring Plans
- **[Large Files Refactoring Plan](docs/refactoring/LARGE_FILES_REFACTORING_PLAN.md)** - Detailed plan to split oversized files into maintainable modules

## Current Status

**All core systems complete!** 🎉

- ✅ GUI client with real engine integration
- ✅ Quest system with auto-tracking
- ✅ Social system (11 phases complete)
- ✅ Skill system V2 (11 phases complete)
- ✅ All testing migration & cleanup complete
- ✅ All known bugs resolved

**Latest updates** can be found in the git commit history and individual documentation files.

See [Implementation Log (archived)](docs/archive/IMPLEMENTATION_LOG.md) for complete feature history.
