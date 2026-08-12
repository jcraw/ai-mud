# Builder plan/brief token preflight (MUD-033)

Lean gate so fat plans/briefs do not burn builder context before **APPROVED** / impl.

## Checker

| Path | Role |
|------|------|
| `tools/quality/check_builder_preflight.py` | Standalone Python3 checker |
| `./tools/verify_mud.sh --preflight <path>` | Optional thin wrapper (not on default lanes) |

**Token estimate:** `ceil(chars/4)` = `max(0, (len+3)//4)`. Raw file text (no fence strip).

## Budgets (DESIGN D1/D2)

| Role | Warn | Fail |
|------|------|------|
| **plan** | 2000 | 3500 |
| **brief** | 1200 | 2000 |

Classify: `*BRIEF*` → brief; `plans/*.md` or `PLAN*.md` (not BRIEF) → plan.

## Exit codes

| Code | Meaning |
|------|---------|
| **0** | All clear (or warn-only with `--allow-warn`) |
| **1** | Warnings only (no hard fail) |
| **2** | Any hard fail (over fail budget, missing explicit path) |

## Usage

```bash
# Single path (primary for plan approve)
python3 tools/quality/check_builder_preflight.py plans/YYYY-MM-DD-….md

# Or via verify (records gates.builder_preflight)
./tools/verify_mud.sh --preflight plans/YYYY-MM-DD-….md

# Full inventory (historical plans often exit 1 warn — expected)
python3 tools/quality/check_builder_preflight.py

# Treat warn as green (still fail on exit 2)
python3 tools/quality/check_builder_preflight.py --allow-warn plans/….md
```

Default globs: `plans/*.md`, `tmp/workers/*/PLAN*.md`, `tmp/workers/*/*BRIEF*.md`.

## Process

Before stamping **APPROVED** on a plan, run the checker on that plan path (see `issues/ORCHESTRATION.md`). Prefer **PATH mode** over full inventory so historical warn-band plans do not block.

## Verify behavior

- **Not** on default / fast / core / full / pitest / quarantine.
- `--preflight <path>` only: checker **2** → verify fail; **1** (warn) → **pass + note**; **0** → pass.

## Non-goals (v1)

- D7 mandatory-read pack graph (spawn-time) — later ticket
- Mass rewrite of historical fat plans
- Product `*.kt` / source-token (`check_token_budget_kt.py`) changes

## Design

Wave Q2 **D1/D2** — `docs/AGENT_QUALITY_GATES_DESIGN.md`.
