# Test-file lock (MUD-012)

Content-hash gate against agents “passing” by weakening or rewriting tests.

## Paths

| Path | Role |
|------|------|
| `tools/test_lock.sh` | Check / write CLI |
| `tools/test-lock/manifest.sha256` | Committed SHA-256 baseline of tracked `*/src/test/**` |

## Verify behavior

- **Default / fast / core / full:** hard `./tools/test_lock.sh --check` after detekt + Konsist.
- **Quarantine lane:** test-lock is **not** run (debt-only lane).
- Fail-closed if baseline missing, content drifts, a tracked test path is new/removed, or an **untracked** path under `src/test/` appears in `git status --porcelain`.

```bash
./tools/verify_mud.sh            # includes test-lock
./tools/verify_mud.sh --fast     # includes test-lock
./tools/verify_mud.sh --dry-run  # prints: ./tools/test_lock.sh --check
./tools/test_lock.sh --check     # direct
```

Product (non-test) dirty files do **not** trip the lock — only `src/test` content vs baseline.

## Escape hatch (authorized test edits only)

Tickets must **explicitly** scope test file changes. Then:

```bash
# 1) Edit tests as authorized
# 2) Regen baseline (never silent)
MUD_ALLOW_TEST_CHANGES=1 ./tools/test_lock.sh --write
# synonym: ALLOW_TEST_CHANGES=1
# 3) Commit tests + tools/test-lock/manifest.sha256 together
```

Without the env gate, `--write` **refuses**. Gradle property `-PallowTestChanges` is a documented synonym for agents: set the env before verify/write; verify itself always runs a hard check (property does not soft-skip the lock).

## Do not

- Regen baseline to hide weakened assertions
- Leave new tests untracked to bypass the manifest
- Set `MUD_ALLOW_TEST_CHANGES=1` on every run without ticket scope

## Non-goals (other tickets)

- Detekt → MUD-010 · Konsist → MUD-011 · PIT → MUD-014 · CI → MUD-016  
- Mass test rewrites / quarantine clear → MUD-017
