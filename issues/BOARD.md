# AI MUD Issue Board

**Task bus for humans + Astra + builders.** One open ticket ≈ one builder thread.

Say: **save issue for ai-mud: \<text\>** · **list ai-mud issues** · **drain ai-mud** / **spare capacity → mud**

Statuses: `open` → `scheduled` → `in_progress` → `done` (or `blocked` / `wontfix` / `plan_review`)

Prefix: **MUD-NNN**. Repo: `/run/media/j/M2MegaStore/Code/claude-code/ai-mud` · GitHub: `jcraw/ai-mud`

## Posture (Jason 2026-08-09)

- **Background / low-stakes** ops experiment — not a primary product push
- Run on **spare agent capacity** only (don’t compete with jam/job/money)
- Goal: autonomous drains via Astra + builders; eventually play with friends; nice-to-have fan-facing fixes
- Do **everything** on the backlog over time — slow drip, not rush

## Drain order (spare capacity)

1. **Wave A — ops:** MUD-003 → 004 → 005 → 006 (serial)
2. **Wave B — truth + one fix:** MUD-008, MUD-007 (MUD-009 = Jason git calls)
3. **Wave C — gates 30d:** MUD-010, 011, 012 → 013
4. **Wave D — gates 60d:** MUD-014 (done), 015 (done)
5. **Wave E — later:** MUD-016 CI (done), 017 quarantine slice-1 (done; residual 20 → follow-up), 018 CLAUDE deprecation (done)
6. **Wave F — product + quarantine drip:** MUD-019 → 020 → 021 (serial; push GitHub after each done)

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
- **MUD-007** — treasure-room inventory GUI (`high`) · deps 004 · **impl done, awaiting Jason playtest** (not player-done)

### Wave C — quality gates (DIGEST-025 ~30d)
_(empty — Wave C complete: MUD-010…013 done)_

### Wave D — quality gates (~60d)
_(empty — Wave D complete: MUD-014, 015 done)_

### Wave E — later
_(empty — Wave E complete: MUD-016…018 done)_

### Wave F — product + quarantine drip (Jason 2026-08-11)
- **MUD-021** — quarantine slice 3 lore/death/placer ×4 (`med`) · deps 020 · after 020
- **Post-done:** allowlisted `git push origin master` after each ticket closeout (no force)

## Plan review

_(empty)_

## Scheduled / In progress

- **MUD-021** — quarantine slice 3 · `open` · after 020 (020 done)

## Blocked (awaiting Jason)

- **MUD-007** — treasure-room inventory GUI · **impl done, awaiting playtest** · verify green · plan `plans/2026-08-10-ai-mud-MUD-007-treasure-inventory-gui.md` · `needs_jason: playtest` · closeout `tmp/workers/MUD-007/CLOSEOUT.md`
- **MUD-009** — Git hygiene dirty testbot/logs/PR policy (`med`) · `needs_jason: action`

## Recently done

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
