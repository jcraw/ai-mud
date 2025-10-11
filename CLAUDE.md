# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Current State: GUI CLIENT WITH REAL ENGINE INTEGRATION COMPLETE** - This is an AI-powered MUD (Multi-User Dungeon) engine with modular architecture, procedural dungeon generation, **dynamic quest system**, persistent save/load system, fully functional multi-user game server, **fully integrated Compose Multiplatform GUI client**, and working console-based game loop with turn-based combat, full equipment system (weapons & armor), consumables, D&D-style skill checks, and RAG-enhanced memory system for contextual narratives.

**Quick Start**:
- Console: `gradle installDist && app/build/install/app/bin/app`
- GUI Client: `gradle :client:run`

For complete documentation, see:
- **[Getting Started Guide](docs/GETTING_STARTED.md)** - Setup, commands, gameplay features
- **[Client UI Guide](docs/CLIENT_UI.md)** - Graphical client documentation
- **[Architecture Documentation](docs/ARCHITECTURE.md)** - Module structure, data flow, component details
- **[Implementation Log](docs/IMPLEMENTATION_LOG.md)** - Chronological list of completed features
- **[Multi-User Architecture](docs/MULTI_USER.md)** - Multi-player system details

## What's Implemented

### Core Systems ✅
- **9 Gradle modules**: core, perception, reasoning, memory, action, llm, app, testbot, client, utils
- **World model**: Room, WorldState, PlayerState, Entity hierarchy, CombatState, Direction
- **Multi-user architecture**: Multiple concurrent players with thread-safe state management
- **Intent system**: 18+ intent types for player actions
- **Working game loop**: Console-based with text parser and LLM integration

### Game Features ✅
- **Combat system**: Turn-based with STR modifiers, weapon bonuses, armor defense
- **Equipment system**: Weapons (+damage) and armor (+defense)
- **Consumables**: Healing potions and other usable items
- **Skill checks**: D&D-style (d20 + modifier vs DC) with all 6 stats
- **Social interactions**: Persuasion and intimidation CHA checks
- **Quest system**: Procedurally generated with 6 objective types (Kill, Collect, Explore, Talk, UseSkill, Deliver)
- **Persistence**: JSON-based save/load for game state
- **Procedural generation**: 4 themed dungeons (Crypt, Castle, Cave, Temple)

### AI/LLM Features ✅
- **RAG memory system**: Vector embeddings with semantic search
- **Dynamic descriptions**: Room descriptions that build on history
- **NPC dialogue**: Personality-driven with conversation memory
- **Combat narration**: Atmospheric, contextual combat descriptions

### Multi-User Features ✅
- **GameServer**: Thread-safe shared world state
- **PlayerSession**: Individual player I/O and event handling
- **Event broadcasting**: Players see actions in their room
- **Mode selection**: Single-player or multi-user at startup

### UI Client ✅
- **Compose Multiplatform**: Desktop GUI with fantasy theme
- **Real engine integration**: EngineGameClient wraps complete MudGame engine
- **Character selection**: 5 pre-made templates (Warrior, Rogue, Mage, Cleric, Bard)
- **Full gameplay**: All game systems work through GUI (combat, quests, equipment, etc.)
- **Game log**: Scrollable, color-coded message history
- **Command input**: Text field with history navigation (up/down arrows)
- **Status bar**: HP, gold, XP, theme toggle, copy log
- **Unidirectional flow**: Immutable UiState with StateFlow/ViewModel pattern

### Testing ✅
- **Test bot**: Automated LLM-powered testing with 8 scenarios (exploration, combat, skill checks, item interaction, social interaction, quest testing, exploratory, full playthrough)
- **Comprehensive tests**: 67+ tests across all modules (including 7 UI tests)
- **InMemoryGameEngine**: Headless engine for automated testing

## What's Next

Priority tasks:
1. **Quest objective auto-tracking** - Automatically update quest progress when player actions occur
2. **Network layer** (optional) - TCP/WebSocket support for remote multi-player
3. **Persistent memory storage** (optional) - Save/load vector embeddings to disk

## Commands

### Build and Development
- `gradle build` - Build the project (requires Java 17 toolchain)
- `gradle test` - Run all tests
- `gradle installDist && app/build/install/app/bin/app` - **Run console game**
- `gradle :client:run` - **Run GUI client**

### Testing
- `gradle test` - Run unit tests across all modules
- `gradle :core:test` - Run tests for specific module
- `gradle :client:test` - Run UI client tests
- `gradle :testbot:run` - Run automated test bot (requires OpenAI API key)
- `./test_quests.sh` - Run quest testing scenario specifically

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
- **GUI client**: `client/src/main/kotlin/com/jcraw/mud/client/Main.kt`
- **Game server**: `app/src/main/kotlin/com/jcraw/app/GameServer.kt`
- **Client architecture**:
  - `core/src/main/kotlin/com/jcraw/mud/core/GameClient.kt` - Client interface
  - `core/src/main/kotlin/com/jcraw/mud/core/GameEvent.kt` - Event types
  - `client/src/main/kotlin/com/jcraw/mud/client/GameViewModel.kt` - State management
  - `client/src/main/kotlin/com/jcraw/mud/client/ui/` - UI screens
- **Quest system**:
  - `core/src/main/kotlin/com/jcraw/mud/core/Quest.kt`
  - `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/procedural/QuestGenerator.kt`
  - `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/QuestTracker.kt`
- **Procedural generation**: `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/procedural/`
- **Sample dungeon**: `core/src/main/kotlin/com/jcraw/mud/core/SampleDungeon.kt`

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

### Available Commands
- **Movement**: `n/s/e/w`, `north/south/east/west`, `go <direction>`
- **Interaction**: `look [target]`, `take/get <item>`, `drop <item>`, `talk <npc>`, `inventory/i`
- **Combat**: `attack/kill/fight/hit <npc>` to start/continue combat
- **Equipment**: `equip/wield/wear <item>` to equip weapons or armor
- **Consumables**: `use/consume/drink/eat <item>` to use healing potions
- **Skill checks**: `check/test <feature>` to attempt skill checks
- **Social**: `persuade/convince <npc>` and `intimidate/threaten <npc>` for CHA checks
- **Quests**: `quests/journal/j` to view quests, `accept <id>`, `abandon <id>`, `claim <id>`
- **Persistence**: `save [name]` to save, `load [name]` to load
- **Meta**: `help`, `quit`

## Quest System

The dynamic quest system generates procedural quests based on dungeon state and theme.

### Quest Objective Types (6)
1. **Kill** - Defeat a specific NPC
2. **Collect** - Gather items (with quantity tracking)
3. **Explore** - Visit a specific room
4. **Talk** - Converse with an NPC
5. **UseSkill** - Successfully complete a skill check
6. **Deliver** - Bring an item to an NPC

### Quest Lifecycle
- **ACTIVE** - Quest accepted and in progress
- **COMPLETED** - All objectives finished, reward ready
- **CLAIMED** - Reward collected
- **FAILED** - Quest abandoned (not yet implemented)

### Quest Commands
- `quests` or `journal` or `j` - View quest log with active/available/completed quests
- `accept <quest_id>` - Accept an available quest
- `abandon <quest_id>` - Remove an active quest
- `claim <quest_id>` - Claim rewards for completed quest (XP, gold, items)

### Quest Rewards
- **Experience points** - Track player progression
- **Gold** - In-game currency
- **Items** - Weapons, armor, consumables

## Multi-User Architecture

The game supports multiple concurrent players in a shared world:
- **GameServer**: Thread-safe shared WorldState with Mutex protection
- **PlayerSession**: Per-player I/O and event channels
- **Event broadcasting**: Players see actions happening in their room
- **Per-player combat**: Each player has independent combat state
- **Mode selection**: Choose single-player or multi-user at startup

See [Multi-User Documentation](docs/MULTI_USER.md) for complete details.

## Important Notes

- **No backward compatibility needed** - Can wipe and restart data between versions
- **API key optional** - Game works without OpenAI API key (fallback mode)
- **Java 17 required** - Uses Java 17 toolchain
- **All modules building** - 60+ tests passing
- **Project guidelines**: See `CLAUDE_GUIDELINES.md`
- **Requirements**: See `docs/requirements.txt`

## Documentation

- **[Getting Started](docs/GETTING_STARTED.md)** - Setup, commands, gameplay walkthrough
- **[Client UI](docs/CLIENT_UI.md)** - GUI client documentation and usage
- **[Architecture](docs/ARCHITECTURE.md)** - Module structure, data flow, file locations
- **[Implementation Log](docs/IMPLEMENTATION_LOG.md)** - Chronological feature list
- **[Multi-User](docs/MULTI_USER.md)** - Multi-player architecture details

## Next Developer

The GUI client with real engine integration, quest system, and automated testing are complete! Next priorities:
1. **Analyze and fix issues in full_playthrough_1760213544641.txt**
2. ~~**Connect GUI client to real game engine**~~ - ✅ **COMPLETE** - EngineGameClient fully integrated
3. **Quest auto-tracking** - Automatically update quest progress as player performs actions (kill NPCs, collect items, explore rooms, etc.)
4. **Network layer** (optional) - Add TCP/WebSocket support for remote multi-player
5. **Persistent vector storage** (optional) - Save/load embeddings to disk

See [Implementation Log](docs/IMPLEMENTATION_LOG.md) for full feature history.
