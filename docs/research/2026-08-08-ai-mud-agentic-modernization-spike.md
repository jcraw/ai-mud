# AI MUD — Agentic modernization spike (2026-08-08)

**Ticket:** MUD-001 · **Type:** investigation only (no product code)  
**Plan:** `plans/2026-08-08-mud-001-agentic-modernization-spike.md` (APPROVED by Astra 2026-08-08 20:17 MST)  
**Closeout:** human-gated — awaiting Jason opinion (**not** `done`)

---

## 1. Executive verdict

**Ready for a tooling-first modernization wave.** The game is a large, feature-complete Kotlin/Gradle multi-module MUD with real systems (combat, items, skills, social, quests, worldgen V3, LLM/RAG, multi-user, Compose client, testbot). What is missing is the **operating system** Jason’s other repos already use: neutral `AGENTS.md`, plan→approve→impl handoffs as first-class, a single verify entrypoint, ticket hygiene fields, and protected-path rules.

| Signal | Result |
|--------|--------|
| Product maturity | High — playable V2/V3 systems; docs claim production-ready |
| Agent ops maturity | Low — Claude-era surface only; board just started (MUD-001 only) |
| Build health (this spike) | `:core:compileKotlin` **BUILD SUCCESSFUL** (~70s cold Gradle download) |
| Full test suite | **Not re-run**; on-disk results ~2025-12 are stale but informative |
| Blockers to start tooling | None technical — **Jason product/ops choices** only (see §10) |

**Recommendation:** File a short tooling backlog (AGENTS + verify + board hygiene), then product bugs (treasure inventory GUI), then optional feature work. Do **not** wait for a green full suite or dependency upgrades before AGENTS/board work.

---

## 2. Current state snapshot

### Stack

| Layer | Reality |
|-------|---------|
| Language / build | Kotlin, Gradle multi-project, Java **17** toolchain (`gradle.properties` pins `org.gradle.java.home`) |
| Modules (11) | `app`, `utils`, `llm`, `core`, `config`, `perception`, `reasoning`, `memory`, `action`, `testbot`, `client` (`settings.gradle.kts`) |
| LLM | OpenAI via `llm` (`OpenAIClient`); `OPENAI_API_KEY` or `local.properties` `openai.api.key`; fallback mode without key |
| UI | Console (`app`) + Compose Multiplatform desktop (`client`) |
| Persistence | JSON save/load; SQLite world data under `data/` (gitignored `*.db`) |
| Remote | `https://github.com/jcraw/ai-mud.git` · branch `master` |

### Run path (from README)

```bash
gradle installDist && app/build/install/app/bin/app
# or ./gradlew installDist
```

Optional API key in `local.properties` (gitignored) or env.

### Last activity / git drift (probed 2026-08-08)

| Probe | Result |
|-------|--------|
| Branch | `master` **ahead 1** of `origin/master` |
| Recent commits | Dec 2025 era: `bot recognize low health`, dodge/heal test tweaks, “claude opus chugging along” |
| Dirty tracked | `testbot/.../GameplayReportGenerator.kt`, `testbot/.../TestBotRunner.kt` |
| Untracked | `issues/`, `plans/`, `tmp/`, `testbot/test-logs/skill_progression/*` |
| Secrets | `local.properties` present locally; listed in `.gitignore` — **do not commit** |

### On-disk test signal (stale — not re-run this spike)

Aggregated from existing `*/build/test-results/**/TEST-*.xml` (mtimes ~2025-12-13):

| Module | Tests | Failures | Errors |
|--------|------:|---------:|-------:|
| core | 462 | 0 | 0 |
| memory | 321 | 0 | 0 |
| perception | 56 | 0 | 0 |
| client | 6 | 0 | 0 |
| testbot | 1 | 0 | 0 |
| reasoning | 644 | **22** | 0 |
| **Total on disk** | **1490** | **22** | **0** |

Docs/CLAUDE claims (not re-verified): ~621 core-ish green; reasoning ~23 pre-existing fails; README “773 tests passing” is **stale/inconsistent** with on-disk totals and should not be trusted for gates.

### Known product bug

`KNOWN_ISSUES.md`: **Treasure Room inventory** — GUI still broken (item take succeeds, inventory empty). Console claimed fixed but “not tested.” Good first product ticket after tooling.

### Docs layout

Heavy design/status under `docs/` (ARCHITECTURE, GETTING_STARTED, V3_STATUS, TREASURE_ROOMS, requirements/, archive/). `docs/research/` existed empty — this report is the first research artifact.

---

## 3. Legacy agent surface

| Artifact | Role today | Gap |
|----------|------------|-----|
| `CLAUDE.md` (~25k) | Canonical system status + module map for Claude Code | Too large for mandatory-read packs; Claude-branded; drifts vs reality |
| `CLAUDE_GUIDELINES.md` | Personality + engineering principles (Linus-style) | Agent-specific tone; useful principles, wrong home for multi-agent ops |
| `CODEX.md` | Codex coexistence rules; defers overview to CLAUDE.md | Explicit “don’t edit CLAUDE*”; no Grok/Astra loop |
| `.claude/settings.local.json` | Claude allow-list of bash/git/gradle patterns | Claude-only; not portable; not a DoD |
| `AGENTS.md` | **Missing** | Primary modernization gap |
| `issues/` board | Started: BOARD, README, ticket template, MUD-001 | Immature vs game_jam ORCHESTRATION |
| Verify tooling | Ad-hoc `./gradlew test`, `test_spatial_coherence.sh`; no single selector | No ticket `verify:` contract |
| Plans | `plans/` untracked (MUD-001 plan only so far) | Pattern exists; not codified in AGENTS |

**Net:** Dual Claude/Codex docs with Claude as SoT. Modern stack (Grok builders + Astra review + human gates) is **operationally present via portfolio/tmp workers** but **not codified in-repo**.

---

## 4. Target agentic model (adapt, don’t copy)

Patterns from peer repos (skim only):

### game_jam (heavy, full SDD)

- `AGENTS.md`: startup reads, SDD workflow, material DoD, protected artifacts, agent parity, verify selector (`tools/codex_verify.py`)
- `issues/ORCHESTRATION.md`: serial-per-app, multi-turn plan→Astra approve→fresh impl session, fire-and-forget, human_gated tickets, token budgets (≤~6k mandatory pack), worker pid hygiene
- Briefs under `issues/_templates/` + `tmp/workers/<ID>/`

### dustcrawl-website (lighter)

- Lean `AGENTS.md`: overview, startup reads, workflow, protected secrets, single verify (`python3 tools/verify_site.py`), deploy not part of done
- Simple BOARD with Blocked (awaiting Jason)

### AI MUD fit

| Borrow | Skip / delay |
|--------|----------------|
| Neutral `AGENTS.md` + agent parity | Full ORCHESTRATION novel + dashboard tooling on day 1 |
| Plan→approve→impl for substantial work | Godot/runtime-specific gates |
| Single verify entrypoint (Gradle-scoped) | Shipping a 9k-token quality stack script as mandatory read |
| Protected paths + secrets rules | Archiving/deleting CLAUDE.md immediately |
| Board + ticket frontmatter (`verify`, `eligibility`, sessions) | Parallel multi-builder in one tree (keep serial) |
| Human_gated ≠ fake done | Portfolio skill rewrites as part of this repo |

**Minimal viable agentic ops for AI MUD** = dustcrawl weight + game_jam multi-turn discipline (already proven on MUD-001).

---

## 5. Gap analysis

| Capability | Status | Notes |
|------------|--------|-------|
| In-repo tickets + BOARD | **Partial** | MUD-001 only; statuses exist; no archive, no ORCHESTRATION |
| Ticket template | **Partial** | Basic frontmatter; missing `agent_eligible`, `eligibility`, `needs_jason`, `phase`, `report`, `plan`, `impl_session` conventions used live |
| Neutral AGENTS.md | **Missing** | CLAUDE/CODEX only |
| Plan directory convention | **Partial** | Working for MUD-001; not documented as DoD |
| Plan/impl briefs + worker dir | **Partial** | Portfolio/tmp pattern works; not in-repo templates |
| Verify command(s) | **Missing** | No `tools/verify_mud.sh` / documented default lanes |
| CI | **Missing / unknown** | Not probed beyond local Gradle |
| Secrets hygiene | **Have** | `local.properties` gitignored; document in AGENTS |
| Module map for agents | **Partial** | CLAUDE.md huge; need thin AGENTS pointer + README |
| Compile green (core) | **Have** (this spike) | `:core:compileKotlin` OK |
| Full test green | **Partial** | reasoning ~22 fails on disk; suite not re-run |
| Product bug backlog filed | **Missing** | KNOWN_ISSUES only |
| LLM cost / flakiness policy | **Partial** | CODEX/CLAUDE mention mocks + mini models; not agent DoD |
| Multi-agent parity (Grok/Codex/Claude) | **Missing** | Codex defers to Claude branding |
| Protected artifact rules | **Missing** | No formal list |
| Token-budget brief discipline | **Partial** | Used on MUD-001 via Astra; not repo-owned |

---

## 6. Recommended end-state (minimal viable agent ops)

Ship in roughly this order (tooling first):

1. **`AGENTS.md` (lean, ~100–200 lines)**  
   - Overview, stack, run/build one-liners  
   - Startup reads: AGENTS + ticket + (optional) README/KNOWN_ISSUES — **never** full CLAUDE.md by default  
   - Point at CLAUDE.md as *optional deep status* only  
   - SDD: plan under `plans/`, Astra/human approve, fresh impl session  
   - DoD: code + tests for touched behavior + verify lane green + closeout  
   - Secrets, protected paths (`plans/` edits policy, no secret commits, no force-push)  
   - Serial one-builder-per-repo (or per tree)

2. **`tools/verify_mud.sh` (or Gradle alias)** — thin wrapper, not a novel:  
   - Default: `./gradlew :core:compileKotlin` or scoped `test` for touched modules  
   - Optional: `--full` for broader suite (document reasoning fail debt)  
   - Ticket `verify:` field points at a concrete command

3. **Board hygiene**  
   - Template fields aligned with live frontmatter  
   - Statuses already OK; add human_gated / Blocked discipline (this spike is the template)  
   - Later: short `issues/ORCHESTRATION.md` (1–2 screens, not game_jam length)

4. **Brief templates** under `issues/_templates/` (plan + implement) with ≤~6k read packs

5. **Legacy surface policy**  
   - Keep CLAUDE.md / CODEX.md until AGENTS is proven; then shrink CLAUDE.md or mark historical  
   - Stop growing Claude-only permission JSON as SoT

6. **Product follow-through** (after tooling tickets exist)  
   - Treasure inventory GUI  
   - Reasoning test triage  
   - Dirty testbot + skill_progression logs: commit intent or discard — **Jason call**

**Explicit non-goals for first wave:** network multiplayer, dep upgrades, V1/V2 field purge, full CI green-up, applying AGENTS as “modernization done” without verify + board follow-through.

---

## 7. Phased follow-up tickets

IDs suggested; Astra/Jason may renumber.

### Phase A — Tooling (do first)

| ID | Title | Pri | Type | Deps |
|----|-------|-----|------|------|
| **MUD-002** | Add lean `AGENTS.md` (neutral agent ops + DoD + secrets) | high | tooling | Jason OK on end-state |
| **MUD-003** | Add `tools/verify_mud.sh` + document default/full lanes; ticket `verify:` convention | high | tooling | MUD-002 helpful |
| **MUD-004** | Board/template hygiene (`agent_eligible`, `eligibility`, `needs_jason`, plan/impl fields) + short ORCHESTRATION note | med | tooling | MUD-002 |
| **MUD-005** | Plan/implement brief templates under `issues/_templates/` (token-budget aware) | med | tooling | MUD-004 |
| **MUD-006** | CLAUDE/CODEX deprecation path: “deep status only” pointers; freeze growth of Claude-only SoT | low | docs | MUD-002 |

### Phase B — Health & product truth

| ID | Title | Pri | Type | Deps |
|----|-------|-----|------|------|
| **MUD-007** | Fix treasure-room inventory in GUI (KNOWN_ISSUES) | high | bug | tooling optional |
| **MUD-008** | Re-baseline test suite; triage reasoning ~22 failures; update README pass claims | med | testing | MUD-003 |
| **MUD-009** | Git hygiene: decide fate of dirty testbot + untracked skill_progression logs; first clean PR of issues/AGENTS | med | chore | Jason |

### Phase C — Optional later

| ID | Title | Pri | Type | Deps |
|----|-------|-----|------|------|
| **MUD-010** | CI workflow (compile + scoped tests on PR) | med | tooling | MUD-003, MUD-008 |
| **MUD-011** | V1/V2 PlayerState debt removal (file-by-file) | low | refactor | product need |
| **MUD-012** | Portfolio/orchestrate-repos skill extension for ai-mud drain | low | portfolio | Jason |

---

## 8. Risks / landmines

| Risk | Why it bites agents |
|------|---------------------|
| **Huge CLAUDE.md as mandatory read** | Blows token budgets; contradicts modern brief discipline |
| **Multi-module Gradle cold starts** | First compile can download Gradle (~1m+); agents timeout or assume broken |
| **Reasoning test debt** | Blind `./gradlew test` “fails” and agents thrash green-up |
| **LLM-backed tests / testbot cost** | Flaky + API spend; needs mock/fallback policy in DoD |
| **API key optional** | Agents must not invent failures when fallback is intended |
| **Dirty tree + untracked logs** | Accidental secret/log commits; unclear PR base |
| **Dual handlers (console + client)** | Feature parity bugs (treasure inventory) — always check both |
| **Java 17 pin vs host Java 26** | Toolchain OK if `java.home` set; broken machines fail obscurely |
| **Compose client** | Longer builds; keep GUI work out of default verify lane |
| **Ticket body said `done` for spikes** | Prefer human_gated blocked for opinion spikes (this closeout) |

---

## 9. What was not verified

Explicit **not verified** / shallow:

- [ ] Full `./gradlew test` / `build` (not run)
- [ ] App runtime / interactive playthrough
- [ ] GUI client launch
- [ ] Testbot scenarios / LLM live calls
- [ ] Multi-user server mode
- [ ] Whether ahead-1 commit is intentional / push-ready
- [ ] Semantic correctness of dirty testbot diffs
- [ ] CI presence on GitHub
- [ ] Fresh reasoning failure reproduction (used on-disk 2025-12 XML only)

**Verified this spike:**

- [x] Git status/log drift
- [x] Module list from `settings.gradle.kts`
- [x] Legacy agent file inventory + CODEX/.claude skim
- [x] Issues board + template skim
- [x] KNOWN_ISSUES + README skim
- [x] `local.properties` gitignored
- [x] Comparative AGENTS/ORCHESTRATION headings (game_jam, dustcrawl)
- [x] `./gradlew :core:compileKotlin` → **BUILD SUCCESSFUL** (~70s including Gradle 8.14 download)

---

## 10. Open questions for Jason

1. **Closeout policy:** Keep spike as `blocked`/`human_gated` until you approve next tickets, or mark MUD-001 `done` after reading this report?
2. **AGENTS weight:** dustcrawl-lean (~100 lines) or game_jam-heavier SDD (DoD + protected + failure loop)?
3. **Verify default:** compile-only (`:core:compileKotlin`) vs scoped module tests vs “never claim green while reasoning fails”?
4. **First PR contents:** May agents commit `issues/` + this report + future AGENTS in one hygiene PR? Fate of dirty testbot + skill_progression logs?
5. **CLAUDE.md:** Archive, slim, or leave as optional deep status indefinitely?
6. **Builder default:** Grok-only for ai-mud (like post-jam game_jam) or keep Codex dual-path?
7. **Product priority after tooling:** Treasure inventory (MUD-007) first, or test re-baseline (MUD-008)?
8. **Portfolio:** Extend orchestrate-repos / drain for ai-mud now, or only after MUD-002–004 land?

---

## Appendix A — Draft `AGENTS.md` outline (do not apply)

```markdown
# AGENTS.md

## Project Overview
AI-powered Kotlin MUD (console + Compose client). Multi-module Gradle. LLM optional (fallback without key).

## Startup Reads (minimal)
1. This file
2. Active ticket acceptance (+ plan if present)
3. README.md run path; KNOWN_ISSUES.md if touching inventory/treasure
4. NEVER full-read CLAUDE.md by default — use as optional deep status only

## Stack
- Kotlin / Gradle / Java 17 toolchain
- Modules: core, perception, reasoning, action, memory, llm, app, client, testbot, config, utils
- Secrets: local.properties (gitignored), OPENAI_API_KEY env

## Workflow
1. Classify: tooling / engine / client / docs / spike
2. Substantial work: plan under plans/YYYY-MM-DD-… → Astra/Jason approve → fresh impl session
3. One problem per ticket; serial one live builder per tree
4. Verify before done (see Verification)

## Definition of Done
- Code implements accepted behavior
- Tests for changed contract (mock LLM where possible)
- verify command on ticket exits 0
- Closeout: paths, verify result, residual risk
- Spikes/opinion: human_gated blocked — do not fake done

## Verification
Default: ./gradlew :core:compileKotlin   # or tools/verify_mud.sh when added
Scoped: ./gradlew :<module>:test for touched modules
Full suite: optional; reasoning has known debt — document, don’t thrash

## Protected / Secrets
- Never commit local.properties, API keys, *.db, test-logs with secrets
- No force-push / history rewrite unless Jason explicit
- plans/ and AGENTS.md rule changes need explicit approval

## Working Principles
- KISS; immutable state; sealed classes; files under ~1000 lines
- Console + client parity when changing handlers
- No drive-by refactors or dep upgrades

## Agent parity
Grok, Codex, Claude, Cursor — same DoD. Prefer neutral wording in new docs.
```

---

## Resolution note (for ticket)

Spike investigation complete. Report path:

**`docs/research/2026-08-08-ai-mud-agentic-modernization-spike.md`**

Awaiting **Jason opinion** on §10 before filing Phase A tickets and scheduling builders. **Not** product-done.
