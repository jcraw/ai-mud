# AI MUD Issue Board

**Task bus for humans + Astra + builders.** One open ticket ≈ one builder thread.

Say: **save issue for ai-mud: \<text\>** · **list ai-mud issues** · **drain ai-mud** / **spare capacity → mud**

Statuses: `open` → `scheduled` → `in_progress` → `done` (or `blocked` / `wontfix` / `plan_review`)

Prefix: **MUD-NNN**. Repo: `/run/media/j/M2MegaStore/Code/claude-code/ai-mud` · GitHub: `jcraw/ai-mud`

## Posture (Jason 2026-08-11)

- **Harness-first modernization** — quality gates, unit/contract tests, AGENTS/board/verify/CI — so later product work is easy
- **Not playtest-ready** — do **not** block tickets on Jason playtest/opinion (except explicit design spikes)
- Spare agent capacity OK; autonomous drains; slow drip OK; no rush
- Product play / friends multiplayer = later phase, not this board’s gate

## Drain order (spare capacity)

1. **Wave A — ops:** MUD-003 → 004 → 005 → 006 (serial)
2. **Wave B — truth + one fix:** MUD-008, MUD-007 (MUD-009 = Jason git calls)
3. **Wave C — gates 30d:** MUD-010, 011, 012 → 013
4. **Wave D — gates 60d:** MUD-014 (done), 015 (done)
5. **Wave E — later:** MUD-016 CI (done), 017 quarantine slice-1 (done; residual 20 → follow-up), 018 CLAUDE deprecation (done)
6. **Wave F — product + quarantine drip:** MUD-019 → 020 → 021 (serial; push GitHub after each done)
7. **Wave G — finish modernization:** MUD-022 → 023 → 024 → 025 (serial; push after each done)

## How agents use this

1. Read **`AGENTS.md`** (ops contract + DoD) and **`issues/ORCHESTRATION.md`** (drain mechanics).
2. Astra files/updates tickets; **Grok/Codex/Claude/Cursor implement** (plan→Astra/Jason approve→**fresh** impl when substantial).
3. Before work: ticket acceptance only (+ research under `docs/research/` / DIGEST-025 for gates when relevant).
4. One builder session per ticket; **serial one live builder per tree** (see ORCHESTRATION).
5. Verify: ticket `verify:` field (default `./tools/verify_mud.sh`).
6. No drive-by refactors; one problem per ticket.
7. Spikes/opinion/`needs_jason` → `human_gated`, not fake done.

## Open (backlog)

### Wave A — agent ops
_(empty — Wave A complete)_

### Wave B — health + fan-facing
_(empty — MUD-007 done on automated/harness criteria; playtest not a gate)_

### Wave C — quality gates (DIGEST-025 ~30d)
_(empty — Wave C complete: MUD-010…013 done)_

### Wave D — quality gates (~60d)
_(empty — Wave D complete: MUD-014, 015 done)_

### Wave E — later
_(empty — Wave E complete: MUD-016…018 done)_

### Wave F — product + quarantine drip (Jason 2026-08-11)
- **Post-done:** allowlisted `git push origin master` after each ticket closeout (no force)
- _(Wave F complete — MUD-019…021 done)_

### Wave G — finish modernization (Jason 2026-08-11)
- **Post-done:** allowlisted `git push origin master` after each (no force)
- _(Wave G complete — MUD-022…025 done; 022–024 pushed through `54bcce4`; 025 push = Astra)_

## Plan review

_(empty)_

## Scheduled / In progress

_(empty)_

## Blocked (awaiting Jason)

_(empty — harness posture: no Jason playtest blockers)_ 

## Recently done

- **MUD-025** — modernization program closeout (`med`) · done · docs/board only · quarantine **0** truth · `TESTING.md` 644 green · `docs/MODERNIZATION_STATUS.md` one-pager · Wave G complete · Open empty (harness) · OVERNIGHT Live=none · `--core` exit 0 · closeout `tmp/workers/MUD-025/CLOSEOUT.md` · plan `plans/2026-08-11-ai-mud-MUD-025-modernization-closeout.md`
- **MUD-024** — V1 inventory/equip production write purge (`med`) · done · pure `GiveItemApply` + `UseConsumableApply`; equip V2 all surfaces (GameServer ported; legacy branches deleted); GUI buy → `addItemInstance`; grep gate 0 V1 mutators in app/client/reasoning main; exceptions core defs + testbot; KNOWN_ISSUES give/V1 writes fixed; field delete residual; `--core` exit 0 · test-lock 114 · closeout `tmp/workers/MUD-024/CLOSEOUT.md` · plan `plans/2026-08-11-ai-mud-MUD-024-v1-inventory-write-purge.md`
- **MUD-023** — drop → V2 inventory parity (`high`) · done · pure `FloorItemDropApply` + thin console/GUI/GameServer `handleDrop`; V2 `removeItem` + floor entity props + `itemsDropped`; no V1-only Success; contract tests ×5 (incl. equip clear + drop→take round-trip); KNOWN_ISSUES drop residual fixed (give/V1 purge → MUD-024); `--core` exit 0 · test-lock 113 · closeout `tmp/workers/MUD-023/CLOSEOUT.md` · plan `plans/2026-08-11-ai-mud-MUD-023-drop-v2-inventory-parity.md`
- **MUD-022** — SkillManager quarantine ×8 clear (`high`) · done · 8→**0** tags · C1 prod `skillXpMultiplier` default **1.0f** · C2 locked `grantXp` re-contract auto-unlock · C3 hard L1 unlock + defensive isolation (`enableLuckyProgression=false`) · residual quarantine **0** · `--core` exit 0 · `--quarantine` exit 0 (empty set, quarantine_count **0**) · test-lock 112 · closeout `tmp/workers/MUD-022/CLOSEOUT.md` · plan `plans/2026-08-11-ai-mud-MUD-022-skillmanager-quarantine-clear.md`
- **MUD-007** — treasure inventory GUI · **done** (harness) · contract tests + shared apply + verify green · playtest deferred to future product phase (not blocking)
- **MUD-009** — git hygiene · done · testbot SkillProgression parsers committed+pushed `74c343e` (Jason finish-mod authority)
- **MUD-021** — quarantine slice 3 lore/death/placer ×4 (`med`) · done · 12→**8** tags · C4 prod `isTreasureEligible` (Hub/Boss/Frontier) candidates+fallback · C1/C2 harness parent-keyword mock lore · C3 DeathHandler V3 dual-write gold `>=1` (variance 0.8–1.2) · residual SkillManager×8 only (cleared MUD-022) · `--core` exit 0 · `--quarantine` hard-fail quarantine_count **8** · test-lock 112 · closeout `tmp/workers/MUD-021/CLOSEOUT.md` · plan `plans/2026-08-11-ai-mud-MUD-021-quarantine-slice3-lore-death-treasure.md`
- **MUD-020** — quarantine slice 2 catalog/hierarchy ×8 (`med`) · done · 20→**12** tags · C1 combat size 11+membership · C2 prod `hasSkill` filter fallback+LLM parse · C3 DeepDungeon 4 REGIONs [1,5,12,18] children 4 · residual SkillManager×8 + MUD-021 cluster · `--core` exit 0 · `--quarantine` hard-fail quarantine_count **12** · test-lock 112 · closeout `tmp/workers/MUD-020/CLOSEOUT.md` · plan `plans/2026-08-11-ai-mud-MUD-020-quarantine-slice2-catalog-hierarchy.md`
- **MUD-019** — floor-item take → V2 InventoryComponent parity (`high`) · done · pure `FloorItemTakeApply` + thin console/GUI/GameServer take handlers; contract tests (templateId Success, overweight Failure, space cleared); no `addToInventory` on Success; KNOWN_ISSUES residual floor fixed/narrowed (drop/legacy residual remain); `--core` exit 0 · test-lock 112 · closeout `tmp/workers/MUD-019/CLOSEOUT.md` · plan `plans/2026-08-11-ai-mud-MUD-019-floor-item-v2-inventory.md`
- **MUD-018** — CLAUDE/CODEX deprecation path (`low`) · done · docs-only: banners on CLAUDE+CODEX; CODEX collab → AGENTS ops SoT; README demotes CLAUDE primary SoT; guidelines top note; AGENTS settings.local not-DoD line; settings.local untouched; CLAUDE body zero-diff · closeout `tmp/workers/MUD-018/CLOSEOUT.md` · plan `plans/2026-08-11-ai-mud-MUD-018-claude-codex-deprecation-path.md`
- **MUD-017** — clear `:reasoning` quarantine slice 1 (`low`) · done · 23→**20** tags · Capacity floor test + ThemeRegistry count(9)+magma `volcanic` prod · residual Skill*/Dungeon/Lore/Death/Treasure deferred · `--core` exit 0 · `--quarantine` hard-fail 20 OK · `tmp/dod-summary.json` quarantine_count 20 · closeout `tmp/workers/MUD-017/CLOSEOUT.md` · plan `plans/2026-08-11-ai-mud-MUD-017-clear-reasoning-quarantine.md`
- **MUD-016** — CI compile + scoped tests (`med`) · done · `.github/workflows/verify.yml` job `core` → `./tools/verify_mud.sh --core` on PR/push `master`/`main` · `contents: read` only · no secrets/OpenAI · quarantine excluded · stripped machine `org.gradle.java.home` from committed `gradle.properties` · README 1-line CI blurb · local verify `--core` exit 0 · first Actions run needs Jason push · closeout `tmp/workers/MUD-016/CLOSEOUT.md` · plan `plans/2026-08-11-ai-mud-MUD-016-ci-scoped-tests.md`
- **MUD-015** — Kotest PBT combat/graph hot paths (`med`) · done · kotest 5.9.1 property on `:core` only · G1–G4+S2 graph + C1–C3 combat HP · seed `15_015L` · `docs/PBT.md` · test-lock 111 files · verify `--core` exit 0 · closeout `tmp/workers/MUD-015/CLOSEOUT.md` · plan `plans/2026-08-11-ai-mud-MUD-015-kotest-pbt-hotpaths.md`
- **MUD-014** — PIT mutation pure modules (`med`) · done · `info.solidsoft.pitest` 1.19.0 on `:core`/`:perception`/`:memory` (STRONGER) · `--pitest` lane · soft 60% (min 9.1 day-one) · full skips (core ~130s) · `docs/PIT.md` · closeout `tmp/workers/MUD-014/CLOSEOUT.md` · plan `plans/2026-08-11-ai-mud-MUD-014-pit-mutation-pure-modules.md`
- **MUD-013** — fast verify + dod-summary.json (`med`) · done · `tools/verify_mud.sh` gates+durations → `tmp/dod-summary.json` · fast≡default · AGENTS N=3 + coverage≠done · closeout `tmp/workers/MUD-013/CLOSEOUT.md` · plan `plans/2026-08-10-ai-mud-MUD-013-fast-verify-dod-json.md`
- **MUD-012** — test-file lock (`high`) · done · `tools/test_lock.sh` + `tools/test-lock/manifest.sha256` (108 files) · hard check on default/fast/core/full · quarantine skips · `docs/TEST_LOCK.md` · plan `plans/2026-08-10-ai-mud-MUD-012-test-file-lock.md`
- **MUD-011** — Konsist architecture (`high`) · done · konsist 0.17.3 · `ModuleBoundaryTest` (real package roots; declared Gradle edges; zero allowlist) · verify hard arch on default/fast/core/full · `docs/KONSIST.md` · plan `plans/2026-08-10-ai-mud-MUD-011-konsist-architecture.md`
- **MUD-010** — Detekt + baseline (`high`) · done · detekt 1.23.8 · shared `config/detekt/baseline.xml` (1478 IDs) · verify hard detekt · `docs/DETEKT.md` · plan `plans/2026-08-10-ai-mud-MUD-010-detekt-baseline.md`
- **MUD-008** — test baseline + quarantine reasoning (`high`) · done · 23 `@Tag("quarantine")` · `docs/TEST_QUARANTINE.md` · verify lanes exclude/include · plan `plans/2026-08-10-ai-mud-MUD-008-test-baseline-quarantine.md`
- **MUD-006** — plan/impl brief templates (`med`) · done · `issues/_templates/plan-brief.md` + `implement-brief.md` · ORCHESTRATION/AGENTS pointers · plan `plans/2026-08-10-ai-mud-MUD-006-brief-templates.md`
- **MUD-005** — board hygiene + short ORCHESTRATION (`med`) · done · template live fields · `issues/ORCHESTRATION.md` · BOARD pointer · plan `plans/2026-08-10-ai-mud-MUD-005-board-hygiene-orchestration.md`
- **MUD-004** — `tools/verify_mud.sh` lanes (`high`) · done · `./tools/verify_mud.sh` default verify · plan `plans/2026-08-10-ai-mud-MUD-004-verify-mud-sh.md`

- **MUD-003** — lean `AGENTS.md` (`high`) · root ops contract · plan `plans/2026-08-09-ai-mud-MUD-003-lean-agents-md.md`
- **MUD-001** — Spike agentic modernization · report `docs/research/2026-08-08-ai-mud-agentic-modernization-spike.md`
- **MUD-002** — Research Kotlin quality gates + unit tests · DIGEST-025 (draft)
