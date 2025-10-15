# Known Bugs

*Last Updated: 2025-10-14*
*Source: Automated test bot results from test-logs/*

This document tracks known bugs discovered through automated testing. Bugs are prioritized by severity and impact on gameplay.

## Resolved Bugs

### BUG-006: Navigation Command Parser Issue ✅ RESOLVED
**Severity:** MEDIUM
**Status:** RESOLVED (2025-10-14)
**Discovered:** 2025-10-13 (brute_force_playthrough test)

**Symptoms:**
- Command `go to throne room` returns "unknown command"
- Natural language navigation fails
- Simple `go north` works as workaround

**Resolution:**
Enhanced IntentRecognizer to support room-name navigation:
1. Added `exitsWithNames` parameter to `parseIntent()` - maps exits to destination room names
2. Updated LLM system prompt with rule 11: "ROOM-NAME NAVIGATION" to match player input like "go to throne room" with available exits
3. Updated all parseIntent callers (MudGame, MultiUserGame, EngineGameClient, InMemoryGameEngine) to build and pass exit information

**Files Modified:**
- `perception/src/main/kotlin/com/jcraw/mud/perception/IntentRecognizer.kt`
- `app/src/main/kotlin/com/jcraw/app/App.kt`
- `client/src/main/kotlin/com/jcraw/mud/client/EngineGameClient.kt`
- `testbot/src/main/kotlin/com/jcraw/mud/testbot/InMemoryGameEngine.kt`

**Impact:** Improved natural language navigation. Players can now use commands like "go to throne room" in addition to directional commands.

---

## Test Statistics Summary

*Last Updated: 2025-10-14*

| Test Scenario | Pass Rate | Status |
|---------------|-----------|--------|
| brute_force_playthrough | **100% (17/17)** | ✅ |
| bad_playthrough | **100% (8/8)** | ✅ |
| smart_playthrough | **100% (7/7)** | ✅ |

**Remaining Known Issues:**
- None! All discovered bugs have been resolved. 🎉

---

## Testing Protocol

Run full test suite:
```bash
gradle test && ./test_bad_playthrough.sh && ./test_brute_force_playthrough.sh && ./test_smart_playthrough.sh
```

**All Success Criteria Achieved:** ✅
