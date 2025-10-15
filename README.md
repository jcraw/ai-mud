# AI-Powered MUD Engine

A text-based Multi-User Dungeon (MUD) game with dynamic LLM-generated content, procedural generation, and RAG-enhanced memory.

## Features

**Fully Playable Single-Player Game** with:
- ✅ **LLM-Powered Descriptions** - GPT-4o-mini generates vivid room descriptions, NPC dialogue, and combat narratives
- ✅ **RAG Memory System** - Vector embeddings provide contextual history for all generated content
- ✅ **Turn-Based Combat** - Stat-based combat with STR modifiers, weapons, armor, and atmospheric narration
- ✅ **D&D-Style Skills** - D20 + modifier skill checks (STR, DEX, CON, INT, WIS, CHA)
- ✅ **Equipment System** - Equip weapons for damage bonuses, armor for defense bonuses
- ✅ **Consumables** - Use potions and items for healing
- ✅ **Interactive Features** - Skill challenges on environment objects (locked chests, stuck doors, hidden items)
- ✅ **Social Skills** - Persuade and intimidate NPCs with CHA checks
- ✅ **Quest System** - Procedurally generated quests with **automatic progress tracking** as you play
- ✅ **Procedural Generation** - 4 dungeon themes (Crypt, Castle, Cave, Temple) with dynamic layouts
- ✅ **Persistence** - Save and load game state to/from JSON files
- ✅ **Multi-User Architecture** - Foundation complete with PlayerId system and GameServer implementation
- ✅ **GUI Client** - Compose Multiplatform desktop client with full game integration

## Quick Start

### 1. Set up OpenAI API Key

Add your API key to `local.properties`:
```properties
openai.api.key=sk-your-key-here
```

Or set as environment variable:
```bash
export OPENAI_API_KEY="sk-your-key-here"
```

### 2. Build and Run

```bash
gradle installDist && app/build/install/app/bin/app
```

### 3. Select Dungeon Type

At startup, choose from:
- Sample Dungeon (handcrafted, 6 rooms)
- Procedural Crypt (ancient tombs)
- Procedural Castle (ruined fortress)
- Procedural Cave (dark caverns)
- Procedural Temple (forgotten shrine)

### 4. Play!

**Movement**: `n/s/e/w`, `north/south/east/west`, `go <direction>`

**Actions**:
- `look [target]` - Examine room or specific entity
- `take/get <item>` - Pick up items
- `drop/put <item>` - Drop items
- `talk/speak <npc>` - Talk to NPCs
- `attack/fight <npc>` - Start or continue combat
- `equip/wield <item>` - Equip weapons or armor
- `use/consume <item>` - Use healing potions
- `check/test <feature>` - Attempt skill checks on interactive features
- `persuade <npc>` - Attempt to persuade an NPC (CHA check)
- `intimidate <npc>` - Attempt to intimidate an NPC (CHA check)
- `inventory/i` - View inventory and equipped items

**Quests**: `quests/j`, `accept <id>`, `claim <id>`, `abandon <id>` (auto-tracks progress!)

**Meta**: `save [name]`, `load [name]`, `help`, `quit`

## Architecture

Multi-module Gradle project following clean architecture:

```
core       → World model (Room, Entity, WorldState, PlayerState)
perception → Input parsing (text → Intent objects)
reasoning  → LLM-powered generation and game logic (combat, skills, procedural gen)
action     → Output formatting and narration
memory     → RAG system, vector embeddings, persistence
llm        → OpenAI client (chat completion + embeddings)
app        → Console game interface + GameServer for multi-user
```

## Development

### Build
```bash
gradle build
```

### Run Tests
```bash
gradle test
```

### Run Specific Module Tests
```bash
gradle :core:test
gradle :reasoning:test
gradle :memory:test
```

### Integration Tests
```bash
./test_game.sh      # Full game playthrough
./test_combat.sh    # Combat system
./test_items.sh     # Item/equipment system
./test_procedural.sh # Procedural generation
```

## Project Guidelines

See `CLAUDE_GUIDELINES.md` for development principles:
- **KISS principle**: Avoid overengineering
- **Sealed classes** over enums
- **Behavior-driven testing**: Test contracts, not coverage
- **File size**: Keep modules under 300-500 lines
- **Cost optimization**: Use gpt-4o-mini for development

## Documentation

- **[CLAUDE.md](CLAUDE.md)** - Complete project overview, implementation status, and developer guide
- **[CODEX.md](CODEX.md)** - Companion guidance for the Codex CLI / ChatGPT agent
- **[CLAUDE_GUIDELINES.md](CLAUDE_GUIDELINES.md)** - Development principles and testing philosophy
- **[docs/requirements.txt](docs/requirements/requirements - mvp overall.txt)** - Original vision and specifications

**Note**: `CLAUDE.md` is the primary source of truth for project status and architecture.

## Current Status

**Feature-complete single-player MVP** with combat, equipment, skills, quest system with auto-tracking, procedural generation, persistence, RAG memory, and GUI client. Multi-user server fully operational in local mode.

See `CLAUDE.md` for detailed implementation status and next steps.
