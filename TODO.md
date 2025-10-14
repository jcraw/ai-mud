# TODO List

*Last Updated: 2025-10-13*

## Next Up - Critical Bug Fixes

### 🔴 BUG-001: Fix Combat State Desync
**Priority:** CRITICAL
**Description:** Skeleton King disappears mid-combat after 5 attacks. Combat becomes unwinnable.
**Test:** brute_force_playthrough (36% pass rate)
**Files:**
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/QuestTracker.kt`
- `core/src/main/kotlin/com/jcraw/mud/core/WorldState.kt`
- Combat intent handling in perception/reasoning

**Details:** See [BUGS.md#BUG-001](docs/BUGS.md#bug-001-combat-state-desync---skeleton-king-disappears-during-combat)

---

### 🔴 BUG-002: Fix Social Checks on Boss NPCs
**Priority:** CRITICAL
**Description:** All intimidate/persuade attempts fail on Skeleton King. Social victory path impossible.
**Test:** smart_playthrough (14% pass rate)
**Files:**
- `perception/` module - Social intent parsing
- `reasoning/` module - Social check mechanics and CHA calculations
- Boss NPC configuration

**Details:** See [BUGS.md#BUG-002](docs/BUGS.md#bug-002-social-checks-non-functional-on-boss-npcs)

---

### 🟠 BUG-003: Fix Death/Respawn Combat Initiation
**Priority:** HIGH
**Description:** After death and respawn, cannot attack NPCs anymore. Game becomes unplayable.
**Test:** bad_playthrough (87% pass rate, breaks at step 8)
**Files:**
- `app/src/main/kotlin/com/jcraw/app/App.kt:302-314, 671-683`
- World state reset logic

**Details:** See [BUGS.md#BUG-003](docs/BUGS.md#bug-003-deathrespawn-bug---cannot-attack-after-player-death)

---

### 🟠 BUG-004: Enable Potion Use in Combat
**Priority:** HIGH
**Description:** Cannot use health potions during/after combat. Healing broken.
**Test:** brute_force_playthrough (step 17)
**Files:**
- `perception/` module - Consumable intent parsing
- `reasoning/` module - Combat state blocking consumables

**Details:** See [BUGS.md#BUG-004](docs/BUGS.md#bug-004-cannot-use-potions-duringafter-combat)

---

### 🟡 BUG-005: Implement Feature Skill Checks
**Priority:** MEDIUM
**Description:** Checking features doesn't trigger STR/INT checks. Skill exploration broken.
**Test:** smart_playthrough (steps 7-8)
**Files:**
- `perception/` module - Feature interaction parsing
- `reasoning/` module - Skill check triggers

**Details:** See [BUGS.md#BUG-005](docs/BUGS.md#bug-005-skill-checks-on-features-not-triggering)

---

### 🟡 BUG-006: Improve Natural Language Navigation
**Priority:** MEDIUM
**Description:** "go to throne room" doesn't work, only "go north" works.
**Test:** brute_force_playthrough (step 16)
**Files:**
- `perception/` module - NLU intent parsing

**Details:** See [BUGS.md#BUG-006](docs/BUGS.md#bug-006-navigation-command-parser-issue)

---

## Feature Backlog

### Deliver Quest Objectives
**Status:** Not implemented
**Description:** Implement DeliverItem quest objective tracking
**Files:**
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/QuestTracker.kt`
- `core/src/main/kotlin/com/jcraw/mud/core/Quest.kt`

---

### Network Layer (Optional)
**Status:** Future enhancement
**Description:** TCP/WebSocket support for remote multi-player
**Files:**
- New `network/` module
- `app/src/main/kotlin/com/jcraw/app/GameServer.kt`

---

### Persistent Vector Storage (Optional)
**Status:** Future enhancement
**Description:** Save/load vector embeddings to disk
**Files:**
- `memory/` module

---

## Testing Protocol

After each bug fix, run relevant test scenarios:

```bash
# For BUG-001, BUG-004:
./test_brute_force_playthrough.sh

# For BUG-002, BUG-005:
./test_smart_playthrough.sh

# For BUG-003:
./test_bad_playthrough.sh

# Full test suite:
gradle test && ./test_bad_playthrough.sh && ./test_brute_force_playthrough.sh && ./test_smart_playthrough.sh
```

**Target Success Criteria:**
- bad_playthrough: 100% pass rate
- brute_force_playthrough: 90%+ pass rate
- smart_playthrough: 90%+ pass rate

---

## Completed

*(Nothing yet - this is the first bug tracking session)*
