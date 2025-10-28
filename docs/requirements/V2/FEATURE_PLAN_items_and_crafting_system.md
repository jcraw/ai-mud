# Items, Inventory, and Crafting System - Implementation Plan

## Overview

This plan breaks down the implementation of a flexible, multipurpose item system with weight-based inventory management, gathering, crafting, trading, and pickpocketing. The system integrates with existing ECS architecture, skills, combat V2, and social systems.

**Key Design Principles:**
- ECS component-based architecture (InventoryComponent, TradingComponent)
- Hybrid tag-based + LLM system for multipurpose items (e.g., pot as weapon/container/trap)
- Weight-limited inventory derived from Strength skill
- Finite, depletable gathering nodes (no respawn in V2)
- Realistic corpse loot (80% equipped gear, RNG for extras)
- Merchant finite gold constraints
- Combat equip penalty (time cost + agility debuff)
- Pickpocket consequences (disposition drop, wariness status)

**Total Estimated Time:** 24 hours across 10 chunks

---

## Chain-of-Thought Analysis

### Dependencies
1. **Existing Systems:**
   - ECS (core): Component, Entity, WorldState
   - Skills V2: Skill levels, XP awards, perks
   - Combat V2: Turn queue, damage calculation, corpse creation
   - Social: Disposition, NPC interactions
   - Persistence: SQLite repositories

2. **Integration Points:**
   - **Combat**: Equip time cost integrates with turn queue; item bonuses modify damage/defense
   - **Skills**: Strength determines capacity; gathering/crafting award XP
   - **Social**: Disposition affects trade prices; pickpocket failures damage relationships
   - **World**: Room descriptions include gathering nodes (skill-filtered)
   - **Memory**: RAG recalls creative item uses

### Challenges & Solutions
1. **Multipurpose Items:**
   - Challenge: Flexible uses without brittle hardcoding
   - Solution: Tags (code-enforced, e.g., "blunt" → improvised weapon) + LLM intent parsing (emergent, e.g., "place dynamite in pocket" → pickpocket + explosive tag → timed detonation)

2. **Weight Management:**
   - Challenge: Prevent unrealistic hoarding
   - Solution: Capacity = Strength level * 5kg + bag bonuses; weight checks on add/equip

3. **Finite Resources:**
   - Challenge: Prevent infinite farming
   - Solution: Gathering nodes with quantity field; deplete to 0 (no respawn in V2)

4. **Realistic Loot:**
   - Challenge: Avoid loot explosion while maintaining variety
   - Solution: 80% equipped gear + gold; RNG table for 1-2 extras (rarity-weighted)

5. **Combat Equip Penalty:**
   - Challenge: Prevent mid-combat gear swapping
   - Solution: Equip action costs 20 ticks + Agility -20 penalty for 2 turns; grants free enemy action

### Sequencing Rationale
1. **Foundation First:** Components/enums/data model (Chunk 1) before any logic
2. **Persistence Early:** Database schema (Chunk 2) enables testing with real data
3. **Core Before Advanced:** Inventory (Chunk 3) before loot/gathering/crafting
4. **Dependencies Respected:** Loot (Chunk 4) needs inventory; crafting (Chunk 6) needs gathering (Chunk 5)
5. **Integration Last:** Skills/combat ties (Chunk 9) after all systems exist
6. **Documentation Final:** Comprehensive docs (Chunk 10) reflect completed state

---

## TODO: Implementation Chunks

### Chunk 1: Core Components & Data Model (Foundation)
**Estimated Time:** 3 hours

**Description:**
Create the foundational data structures for the item system: components, templates, instances, enums. Implement core methods for weight calculation, capacity checks, and basic inventory operations.

**Affected Modules/Files:**
- **:core/src/main/kotlin/com/jcraw/mud/core/components/**
  - `InventoryComponent.kt` (new) - Inventory with items, equipped, gold, capacity
  - `ItemTemplate.kt` (new) - Template definitions from DB
  - `ItemInstance.kt` (new) - Instance references template with quality/charges
- **:core/src/main/kotlin/com/jcraw/mud/core/enums/**
  - `ItemType.kt` (new) - WEAPON, ARMOR, CONSUMABLE, RESOURCE, QUEST, TOOL, CONTAINER, SPELL_BOOK, SKILL_BOOK
  - `EquipSlot.kt` (new) - HANDS_MAIN, HANDS_OFF, HEAD, CHEST, LEGS, FEET, BACK, HANDS_BOTH, ACCESSORY_1..4
  - `Rarity.kt` (new) - COMMON, UNCOMMON, RARE, EPIC, LEGENDARY
- **:core/src/main/kotlin/com/jcraw/mud/core/ComponentType.kt**
  - Add INVENTORY to enum

**Key Technical Decisions:**
1. **Immutable data classes:** All components return new copies on mutation (aligns with existing ECS pattern)
   - Rationale: Thread-safe, testable, fits existing WorldState.copy() pattern
2. **Separated template/instance:** Templates are shared DB definitions; instances reference templates
   - Rationale: Memory-efficient (50+ templates vs 1000s instances); enables future procedural generation
3. **Weight formula in templates:** `properties["weight"]` as Double
   - Rationale: Flexible for future item types; supports decimal weights (e.g., potion 0.5kg)
4. **Capacity from Strength:** Base = Strength level * 5kg
   - Rationale: Scales with character progression; aligns with skill system
5. **Tags as List<String>:** 5-10 tags per item (e.g., "blunt", "metal", "flammable")
   - Rationale: Enables hybrid rule-based + LLM multipurpose checks without rigid schemas

**Testing Approach:**
- **Unit tests (InventoryComponentTest.kt, ~25 tests):**
  - `currentWeight()` calculation (empty, stacked items, equipped items)
  - `canAdd()` weight limit enforcement (over/under capacity)
  - `equip()` slot validation (2H weapon clears off-hand, can't equip without item in inventory)
  - `addGold()` / `removeGold()` with finite limits
  - `augmentCapacity()` with bags/perks
  - Rationale: Weight logic is foundational; bugs cascade to all systems
- **Property-based tests:**
  - Random item additions respect capacity constraint
  - Equipping always maintains invariant: equipped items are subset of inventory

**Documentation:**
- Inline KDoc for all public methods
- Document weight formula, capacity calculation, slot rules

---

### Chunk 2: Database Schema & Repositories
**Estimated Time:** 4 hours

**Description:**
Create SQLite schema for item persistence: templates, inventories, instances. Implement repository interfaces and SQLite implementations. Preload 50+ item templates (weapons, armor, consumables, resources, tools) for testing and gameplay.

**Affected Modules/Files:**
- **:memory/src/main/kotlin/com/jcraw/mud/memory/persistence/**
  - `ItemDatabase.kt` (new) - Schema definitions (item_templates, inventories, item_instances)
  - `ItemRepository.kt` (new, :core) - Interface for template CRUD, findByTag/Rarity
  - `SQLiteItemRepository.kt` (new) - Implementation using JDBC
  - `InventoryRepository.kt` (new, :core) - Interface for inventory save/load
  - `SQLiteInventoryRepository.kt` (new) - Implementation
- **:memory/src/main/resources/**
  - `item_templates.json` (new) - Preloaded template data (50+ items)

**Key Technical Decisions:**
1. **Separate database:** ItemDatabase vs extending CombatDatabase
   - Rationale: Combat DB is 132 lines; item system is large (7 tables); separation aids modularity
   - Decision: Create ItemDatabase.kt, follow CombatDatabase pattern
2. **JSON columns for tags/properties:** TEXT columns with Gson serialization
   - Rationale: Flexible schema; SQLite lacks native JSON/array types; aligns with existing patterns
3. **Template preloading:** JSON file loaded at DB init
   - Rationale: 50+ items tedious to hardcode; JSON enables easy editing/expansion
4. **Inventory as entity component:** inventories table with entity_id FK
   - Rationale: Players, NPCs, merchants all have inventories; ECS pattern
5. **Separate item_instances table:** Track ownership, quality, charges
   - Rationale: Future multiplayer needs instance tracking; enables quest item flags

**Schema:**
```sql
CREATE TABLE item_templates (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    type TEXT NOT NULL,  -- ItemType enum
    tags TEXT NOT NULL,  -- JSON array
    properties TEXT NOT NULL,  -- JSON map
    rarity TEXT NOT NULL,  -- Rarity enum
    description TEXT NOT NULL,
    equip_slot TEXT  -- EquipSlot enum or null
);

CREATE TABLE inventories (
    entity_id TEXT PRIMARY KEY,
    items TEXT NOT NULL,  -- JSON List<ItemInstance>
    equipped TEXT NOT NULL,  -- JSON Map<EquipSlot, ItemInstance>
    gold INTEGER NOT NULL,
    capacity_weight REAL NOT NULL
);

CREATE TABLE item_instances (
    id TEXT PRIMARY KEY,
    template_id TEXT NOT NULL,
    owner_entity_id TEXT,
    quality INTEGER NOT NULL,
    charges INTEGER,
    FOREIGN KEY (template_id) REFERENCES item_templates(id)
);

CREATE INDEX idx_inventories_entity ON inventories(entity_id);
```

**Testing Approach:**
- **Integration tests (ItemDatabaseTest.kt, ~20 tests):**
  - Template save/load roundtrip (with tags, properties JSON)
  - Inventory save/load with equipped items
  - findByTag() returns correct templates (e.g., "sharp" weapons)
  - findByRarity() filtering (RARE+ only)
  - Preloaded templates exist (count >= 50, specific items like "Iron Sword" present)
  - Rationale: DB schema bugs cause data corruption; roundtrip tests prove correctness
- **Repository tests (SQLiteInventoryRepositoryTest.kt, ~15 tests):**
  - Update equipped items
  - Update gold (finite limits for NPCs)
  - Weight capacity updates
  - Rationale: Repository layer is integration boundary; must handle edge cases

**Preloaded Items (examples):**
- **Weapons:** Iron Sword, Steel Axe, Wooden Club, Longbow, Dagger (tags: "sharp", "slashing", "metal")
- **Armor:** Leather Armor, Chain Mail, Iron Helm (tags: "protective", "metal", "heavy")
- **Consumables:** Health Potion, Mana Potion, Antidote (tags: "edible", "liquid", "restorative")
- **Resources:** Iron Ore, Wood Log, Honey, Herbs (tags: "mineable", "flammable", "sweet", "medicinal")
- **Tools:** Pickaxe, Axe, Fishing Rod (tags: "mining_tool", "chopping_tool", "fishing_tool")
- **Containers:** Leather Bag, Large Backpack (tags: "container", "wearable")
- **Quest:** Ancient Key, Dragon Egg (tags: "quest_item", "non_droppable")
- **Multipurpose:** Clay Pot (tags: "container", "blunt", "fragile"), Dynamite (tags: "explosive", "throwable", "timed")

**Documentation:**
- Schema diagram in docs
- Repository interface contracts (KDoc)

---

### Chunk 3: Inventory Management & Equipping
**Estimated Time:** 4 hours

**Description:**
Implement inventory action handlers: add/remove items, equip/unequip, drop, basic loot (corpse). Integrate combat equip penalty (20 tick cost + Agility -20 for 2 turns). Add new intents to perception layer with LLM parsing.

**Affected Modules/Files:**
- **:perception/src/main/kotlin/com/jcraw/mud/perception/**
  - `Intent.kt` - Add Equip, Drop, Loot sealed classes
  - `IntentParser.kt` - LLM parsing for "equip sword", "drop 5 ore", "loot goblin corpse"
- **:reasoning/src/main/kotlin/com/jcraw/mud/reasoning/inventory/**
  - `InventoryManager.kt` (new) - Core add/remove/equip logic with weight checks
  - `EquipHandler.kt` (new) - Slot validation, combat penalty calculation
- **:app/src/main/kotlin/com/jcraw/app/handlers/**
  - `InventoryHandlers.kt` (new) - Handle Equip, Drop, Loot intents
- **:core/src/main/kotlin/com/jcraw/mud/core/Entity.kt**
  - Update Corpse to include InventoryComponent (for looting)

**Key Technical Decisions:**
1. **Combat equip penalty:** Equip in combat costs 20 ticks + Agility -20 for 2 turns
   - Rationale: Prevents gear-swap abuse; aligns with turn queue system; Agility affects dodge/initiative
   - Implementation: Check if enemies in room with hostile disposition; if yes, apply penalties and advance turn queue
2. **Weight checks on add:** Fail gracefully with message "Too heavy to carry!"
   - Rationale: Prevents inventory bloat; encourages strategic choices
3. **Equipped items also in inventory:** `inventory.items` contains all; `equipped` is Map reference
   - Rationale: Simplifies unequip (no need to transfer); equip = move reference not item
   - Alternative considered: Separate lists (rejected due to duplication complexity)
4. **Slot validation:** 2H weapon (HANDS_BOTH) clears HANDS_OFF
   - Rationale: Realistic; prevents dual-wielding 2H + shield
5. **Drop creates entity:** Entity.Item with ItemComponent in current room
   - Rationale: Items on ground are world objects; can be looted by others (multiplayer future)

**Testing Approach:**
- **Unit tests (InventoryManagerTest.kt, ~20 tests):**
  - Add item success/failure (weight limit)
  - Equip item to slot (success, already equipped, slot conflict)
  - Equip 2H weapon clears off-hand
  - Remove item reduces weight
  - Rationale: Core inventory logic; bugs break all item interactions
- **Integration tests (InventoryHandlersTest.kt, ~15 tests):**
  - Equip in combat applies penalty (check turn queue, agility debuff)
  - Equip out of combat no penalty
  - Drop creates Entity.Item in room
  - Loot corpse transfers items to player inventory (weight check)
  - Loot empty corpse ("Nothing to loot")
  - Rationale: Integration points (turn queue, world state) have subtle bugs; full flow tests catch them

**Documentation:**
- Update command reference with equip/drop/loot syntax
- Document combat equip penalty mechanics

---

### Chunk 4: Loot Generation System
**Estimated Time:** 3 hours

**Description:**
Implement loot table schema, LootGenerator for corpse-based drops with rarity-weighted RNG. Integrate with DeathHandler to populate corpse inventories. Avoid loot explosion (80% equipped gear + 1-2 extras max).

**Affected Modules/Files:**
- **:memory/src/main/kotlin/com/jcraw/mud/memory/persistence/**
  - `ItemDatabase.kt` - Add loot_tables schema
  - `LootRepository.kt` (new, :core) - Interface for loot table queries
  - `SQLiteLootRepository.kt` (new) - Implementation
- **:reasoning/src/main/kotlin/com/jcraw/mud/reasoning/loot/**
  - `LootGenerator.kt` (new) - Generate drops from loot tables with RNG
  - `LootTable.kt` (new) - Data class for loot rules
- **:reasoning/src/main/kotlin/com/jcraw/mud/reasoning/combat/**
  - `DeathHandler.kt` - Integrate loot generation on NPC death
- **:memory/src/main/resources/**
  - `loot_tables.json` (new) - Preloaded loot rules for mob types

**Key Technical Decisions:**
1. **Corpse loot = equipped (80%) + gold + RNG extras (0-2):**
   - Rationale: Realistic (NPCs wear gear); prevents loot explosion; rarity adds variety
   - Implementation: DeathHandler checks NPC's equipped items, rolls each at 80% drop chance; then rolls loot table 0-2 times (weighted by rarity)
2. **Rarity weights:** COMMON 70%, UNCOMMON 25%, RARE 4%, EPIC 0.9%, LEGENDARY 0.1%
   - Rationale: Rare upgrades feel rewarding; balance progression
3. **Loot table by mob type:** Goblin → low-tier (COMMON/UNCOMMON), Dragon → high-tier (RARE+)
   - Rationale: Scales challenge = reward; aligns with future procedural mobs
4. **Seeded RNG for tests:** Kotlin Random with seed parameter
   - Rationale: Deterministic tests for loot generation (avoid flaky tests)
5. **Gold ranges in loot table:** min_gold, max_gold per mob type
   - Rationale: Variable rewards; prevents predictable farming

**Schema:**
```sql
CREATE TABLE loot_tables (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    mob_type TEXT NOT NULL,
    rarity TEXT NOT NULL,
    item_template_id TEXT NOT NULL,
    drop_chance REAL NOT NULL,  -- 0.0 to 1.0
    min_quantity INTEGER NOT NULL,
    max_quantity INTEGER NOT NULL,
    FOREIGN KEY (item_template_id) REFERENCES item_templates(id)
);

CREATE INDEX idx_loot_mob_type ON loot_tables(mob_type);
```

**Testing Approach:**
- **Unit tests (LootGeneratorTest.kt, ~18 tests):**
  - Seeded RNG produces expected drops (e.g., seed 42 → Iron Sword, 10 gold)
  - Rarity distribution matches weights over 10,000 rolls (chi-squared test)
  - Equipped gear 80% drop chance (1000 trials, ~800 drops)
  - Max 2 extra items per corpse
  - Empty loot table → no drops
  - Rationale: RNG is notoriously buggy; statistical tests prove correctness; seeded tests ensure reproducibility
- **Integration tests (DeathHandlerTest.kt additions, ~8 tests):**
  - NPC death populates corpse with equipped sword (80% chance)
  - Goblin corpse has 0-2 loot table items (COMMON/UNCOMMON)
  - Dragon corpse includes RARE+ item (if rolled)
  - Corpse gold matches NPC's inventory gold + loot table roll
  - Rationale: DeathHandler integration is critical; loot must appear on corpses for looting

**Preloaded Loot Tables (examples):**
- **Goblin:** 70% COMMON (Rusty Dagger, Tattered Cloth), 30% UNCOMMON (Iron Sword), 5-20 gold
- **Skeleton:** 60% COMMON (Bone Fragment), 40% UNCOMMON (Steel Axe), 0-10 gold
- **Dragon:** 50% RARE (Dragon Scale Armor), 30% EPIC (Flaming Sword), 20% LEGENDARY (Dragon Heart), 100-500 gold

**Documentation:**
- Loot table format (JSON schema)
- Rarity weights explanation

---

### Chunk 5: Gathering System
**Estimated Time:** 4 hours

**Description:**
Implement finite gathering nodes (mineable ore, herbs, trees) with skill-based spotting, tool requirements, and depletion. Integrate with room descriptions (LLM includes nodes if skill >= threshold). Add Intent.Gather and GatheringHandler with skill checks.

**Affected Modules/Files:**
- **:memory/src/main/kotlin/com/jcraw/mud/memory/persistence/**
  - `ItemDatabase.kt` - Add gathering_nodes schema
  - `GatheringRepository.kt` (new, :core) - Interface for node CRUD, harvest
  - `SQLiteGatheringRepository.kt` (new) - Implementation
- **:perception/src/main/kotlin/com/jcraw/mud/perception/**
  - `Intent.kt` - Add Gather sealed class
  - `IntentParser.kt` - Parse "mine ore", "chop tree", "gather herbs"
- **:reasoning/src/main/kotlin/com/jcraw/mud/reasoning/gathering/**
  - `GatheringHandler.kt` (new) - Skill checks, tool validation, node depletion
  - `GatheringNode.kt` (new) - Data class for nodes
- **:reasoning/src/main/kotlin/com/jcraw/mud/reasoning/world/**
  - `RoomDescriptionGenerator.kt` - Integrate node hints (skill-filtered)
- **:app/src/main/kotlin/com/jcraw/app/handlers/**
  - `GatheringHandlers.kt` (new) - Handle Gather intent

**Key Technical Decisions:**
1. **Finite nodes:** quantity field decrements to 0; no respawn in V2
   - Rationale: Prevents infinite farming; encourages exploration; aligns with realistic dungeon
   - Alternative: Respawn over time (deferred to V3)
2. **Skill-based spotting:** Low skill → "odd rocks", high skill → "rich iron ore vein"
   - Rationale: Progression reward; low-level players may miss nodes; fits skill system
   - Implementation: LLM prompt includes node if player's Mining >= node.difficulty - 10
3. **Tool requirements:** Tag matching (e.g., "mining_tool" for ore, "chopping_tool" for trees)
   - Rationale: Prevents unrealistic gathering (mining with bare hands); encourages tool crafting/purchase
   - Fallback: Hands allowed for herbs/honey (no tool tag check)
4. **Skill check on harvest:** d20 + skill modifier vs node.difficulty
   - Rationale: Aligns with existing skill check pattern; failure can damage player or yield low quantity
5. **XP reward:** Full XP on success (skill progression), 20% on failure
   - Rationale: Incentivizes repeated gathering for skill growth

**Schema:**
```sql
CREATE TABLE gathering_nodes (
    id TEXT PRIMARY KEY,
    room_id TEXT NOT NULL,
    resource_template_id TEXT NOT NULL,
    quantity INTEGER NOT NULL,  -- Deplete to 0
    difficulty INTEGER NOT NULL,  -- Skill DC
    required_tool_tag TEXT,  -- "mining_tool", null for hands
    FOREIGN KEY (resource_template_id) REFERENCES item_templates(id)
);

CREATE INDEX idx_gathering_room ON gathering_nodes(room_id);
```

**Testing Approach:**
- **Unit tests (GatheringHandlerTest.kt, ~22 tests):**
  - Skill check success → add resource, reduce quantity
  - Skill check failure → no resource, 20% XP
  - Tool missing → failure ("You need a pickaxe to mine ore")
  - Node depleted (quantity 0) → failure ("The vein is exhausted")
  - Hands-allowed node (herbs) works without tool
  - Quantity decrement (5 → 4 → 3 → ... → 0)
  - Rationale: Gathering has many edge cases (tool, skill, quantity); must test all paths
- **Integration tests (RoomDescriptionGeneratorTest.kt additions, ~8 tests):**
  - Low Mining skill (5) → node not mentioned
  - High Mining skill (15) → "A rich iron ore vein sparkles in the wall"
  - Multiple nodes in room → all mentioned (if skill sufficient)
  - Depleted node (quantity 0) → not mentioned
  - Rationale: Room description integration is player-facing; bugs frustrate exploration

**Preloaded Nodes (examples):**
- **Crypt:** Herbs (medicinal), Honey (in alcove), difficulty 5-10
- **Cave:** Iron Ore (walls), difficulty 12, requires "mining_tool"
- **Forest:** Wood Logs (trees), difficulty 8, requires "chopping_tool"

**Documentation:**
- Gathering commands ("mine ore with pickaxe")
- Tool requirements table (resource → tool tag)

---

### Chunk 6: Crafting System
**Estimated Time:** 4 hours

**Description:**
Implement recipe-based crafting with skill checks, input consumption, and quality scaling. Add Intent.Craft, CraftingManager, and RecipeRepository. Preload 20+ recipes (weapons, armor, consumables). Support LLM-based ad-hoc crafting for emergent uses.

**Affected Modules/Files:**
- **:memory/src/main/kotlin/com/jcraw/mud/memory/persistence/**
  - `ItemDatabase.kt` - Add recipes schema
  - `RecipeRepository.kt` (new, :core) - Interface for recipe queries, findViable
  - `SQLiteRecipeRepository.kt` (new) - Implementation
- **:perception/src/main/kotlin/com/jcraw/mud/perception/**
  - `Intent.kt` - Add Craft sealed class
  - `IntentParser.kt` - Parse "craft iron sword", "mix healing potion"
- **:reasoning/src/main/kotlin/com/jcraw/mud/reasoning/crafting/**
  - `CraftingManager.kt` (new) - Recipe matching, skill checks, input consumption
  - `Recipe.kt` (new) - Data class for recipes
- **:app/src/main/kotlin/com/jcraw/app/handlers/**
  - `CraftingHandlers.kt` (new) - Handle Craft intent
- **:memory/src/main/resources/**
  - `recipes.json` (new) - Preloaded recipes

**Key Technical Decisions:**
1. **DB recipes vs LLM ad-hoc:**
   - DB: Predefined recipes (fast, predictable, balanced)
   - LLM: Emergent recipes (flexible, creative, slower)
   - Rationale: Hybrid approach; try DB first, fallback to LLM for novel combinations
   - Implementation: CraftingManager.findRecipe() checks DB; if none, calls LLM to generate ad-hoc recipe (then optionally cache in DB)
2. **Skill check on craft:** d20 + skill modifier vs recipe.difficulty
   - Rationale: Aligns with existing skill system; failure wastes some inputs (50%)
3. **Quality scaling:** quality = skill level / 10 (clamped 1-10)
   - Rationale: Progression reward; high-skill crafters make superior items; aligns with instance quality field
4. **Input consumption:** Exact match (e.g., 2 Iron Ore + 1 Wood) from inventory
   - Rationale: Prevents item duplication; encourages gathering
   - Edge case: Partial consumption on failure (50% returned)
5. **Tool requirements:** Some recipes need tools (e.g., Blacksmith Hammer for weapons)
   - Rationale: Adds depth; encourages tool acquisition; realistic

**Schema:**
```sql
CREATE TABLE recipes (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    input_items TEXT NOT NULL,  -- JSON Map<ItemId, Int>
    output_item TEXT NOT NULL,  -- JSON ItemInstance
    required_skill TEXT NOT NULL,
    min_skill_level INTEGER NOT NULL,
    required_tools TEXT,  -- JSON List<String> tags
    difficulty INTEGER NOT NULL
);

CREATE INDEX idx_recipes_skill ON recipes(required_skill);
```

**Testing Approach:**
- **Unit tests (CraftingManagerTest.kt, ~25 tests):**
  - Recipe match (exact inputs) → craft success
  - Missing inputs → failure ("You need 2 Iron Ore and 1 Wood")
  - Insufficient skill → failure ("Your Blacksmithing is too low")
  - Skill check success → output with quality = skill/10
  - Skill check failure → 50% input loss
  - Tool missing → failure ("You need a Blacksmith Hammer")
  - Ad-hoc LLM recipe generation (mock LLM)
  - Rationale: Crafting logic is complex (matching, checks, consumption); edge cases abound
- **Integration tests (CraftingHandlersTest.kt, ~12 tests):**
  - Full flow: gather ore → craft sword → equip → combat with +damage
  - Multiple crafts deplete resources
  - XP awarded to skill
  - Rationale: End-to-end flow proves all integrations work (gathering → crafting → equip → combat)

**Preloaded Recipes (examples):**
- **Iron Sword:** 2 Iron Ore + 1 Wood → Iron Sword (Blacksmithing 10, difficulty 12, requires "blacksmith_tool")
- **Health Potion:** 2 Herbs + 1 Honey → Health Potion (Alchemy 5, difficulty 8)
- **Leather Armor:** 3 Leather + 1 Thread → Leather Armor (Leatherworking 8, difficulty 10)
- **Dynamite:** 1 Sulfur + 1 Charcoal + 1 Cloth → Dynamite (Alchemy 15, difficulty 18)

**Documentation:**
- Recipe list (inputs → outputs)
- Crafting commands ("craft iron sword")
- Skill requirements table

---

### Chunk 7: Trading & TradingComponent
**Estimated Time:** 3 hours

**Description:**
Implement TradingComponent for merchants, trading_stocks schema, TradeHandler with finite gold constraints and disposition price modifiers. Add Intent.Trade for buy/sell interactions.

**Affected Modules/Files:**
- **:core/src/main/kotlin/com/jcraw/mud/core/components/**
  - `TradingComponent.kt` (new) - Merchant gold, stock, price mods
- **:core/src/main/kotlin/com/jcraw/mud/core/ComponentType.kt**
  - Add TRADING to enum
- **:memory/src/main/kotlin/com/jcraw/mud/memory/persistence/**
  - `ItemDatabase.kt` - Add trading_stocks schema
  - `TradingRepository.kt` (new, :core) - Interface for stock/gold updates
  - `SQLiteTradingRepository.kt` (new) - Implementation
- **:perception/src/main/kotlin/com/jcraw/mud/perception/**
  - `Intent.kt` - Add Trade sealed class
  - `IntentParser.kt` - Parse "buy health potion", "sell 3 iron ore"
- **:reasoning/src/main/kotlin/com/jcraw/mud/reasoning/trade/**
  - `TradeHandler.kt` (new) - Buy/sell logic with gold checks, disposition mods
- **:app/src/main/kotlin/com/jcraw/app/handlers/**
  - `TradeHandlers.kt` (new) - Handle Trade intent

**Key Technical Decisions:**
1. **Finite merchant gold:** TradingComponent.merchantGold (e.g., 500 for village merchant)
   - Rationale: Prevents infinite sell exploits; realistic economy; encourages selling to multiple merchants
   - Implementation: Buy from player checks merchantGold >= price; sell to player deducts player gold
2. **Disposition price modifier:** price = base * (1.0 + (disposition - 50) / 100)
   - Rationale: Integrates social system; friendly NPCs give discounts; hostile refuse trade
   - Example: Disposition 70 → 0.8x price (20% discount); disposition 30 → 1.2x price (20% markup)
3. **Merchants buy anything:** buyAnything = true for general merchants
   - Rationale: Flexible; prevents "This merchant doesn't buy that" frustration
   - Alternative: Specialist merchants (future enhancement)
4. **Stock depletion:** Buying removes from stock; selling adds to stock (finite)
   - Rationale: Prevents duplicate purchases; realistic; multiplayer-ready
5. **Price formula:** Base = template properties["value"] or calculated from rarity
   - Rationale: Centralized pricing; RARE items cost more; aligns with loot weights

**Schema:**
```sql
CREATE TABLE trading_stocks (
    entity_id TEXT PRIMARY KEY,
    merchant_gold INTEGER NOT NULL,
    stock TEXT NOT NULL,  -- JSON List<ItemInstance>
    buy_anything INTEGER NOT NULL,  -- 0 or 1 (boolean)
    price_mod_base REAL NOT NULL
);
```

**Testing Approach:**
- **Unit tests (TradeHandlerTest.kt, ~20 tests):**
  - Buy item success (player gold -= price, item added, stock removed, merchant gold += price)
  - Buy item failure (insufficient player gold)
  - Sell item success (player gold += price, item removed, merchant gold -= price, stock added)
  - Sell item failure (merchant gold insufficient)
  - Disposition price mod (70 → 0.8x, 30 → 1.2x)
  - Stock depletion (buy last item → stock empty)
  - Rationale: Trade has many failure modes (gold, stock, disposition); must test all paths
- **Integration tests (TradeHandlersTest.kt, ~10 tests):**
  - Full flow: loot sword → sell to merchant → buy health potion
  - Merchant gold depletion (sell until merchantGold = 0)
  - Disposition affects prices (persuade NPC → price drop)
  - Rationale: End-to-end trade flow proves all integrations (inventory, gold, disposition)

**Documentation:**
- Trade commands ("buy health potion", "sell 3 iron ore")
- Disposition price modifier table

---

### Chunk 8: Pickpocketing & Advanced Item Use
**Estimated Time:** 4 hours

**Description:**
Implement pickpocketing with stealth/sleight skill checks, disposition consequences, and wariness status. Add multipurpose item uses (LLM + tag-based) for emergent gameplay (e.g., pot as weapon, dynamite in pocket). Add Intent.Pickpocket and Intent.UseItem.

**Affected Modules/Files:**
- **:perception/src/main/kotlin/com/jcraw/mud/perception/**
  - `Intent.kt` - Add Pickpocket, UseItem sealed classes
  - `IntentParser.kt` - Parse "steal gold from goblin", "place dynamite in pocket", "bash goblin with pot"
- **:reasoning/src/main/kotlin/com/jcraw/mud/reasoning/pickpocket/**
  - `PickpocketHandler.kt` (new) - Stealth/Sleight vs Perception checks, consequences
- **:reasoning/src/main/kotlin/com/jcraw/mud/reasoning/items/**
  - `ItemUseHandler.kt` (new) - Multipurpose uses via tags + LLM
- **:core/src/main/kotlin/com/jcraw/mud/core/StatusEffect.kt**
  - Add WARINESS status (temp +20 Perception for 10 turns)
- **:app/src/main/kotlin/com/jcraw/app/handlers/**
  - `PickpocketHandlers.kt` (new) - Handle Pickpocket intent
  - `ItemUseHandlers.kt` (new) - Handle UseItem intent

**Key Technical Decisions:**
1. **Pickpocket skill check:** Stealth or Sleight (higher) vs target Perception
   - Rationale: Two skill paths (stealth thief vs sleight magician); aligns with skill system
   - Implementation: d20 + max(Stealth, Sleight) vs target's Perception passive (10 + modifier)
2. **Disposition consequences:** Failure → disposition -20 to -50 (based on margin)
   - Rationale: High risk/reward; integrates social system; repeated failures make NPCs hostile
3. **Wariness status:** Temp +20 Perception for 10 turns after failed pickpocket
   - Rationale: Prevents immediate retry abuse; realistic (NPC on guard); uses existing status effect system
4. **Multipurpose item uses:**
   - Tag-based: "blunt" → improvised weapon (damage = weight * 0.5), "explosive" → timed detonation
   - LLM-based: "place dynamite in pocket" → pickpocket + explosive tag → detonate on timer/use
   - Rationale: Hybrid enables both predictable (tags) and emergent (LLM) uses; avoids brittle hardcoding
5. **Item placement in NPC inventory:** Pickpocket can add items (e.g., dynamite), not just steal
   - Rationale: Enables creative tactics; aligns with flexible item philosophy

**Testing Approach:**
- **Unit tests (PickpocketHandlerTest.kt, ~18 tests):**
  - Success: Steal gold, add to player, remove from NPC
  - Success: Place item in NPC inventory
  - Failure: Disposition drop (-20 to -50)
  - Failure: Wariness status applied (+20 Perception for 10 turns)
  - High Stealth overcomes Perception
  - Wariness increases difficulty (retry fails even with same roll)
  - Rationale: Pickpocket has many outcomes (success, failure, consequences); must test all
- **Unit tests (ItemUseHandlerTest.kt, ~15 tests):**
  - Pot with "blunt" tag → improvised weapon (damage = 3kg * 0.5 = 1.5)
  - Dynamite with "explosive" tag → AoE damage + timer
  - LLM parses "bash goblin with pot" → attack intent + improvised weapon
  - LLM parses "place dynamite in pocket" → pickpocket + explosive tag
  - Missing required tag → failure or high difficulty
  - Rationale: Multipurpose system is core innovation; must prove flexibility
- **Integration tests (~10 tests):**
  - Full pickpocket flow: steal gold → NPC hostile → combat
  - Dynamite placement → detonation in 3 turns → AoE damage
  - Pot as weapon → combat with improvised damage
  - Rationale: End-to-end emergent gameplay proves system works

**Documentation:**
- Pickpocket commands and risks
- Multipurpose item examples (pot, dynamite, etc.)
- Tag reference (what tags enable what uses)

---

### Chunk 9: Skills & Combat Integration
**Estimated Time:** 3 hours

**Description:**
Integrate item system with Skills V2 (Strength capacity, gathering/crafting XP) and Combat V2 (equipped item stat bonuses, damage/defense, turn queue equip penalties). Add capacity augmentation from perks/items.

**Affected Modules/Files:**
- **:reasoning/src/main/kotlin/com/jcraw/mud/reasoning/skills/**
  - `SkillModifierCalculator.kt` - Add equipped item bonuses (e.g., sword → +Sword Fighting)
  - `CapacityCalculator.kt` (new) - Strength * 5kg + bag bonuses + perks
- **:reasoning/src/main/kotlin/com/jcraw/mud/reasoning/combat/**
  - `DamageCalculator.kt` - Add weapon damage property to calculation
  - `AttackResolver.kt` - Include equipped weapon in skill checks
- **:reasoning/src/main/kotlin/com/jcraw/mud/reasoning/inventory/**
  - `InventoryManager.kt` - Integrate CapacityCalculator, update on Strength change
- **:app/src/main/kotlin/com/jcraw/app/MudGameEngine.kt**
  - Integrate equipped item bonuses in combat narration

**Key Technical Decisions:**
1. **Capacity formula:** Base = Strength level * 5kg, augments = bags + perks + spells
   - Rationale: Scales with progression; bags are meaningful upgrades; perks add depth
   - Example: Strength 10 + Leather Bag (+10kg) + "Pack Mule" perk (+20%) = 50kg + 10kg + 12kg = 72kg
2. **Equipped item stat bonuses:** Weapon properties add to skill modifiers
   - Rationale: Makes items meaningful in combat; incentivizes upgrades
   - Example: Iron Sword properties["sword_fighting_bonus"] = 2 → +2 to Sword Fighting skill rolls
3. **Damage calculation:** Base damage + weapon properties["damage"]
   - Rationale: Simple additive; weapon quality (1-10) can multiply
   - Example: STR damage 5 + Iron Sword (damage 10, quality 3) = 5 + (10 * 1.2) = 17
4. **Defense from armor:** properties["defense"] subtracts from incoming damage
   - Rationale: Aligns with existing Combat V2 resistance system
5. **Gathering/crafting XP:** Success = full XP, failure = 20%
   - Rationale: Encourages repeated attempts; prevents failure grinding

**Testing Approach:**
- **Unit tests (CapacityCalculatorTest.kt, ~12 tests):**
  - Base capacity from Strength (level 10 → 50kg)
  - Bag bonus (+10kg from Leather Bag)
  - Perk bonus (+20% from "Pack Mule")
  - Combined (base + bags + perks)
  - Rationale: Capacity is foundational; bugs prevent item collection
- **Integration tests (~15 tests):**
  - Equip sword → Sword Fighting +2 in combat
  - Equip armor → damage reduction in combat
  - Strength increase → capacity increase
  - Craft success → Blacksmithing XP
  - Gather success → Mining XP
  - Full combat: equip weapon → attack → damage includes weapon bonus
  - Rationale: Integration points (skills, combat) are where bugs hide

**Documentation:**
- Capacity formula explanation
- Item stat bonuses table (weapon → skills, armor → defense)

---

### Chunk 10: Documentation & Final Testing
**Estimated Time:** 2 hours

**Description:**
Comprehensive documentation updates (CLAUDE.md, ARCHITECTURE.md, GETTING_STARTED.md) and create dedicated ITEMS_AND_CRAFTING.md system doc. Add bot test scenario: gather resources → craft gear → pickpocket dynamite → trade → win dungeon.

**Affected Files:**
- **docs/CLAUDE.md**
  - Update "What's Implemented" section with item system
  - Add item commands to quick reference
  - Update module structure
- **docs/ARCHITECTURE.md**
  - Add InventoryComponent, TradingComponent to components list
  - Document ItemDatabase schema
  - Add inventory/crafting/gathering data flow diagrams
- **docs/GETTING_STARTED.md**
  - Add full command reference: equip, drop, loot, gather, craft, trade, pickpocket, use
  - Add item system walkthrough (gather → craft → equip → fight)
- **docs/ITEMS_AND_CRAFTING.md** (new)
  - System overview
  - Component architecture
  - Weight/capacity mechanics
  - Gathering nodes (finite, skill-based)
  - Crafting recipes (DB + LLM)
  - Trading (finite gold, disposition)
  - Pickpocketing (consequences, wariness)
  - Multipurpose items (tags + LLM)
  - Integration points (skills, combat, social)
- **:testbot/src/test/kotlin/com/jcraw/mud/testbot/scenarios/**
  - `ItemSystemPlaythroughTest.kt` (new) - Bot test for full item flow

**Testing Approach:**
- **Bot test scenario (ItemSystemPlaythroughTest.kt):**
  1. Spawn in dungeon with pickaxe, empty inventory
  2. Gather iron ore (skill check, deplete node)
  3. Craft iron sword (recipe, skill check)
  4. Equip sword (combat bonus applied)
  5. Pickpocket dynamite into goblin's pocket
  6. Fight goblin with sword (+damage)
  7. Loot goblin corpse (equipped gear + loot table)
  8. Trade looted items to merchant (disposition affects price)
  9. Buy health potion with gold
  10. Win dungeon with upgraded gear
  - Rationale: End-to-end test proves all systems integrate; bot validates full feature
- **Integration test suite (~50 tests across all handlers):**
  - Gather → craft → equip → combat flow
  - Weight overflow scenarios
  - Finite gold trade depletion
  - Pickpocket success/fail with disposition changes
  - Loot generation with rarity distribution
  - Rationale: Comprehensive coverage catches regressions

**Documentation Updates:**
- **CLAUDE.md additions:**
  ```markdown
  ### Item System ✅
  - **Inventory management**: Weight-based limits (Strength * 5kg + bags)
  - **Equipment system**: Weapons (+damage), armor (+defense), combat equip penalty
  - **Gathering**: Finite nodes with skill-based spotting, tool requirements
  - **Crafting**: 20+ DB recipes + LLM ad-hoc, quality scaling with skill
  - **Loot generation**: Corpse-based (80% equipped + RNG extras), rarity-weighted
  - **Trading**: Finite merchant gold, disposition price modifiers
  - **Pickpocketing**: Stealth/Sleight checks, disposition consequences, wariness status
  - **Multipurpose items**: Hybrid tag-based + LLM (pot as weapon, dynamite placement)
  ```

- **ARCHITECTURE.md additions:**
  - InventoryComponent, TradingComponent in components section
  - ItemDatabase schema diagram
  - Data flow: Gather → Craft → Equip → Combat

- **GETTING_STARTED.md additions:**
  - Commands: `gather <resource>`, `craft <recipe>`, `buy/sell <item>`, `pickpocket <npc>`, `use <item>`
  - Walkthrough: "Gather iron ore from the cave wall, craft an iron sword at the forge, equip it for combat"

---

## Summary

**Total Chunks:** 10
**Estimated Time:** 24 hours
**Modules Affected:** :core, :perception, :reasoning, :memory, :app, :client, :testbot

**Key Innovations:**
1. **Multipurpose items** via hybrid tag-based rules + LLM intent parsing
2. **Weight-limited inventory** encouraging strategic choices
3. **Finite gathering** preventing infinite farming
4. **Realistic loot** (80% equipped gear, not explosion)
5. **Combat equip penalty** preventing gear-swap abuse
6. **Pickpocket consequences** (disposition, wariness)
7. **Finite merchant gold** for balanced economy

**Integration Complexity:**
- High: Skills (capacity, XP), Combat (turn queue, bonuses), Social (disposition)
- Medium: World (room descriptions), Memory (RAG)
- Low: Future stubs (multiplayer, quests)

**Risk Mitigation:**
- Unit tests for each component (weight, loot RNG, skill checks)
- Integration tests for cross-system flows (gather → craft → equip → combat)
- Bot test for end-to-end validation
- Seeded RNG for deterministic tests
- Mocked LLM calls for speed/reliability

This plan should be executed sequentially, with each chunk fully tested before moving to the next. Chunk 10 documentation updates should reflect the actual implemented state, not planned state.
