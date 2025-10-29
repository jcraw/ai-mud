# Getting Started

Quick start guide for developers and players.

## Setup

### Prerequisites
- Java 17 or higher
- Gradle (wrapper included)
- OpenAI API key (optional - game works without it in fallback mode)

### API Key Configuration
Add your OpenAI API key using one of these methods:

**Option 1: local.properties** (recommended)
```properties
openai.api.key=sk-your-key-here
```

**Option 2: Environment variable**
```bash
export OPENAI_API_KEY=sk-your-key-here
```

## Build Commands

```bash
# Build the entire project
gradle build

# Run all tests
gradle test

# Clean build outputs
gradle clean

# Build and run the game
gradle installDist && app/build/install/app/bin/app

# Build just the app module
gradle :app:build
```

## Testing Commands

```bash
# Run tests for specific modules
gradle :core:test
gradle :reasoning:test
gradle :memory:test
gradle :app:test

# Run the automated test bot (requires OpenAI API key)
gradle :testbot:run
```

Test logs are saved to `test-logs/` directory (JSON + human-readable text).

## Running the Game

1. **Start the game**:
   ```bash
   gradle installDist && app/build/install/app/bin/app
   ```

2. **Choose game mode**:
   - **Single-player mode** - Traditional single-player experience with save/load support
   - **Multi-user mode (local)** - Uses GameServer architecture (network support coming soon)

3. **Select dungeon type**:
   - Sample Dungeon (handcrafted, 6 rooms)
   - Procedural Crypt (ancient tombs)
   - Procedural Castle (ruined fortress)
   - Procedural Cave (dark caverns)
   - Procedural Temple (forgotten shrine)
   - Specify room count for procedural dungeons (default: 10)

## Available Commands

### Movement
- `n`, `s`, `e`, `w` - Short directions
- `north`, `south`, `east`, `west` - Full direction names
- `ne`, `nw`, `se`, `sw` - Diagonal directions
- `u`, `d`, `up`, `down` - Vertical movement
- `go <direction>` - Explicit movement

### Interaction
- `look` or `look <target>` - Examine room or specific object
- `take <item>` or `get <item>` - Pick up an item
- `drop <item>` or `put <item>` - Drop an item from inventory
- `talk <npc>` or `speak <npc>` - Talk to an NPC
- `inventory` or `i` - View your inventory and equipped items

### Combat
- `attack <npc>` or `fight <npc>` - Initiate combat with an NPC
- `attack` - Continue attacking in combat
- `kill <npc>`, `hit <npc>` - Alternative attack commands

### Equipment
- `equip <item>` - Equip a weapon or armor from inventory
- `wield <item>`, `wear <item>` - Alternative equip commands

### Consumables
- `use <item>` - Use a healing potion or consumable
- `consume <item>`, `drink <item>`, `eat <item>` - Alternative use commands

### Looting
- `loot <corpse>` - Inspect corpse contents
- `loot <item> from <corpse>` - Loot a specific item from a corpse
- `loot all from <corpse>` - Take all items and gold from a corpse

### Gathering
- `interact <feature>` - Harvest resources from harvestable features
- `harvest <feature>`, `gather <resource>` - Alternative gathering commands
- Features may require specific tools (pickaxe for mining, axe for trees, etc.)

### Crafting
- `craft <recipe>` - Craft an item using a recipe
- Requires materials, tools, and sufficient skill level
- Recipes can be discovered through experimentation

### Trading
- `buy <item>` - Buy an item from a merchant in the room
- `sell <item>` - Sell an item to a merchant
- `buy <item> from <merchant>` - Buy from a specific merchant
- `sell <item> to <merchant>` - Sell to a specific merchant
- `list stock` - View merchant inventory and prices (affected by disposition)

### Pickpocketing
- `pickpocket <npc>` - Attempt to steal from an NPC
- `steal <item> from <npc>` - Steal a specific item
- `place <item> on <npc>` - Secretly place an item on an NPC
- Failed attempts damage disposition and apply wariness status (+20 Perception)

### Skill Checks
- `check <feature>` - Attempt a skill check on an interactive feature
- `test <feature>`, `attempt <feature>`, `try <feature>` - Alternative check commands
- `persuade <npc>` or `convince <npc>` - Attempt to persuade an NPC (CHA check)
- `intimidate <npc>` or `threaten <npc>` - Attempt to intimidate an NPC (CHA check)

### Social Interactions
- `smile` or `smile at <npc>` - Smile at an NPC (+2 disposition)
- `wave` or `wave at <npc>` - Wave at an NPC (+1 disposition)
- `nod` or `nod at <npc>` - Nod at an NPC (+1 disposition)
- `shrug` or `shrug at <npc>` - Shrug at an NPC (no effect)
- `laugh` or `laugh at <npc>` - Laugh at an NPC (-1 disposition)
- `cry` or `cry at <npc>` - Cry in front of an NPC (no effect)
- `bow` or `bow at <npc>` - Bow to an NPC (+2 disposition)
- `ask <npc> about <topic>` - Ask an NPC about a topic (builds knowledge base)

### Quests
- `quests` or `quest` or `journal` or `j` - View quest log and available quests
- `accept <quest_id>` - Accept an available quest
- `abandon <quest_id>` - Abandon an active quest
- `claim <quest_id>` - Claim reward for a completed quest

### Meta Commands
- `save [name]` - Save game (defaults to 'quicksave')
- `load [name]` - Load game (defaults to 'quicksave')
- `help` or `h` or `?` - Show available commands
- `quit` or `exit` or `q` - Quit game

## Gameplay Features

### Dungeon Types
- **Sample dungeon**: 6 handcrafted rooms with items (weapons, armor, potions, gold) and NPCs (Old Guard, Skeleton King)
- **Procedural dungeons**: Dynamically generated with theme-appropriate traits, random NPCs, and distributed loot

### LLM Features (with API key)
- **Room descriptions**: Dynamically generated using gpt-4o-mini with RAG context
- **NPC dialogue**: Personality-driven (friendly vs hostile, health-aware) with conversation history
- **Combat narratives**: Visceral, atmospheric descriptions that build on previous rounds
- **Embeddings**: via text-embedding-3-small for semantic memory retrieval

### Game Mechanics

**Item System V2**
- **Inventory**: Weight-based capacity (Strength * 5kg + bonuses from bags/perks)
- **53 item templates**: Weapons, armor, consumables, resources, tools, containers, quest items
- **Equipment**: 12 equip slots with quality scaling (1-10), skill bonuses, damage/defense modifiers
- **Looting**: Corpses contain items and gold based on loot tables and equipped gear
- **Gathering**: Harvest resources from features with skill checks and tool requirements
- **Crafting**: 24 recipes using materials, tools, and skills (D&D-style checks)
- **Trading**: Buy/sell from merchants with disposition-based pricing and finite gold
- **Pickpocketing**: Steal from NPCs (Stealth/Agility vs Perception) with consequences
- **Multipurpose items**: Tag-based system (blunt→weapon, explosive→detonate, container→storage)

**NPC Interaction**
- Talk to NPCs with `talk` command
- Get contextual, personality-driven responses
- Responses reference past conversations (RAG memory)

**Combat System**
- Turn-based combat with health tracking
- Weapon damage bonuses (e.g., iron sword +5)
- Armor defense bonuses (e.g., chainmail +4)
- Victory and defeat conditions
- LLM-narrated combat events

**Skill System**
- D&D-style stats: STR, DEX, CON, INT, WIS, CHA
- d20 + modifier vs Difficulty Class
- Critical successes (nat 20) and failures (nat 1)
- Sample dungeon has 4 interactive challenges:
  - Loose stone (WIS/DC10) - Find hidden items
  - Locked chest (DEX/DC15) - Pick locks
  - Stuck door (STR/DC20) - Force open
  - Arcane runes (INT/DC15) - Decipher magic

**Social System**
- Express emotions through emotes (smile, wave, nod, shrug, laugh, cry, bow)
- Ask NPCs questions to learn about topics
- Build relationships through disposition system (-100 to +100)
- Disposition affects dialogue tone and NPC behavior
- Quest completion grants +15 disposition with quest giver
- Persuade NPCs (CHA check) to reveal information
- Intimidate NPCs (CHA check) to avoid combat
- All interactions and knowledge persist in database
- See [Social System Documentation](./SOCIAL_SYSTEM.md) for details

**Quest System**
- Procedurally generated quests based on dungeon
- 6 objective types: Kill, Collect, Explore, Talk, UseSkill, Deliver
- Rewards: Experience points, gold, items
- Track progress in quest log
- Claim rewards upon completion

**RAG Memory**
- All game events stored with embeddings
- Retrieved contextually for LLM prompts
- Makes descriptions and dialogue more immersive
- Remembers your actions and decisions

**Procedural Generation**
- Create dungeons of any size
- 4 themes with unique traits
- NPCs with varied stats and personalities
- Distributed loot (weapons, armor, consumables, gold)

**Persistence**
- Save and load game state to/from JSON files
- Preserves all state: player stats, inventory, equipment, combat, world
- Multiple save slots supported
- Saves stored in `saves/` directory

**Multi-User Mode**
- Server-based architecture
- Multiple players in shared world
- Per-player combat state
- Event broadcasting for player visibility
- Thread-safe state management
- Ready for network layer (coming soon)

## Troubleshooting

### No API Key
Game works without an API key! You'll get simpler descriptions instead of LLM-generated ones.

### Build Errors
```bash
# Clean and rebuild
gradle clean build

# Check Java version
java -version  # Should be 17 or higher
```

### Save/Load Issues
Save files are in `saves/` directory as human-readable JSON. You can inspect or manually edit them if needed.

## Next Steps

Once you're comfortable with the basics:
1. Try different dungeon themes and sizes
2. Experiment with skill checks (you'll need high stats for harder checks)
3. Complete quests for XP and gold
4. Test the multi-user mode (great for testing multi-player features)
5. Check out the test bot to see autonomous gameplay

For more details, see:
- [Architecture Documentation](./ARCHITECTURE.md)
- [Social System Documentation](./SOCIAL_SYSTEM.md)
- [Items and Crafting Documentation](./ITEMS_AND_CRAFTING.md)
- [Multi-User Details](./MULTI_USER.md)
- [Implementation Log](./IMPLEMENTATION_LOG.md)
