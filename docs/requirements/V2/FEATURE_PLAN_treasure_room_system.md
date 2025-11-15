# Treasure Room System - Implementation Plan

## Overview

This plan implements a Brogue-inspired treasure room system where players can choose playstyle-defining items from themed pedestals/altars. Players can take one item at a time, with "cages" (magical barriers) locking other pedestals, but can return the item to swap freely until leaving the room. The starter treasure room provides 5 items aligned with major skill categories to enable early build differentiation.

**Key Design Principles:**
- Brogue-style item selection (take one, cages lock others, return to swap)
- 5 pedestals aligned with major skill categories (Combat, Rogue, Magic, Utility, Hybrid)
- Early dungeon placement (first 2-3 rooms) for playstyle definition
- Themed to dungeon biome (Ancient Abyss → stone altars, Magma Caves → obsidian shrines)
- Unlimited swaps within the room (cages lift when item returned)
- One-time room access (treasure consumed after leaving)
- ECS component-based architecture (TreasureRoomComponent, PedestalState)

**Total Estimated Time:** 24-28 hours across 8 chunks

---

## Chain-of-Thought Analysis

### Dependencies
1. **Existing Systems:**
   - ECS (core): Component, Entity, WorldState, SpacePropertiesComponent
   - World Generation V2: Hierarchical generation, room placement, theming system
   - Item System V2: ItemInstance, ItemTemplate, InventoryComponent, equipment
   - Skill System V2: Skill definitions, progression, perks
   - Perception: Intent parsing for interactions
   - Reasoning: LLM descriptions, interaction handlers

2. **Integration Points:**
   - **World Generation**: Early room placement (first 2-3 rooms), biome theming for altar descriptions
   - **Items**: 5 high-quality starter items (RARE rarity, skill-aligned)
   - **Skills**: Item bonuses align with skill categories (Sword Fighting, Stealth, Fire Magic, etc.)
   - **Inventory**: Taking item adds to inventory, returning item removes from inventory
   - **State Persistence**: Room state saved (which pedestals locked, current item taken)

### Challenges & Solutions
1. **Cage Mechanic:**
   - Challenge: Represent visual "cages" or barriers that lock/unlock
   - Solution: PedestalState enum (AVAILABLE, LOCKED, EMPTY) + LLM descriptions for atmospheric barriers (force fields, stone slabs, iron cages based on theme)

2. **Swap Mechanics:**
   - Challenge: Allow unlimited swaps but prevent taking multiple items
   - Solution: Track `currentlyTakenItem: String?` in TreasureRoomComponent; returning item unlocks all pedestals

3. **One-Time Access:**
   - Challenge: Prevent re-farming treasure rooms
   - Solution: State flag `hasBeenLooted: Boolean` set when player leaves room with item; on re-entry, pedestals are EMPTY

4. **Biome Theming:**
   - Challenge: Altars should match dungeon atmosphere
   - Solution: LLM prompt includes dungeon biome/theme; altar descriptions adapt (ancient stone, obsidian, ice crystal, bone)

5. **Early Placement:**
   - Challenge: Ensure treasure room spawns early without breaking procedural generation
   - Solution: World generation constraint: treasure room spawns in first 2-3 graph nodes from starting position

### Sequencing Rationale
1. **Foundation First:** Components/data model (Chunk 1) before logic
2. **Persistence Early:** Database schema (Chunk 2) enables save/load testing
3. **Core Mechanics:** Interaction handlers (Chunk 3) for take/return/swap logic
4. **Content Creation:** Starter items (Chunk 4) and biome theming (Chunk 5)
5. **World Integration:** Generation placement (Chunk 6) after room mechanics work
6. **Polish Last:** UI/descriptions (Chunk 7) and testing/documentation (Chunk 8)

---

## TODO: Implementation Chunks

### Chunk 1: Core Components & Data Model (Foundation)
**Estimated Time:** 3 hours

**Description:**
Create the foundational data structures for treasure rooms: TreasureRoomComponent with pedestal states, altar theming, and swap tracking. Define PedestalState enum and Pedestal data class. Implement core methods for checking availability, locking/unlocking, and state transitions.

**Affected Modules/Files:**
- **:core/src/main/kotlin/com/jcraw/mud/core/components/**
  - `TreasureRoomComponent.kt` (new) - Treasure room state with pedestal array, current taken item, loot status
  - `Pedestal.kt` (new) - Data class for pedestal (item, state, description)
- **:core/src/main/kotlin/com/jcraw/mud/core/enums/**
  - `PedestalState.kt` (new) - AVAILABLE, LOCKED, EMPTY
  - `TreasureRoomType.kt` (new) - STARTER, COMBAT, MAGIC, BOSS (future room types)
- **:core/src/main/kotlin/com/jcraw/mud/core/ComponentType.kt**
  - Add TREASURE_ROOM to enum

**Key Technical Decisions:**
1. **Immutable component:** TreasureRoomComponent returns new copy on state change (aligns with ECS pattern)
   - Rationale: Thread-safe, testable, fits existing WorldState.copy() pattern
2. **Pedestal as data class:** `Pedestal(itemTemplateId: String, state: PedestalState, themeDescription: String)`
   - Rationale: Encapsulates pedestal state; themeDescription enables biome-adaptive LLM descriptions
3. **State tracking:** `currentlyTakenItem: String?` (null = no item taken, non-null = item ID currently held)
   - Rationale: Simple boolean logic for locking/unlocking; null-safe Kotlin pattern
4. **One-time flag:** `hasBeenLooted: Boolean` (false = can interact, true = pedestals empty)
   - Rationale: Prevents re-farming; set to true when player leaves room with item
5. **Pedestals as List:** `pedestals: List<Pedestal>` (5 pedestals for starter room)
   - Rationale: Extensible for future room types (3 pedestals, 7 pedestals, etc.)

**TreasureRoomComponent Structure:**
```kotlin
data class TreasureRoomComponent(
    val roomType: TreasureRoomType,
    val pedestals: List<Pedestal>,
    val currentlyTakenItem: String?, // Item template ID or null
    val hasBeenLooted: Boolean,
    val biomeTheme: String // "ancient_ruins", "magma_cave", "ice_cavern", etc.
) : Component {
    override val type = ComponentType.TREASURE_ROOM

    fun takeItem(itemTemplateId: String): TreasureRoomComponent
    fun returnItem(itemTemplateId: String): TreasureRoomComponent
    fun markAsLooted(): TreasureRoomComponent
    fun lockPedestals(): TreasureRoomComponent
    fun unlockPedestals(): TreasureRoomComponent
}

data class Pedestal(
    val itemTemplateId: String,
    val state: PedestalState,
    val themeDescription: String // "stone altar", "obsidian shrine", "ice pedestal"
)

enum class PedestalState {
    AVAILABLE, // Can take item
    LOCKED,    // Cage/barrier active
    EMPTY      // Item taken and room looted
}
```

**Testing Approach:**
- **Unit tests (TreasureRoomComponentTest.kt, ~25 tests):**
  - `takeItem()` locks other pedestals, sets currentlyTakenItem
  - `returnItem()` unlocks all pedestals, clears currentlyTakenItem
  - `markAsLooted()` sets hasBeenLooted=true, all pedestals to EMPTY
  - Swap sequence: take A → return A → take B (validates unlock/relock)
  - Edge case: take from already-taken room (should fail)
  - Edge case: return item not currently taken (should fail)
  - Rationale: State machine logic is complex; bugs break core mechanic
- **Property-based tests:**
  - After `takeItem()`, exactly 1 pedestal AVAILABLE, others LOCKED
  - After `returnItem()`, all pedestals AVAILABLE (if not looted)
  - After `markAsLooted()`, all pedestals EMPTY

**Documentation:**
- Inline KDoc for all public methods
- Document state transitions (AVAILABLE → LOCKED → AVAILABLE on return)

---

### Chunk 2: Database Schema & Repositories
**Estimated Time:** 3 hours

**Description:**
Create SQLite schema for treasure room persistence: treasure_rooms, pedestals tables. Implement repository interfaces and SQLite implementations. Define starter treasure room template data (5 pedestals with skill-aligned items).

**Affected Modules/Files:**
- **:memory/src/main/kotlin/com/jcraw/mud/memory/persistence/**
  - `TreasureRoomDatabase.kt` (new) - Schema definitions for treasure rooms
  - `TreasureRoomRepository.kt` (new, :core) - Interface for room state CRUD
  - `SQLiteTreasureRoomRepository.kt` (new) - Implementation using JDBC
- **:memory/src/main/resources/**
  - `treasure_room_templates.json` (new) - Starter room configuration

**Key Technical Decisions:**
1. **Separate table for pedestals:** treasure_rooms + pedestals with FK
   - Rationale: Normalizes data; supports variable pedestal counts (3, 5, 7)
   - Alternative: JSON column (rejected due to query complexity)
2. **Room state persistence:** currentlyTakenItem and hasBeenLooted in treasure_rooms table
   - Rationale: Critical state for swap mechanic; must persist across save/load
3. **Template-based rooms:** JSON defines starter room (5 pedestals, item IDs, theme)
   - Rationale: Easy editing; supports future treasure room types without code changes
4. **Biome theme as string:** Maps to dungeon biome (set during world generation)
   - Rationale: LLM prompt can adapt descriptions; flexible for future biomes

**Schema:**
```sql
CREATE TABLE treasure_rooms (
    space_id TEXT PRIMARY KEY, -- Links to graph node/space
    room_type TEXT NOT NULL, -- TreasureRoomType enum
    biome_theme TEXT NOT NULL, -- "ancient_ruins", "magma_cave", etc.
    currently_taken_item TEXT, -- Item template ID or null
    has_been_looted INTEGER NOT NULL, -- 0 or 1 (boolean)
    FOREIGN KEY (space_id) REFERENCES spaces(id)
);

CREATE TABLE pedestals (
    id TEXT PRIMARY KEY,
    treasure_room_id TEXT NOT NULL,
    item_template_id TEXT NOT NULL,
    state TEXT NOT NULL, -- PedestalState enum
    pedestal_index INTEGER NOT NULL, -- Position in room (0-4 for starter)
    theme_description TEXT NOT NULL, -- "ancient stone altar", etc.
    FOREIGN KEY (treasure_room_id) REFERENCES treasure_rooms(space_id),
    FOREIGN KEY (item_template_id) REFERENCES item_templates(id)
);

CREATE INDEX idx_pedestals_room ON pedestals(treasure_room_id);
```

**Testing Approach:**
- **Integration tests (TreasureRoomDatabaseTest.kt, ~20 tests):**
  - Save/load treasure room with 5 pedestals (roundtrip)
  - Update currentlyTakenItem (null → "sword_id" → null)
  - Update pedestal states (AVAILABLE → LOCKED → AVAILABLE)
  - Mark room as looted (hasBeenLooted = true, all pedestals EMPTY)
  - Load starter room template (5 pedestals, correct items)
  - Rationale: DB schema bugs cause save/load corruption; roundtrip tests prove correctness
- **Repository tests (SQLiteTreasureRoomRepositoryTest.kt, ~15 tests):**
  - findBySpaceId() returns correct room state
  - updatePedestalState() persists changes
  - updateCurrentlyTakenItem() handles null
  - Rationale: Repository layer is integration boundary; must handle edge cases

**Starter Room Template (treasure_room_templates.json):**
```json
{
  "starter_treasure_room": {
    "roomType": "STARTER",
    "pedestals": [
      {
        "itemTemplateId": "veterans_longsword",
        "category": "COMBAT",
        "themeDescriptionTemplate": "ancient {material} altar"
      },
      {
        "itemTemplateId": "shadowcloak",
        "category": "ROGUE",
        "themeDescriptionTemplate": "shadowed {material} pedestal"
      },
      {
        "itemTemplateId": "apprentice_staff",
        "category": "MAGIC",
        "themeDescriptionTemplate": "glowing {material} shrine"
      },
      {
        "itemTemplateId": "enchanted_satchel",
        "category": "UTILITY",
        "themeDescriptionTemplate": "sturdy {material} stand"
      },
      {
        "itemTemplateId": "spellblade",
        "category": "HYBRID",
        "themeDescriptionTemplate": "ornate {material} dais"
      }
    ]
  }
}
```

**Documentation:**
- Schema diagram in docs
- Repository interface contracts (KDoc)
- Template JSON format specification

---

### Chunk 3: Interaction Handlers & Swap Mechanics
**Estimated Time:** 4 hours

**Description:**
Implement interaction handlers for treasure rooms: take item from pedestal, return item to pedestal, examine pedestals. Integrate cage/lock mechanics (taking locks others, returning unlocks all). Add Intent.TakeTreasure and Intent.ReturnTreasure to perception layer with LLM parsing.

**Affected Modules/Files:**
- **:perception/src/main/kotlin/com/jcraw/mud/perception/**
  - `Intent.kt` - Add TakeTreasure, ReturnTreasure, ExaminePedestal sealed classes
  - `IntentParser.kt` - LLM parsing for "take sword from altar", "return staff", "examine pedestals"
- **:reasoning/src/main/kotlin/com/jcraw/mud/reasoning/treasureroom/**
  - `TreasureRoomHandler.kt` (new) - Core take/return/swap logic with state transitions
  - `PedestalInteractionValidator.kt` (new) - Validate interactions (can take? already taken? room looted?)
- **:app/src/main/kotlin/com/jcraw/app/handlers/**
  - `TreasureRoomHandlers.kt` (new) - Handle TakeTreasure, ReturnTreasure, ExaminePedestal intents

**Key Technical Decisions:**
1. **Take item flow:**
   - Validation: Room not looted, no item currently taken, pedestal AVAILABLE
   - Action: Add item to player inventory, set currentlyTakenItem, lock other pedestals
   - Narration: LLM describes cages/barriers descending on other altars
   - Rationale: Single responsibility (validation → action → narration)
2. **Return item flow:**
   - Validation: Item in player inventory matches currentlyTakenItem
   - Action: Remove item from inventory, clear currentlyTakenItem, unlock all pedestals
   - Narration: LLM describes barriers lifting, choice available again
   - Rationale: Allows swaps; symmetric with take flow
3. **Leaving room with item:**
   - Detection: Player moves to different space while currentlyTakenItem != null
   - Action: Mark room as looted (hasBeenLooted = true), set all pedestals to EMPTY
   - Narration: "The remaining treasures vanish as you depart"
   - Rationale: One-time access; prevents returning to swap infinitely across dungeon
4. **Examine pedestal:**
   - Shows item description, state (available/locked/empty), themed altar description
   - Rationale: Player needs to evaluate choices before committing
5. **Inventory weight check:**
   - Taking item must pass inventory weight limit (use existing InventoryComponent logic)
   - Rationale: Consistent with item system; prevents exploits

**Testing Approach:**
- **Unit tests (TreasureRoomHandlerTest.kt, ~30 tests):**
  - Take item success: inventory updated, pedestals locked, currentlyTakenItem set
  - Take item failure: room looted (all pedestals EMPTY)
  - Take item failure: item already taken (currentlyTakenItem != null)
  - Take item failure: pedestal already LOCKED
  - Take item failure: inventory weight limit exceeded
  - Return item success: inventory updated, pedestals unlocked, currentlyTakenItem cleared
  - Return item failure: item not in inventory
  - Return item failure: item doesn't match currentlyTakenItem
  - Swap sequence: take sword → return sword → take staff (full flow)
  - Leave room: currentlyTakenItem != null → room marked looted
  - Rationale: Complex state machine with many edge cases; must test all paths
- **Integration tests (TreasureRoomHandlersTest.kt, ~20 tests):**
  - Full flow: enter room → examine pedestals → take sword → return sword → take staff → leave room
  - Re-enter looted room: all pedestals EMPTY, cannot take items
  - Inventory weight limit: take heavy item fails gracefully
  - Persistence: take item → save game → load game → return item (state persists)
  - Rationale: Integration with inventory, world state, persistence must work seamlessly

**Documentation:**
- Interaction commands ("take sword from altar", "return staff to pedestal")
- Swap mechanic explanation (unlimited swaps until leaving room)

---

### Chunk 4: Starter Treasure Room Items
**Estimated Time:** 4 hours

**Description:**
Create 5 high-quality starter items aligned with major skill categories (Combat, Rogue, Magic, Utility, Hybrid). Define item templates with RARE rarity, skill bonuses, and progression-friendly stats. Add items to item_templates.json and integrate with treasure room template.

**Affected Modules/Files:**
- **:memory/src/main/resources/**
  - `item_templates.json` - Add 5 new starter treasure items
- **:reasoning/src/main/kotlin/com/jcraw/mud/reasoning/treasureroom/**
  - `StarterItemGenerator.kt` (new) - Factory for generating starter items with quality scaling
- **:core/src/main/kotlin/com/jcraw/mud/core/ItemTemplate.kt**
  - No changes (reuse existing structure)

**Key Technical Decisions:**
1. **Rarity: RARE for all starter items:**
   - Rationale: Feels rewarding early; defines playstyle; not overpowered (EPIC/LEGENDARY too strong)
2. **Skill bonuses align with categories:**
   - Combat: +3 Sword Fighting (melee weapon)
   - Rogue: +5 Stealth (stealth tool/cloak)
   - Magic: +2 spell damage, -10% mana cost (staff/focus)
   - Utility: +20kg capacity, +1 Charisma (satchel/container)
   - Hybrid: Weapon + minor spell (enables multiclass builds)
   - Rationale: Clear playstyle differentiation; meaningful early choice
3. **Item progression:**
   - Items are powerful but not BiS (Best in Slot); players can upgrade later
   - Rationale: Starter items bootstrap playstyle, not endgame; encourages exploration/loot
4. **Quality set to 5 (mid-tier):**
   - Rationale: Room for crafted/boss items to be better (quality 7-10); not trash-tier
5. **Item descriptions reference skill usage:**
   - Example: "This longsword has seen countless battles. Wield it with skill to honor its legacy." (+Sword Fighting)
   - Rationale: Tutorial-friendly; hints at skill synergy

**Starter Item Definitions:**

**1. Veteran's Longsword (Combat):**
```json
{
  "id": "veterans_longsword",
  "name": "Veteran's Longsword",
  "type": "WEAPON",
  "tags": ["sharp", "slashing", "metal", "two_handed"],
  "properties": {
    "weight": "4.0",
    "damage": "12",
    "sword_fighting_bonus": "3",
    "value": "150"
  },
  "rarity": "RARE",
  "description": "A well-balanced longsword with worn leather grip. Its blade bears the marks of countless battles, yet remains razor-sharp. A warrior's trusted companion.",
  "equipSlot": "HANDS_BOTH"
}
```

**2. Shadowcloak (Rogue):**
```json
{
  "id": "shadowcloak",
  "name": "Shadowcloak",
  "type": "ARMOR",
  "tags": ["cloth", "stealth", "dark", "enchanted"],
  "properties": {
    "weight": "1.5",
    "defense": "2",
    "stealth_bonus": "5",
    "backstab_bonus": "2",
    "value": "200"
  },
  "rarity": "RARE",
  "description": "A midnight-black cloak that seems to absorb light. When worn, you feel the comforting embrace of shadows, as if the darkness itself shields you from prying eyes.",
  "equipSlot": "BACK"
}
```

**3. Apprentice's Staff (Magic):**
```json
{
  "id": "apprentice_staff",
  "name": "Apprentice's Staff",
  "type": "WEAPON",
  "tags": ["blunt", "wood", "enchanted", "spell_focus", "two_handed"],
  "properties": {
    "weight": "3.0",
    "damage": "6",
    "spell_damage_bonus": "2",
    "mana_cost_reduction": "10",
    "intelligence_bonus": "1",
    "value": "180"
  },
  "rarity": "RARE",
  "description": "A gnarled oak staff topped with a softly glowing crystal. Though meant for apprentices, its enchantment is potent—channeling magic through it feels effortless, like breathing.",
  "equipSlot": "HANDS_BOTH"
}
```

**4. Enchanted Satchel (Utility):**
```json
{
  "id": "enchanted_satchel",
  "name": "Enchanted Satchel",
  "type": "CONTAINER",
  "tags": ["cloth", "enchanted", "container", "weightless"],
  "properties": {
    "weight": "0.5",
    "capacity_bonus": "20",
    "charisma_bonus": "1",
    "value": "250"
  },
  "rarity": "RARE",
  "description": "A supple leather satchel adorned with silver runes. Its interior seems impossibly spacious, and items placed within weigh almost nothing. A merchant's dream.",
  "equipSlot": "ACCESSORY_1"
}
```

**5. Spellblade (Hybrid):**
```json
{
  "id": "spellblade",
  "name": "Spellblade",
  "type": "WEAPON",
  "tags": ["sharp", "slashing", "metal", "enchanted", "spell_focus", "one_handed"],
  "properties": {
    "weight": "2.5",
    "damage": "8",
    "sword_fighting_bonus": "2",
    "spell_damage_bonus": "1",
    "intelligence_bonus": "1",
    "value": "220"
  },
  "rarity": "RARE",
  "description": "A slender blade etched with arcane symbols that pulse with faint blue light. It feels balanced for both swordplay and spellcasting—a weapon for those who walk the line between warrior and mage.",
  "equipSlot": "HANDS_MAIN"
}
```

**Testing Approach:**
- **Unit tests (StarterItemGeneratorTest.kt, ~10 tests):**
  - Generate each item, verify stats match template
  - Verify quality = 5 for all items
  - Verify rarity = RARE for all items
  - Rationale: Items must match spec; bugs break balance
- **Integration tests (~8 tests):**
  - Take sword → equip → check Sword Fighting bonus applied
  - Take staff → cast spell → check mana cost reduction
  - Take satchel → check capacity increase
  - Take cloak → check Stealth bonus applied
  - Rationale: Skill/inventory integration is critical; bonuses must work

**Documentation:**
- Starter item stat table (name, type, bonuses, weight)
- Playstyle recommendations (Combat → sword, Rogue → cloak, etc.)

---

### Chunk 5: Biome Theming & LLM Descriptions
**Estimated Time:** 3 hours

**Description:**
Implement biome-adaptive altar/pedestal descriptions using LLM. Integrate with dungeon theming system to generate atmospheric descriptions (ancient stone altars in ruins, obsidian shrines in magma caves, ice pedestals in frozen caverns). Add room entry description that highlights the treasure room's unique nature.

**Affected Modules/Files:**
- **:reasoning/src/main/kotlin/com/jcraw/mud/reasoning/treasureroom/**
  - `TreasureRoomDescriptionGenerator.kt` (new) - LLM prompts for themed descriptions
  - `BiomeThemeMapper.kt` (new) - Maps dungeon biomes to altar materials/aesthetics
- **:reasoning/src/main/kotlin/com/jcraw/mud/reasoning/world/**
  - `RoomDescriptionGenerator.kt` - Integrate treasure room detection and special description
- **:llm/src/main/kotlin/com/jcraw/mud/llm/**
  - `PromptTemplates.kt` - Add treasure room description prompt

**Key Technical Decisions:**
1. **Biome theme mapping:**
   - Ancient Abyss → stone, weathered, ancient
   - Magma Caves → obsidian, glowing, volcanic
   - Frozen Depths → ice crystal, frosted, glacial
   - Bone Crypts → bone, skeletal, macabre
   - Rationale: Atmospheric consistency; each dungeon feels unique
2. **LLM prompt structure:**
   - Input: Biome theme, pedestal items (names), current states (available/locked/empty)
   - Output: 2-3 paragraph room description + individual altar descriptions
   - Rationale: Single LLM call for efficiency; coherent narrative
3. **State-aware descriptions:**
   - AVAILABLE: "The sword rests upon the altar, ready to be claimed"
   - LOCKED: "A shimmering barrier encases the altar, the sword just beyond reach"
   - EMPTY: "The altar stands bare, its treasure long since claimed"
   - Rationale: Visual feedback for swap mechanic; player understands state
4. **First-time entry description:**
   - Emphasizes choice: "You may claim one treasure, but choose wisely—the others will be sealed away"
   - Rationale: Tutorial-friendly; player understands mechanic immediately
5. **Post-loot description:**
   - "The chamber feels hollow, its magic spent. Only empty altars remain."
   - Rationale: Atmospheric closure; player knows room is exhausted

**LLM Prompt Template:**
```
You are describing a treasure room in a {biome_theme} dungeon.

The room contains 5 altars/pedestals, each themed to match the dungeon's aesthetic:
1. {altar_1_material} altar with {item_1_name} ({state_1})
2. {altar_2_material} pedestal with {item_2_name} ({state_2})
...

Current state: {if currently_taken_item: "The player has taken the {item_name}. Magical barriers have sealed the other altars." else: "All altars are accessible. The player may claim one treasure."}

Generate:
1. A 2-3 paragraph room entry description (atmospheric, emphasizes choice and consequence)
2. A one-sentence description for each altar (material, item, state)

Keep descriptions concise (under 100 words total). Focus on atmosphere and mechanics.
```

**Testing Approach:**
- **Unit tests (TreasureRoomDescriptionGeneratorTest.kt, ~15 tests):**
  - Generate description for each biome (Ancient Abyss, Magma Caves, etc.)
  - Verify descriptions include altar materials matching biome
  - State-aware descriptions (AVAILABLE vs LOCKED vs EMPTY)
  - Mock LLM responses to test prompt structure
  - Rationale: LLM integration must produce consistent output; prompt engineering critical
- **Integration tests (~8 tests):**
  - Enter treasure room → description includes "choose wisely"
  - Take item → description shows barriers/cages on other altars
  - Return item → description shows barriers lifting
  - Re-enter looted room → description shows empty altars
  - Rationale: Player-facing descriptions must match game state

**Biome Theme Mappings:**
```kotlin
object BiomeThemeMapper {
    private val themes = mapOf(
        "ancient_abyss" to BiomeTheme(
            material = "weathered stone",
            aesthetic = "ancient, crumbling, moss-covered",
            barrierType = "shimmering arcane barrier"
        ),
        "magma_cave" to BiomeTheme(
            material = "obsidian",
            aesthetic = "glowing, volcanic, heat-warped",
            barrierType = "wall of molten energy"
        ),
        "frozen_depths" to BiomeTheme(
            material = "ice crystal",
            aesthetic = "frosted, glacial, pristine",
            barrierType = "frozen barrier of solid ice"
        ),
        "bone_crypt" to BiomeTheme(
            material = "bone",
            aesthetic = "skeletal, macabre, dusty",
            barrierType = "cage of blackened bone"
        )
    )
}
```

**Documentation:**
- Biome theme table (biome → materials → barrier types)
- Example treasure room descriptions for each biome

---

### Chunk 6: World Generation Integration & Early Placement
**Estimated Time:** 4 hours

**Description:**
Integrate treasure room spawning into World Generation V2. Implement constraint for early placement (first 2-3 graph nodes from starting position). Add treasure room type to space generation logic and populate with TreasureRoomComponent. Support both V2 hierarchical generation and V3 graph-based generation.

**Affected Modules/Files:**
- **:reasoning/src/main/kotlin/com/jcraw/mud/reasoning/worldgen/**
  - `WorldGenerator.kt` - Add treasure room generation to chunk population
  - `SpacePopulator.kt` - Add treasure room spawning logic
  - `TreasureRoomPlacer.kt` (new) - Early placement constraint logic
- **:core/src/main/kotlin/com/jcraw/mud/core/SpacePropertiesComponent.kt**
  - Add `isTreasureRoom: Boolean` flag
- **:reasoning/src/main/kotlin/com/jcraw/mud/reasoning/worldgen/v3/**
  - `GraphNodeContentGenerator.kt` - Add treasure room node type for V3

**Key Technical Decisions:**
1. **Early placement constraint:**
   - V2: Place in first 2-3 rooms of SUBZONE (shallow depth)
   - V3: Place in graph nodes within distance 2-3 from starting node (BFS distance)
   - Rationale: Player encounters treasure room before major challenges; playstyle-defining items matter early
2. **One treasure room per dungeon (V2) or chunk (V3):**
   - Prevents multiple starter treasure rooms in same playthrough
   - Rationale: Starter items are special; scarcity increases choice weight
3. **Biome from dungeon theme:**
   - V2: SUBZONE.theme → treasure room biome (Ancient Abyss → "ancient_abyss")
   - V3: Chunk.theme → treasure room biome
   - Rationale: Thematic consistency with dungeon
4. **Treasure room as special node type (V3):**
   - GraphNodeComponent.nodeType = TREASURE_ROOM (new enum value)
   - Frontiers cannot be treasure rooms (prevents generation overlap)
   - Rationale: Distinguishes from normal rooms; special generation logic
5. **Safe zone flag:**
   - Treasure rooms have `isSafeZone = true` (no combat spawns)
   - Rationale: Player should deliberate choice without combat pressure

**World Generation Logic (V2):**
```kotlin
// In SpacePopulator.kt
fun populateTreasureRoom(space: Space, theme: String): Space {
    val treasureRoomComponent = TreasureRoomComponent(
        roomType = TreasureRoomType.STARTER,
        pedestals = generateStarterPedestals(theme),
        currentlyTakenItem = null,
        hasBeenLooted = false,
        biomeTheme = theme
    )

    return space.copy(
        properties = space.properties.copy(
            isTreasureRoom = true,
            isSafeZone = true,
            description = null // LLM-generated on first visit
        ),
        components = space.components + treasureRoomComponent
    )
}
```

**World Generation Logic (V3):**
```kotlin
// In GraphNodeContentGenerator.kt
fun generateTreasureRoomNode(nodeId: String, theme: String): GraphNodeComponent {
    return GraphNodeComponent(
        id = nodeId,
        nodeType = NodeType.TREASURE_ROOM, // New enum value
        nodeSize = NodeSize.MEDIUM,
        isFrontier = false, // Never a frontier
        exits = emptyMap() // Populated by exit resolution
    )
}
```

**Testing Approach:**
- **Unit tests (TreasureRoomPlacerTest.kt, ~20 tests):**
  - V2: Treasure room spawns in first 2-3 rooms of SUBZONE
  - V3: Treasure room spawns within BFS distance 2-3 from start
  - Only one treasure room per dungeon/chunk
  - Treasure room has isSafeZone = true
  - Treasure room biome matches dungeon theme
  - Rationale: Placement logic is complex; edge cases (small dungeons, no valid placement)
- **Integration tests (WorldGeneratorTest.kt additions, ~12 tests):**
  - Generate Ancient Abyss dungeon → treasure room present, biome = "ancient_abyss"
  - Generate Magma Cave dungeon → treasure room present, biome = "magma_cave"
  - Small dungeon (3 rooms) → treasure room still spawns (early constraint relaxed if needed)
  - V3 graph generation → treasure room node present, not frontier
  - Rationale: Full world generation integration proves placement works

**Documentation:**
- World generation treasure room rules (early placement, one per dungeon)
- Biome mapping table (dungeon theme → treasure room biome)

---

### Chunk 7: UI/UX Polish & Leave Room Detection
**Estimated Time:** 3 hours

**Description:**
Implement room transition detection to mark treasure rooms as looted when player leaves with item. Add UI polish: examine command shows all pedestals and states, room entry messages emphasize choice, leave messages show consequence. Integrate with client GUI if applicable.

**Affected Modules/Files:**
- **:app/src/main/kotlin/com/jcraw/app/handlers/**
  - `MovementHandlers.kt` - Detect leaving treasure room with item, mark as looted
- **:app/src/main/kotlin/com/jcraw/app/MudGameEngine.kt**
  - Add treasure room state check in game loop
- **:client/src/main/kotlin/com/jcraw/mud/client/handlers/**
  - `ClientMovementHandlers.kt` - GUI client support for treasure room transitions
- **:reasoning/src/main/kotlin/com/jcraw/mud/reasoning/treasureroom/**
  - `TreasureRoomHandler.kt` - Add `onPlayerExit()` method

**Key Technical Decisions:**
1. **Leave detection:**
   - MovementHandlers checks previous space for TreasureRoomComponent
   - If currentlyTakenItem != null, mark room as looted before transition
   - Rationale: Automatic; player doesn't need explicit "finalize choice" command
2. **Leave narration:**
   - "As you depart with the {item_name}, the remaining treasures dissolve into mist. Your choice is made."
   - Rationale: Atmospheric feedback; player understands consequence
3. **Re-entry to looted room:**
   - Description shows empty altars, no interaction options
   - Rationale: Clear that room is exhausted; prevents confusion
4. **Examine command:**
   - Lists all pedestals with item names, states, and themed descriptions
   - Example: "1. Weathered stone altar (AVAILABLE): Veteran's Longsword - A well-balanced longsword..."
   - Rationale: Player can evaluate all choices before taking
5. **GUI client integration:**
   - Show pedestal grid with clickable items (if available)
   - Locked pedestals grayed out with barrier icon
   - Rationale: Visual representation clearer than text for GUI users

**Leave Detection Logic:**
```kotlin
// In MovementHandlers.kt (handleMove function)
fun handleMove(intent: Intent.Move, state: WorldState): Pair<String, WorldState> {
    val currentSpace = state.getCurrentSpace(state.playerState.currentLocation)
    val treasureRoomComponent = currentSpace?.components
        ?.filterIsInstance<TreasureRoomComponent>()
        ?.firstOrNull()

    // Check if leaving treasure room with item
    var updatedState = state
    var leaveMessage = ""
    if (treasureRoomComponent != null && treasureRoomComponent.currentlyTakenItem != null) {
        val itemName = getItemName(treasureRoomComponent.currentlyTakenItem)
        leaveMessage = "\n\nAs you depart with the $itemName, the remaining treasures shimmer and fade. Your choice is final."

        // Mark room as looted
        val lootedComponent = treasureRoomComponent.markAsLooted()
        updatedState = state.updateComponent(currentSpace.id, lootedComponent)
    }

    // ... rest of movement logic ...
    return Pair(moveMessage + leaveMessage, updatedState)
}
```

**Testing Approach:**
- **Unit tests (MovementHandlersTest.kt additions, ~15 tests):**
  - Leave treasure room with item → room marked looted
  - Leave treasure room without item (returned) → room not looted
  - Re-enter looted room → pedestals all EMPTY
  - Move within same room → no loot trigger
  - Rationale: Leave detection logic has edge cases; must test all scenarios
- **Integration tests (~10 tests):**
  - Full flow: enter → take → leave → re-enter (looted state persists)
  - Full flow: enter → take → return → leave → re-enter (not looted, can still choose)
  - Save game after leaving with item → load game → room still looted
  - GUI client: click pedestal → take item → move → room looted
  - Rationale: End-to-end UX must feel seamless

**Documentation:**
- Movement mechanic with treasure rooms (leaving finalizes choice)
- Examine command syntax ("examine pedestals")

---

### Chunk 8: Testing & Documentation
**Estimated Time:** 3 hours

**Description:**
Comprehensive documentation updates (CLAUDE.md, ARCHITECTURE.md, GETTING_STARTED.md) and create dedicated TREASURE_ROOMS.md system doc. Add testbot scenario: enter treasure room → examine pedestals → take sword → return sword → take staff → leave → win dungeon with staff.

**Affected Files:**
- **docs/CLAUDE.md**
  - Update "What's Implemented" section with treasure room system
  - Add treasure room commands to quick reference
  - Update module structure
- **docs/ARCHITECTURE.md**
  - Add TreasureRoomComponent to components list
  - Document TreasureRoomDatabase schema
  - Add treasure room placement logic to world generation section
- **docs/GETTING_STARTED.md**
  - Add treasure room interaction commands
  - Add treasure room walkthrough (examine → take → return → swap → leave)
- **docs/TREASURE_ROOMS.md** (new)
  - System overview (Brogue inspiration, choice mechanic)
  - Component architecture (TreasureRoomComponent, Pedestal, PedestalState)
  - Interaction flow (take → lock → return → unlock → leave → loot)
  - Starter items (5 items, skill categories, stats)
  - Biome theming (altar descriptions by dungeon)
  - World generation (early placement, one per dungeon)
- **:testbot/src/test/kotlin/com/jcraw/mud/testbot/scenarios/**
  - `TreasureRoomPlaythroughTest.kt` (new) - Bot test for treasure room flow

**Testing Approach:**
- **Bot test scenario (TreasureRoomPlaythroughTest.kt):**
  1. Start in dungeon, move to treasure room (should be in first 2-3 rooms)
  2. Examine pedestals (verify 5 items listed)
  3. Take Veteran's Longsword (verify locked message for others)
  4. Return longsword (verify unlock message)
  5. Take Apprentice's Staff (verify locked message again)
  6. Leave room (verify finalization message)
  7. Re-enter room (verify pedestals empty)
  8. Use staff in combat (verify spell bonuses)
  9. Win dungeon with staff
  - Rationale: End-to-end test proves all systems integrate; bot validates full feature
- **Integration test suite (~50 tests across all handlers):**
  - Take → return → swap flow
  - Leave room triggers loot state
  - Re-entry to looted room shows empty pedestals
  - Save/load preserves treasure room state
  - Biome descriptions match dungeon theme
  - Starter items provide correct bonuses
  - Rationale: Comprehensive coverage catches regressions

**Documentation Updates:**
- **CLAUDE.md additions:**
  ```markdown
  ### Treasure Room System ✅
  - **Brogue-inspired choice mechanic**: Take one item, cages lock others, return to swap
  - **5 starter pedestals**: Combat (longsword), Rogue (shadowcloak), Magic (staff), Utility (satchel), Hybrid (spellblade)
  - **Unlimited swaps**: Return item to unlock all pedestals, swap freely until leaving room
  - **One-time access**: Leaving with item finalizes choice, pedestals emptied
  - **Early placement**: Spawns in first 2-3 rooms for playstyle definition
  - **Biome theming**: Altars adapt to dungeon (stone, obsidian, ice, bone)
  - **Safe zone**: No combat spawns, deliberate choice without pressure
  ```

- **ARCHITECTURE.md additions:**
  - TreasureRoomComponent in components section
  - TreasureRoomDatabase schema diagram (treasure_rooms + pedestals tables)
  - Data flow: Enter room → Examine → Take → Return → Swap → Leave → Loot

- **GETTING_STARTED.md additions:**
  - Commands: `examine pedestals`, `take <item> from altar`, `return <item>`
  - Walkthrough: "Treasure rooms offer powerful starter items. Examine pedestals carefully, take one item to try it out, and return it to swap if it doesn't suit your playstyle. Once you leave with an item, your choice is final—the remaining treasures vanish."

- **TREASURE_ROOMS.md (new, ~400 lines):**
  - Overview: Brogue inspiration, choice and consequence mechanic
  - Architecture: Components (TreasureRoomComponent, Pedestal, PedestalState)
  - Interaction flow: Detailed state transitions (AVAILABLE → LOCKED → AVAILABLE → EMPTY)
  - Starter items: Full stat tables, playstyle recommendations
  - Biome theming: Materials, aesthetics, barrier types by dungeon
  - World generation: Placement constraints, V2 vs V3 integration
  - Commands reference: Examples with expected outputs

---

## Summary

**Total Chunks:** 8
**Estimated Time:** 24-28 hours
**Modules Affected:** :core, :perception, :reasoning, :memory, :app, :client, :testbot

**Key Innovations:**
1. **Brogue-inspired choice mechanic** with unlimited swaps before commitment
2. **Playstyle-defining starter items** aligned with 5 major skill categories
3. **Biome-adaptive theming** for atmospheric consistency
4. **Early placement** to maximize impact on player progression
5. **One-time access** to create meaningful, high-stakes choice
6. **Safe zone treasure rooms** for deliberate decision-making

**Integration Complexity:**
- High: World Generation (early placement, biome theming), Item System (inventory, equipment)
- Medium: Skills (bonuses, categories), Perception (intents), LLM (descriptions)
- Low: Combat (safe zone), Social (no NPCs in treasure rooms)

**Risk Mitigation:**
- Unit tests for state machine (take/return/swap transitions)
- Integration tests for world generation placement
- Bot test for end-to-end treasure room playthrough
- Mocked LLM calls for description generation tests
- State persistence tests (save/load treasure room state)

This plan should be executed sequentially, with each chunk fully tested before moving to the next. Chunk 8 documentation updates should reflect the actual implemented state.
