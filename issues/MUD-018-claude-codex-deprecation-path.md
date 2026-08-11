---
id: MUD-018
area: docs
title: CLAUDE/CODEX deprecation path — deep status only
status: done
priority: low
created: 2026-08-09
updated: 2026-08-11
source: jason
labels: [docs, modernization, wave-e]
assignee: ""
worker: grok
phase: done
agent_eligible: true
eligibility: agent_eligible
depends_on: [MUD-003, MUD-005]
verify: "docs-only (banner/rg; optional --fast)"
plan: plans/2026-08-11-ai-mud-MUD-018-claude-codex-deprecation-path.md
worker_out_dir: tmp/workers/MUD-018
worker_pid: ""
grok_session: ""
codex_session: ""
approved_by: Astra
approved_at: "2026-08-11 02:40 MST"
---

# MUD-018 — CLAUDE/CODEX deprecation path

## Problem
Dual Claude/Codex SoT will keep drifting. After AGENTS is proven, freeze growth of Claude-only surface.

## Acceptance
- [x] Header/banner on CLAUDE.md + CODEX.md: optional deep status; AGENTS is SoT for ops
- [x] Stop growing `.claude/settings.local.json` as DoD
- [x] Optional slim pass later (not mandatory this ticket) — no huge rewrite required
- [x] AGENTS still says never full-read CLAUDE by default

## Resolution

Docs-only (2026-08-11, fresh impl after Astra approve):
- `CLAUDE.md` — deprecation banner prepended; body zero-diff
- `CODEX.md` — banner + collab rewrite (ops → AGENTS; no CLAUDE-as-canonical)
- `README.md` — AGENTS ops/DoD SoT; CLAUDE demoted from primary SoT
- `CLAUDE_GUIDELINES.md` — short top note (style ≠ ops)
- `AGENTS.md` — 1-line settings.local not DoD
- `.claude/settings.local.json` untouched
- Verify: `rg` banners + README no “primary source of truth”; body/settings hashes match
- Closeout: `tmp/workers/MUD-018/CLOSEOUT.md`

## Astra approve (2026-08-11 02:40 MST)
Docs-only: CLAUDE banner + CODEX collab + README SoT demote; freeze growth; settings.local untouched. Fresh impl.
