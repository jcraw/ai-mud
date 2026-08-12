# MUD-033 Plan — Builder plan/brief token preflight (Wave Q2 · D1/D2)

**Ticket:** MUD-033 · **Worker:** grok · **Phase:** plan_review  
**Status:** APPROVED by Astra 2026-08-12 02:14 MST → fresh IMPL session
**Paths:** `plans/2026-08-12-ai-mud-MUD-033-builder-preflight-token.md` · mirror `tmp/workers/MUD-033/PLAN.md`  
**Post-impl verify:** `./tools/verify_mud.sh` (+ optional `--preflight <path>`) · **Depends:** MUD-030 done  
**Not impl approval** — Astra/Jason APPROVED → **fresh** impl session.

---

## 1. Goal / acceptance mapping

| # | Acceptance | Deliver |
|---|------------|---------|
| 1 | Check `plans/*.md`, `tmp/workers/*/PLAN*.md` + briefs vs DESIGN budgets | Python checker; plan **2k warn / 3.5k fail**; brief **1.2k / 2k**; tok ≈ chars/4 |
| 2 | Exit **0** clear / **1** warn-only / **2** hard fail | Script help + lean doc |
| 3 | ORCHESTRATION or AGENTS one-liner before plan approve | Prefer `issues/ORCHESTRATION.md` plan→approve |
| 4 | No product `*.kt` | Tools + docs/ops only |
| 5 | Optional `verify_mud.sh --preflight <path>` | Thin flag → checker; not on default lanes |

---

## 2. Current inventory

| Piece | State |
|-------|--------|
| **Mud preflight** | **None** under `tools/` |
| **Source-token analogue** | `check_token_budget_kt.py` — prod `.kt`; exit 0; tok ceil(chars/4); verify owns hard (031) |
| **Verify hooks** | `record_gate` + `run_*`/`skip_*`; arg case lanes/flags; **no** `--preflight`; optional gates via schema `additionalProperties` |
| **DESIGN D** | D1 plan 2k/3.5k; D2 brief 1.2k/2k; D7 pack (spawn); lane **agent-preflight** proposed |
| **Jam** | `../Ai/game_jam/tools/check_token_budget_gates.py` — exit 0/1/2; same plan/brief thresholds; pack = full D7 (non-goal clone) |
| **Naming** | `plans/*.md`; worker `PLAN.md` / `PLAN*.md`; briefs `*BRIEF*.md` |
| **Samples (//4)** | Plans 030≈2291, 032≈2100 **warn**; 031≈1616 ok. Briefs mostly &lt;1.2k; fail rare |
| **032 residual** | Wire pattern: `tools/quality/*` + optional verify + ORCH one-liner; no product kt |

---

## 3. Design / recommended approach

### Script: `tools/quality/check_builder_preflight.py` (Python3)

| Choice | Decision |
|--------|----------|
| Token | `max(0, (len(text)+3)//4)` (jam/kt ceil chars/4) |
| Plan | warn **2000** / fail **3500** (D1) |
| Brief | warn **1200** / fail **2000** (D2) |
| Level | ≥fail → fail; ≥warn → warn; else clear |
| Exit | **0** clean; **1** warn only; **2** any fail. Optional `--allow-warn` → 0 on warn-only (still 2 on fail) |
| Classify | `*BRIEF*` → brief; `plans/*` or `PLAN*.md` (not BRIEF) → plan; skip else |
| Default globs | `plans/*.md`; `tmp/workers/*/PLAN*.md` excl BRIEF; `tmp/workers/*/*BRIEF*.md` |
| PATH mode | Positional paths = check only those (**primary** for approve) |
| Double-count | Check each path independently (mirror + `plans/` both loadable) |
| Missing explicit path | fail finding `path_missing`; empty default scan → 0 |
| Strip fences? | **No** — raw file text |
| D7 pack | **Not v1** — help note only; no mandatory-read graph |
| Config | Constants in script (no JSON v1) |

### CLI

```text
python3 tools/quality/check_builder_preflight.py [--root .] [PATH ...]
  [--allow-warn] [--json-out PATH] [-h]
```

No PATH → full inventory. Lines: `path: ROLE tok=N (W/F) → level`. Summary → exit code.

### Optional verify wire

- `./tools/verify_mud.sh --preflight <path>` (required path): run checker only; `record_gate builder_preflight`. Checker **2** → verify fail; **1** (warn) → **pass + note**; **0** → pass.
- **Not** on default/fast/core/full/pitest/quarantine.
- Dry-run prints cmd. No schema `known` tuple churn.

### Docs one-liner

`issues/ORCHESTRATION.md` plan→approve: before APPROVED, run  
`python3 tools/quality/check_builder_preflight.py <plan-path>`  
(or `./tools/verify_mud.sh --preflight <plan-path>`). Optional AGENTS half-line only if needed.

---

## 4. Files to create/touch

| Path | Action |
|------|--------|
| `tools/quality/check_builder_preflight.py` | **Create** |
| `docs/BUILDER_PREFLIGHT.md` | **Create** lean policy |
| `tools/verify_mud.sh` | Optional `--preflight PATH` + usage/dry-run/record_gate |
| `issues/ORCHESTRATION.md` | One-liner |
| `docs/AGENT_QUALITY_GATES_DESIGN.md` | D1/D2 live pointer |
| Ticket / BOARD / CLOSEOUT | Impl bookkeeping |

**Not touch:** product `*.kt`, test-lock, token_kt config, jam launcher, full D7.

---

## 5. Non-goals

- Cross-repo launcher changes · full jam pack scanner (D7) · source-token / no-live-LLM rework · 034–038 · auto-fail default verify on historical fat plans · commit/push unless Jason asks

---

## 6. How impl confirms acceptance

| Check | Expect |
|-------|--------|
| Under-budget plan path | exit **0** |
| Synthetic plan ≥3.5k tok | exit **2** (delete fixture) |
| Warn band 2k–3.5k, no `--allow-warn` | exit **1** |
| `--allow-warn` on warn-only | exit **0** |
| Brief ≥2k tok | exit **2** |
| Default inventory | 0 or 1 (historical warn); not 2 unless fail-tier exists |
| ORCHESTRATION (or AGENTS) one-liner | `rg` preflight/check_builder |
| `--preflight` ok plan / fat plan | verify 0 / non-zero |
| Default `./tools/verify_mud.sh` | unchanged lanes; no forced preflight |
| No product `*.kt` | `git diff --name-only` |
| `--help` documents exit 0/1/2 | yes |

---

## 7. Ordered impl steps

1. Ship `check_builder_preflight.py` (thresholds, classify, globs, PATH, exit 0/1/2, `--allow-warn`).  
2. Smoke: lean path → 0; fat → 2; warn → 1.  
3. `docs/BUILDER_PREFLIGHT.md` (≤~80 lines).  
4. Optional verify `--preflight` + usage + dry-run.  
5. ORCHESTRATION one-liner; DESIGN D1/D2 pointer.  
6. Confirm default verify still green.  
7. Closeout: acceptance boxes, BOARD done, `tmp/workers/MUD-033/CLOSEOUT.md`.

---

## 8. Risks

| Risk | Mitigation |
|------|------------|
| Tables/fences inflate chars | Raw count; fail 3.5k generous |
| Glob misses | Document; PATH mode for explicit files |
| Double plan + mirror | Independent check intentional |
| Historical plans in warn (030/032) | Full scan exit 1; approve uses **single path**; no mass rewrite |
| `PLAN_BRIEF` as plan | `*BRIEF*` wins before `PLAN*` |
| Verify maps warn as fail | Warn ≠ verify fail; only exit 2 hard |
| D7 scope creep | Non-goal; help pointer only |

---

## Impl session note

Fresh session after **APPROVED**. Do not resume plan session for tool edits. Serial one builder. No secrets; no force-push; no commit unless Jason asks.
