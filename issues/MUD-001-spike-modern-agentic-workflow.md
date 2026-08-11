---
id: MUD-001
area: tooling
title: Spike — modernize AI MUD for agentic workflow
status: done
priority: high
created: 2026-08-05
updated: 2026-08-09
source: jason
labels: [spike, agentic, modernization, workflow]
spike: true
assignee: jason
handoff_to: jason
worker: grok
brief: tmp/workers/MUD-001/IMPL_BRIEF.md
plan: plans/2026-08-08-mud-001-agentic-modernization-spike.md
worker_out_dir: tmp/workers/MUD-001
worker_pid: ""
plan_session: "019fe47a-faf4-7c31-9ff6-58a67fd00a52"
grok_session: "019fe487-95ca-70a2-861b-d4a548531ac2"
codex_session: ""
phase: done
verify: ""
report: docs/research/2026-08-08-ai-mud-agentic-modernization-spike.md
prior_failure: "2026-08-07 plan turn permission_cancelled on run_terminal_command under acceptEdits; relaunched 2026-08-08 with bypassPermissions"
impl_session: "019fe487-95ca-70a2-861b-d4a548531ac2"
needs_jason: ""
agent_eligible: false
eligibility: done
---

# MUD-001 — Spike: modernize AI MUD for agentic workflow

## Problem
AI MUD is a real, feature-rich Kotlin MUD (combat/items/skills/social/quests/worldgen/LLM/RAG/persistence/multi-user/Compose client/testbot) last actively driven in a **Claude Code** workflow (`CLAUDE.md`, `.claude/`, older agent notes). The rest of Jason’s stack now runs a **modern agentic loop**: in-repo tickets, BOARD backlog, multi-turn plan→approve→impl builders (Grok/Codex), AGENTS.md SDD, verify gates, thin Astra orchestration.

This repo has **not** been inventoried against that model. We don’t yet know what to keep, replace, add, or sequence to bring AI MUD into the same operating system without thrashing a large working codebase.

## Intent
**Spike only.** Investigate the repo + compare to current agentic practices (game_jam / dustcrawl-website / orchestration skills) and produce a concrete modernization recommendation.

Investigate at least:
1. **Repo reality** — modules, build, run paths, tests, LLM providers, secrets, known issues, drift (dirty tree / ahead of origin)
2. **Legacy agent surface** — `CLAUDE.md`, `CLAUDE_GUIDELINES.md`, `CODEX.md`, `.claude/`, docs layout, any verify/scripts
3. **Target agentic workflow** — what “like everything else” means here: issues board (started), AGENTS.md, plan/impl briefs, builder handoff, quality/verify gates, memory of DoD
4. **Gaps & risks** — Kotlin/Gradle specifics, flaky/long tests, API keys, multi-module boundaries, what would break agents
5. **Phased backlog proposal** — ordered follow-up tickets (tooling first vs product), explicit non-goals

**Out of scope this ticket:**
- Implementing modernization (no AGENTS.md rewrite as “done work” beyond optional draft appendix)
- Product features / gameplay changes
- Dependency upgrades for their own sake (recommend only)
- Force-push / history rewrite / secret commits
- Scheduling builders unless Jason says go after the report

## Type
**spike** — investigation + written recommendation; no required behavior change to the game.

## Deliverable
**`docs/research/2026-08-08-ai-mud-agentic-modernization-spike.md`** (written 2026-08-08)

## Acceptance (spike)
- [x] Report path exists and is self-contained enough for Jason + Astra to file next tickets without re-exploring
- [x] Explicit list of proposed follow-up tickets (backlog-shaped)
- [x] States what was *not* verified (build/tests/runtime) if skipped
- [x] No drive-by gameplay or large refactors in the same change set
- [ ] ~~Ticket → done~~ **Overridden:** human_gated blocked for Jason opinion (plan brief)

## Builder
- worker: grok
- plan: `plans/2026-08-08-mud-001-agentic-modernization-spike.md`
- plan_session: `019fe47a-faf4-7c31-9ff6-58a67fd00a52`
- impl_session / grok_session: `019fe487-95ca-70a2-861b-d4a548531ac2`
- phase: `spike_complete`

## Resolution

**Done 2026-08-09** — Jason accepted background/spare-capacity posture; full backlog filed MUD-003…018.

- Report: `docs/research/2026-08-08-ai-mud-agentic-modernization-spike.md`
- Decisions: do everything over time on spare agent power; ops→tests/quarantine→gates→refactor; not high priority; eventual friends play + fan-facing niceties
- Closeout: human gate cleared; implementation via board drain order
