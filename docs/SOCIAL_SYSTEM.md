# Social System V2

The Social System V2 provides rich NPC interactions through emotes, questions, and a dynamic disposition system with persistent knowledge tracking.

## Overview

The social system allows players to:
- **Express emotions** through emotes (smile, wave, nod, etc.)
- **Ask NPCs questions** about topics and receive contextual answers
- **Build relationships** through disposition tracking
- **Influence NPC behavior** based on relationship status
- **Persistent memory** of all social interactions and knowledge

## Core Concepts

### SocialComponent

Every NPC can have a `SocialComponent` attached, which tracks:
- **Disposition** - Numeric value (-100 to +100) representing relationship status
- **Personality** - NPC's core personality (e.g., "Gruff warrior", "Wise elder")
- **Traits** - List of character traits (e.g., "Honorable", "Greedy", "Paranoid")

Disposition tiers affect NPC behavior:
- **ALLIED** (75-100): NPCs are very helpful, may offer discounts or special quests
- **FRIENDLY** (25-74): NPCs are cooperative and willing to help
- **NEUTRAL** (-24 to 24): Default behavior, NPCs are indifferent
- **UNFRIENDLY** (-74 to -25): NPCs are cold or dismissive
- **HOSTILE** (-100 to -75): NPCs are aggressive or refuse interaction

### Knowledge System

NPCs maintain knowledge about topics through:
- **Knowledge entries** - Facts about entities, stored with canonicity flags
- **Canon generation** - LLM determines what is "true" based on conversation context
- **Persistence** - All knowledge saved to SQLite database

### Social Events

All social interactions create `SocialEvent` records:
- **Emotes** - Player expresses emotion toward NPC
- **Questions** - Player asks NPC about a topic
- **Quest completion** - Player completes quest for NPC (+15 disposition bonus)

## Emote System

Players can express emotions through 7 emote types:

| Emote | Command | Effect | Example Narrative |
|-------|---------|--------|-------------------|
| **Smile** | `smile` or `smile at <npc>` | +2 disposition | "You smile warmly at the Guard. They seem pleased." |
| **Wave** | `wave` or `wave at <npc>` | +1 disposition | "You wave at the Merchant. They wave back casually." |
| **Nod** | `nod` or `nod at <npc>` | +1 disposition | "You nod respectfully at the Elder. They nod in return." |
| **Shrug** | `shrug` or `shrug at <npc>` | 0 disposition | "You shrug at the Stranger. They seem indifferent." |
| **Laugh** | `laugh` or `laugh at <npc>` | -1 disposition | "You laugh at the Knight. They look offended." |
| **Cry** | `cry` or `cry at <npc>` | 0 disposition | "You cry in front of the Priest. They offer comfort." |
| **Bow** | `bow` or `bow at <npc>` | +2 disposition | "You bow deeply to the King. They look pleased." |

**Usage:**
```
> smile at Guard
You smile warmly at the Guard. They seem pleased by your friendly gesture.

> wave
You wave at the nearby NPCs. Several wave back.
```

## Ask Question System

Players can ask NPCs about topics to learn information:

**Syntax:**
```
ask <npc> about <topic>
```

**Examples:**
```
> ask Guard about the castle
The Guard tells you: "This castle was built centuries ago by the old kings..."

> ask Merchant about potions
The Merchant tells you: "Healing potions are our specialty! Made from rare herbs..."

> ask Elder about the quest
The Elder tells you: "The ancient artifact you seek lies deep in the crypt..."
```

**How it works:**
1. Player asks question
2. System searches NPC's knowledge base for relevant information
3. If knowledge exists, NPC provides factual answer
4. If no knowledge, LLM generates answer and determines canon facts
5. Canon facts stored in knowledge base for future reference
6. All questions/answers recorded as social events

## Disposition System

### Disposition Changes

Disposition changes based on interactions:

| Event | Change |
|-------|--------|
| Smile or Bow | +2 |
| Wave or Nod | +1 |
| Shrug or Cry | 0 |
| Laugh | -1 |
| Quest completion (as quest giver) | +15 |
| Combat attack | -50 |

### Disposition Effects

Disposition tier affects:
- **Dialogue tone** - LLM generates responses matching relationship status
- **Quest availability** - Some quests require minimum disposition
- **Shop prices** - Allied NPCs may offer discounts (future feature)
- **Combat behavior** - Friendly NPCs less likely to attack (future feature)

### Example Progression

```
Starting disposition: 0 (NEUTRAL)
> smile at Guard        → +2 (NEUTRAL)
> ask about castle      → 0 (NEUTRAL)
> nod at Guard          → +1 (NEUTRAL, total +3)
> wave at Guard         → +1 (NEUTRAL, total +4)
> complete quest        → +15 (NEUTRAL, total +19)
> smile at Guard        → +2 (NEUTRAL, total +21)
> bow at Guard          → +2 (NEUTRAL, total +23)
> nod at Guard          → +1 (NEUTRAL, total +24)
> wave at Guard         → +1 (FRIENDLY, total +25) ✓ Tier change!
```

## Procedural Generation

NPCGenerator automatically creates NPCs with SocialComponents:

### Theme-Based Personalities

Each dungeon theme has curated personality pools:

**Crypt** (undead/necromancy):
- "Ancient revenant bound by dark magic"
- "Skeletal warrior from a forgotten war"
- "Spectral guardian of the crypt"
- etc.

**Castle** (knights/nobility):
- "Fallen knight who guards the ruins"
- "Ghostly noble from ages past"
- "Corrupted royal guard"
- etc.

**Cave** (beasts/primitives):
- "Feral creature adapted to darkness"
- "Ancient beast guardian of the caves"
- "Savage tribal warrior"
- etc.

**Temple** (cultists/divine):
- "Fanatic cultist devoted to dark gods"
- "Corrupted priest seeking forbidden power"
- "Ancient temple guardian"
- etc.

### Trait Generation

NPCs receive traits based on type:
- **Hostile NPCs**: 1-3 traits (e.g., "Aggressive", "Territorial", "Ruthless")
- **Friendly NPCs**: 1-3 traits (e.g., "Helpful", "Wise", "Cautious")
- **Boss NPCs**: 2-4 traits (e.g., "Ancient", "Powerful", "Cunning")

### Disposition Initialization

Starting disposition varies by NPC type:
- **Hostile**: -75 to -25 (UNFRIENDLY to HOSTILE)
- **Friendly**: 25 to 60 (FRIENDLY)
- **Boss**: -100 to -75 (HOSTILE)

All generation is **deterministic** - same seed produces identical NPCs.

## Database Architecture

Social data persists in SQLite with 3 tables:

### knowledge_entries
```sql
CREATE TABLE knowledge_entries (
    id TEXT PRIMARY KEY,
    entity_id TEXT NOT NULL,     -- NPC who knows this
    topic TEXT NOT NULL,          -- What the knowledge is about
    content TEXT NOT NULL,        -- The actual knowledge
    is_canon INTEGER NOT NULL,    -- 1 if confirmed true, 0 if uncertain
    source TEXT NOT NULL,         -- CONVERSATION, OBSERVATION, QUEST, etc.
    tags TEXT NOT NULL,           -- JSON map of metadata
    timestamp INTEGER NOT NULL
)
```

### social_events
```sql
CREATE TABLE social_events (
    id TEXT PRIMARY KEY,
    entity_id TEXT NOT NULL,     -- NPC involved
    event_type TEXT NOT NULL,    -- EMOTE, QUESTION, QUEST_COMPLETE
    details TEXT NOT NULL,       -- JSON event data
    disposition_change INTEGER NOT NULL,
    timestamp INTEGER NOT NULL
)
```

### social_components
```sql
CREATE TABLE social_components (
    entity_id TEXT PRIMARY KEY,
    disposition INTEGER NOT NULL,
    personality TEXT NOT NULL,
    traits TEXT NOT NULL         -- JSON array
)
```

### Repositories

Three repository interfaces handle persistence:
- **KnowledgeRepository** - Store/retrieve knowledge entries
- **SocialEventRepository** - Store/retrieve social events
- **SocialComponentRepository** - Store/retrieve social component state

## System Integration

### Quest Integration

When a quest is completed:
1. `QuestTracker.claimReward()` detects quest giver NPC
2. If NPC has `SocialComponent`, disposition increases by +15
3. Creates `QUEST_COMPLETE` social event
4. Updates NPC in world state

### Memory Integration

`MemoryManager` enhanced with metadata filtering:
```kotlin
// Search for knowledge about specific entity
val knowledge = memoryManager.recallWithMetadata(
    query = "What does the guard know about the castle?",
    metadata = mapOf("entityId" to "guard_01")
)
```

### Dialogue Integration

`NPCInteractionGenerator` uses disposition for dialogue tone:
```kotlin
// Friendly disposition (25-74)
"Hello friend! How can I help you today?"

// Hostile disposition (-100 to -75)
"What do you want? Make it quick before I lose my patience."
```

## File Locations

### Core Data Models
- `core/src/main/kotlin/com/jcraw/mud/core/Component.kt` - Component system foundation
- `core/src/main/kotlin/com/jcraw/mud/core/SocialComponent.kt` - Social component data
- `core/src/main/kotlin/com/jcraw/mud/core/SocialEvent.kt` - Event types

### Database Layer
- `memory/src/main/kotlin/com/jcraw/mud/memory/social/SocialDatabase.kt` - SQLite connection
- `memory/src/main/kotlin/com/jcraw/mud/memory/social/KnowledgeRepository.kt` - Knowledge persistence
- `memory/src/main/kotlin/com/jcraw/mud/memory/social/SocialEventRepository.kt` - Event persistence
- `memory/src/main/kotlin/com/jcraw/mud/memory/social/SocialComponentRepository.kt` - Component persistence

### Core Logic
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/social/DispositionManager.kt` - Disposition tracking
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/social/EmoteHandler.kt` - Emote processing
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/social/NPCKnowledgeManager.kt` - Knowledge queries

### Intent Recognition
- `perception/src/main/kotlin/com/jcraw/mud/perception/Intent.kt` - Emote and AskQuestion intents

### Procedural Generation
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/procedural/NPCGenerator.kt` - NPC creation with social components

### Integration Points
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/QuestTracker.kt` - Quest completion bonuses
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/NPCInteractionGenerator.kt` - Disposition-aware dialogue
- `memory/src/main/kotlin/com/jcraw/mud/memory/MemoryManager.kt` - Metadata filtering

### Tests
- `core/src/test/kotlin/com/jcraw/mud/core/ComponentSystemTest.kt` - Component tests (30 tests)
- `memory/src/test/kotlin/com/jcraw/mud/memory/social/SocialDatabaseTest.kt` - Database tests (17 tests)
- `app/src/test/kotlin/com/jcraw/mud/app/SocialSystemV2IntegrationTest.kt` - Integration tests (18 tests)
- `reasoning/src/test/kotlin/com/jcraw/mud/reasoning/procedural/NPCGeneratorTest.kt` - Generation tests (19 tests)

## Usage Examples

### Basic Emote Interaction
```
> look
You are in the Throne Room. A royal guard stands at attention.

> smile at Guard
You smile warmly at the Guard. They seem pleased by your friendly gesture.

> wave at Guard
You wave at the Guard. They wave back casually.

> talk Guard
The Guard says: "Good day to you, friend! How may I assist?"
```

### Knowledge Acquisition
```
> ask Guard about the castle
The Guard tells you: "This castle was built five centuries ago by King Aldric..."

> ask Guard about the king
The Guard tells you: "King Aldric was a wise and just ruler. His descendants ruled for three generations..."

> ask Guard about the treasury
The Guard tells you: "The treasury is said to be sealed in the eastern vault, but the key was lost long ago..."
```

### Disposition Building
```
> quests
Available Quests:
[1] "Guard the Gate" (from Guard) - Reward: 50 gold, 100 XP

> accept 1
You accepted the quest "Guard the Gate"!

> kill Goblin
[Combat occurs...]
You defeated the Goblin!
Quest objective completed: Kill 1 goblin

> claim 1
You completed "Guard the Gate"!
Rewards: 50 gold, 100 XP
The Guard seems much more friendly toward you now! (+15 disposition)

> talk Guard
The Guard says: "Ah, my trusted friend! Your bravery is legendary! What can I do for you?"
```

## Testing Coverage

Social system has **84 tests** across 4 test suites:

### ComponentSystemTest.kt (30 tests)
- Component attachment/detachment
- Disposition tracking and tier calculation
- Social event creation and storage
- Emote disposition effects
- Knowledge entry management

### SocialDatabaseTest.kt (17 tests)
- SQLite schema validation
- Knowledge repository CRUD operations
- Social event repository operations
- Social component repository operations
- Query filtering and metadata search

### SocialSystemV2IntegrationTest.kt (18 tests)
- End-to-end emote workflows
- Question/answer cycles with knowledge persistence
- Disposition tier transitions
- Quest completion bonuses
- Disposition-aware dialogue generation
- Multi-interaction scenarios

### NPCGeneratorTest.kt (19 tests)
- Theme-based personality generation
- Trait assignment (hostile/friendly/boss)
- Disposition initialization
- Deterministic generation
- SocialComponent attachment

All tests passing with 100% success rate.

## Future Enhancements

Potential features for future development:
1. **Reputation system** - Faction-wide disposition tracking
2. **Romance system** - Special interactions at very high disposition
3. **Betrayal mechanics** - Disposition penalties for player actions
4. **Shop discounts** - Allied merchants offer better prices
5. **Combat avoidance** - Friendly NPCs won't attack player
6. **Quest unlocks** - High-tier quests require minimum disposition
7. **Memory sharing** - NPCs can tell each other about player actions
8. **Emotional states** - NPCs have temporary moods affecting interactions
