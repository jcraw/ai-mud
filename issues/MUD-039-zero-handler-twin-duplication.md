---
id: MUD-039
area: tooling
title: Zero handler twin duplication + hard gate (Wave Q4)
status: done
priority: med
created: 2026-08-19
updated: 2026-08-19
source: jason
labels: [quality-gates, wave-q]
assignee: ""
worker: "grok"
phase: done
agent_eligible: true
eligibility: done
needs_jason: ""
depends_on: [MUD-036, MUD-037]
verify: "./tools/verify_mud.sh --core"
plan: plans/2026-08-19-ai-mud-MUD-039-zero-handler-twin-duplication.md
worker_out_dir: tmp/workers/MUD-039
worker_pid: ""
---

# MUD-039 — Zero handler twin duplication + hard gate

## Problem
MUD-036 left 19 live app↔client handler clones as warn-only (`DUP_BLOCK_W`). New twins can land without failing `--core`.

## Acceptance
- [x] Checker `pairs=0` with empty allowlist (no allowlist of the 19)
- [x] Emit `DUP_BLOCK_E` (retire live W). Fail default/fast/core/full if E>0
- [x] Soft opt-out `MUD_DUP_SOFT=1` / `--dup-soft`. No R1 / no `MUD_DUP_HARD`
- [x] `--core` green after extracts + flip. Missing checker fail-closed when hard
- [x] Docs: `DUPLICATION_KT` R2; DESIGN C3; AGENTS; DOD_SUMMARY E live

## Non-goals
- Merge twins into one handler file
- GUI redesign
- GameServer combat/emote (not under `handlers/`)
- Intra-app / non-`handlers/` clones
- Rename “fixes”
- Hard gate before `pairs=0`
- New 037-style contract pack
- Smoke/PIT/LLM; token/detekt/Konsist policy

## Notes
Extract pures → thin IO wrappers → remeasure → flip hard. Hit `resolveWeapon` includes `HANDS_BOTH` (prep already did; console hit drifted).

## Builder
- session: wave BL-20260819155318 IMPL 1
- brief: `tmp/wave-runs/BL-20260819155318/MUD-039/IMPL/1/brief.md`

## Resolution
Extracted shared pures into `:action` / `:reasoning`; thinned app+client handler twins. Checker emits `DUP_BLOCK_E`. Verify hard on default/fast/core/full; soft `MUD_DUP_SOFT=1` / `--dup-soft`; missing checker fail-closed when hard. `--core` PASS `duplication_kt` `hard E=0 pairs=0`. Closeout `tmp/workers/MUD-039/CLOSEOUT.md`. dod-summary `tmp/dod-summary.json`.
