# ai-mud overnight / Wave F handoff

**Updated:** 2026-08-11 12:21 MST
**Live:** MUD-019 implementing (fresh after Astra approve) · cron `0047039f`
**Agent queue:** MUD-019 → MUD-020 → MUD-021 (serial)
**Human-gated left:** MUD-007 playtest · MUD-009 Jason git
**Cron:** AI MUD Wave F re-drain (20m) — plan_review→approve+impl; on done→push; next ticket; stop after 021 done+pushed
**No time rush**

## Wave F goals
1. **MUD-019** floor-item V2 inventory parity
2. **MUD-020** quarantine slice 2 (SkillDefinitions/Classifier/DungeonInit ×8)
3. **MUD-021** quarantine slice 3 (Lore/WG/Death/TreasurePlacer ×4)

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
