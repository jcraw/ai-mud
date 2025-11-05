# Architecture

This document describes the technical architecture of the AI-MUD engine.

## Module Structure

Multi-module Gradle project with 9 modules:

### Core Modules

- **core** - World model (Room, WorldState, PlayerState, Entity, Direction, Quest, SocialComponent)
  - Immutable data models
  - Game state management
  - Component system for extensible entity behaviors
  - No external dependencies except kotlinx.serialization

- **perception** - Input parsing and intent recognition
  - Depends on: core, llm
  - Text-to-Intent parsing
  - Command interpretation

- **reasoning** - LLM-powered content generation and game logic
  - Depends on: core, llm, memory
  - Room description generation
  - NPC dialogue generation (disposition-aware)
  - Combat narration
  - Procedural dungeon generation
  - Quest generation
  - Skill check resolution
  - Social system logic (disposition tracking, emotes, knowledge management)

- **memory** - Vector database integration and state persistence
  - Depends on: core
  - RAG (Retrieval-Augmented Generation) system
  - Vector embeddings and similarity search
  - JSON-based persistence
  - Pluggable vector store interface
  - Social system database (SQLite) with knowledge/event/component persistence

- **action** - Output formatting and narration
  - Depends on: core
  - Response generation

- **llm** - OpenAI client and LLM interfaces
  - No dependencies on other modules
  - OpenAI API integration (chat completion + embeddings)
  - ktor 3.1.0 HTTP client

- **app** - Main game application and console interface
  - Depends on all modules
  - Game loop implementation
  - Multi-user server (GameServer, PlayerSession)
  - Single-player game (MudGame)

- **testbot** - Automated testing system
  - Depends on: core, llm, perception, reasoning, memory, action
  - LLM-powered test generation
  - ReAct loop for autonomous testing

- **client** - Compose Multiplatform GUI client
  - Depends on: core, perception, reasoning, memory, action, llm
  - Desktop GUI with unidirectional data flow
  - EngineGameClient wraps complete game engine

- **utils** - Shared utilities
  - No dependencies

### Build Configuration
- Gradle with Kotlin DSL
- Version catalog in `gradle/libs.versions.toml`
- Convention plugin in `buildSrc` for shared build logic
- Java 17 toolchain
- Kotlin 2.2.0
- kotlinx ecosystem dependencies configured

## Implemented Architecture

Clean separation following SOLID principles:

### Layer Responsibilities

1. **Core** - Immutable data models and world state
   - All game entities (Room, Player, NPC, Item, Feature)
   - State transitions are pure functions
   - No I/O or side effects

2. **Perception** - Input parsing and LLM-based intent recognition
   - Converts raw text to structured Intent objects
   - Pattern matching and command aliases

3. **Reasoning** - LLM-powered generation and game logic resolution
   - Processes intents and generates responses
   - Updates world state
   - Generates dynamic content (descriptions, dialogue, narration)
   - Procedural generation (dungeons, quests)

4. **Action** - Output narration and response generation
   - Formats responses for display

5. **Memory** - Vector database for history and structured world state
   - Stores game events with embeddings
   - Semantic search for contextual retrieval
   - Persistence to disk

## Data Flow

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

### Single-Player Flow
1. User types command
2. App.parseInput() → Intent
3. App.processIntent() → calls appropriate handler
4. Handler updates WorldState
5. Response displayed to user

### Multi-User Flow
1. User types command
2. PlayerSession receives input
3. GameServer.processIntent() with player ID
4. Handler updates shared WorldState (thread-safe with Mutex)
5. GameEvent broadcast to relevant players
6. Response sent to player's session

## Key Principles

- **KISS principle** - Avoid overengineering, minimal abstractions
- **Sealed classes over enums** - Better type safety and exhaustive when expressions
- **Behavior-driven testing** - Focus on contracts, not implementation
- **Files under 300-500 lines** - Maintain readability
- **GPT4_1Nano for development** - Cost savings during development
- **Immutable state** - All state transitions return new copies
- **Thread-safe mutations** - Kotlin coroutines with Mutex for shared state

## Component Details

### World State Management
- `WorldState` - Top-level game state
  - Map of rooms
  - Map of players (multi-user support)
  - Available quests
  - Game properties
- `PlayerState` - Per-player state
  - Health, stats, inventory
  - Equipped items
  - Active combat
  - Active quests
  - Experience and gold
- `Room` - Location data
  - Name, description, traits
  - Entities (NPCs, items, features, players)
  - Exits to other rooms

### Combat System
- Turn-based with d20 mechanics
- STR modifiers for damage
- Armor provides defense bonus
- Per-player combat state
- LLM-narrated combat events

### Skill System
- D&D-style stats: STR, DEX, CON, INT, WIS, CHA
- d20 + modifier vs Difficulty Class
- Critical success (nat 20) and failure (nat 1)
- Skill challenges on features and NPCs

### Quest System
- Procedurally generated based on world state
- 6 objective types: Kill, Collect, Explore, Talk, UseSkill, Deliver
- Quest lifecycle: ACTIVE → COMPLETED → CLAIMED
- Rewards: XP, gold, items
- Theme-appropriate quest generation

### RAG (Memory) System
- Stores game events with text embeddings
- Cosine similarity search for relevant context
- Enhances LLM prompts with historical context
- Pluggable vector store (in-memory or persistent)

### Procedural Generation
- 4 themed dungeons: Crypt, Castle, Cave, Temple
- Graph-based room layout
- Theme-appropriate traits, NPCs, and items
- NPCs generated with SocialComponents (personality, traits, disposition)
- Deterministic with optional seed
- Boss rooms and entrance designation

### Social System
- Component-based architecture for extensible NPC behaviors
- Disposition tracking (-100 to +100) with 5 tiers (ALLIED, FRIENDLY, NEUTRAL, UNFRIENDLY, HOSTILE)
- Emote system with 7 emote types (smile, wave, nod, shrug, laugh, cry, bow)
- Question/answer system with persistent knowledge base
- Quest completion grants +15 disposition to quest giver
- Disposition-aware dialogue generation
- SQLite database for persistence (knowledge entries, social events, social components)
- Procedurally generated NPC personalities and traits based on dungeon theme
- See [Social System Documentation](./SOCIAL_SYSTEM.md) for complete details

### Item System V2
- **Component-based architecture** for inventory and trading systems
  - `InventoryComponent` - Weight-limited inventory with 12 equip slots (`core/InventoryComponent.kt`)
  - `TradingComponent` - Merchant trading with finite gold and stock (`core/TradingComponent.kt`)
- **ECS-based item model**:
  - `ItemTemplate` - Shared definitions with properties and tags (53 templates loaded from JSON)
  - `ItemInstance` - Quality (1-10), charges, quantity with stacking support
  - 10 item types: WEAPON, ARMOR, CONSUMABLE, RESOURCE, QUEST, TOOL, CONTAINER, SPELL_BOOK, SKILL_BOOK, MISC
  - 5 rarity tiers: COMMON, UNCOMMON, RARE, EPIC, LEGENDARY
- **Loot system** - Weighted drop tables with rarity-based distribution (`reasoning/loot/`)
  - LootTable with quality modifiers and drop chances
  - LootGenerator with source-based quality (+0/+1/+2 for common/elite/boss)
  - 9 predefined loot tables in LootTableRegistry
  - Corpse-based loot generation integrated with DeathHandler
- **Gathering system** - Skill-based resource harvesting from features
  - D&D-style skill checks (d20 + modifier vs DC)
  - Tool requirements via tag matching ("mining_tool", "chopping_tool", etc.)
  - Finite resources tracked via Entity.Feature.isCompleted
  - XP rewards (50 on success, 25 on failure)
- **Crafting system** - Recipe-based with 24 preloaded recipes (`reasoning/crafting/`)
  - D&D-style skill checks determine success
  - Quality scaling based on skill level (level/10, clamped 1-10)
  - Failure consumes 50% of inputs
  - Tool requirements and material consumption
  - XP rewards (50 + difficulty*5 on success, 10 + difficulty on failure)
- **Trading system** - Disposition-aware merchant interactions (`reasoning/trade/`)
  - Finite merchant gold prevents exploits
  - Disposition price formula: price = base * (1.0 + (disposition - 50) / 100)
  - Stock depletion on buy, replenishment on sell
  - Weight checks prevent over-encumbrance
- **Pickpocketing system** - Stealth/consequence mechanics (`reasoning/pickpocket/`)
  - Skill check: max(Stealth, Agility) vs target Perception
  - Disposition penalty (-20 to -50) on failure
  - Wariness status (+20 Perception for 10 turns) after caught
- **Multipurpose items** - Tag-based system for emergent uses (`reasoning/items/`)
  - Tags enable flexible uses: "blunt"→weapon, "explosive"→detonate, "container"→storage
  - Improvised weapon damage = weight * 0.5
  - Environmental uses: flammable (burn), fragile (break), liquid (pour)
- **Database persistence** - SQLite schema with 5 tables (`memory/item/ItemDatabase.kt`)
  - item_templates, item_instances, inventories, recipes, trading_stocks
  - JSON serialization for complex fields (tags, properties, equipped items)
- **Skills & combat integration**:
  - Capacity = Strength * 5kg + bag bonuses + perk multipliers
  - Equipped items provide skill bonuses, weapon damage, armor defense
  - Quality multiplier affects damage (0.8x to 2.0x for quality 1-10)
- See [Items and Crafting Documentation](./ITEMS_AND_CRAFTING.md) for complete details

### Combat Narration Caching System
- **Vector DB caching** for optimized LLM performance
- **Three-tier approach**:
  1. **Pre-generated variants** - Offline generation of 50+ narration variants per scenario
  2. **Semantic search** - Runtime matching of combat context to cached narrations
  3. **LLM fallback** - Live generation for unique scenarios with automatic caching
- **NarrationVariantGenerator** (`memory/combat/NarrationVariantGenerator.kt`)
  - Pre-generates variants for common scenarios (melee, ranged, spell, critical, death, status effects)
  - Tags variants with metadata (weapon type, damage tier, outcome) for semantic search
  - Uses LLM offline to create diverse, vivid combat descriptions
  - Stores in vector DB with embeddings for fast retrieval
- **NarrationMatcher** (`memory/combat/NarrationMatcher.kt`)
  - CombatContext data class captures combat situation (scenario, weapon, damage, outcome)
  - Semantic search via MemoryManager to find best matching cached narration
  - Helper methods: determineDamageTier(), determineScenario() for context classification
  - Weapon category matching for flexible retrieval (e.g., "dagger" matches "blade" variants)
- **CombatNarrator** (`reasoning/CombatNarrator.kt`)
  - narrateAction() - New method for single action narration with caching
  - Flow: Try cache → LLM fallback on miss → Store response in cache
  - Equipment-aware descriptions (includes actual weapon/armor names)
  - Maintains backward compatibility with existing narrateCombatRound()
- **Performance benefits**:
  - Target >60% cache hit rate reduces LLM calls
  - Cached narratives return in <50ms vs. 1-3sec for live LLM
  - Cost savings: $0 for cached vs. ~$0.001 per LLM generation
  - Growing cache improves over time as more scenarios are stored

### Death & Corpse System
- **Entity.Corpse** - New entity type for dead entities (`core/Entity.kt`)
  - Contents: List<Item> for loot retrieval
  - DecayTimer: Ticks until despawn (100 for NPCs, 200 for players)
  - tick() method decrements timer, returns null when expired
  - removeItem() method for looting items
- **DeathHandler** (`reasoning/combat/DeathHandler.kt`)
  - Handles entity death and corpse creation
  - NPC death: Creates corpse with basic description
  - Player death: Creates corpse with full inventory + equipped items
  - DeathResult sealed class for type-safe handling (NPCDeath, PlayerDeath)
  - shouldDie() checks if entity health <= 0
- **CorpseDecayManager** (`reasoning/combat/CorpseDecayManager.kt`)
  - Manages corpse decay over time
  - tickDecay() processes all corpses, decrements timers
  - Removes expired corpses from rooms
  - 30% chance per item to drop to room on decay
  - Remaining items are destroyed
- **Persistence** - Corpses table in CombatDatabase
  - SQLite schema with id, name, location, contents (JSON), decay_timer
  - Indices on location_room_id and decay_timer for efficient queries

## File Locations (Important)

### Main Application
- `app/src/main/kotlin/com/jcraw/app/App.kt` - Entry point and game loops
- `app/src/main/kotlin/com/jcraw/app/handlers/` - Intent handlers (~661 lines total)
  - `MovementHandlers.kt` - Navigation and exploration (~173 lines)
  - `ItemHandlers.kt` - Inventory and equipment (~350 lines)
  - `CombatHandlers.kt` - Combat system (~138 lines)

### Multi-User Architecture
- `core/src/main/kotlin/com/jcraw/mud/core/Room.kt` - PlayerId type alias
- `core/src/main/kotlin/com/jcraw/mud/core/WorldState.kt` - Multi-player world state
- `core/src/main/kotlin/com/jcraw/mud/core/PlayerState.kt` - Per-player state
- `app/src/main/kotlin/com/jcraw/app/GameServer.kt` - Thread-safe game server
- `app/src/main/kotlin/com/jcraw/app/PlayerSession.kt` - Player I/O handling
- `app/src/main/kotlin/com/jcraw/app/GameEvent.kt` - Event broadcasting

### LLM Generators (RAG-enhanced)
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/RoomDescriptionGenerator.kt`
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/NPCInteractionGenerator.kt`
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/CombatNarrator.kt`

### Memory/RAG System
- `memory/src/main/kotlin/com/jcraw/mud/memory/MemoryManager.kt`
- `memory/src/main/kotlin/com/jcraw/mud/memory/VectorStoreInterface.kt`
- `memory/src/main/kotlin/com/jcraw/mud/memory/VectorStore.kt` - In-memory
- `memory/src/main/kotlin/com/jcraw/mud/memory/PersistentVectorStore.kt` - Disk-based
- `memory/src/main/kotlin/com/jcraw/mud/memory/MemoryEntry.kt`
- `llm/src/main/kotlin/com/jcraw/sophia/llm/OpenAIClient.kt`

### Persistence
- `memory/src/main/kotlin/com/jcraw/mud/memory/PersistenceManager.kt`

### Combat System
- `core/src/main/kotlin/com/jcraw/mud/core/CombatState.kt`
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/CombatResolver.kt`

### Skill System
- `core/src/main/kotlin/com/jcraw/mud/core/Entity.kt` - Stats
- `core/src/main/kotlin/com/jcraw/mud/core/SkillCheck.kt`
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/SkillCheckResolver.kt`

### Quest System
- `core/src/main/kotlin/com/jcraw/mud/core/Quest.kt` - Data models
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/procedural/QuestGenerator.kt`
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/QuestTracker.kt`

### Procedural Generation
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/procedural/DungeonTheme.kt`
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/procedural/RoomGenerator.kt`
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/procedural/NPCGenerator.kt` - Creates NPCs with SocialComponents
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/procedural/ItemGenerator.kt`
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/procedural/DungeonLayoutGenerator.kt`
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/procedural/ProceduralDungeonBuilder.kt`

### Social System
- `core/src/main/kotlin/com/jcraw/mud/core/Component.kt` - Component system foundation
- `core/src/main/kotlin/com/jcraw/mud/core/SocialComponent.kt` - Social component data model
- `core/src/main/kotlin/com/jcraw/mud/core/SocialEvent.kt` - Social event types
- `memory/src/main/kotlin/com/jcraw/mud/memory/social/SocialDatabase.kt` - SQLite database
- `memory/src/main/kotlin/com/jcraw/mud/memory/social/KnowledgeRepository.kt` - Knowledge persistence
- `memory/src/main/kotlin/com/jcraw/mud/memory/social/SocialEventRepository.kt` - Event persistence
- `memory/src/main/kotlin/com/jcraw/mud/memory/social/SocialComponentRepository.kt` - Component persistence
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/social/DispositionManager.kt` - Disposition tracking
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/social/EmoteHandler.kt` - Emote processing
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/social/NPCKnowledgeManager.kt` - Knowledge queries
- `perception/src/main/kotlin/com/jcraw/mud/perception/Intent.kt` - Emote and AskQuestion intents

### Item System V2
- **Core components** (`core/src/main/kotlin/com/jcraw/mud/core/`)
  - `InventoryComponent.kt` - Weight-limited inventory with 12 equip slots
  - `TradingComponent.kt` - Merchant trading with finite gold and stock
  - `ItemTemplate.kt` - Shared item definitions
  - `ItemInstance.kt` - Item instances with quality, charges, quantity
  - `ItemType.kt`, `EquipSlot.kt`, `Rarity.kt` - Item type enums
  - `LootTable.kt` - Weighted loot table system
  - `Recipe.kt` - Crafting recipe data model
- **Repositories** (`core/src/main/kotlin/com/jcraw/mud/core/repository/`)
  - `ItemRepository.kt`, `InventoryRepository.kt`, `RecipeRepository.kt`, `TradingRepository.kt`
- **Database** (`memory/src/main/kotlin/com/jcraw/mud/memory/item/`)
  - `ItemDatabase.kt` - SQLite schema (5 tables)
  - `SQLiteItemRepository.kt`, `SQLiteInventoryRepository.kt`, `SQLiteRecipeRepository.kt`, `SQLiteTradingRepository.kt`
- **Loot system** (`reasoning/src/main/kotlin/com/jcraw/mud/reasoning/loot/`)
  - `LootGenerator.kt` - Item generation from loot tables
  - `LootTableRegistry.kt` - Predefined loot tables
- **Crafting** (`reasoning/src/main/kotlin/com/jcraw/mud/reasoning/crafting/`)
  - `CraftingManager.kt` - Recipe matching and skill checks
- **Trading** (`reasoning/src/main/kotlin/com/jcraw/mud/reasoning/trade/`)
  - `TradeHandler.kt` - Buy/sell logic with disposition modifiers
- **Pickpocketing** (`reasoning/src/main/kotlin/com/jcraw/mud/reasoning/pickpocket/`)
  - `PickpocketHandler.kt` - Stealth/Agility checks vs Perception
- **Item uses** (`reasoning/src/main/kotlin/com/jcraw/mud/reasoning/items/`)
  - `ItemUseHandler.kt` - Multipurpose uses via tags
- **Skills integration** (`reasoning/src/main/kotlin/com/jcraw/mud/reasoning/skills/`)
  - `CapacityCalculator.kt` - Strength-based capacity calculation
  - `SkillModifierCalculator.kt` - Equipped item bonuses
- **Inventory management** (`reasoning/src/main/kotlin/com/jcraw/mud/reasoning/inventory/`)
  - `InventoryManager.kt` - High-level inventory operations
- **Resources**
  - `memory/src/main/resources/item_templates.json` - 53 item templates
  - `memory/src/main/resources/recipes.json` - 24 crafting recipes
- **Handlers** (`app/src/main/kotlin/com/jcraw/app/handlers/`)
  - `ItemHandlers.kt`, `TradeHandlers.kt`, `PickpocketHandlers.kt`, `ItemUseHandlers.kt`
  - `SkillQuestHandlers.kt` - Gathering (handleInteract) and crafting (handleCraft)
- **Intents** (`perception/src/main/kotlin/com/jcraw/mud/perception/Intent.kt`)
  - Craft, Trade, Pickpocket, UseItem intents

### World Generation System V2/V3 (Chunks 1-6 Complete, V3 Chunk 3 Complete)
- **Component-based architecture** for hierarchical world generation (5 levels)
  - `WorldChunkComponent` - Chunk hierarchy (WORLD → REGION → ZONE → SUBZONE → SPACE) with lore inheritance (`core`)
  - `SpacePropertiesComponent` - Space details (description, exits, traps, resources, entities, state flags) (`core`)
  - `GraphNodeComponent` - Pre-generated graph topology for V3 navigation (7 node types: HUB, LINEAR, BRANCHING, DEAD_END, BOSS, FRONTIER, QUESTABLE) (`core`)
  - `GraphTypes.kt` - NodeType sealed class and EdgeData with hidden exits and conditions (`core/world`)
  - `ChunkLevel.kt`, `TerrainType.kt`, `ExitData.kt`, `TrapData.kt`, `ResourceNode.kt`, `GenerationContext.kt`, `NavigationState.kt`, `WorldAction.kt` (`core/world`)
- **Database persistence** - SQLite schema with 4 tables (`memory/world/WorldDatabase.kt`)
  - world_seed (singleton with global lore), world_chunks, graph_nodes (V3), space_properties
  - JSON serialization for complex fields (neighbors, exits, traps, resources, entities, stateFlags)
  - graph_nodes stores pre-generated topology (id, chunk_id, position_x, position_y, type, neighbors as JSON)
- **Repositories** (`core/repository/`, `memory/world/`)
  - `WorldChunkRepository.kt`, `SpacePropertiesRepository.kt`, `WorldSeedRepository.kt`, `GraphNodeRepository.kt` (V3)
  - `SQLiteWorldChunkRepository.kt`, `SQLiteSpacePropertiesRepository.kt`, `SQLiteWorldSeedRepository.kt`, `SQLiteGraphNodeRepository.kt` (V3)
- **Generation pipeline** (`reasoning/world/`)
  - `WorldGenerator.kt` - Primary generation engine with LLM integration (567 lines)
    - **V3 Chunk 5 UPDATE (Generation Layer Complete)**:
      - `generateChunk()` (lines 48-85) - Now generates graph topology at SUBZONE level before content
      - `generateGraphTopology()` (lines 96-127) - Creates validated graph using GraphGenerator, seeded RNG
      - Returns `ChunkGenerationResult` with chunk + graph nodes (empty list for V2 mode or non-SUBZONE levels)
    - **V3 Lazy-Fill System**:
      - `generateSpaceStub()` (lines 211-252) - Creates SpacePropertiesComponent with empty description for on-demand generation
      - `fillSpaceContent()` (lines 264-287) - On-demand LLM generation when player enters space for first time
      - `generateNodeDescription()` (lines 293-341) - LLM generates description using node type, neighbors, chunk lore
      - `determineNodeProperties()` (lines 343-360) - Node type-based brightness/terrain (Hub=bright/safe, Frontier=dark/difficult)
    - **V2 Methods (Still Active)**:
      - `generateSpace()` (lines 139-200) - V2 immediate generation for existing dungeons
      - Supports both V2 (immediate content) and V3 (graph-first lazy-fill) modes in parallel
    - **Integration Status**: Generation layer complete, movement handler integration pending (see TODO.md)
  - `GraphToRoomAdapter.kt` - **V3 Chunk 5 Adapter Layer (Option B: Parallel Systems)**
    - Converts V3 components (GraphNodeComponent + SpacePropertiesComponent) to V2 Room format
    - `toRoom()` - Single space conversion with name extraction, trait building, exit mapping
    - `toRooms()` - Batch conversion for chunk-level operations
    - Cardinal direction mapping (Direction enum), non-cardinal exits stored in properties
    - Trait generation from brightness, terrain, node type, features (traps/resources/safe zones)
    - Properties map preserves V3 metadata for potential future use
    - Tested with 16 comprehensive tests covering edge cases and data integrity
    - **Design Philosophy**: KISS principle - additive, non-disruptive, allows gradual ECS migration
  - `LoreInheritanceEngine.kt` - Lore variation and theme blending
  - `DungeonInitializer.kt` - Deep dungeon MVP starter (3 regions: Upper/Mid/Lower Depths)
  - `ChunkIdGenerator.kt` - Hierarchical ID generation (level_parent_uuid format)
- **Graph generation (V3)** (`reasoning/worldgen/`)
  - `GraphLayout.kt` - Sealed class for algorithm selection (Grid, BSP, FloodFill)
    - Grid: Regular NxM rectangular layout for dungeons, buildings
    - BSP: Binary space partitioning for hierarchical rooms, temples
    - FloodFill: Organic growth for caves, natural formations
    - Helper methods: forBiome(), forNodeCount() for automatic selection
  - `GraphGenerator.kt` - Core topology generation engine
    - Three layout algorithms: generateGridNodes(), generateBSPNodes(), generateFloodFillNodes()
    - Kruskal MST for connectivity (ensures all nodes reachable)
    - Add 20% extra edges for loops (creates exploration choices)
    - Node type assignment: 1-2 Hubs, 1 Boss, 2+ Frontiers, 20% Dead-ends
    - Hidden edge marking: 15-25% edges marked with Perception DC 10-30
    - Bidirectional edges with cardinal direction labels (N/S/E/W)
  - `GraphValidator.kt` - Post-generation validation (Chunk 4, pending)
- **Exit system** (`reasoning/world/`)
  - `ExitResolver.kt` - Three-phase resolution (exact → fuzzy → LLM)
  - `ExitLinker.kt` - Links placeholder exits and creates reciprocal paths
  - `MovementCostCalculator.kt` - Terrain-based movement costs and damage
- **Content placement** (`reasoning/world/`)
  - `ThemeRegistry.kt` - 8 predefined biome themes with content rules
  - `TrapGenerator.kt` - Probabilistic trap generation (~15%)
  - `ResourceGenerator.kt` - Resource node generation (~5%) tied to ItemRepository
  - `MobSpawner.kt` - LLM-driven entity generation with fallback
  - `LootTableGenerator.kt` - Procedural loot tables from item pool
  - `SpacePopulator.kt` - Orchestrates trap/resource/mob placement
- **State management** (`reasoning/world/`, `memory/world/`)
  - `StateChangeHandler.kt` - WorldAction processing (destroy, trigger, harvest, unlock, etc.)
  - `WorldPersistence.kt` - Save/load with incremental autosave and prefetch
  - `RespawnManager.kt` - Mob regeneration preserving player changes
  - `AutosaveManager.kt` - Periodic autosave (every 5 moves or 2 minutes)
  - `GenerationCache.kt` - LRU cache (1000 chunks) for performance
- **Testing** - 576+ unit/integration tests across V2 chunks 1-6, comprehensive tests for V3 GraphGenerator and GraphLayout
- **Status** - V2 foundation complete, V3 graph generation (Chunk 3) complete
- See [World Generation Documentation](./WORLD_GENERATION.md) for complete details

### Sample Content
- `core/src/main/kotlin/com/jcraw/mud/core/SampleDungeon.kt`

### Intent System
- `perception/src/main/kotlin/com/jcraw/mud/perception/Intent.kt` - 22+ intent types including Emote and AskQuestion

### Client (GUI)
- `client/src/main/kotlin/com/jcraw/mud/client/Main.kt` - Entry point
- `client/src/main/kotlin/com/jcraw/mud/client/GameViewModel.kt` - State management
- `client/src/main/kotlin/com/jcraw/mud/client/EngineGameClient.kt` - Engine integration (~311 lines)
- `client/src/main/kotlin/com/jcraw/mud/client/handlers/` - Intent handlers (~1400 lines total)
  - `ClientMovementHandlers.kt` - Navigation and exploration (~250 lines)
  - `ClientItemHandlers.kt` - Inventory and equipment (~300 lines)
  - `ClientCombatHandlers.kt` - Combat system (~140 lines)
  - `ClientSocialHandlers.kt` - Social interactions (~230 lines)
  - `ClientSkillQuestHandlers.kt` - Skills, quests, persistence, meta-commands (~480 lines)
- `client/src/main/kotlin/com/jcraw/mud/client/ui/` - UI screens

### Test Bot
- `testbot/src/main/kotlin/com/jcraw/mud/testbot/OutputValidator.kt` - Main orchestrator (~65 lines)
- `testbot/src/main/kotlin/com/jcraw/mud/testbot/validation/` - Validation logic (~1050 lines total)
  - `CodeValidationRules.kt` - Deterministic code validation (~680 lines)
  - `ValidationPrompts.kt` - LLM prompt builders (~460 lines)
  - `ValidationParsers.kt` - Response parsing (~40 lines)

## Design Notes

- **No backward compatibility needed** - Can wipe and restart data between versions
- **Project follows guidelines** in `CLAUDE_GUIDELINES.md`
- **Requirements** are in `docs/requirements.txt`
