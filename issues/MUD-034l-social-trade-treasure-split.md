---
id: MUD-034l
area: engine
title: Split social/trade/treasure handlers (Wave Q3)
status: done
priority: med
created: 2026-08-12
updated: 2026-08-12
source: jason
labels: [quality-gates, wave-q, refactor, god-file-split]
assignee: "grok"
worker: "grok"
phase: done
agent_eligible: true
eligibility: agent_eligible
depends_on: [MUD-034, MUD-031]
verify: "./tools/verify_mud.sh --core"
plan: plans/2026-08-12-ai-mud-MUD-034l-social-trade-treasure-split.md
worker_out_dir: tmp/workers/MUD-034l
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
- [x] Behavior-preserving extract of `SocialHandlers.kt`, `ClientSocialHandlers.kt`, `TradeHandlers.kt`, `TreasureRoomHandlers.kt`, `ClientTreasureRoomHandlers.kt` (pure moves / thin public entrypoints; no feature work)
- [x] Console+GUI **parity** where app/client pairs exist in this family
- [x] `./tools/verify_mud.sh --core` exit 0
- [x] Remeasure with `check_token_budget_kt.py --files <touched>` then **lower or remove** overrides for reduced hosts (never raise; no new/Added override)
- [x] Retarget remaining override `ticket` from `MUD-034` → `MUD-034l` when still needed _(N/A — all 5 host overrides removed)_
- [x] New extracted `.kt` files meet global E (no override grandfather)
- [x] No unauthorized `src/test/**` edits unless explicitly scoped + `MUD_ALLOW_TEST_CHANGES=1 ./tools/test_lock.sh --write`

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
- Done 2026-08-12 by grok (fresh IMPL after Astra APPROVED plan).
- Pure-move social/trade/treasure: SocialNpcResolve+Dialogue+Disposition · ClientSocialNpcResolve+Dialogue · Treasure/ClientTreasure PedestalSupport+Take/Return/Examine · TradeMerchantSupport+Buy/Sell/ListStock · thin hosts.
- Hosts: **3491→311** / **2879→587** / **2545→208** / **3125→219** / **3274→240**.
- All 5 host overrides **removed** (under global E; never raised; no Added override).
- Client social stubs + public `isQuestion` kept. `ClientTradeHandlers` untouched (gap in CLOSEOUT).
- `./tools/verify_mud.sh --core` PASS · dod-summary `tmp/dod-summary.json` · closeout `tmp/workers/MUD-034l/CLOSEOUT.md`.
- No `src/test/**` · no features · no 034m/n · no git commit.

## Drain note
- 2026-08-12 08:24 MST clear-backlog: Turn 1 PLAN spawned (grok pid **1216892**). Prior **MUD-034k** done.

## Plan
- Path: `plans/2026-08-12-ai-mud-MUD-034l-social-trade-treasure-split.md` (mirror `tmp/workers/MUD-034l/PLAN.md`)
- **APPROVED by Astra 2026-08-12 08:34 MST** → fresh IMPL session (do not resume plan session)
- Scope OK: pure-move social/trade/treasure (5 hosts) · Social+Treasure parity lockstep · Trade app-only · override remove/lower only · `--core` · no features / no 034m/n parallel
