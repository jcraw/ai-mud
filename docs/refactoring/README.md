# Refactoring Documentation

This directory contains detailed plans for major refactoring work on the AI-MUD project.

## Active Plans

### [Large Files Refactoring Plan](LARGE_FILES_REFACTORING_PLAN.md)
**Status:** TODO - Not started

Refactors 3 oversized files into 17 smaller, focused modules:
- **App.kt** (1961 lines) → 8 files (~150-350 lines each)
- **EngineGameClient.kt** (1507 lines) → 6 files (~200-350 lines each)
- **OutputValidator.kt** (1146 lines) → 4 files (~100-600 lines each)

**Estimated effort:** 10-13 hours across multiple sessions

**Goal:** Improve maintainability, follow KISS principle, adhere to 300-500 line guideline

---

## How to Use These Plans

1. **Read the plan** - Each plan contains detailed migration steps
2. **Follow phases** - Plans are broken into phases that can be completed in separate sessions
3. **Test frequently** - Run tests after each phase
4. **Commit often** - Commit after each successful extraction
5. **Update docs** - Update ARCHITECTURE.md and CLAUDE.md when complete

## Guidelines

All refactoring work should:
- ✅ Maintain all existing functionality (no behavior changes)
- ✅ Pass all existing tests (no test modifications needed)
- ✅ Follow Kotlin best practices
- ✅ Keep files under 500 lines (target: 300-350)
- ✅ Improve separation of concerns
- ✅ Maintain KISS principle

## After Completion

When a refactoring plan is complete:
1. Mark status as COMPLETED in the plan document
2. Update ARCHITECTURE.md and CLAUDE.md with new file structure
3. Delete temporary planning documents (e.g., `docs/instructions/refactor_*.txt`)
4. Move completed plan to `docs/archive/` if desired
