# MUD-001 Plan — Spike: agentic modernization (investigation only)

**Ticket:** MUD-001 · **Worker:** grok · **Phase:** planning → plan_review  
**Impl = fresh session** after Astra approve. Plan session is not resumed for product work.  
**Report path:** `docs/research/2026-08-08-ai-mud-agentic-modernization-spike.md`

---

## 1. Goal / acceptance mapping

| Goal | Maps to |
|------|---------|
| Inventory repo + gap vs modern agent ops | Report: current state + gap analysis |
| Concrete modernization recommendation | Report: end-state + phased tickets |
| Jason/Astra can file next tickets without re-explore | Self-contained report + follow-up list |

**Spike acceptance (report-only, not product DoD):**
- [ ] Report exists at path above
- [ ] Self-contained: snapshot, gaps, end-state, phased tickets, risks, Jason Qs, verdict
- [ ] Explicit “not verified” if build/tests/runtime skipped or shallow
- [ ] No gameplay / drive-by refactors in change set
- [ ] **Closeout ≠ done:** hand to Jason (see §7)

**Note:** Ticket body still says `done`; **brief overrides** — after report, use human_gated blocked handoff.

---

## 2. Current inventory probes (cheap)

Do in order; stop when enough for report bullets.

| Probe | How (cheap) | Signal |
|-------|-------------|--------|
| Git drift | `git status -sb`, `git log -5 --oneline` | Ahead/dirty; known dirty: testbot runners + untracked issues/tmp |
| Module map | `settings.gradle.kts` + top-level dirs | 11 modules: app, utils, llm, core, config, perception, reasoning, memory, action, testbot, client |
| Build health | `./gradlew :core:compileKotlin` or `:app:compileKotlin` (cap ~3–5 min) | Compiles? Skip full suite unless cheap |
| Test signal | Skim existing reports under `*/build/test-results` + CLAUDE.md claims | 621 core-ish green; reasoning ~23 fails documented — **re-run only if reports stale** |
| Secrets surface | Confirm `local.properties` gitignored; note `OPENAI_API_KEY` / fallback | Never commit keys |
| Legacy agent surface | Headers: `CLAUDE.md`, `CLAUDE_GUIDELINES.md`, `CODEX.md`, `.claude/settings.local.json` | Claude-era; no `AGENTS.md`; Claude-only perms |
| Issues board | `issues/BOARD.md`, ticket template, README | Board started; only MUD-001; no verify field yet |
| Known product bugs | `KNOWN_ISSUES.md` | Treasure-room inventory (GUI) open |
| Docs layout | `docs/` names only | Heavy design/status docs; empty `docs/research/` ready |
| Comparative | game_jam AGENTS SDD/DoD + ORCHESTRATION multi-turn; dustcrawl AGENTS lighter | Patterns to adapt, not copy |

**Plan-turn snapshot (2026-08-08):** `master` ahead 1 of origin; dirty testbot 2 files; untracked `issues/`, `tmp/`, skill_progression logs. Last commits Dec-ish 2025 era (`bot recognize low health`). Portfolio: agentic onboarding goal; stack stays Kotlin/Gradle.

---

## 3. Recommended investigation approach (ordered)

1. **Freeze scope** — spike report only; no AGENTS.md apply, no module edits.
2. **Repo reality** — git drift + module tree + run path (`gradle installDist` / app bin) from README.
3. **Legacy agent surface** — what agents read today; Claude-centric vs neutral; CODEX.md coexists.
4. **Board maturity** — MUD board vs game_jam ORCHESTRATION (serial-per-app, plan→impl, briefs, token budgets, verify).
5. **Target end-state (minimal viable agentic)** — propose lean set: AGENTS.md, issues template+BOARD hygiene, plan/impl briefs, verify command(s), protected paths, secret rules.
6. **Kotlin/Gradle landmines** — multi-module, long tests, LLM flakiness, API key optional, Compose client, testbot cost.
7. **Gap table** — have / partial / missing for each target artifact.
8. **Phased backlog** — tooling wave first, then product bugs, then features; IDs optional.
9. **Write report** once; optional AGENTS outline as appendix only.
10. **Human_gated closeout** — Jason opinion gate (not mark done).

---

## 4. Report outline + path

**Path:** `docs/research/2026-08-08-ai-mud-agentic-modernization-spike.md`

```
# AI MUD — Agentic modernization spike (2026-08-08)
## 1. Executive verdict
## 2. Current state snapshot (stack, modules, last activity, drift)
## 3. Legacy agent surface (CLAUDE*/CODEX/.claude/docs)
## 4. Target agentic model (game_jam/dustcrawl patterns adapted)
## 5. Gap analysis table (have / partial / missing)
## 6. Recommended end-state (minimal viable agent ops)
## 7. Phased follow-up tickets (title, priority, deps, type)
## 8. Risks / landmines
## 9. What was not verified
## 10. Open questions for Jason
## Appendix A (optional): draft AGENTS.md outline — do not apply
```

---

## 5. What not to touch

- No Kotlin/game modules, gameplay, handlers, worldgen, client UI
- No drive-by refactors / dep upgrades / full CI green-up
- No force-push, history rewrite, secret commits (`local.properties`, keys)
- No applying AGENTS.md / verify scripts as “done modernization”
- No scheduling further builders unless Jason says go
- Bookkeeping only outside report: ticket, BOARD, `tmp/workers/MUD-001/`, plans/

---

## 6. Verify strategy (spike)

Spike has **no product verify gate**. Success =
1. Report file exists at §4 path  
2. Covers §1 checklist sections  
3. Follow-ups listed (backlog-shaped)  
4. “Not verified” explicit if probes shallow  
5. Diff = report + ticket/BOARD bookkeeping only  

Optional cheap: `./gradlew :core:compileKotlin` noted in §9 of report if run.

---

## 7. Ordered Turn-2 steps (fresh impl session)

1. Read this plan + ticket acceptance (not full CLAUDE.md).
2. Run §2 probes (shallow OK); record results.
3. Write report to `docs/research/2026-08-08-ai-mud-agentic-modernization-spike.md`.
4. Optional: AGENTS outline appendix only.
5. Ticket bookkeeping after findings:
   - `status: blocked`
   - `assignee: jason`
   - `needs_jason: opinion`
   - `agent_eligible: false`
   - `eligibility: human_gated`
   - `phase: spike_complete` (or equivalent)
   - `report:` path set; Resolution → summary + link + “awaiting Jason”
   - **Do not** set `done`
6. BOARD → **Blocked (awaiting Jason)** for MUD-001; leave plan_review empty.
7. Update `issues/README.md` status row.
8. Worker note in `tmp/workers/MUD-001/` if useful; stop.

---

## 8. Out of scope

- Implementing AGENTS.md, verify tooling, CI, network multiplayer
- Product bugfix (treasure inventory) except mention as backlog
- V1/V2 field removal, dep bumps, testbot green-all
- Portfolio/repo-issues skill extension (recommend only)
- Chat resume of this plan session for impl

---

## 9. Risks / open questions (blocking only)

| Item | Why it matters |
|------|----------------|
| Dirty tree (testbot + untracked issues) | Impl must not commit secrets/logs; clarify what lands in first PR |
| Ticket body `done` vs brief `blocked` | Use brief closeout; Jason may still want `done` after review — ask in report Qs |
| Long Gradle/testbot runtime | May force “not verified” on full suite — OK for spike |
| No session id on plan turn | `plan_session` may stay empty; Astra uses plan file as contract |

**Non-blocking for report:** exact AGENTS wording, which builder defaults, whether to archive CLAUDE.md.


---
Status: APPROVED by Astra 2026-08-08 20:17 MST
