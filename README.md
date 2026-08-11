# AI-Powered MUD Engine

A text-based Multi-User Dungeon (MUD) game with dynamic LLM-generated content, procedural generation, and RAG-enhanced memory.

## Features

**✅ PRODUCTION READY** - Fully playable game with all V2 systems complete:
- ✅ **Combat System V2** - Turn-based combat with equipment bonuses, boss mechanics, safe zones
- ✅ **Item System V2** - 53 item templates, inventory management, gathering, crafting, trading, pickpocketing
- ✅ **Skill System V2** - Use-based progression with perks, resource costs, social integration
- ✅ **Social System** - Emotes, persuasion, intimidation, NPC dialogue with disposition tracking
- ✅ **Quest System** - Procedurally generated quests with automatic progress tracking
- ✅ **World Generation V2** - Hierarchical procedural generation with exit resolution
- ✅ **Starting Dungeon** - Ancient Abyss with town, merchants, respawn system, boss fight
- ✅ **LLM Integration** - GPT-4o-mini for dynamic descriptions, NPC dialogue, combat narration
- ✅ **RAG Memory** - Vector embeddings with semantic search for contextual history
- ✅ **Persistence** - Complete save/load system for game state
- ✅ **Multi-User** - GameServer with concurrent players and thread-safe state
- ✅ **GUI Client** - Compose Multiplatform desktop client with real engine integration
- ✅ **Honest test baseline (2026-08-10)** — core lane green: core 462 + perception 56 + memory 321 + green reasoning 621; **23** `:reasoning` tests `@Tag("quarantine")` (see `docs/TEST_QUARANTINE.md`)

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
core       → World model (Room, Entity, WorldState, PlayerState, Quest, GameClient)
perception → Input parsing (text → Intent objects)
reasoning  → LLM-powered generation and game logic (combat, skills, world generation)
action     → Output formatting and narration
memory     → RAG system, vector embeddings, persistence
llm        → OpenAI client (chat completion + embeddings)
app        → Console game interface + GameServer for multi-user
client     → Compose Multiplatform GUI client
testbot    → Automated testing system with LLM validation
utils      → Shared utilities
```

## Development

### Build
```bash
gradle build
```

### Verify (agent / ticket default)
```bash
./tools/verify_mud.sh              # default/fast compile smoke
./tools/verify_mud.sh --core       # core + perception + memory + green :reasoning
./tools/verify_mud.sh --full       # stable green set (includes green :reasoning)
./tools/verify_mud.sh --quarantine # @Tag(quarantine) debt only (hard-fail OK)
./tools/verify_mud.sh --help
```
PRs and pushes to `master`/`main` run GitHub Actions [`.github/workflows/verify.yml`](.github/workflows/verify.yml) → `./tools/verify_mud.sh --core` (quarantine excluded; no live OpenAI).

### Run Tests
```bash
gradle test
# Default excludes @Tag("quarantine"). Known reds: docs/TEST_QUARANTINE.md
```

### Run Specific Module Tests
```bash
gradle :core:test
gradle :reasoning:test                          # green only (excludeTags quarantine)
gradle :reasoning:test -Pmud.quarantineOnly=true  # debt only
gradle :memory:test
```

## Project Guidelines

See `CLAUDE_GUIDELINES.md` for development principles:
- **KISS principle**: Avoid overengineering
- **Sealed classes** over enums
- **Behavior-driven testing**: Test contracts, not coverage
- **Maintainable file sizes**: All files under 1000 lines (largest is 910 lines)
- **Cost optimization**: Use gpt-4o-mini for development

## Documentation

- **[AGENTS.md](AGENTS.md)** - Ops contract, startup reads, and Definition of Done (all agents + humans)
- **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)** - Module structure, data flow, component details
- **[CLAUDE.md](CLAUDE.md)** - Optional deep / historical Claude-era status (do not full-read by default)
- **[CODEX.md](CODEX.md)** - Optional Codex-specific runtime notes (not ops SoT)
- **[CLAUDE_GUIDELINES.md](CLAUDE_GUIDELINES.md)** - Engineering style and testing philosophy (not mandatory startup)
- **[docs/requirements.txt](docs/requirements/requirements - mvp overall.txt)** - Original vision and specifications

**Note**: Ops, startup, and DoD SoT is **`AGENTS.md`**. Architecture detail lives in `docs/*`. `CLAUDE.md` is optional deep / historical status only.

## Current Status

**✅ PRODUCTION READY** - All V2 systems complete and integrated. Baseline 2026-08-10: green core/perception/memory + 621 green `:reasoning` tests; **23** quarantined (see `docs/TEST_QUARANTINE.md`). Game is fully playable in both console and GUI modes with single-player and multi-user support.

See `AGENTS.md` for ops/DoD, optional deep status in `CLAUDE.md` when needed, and `docs/TODO.md` for optional future enhancements.
