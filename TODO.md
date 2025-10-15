# TODO List

*Last Updated: 2025-10-14*

## Current Status

**Test Results:**
- ✅ brute_force_playthrough: **100% pass rate (17/17)**
- ✅ bad_playthrough: **100% pass rate (8/8)**
- ✅ smart_playthrough: **100% pass rate (7/7)**

---

## Next Up - Active Bug Fixes

### 🟡 BUG-006: Improve Natural Language Navigation
**Priority:** MEDIUM
**Description:** "go to throne room" doesn't work, only "go north" works.
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
# Full test suite:
gradle test && ./test_bad_playthrough.sh && ./test_brute_force_playthrough.sh && ./test_smart_playthrough.sh
```

**Success Criteria (All Achieved):** ✅
- bad_playthrough: 100% pass rate ✅
- brute_force_playthrough: 100% pass rate ✅
- smart_playthrough: 100% pass rate ✅

---
