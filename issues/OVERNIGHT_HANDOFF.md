# ai-mud overnight / Wave F handoff

**Updated:** 2026-08-11 14:22 MST
**Live:** none
**Agent queue:** empty (Wave F complete)
**Human-gated left:** MUD-007 playtest · MUD-009 Jason git · SkillManager ×8 (L1/L2 opinion)
**Cron:** AI MUD Wave F re-drain — **DISARMED** (`0047039f`) after 019+020+021 done+pushed
**No time rush**

## Wave F goals
1. **MUD-019** floor-item V2 inventory parity — **done + pushed** `d6446aa`
2. **MUD-020** quarantine slice 2 (SkillDefinitions/Classifier/DungeonInit ×8) — **done + pushed** `e68ff12` (bookkeep `49e861c`)
3. **MUD-021** quarantine slice 3 (Lore/WG/Death/TreasurePlacer ×4) — **done + pushed** `4eef71e`

## Push allowlist (after each done)
- Include: ticket-owned product/docs, `AGENTS.md`, `issues/`, `plans/`, `docs/`, `tools/`, `.github/`, `config/`, gradle portable bits as ticket-scoped
- Exclude: secrets (`local.properties`, keys, dbs), dirty `testbot/` without Jason (MUD-009), `tmp/`, logs
- Branch: `master` · remote `origin` · **no force**
- Record SHA in `issues/PUSHED.md` (append wave F rows)

## Drain tick checklist
1. Live pid in `tmp/workers/*/grok.pid` or `*.impl.pid`? → wait
2. `plan_review`? → Astra common-sense APPROVED stamp → fresh IMPL
3. Ticket `done` and not yet pushed this wave? → commit allowlist + push + note PUSHED.md
4. Else next open Wave F ticket → PLAN spawn
5. All three done+pushed → disarm cron; leave human_gated

## Wave F closeout (2026-08-11 14:22 MST)
- Serial drain complete; quarantine residual **8** = SkillManager only (Jason-gated)
- No further Wave F auto-spawn
