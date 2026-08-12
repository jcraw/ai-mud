---
id: MUD-034l
area: engine
title: Split social/trade/treasure handlers (Wave Q3)
status: in_progress
priority: med
created: 2026-08-12
updated: 2026-08-12
source: jason
labels: [quality-gates, wave-q, refactor, god-file-split]
assignee: "grok"
worker: "grok"
phase: implementing
agent_eligible: true
eligibility: agent_eligible
depends_on: [MUD-034, MUD-031]
verify: "./tools/verify_mud.sh --core"
plan: plans/2026-08-12-ai-mud-MUD-034l-social-trade-treasure-split.md
worker_out_dir: tmp/workers/MUD-034l
worker_pid: ""
parent: MUD-034
---

# MUD-034l — Social/trade/treasure split

## Problem
Token hard-on-touched (MUD-031) grandfathers oversized hosts under `ticket: MUD-034`. This family owns a **behavior-preserving** extract so file-token/LOC can return toward global ceilings (tok E2500 / LOC E1100).

**Family:** Social/trade/treasure · **peak measured tokens:** 3491 · **note:** small; ≤1–2 pairs/PR

**Hosts (from `tmp/workers/MUD-034/RANKED_GODS.md`):**

| path | file_tokens | file_loc | override tok_E |
|------|------------:|---------:|---------------:|
| `app/src/main/kotlin/com/jcraw/app/handlers/SocialHandlers.kt` | 3491 | 338 | 3491 |
| `client/src/main/kotlin/com/jcraw/mud/client/handlers/ClientSocialHandlers.kt` | 2879 | 247 | 2879 |
| `app/src/main/kotlin/com/jcraw/app/handlers/TradeHandlers.kt` | 2545 | 242 | 2545 |
| `app/src/main/kotlin/com/jcraw/app/handlers/TreasureRoomHandlers.kt` | 3125 | 275 | 3125 |
| `client/src/main/kotlin/com/jcraw/mud/client/handlers/ClientTreasureRoomHandlers.kt` | 3274 | 260 | 3274 |

## Acceptance
- [ ] Behavior-preserving extract of `SocialHandlers.kt`, `ClientSocialHandlers.kt`, `TradeHandlers.kt`, `TreasureRoomHandlers.kt`, `ClientTreasureRoomHandlers.kt` (pure moves / thin public entrypoints; no feature work)
- [ ] Console+GUI **parity** where app/client pairs exist in this family
- [ ] `./tools/verify_mud.sh --core` exit 0
- [ ] Remeasure with `check_token_budget_kt.py --files <touched>` then **lower or remove** overrides for reduced hosts (never raise; no new/Added override)
- [ ] Retarget remaining override `ticket` from `MUD-034` → `MUD-034l` when still needed
- [ ] New extracted `.kt` files meet global E (no override grandfather)
- [ ] No unauthorized `src/test/**` edits unless explicitly scoped + `MUD_ALLOW_TEST_CHANGES=1 ./tools/test_lock.sh --write`

## Non-goals
- Raising override caps
- Mass detekt baseline regen · PIT 80% (MUD-035) · MUD-036–038
- Splitting hosts outside this family
- Features / behavior changes

## Notes
- Umbrella: `issues/MUD-034-god-file-split-program.md` · plan `plans/2026-08-12-ai-mud-MUD-034-god-file-split-program.md`
- Ranked inventory: `tmp/workers/MUD-034/RANKED_GODS.md`
- Extract patterns: pure apply objects (MUD-019/023 style); mock LLM; KISS; files under ~1000 lines
- Serial one live builder per tree

## Builder
- session: _(fill when spawned)_
- brief: plan under `plans/` if substantial → Astra/Jason APPROVED → fresh impl

## Resolution

## Drain note
- 2026-08-12 08:24 MST clear-backlog: Turn 1 PLAN spawned (grok pid **1216892**). Prior **MUD-034k** done.


## Plan
- Path: `plans/2026-08-12-ai-mud-MUD-034l-social-trade-treasure-split.md` (mirror `tmp/workers/MUD-034l/PLAN.md`)
- Phase: **implementing** — **APPROVED by Astra 2026-08-12 08:34 MST** → fresh impl session
- Approach: pure-move social/trade/treasure split (Social+ClientSocial pair, Treasure pair, Trade app-only); thin facades; leave client social stubs; do not touch ClientTradeHandlers; remeasure lower/remove/retarget overrides to MUD-034l only; no features; `--core` green
- next: **APPROVED by Astra 2026-08-12 08:34 MST** → fresh IMPL live (do not resume plan session)
