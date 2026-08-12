# Modernization program status

**Status:** program complete (Wave A–G) · **Date:** 2026-08-11  
**Board:** [`issues/BOARD.md`](../issues/BOARD.md) · **Pushes:** [`issues/PUSHED.md`](../issues/PUSHED.md)  
**Closeout ticket:** MUD-025

Harness-first modernization is **done**. Quality gates, quarantine clear, and V2 inventory Success writes are in place. This is **not** a claim that the product is playtest-ready.

## Gates on

| Gate | Entry | Notes |
|------|-------|--------|
| Verify default / fast | `./tools/verify_mud.sh` | compile smoke + detekt + Konsist + test-lock |
| Verify core | `./tools/verify_mud.sh --core` | `:core`/`:perception`/`:memory`/`:reasoning` + gates |
| Verify full | `./tools/verify_mud.sh --full` | stable green set; PIT skipped |
| PIT | `./tools/verify_mud.sh --pitest` | pure modules only; **soft** 60% |
| Quarantine lane | `./tools/verify_mud.sh --quarantine` | empty set → exit 0 |
| Detekt | hard on default/core/full/pitest | baseline soft; mass regen Jason-only |
| Konsist | hard arch on same lanes | see `docs/KONSIST.md` |
| Test-lock | hard on same lanes | see `docs/TEST_LOCK.md` |
| CI | `.github/workflows/verify.yml` | job `core` → verify `--core` |

Ops contract: **`AGENTS.md`**. DoD summary: `tmp/dod-summary.json`.

## Quarantine

Active quarantine **0** (post-MUD-022). Full history and cleared list: [`docs/TEST_QUARANTINE.md`](TEST_QUARANTINE.md).

Repair path: MUD-008 baseline → MUD-017/020/021 slices → MUD-022 SkillManager clear.

## Product inventory path (Wave F/G)

V2 `InventoryComponent` Success writes on production paths:

| Path | Ticket |
|------|--------|
| Floor take | MUD-019 |
| Floor drop | MUD-023 |
| Give / equip / use / GUI buy | MUD-024 |
| Treasure take (harness) | MUD-007 |

See also [`KNOWN_ISSUES.md`](../KNOWN_ISSUES.md).

## Residual human-only / optional (not gates)

- **PlayerState V1 fields** — optional hard-delete of deprecated inventory/equip fields (display/read fallbacks OK today)
- **PIT soft → hard** — day-one soft; hard threshold out of program scope
- **Detekt baseline burn-down** — Jason/explicit only
- **MUD-007 GUI/console playtest** — Jason product phase; not a harness drain gate

## Posture

- **Harness-first** — unit/contract tests, verify lanes, board truth
- Do **not** block spare-capacity drains on playtest opinion unless the ticket is an explicit design spike
- One problem per ticket; serial one live builder per tree

## Verify (closeout)

```bash
./tools/verify_mud.sh --core
```
