# Treasure Rooms System

A Brogue-inspired treasure room system where players encounter powerful items early in their journey and can choose one playstyle-defining item to shape their character build.

## Overview

Treasure rooms offer a meaningful choice mechanic inspired by Brogue's altar rooms. Players discover a special chamber containing 5 rare items on themed pedestals. They can:

- **Take an item** to try it out - magical barriers lock the other pedestals
- **Return the item** to unlock all pedestals and swap for a different one
- **Swap freely** as many times as they want while in the room
- **Finalize their choice** by leaving the room with an item - the remaining treasures vanish forever

This creates a high-stakes decision that encourages experimentation without commitment until the player is satisfied with their choice.

## Design Philosophy

**Inspired by Brogue**: The treasure room system draws from Brogue's altar room mechanic, where players choose one enchantment from several options. This creates a meaningful choice that:
- Defines playstyle early in the run
- Encourages experimentation before committing
- Creates memorable "build-defining moments"
- Adds replayability through different item choices

**Integration with Skill System**: Each treasure room item aligns with a major skill category:
- **Combat** - Melee warriors who excel in direct confrontation
- **Rogue** - Stealth specialists who avoid or ambush enemies
- **Magic** - Spellcasters who harness arcane power
- **Utility** - Survivalists who maximize resources and endurance
- **Hybrid** - Versatile adventurers who blend multiple approaches

**Early Placement**: Treasure rooms spawn 2-3 rooms from the dungeon entrance, ensuring players encounter them before significant challenges. This timing is intentional:
- Early enough to define playstyle before major fights
- Late enough that players understand basic mechanics
- Positioned to feel like a reward for initial exploration

## Architecture

### Component Model

The system uses an ECS (Entity-Component-System) architecture:

**TreasureRoomComponent** (`core/TreasureRoomComponent.kt`)
```kotlin
data class TreasureRoomComponent(
    val roomType: TreasureRoomType,        // STARTER, COMBAT, MAGIC, BOSS
    val pedestals: List<Pedestal>,         // 5 pedestals for starter rooms
    val currentlyTakenItem: String?,       // Item template ID or null
    val hasBeenLooted: Boolean,            // True after leaving with item
    val biomeTheme: String                 // "ancient_abyss", "magma_cave", etc.
) : Component
```

**Pedestal Data Class**
```kotlin
data class Pedestal(
    val itemTemplateId: String,            // Links to item template
    val state: PedestalState,              // AVAILABLE, LOCKED, EMPTY
    val themeDescription: String,          // Biome-adaptive altar description
    val pedestalIndex: Int                 // Position (0-4)
)
```

**PedestalState Enum**
- `AVAILABLE` - Item can be taken (pedestal accessible)
- `LOCKED` - Pedestal sealed by magical barrier (another item taken)
- `EMPTY` - Item removed (room has been looted)

**TreasureRoomType Enum**
- `STARTER` - 5 pedestals with playstyle-defining items (currently implemented)
- `COMBAT` - Future: combat-focused legendary weapons
- `MAGIC` - Future: arcane artifacts and spellbooks
- `BOSS` - Future: endgame reward rooms

### State Machine

The treasure room operates as a state machine:

```
Initial State: All pedestals AVAILABLE, currentlyTakenItem = null
    ↓
Take Item: currentlyTakenItem = itemId, other pedestals → LOCKED
    ↓
Return Item: currentlyTakenItem = null, all pedestals → AVAILABLE
    ↓
Leave Room (with item): hasBeenLooted = true, all pedestals → EMPTY
    ↓
Re-enter: All pedestals EMPTY, no interactions available
```

**Invariants**:
1. If `currentlyTakenItem != null`, exactly 1 pedestal is AVAILABLE (the taken one for returning), others LOCKED
2. If `currentlyTakenItem == null` and `!hasBeenLooted`, all pedestals are AVAILABLE
3. If `hasBeenLooted`, all pedestals are EMPTY regardless of currentlyTakenItem

## Interaction Flow

### 1. Entering the Treasure Room

When a player enters a treasure room for the first time, they see:

```
You enter a chamber of ancient magic. Five pedestals stand before you, each bearing
a powerful artifact wreathed in faint luminescence. The air thrums with potential.

A whisper echoes: "Choose wisely, traveler. You may claim one treasure, but only one."
```

**Available pedestals** (with `examine pedestals`):
```
1. Weathered stone altar bearing a warrior's blade
   Flamebrand Longsword - A masterwork longsword wreathed in flickering flames
   [AVAILABLE]

2. Shadowed stone pedestal wreathed in darkness
   Shadowweave Cloak - A midnight-black cloak that seems to absorb light
   [AVAILABLE]

... (3 more pedestals)
```

### 2. Taking an Item

**Command**: `take treasure flamebrand longsword` or `take treasure longsword`

**Result**:
- Item added to player inventory (weight check applied)
- `currentlyTakenItem` set to item template ID
- Other 4 pedestals transition to LOCKED state
- Barriers descend with atmospheric description:

```
You reach out and grasp the Flamebrand Longsword. Its warmth spreads through your hand
as eternal flames dance along the blade.

As you lift the sword, shimmering arcane barriers descend upon the other pedestals,
sealing the remaining treasures behind translucent walls of magic. The choice begins
to narrow.
```

**Updated pedestal states** (with `examine pedestals`):
```
1. Weathered stone altar bearing a warrior's blade
   Flamebrand Longsword - (currently in your possession)
   [AVAILABLE] - Return here to swap for another item

2. Shadowed stone pedestal wreathed in darkness
   Shadowweave Cloak - Sealed behind a shimmering arcane barrier
   [LOCKED]

... (3 more pedestals, all LOCKED)
```

### 3. Returning an Item

**Command**: `return treasure flamebrand longsword` or `return treasure longsword`

**Result**:
- Item removed from player inventory
- `currentlyTakenItem` cleared to null
- All pedestals transition to AVAILABLE state
- Barriers lift with atmospheric description:

```
You carefully replace the Flamebrand Longsword upon its altar. The eternal flames dim
as the weapon settles back into its resting place.

The arcane barriers shudder and fade, revealing the other treasures once more. Your
choice remains open.
```

### 4. Swapping Items

Players can swap items as many times as desired:

1. `take treasure stormcaller staff` → staff taken, others locked
2. `return treasure stormcaller staff` → staff returned, all unlocked
3. `take treasure shadowweave cloak` → cloak taken, others locked
4. `return treasure shadowweave cloak` → cloak returned, all unlocked
5. `take treasure arcane blade` → blade taken, others locked
6. (Leave room) → choice finalized

This unlimited swapping allows players to:
- Test items in their actual loadout
- Compare stats and bonuses
- Feel confident in their final choice

### 5. Leaving the Room (Finalization)

When a player moves to a different room while `currentlyTakenItem != null`:

**Automatic finalization**:
- `hasBeenLooted` set to true
- All pedestals transition to EMPTY state
- Exit narration displayed:

```
As you depart with the Arcane Blade, the remaining treasures shimmer and fade like
morning mist. The pedestals stand empty, their magic spent. Your choice is final.
```

**Re-entering the room**:
```
You return to the treasure chamber. The pedestals stand bare and cold, their magic
exhausted. Only faint echoes of power remain in the still air.
```

No interactions are available - the room is permanently emptied.

## Starter Items

The starter treasure room contains 5 RARE items, each aligned with a skill category:

### 1. Flamebrand Longsword (Combat)

**Category**: Combat - Melee Warrior
**Item Type**: WEAPON (one-handed)
**Rarity**: RARE

**Stats**:
- Damage: 25
- Weight: 4.5kg
- Value: 500 gold
- Skill Bonus: STR +3

**Description**: "A masterwork longsword wreathed in flickering flames. Its blade burns with eternal fire, and its hilt is warm to the touch. Warriors who wield this weapon find their strength amplified."

**Playstyle**: Direct melee combat with high damage output. The STR +3 bonus increases attack rolls and damage, making it ideal for front-line fighters who engage enemies directly.

### 2. Shadowweave Cloak (Rogue)

**Category**: Rogue - Stealth Specialist
**Item Type**: ARMOR (back slot)
**Rarity**: RARE

**Stats**:
- Defense: 8
- Weight: 1.0kg
- Value: 600 gold
- Skill Bonus: AGI +4

**Description**: "A midnight-black cloak that seems to absorb light. When worn, you feel the comforting embrace of shadows, as if the darkness itself shields you from prying eyes."

**Playstyle**: Stealth and evasion. The AGI +4 bonus improves stealth checks, dodge chance, and pickpocketing. Perfect for rogues who prefer to avoid or ambush enemies rather than face them head-on.

### 3. Stormcaller Staff (Magic)

**Category**: Magic - Spellcaster
**Item Type**: WEAPON (two-handed, spell focus)
**Rarity**: RARE

**Stats**:
- Damage: 20 (melee)
- Weight: 3.5kg
- Value: 700 gold
- Skill Bonuses: MAG +3, WIS +2

**Description**: "A gnarled oak staff topped with a crystal that crackles with lightning. Though meant for apprentices, its enchantment is potent—channeling magic through it feels effortless, like breathing."

**Playstyle**: Arcane magic and spellcasting. The MAG +3 and WIS +2 bonuses enhance spell power and mana efficiency. Two-handed weapon ideal for pure mages who rely on magical attacks.

### 4. Titan's Band (Utility)

**Category**: Utility - Survivalist
**Item Type**: ACCESSORY (ring slot)
**Rarity**: RARE

**Stats**:
- Defense: 0
- Weight: 0.1kg
- Value: 550 gold
- Skill Bonus: END +5

**Description**: "A heavy golden ring inscribed with ancient runes of fortitude. When worn, you feel invigorated, as if you could march for days without rest."

**Playstyle**: Survival and endurance. The END +5 bonus increases max HP and stamina, making it perfect for players who want to outlast enemies through durability rather than raw damage.

### 5. Arcane Blade (Hybrid)

**Category**: Hybrid - Spellsword
**Item Type**: WEAPON (one-handed, spell focus)
**Rarity**: RARE

**Stats**:
- Damage: 22
- Weight: 2.5kg
- Value: 650 gold
- Skill Bonuses: STR +2, MAG +3

**Description**: "A slender blade etched with arcane symbols that pulse with faint blue light. It feels balanced for both swordplay and spellcasting—a weapon for those who walk the line between warrior and mage."

**Playstyle**: Hybrid combat and magic. The STR +2 and MAG +3 bonuses allow for versatile builds that combine melee attacks with spellcasting. One-handed so it can be paired with a shield or off-hand item.

## Biome Theming

Treasure room descriptions adapt to the dungeon's biome, creating atmospheric consistency:

### Biome Themes

**Ancient Abyss** (default)
- **Material**: Weathered stone
- **Barrier Type**: Shimmering arcane barrier
- **Atmosphere**: Ancient, crumbling, moss-covered, weathered
- **Example**: "The altars are carved from weathered stone, covered in creeping moss. Faint runes glow along their edges."

**Magma Cave**
- **Material**: Obsidian
- **Barrier Type**: Wall of molten energy
- **Atmosphere**: Glowing, volcanic, heat-warped, smoldering
- **Example**: "The pedestals are hewn from black obsidian, their surfaces shimmering with heat. Molten veins pulse within the stone."

**Frozen Depths**
- **Material**: Ice crystal
- **Barrier Type**: Frozen barrier of solid ice
- **Atmosphere**: Frosted, glacial, pristine, crystalline
- **Example**: "The altars are sculpted from translucent ice crystal, glittering in the cold light. Frost patterns spiral across their surfaces."

**Bone Crypt**
- **Material**: Bone
- **Barrier Type**: Cage of blackened bone
- **Atmosphere**: Skeletal, macabre, dusty, grim
- **Example**: "The pedestals are constructed from ancient bones, bleached and dry. Femurs and ribs interlock to form grim stands."

**Elven Ruins**
- **Material**: Silver-veined marble
- **Barrier Type**: Translucent barrier of woven moonlight
- **Atmosphere**: Elegant, ancient, luminous, graceful
- **Example**: "The altars are carved from pristine marble shot through with veins of silver. They seem to glow with inner light."

**Dwarven Halls**
- **Material**: Granite
- **Barrier Type**: Mechanical barrier of interlocking gears
- **Atmosphere**: Sturdy, geometric, metallic, fortified
- **Example**: "The pedestals are hewn from solid granite, inlaid with brass mechanisms. Gears turn slowly along their bases."

### LLM-Generated Descriptions

The `TreasureRoomDescriptionGenerator` uses LLM calls (gpt-4o-mini) to generate atmospheric descriptions:

**Input**:
- Biome theme and properties
- Pedestal items and states
- Current taken item (if any)

**Output**:
- 2-3 paragraph room entry description
- State-aware pedestal descriptions
- Barrier descriptions matching biome

**Fallback Mode**: If no API key is configured, the system uses pre-written fallback descriptions to ensure the game remains playable.

## World Generation Integration

### Placement Logic

**TreasureRoomPlacer** (`reasoning/treasureroom/TreasureRoomPlacer.kt`) uses BFS (Breadth-First Search) to find optimal placement:

1. **Distance constraint**: 2-3 edges from starting node
2. **Node type priority**: DeadEnd > Linear > Branching
   - Dead ends create a "reward for exploration" feel
   - Linear nodes work if no dead ends available
   - Branching nodes avoided (reduces chance of accidentally skipping)
3. **One treasure room per dungeon**: Only one starter treasure room spawns
4. **Safe zone marking**: Treasure rooms flagged as safe (no combat/traps)

**Graph Topology**:
- `NodeType.TreasureRoom` added to graph types
- `SpacePropertiesComponent.isTreasureRoom` flag for identification
- Graph nodes persist in `graph_nodes` table (SQLite)

### Initialization Flow

When generating a new dungeon:

1. `DungeonInitializer` calls `TreasureRoomPlacer.selectTreasureRoomNode()`
2. Placer runs BFS to find candidate nodes at distance 2-3
3. Candidate with highest priority node type selected
4. `GraphNodeComponent` created with `NodeType.TreasureRoom`
5. `SpacePropertiesComponent` created with `isTreasureRoom=true`, `isSafeZone=true`
6. `TreasureRoomComponent` created from template (5 pedestals, biome theme)
7. All components persisted to database via repositories

### Database Schema

**treasure_rooms table**:
```sql
CREATE TABLE treasure_rooms (
    space_id TEXT PRIMARY KEY,
    room_type TEXT NOT NULL,              -- "STARTER", "COMBAT", etc.
    biome_theme TEXT NOT NULL,            -- "ancient_abyss", "magma_cave", etc.
    currently_taken_item TEXT,            -- Item template ID or null
    has_been_looted INTEGER NOT NULL,     -- 0 or 1
    FOREIGN KEY (space_id) REFERENCES spaces(id)
);
```

**pedestals table**:
```sql
CREATE TABLE pedestals (
    id TEXT PRIMARY KEY,
    treasure_room_id TEXT NOT NULL,
    item_template_id TEXT NOT NULL,
    state TEXT NOT NULL,                  -- "AVAILABLE", "LOCKED", "EMPTY"
    pedestal_index INTEGER NOT NULL,      -- 0-4 for starter rooms
    theme_description TEXT NOT NULL,
    FOREIGN KEY (treasure_room_id) REFERENCES treasure_rooms(space_id),
    FOREIGN KEY (item_template_id) REFERENCES item_templates(id)
);
```

## Commands Reference

### examine pedestals

**Aliases**: `examine altars`, `look at pedestals`, `inspect pedestals`

**Description**: Lists all pedestals in the treasure room with their items and states.

**Example Output**:
```
The treasure chamber contains 5 pedestals:

1. Weathered stone altar bearing a warrior's blade
   Flamebrand Longsword - A masterwork longsword wreathed in flickering flames
   [AVAILABLE]

2. Shadowed stone pedestal wreathed in darkness
   Shadowweave Cloak - A midnight-black cloak that seems to absorb light
   [AVAILABLE]

3. Glowing stone shrine pulsing with arcane energy
   Stormcaller Staff - A gnarled oak staff topped with a crystal that crackles
   [AVAILABLE]

4. Sturdy stone stand adorned with fortitude runes
   Titan's Band - A heavy golden ring inscribed with ancient runes
   [AVAILABLE]

5. Ornate stone dais marked with dual symbols
   Arcane Blade - A slender blade etched with arcane symbols
   [AVAILABLE]

You may take one treasure. Choose wisely.
```

### take treasure <item>

**Aliases**: `claim treasure <item>`, `take <item> from pedestal`

**Description**: Takes an item from a pedestal. Locks all other pedestals with magical barriers.

**Requirements**:
- Must be in a treasure room
- Room must not be looted
- No item currently taken
- Pedestal must be AVAILABLE
- Inventory weight limit not exceeded

**Example**:
```
> take treasure flamebrand longsword

You reach out and grasp the Flamebrand Longsword. Its warmth spreads through your
hand as eternal flames dance along the blade.

As you lift the sword, shimmering arcane barriers descend upon the other pedestals,
sealing the remaining treasures behind translucent walls of magic.

Flamebrand Longsword added to inventory.
```

### return treasure <item>

**Aliases**: `return <item>`, `place treasure <item> back`

**Description**: Returns the currently taken item to its pedestal. Unlocks all pedestals.

**Requirements**:
- Must be in a treasure room
- Item must match `currentlyTakenItem`
- Item must be in player inventory

**Example**:
```
> return treasure flamebrand longsword

You carefully replace the Flamebrand Longsword upon its altar. The eternal flames
dim as the weapon settles back into its resting place.

The arcane barriers shudder and fade, revealing the other treasures once more. Your
choice remains open.

Flamebrand Longsword removed from inventory.
```

## Walkthrough Example

### Full Treasure Room Experience

**Step 1: Discovery**
```
> north

You enter a vaulted chamber unlike any other in these depths. Five weathered stone
pedestals stand in a semicircle, each bearing a powerful artifact that glows with
faint luminescence. The air itself seems to hold its breath, heavy with ancient magic.

A whisper echoes through the chamber: "Choose wisely, traveler. You may claim one
treasure, but only one. Your choice will define your path."
```

**Step 2: Examination**
```
> examine pedestals

The treasure chamber contains 5 pedestals:

1. Weathered stone altar bearing a warrior's blade
   Flamebrand Longsword - A masterwork longsword wreathed in flickering flames
   [AVAILABLE]

2. Shadowed stone pedestal wreathed in darkness
   Shadowweave Cloak - A midnight-black cloak that seems to absorb light
   [AVAILABLE]

3. Glowing stone shrine pulsing with arcane energy
   Stormcaller Staff - A gnarled oak staff topped with a crackling crystal
   [AVAILABLE]

4. Sturdy stone stand adorned with fortitude runes
   Titan's Band - A heavy golden ring inscribed with ancient runes
   [AVAILABLE]

5. Ornate stone dais marked with dual symbols
   Arcane Blade - A slender blade etched with pulsing arcane symbols
   [AVAILABLE]
```

**Step 3: First Choice (Testing the Sword)**
```
> take treasure flamebrand longsword

You reach out and grasp the Flamebrand Longsword. Its warmth spreads through your
hand as eternal flames dance along the blade.

As you lift the sword, shimmering arcane barriers descend upon the other pedestals,
sealing the remaining treasures behind translucent walls of magic.

Flamebrand Longsword added to inventory.

> inventory
Carrying: Flamebrand Longsword (4.5kg), Rusty Dagger (1.0kg)
Weight: 5.5/50.0kg
```

**Step 4: Changing Mind**
```
> return treasure flamebrand longsword

You carefully replace the Flamebrand Longsword upon its altar. The eternal flames
dim as the weapon settles back into its resting place.

The arcane barriers shudder and fade, revealing the other treasures once more. Your
choice remains open.

Flamebrand Longsword removed from inventory.
```

**Step 5: Final Choice (Taking the Cloak)**
```
> take treasure shadowweave cloak

You lift the Shadowweave Cloak from its pedestal. It feels lighter than air, and
shadows seem to cling to your shoulders as you hold it.

Once again, the arcane barriers seal the other pedestals. Four treasures now lie
beyond your reach.

Shadowweave Cloak added to inventory.

> equip shadowweave cloak
Equipped Shadowweave Cloak to BACK slot.
Defense: +8, AGI: +4
```

**Step 6: Leaving (Finalization)**
```
> south

As you depart with the Shadowweave Cloak, the remaining treasures shimmer and fade
like morning mist. The pedestals stand empty, their magic spent. Your choice is final.

You leave the treasure chamber behind...
```

**Step 7: Re-entering**
```
> north

You return to the treasure chamber. The pedestals stand bare and cold, their magic
exhausted. Only faint echoes of power remain in the still air.

There is nothing left to claim here.
```

## Implementation Details

### File Locations

**Core Components**:
- `core/src/main/kotlin/com/jcraw/mud/core/TreasureRoomComponent.kt` (206 lines)
- `core/src/main/kotlin/com/jcraw/mud/core/ComponentType.kt` - TREASURE_ROOM enum value
- `core/src/main/kotlin/com/jcraw/mud/core/repository/TreasureRoomRepository.kt` (72 lines)

**Database & Persistence**:
- `memory/src/main/kotlin/com/jcraw/mud/memory/world/SQLiteTreasureRoomRepository.kt` (257 lines)
- `memory/src/main/kotlin/com/jcraw/mud/memory/world/TreasureRoomDatabase.kt` - Schema definitions
- `memory/src/main/resources/treasure_room_templates.json` (119 lines)

**Reasoning & Handlers**:
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/treasureroom/TreasureRoomHandler.kt` (218 lines)
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/treasureroom/TreasureRoomDescriptionGenerator.kt` (243 lines)
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/treasureroom/TreasureRoomPlacer.kt` (189 lines)
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/treasureroom/TreasureRoomExitLogic.kt` (37 lines)

**App Layer**:
- `app/src/main/kotlin/com/jcraw/app/handlers/TreasureRoomHandlers.kt` (280 lines)
- `perception/src/main/kotlin/com/jcraw/mud/perception/Intent.kt` - TakeTreasure, ReturnTreasure, ExaminePedestal intents

**GUI Client**:
- `client/src/main/kotlin/com/jcraw/mud/client/handlers/ClientTreasureRoomHandlers.kt` (250 lines)

**Tests**:
- `core/src/test/kotlin/com/jcraw/mud/core/TreasureRoomComponentTest.kt` (30 tests)
- `memory/src/test/kotlin/com/jcraw/mud/memory/world/TreasureRoomRepositoryTest.kt` (33 tests)
- `reasoning/src/test/kotlin/com/jcraw/mud/reasoning/treasureroom/TreasureRoomPlacerTest.kt` (26 tests)
- `reasoning/src/test/kotlin/com/jcraw/mud/reasoning/treasureroom/TreasureRoomExitLogicTest.kt` (8 tests)
- `app/src/test/kotlin/com/jcraw/app/integration/TreasureRoomIntegrationTest.kt` (8 tests)
- `app/src/test/kotlin/com/jcraw/app/integration/TreasureRoomWorldGenIntegrationTest.kt` (15 tests)

### Testing Coverage

**Unit Tests (30 tests)**:
- State transitions (take, return, mark looted)
- Swap sequences (take → return → take different item)
- Edge cases (already taken, invalid item, looted room)
- Pedestal locking/unlocking logic

**Integration Tests (89 tests)**:
- Database persistence (save/load roundtrip)
- Repository operations (CRUD for treasure rooms and pedestals)
- Placement logic (BFS distance, node type priority)
- Exit detection (leave room → finalization)
- World generation (treasure room in graph topology)

**Total Coverage**: 119 tests across 6 test files

## Future Enhancements

### Additional Room Types

**Combat Treasure Room** (TreasureRoomType.COMBAT)
- 3 pedestals with legendary weapons
- Spawns deeper in dungeon (floors 15-20)
- Items: Dragonslayer Greatsword, Vampiric Dagger, Sacred Warhammer

**Magic Treasure Room** (TreasureRoomType.MAGIC)
- 3 pedestals with powerful spellbooks/artifacts
- Spawns in magic-themed zones
- Items: Necromancer's Tome, Elemental Orb, Chronomancer's Hourglass

**Boss Treasure Room** (TreasureRoomType.BOSS)
- Unlocked after defeating a major boss
- Contains 1 LEGENDARY item
- One-time reward for significant achievement

### Enhanced Mechanics

**Cursed Items**: Items with powerful bonuses but drawbacks
- Example: "Blade of Hunger" - Massive damage but constant HP drain
- Curse revealed only after leaving room
- Adds risk/reward tension to choices

**Conditional Pedestals**: Require skill checks or items to access
- Example: "Sealed pedestal" - Requires Lockpicking 15 to unlock
- Creates build-specific choices and replayability

**Dynamic Item Quality**: Items scale to player level
- Keeps starter treasures relevant throughout game
- Quality = max(5, playerLevel / 2)

## See Also

- [Items and Crafting System](./ITEMS_AND_CRAFTING.md) - Item templates and inventory system
- [World Generation](./WORLD_GENERATION.md) - Dungeon generation and graph topology
- [Architecture](./ARCHITECTURE.md) - Component system and module structure
- [Getting Started](./GETTING_STARTED.md) - Basic commands and gameplay
