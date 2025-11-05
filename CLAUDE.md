# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Current State: ✅ PRODUCTION READY** - This is a fully functional AI-powered MUD (Multi-User Dungeon) engine with modular architecture, procedural world generation, dynamic quest system, persistent save/load, multi-user game server, Compose Multiplatform GUI client, and console-based game loop with turn-based combat, equipment system, consumables, D&D-style skill checks, social interactions, and RAG-enhanced memory system.

**Quick Start**:
- Console: `gradle installDist && app/build/install/app/bin/app`
- GUI Client: `gradle :client:run`

For complete documentation, see:
- **[Getting Started Guide](docs/GETTING_STARTED.md)** - Setup, commands, gameplay features
- **[Client UI Guide](docs/CLIENT_UI.md)** - Graphical client documentation
- **[Architecture Documentation](docs/ARCHITECTURE.md)** - Module structure, data flow, component details
- **[Social System Documentation](docs/SOCIAL_SYSTEM.md)** - Social interactions, disposition, knowledge system
- **[World Generation Documentation](docs/WORLD_GENERATION.md)** - Hierarchical world generation system
- **[Testing Strategy](docs/TESTING.md)** - Comprehensive testing guide, test organization
- **[Multi-User Architecture](docs/MULTI_USER.md)** - Multi-player system details

## What's Implemented

### Core Systems ✅
- **10 Gradle modules**: core, perception, reasoning, memory, action, llm, app, testbot, client, utils
- **World model**: Room, WorldState, PlayerState, Entity hierarchy, CombatState, Direction
- **Multi-user architecture**: Multiple concurrent players with thread-safe state management
- **Intent system**: 25+ intent types for player actions
- **Working game loop**: Console-based with text parser and LLM integration
- **ECS Components**: Full component system for game entities and state

### Game Features ✅
- **Combat System V2**: Turn-based with STR modifiers, equipment bonuses, boss mechanics, safe zones
- **Item System V2**: 53 item templates across 10 types, inventory management (weight-based), equipment (12 slots), gathering, crafting (24 recipes), trading, pickpocketing
- **Skill System V2**: Use-based progression with infinite growth, perk system, resource costs (stamina/mana/focus), social integration
- **Social System**: Emotes, persuasion, intimidation, NPC dialogue with disposition tracking and knowledge system
- **Quest System**: Procedurally generated with 6 objective types and automatic progress tracking
- **World Generation V2**: Hierarchical procedural generation with exit resolution, content placement, themed dungeons
- **Starting Dungeon**: Ancient Abyss with 4-region structure, town safe zone, merchants, mob respawn, boss fight, victory condition
- **Persistence**: JSON-based save/load for complete game state

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
- **773 tests passing** across all modules (0 failures, 100% pass rate)
- **Test bot**: Automated LLM-powered testing with 11 scenarios
- **InMemoryGameEngine**: Headless engine for automated testing
- See [Testing Strategy](docs/TESTING.md) for details

## Future Enhancements (Optional)

- **Network layer** - TCP/WebSocket support for remote multi-player
- **Persistent vector storage** - Save/load vector embeddings to disk
- **Additional quest types** - Escort, Defend, Craft objectives
- **Character progression** - Leveling, skill trees
- **More dungeons** - Additional themes and procedural variations
- **GUI persistence** - Save/load for GUI client
- **Multiplayer lobby** - Lobby system for multi-player games

## Commands

See [Getting Started Guide](docs/GETTING_STARTED.md) for complete command reference.

**Quick reference**:
- **Movement**: n/s/e/w, go <direction>, go to <room name>
- **Interaction**: look, take, drop, talk, inventory
- **Combat**: attack <npc>
- **Equipment**: equip <item>, unequip <item>
- **Consumables**: use <item>
- **Looting**: loot <corpse>, loot <item> from <corpse>, loot all from <corpse>
- **Gathering**: interact/harvest/gather <resource>
- **Crafting**: craft <recipe>
- **Trading**: buy <item> [from <merchant>], sell <item> [to <merchant>], list stock
- **Pickpocketing**: pickpocket <npc>, steal <item> from <npc>, place <item> on <npc>
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
- **Console app**: `app/src/main/kotlin/com/jcraw/app/`
  - `App.kt` (145 lines) - Main entry point and initialization
  - `MudGameEngine.kt` (430 lines) - Core game engine and loop with Combat V2 integration
  - `MultiUserGame.kt` (248 lines) - Multi-user server mode
- **Intent handlers**: `app/src/main/kotlin/com/jcraw/app/handlers/` (5 handler files)
  - `MovementHandlers.kt` (171 lines) - Navigation and exploration
  - `ItemHandlers.kt` (413 lines) - Inventory, equipment, corpse looting
  - `CombatHandlers.kt` (136 lines) - Combat system
  - `SocialHandlers.kt` (348 lines) - NPC interactions
  - `SkillQuestHandlers.kt` (522 lines) - Skills, quests, persistence, meta-commands
- **GUI client**: `client/src/main/kotlin/com/jcraw/mud/client/`
  - `EngineGameClient.kt` (311 lines) - Core GameClient implementation
  - `handlers/ClientMovementHandlers.kt` (250 lines) - Navigation and exploration
  - `handlers/ClientItemHandlers.kt` (300 lines) - Inventory and equipment
  - `handlers/ClientCombatHandlers.kt` (140 lines) - Combat system
  - `handlers/ClientSocialHandlers.kt` (230 lines) - Social interactions
  - `handlers/ClientSkillQuestHandlers.kt` (480 lines) - Skills, quests, persistence, meta-commands
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
- **Maintainable file sizes** - All files under 1000 lines for readability

## Getting Started

1. **Set up API key**: Add `openai.api.key=sk-...` to `local.properties` (or set `OPENAI_API_KEY` env var)
   - Game works without API key in fallback mode with simpler descriptions
2. **Run the game**: `gradle installDist && app/build/install/app/bin/app`
3. **Choose game mode**: Single-player or multi-user (local) at startup
4. **Choose dungeon**: Sample (6 rooms) or procedural (4 themes, customizable size)
5. **Play**: See [Getting Started Guide](docs/GETTING_STARTED.md) for full command reference

## Important Notes

- **✅ Production Ready** - All planned features complete, 773 tests passing (100% pass rate)
- **No backward compatibility needed** - Can wipe and restart data between versions
- **API key optional** - Game works without OpenAI API key (fallback mode)
- **Java 17 required** - Uses Java 17 toolchain
- **Project guidelines**: See `CLAUDE_GUIDELINES.md`
- **Development status**: See `docs/TODO.md` for optional future enhancements

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
- **[Items and Crafting](docs/ITEMS_AND_CRAFTING.md)** - Item system, inventory, gathering, crafting, trading, pickpocketing
- **[World Generation](docs/WORLD_GENERATION.md)** - Hierarchical world generation, exits, content placement, state persistence

## Current Status

**✅ PRODUCTION READY - ALL SYSTEMS COMPLETE**
**🚧 V3 IN PROGRESS** - Graph-Based Navigation (Chunk 5 complete: WorldState refactored + MudGame dependencies added, ready for handler implementation)

All V2 systems fully integrated and tested:
- ✅ Combat System V2 (7 phases) - Turn-based combat with equipment, boss mechanics, safe zones
- ✅ Item System V2 (10 chunks) - Full inventory, gathering, crafting, trading, pickpocketing
- ✅ Skill System V2 (11 phases) - Use-based progression, perks, resources, social integration
- ✅ Social System (11 phases) - Disposition, NPC memory, emotes, knowledge system
- ✅ Quest System - Procedural generation with auto-tracking
- ✅ World Generation V2 (7 chunks) - Hierarchical procedural generation with exit resolution
- ✅ Starting Dungeon (8 chunks) - Ancient Abyss with town, merchants, respawn, boss fight
- ✅ GUI Client - Compose Multiplatform with real engine integration
- ✅ Multi-User Architecture - Concurrent players with thread-safe state
- 🚧 World System V3 (Chunks 1-5 WorldState refactoring complete, handler integration pending):
  - ✅ Chunk 1-2: GraphNodeComponent ECS component (155 lines, 29 tests), database schema, GraphNodeRepository (219 lines, 29 unit tests)
  - ✅ Chunk 3: Graph generation algorithms - Grid/BSP/FloodFill layouts, Kruskal MST, 20% extra edges for loops, node type assignment (31 tests GraphGeneratorTest, 25 tests GraphLayoutTest)
  - ✅ Chunk 4: Graph validation - Reachability (BFS), loop detection (DFS), avg degree >= 3.0, 2+ frontiers (212 lines, 20 tests GraphValidatorTest)
  - ✅ Chunk 5 Generation Layer: WorldGenerator.kt (567 lines) with graph generation at SUBZONE, lazy-fill content system
  - ✅ Chunk 5 WorldState V3: Full ECS refactoring - graphNodes/spaces storage, 12 new V3 methods (movePlayerV3, getCurrentSpace, etc.), Room deprecated
  - ✅ Chunk 5 MudGame V3 Dependencies: LoreInheritanceEngine, GraphGenerator, GraphValidator, WorldGenerator added to MudGameEngine.kt (lines 126-142), compiles successfully
  - 🔍 Chunk 5 Movement Handler Integration: **READY** - Detailed migration plan in TODO.md lines 64-91
  - ❌ Handler implementation - Implement V3 in all handlers (movement, combat, items, social) (~11-14h remaining)
  - ❌ Chunks 6-11: Hidden exits, dynamic edges, breakouts, exit resolution, testing, docs
- ✅ 773 tests passing (0 failures, 100% pass rate)
- ✅ Code quality - All files under 1000 lines (largest is 910 lines)

See detailed implementation plans in `docs/requirements/V2/` and [TODO.md](docs/TODO.md) for optional future enhancements.
