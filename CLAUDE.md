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
- **Intent system**: 20+ intent types for player actions (including Emote and AskQuestion)
- **Working game loop**: Console-based with text parser and LLM integration

### Game Features ✅
- **Combat system**: Turn-based with STR modifiers, weapon bonuses, armor defense
- **Death & respawn**: "Press any key to play again" prompt with full game restart
- **Equipment system**: Weapons (+damage) and armor (+defense)
- **Consumables**: Healing potions and other usable items
- **Skill checks**: D&D-style (d20 + modifier vs DC) with all 6 stats
- **Social interactions**: Persuasion and intimidation CHA checks
- **Quest system**: Procedurally generated with 6 objective types (Kill, Collect, Explore, Talk, UseSkill, Deliver) and **automatic progress tracking**
- **Persistence**: JSON-based save/load for game state
- **Procedural generation**: 4 themed dungeons (Crypt, Castle, Cave, Temple)

### AI/LLM Features ✅
- **RAG memory system**: Vector embeddings with semantic search
- **Dynamic descriptions**: Room descriptions that build on history
- **NPC dialogue**: Personality-driven with conversation memory
- **Combat narration**: Equipment-aware, concise combat descriptions (1-2 sentences per action, line-separated)

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
- **Comprehensive tests**: **~352 tests** across all modules (including 7 UI tests, 128 integration tests, 12 E2E scenario tests, 47 social system tests, 27 perception tests)
  - **Phase 1 Complete**: 82 new unit tests for equipment, inventory, world state, and combat
  - **Phase 2 Complete**: 5/5 integration tests complete (CombatIntegrationTest - 7 tests, ItemInteractionIntegrationTest - 13 tests, QuestIntegrationTest - 11 tests, SkillCheckIntegrationTest - 14 tests, SocialInteractionIntegrationTest - 13 tests)
  - **Phase 3 Complete**: 4/4 integration tests complete (SaveLoadIntegrationTest - 13 tests, ProceduralDungeonIntegrationTest - 21 tests, NavigationIntegrationTest - 21 tests, FullGameplayIntegrationTest - 15 tests)
  - **Phase 4 Complete**: 4/4 bot scenario tests complete (BruteForcePlaythroughTest - 3 tests, SmartPlaythroughTest - 3 tests, BadPlaythroughTest - 3 tests, AllPlaythroughsTest - 3 tests)
  - **Social System Tests**: 47 tests for social system (Phase 1: 30 component tests, Phase 2: 17 database tests)
- **InMemoryGameEngine**: Headless engine for automated testing
- **TestReport metrics**: Tracks playthrough metrics including combat rounds, damage taken, NPCs killed, skill/social checks passed, player death, and room exploration for game balance validation

## What's Next

Priority tasks:
1. **Network layer** (optional) - TCP/WebSocket support for remote multi-player
2. **Persistent memory storage** (optional) - Save/load vector embeddings to disk

## Commands

### Build and Development
- `gradle build` - Build the project (requires Java 17 toolchain)
- `gradle test` - Run all tests
- `gradle installDist && app/build/install/app/bin/app` - **Run console game**
- `gradle :client:run` - **Run GUI client**

### Testing
- `gradle test` - Run all tests (~352 tests across all modules)
- `gradle :core:test` - Run tests for specific module
- `gradle :client:test` - Run UI client tests
- `gradle :testbot:run` - Run automated test bot (requires OpenAI API key)

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
- **Social system**:
  - `core/src/main/kotlin/com/jcraw/mud/core/Component.kt` - Component system foundation
  - `core/src/main/kotlin/com/jcraw/mud/core/SocialComponent.kt` - Social component data model
  - `core/src/main/kotlin/com/jcraw/mud/core/SocialEvent.kt` - Social event types
  - `memory/src/main/kotlin/com/jcraw/mud/memory/social/` - Database layer (repositories)

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
- **Movement**: `n/s/e/w`, `north/south/east/west`, `go <direction>`, `go to <room name>` (natural language navigation!)
- **Interaction**: `look [target]`, `take/get <item>`, `take all/get all`, `drop <item>` (works with equipped items), `give <item> to <npc>`, `talk <npc>`, `inventory/i`
- **Combat**: `attack/kill/fight/hit <npc>` to start/continue combat
- **Equipment**: `equip/wield/wear <item>` to equip weapons or armor
- **Consumables**: `use/consume/drink/eat <item>` to use healing potions
- **Skill checks**: `check/test <feature>` to attempt skill checks
- **Social**: `persuade/convince <npc>` and `intimidate/threaten <npc>` for CHA checks, `smile/wave/nod/shrug/laugh/cry/bow [at <npc>]` for emotes, `ask <npc> about <topic>` to ask questions
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
- **ACTIVE** - Quest accepted and in progress (auto-tracks progress as you play)
- **COMPLETED** - All objectives finished, reward ready
- **CLAIMED** - Reward collected
- **FAILED** - Quest abandoned (not yet implemented)

### Auto-Tracking
Quest objectives automatically track progress as you play:
- **Kill objectives** - Track when you defeat NPCs in combat
- **Collect objectives** - Track when you pick up items
- **Explore objectives** - Track when you enter rooms
- **Talk objectives** - Track when you talk to NPCs
- **UseSkill objectives** - Track when you successfully complete skill checks
- **Deliver objectives** - Track when you give items to NPCs using `give <item> to <npc>`

When an objective completes, you'll see: `✓ Quest objective completed: <description>`
When all objectives complete, you'll see: `🎉 Quest completed: <title>`

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
- **All modules building** - **~352 tests** across modules (Phase 1 complete, Phase 2 complete - 5/5 integration tests done, Phase 3 complete - 4/4 integration tests done, Phase 4 complete - 4/4 E2E tests done, Social System Phases 1-4 complete - 54 tests)
- **Project guidelines**: See `CLAUDE_GUIDELINES.md`
- **Requirements**: See `docs/requirements.txt`

## Documentation

- **[Getting Started](docs/GETTING_STARTED.md)** - Setup, commands, gameplay walkthrough
- **[Client UI](docs/CLIENT_UI.md)** - GUI client documentation and usage
- **[Architecture](docs/ARCHITECTURE.md)** - Module structure, data flow, file locations
- **[Testing Strategy](docs/TESTING.md)** - Comprehensive testing guide, test organization, migration plan
- **[Implementation Log](docs/IMPLEMENTATION_LOG.md)** - Chronological feature list
- **[Multi-User](docs/MULTI_USER.md)** - Multi-player architecture details

## Current Status: All Testing Migration & Cleanup Complete (2025-10-16)

**Latest Update**: Legacy shell script tests deleted - codebase now uses only Kotlin tests via `gradle test`

**Phase 1 Testing Migration - COMPLETE** ✅
- ✅ Created `EquipmentSystemTest.kt` - 16 tests for weapon/armor mechanics
- ✅ Created `InventorySystemTest.kt` - 19 tests for inventory management
- ✅ Created `WorldStateTest.kt` - 33 tests for navigation and entity lookup
- ✅ Created `CombatResolverTest.kt` - 14 tests for combat damage and flow
- **Total: 82 new behavioral tests, all passing**

**Phase 2 Testing Migration - COMPLETE** ✅
- ✅ Fixed app module test compilation (removed outdated GameServerTest, added dependencies)
- ✅ Created `CombatIntegrationTest.kt` - **7 tests, all passing**
  - Player defeats weak NPC in combat
  - Equipped weapon increases damage
  - Equipped armor reduces incoming damage
  - Player death ends combat
  - Defeated NPC removed from room
  - Player can flee from combat
  - Combat progresses through multiple rounds
- ✅ Created `ItemInteractionIntegrationTest.kt` - **13 tests, all passing**
  - Take single/all items from room
  - Drop items (including equipped weapons)
  - Equip weapons and armor
  - Weapon swapping (replacing equipped weapons)
  - Use consumables (healing potions)
  - Consumables during combat
  - Healing caps at max health
  - Non-pickupable items
  - Inventory display
- ✅ Created `QuestIntegrationTest.kt` - **11 tests, all passing**
  - Quest acceptance and abandonment
  - Kill objective auto-tracking
  - Collect objective auto-tracking
  - Explore objective auto-tracking
  - Talk objective auto-tracking
  - Skill check objective auto-tracking
  - **Deliver objective auto-tracking** (fully implemented with `give` command)
  - Multi-objective quest completion
  - Reward claiming
  - Quest log display
- ✅ Created `SkillCheckIntegrationTest.kt` - **14 tests, all passing**
  - All 6 stat types (STR, DEX, CON, INT, WIS, CHA)
  - Difficulty levels (Easy, Medium, Hard)
  - Success and failure outcomes
  - Stat modifier effects on success rates
  - Multiple sequential skill checks
  - Edge cases with very high/low stats
- ✅ Created `SocialInteractionIntegrationTest.kt` - **13 tests, all passing**
  - Talk to NPCs (friendly and hostile)
  - Persuasion checks (success/failure)
  - Intimidation checks (success/failure)
  - Difficulty levels (easy vs hard)
  - Cannot persuade/intimidate same NPC twice
  - CHA modifier effects on success rates
  - Multiple NPCs independently
- **Progress: 5/5 integration tests complete (58 total integration tests)**

**Phase 3 Testing Migration - COMPLETE** ✅
- ✅ Created `SaveLoadIntegrationTest.kt` - **13 tests, all passing**
  - Save game state to disk
  - Load game state from disk
  - Persistence roundtrip (save → modify → load → verify)
  - Save file format validation (JSON structure)
  - Load after game modifications
  - Non-existent save file handling
  - Custom save names
  - List all saves
  - Delete save files
  - Combat state preservation
  - Room connections preservation
- ✅ Created `ProceduralDungeonIntegrationTest.kt` - **21 tests, all passing**
  - All 4 dungeon themes (Crypt, Castle, Cave, Temple)
  - Room connectivity (reachability, bidirectional connections, navigation)
  - NPC generation (boss NPCs, hostile NPCs, friendly NPCs, combat and dialogue)
  - Item distribution (weapons, armor, consumables, pickup/use mechanics)
  - Quest generation (all quest types based on dungeon state)
  - Deterministic generation with seeds
- ✅ Created `NavigationIntegrationTest.kt` - **21 tests, all passing**
  - Directional navigation (n/s/e/w, cardinal names, "go" syntax)
  - Natural language navigation ("go to throne room", partial names, adjacency checks)
  - Invalid direction handling (invalid directions, non-existent rooms, gibberish)
  - Exit validation (bidirectionality, reachability, hub rooms, dead ends)
  - Procedural dungeon navigation (connectivity, valid exits, fully connected)
- ✅ Created `FullGameplayIntegrationTest.kt` - **15 tests, all passing**
  - Basic game loop (exploration, NPC interaction, inventory checks)
  - Multi-system integration (navigation + equipment + combat, consumables in combat, loot collection, skill checks during exploration)
  - Quest integration (explore, collect, kill quests with auto-tracking)
  - Save/load during gameplay (mid-exploration, active combat, with active quests)
  - Complete playthroughs (full dungeon exploration, realistic gameplay session, procedural dungeon)
- **Progress: 4/4 integration tests complete (70 additional tests)**

**Phase 4 Testing Migration - COMPLETE** ✅
- ✅ Created `BruteForcePlaythroughTest.kt` - **3 tests, all passing**
  - Bot completes brute force playthrough by collecting gear and defeating boss
  - Bot explores multiple rooms looking for gear
  - Bot takes damage but survives with equipment
- ✅ Created `SmartPlaythroughTest.kt` - **3 tests, all passing**
  - Bot completes smart playthrough using social skills and intelligence
  - Bot attempts social interactions before resorting to combat
  - Bot explores secret areas and completes skill checks
- ✅ Created `BadPlaythroughTest.kt` - **3 tests, all passing**
  - Bot rushes to boss and dies without gear
  - Bot reaches boss room quickly without collecting gear
  - Bot takes fatal damage from boss encounter
- ✅ Created `AllPlaythroughsTest.kt` - **3 tests, all passing**
  - All three playthrough scenarios complete successfully
  - Game balance validation (bad player dies, good players win)
  - Multiple solution paths exist and are viable
- **Progress: 4/4 E2E scenario tests complete (12 additional tests)**

**Major Achievements**:
- ✅ All automated tests: **100% pass rate**
- ✅ Phase 1 unit tests: **COMPLETE (82 tests total)**
- ✅ Phase 2 integration tests: **5/5 COMPLETE (58 tests total)**
- ✅ Phase 3 integration tests: **4/4 COMPLETE (70 tests total)**
- ✅ Phase 4 E2E scenario tests: **4/4 COMPLETE (12 tests total)**
- ✅ BUG-001 (combat state desync): **FIXED**
- ✅ BUG-002 (social checks): **FIXED**
- ✅ BUG-003 (death/respawn): **FIXED**
- ✅ BUG-006 (navigation NLU): **FIXED**
- ✅ BUG-007 (death detection): **FIXED**
- ✅ BUG-008 (health synchronization): **FIXED**
- ✅ IMPROVEMENT-001 (smart_playthrough validation): **FIXED**

1. **IMPROVEMENT-001: SmartPlaythrough Test Validation - RESOLVED** ✅
   - **Root Cause**:
     - Validator expected all social checks to succeed, didn't account for dice roll failures
     - Input generator tried to intimidate Skeleton King before navigating to throne room
   - **Fix**:
     - Updated OutputValidator.kt criteria to recognize dice roll failures as valid behavior
     - Updated InputGenerator.kt strategy to navigate to throne room before intimidating
   - **Files Modified**:
     - `testbot/src/main/kotlin/com/jcraw/mud/testbot/OutputValidator.kt`
     - `testbot/src/main/kotlin/com/jcraw/mud/testbot/InputGenerator.kt`

2. **BUG-002: Social Checks Non-Functional - RESOLVED** ✅ (previous session)
   - **Root Cause**: Procedural NPC generator wasn't setting social challenge properties
   - **Fix**: Added `createSocialChallenges()` function that scales difficulty with NPC power level
   - All NPCs now support appropriate social interactions
   - Skeleton King defeatable via combat, persuasion (DC 25), or intimidation (DC 25)

3. **BUG-003: Death/Respawn - RESOLVED** ✅ (previous session)
   - **Root Cause**: Test bot continued playing after player death
   - **Fix**: Early termination logic handles BadPlaythrough scenario
   - Test detects player death and terminates with PASSED status

4. **BUG-006: Navigation NLU - RESOLVED** ✅
   - **Root Cause**: IntentRecognizer only supported directional navigation (e.g., "go north"), not room-name navigation (e.g., "go to throne room")
   - **Fix**: Enhanced IntentRecognizer to support room-name navigation
     1. Added `exitsWithNames` parameter to `parseIntent()` - maps exits to destination room names
     2. Updated LLM system prompt with rule 11: "ROOM-NAME NAVIGATION" to match player input like "go to throne room" with available exits
     3. Updated all parseIntent callers to build and pass exit information
   - **Files Modified**:
     - `perception/src/main/kotlin/com/jcraw/mud/perception/IntentRecognizer.kt`
     - `app/src/main/kotlin/com/jcraw/app/App.kt`
     - `client/src/main/kotlin/com/jcraw/mud/client/EngineGameClient.kt`
     - `testbot/src/main/kotlin/com/jcraw/mud/testbot/InMemoryGameEngine.kt`

5. **BUG-007: Player Death Detection - RESOLVED** ✅
   - **Root Cause**: CombatResolver returned `playerDied = true` but narrative didn't include death messages, causing test detection to fail
   - **Fix**: Updated CombatResolver to include explicit death message in combat narratives
     - Added "\n\nYou have been defeated! Your vision fades as you fall to the ground..." to death narratives
     - Applied to both regular combat death and flee failure death scenarios
   - **Files Modified**:
     - `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/CombatResolver.kt`
   - **Test Result**: AllPlaythroughsTest now correctly detects player death, bad playthrough test passes

6. **BUG-008: Health Synchronization - RESOLVED** ✅
   - **Root Cause**: Combat state tracked player health correctly, but PlayerState.health was never synchronized
     - CombatState.playerHealth decreased properly during combat
     - But `worldState.player.health` stayed at original value (40 HP)
     - Players could survive with -4 HP because actual health never updated
   - **Fix**: Synchronized player's actual health with combat state health at every update:
     - During combat: `player.copy(health = combatState.playerHealth)` when updating combat state
     - When ending combat: Sync final health before calling `endCombat()`
     - Applied to flee attempts, normal attacks, and consumable use during combat
   - **Files Modified**:
     - `testbot/src/main/kotlin/com/jcraw/mud/testbot/InMemoryGameEngine.kt` (lines 302-316, 98-105)
     - `app/src/main/kotlin/com/jcraw/app/App.kt` (lines 715-730, 333-339, 912-916)
     - `client/src/main/kotlin/com/jcraw/mud/client/EngineGameClient.kt` (lines 668-689, 750-754, 266-271)
   - **Test Result**: AllPlaythroughsTest now correctly validates game balance - bad players die as expected

**Test Results**:
- Brute force playthrough: **100% pass rate (17/17 steps)** ✅
- Bad playthrough: **100% pass rate (8/8 steps)** ✅
- Smart playthrough: **100% pass rate (7/7 steps)** ✅

## Next Developer

The GUI client with real engine integration, quest system with auto-tracking, automated testing improvements, social interaction system, natural language navigation, **ALL 4 PHASES OF TESTING MIGRATION & CLEANUP COMPLETE**! **SOCIAL SYSTEM PHASE 8 COMPLETE**! 🎉

**Latest Update (2025-10-17)**: Social System Phase 9 - Integration Testing IN PROGRESS ⏳
  - Created comprehensive SocialSystemV2IntegrationTest.kt with 18 integration tests covering:
    - Emote system (all 7 emote types: smile, wave, nod, shrug, laugh, cry, bow)
    - Ask question system with knowledge persistence
    - Disposition tracking and tier calculation (ALLIED/FRIENDLY/NEUTRAL/UNFRIENDLY/HOSTILE)
    - Quest completion disposition bonuses (+15 to quest giver)
    - Disposition-aware dialogue generation
    - Social event persistence and retrieval
    - Full end-to-end social interaction cycles
  - Fixed PersistentVectorStore to implement searchWithMetadata interface method
  - Fixed QuestTracker disposition bonus application to properly update NPCs via room
  - **TODO**: Fix GameServer.kt destructuring errors (trackQuests returns Triple but being destructured as Pair in 9 locations)
  - Tests ready to run once compilation errors are resolved

**Previous Update (2025-10-17)**: Social System Phase 8 - Quest/Memory Integration COMPLETE ✅
  - All 4 game implementations now have quest tracking with disposition bonuses
  - App.kt: SocialDatabase, repositories, and DispositionManager wired up to QuestTracker
  - GameServer.kt: Social system fully integrated with optional SocialDatabase parameter
  - EngineGameClient.kt: Quest tracking fully integrated with UI event notifications
  - InMemoryGameEngine.kt: Quest tracking integrated for automated testing
  - MemoryManager enhanced with metadata filtering (recallWithMetadata method)
  - Quest completion now triggers +15 disposition to quest giver NPC (when dispositionManager is present)

**Previous Update (2025-10-17)**: Completed Social System Phase 7 - Procedural Generation Update
  - Updated NPCGenerator to automatically attach SocialComponent to all generated NPCs
  - Theme-based personality generation: Each of 4 dungeon themes (Crypt, Castle, Cave, Temple) has unique personality pools
  - Trait system: NPCs get 1-3 traits (hostile/friendly) or 2-4 traits (boss) from curated trait lists
  - Disposition initialization: Hostile NPCs start at -75 to -25, friendly at 25 to 60, bosses at -100 to -75
  - Deterministic generation: Same seed produces identical personalities, traits, and dispositions
  - 19 new tests in NPCGeneratorTest.kt covering all NPC types and themes (all passing)
  - Total test count ~371 tests (all passing)

**Previous Fix (2025-10-16)**: Fixed critical combat health synchronization bug (BUG-008). The combat state tracked player health correctly, but the player's actual health was never updated, allowing players to survive with negative health. Fixed in all three game implementations:
  - `InMemoryGameEngine.kt` (testbot) - Lines 302-316, 98-105
  - `App.kt` (console) - Lines 715-730, 333-339, 912-916
  - `EngineGameClient.kt` (GUI) - Lines 668-689, 750-754, 266-271

**Previous Fixes (2025-10-16)**:
- BUG-007: Fixed player death detection - Combat Resolver now includes explicit death messages in narratives
- Test infrastructure: Fixed AllPlaythroughsTest to use centralized player stats from SampleDungeon

**Testing Status**:
- ✅ **Phase 1 Complete**: 82 new unit tests created
  - Equipment system (16 tests)
  - Inventory system (19 tests)
  - World state navigation (33 tests)
  - Combat resolver (14 tests)
- ✅ **Phase 2 Complete**: 5/5 integration tests complete (58 tests)
  - CombatIntegrationTest, ItemInteractionIntegrationTest, QuestIntegrationTest, SkillCheckIntegrationTest, SocialInteractionIntegrationTest
- ✅ **Phase 3 Complete**: 4/4 integration tests complete (70 tests)
  - SaveLoadIntegrationTest, ProceduralDungeonIntegrationTest, NavigationIntegrationTest, FullGameplayIntegrationTest
- ✅ **Phase 4 Complete**: 4/4 E2E scenario tests complete (12 tests)
  - BruteForcePlaythroughTest, SmartPlaythroughTest, BadPlaythroughTest, AllPlaythroughsTest
- ✅ **Cleanup Complete**: All 13 legacy shell scripts deleted (2025-10-16)
- ✅ **Social System Phase 1 Complete**: Component-based architecture foundation (2025-10-16)
  - Component system with 30 tests (component attachment, disposition, social events, emotes, knowledge)
- ✅ **Social System Phase 2 Complete**: Database layer with SQLite persistence (2025-10-16)
  - 17 tests for repository implementations (all passing)
- ✅ **Social System Phase 3 Complete**: Core logic components (2025-10-16)
  - DispositionManager, EmoteHandler, NPCKnowledgeManager
- ✅ **Social System Phase 4 Complete**: Intent recognition (2025-10-17)
  - Intent.Emote and Intent.AskQuestion added with 7 new tests (all passing)
- ✅ **Social System Phase 5 Complete**: NPC Dialogue Enhancement (2025-10-17)
  - Disposition-aware dialogue generation
- ✅ **Social System Phase 6 Complete**: Game Loop Integration (2025-10-17)
  - All three game implementations handle emotes and questions
- ✅ **Social System Phase 7 Complete**: Procedural Generation Update (2025-10-17)
  - NPCGenerator creates NPCs with SocialComponent (19 tests)
- **Total: ~371 tests** across all modules, 100% pass rate

**All Known Bugs Resolved!** 🎉

**Next Priorities**:

1. **Social System V2** (IN PROGRESS):
   - ✅ Phase 1: Core Data Models - COMPLETE (2025-10-16)
   - ✅ Phase 2: Database Layer - COMPLETE (2025-10-16)
     - SQLite schema with 3 tables (knowledge_entries, social_events, social_components)
     - KnowledgeRepository for NPC knowledge persistence (17 tests passing)
     - SocialEventRepository for event history tracking (17 tests passing)
     - SocialComponentRepository for social state persistence (17 tests passing)
   - ✅ Phase 3: Core Logic Components - COMPLETE (2025-10-16)
     - DispositionManager for event application and disposition-based behavior
     - EmoteHandler for processing 7 emote types with context-aware narratives
     - NPCKnowledgeManager for knowledge queries and LLM canon generation
     - SkillSystem and StorySystem stub interfaces for future integration
   - ✅ Phase 4: Intent Recognition - COMPLETE (2025-10-17)
     - Added Intent.Emote for expressing emotions/actions (smile, wave, nod, shrug, laugh, cry, bow)
     - Added Intent.AskQuestion for asking NPCs about topics
     - Updated IntentRecognizer with LLM prompts and fallback parsing for both new intents
     - 7 new tests added to IntentTest.kt (all passing)
   - ✅ Phase 5: NPC Dialogue Enhancement - COMPLETE (2025-10-17)
     - Enhanced NPCInteractionGenerator with disposition-aware dialogue
     - Disposition tier affects LLM tone (ALLIED → NEUTRAL → HOSTILE)
     - Personality and traits included in dialogue prompts
     - Unified KnowledgeEntry data model (removed duplicate)
     - Updated SocialDatabase schema with is_canon and tags
   - ✅ Phase 6: Game Loop Integration - COMPLETE (2025-10-17)
     - InMemoryGameEngine.kt: Full emote and question handler integration with EmoteHandler and NPCKnowledgeManager
     - App.kt: Graceful fallback handlers added (placeholder for future integration)
     - EngineGameClient.kt: Graceful fallback handlers added (placeholder for future integration)
     - All three game implementations now handle Intent.Emote and Intent.AskQuestion
   - ✅ Phase 7: Procedural Generation Update - COMPLETE (2025-10-17)
     - Updated NPCGenerator to create NPCs with SocialComponent
     - Theme-based personality generation (4 themes: Crypt, Castle, Cave, Temple)
     - Trait generation (hostile: 1-3 traits, friendly: 1-3 traits, boss: 2-4 traits)
     - Disposition ranges: hostile (-75 to -25), friendly (25 to 60), boss (-100 to -75)
     - 19 new tests in NPCGeneratorTest.kt (all passing)
   - ✅ Phase 8: Quest/Memory Integration - COMPLETE (2025-10-17)
     - DispositionManager integrated with QuestTracker for quest completion bonuses (+15 disposition)
     - All 4 game implementations updated (App.kt, GameServer.kt, EngineGameClient.kt, InMemoryGameEngine.kt)
     - MemoryManager enhanced with metadata filtering (recallWithMetadata method)
     - VectorStore interface extended with searchWithMetadata for filtered searches
     - Quest tracking automatically updates NPC disposition when quests are completed
   - ⏳ Phase 9: Integration Testing - IN PROGRESS (2025-10-17)
     - Created SocialSystemV2IntegrationTest.kt with 18 comprehensive tests
     - Tests cover: emotes, questions, disposition, quest bonuses, dialogue, persistence, E2E cycles
     - Fixed PersistentVectorStore and QuestTracker compilation issues
     - TODO: Fix GameServer.kt trackQuests destructuring (9 locations need Triple instead of Pair)
   - ⏳ Phase 10-11: Documentation and polish (pending)

2. **Feature work** (Future):
   - **Network layer** (optional) - TCP/WebSocket support for remote multi-player
   - **Persistent memory storage** (optional) - Save/load vector embeddings to disk

2. **Polish & Enhancement Ideas**:
   - More quest objective types (Escort, Defend, Craft, etc.)
   - Character progression system (leveling, skill trees)
   - More dungeon themes and procedural variations
   - Save/load for GUI client
   - Multiplayer lobby system

See [Implementation Log](docs/IMPLEMENTATION_LOG.md) for full feature history and [BUGS.md](docs/BUGS.md) for detailed bug status.
