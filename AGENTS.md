# AGENTS.md

Neutral ops contract for Grok, Codex, Claude, Cursor, and human builders.
Keep this file lean. Link out; do not embed full project status.

## Project Overview

- **AI MUD** — text multi-user dungeon (console + Compose Multiplatform GUI).
- Kotlin multi-module Gradle project; LLM-assisted narration optional (works without API key via fallbacks).
- **Posture (Jason 2026-08-11):** **harness-first** — quality gates, unit/contract tests, AGENTS/board/verify/CI modern ops. **Not** ready for product playtest; **do not block drains on Jason playtest/opinion** unless ticket is an explicit design spike. Spare capacity OK; one problem per ticket; no rush.

## Startup Reads (minimal)

1. **This file** (`AGENTS.md`) — ops contract and DoD.
2. **Active ticket** — acceptance criteria only; plan path if present under `plans/`.
3. **Run path:** `README.md` (setup + commands). Read `KNOWN_ISSUES.md` if touching inventory, treasure rooms, or known product bugs.
4. **NEVER full-read `CLAUDE.md` by default.** It is optional deep status only (Claude-era narrative). Do not treat `CODEX.md` or `CLAUDE_GUIDELINES.md` as a mandatory startup pack.

Deep docs (`docs/ARCHITECTURE.md`, system guides) only when the ticket needs them.

## Stack

| Piece | Notes |
|-------|--------|
| Language / build | Kotlin, Gradle, **Java 17** |
| Modules | `app`, `client`, `core`, `config`, `perception`, `reasoning`, `memory`, `action`, `llm`, `testbot`, `utils` |
| Secrets surface | `local.properties` (gitignored); env `OPENAI_API_KEY` |
| Run (pointer) | `gradle installDist && app/build/install/app/bin/app` — see `README.md` |
| World model | **V3** graph-based (`graphNodes` / spaces / chunks / entities). Do not reintroduce V2 room paths casually. |

## Workflow

1. **Classify** the change: tooling / engine / client / docs / spike.
2. **Substantial work:** write a plan under `plans/YYYY-MM-DD-…` → **Astra or Jason approve** → **fresh impl session** (do not continue the planning session into product edits unless the ticket says so). Session briefs: `issues/_templates/plan-brief.md` + `implement-brief.md` (fill into `tmp/workers/<ID>/`).
3. **One problem per ticket.** Serial **one live builder per tree** (no parallel implementers fighting the same checkout).
4. **Verify before done** (see Verification).
5. Spikes and opinion work stay **`human_gated`** — do not mark done without a real deliverable.

## Definition of Done

- Implements **accepted behavior only** (ticket acceptance + approved plan if any).
- Tests cover changed contracts; mock LLM where possible.
- Ticket `verify:` command exits 0 when wired (see Verification).
- Closeout note: paths changed, verify result, residual risk, and **`tmp/dod-summary.json`** path (or `$MUD_DOD_SUMMARY`) when verify ran.
- **Coverage % / mutation score alone ≠ done** — never mark done on those metrics without the accepted behavior + green verify for the ticket.
- **No** drive-by refactors, dependency upgrades, or scope creep outside the ticket.
- **No unauthorized test edits:** do not change `*/src/test/**` unless the ticket explicitly scopes them; then regen the lock with `MUD_ALLOW_TEST_CHANGES=1 ./tools/test_lock.sh --write` and commit the updated `tools/test-lock/manifest.sha256`. See **`docs/TEST_LOCK.md`**.

## Verification

Default entrypoint: **`./tools/verify_mud.sh`** (also the ticket `verify:` field unless the ticket says otherwise). Prefer this over ad-hoc `./gradlew test` (avoids thrashing on quarantined `:reasoning` debt). **`fast` ≡ `default`** (bare = compile smoke + hard gates; unit tests only with module args or `--core`/`--full`).

| Lane | Command | What it runs |
|------|---------|--------------|
| Default / fast | `./tools/verify_mud.sh` or `--fast` | `:core:compileKotlin` smoke; with module args → compile (+ test if `src/test` exists); then **detekt** + **Konsist arch** + **test-lock** + **token soft** (never PIT) |
| Core | `./tools/verify_mud.sh --core` | `:core:test :perception:test :memory:test :reasoning:test` (default `excludeTags("quarantine")`) + detekt + Konsist arch + test-lock + token soft (never PIT) |
| Full | `./tools/verify_mud.sh --full` | Stable green set including green `:reasoning` (exclude quarantine); no testbot thrash; + detekt + Konsist arch + test-lock + token soft; PIT skipped (core &gt;45s — use `--pitest`) |
| PIT | `./tools/verify_mud.sh --pitest` | Pure-module PIT (`:core` / `:perception` / `:memory`) + detekt + Konsist arch + test-lock; soft 60% (see **`docs/PIT.md`**); token skipped |
| Quarantine | `./tools/verify_mud.sh --quarantine` | `:reasoning:test -Pmud.quarantineOnly=true` — debt only; hard-fail OK (no detekt / no Konsist / no test-lock / no PIT / no token) |

Every lane writes compact **`tmp/dod-summary.json`** (override: `$MUD_DOD_SUMMARY`) with **schema_version 2**, per-gate `pass|fail|skipped`, durations, `quarantine_count`, `exit_code`, and optional **`findings[]`** (may be empty; see `docs/DOD_SUMMARY.md`). Cite this path in closeout. Human `== verify_mud ==` summary still prints (`dod_summary:` line).

**N=3 then escalate:** agents may re-run a failed verify up to **3** times for transient/env flakiness, then escalate to a human. The verify script does **not** auto-retry.

Quarantine list + baseline counts: **`docs/TEST_QUARANTINE.md`** (quarantine **0** after MUD-022; was 8 after MUD-021, 12 after MUD-020, 20 after MUD-017, 23 as of 2026-08-10).

**Detekt** is live on default/fast/core/full/pitest (`./gradlew detekt`). Legacy soft via baseline; mass baseline regen = **Jason/explicit only**. See **`docs/DETEKT.md`**. **Konsist** architecture tests are live on default/fast/core/full/pitest (`:core:test --tests 'com.jcraw.mud.architecture.*'`); exceptions → **`docs/KONSIST.md`**. **Test-lock** is live on default/fast/core/full/pitest (`./tools/test_lock.sh --check`); unauthorized `src/test` edits fail closed — see **`docs/TEST_LOCK.md`**. **PIT** mutation on pure modules (`:core` / `:perception` / `:memory`) via `./tools/verify_mud.sh --pitest` only (default/fast/core never; full skips — core measured &gt;45s). Soft 60%; hard opt-in `MUD_PITEST_HARD=1`. See **`docs/PIT.md`**. **Token/structure** pilot is soft on default/fast/core/full (findings → dod-summary; `gates.token_budget`); hard pilot via `MUD_TOKEN_HARD=1` or `--token-hard` on scoped git-diff `*_E` only; quarantine/pitest skip — see **`docs/TOKEN_BUDGET_KT.md`**.

Help / dry-run: `./tools/verify_mud.sh --help`, `./tools/verify_mud.sh --dry-run` (dry-run still emits parseable dod-summary with `dry_run: true`).

**Agent-native gate program (Wave Q):** accepted design **`docs/AGENT_QUALITY_GATES_DESIGN.md`** (token-primary ceilings, hard-on-touched, anti-gaming, compact findings). Implement only via board tickets **MUD-026…038** — do not invent thresholds ad hoc.

## Protected / Secrets

**Never commit:**

- `local.properties`
- API keys / secret tokens (any provider)
- `*.db` game/state databases
- Secret-bearing logs or `test-logs` that may contain keys or PII

Also:

- No force-push or history rewrite unless **Jason** explicitly requests it.
- Changes to **this file** (`AGENTS.md`) or to `plans/` process rules need **explicit approval**.
- Broader git dirty-tree hygiene policy → **MUD-009** (do not invent policy here).
- `.claude/settings.local.json` = local Claude tool permissions only; **not** DoD; do not grow as project contract.

## Working Principles

- **KISS** — avoid overengineering.
- Prefer **immutable state** transitions and **sealed classes** over loose enums/flags.
- Keep source files under ~**1000 lines** for readability.
- **Console + GUI parity** when changing handlers or player-facing behavior (app handlers and client handlers stay aligned).
- **V3 world model is current** — graph navigation and ECS-style storage; do not casually revive V2 `Room` paths.

## Agent parity

- **Grok, Codex, Claude, Cursor** — same Definition of Done and verify expectations.
- Prefer **neutral wording** in new docs and tickets (not Claude-only or tool-specific).
- For day-to-day ops, **this file wins** over older Claude/Codex-specific notes when they conflict.
- **Serial-one-builder:** one live implementer per working tree; queue others (see board drain order in `issues/BOARD.md`).

## Out of scope for this file

Do not grow AGENTS into full architecture status, ORCHESTRATION novels, or verify-script source. Those belong in:

- Deep status → optional `CLAUDE.md` / `docs/*`
- Board + short orchestration → MUD-005
- Verify script → MUD-004
- CLAUDE/CODEX deprecation path → MUD-018
