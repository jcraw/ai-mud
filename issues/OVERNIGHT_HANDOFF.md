# ai-mud overnight / Wave G handoff

**Updated:** 2026-08-11 (MUD-027 DONE — dod-summary v2; Wave G still complete)
**Live:** none
**Agent queue:** Wave Q — 026–027 done; next **MUD-028** (token report) → 029→038
**Human-gated left:** **none** for harness; product play still later
**Cron:** Wave Q re-drain `fb01f053` every 20m — **ARMED**
**Posture:** Jason 2026-08-11 night — accepted `docs/AGENT_QUALITY_GATES_DESIGN.md` (token-first, hard-on-touched, PIT 80% after splits)
**Design:** token ceilings + anti-gaming; ratchet beyond detekt baseline
**MUD-027 closeout:** `tmp/workers/MUD-027/CLOSEOUT.md` · schema v2 + empty findings[] · `--core` PASS

## Wave G goals
1. **MUD-022** SkillManager quarantine ×8 → 0 — **DONE+PUSHED** `2b265ca`
2. **MUD-023** drop → V2 inventory parity — **DONE+PUSHED** `85a1af1` (bookkeep `3a60b38`)
3. **MUD-024** V1 inventory/equip production write purge — **DONE+PUSHED** `54bcce4`
4. **MUD-025** docs/board closeout quarantine 0 — **DONE+PUSHED** `6b9e0fb` (plan `plans/2026-08-11-ai-mud-MUD-025-modernization-closeout.md`)

**Program complete.** Status one-pager: `docs/MODERNIZATION_STATUS.md`.

## Push allowlist (after each done)
- Include: ticket-owned product/docs, AGENTS, issues/, plans/, docs/, tools/, config/, test-lock
- Exclude: secrets, local.properties, tmp/, logs
- master → origin · **no force** · record `issues/PUSHED.md`

## Drain tick
1. Live pid → wait
2. plan_review → APPROVED + fresh IMPL
3. done sans PUSHED row → allowlisted push
4. next open Wave G → PLAN
5. 022–025 all done+pushed → disarm cron; Live=none ← **COMPLETE; cron disarmed**
