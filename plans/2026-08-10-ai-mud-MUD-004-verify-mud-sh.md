# MUD-004 Plan — tools/verify_mud.sh lanes

**Ticket:** MUD-004 · **Worker:** grok · **Phase:** planning → plan_review  
**Impl = fresh session** after Astra/Jason approve. This plan turn is not resumed for product edits.  
**Plan path:** `plans/2026-08-10-ai-mud-MUD-004-verify-mud-sh.md`  
**Worker mirror:** `tmp/workers/MUD-004/PLAN.md`

## APPROVED by Astra

- **When:** 2026-08-10 ~18:55 America/Phoenix
- **Verdict:** Approve as-is. Thin wrapper, honest default/fast compile smoke, core/full exclude reasoning thrash, quarantine hard-fail, stub hooks only for later gates. Scope clean (no MUD-008/010+ creep).
- **Next:** Fresh IMPL session only — do not resume plan session for product edits.

---

## 1. Goal / acceptance mapping

| Acceptance | Impl delivers |
|------------|---------------|
| `tools/verify_mud.sh` exists + executable | New bash wrapper under `tools/` |
| **default/fast** | No args / `--fast` / `default`: `:core:compileKotlin`; optional module args → `:<m>:compileKotlin` (+ `:<m>:test` when module has tests / explicit) |
| **core** | `--core`: `:core:test :perception:test :memory:test` (exclude quarantine tags when MUD-008 wires them) |
| **full** | `--full`: broader green modules (compile + unit tests for stable set); **does not** thrash on `:reasoning` as hard green; prints debt note |
| **quarantine** | `--quarantine`: run known-red path (`:reasoning:test` now; later `@Tag("quarantine")` include) — honest non-zero OK |
| Exit non-zero on real failure; short summary | Aggregate exit; print lane / steps / PASS\|FAIL / notes |
| `AGENTS.md` Verification → this script | Replace placeholder table with `./tools/verify_mud.sh` + lane flags |
| Ticket `verify:` / template note | Default `./tools/verify_mud.sh`; document flags in help + AGENTS |

**Ticket `verify:` after ship:** `./tools/verify_mud.sh` (default/fast).

---

## 2. Current inventory

| Item | Status |
|------|--------|
| `tools/` | **Missing** — create with script |
| Verify entrypoint | **None** |
| `test_spatial_coherence.sh` | Ad-hoc gameplay script (root); **not** a gate; leave alone |
| Modules (11) | app, utils, llm, core, config, perception, reasoning, memory, action, testbot, client |
| Unit-test density | core 24 · memory 20 · perception 4 · reasoning 41 · testbot 15 · client 1 · others 0 |
| Quarantine tags | **None yet** (MUD-008). Only `CombatResolverTest.kt.disabled` on disk |
| `:reasoning` debt | Docs/spike: ~22–23 known fails; blind `./gradlew test` thrash risk |
| Detekt / Konsist / PIT | Not wired (MUD-010/011/014) — stub hooks only |
| AGENTS Verification | Placeholder: interim compile/scoped/full table until MUD-004 |
| DIGEST-025 | Pointer in-repo; lanes draft: Fast / Core / Full; quarantine tags + DoD JSON later |
| Gradle | `./gradlew` present; module tasks `compileKotlin` / `test` standard |

---

## 3. Recommended approach

- **Thin bash wrapper** over `./gradlew` — no new Gradle plugin, no Docker, no CI (MUD-016).
- **Honest defaults:** default lane is **compile smoke**, not full green theater.
- **Lane CLI:** `./tools/verify_mud.sh [default|fast|core|full|quarantine] [module…]`  
  Also accept long flags: `--fast` `--core` `--full` `--quarantine` `--help` `--dry-run`.
- Prefer `set -euo pipefail`; cd to repo root from script location.
- Use `./gradlew` (wrapper), not bare `gradle`.
- When quarantine tags **absent**, core/full simply omit `:reasoning:test`; print one-line note pointing to MUD-008/017.
- When tags **exist** later: append Gradle/JUnit exclude filter if project supports it (document as follow-up wiring; do not invent full tag infra here).
- **Stub hooks:** after real steps, optionally call `maybe_run_task <name>` — if `./gradlew tasks` does not list task, print `SKIP stub: <gate> (MUD-0xx)` and continue. Do **not** fail on missing future gates.

---

## 4. Files to create/touch

| Path | Action |
|------|--------|
| `tools/verify_mud.sh` | **Create** (~80–150 lines), `chmod +x` |
| `AGENTS.md` | **Edit** Verification section only — default `./tools/verify_mud.sh`; lane table; remove “Placeholder until MUD-004” |
| `README.md` | **Optional** 2–4 line Testing pointer if a natural “verify” spot exists; skip if would bloat |
| `issues/_templates/ticket.md` | **Optional** set example `verify: "./tools/verify_mud.sh"` |
| `issues/MUD-004-…` / `BOARD.md` | Bookkeeping at plan end + closeout at impl |

**No** `*.kt`, no Gradle plugin, no Detekt/PIT/Konsist config this ticket.

---

## 5. Lane commands + exit codes + summary

| Lane | Command body (impl) | Fail policy |
|------|---------------------|-------------|
| **default/fast** | No modules: `./gradlew :core:compileKotlin`. With `mod…`: for each, compile (+ test if `src/test` exists or always test when arg given — pick **always test if arg + test sources exist**, else compile-only) | Any gradle ≠0 → exit ≠0 |
| **core** | `./gradlew :core:test :perception:test :memory:test` | Same |
| **full** | Stable green set e.g. `./gradlew :core:test :perception:test :memory:test :action:compileKotlin :llm:compileKotlin :config:compileKotlin` (+ optional `:client:compileKotlin` if cheap enough; **exclude** testbot by default — slow/integration). Print: “`:reasoning` not in full green; use --quarantine (MUD-008 tags later)” | Same |
| **quarantine** | `./gradlew :reasoning:test` (today’s known-red suite). Summary must say failures may be expected debt until MUD-008/017 | **Still** exit ≠0 on fail — honesty, not soft-pass |

**Summary print (always, last lines):**
```
== verify_mud ==
lane: <name>
steps: <one-line command list or numbered>
result: PASS|FAIL (exit N)
notes: <reasoning debt / stubs skipped>
```

**Exit codes:** `0` all executed hard steps green; `1` usage/unknown lane; `2` (or gradle’s) step failure — pick simple: **usage=1, fail=gradle exit or 1**. Document in `--help`.

---

## 6. Stub hooks (MUD-010+)

| Future gate | Ticket | Script behavior now |
|-------------|--------|---------------------|
| Detekt | MUD-010 | `maybe_stub detekt` — SKIP unless task present |
| Konsist | MUD-011 | SKIP unless test/task present |
| Test-file lock | MUD-012 | SKIP |
| PIT | MUD-014 | SKIP; never default |
| dod-summary.json | MUD-013 | Out of scope — do not invent JSON yet |

Comment block in script: “wire when task exists; do not fail missing”.

---

## 7. Tests / verify for the script itself

1. `bash -n tools/verify_mud.sh` — syntax OK  
2. `./tools/verify_mud.sh --help` → 0, prints lanes  
3. `./tools/verify_mud.sh --dry-run` (or `default --dry-run`) → prints intended gradle cmds, no build required  
4. Optional smoke (impl session if time): `./tools/verify_mud.sh` → expects `:core:compileKotlin` green (needs JDK 17)  
5. Ticket verify after ship: `./tools/verify_mud.sh` exits 0 on clean tree  

No shell unit framework required.

---

## 8. Ordered impl steps (fresh session)

1. Create `tools/`; write `verify_mud.sh` (lanes, dry-run, help, summary, stubs).  
2. `chmod +x tools/verify_mud.sh`.  
3. `bash -n` + `--help` + `--dry-run` self-check.  
4. Update `AGENTS.md` Verification (lane table → script).  
5. Optional: template `verify:` example; tiny README pointer.  
6. Run default lane once if environment allows; note result in closeout.  
7. Ticket → `done` (or `impl` then done); BOARD move; residual risk note.  

---

## 9. Out of scope

- Full suite re-baseline / `@Tag("quarantine")` wiring → **MUD-008**  
- Clear reasoning quarantine → **MUD-017**  
- CI workflows → **MUD-016**  
- Detekt / Konsist / PIT / dod JSON → **MUD-010–014**  
- Product gameplay, handler fixes, dependency upgrades  
- Fixing the ~22 reasoning failures  
- Replacing `test_spatial_coherence.sh`  
- Git commit/push (unless Jason asks later)

---

## 10. Risks

| Risk | Mitigation |
|------|------------|
| Agents still run bare `./gradlew test` | AGENTS + ticket `verify:` + BOARD already point here after ship |
| full lane still too slow (client Compose) | Keep client out of full or compile-only; document |
| core fails for env/JDK reasons | Document Java 17; dry-run still works |
| Soft-passing quarantine confuses DoD | Quarantine lane stays hard-fail; never claim green |
| Tag exclude before MUD-008 | Comment + skip reasoning; no fake JUnit filter |
| Script path from subdirs | Resolve repo root via `SCRIPT_DIR` |
| Scope creep into quality-gate install | Stubs only; refuse to add plugins this ticket |

---

## Handoff

- **Approve this plan** → fresh impl session (do not continue planning session into script write unless ticket overrides).  
- **verify (post-impl):** `./tools/verify_mud.sh` and `bash -n tools/verify_mud.sh`.  
- **Depends on:** MUD-003 done (AGENTS present). **Unblocks:** MUD-007/008/010+ verify field honesty.
