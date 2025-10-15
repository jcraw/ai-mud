# Known Bugs

*Last Updated: 2025-10-14*
*Source: Automated test bot results from test-logs/*

This document tracks known bugs discovered through automated testing. Bugs are prioritized by severity and impact on gameplay.

## Medium Priority

### BUG-006: Navigation Command Parser Issue
**Severity:** MEDIUM
**Status:** Open
**Discovered:** 2025-10-13 (brute_force_playthrough test)

**Symptoms:**
- Command `go to throne room` returns "unknown command"
- Natural language navigation fails
- Simple `go north` works as workaround

**Reproduction:**
1. Be in room with throne room to the north
2. Command `go to throne room` (fails)
3. Command `go north` (works)

**Test Evidence:**
- Step 16: "go to throne room" returns unknown command ✗

**Suspected Locations:**
- `perception/` module - Natural language intent parsing for navigation
- Intent recognition may be too strict

**Impact:** Minor UX issue. Simple directional commands work, but reduces natural language immersion.

---

## Test Statistics Summary

*Last Updated: 2025-10-14*

| Test Scenario | Pass Rate | Status |
|---------------|-----------|--------|
| brute_force_playthrough | **100% (17/17)** | ✅ |
| bad_playthrough | **100% (8/8)** | ✅ |
| smart_playthrough | **100% (7/7)** | ✅ |

**Remaining Known Issues:**
- BUG-006: Navigation NLU (minor UX polish)

---

## Testing Protocol

Run full test suite:
```bash
gradle test && ./test_bad_playthrough.sh && ./test_brute_force_playthrough.sh && ./test_smart_playthrough.sh
```

**All Success Criteria Achieved:** ✅
