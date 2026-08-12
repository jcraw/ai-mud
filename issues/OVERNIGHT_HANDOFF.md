# ai-mud overnight / Wave G handoff

**Updated:** 2026-08-11 17:27 MST
**Live:** MUD-024 planning (Grok)
**Agent queue:** MUD-024 → 025 (serial)
**Human-gated left:** **none** (harness posture — no Jason playtest blockers)
**Cron:** AI MUD Wave G re-drain (`cd5f9827`) every 20m
**Posture:** Jason 2026-08-11 — quality gates / unit tests / modern harness only; product play later

## Wave G goals
1. **MUD-022** SkillManager quarantine ×8 → 0 — **DONE+PUSHED** `2b265ca`
2. **MUD-023** drop → V2 inventory parity — **DONE+PUSHED** `85a1af1` (bookkeep `3a60b38`)
3. **MUD-024** V1 inventory/equip production write purge — **PLAN spawning**
4. **MUD-025** docs/board closeout quarantine 0

## Push allowlist (after each done)
- Include: ticket-owned product/docs, AGENTS, issues/, plans/, docs/, tools/, config/, test-lock
- Exclude: secrets, local.properties, tmp/, logs
- master → origin · **no force** · record `issues/PUSHED.md`

## Drain tick
1. Live pid → wait
2. plan_review → APPROVED + fresh IMPL
3. done sans PUSHED row → allowlisted push
4. next open Wave G → PLAN
5. 022–025 all done+pushed → disarm cron; Live=none
