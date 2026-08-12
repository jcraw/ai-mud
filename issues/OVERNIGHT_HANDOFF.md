# ai-mud overnight / Wave G handoff

**Updated:** 2026-08-11 (MUD-025 done — modernization program complete)
**Live:** none
**Agent queue:** empty (Wave G complete; no harness backlog)
**Human-gated left:** **none** (harness posture — no Jason playtest blockers; optional product play later)
**Cron:** AI MUD Wave G re-drain (`cd5f9827`) — **disarm** after MUD-025 allowlisted push (Astra)
**Posture:** Jason 2026-08-11 — quality gates / unit tests / modern harness only; product play later

## Wave G goals
1. **MUD-022** SkillManager quarantine ×8 → 0 — **DONE+PUSHED** `2b265ca`
2. **MUD-023** drop → V2 inventory parity — **DONE+PUSHED** `85a1af1` (bookkeep `3a60b38`)
3. **MUD-024** V1 inventory/equip production write purge — **DONE+PUSHED** `54bcce4`
4. **MUD-025** docs/board closeout quarantine 0 — **DONE** (push = Astra; plan `plans/2026-08-11-ai-mud-MUD-025-modernization-closeout.md`)

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
5. 022–025 all done+pushed → disarm cron; Live=none ← **at 025 done; disarm after push**
