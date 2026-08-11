# MUD-003 Plan — Lean AGENTS.md

**Ticket:** MUD-003 · **Worker:** grok · **Phase:** planning → plan_review  
**Impl = fresh session** after Astra approve. This plan session is not resumed for the AGENTS write.  
**Plan path:** `plans/2026-08-09-ai-mud-MUD-003-lean-agents-md.md`  
**Worker mirror:** `tmp/workers/MUD-003/PLAN.md`

---

## 1. Goal / acceptance mapping

| Acceptance | Impl delivers |
|------------|---------------|
| Root `AGENTS.md` with required topics | New file ~100–200 lines |
| Explicit: no full-read CLAUDE.md by default | Startup Reads + optional deep-status note |
| Secrets: never commit `local.properties`, keys, `*.db`, secret-bearing logs | Protected / Secrets section |
| Spike Appendix A = starting shape (adapt) | Headings below; not blind paste |
| No product code changes | Diff limited to docs/bookkeeping listed in §4 |

**Verify field:** leave empty / placeholder until MUD-004 (`tools/verify_mud.sh`).

---

## 2. Inventory (plan-turn)

| Item | Status |
|------|--------|
| `AGENTS.md` | **Missing** (root) |
| `CLAUDE.md` | ~362 lines — Claude-era deep status; **not** mandatory agent pack |
| `CODEX.md` | ~30 lines — Codex-specific coexists; do not rewrite this ticket |
| `CLAUDE_GUIDELINES.md` | ~56 lines — optional principles pointer only if useful |
| `README.md` | Run path: `local.properties` / `OPENAI_API_KEY` → `gradle installDist && app/build/install/app/bin/app` |
| `KNOWN_ISSUES.md` | Product bugs (e.g. treasure GUI) — cite as conditional read |
| `issues/BOARD.md` | Wave A serial 003→004→005→006; MUD-003 still Open backlog |
| Spike Appendix A | Draft outline exists; **do not apply** until impl turn |
| dustcrawl-website AGENTS | Not on this machine; spike §4: lean overview/startup/workflow/secrets/single verify — adapt weight only |
| `tools/` | **Missing** — verify script = MUD-004 |
| Secrets gitignore | `local.properties`, `*.db` already ignored |
| Modules | app, utils, llm, core, config, perception, reasoning, memory, action, testbot, client |
| Stack | Kotlin / Gradle / Java 17; LLM optional (fallback without key) |

---

## 3. Recommended `AGENTS.md` outline (final headings)

Target **~100–200 lines**. Neutral wording (Grok/Codex/Claude/Cursor). Dustcrawl weight + plan→approve→impl discipline.

```
# AGENTS.md

## Project Overview
  - AI-powered Kotlin MUD (console + Compose client)
  - Multi-module Gradle; LLM optional
  - Posture: background/low-stakes ops + product; spare-capacity drains

## Startup Reads (minimal)
  1. This file
  2. Active ticket acceptance (+ plan path if present)
  3. README.md run path; KNOWN_ISSUES.md if touching inventory/treasure
  4. NEVER full-read CLAUDE.md by default — optional deep status only
  - Do not full-read CODEX.md / CLAUDE_GUIDELINES as mandatory pack

## Stack
  - Kotlin / Gradle / Java 17
  - Modules list (11)
  - Secrets surface: local.properties (gitignored), OPENAI_API_KEY env
  - Run: gradle installDist → app bin (pointer only)

## Workflow
  1. Classify: tooling / engine / client / docs / spike
  2. Substantial: plan under plans/YYYY-MM-DD-… → Astra/Jason approve → fresh impl session
  3. One problem per ticket; serial one live builder per tree
  4. Verify before done (see Verification)
  5. Spikes/opinion → human_gated; do not fake done

## Definition of Done
  - Implements accepted behavior only
  - Tests for changed contract (mock LLM where possible)
  - Ticket verify command exits 0 (when wired)
  - Closeout: paths changed, verify result, residual risk
  - No drive-by refactors / dep upgrades / scope creep

## Verification
  - Placeholder until MUD-004:
    - Default interim: `./gradlew :core:compileKotlin` (or module compile for touched code)
    - Scoped: `./gradlew :<module>:test` when tests touched
    - Full suite optional; reasoning has known debt — document, don’t thrash
  - Future default: `./tools/verify_mud.sh` (MUD-004 fills this)

## Protected / Secrets
  - Never commit: local.properties, API keys, *.db, secret-bearing logs / test-logs
  - No force-push / history rewrite unless Jason explicit
  - plans/ and AGENTS.md rule changes need explicit approval
  - (Git dirty hygiene details → MUD-009; don’t expand here)

## Working Principles
  - KISS; immutable state; sealed classes; files under ~1000 lines
  - Console + client parity when changing handlers
  - V3 world model is current; don’t reintroduce V2 paths casually

## Agent parity
  - Grok, Codex, Claude, Cursor — same DoD
  - Prefer neutral wording in new docs (not Claude-only)
  - Serial-one-builder: one live implementer per tree
```

**Adapt rules (vs Appendix A paste):**
- Add serial-one-builder + posture (BOARD/Jason 2026-08-09)
- Keep Verification explicitly placeholder / interim Gradle until MUD-004
- Point CLAUDE.md as optional deep status only (mandatory constraint)
- Skip game_jam-length SDD / ORCHESTRATION novel (MUD-005 later)

---

## 4. Files to create/touch (impl turn)

| Path | Action |
|------|--------|
| `AGENTS.md` | **Create** (only product of this ticket) |
| `issues/MUD-003-lean-agents-md.md` | Closeout: status done/acceptance checks; verify still empty or interim note |
| `issues/BOARD.md` | Move MUD-003 Open → Recently done (surgical) |

**May touch bookkeeping only if needed:** `tmp/workers/MUD-003/*` closeout notes.

**Must not touch:** Kotlin/src, Gradle build files, `tools/` (MUD-004), `CLAUDE.md`/`CODEX.md` rewrites (MUD-018), git hygiene (MUD-009), product handlers/client.

---

## 5. Non-goals

- No `tools/verify_mud.sh` (MUD-004)
- No product / gameplay / handler / GUI fixes
- No CLAUDE.md / CODEX.md rewrite or deprecation (MUD-018)
- No git hygiene / force-push policy expansion (MUD-009)
- No full ORCHESTRATION.md (MUD-005)
- No brief templates (MUD-006)
- No test baseline / quarantine (MUD-008)
- No quality gates Detekt/Konsist/etc. (Wave C+)
- No paste of full spike or CLAUDE content into AGENTS

---

## 6. How impl confirms acceptance

1. `test -f AGENTS.md` and `wc -l` in ~100–200 range (soft band; prefer lean)
2. Section checklist present: Overview, Startup Reads, Stack, Workflow, DoD, Verification, Protected/Secrets, Working Principles, Agent parity (+ serial-one-builder)
3. Grep/assert phrases:
   - CLAUDE.md not full-read by default / optional deep status
   - secrets: `local.properties`, keys/`API`, `*.db`, logs
   - plan → approve → impl (or equivalent)
   - verify placeholder / interim Gradle + MUD-004 pointer
4. `git status` / diff: **no** `*.kt`, `*.kts` product edits; only AGENTS + ticket/BOARD (+ worker notes)
5. Ticket acceptance boxes checked; status `done` after Astra/closeout convention
6. BOARD: MUD-003 removed from Open Wave A; listed Recently done

**No compile/test gate required** for pure docs ticket; optional `:core:compileKotlin` only if impl wants smoke (not acceptance).

---

## 7. Out of scope / risks

| Risk | Mitigation |
|------|------------|
| AGENTS bloat toward CLAUDE length | Cap ~200 lines; link out, don’t embed status |
| Copying Appendix A verbatim with stale verify default | Explicit MUD-004 placeholder |
| Agents still full-read CLAUDE via habit | Bold NEVER in Startup Reads |
| Scope creep into ORCHESTRATION / verify script | Defer MUD-004/005 |
| BOARD desync | Surgical single-line moves only |
| Conflicting CODEX.md guidance | Leave both; parity section says neutral AGENTS wins for new ops |

---

## Impl session contract (Turn 2)

1. Read this plan + ticket acceptance only (not full CLAUDE).
2. Write `AGENTS.md` per §3.
3. Check acceptance §6; update ticket + BOARD.
4. Stop. No product code. No verify script.

**Handoff:** Plan ready for **Astra review**. Impl = **fresh** session after approve.


---

Status: APPROVED by Astra 2026-08-10 17:15 MST
