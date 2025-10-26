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
- **~684 tests** across all modules, 100% pass rate
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
- **Console app**: `app/src/main/kotlin/com/jcraw/app/`
  - `App.kt` (145 lines) - Main entry point and initialization
  - `MudGameEngine.kt` (430 lines) - Core game engine and loop with Combat V2 integration
  - `MultiUserGame.kt` (248 lines) - Multi-user server mode
- **Intent handlers**: `app/src/main/kotlin/com/jcraw/app/handlers/` (5 handler files)
  - `MovementHandlers.kt` (171 lines) - Navigation and exploration
  - `ItemHandlers.kt` (348 lines) - Inventory and equipment
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
- **All modules building** - ~684 tests passing across all modules
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

## Current Status

**All core systems complete!** 🎉

- ✅ GUI client with real engine integration
- ✅ Quest system with auto-tracking
- ✅ Social system (11 phases complete)
- ✅ Skill system V2 (11 phases complete)
- ✅ All testing migration & cleanup complete
- ✅ Code refactoring complete (all files under 600 lines)
- ✅ All known bugs resolved

**Current Feature: Combat System V2** 🚧

Implementation plan: [Combat System V2 Plan](docs/requirements/V2/COMBAT_SYSTEM_IMPLEMENTATION_PLAN.md)

**Phase 1: Foundation - Component & Schema (COMPLETE)** ✅

Completed:
- ✅ `CombatComponent.kt` - Combat component with HP calculation, damage, healing, timers, status effects (core:308)
- ✅ `StatusEffect.kt` - Status effect data class with tick processing (7 effect types) (core:62)
- ✅ `DamageType.kt` - Damage type enum (6 types: Physical, Fire, Cold, Poison, Lightning, Magic)
- ✅ `CombatEvent.kt` - Sealed class for combat event logging (16 event types) (core:294)
- ✅ `CombatDatabase.kt` - SQLite database schema for combat system (memory:132)
- ✅ `CombatRepository.kt` - Repository interface for combat persistence (core:68)
- ✅ `SQLiteCombatRepository.kt` - SQLite implementation of CombatRepository (memory:122)
- ✅ `CombatComponentTest.kt` - Unit tests for CombatComponent calculations (44 tests, core:test)
- ✅ `CombatDatabaseTest.kt` - Integration tests for repository save/load (22 tests, memory:test)

**Phase 2: Turn Queue & Timing System (COMPLETE)** ✅

Completed:
- ✅ `WorldState.gameTime` - Game clock field and advanceTime() method (core/WorldState.kt:10)
- ✅ `ActionCosts.kt` - Action cost constants and calculation formula (reasoning/combat:46)
- ✅ `TurnQueueManager.kt` - Priority queue for asynchronous turn ordering (reasoning/combat:143)
- ✅ `SpeedCalculator.kt` - Speed skill integration and action cost API (reasoning/combat:88)
- ✅ `ActionCostsTest.kt` - Unit tests for action cost calculations (10 tests, reasoning:test)
- ✅ `SpeedCalculatorTest.kt` - Unit tests for speed calculations (13 tests, reasoning:test)
- ✅ `TurnQueueManagerTest.kt` - Unit tests for turn queue operations (21 tests, reasoning:test)

**Phase 3: Damage Resolution & Multi-Skill Checks (COMPLETE)** ✅

Completed:
- ✅ `SkillClassifier.kt` - LLM-based skill classification with weighted multi-skill detection (reasoning/combat:211)
- ✅ `SkillClassifierTest.kt` - Unit tests for skill classification (20 tests, reasoning:test)
- ✅ `AttackResolver.kt` - Multi-skill attack resolution with d20 rolls and weighted modifiers (reasoning/combat:234)
- ✅ `DamageCalculator.kt` - Configurable damage formulas with resistance and variance (reasoning/combat:167)
- ✅ `StatusEffectApplicator.kt` - Status effect application with stacking rules and event logging (reasoning/combat:205)

**Phase 4: Combat Initiation & Disposition Integration (COMPLETE)** ✅

Completed:
- ✅ `CombatInitiator.kt` - Disposition threshold logic for hostile entity detection (reasoning/combat:95)
- ✅ `CombatBehavior.kt` - Automatic counter-attack and combat de-escalation (reasoning/combat:166)
- ✅ `PlayerState.kt` - Removed activeCombat field (emergent combat replaces modal combat)
- ✅ `CombatHandlers.kt` - Refactored to use AttackResolver and CombatBehavior (app/handlers:182)
- ✅ `MudGameEngine.kt` - Updated describeCurrentRoom with disposition-based combat status
- ✅ `CombatInitiatorTest.kt` - Unit tests for hostility detection (9 tests, reasoning:test)
- ✅ `CombatBehaviorTest.kt` - Unit tests for counter-attack and de-escalation (10 tests, reasoning:test)

**Phase 5: Monster AI & Quality Modulation (COMPLETE)** ✅

Completed:
- ✅ `MonsterAIHandler.kt` - LLM-driven AI with intelligence/wisdom modulation (reasoning/combat:348)
  - Intelligence determines prompt complexity (low/medium/high tactical depth)
  - Wisdom determines temperature (0.3-1.2 for consistent to erratic decisions)
  - Fallback rules for robustness when LLM unavailable
- ✅ `PersonalityAI.kt` - Personality-based behavior modulation (reasoning/combat:251)
  - Trait-based decision modifications (aggressive, cowardly, defensive, brave, greedy, honorable)
  - Personality-specific flee thresholds (cowardly 50%, brave 10%, normal 30%)
  - Action preferences and flavor text generation
- ✅ Integration - MonsterAIHandler uses PersonalityAI to modify LLM decisions
- ✅ `MudGameEngine.kt` - Integrated MonsterAIHandler with turn queue execution (app:430)
  - Added turnQueue, monsterAIHandler, attackResolver, llmService fields
  - Added processNPCTurns() - Executes AI decisions for NPCs whose timer <= gameTime
  - Added executeNPCDecision() - Handles Attack, Defend, UseItem, Flee, Wait decisions
  - Added executeNPCAttack() - Resolves NPC attacks on player with damage and death handling
  - Game loop calls processNPCTurns() before player input
  - Game loop advances gameTime after player actions

**Compilation Fixes (COMPLETE)** ✅

Completed:
- ✅ `CombatResolver.kt` - Deprecated legacy V1 methods (made stubs for backward compatibility)
- ✅ `AttackResolver.kt` - Fixed Entity.Player construction and component access
- ✅ `StatusEffectApplicator.kt` - Fixed Map-based component operations
- ✅ All reasoning module compilation errors resolved

**Phase 5 Testing (COMPLETE)** ✅

Completed:
- ✅ `MonsterAIHandlerTest.kt` - Unit tests for AI decision-making (18 tests, reasoning:test)
  - Temperature calculation based on wisdom
  - Prompt complexity scaling with intelligence
  - Fallback decision-making when LLM unavailable
  - Decision parsing from LLM responses (Attack, Defend, Flee)
  - Personality integration with cowardly traits
- ✅ `PersonalityAITest.kt` - Unit tests for personality behavior (22 tests, reasoning:test)
  - Flee threshold variations by personality (cowardly 50%, brave 10%, normal 30%)
  - Defense preference by personality
  - Aggressive behavior enforcement (never flee/wait/defend)
  - Decision modification based on traits
  - Action preference weights
  - Flavor text generation
- ✅ All 40 tests passing

**Phase 6: Optimized Flavor Narration (COMPLETE)** ✅

Completed:
- ✅ `NarrationVariantGenerator.kt` - Pre-generates combat narration variants for common scenarios (memory/combat:330)
  - Generates 50+ variants per scenario (melee, ranged, spell, critical, death, status effects)
  - Tags variants with metadata (weapon type, damage tier, outcome) for semantic search
  - Uses LLM to create diverse, vivid combat descriptions offline
  - Stores in vector DB for fast runtime retrieval
- ✅ `NarrationMatcher.kt` - Semantic search for cached narrations (memory/combat:150)
  - CombatContext data class for matching (scenario, weapon, damage tier, outcome)
  - findNarration() method with semantic search via MemoryManager
  - Helper methods: determineDamageTier(), determineScenario()
  - Weapon category matching for flexible retrieval
- ✅ `CombatNarrator.kt` - Refactored to use caching with LLM fallback (reasoning:320)
  - narrateAction() - New method for single action narration with caching
  - Tries cache first, falls back to live LLM generation on miss
  - Stores new LLM responses in cache for future use
  - Equipment-aware descriptions (weapon/armor names included)
  - Maintains backward compatibility with existing narrateCombatRound()

Next tasks:
- Phase 7: Death, Corpses & Item Recovery

Key features for V2:
- Emergent combat (no mode switches, disposition-triggered)
- Asynchronous turn-based system with game clock
- Multi-skill checks for attack resolution
- LLM-driven monster AI modulated by intelligence/wisdom
- Optimized narration with vector DB caching
- Permadeath with corpse/item recovery system
- Status effects (DOT, buffs, debuffs)
- Advanced mechanics (stealth, AoE, magic, environmental interactions)

**Latest updates** can be found in the git commit history and individual documentation files.

See [Implementation Log (archived)](docs/archive/IMPLEMENTATION_LOG.md) for complete feature history.
