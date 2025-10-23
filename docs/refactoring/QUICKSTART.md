# Refactoring Quick Start

**For the next Claude Code session working on refactoring:**

## Current Status

📋 **Task:** Large Files Refactoring
📄 **Plan:** [LARGE_FILES_REFACTORING_PLAN.md](LARGE_FILES_REFACTORING_PLAN.md)
⏸️ **Status:** Not started - Ready to begin Phase 1

## Start Here

### Phase 1: OutputValidator.kt (Recommended first step)

**Why start here:**
- Smallest refactoring (1146 lines → 4 files)
- Fewest dependencies
- Establishes testing and validation pattern

**Steps:**
1. Read the plan: [Phase 1 section](LARGE_FILES_REFACTORING_PLAN.md#phase-1-outputvalidatorkt-refactoring)
2. Create `testbot/src/main/kotlin/com/jcraw/mud/testbot/validation/` directory
3. Follow migration steps 1-8 in the plan
4. Run tests: `gradle :testbot:test`
5. Commit: `feat: refactor OutputValidator into 4 files [Phase 1/3]`
6. Update checklist in plan document

**Expected time:** 2-3 hours

### Alternative: Jump to Phase 2

If you want to tackle the main application first:

**Phase 2: App.kt**
- More impactful (affects main game loop)
- Establishes handler pattern for client
- Read: [Phase 2 section](LARGE_FILES_REFACTORING_PLAN.md#phase-2-appkt-refactoring)

**Expected time:** 4-6 hours

## Commands to Remember

```bash
# Check current file sizes
find . -name "*.kt" -exec wc -l {} \; | sort -rn | head -10

# Run tests for a specific module
gradle :testbot:test
gradle :app:test
gradle :client:test

# Run full test suite
gradle clean test

# Run test bot (comprehensive validation)
gradle :testbot:test --tests "*.AllPlaythroughsTest"

# Manual smoke tests
gradle installDist && app/build/install/app/bin/app  # Console
gradle :client:run  # GUI
```

## Common Issues

### "Can't find file"
- Verify package directories exist
- Check import statements
- Use IDE auto-import (if available)

### "Tests failing after extraction"
- Revert last change: `git revert HEAD`
- Check for typos in class/method names
- Verify all imports updated

### "Imports not resolving"
- Run `gradle clean build` to refresh
- Check package names match directory structure

## When You're Done

After completing a phase:
1. ✅ All tests pass
2. ✅ Commit with descriptive message
3. ✅ Update checklist in LARGE_FILES_REFACTORING_PLAN.md
4. ✅ Update ARCHITECTURE.md (if Phase 2 or 3)
5. ✅ Update CLAUDE.md "What's Next" section

## Need Help?

- Read the full plan: [LARGE_FILES_REFACTORING_PLAN.md](LARGE_FILES_REFACTORING_PLAN.md)
- Check guidelines: [CLAUDE_GUIDELINES.md](../../CLAUDE_GUIDELINES.md)
- Review architecture: [ARCHITECTURE.md](../ARCHITECTURE.md)

Good luck! Remember: **This is refactoring, not feature work. No behavior changes allowed.**
