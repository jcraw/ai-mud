# MUD-032 Plan — No live LLM/network in unit tests (Wave Q2 · B2)

**Ticket:** MUD-032 · **Worker:** grok · **Phase:** implementing  
**Status:** APPROVED by Astra 2026-08-12 01:53 MST  
**Prior:** PLAN ONLY — fresh impl authorized.  
**Paths:** `plans/2026-08-12-ai-mud-MUD-032-no-live-llm-unit-tests.md` · mirror `tmp/workers/MUD-032/PLAN.md`  
**Post-impl verify:** `./tools/verify_mud.sh --core` · **Depends:** MUD-031 done+pushed `51abb5d`  
**Fresh IMPL authorized** — do not resume plan session.

---

## 1. Goal / acceptance mapping

| # | Acceptance | Deliver |
|---|------------|---------|
| 1 | Gate hard-fails unit tests under `*/src/test/**` that call real OpenAI / network LLM clients | Static rg gate on forbidden patterns |
| 2 | Mocks / frozen fixtures only; allowlist if integration needs network; **testbot excluded** | Hard-exclude `testbot/**`; optional allowlist file (empty v1) |
| 3 | Wired into core verify | `record_gate no_live_llm_unit` on default/fast/core/full |
| 4 | `--core` exit 0 | Clean tree passes (inventory: no live calls outside testbot) |
| 5 | Short docs note | AGENTS one-liner + lean quality/TESTING note + DESIGN B2 pointer |

---

## 2. Current inventory

| Piece | State |
|-------|--------|
| **Live `OpenAIClient(`** | **Only** `testbot/src/test/**` (BehaviorTestBase + playthrough scenarios). **Zero** under non-testbot `src/test` |
| **Key load** `OPENAI_API_KEY` / `openai.api.key` | Same: testbot only |
| **Mocks (OK)** | memory + reasoning unit tests: `MockLLMClient : LLMClient`, return frozen `OpenAIResponse` fixtures; `chatCompletion` overrides. **No** `import OpenAIClient` outside testbot tests |
| **`--core` units** | `:core:test :perception:test :memory:test :reasoning:test` + default `excludeTags("quarantine")` (`kotlin-jvm.gradle.kts`) · **not** testbot |
| **Other `src/test`** | app, client, utils, testbot exist; action/llm/config: no `src/test` |
| **testbot placement** | Live path by design (API key → `OpenAIClient`); **not** on `--core`/`--full` unit path; separate product lane — **do not rewrite** |
| **Existing tags** | Only `@Tag("quarantine")` on reasoning debt; no live-llm tag |
| **Gate wiring pattern** | MUD-031: `tools/quality/*` checker + `run_*` in `verify_mud.sh` + `record_gate` + optional dod gate (not in required `known` tuple with token_budget) |
| **DESIGN B2** | `no_live_llm_unit` hard · “grep/Konsist-ish: unit tests must not call real OpenAI” |

---

## 3. Design / recommended approach

### Prefer **static rg shell gate** (not bytecode, not new Konsist test)

| Option | Verdict |
|--------|---------|
| **Static `rg` script** (`tools/quality/check_no_live_llm_unit.sh`) | **Recommended** — KISS, matches DESIGN “grep/…”, no test-lock churn, no JUnit compile, mirrors `test_lock.sh` |
| Konsist on test sources | Heavier; Konsist suite is prod layers today; would touch `src/test` → test-lock regen |
| Bytecode / runtime net-block | Overkill for B2; flakey |

### Forbidden patterns (fail = any match in scanned `.kt`)

1. `\bOpenAIClient\s*\(` — real client **construction** (not type name alone)
2. `OPENAI_API_KEY` — env key load
3. `openai\.api\.key` — properties key load

### Explicit **non-matches** (false-positive strategy)

| Pattern | Why OK |
|---------|--------|
| `OpenAIResponse` / builders returning it | Frozen fixture **type**, not network |
| `class MockLLMClient : LLMClient` / `chatCompletion` overrides | Interface mock |
| `import …OpenAIClient` **without** `OpenAIClient(` | Soft; construction is the hard rule (import alone = 0 outside testbot today) |
| Generic `HttpClient(` / non-OpenAI URLs | **Out of scope** v1 (risk: FP on unrelated HTTP tests) — document residual |

### Scope / carve-outs

- **Scan:** all `*/src/test/**/*.kt` under repo root  
- **Hard-exclude always:** `testbot/**` (integration/behavior lane; document only)  
- **Allowlist (v1 empty):** `config/quality/no_live_llm_unit_allowlist.txt` — relative paths or globs, `#` comments; for future non-testbot integration tests if ever needed  
- **Not scanned:** `src/main/**` (product may construct client)

### Verify wire

- **Lanes:** default / fast / core / full — **hard** (exit 1 on hits)  
- **Skip:** quarantine (debt-only); **optional:** also run on pitest (cheap; prefer **yes** for parity with detekt/test_lock)  
- Placement: after `test_lock`, near `token_budget`  
- Gate name: **`no_live_llm_unit`**  
- dod-summary: **optional** gate (like `token_budget`); `finalize_gates` + dry-run list; findings codes e.g. `LIVE_LLM_OPENAI_CLIENT` / `LIVE_LLM_API_KEY` via `append_finding` when fail  
- **Do not** add to required `known` schema tuple (avoid schema churn); schema `additionalProperties` already OK  
- Checker missing / `rg` missing → **fail closed** on hard lanes (same spirit as test_lock)

### Output

- Human: path:line + matched rule  
- Exit 0 clean / 1 any hit  
- Standalone: `./tools/quality/check_no_live_llm_unit.sh` from repo root

---

## 4. Files to create/touch

| Path | Action |
|------|--------|
| `tools/quality/check_no_live_llm_unit.sh` | **Create** — rg scan, excludes, allowlist, exit codes |
| `config/quality/no_live_llm_unit_allowlist.txt` | **Create** — empty + header comment (testbot hard-excluded in script, not listed) |
| `tools/verify_mud.sh` | `run_no_live_llm_unit` / skip; wire lanes; usage; dry-run; finalize/dry gate list |
| `docs/NO_LIVE_LLM_UNIT.md` **or** short section in `docs/TESTING.md` | Lean policy + patterns + allowlist + testbot carve-out |
| `AGENTS.md` | Verification one-liner (gate on default/core) |
| `docs/AGENT_QUALITY_GATES_DESIGN.md` | Surgical B2 “live” pointer only |
| `docs/DOD_SUMMARY.md` | Optional gate + finding codes one line |
| Ticket / BOARD / closeout | impl session |

**No:** product `*.kt`, testbot rewrite, mass `src/test` rewrites (tree already clean), test-lock regen (unless impl accidentally edits tests), git commit/push.

---

## 5. Non-goals

- Rewriting testbot product/behavior  
- Product play / headless smoke (MUD-038)  
- Token hard-on-touched changes (MUD-031 done)  
- Plan/brief token preflight (MUD-033)  
- God splits / PIT 80% / dup / handler parity (034–037)  
- Blocking non-OpenAI network generically  
- git commit/push unless ticket later says  

---

## 6. How impl confirms acceptance

- [ ] Clean `./tools/verify_mud.sh --core` → exit 0; `no_live_llm_unit: pass`  
- [ ] Synthetic: add `OpenAIClient("x")` under e.g. `memory/src/test/...` (or run checker against temp fixture path if supported) → gate **fail**; **revert** before close  
- [ ] testbot still has live calls; core verify **green** (excluded)  
- [ ] Allowlist: if smoke path is allowlisted, green; unlisted fails  
- [ ] Mock files with `OpenAIResponse` / `MockLLMClient` still pass  
- [ ] Quarantine: gate skipped (or not fail-forced)  
- [ ] Docs + AGENTS note present  
- [ ] Closeout + `tmp/dod-summary.json` cite  

---

## 7. Ordered impl steps

1. Add `check_no_live_llm_unit.sh` + empty allowlist; manual run → 0 hits outside testbot  
2. Wire `run_no_live_llm_unit` into `verify_mud.sh` (default/core/full ± pitest); dry-run + finalize  
3. Finding codes + human notes; fail-closed if script/`rg` missing  
4. Docs: NO_LIVE_LLM_UNIT or TESTING subsection; AGENTS; DESIGN B2 pointer; DOD_SUMMARY optional gate  
5. Smokes (§6); clean `--core`  
6. Ticket → done; BOARD; closeout under `tmp/workers/MUD-032/`  
7. **No** commit/push unless ticket requires  

---

## 8. Risks

| Risk | Mitigation |
|------|------------|
| **FP on `OpenAIResponse` mocks** | Match `OpenAIClient(` + key strings only; never bare `OpenAI` |
| **testbot bleed into core** | Hard-exclude `testbot/**`; never add `:testbot:test` to core |
| **Non-OpenAI network** | Residual v1; document; expand only via new ticket |
| **Multi-module globs** | `rg --glob '**/src/test/**/*.kt'` + path exclude, not hand module list |
| **test-lock on synthetic smoke** | Revert test edit before end; prefer checker-only fixture if easy |
| **Comments mentioning API key** | Accept rare FP or strip `//` lines — prefer simple first |
| **Allowlist abuse** | Require comment + ticket id in allowlist line; review in PR |

---

## Impl handoff

- Approve this plan → fill `tmp/workers/MUD-032/IMPL_BRIEF.md` from template  
- **Fresh** impl session (do not resume plan session for product/tools ship)  
- Verify: `./tools/verify_mud.sh --core`
