# Items and Crafting System

Comprehensive documentation for the Item System V2, covering inventory management, gathering, crafting, trading, and pickpocketing.

## Overview

The Item System V2 is a complete overhaul of the basic item system, introducing:
- **ECS-based architecture** with InventoryComponent and TradingComponent
- **Weight-limited inventory** tied to Strength skill
- **53 item templates** across 10 types with quality scaling (1-10)
- **Loot generation** with weighted drop tables and rarity tiers
- **Gathering system** with skill checks and tool requirements
- **Crafting system** with 24 recipes and D&D-style skill checks
- **Trading system** with disposition-based pricing and finite gold
- **Pickpocketing** with stealth mechanics and consequences
- **Multipurpose items** via tag-based system for emergent gameplay

## Architecture

### Component System

**InventoryComponent** (`core/InventoryComponent.kt`)
- Weight-based capacity (Strength * 5kg + bonuses)
- 12 equipment slots (HANDS_MAIN, HANDS_OFF, HEAD, CHEST, LEGS, FEET, BACK, HANDS_BOTH, ACCESSORY_1-4)
- Gold tracking
- Stack management for resources and consumables
- Equip/unequip methods with slot validation

**TradingComponent** (`core/TradingComponent.kt`)
- Finite merchant gold
- Stock management (List<ItemInstance>)
- Price modifiers (base + disposition)
- Buy-anything flag for general merchants

### Item Model

**ItemTemplate** (`core/ItemTemplate.kt`)
- Shared definitions loaded from `item_templates.json`
- Properties map for flexible attributes (weight, damage, defense, etc.)
- Tag system for multipurpose uses
- 53 templates across all types

**ItemInstance** (`core/ItemInstance.kt`)
- References template by ID
- Quality (1-10) affects damage/effectiveness
- Charges for consumables/tools
- Quantity for stackable items
- Unique ID for persistence

**Item Types** (`core/ItemType.kt`)
- WEAPON, ARMOR, CONSUMABLE, RESOURCE, QUEST, TOOL, CONTAINER, SPELL_BOOK, SKILL_BOOK, MISC

**Rarity Tiers** (`core/Rarity.kt`)
- COMMON (70%), UNCOMMON (25%), RARE (4%), EPIC (0.9%), LEGENDARY (0.1%)

## Systems

### Inventory Management

**Weight System**
- Base capacity = Strength * 5kg
- Bag bonuses from equipped containers (+10kg, +20kg, +50kg for different bags)
- Perk multipliers (Pack Mule +20%, Strong Back +15%, Hoarder +25%)
- Formula: `(Strength * 5 + bags) * (1 + perkMultipliers)`

**Equipment Slots**
- Main hand, off-hand, or both hands for 2H weapons
- Head, chest, legs, feet, back
- 4 accessory slots (rings, amulets)
- Two-handed weapons clear off-hand slot automatically

**Stack Management**
- Resources stack up to 99
- Consumables stack based on template
- Equipment and tools don't stack

### Loot System

**LootTable** (`core/LootTable.kt`)
- Weighted entries with drop chances
- Quality/quantity ranges
- Quality modifiers for source type
- Guaranteed drops and max drop limits

**LootGenerator** (`reasoning/loot/LootGenerator.kt`)
- Generates ItemInstances from loot tables
- Source-based quality modifiers:
  - COMMON_MOB: +0 quality
  - ELITE_MOB: +1 quality
  - BOSS: +2 quality
  - CHEST: +1 quality
  - QUEST: +2 quality (guaranteed)
  - FEATURE: +0 quality
- Gold drop with source multipliers and variance
- Automatic charge calculation for consumables/tools/books

**LootTableRegistry** (`reasoning/loot/LootTableRegistry.kt`)
- 9 predefined tables: goblin, skeleton, orc, dragon, chests, mining_iron, mining_gold, herbs, leather
- NPCs reference tables via `lootTableId` field
- Features use tables for harvestable resources

**Corpse Loot**
- NPC death creates corpse with loot table drops
- DeathHandler classifies NPCs by health (common/elite/boss)
- Corpses contain gold and items
- Looting via `loot`, `loot <item> from <corpse>`, `loot all from <corpse>`

### Gathering System

**Harvestable Features**
- Entity.Feature with `lootTableId` field
- Properties store tool requirements: `"required_tool_tag": "mining_tool"`
- SkillChallenge for harvest difficulty
- isCompleted boolean tracks finite resources

**Gathering Process**
1. Find harvestable feature in room
2. Check tool requirement (tag matching)
3. Perform D&D-style skill check (d20 + modifier vs DC)
4. On success: Generate loot via LootGenerator (FEATURE source), mark completed
5. Award XP: 50 on success, 25 on failure

**Tool Requirements**
- "mining_tool" for ore (pickaxe)
- "chopping_tool" for wood (axe)
- "fishing_tool" for fish (fishing rod)
- "alchemy_tool" for herbs (alchemy kit)
- Hands allowed for some features (herbs without kit)

**Commands**
- `interact <feature>`, `harvest <feature>`, `gather <resource>`

### Crafting System

**Recipe Model** (`core/Recipe.kt`)
- Input items: Map<templateId, quantity>
- Output item: templateId
- Required skill and minimum level
- Required tools (tag list)
- Difficulty (DC for skill check)

**Crafting Process**
1. Find recipe by name (case-insensitive)
2. Validate skill level (meets requirement)
3. Check tool requirements (tag matching)
4. Check materials in inventory
5. Perform D&D-style skill check (d20 + skill vs difficulty)
6. On success:
   - Create item with quality = skill level / 10 (clamped 1-10)
   - Consume inputs
   - Award XP: 50 + difficulty * 5
7. On failure:
   - Consume 50% of inputs
   - Award XP: 10 + difficulty

**24 Preloaded Recipes** (`recipes.json`)
- 5 weapons: Iron Sword, Steel Axe, Longbow, Dagger, Wooden Club
- 4 armor: Leather Armor, Chain Mail, Iron Helm, Leather Boots
- 5 consumables: Health Potion, Mana Potion, Antidote, Greater Healing Elixir, Bandage
- 3 tools: Pickaxe, Woodcutter's Axe, Fishing Rod
- 2 containers: Leather Bag, Large Backpack
- 5 misc: Dynamite, Torch, Rope, Arrow Batch, Campfire Kit

**Skills Used**
- Blacksmithing (weapons, armor, tools)
- Woodworking (bows, arrows, handles)
- Leatherworking (armor, bags)
- Alchemy (potions, elixirs)
- Healing (bandages, antidotes)
- Crafting (general items)
- Survival (campfires, ropes, torches)

**Commands**
- `craft <recipe>`

### Trading System

**Merchant Setup**
- NPCs with TradingComponent
- Finite gold (e.g., 500 for village merchant)
- Stock: List<ItemInstance>
- buyAnything flag (true for general merchants)
- priceModBase (1.0 = normal pricing)

**Price Calculation**
```kotlin
price = basePrice * priceModBase * (1.0 + (disposition - 50) / 100)
```

**Examples**:
- Disposition 70 (friendly): 0.8x price (20% discount)
- Disposition 50 (neutral): 1.0x price
- Disposition 30 (unfriendly): 1.2x price (20% markup)

**Trading Process**
- Buy: Player gold check, weight check, merchant stock depletes, merchant gold increases
- Sell: Player can't sell equipped items, merchant gold check, stock replenishes, player gold increases

**Commands**
- `buy <item>`, `buy <item> from <merchant>`
- `sell <item>`, `sell <item> to <merchant>`
- `list stock` - View merchant inventory with prices

### Pickpocketing System

**Skill Check**
```kotlin
playerRoll = d20 + max(Stealth, Agility)
targetDC = 10 + target.WIS modifier + target.Perception skill + wariness bonus
```

**Actions**
- Steal gold from NPC
- Steal specific item from NPC
- Place item on NPC (sneaky tactics)

**Consequences on Failure**
- Disposition penalty: -20 to -50 (based on margin of failure)
- Wariness status: +20 to Perception DC for 10 turns
- Wariness stacks with existing Perception skill

**Commands**
- `pickpocket <npc>` - Attempt to steal
- `steal <item> from <npc>` - Steal specific item
- `place <item> on <npc>` - Place item on NPC

### Multipurpose Items

**Tag System**
Items have tags that enable alternative uses:
- "blunt" → improvised weapon (damage = weight * 0.5)
- "sharp" → improvised weapon (damage = weight * 0.5)
- "explosive" → detonate with AoE damage and timer
- "container" → capacity bonus
- "flammable" → can be burned
- "fragile" → can be broken
- "liquid" → can be poured

**Examples**
- Clay pot: "container", "blunt", "fragile" → Can store items, bash enemies, or break for distraction
- Dynamite: "explosive", "throwable", "timed" → Place in NPC pocket via pickpocket, detonate remotely
- Sword: "sharp", "metal" → Weapon or improvised cutting tool

**Commands**
- `use <item>` - Determines use based on tags and context
- System checks tags and prompts for specific action if multiple uses available

## Database Persistence

**ItemDatabase** (`memory/item/ItemDatabase.kt`)
- **item_templates**: id, name, type, tags (JSON), properties (JSON), rarity, description, equip_slot
- **item_instances**: id, template_id, quality, charges, quantity
- **inventories**: entity_id, items (JSON), equipped (JSON), gold, capacity_weight
- **recipes**: id, name, input_items (JSON), output_item, required_skill, min_skill_level, required_tools (JSON), difficulty
- **trading_stocks**: entity_id, merchant_gold, stock (JSON), buy_anything, price_mod_base

**Repositories**
- ItemRepository, InventoryRepository, RecipeRepository, TradingRepository
- SQLite implementations with JSON serialization for complex fields
- Bulk operations for initial data load

## Integration with Other Systems

### Skills System
- **Capacity calculation**: Uses Strength skill and perks
- **Gathering XP**: Awards skill XP on harvest attempts
- **Crafting XP**: Awards skill XP on crafting attempts
- **Equipped bonuses**: Items provide skill bonuses via properties (e.g., "sword_fighting_bonus": 2)

### Combat System
- **Weapon damage**: Added to base damage calculation
- **Quality multiplier**: Quality 1-10 scales damage (0.8x to 2.0x)
- **Armor defense**: Subtracted from incoming damage
- **Improvised weapons**: Multipurpose items can be used in combat

### Social System
- **Disposition affects pricing**: Friendly NPCs give discounts
- **Pickpocket consequences**: Failed attempts damage disposition
- **Wariness status**: Applied after failed pickpocketing
- **Knowledge system**: NPCs remember being robbed

### Quest System
- **Quest rewards**: Can include specific items
- **Collect objectives**: Track looted/gathered items
- **Craft objectives**: (Future) Require crafting specific items

## Testing

**Unit Tests**
- InventoryComponentTest (39 tests): Weight, capacity, equip/unequip, gold
- LootTableTest (22 tests): Weighted selection, quality modifiers
- LootGeneratorTest (21 tests): Item generation, gold drops, charge calculation
- CapacityCalculatorTest (14 tests): Strength scaling, bag/perk bonuses
- PickpocketHandlerTest (12 tests): Skill checks, disposition, wariness
- ItemUseHandlerTest (20 tests): Tag-based uses, improvised weapons

**Integration Tests**
- ItemDatabaseTest (26 tests): Template/instance persistence, JSON roundtrip
- InventoryDatabaseTest (24 tests): Inventory save/load, equipped items
- Full playthrough scenarios in testbot module

## Future Enhancements

- **LLM-based ad-hoc recipes**: Generate recipes on-the-fly for creative combinations
- **Resource respawn**: Time-based respawn for gathering nodes
- **Specialist merchants**: Merchants that only buy/sell specific item types
- **Item durability**: Wear and repair mechanics
- **Enchantments**: Magic item properties and upgrades
- **Item sets**: Bonuses for wearing matching equipment
- **Player trading**: Direct player-to-player trades (multi-user mode)
- **Auction house**: Asynchronous player marketplace

## Example Gameplay Flow

1. **Start**: Player spawns with empty inventory, 50 gold
2. **Explore**: Find iron ore node in cave
3. **Gather**: `interact iron ore` → Skill check (Mining DC 12) → Success! +2 iron ore, +50 XP
4. **Craft**: `craft iron sword` → Skill check (Blacksmithing DC 12) → Success! Iron Sword (quality 5), +55 XP
5. **Equip**: `equip iron sword` → +10 damage in combat
6. **Combat**: Kill goblin → Corpse drops: 15 gold, rusty dagger
7. **Loot**: `loot all from goblin corpse` → +15 gold, +rusty dagger
8. **Trade**: Find merchant → `sell rusty dagger` → +8 gold (disposition 50, 1.0x price)
9. **Buy**: `buy health potion` → -20 gold, +health potion (quality 3)
10. **Use**: `use health potion` → Restore 30 HP (quality 3, base 25 + 5 quality bonus)

## File Locations

See [Architecture Documentation](./ARCHITECTURE.md#item-system-v2) for complete file locations.

**Key Files**:
- Core: `core/src/main/kotlin/com/jcraw/mud/core/` (components, templates, enums)
- Reasoning: `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/` (loot, crafting, trade, pickpocket, items)
- Memory: `memory/src/main/kotlin/com/jcraw/mud/memory/item/` (database, repositories)
- Handlers: `app/src/main/kotlin/com/jcraw/app/handlers/` (item, trade, pickpocket handlers)
- Resources: `memory/src/main/resources/` (item_templates.json, recipes.json)

## Commands Quick Reference

See [Getting Started Guide](./GETTING_STARTED.md#available-commands) for complete command reference.

**Inventory**: `inventory`, `i`
**Equip**: `equip <item>`, `unequip <item>`
**Loot**: `loot <corpse>`, `loot <item> from <corpse>`, `loot all from <corpse>`
**Gather**: `interact <feature>`, `harvest <feature>`, `gather <resource>`
**Craft**: `craft <recipe>`
**Trade**: `buy <item>`, `sell <item>`, `list stock`
**Pickpocket**: `pickpocket <npc>`, `steal <item> from <npc>`, `place <item> on <npc>`
**Use**: `use <item>`
