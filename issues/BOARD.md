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

1. **Wave A — ops:** MUD-003 → 004 → 005 → 006 (serial) · done
2. **Wave B — truth + one fix:** MUD-008, MUD-007 (MUD-009 = Jason git calls) · done
3. **Wave C — gates 30d:** MUD-010, 011, 012 → 013 · done
4. **Wave D — gates 60d:** MUD-014, 015 · done
5. **Wave E — later:** MUD-016…018 · done
6. **Wave F — product + quarantine drip:** MUD-019 → 020 → 021 · done
7. **Wave G — finish modernization:** MUD-022 → 023 → 024 → 025 · done
8. **Wave Q — agent-native gates** (Jason 2026-08-11 · design `docs/AGENT_QUALITY_GATES_DESIGN.md`):
   - **Q0:** MUD-026 design lock
   - **Q1 serial:** MUD-027 → 028 → 029 → 030 (dod v2 · token report · touched · verify pilot)
   - **Q2 serial:** MUD-031 hard-on-touched → 032 no-live-LLM · 033 preflight (033∥ok after 030)
   - **Q3:** MUD-034 god-file split umbrella → children **034a–n** (open)
   - **Q4:** MUD-035 PIT raise · 036 dup · 037 parity contracts · 038 headless smoke
   - **Post-done:** allowlisted `git push origin master` after each (no force)
   - **Policy:** token-primary ceilings; hard-on-touched before new features; PIT 80% after splits; E-tier not core-blocking yet

## How agents use this

1. Read **`AGENTS.md`** (ops contract + DoD) and **`issues/ORCHESTRATION.md`** (drain mechanics).
2. Astra files/updates tickets; **Grok/Codex/Claude/Cursor implement** (plan→Astra/Jason approve→**fresh** impl when substantial).
3. Before work: ticket acceptance only (+ research under `docs/research/` / DIGEST-025 for gates when relevant).
4. One builder session per ticket; **serial one live builder per tree** (see ORCHESTRATION).
5. Verify: ticket `verify:` field (default `./tools/verify_mud.sh`).
6. No drive-by refactors; one problem per ticket.
7. Spikes/opinion/`needs_jason` → `human_gated`, not fake done.

## Open (backlog)

### Wave Q — agent-native quality gates (active)
Policy: `docs/AGENT_QUALITY_GATES_DESIGN.md` (accepted 2026-08-11).

#### Q0 — design lock
- _(MUD-026 done — design accepted + pointers)_

#### Q1 — feedback shape (serial)
- _(MUD-027 done — dod-summary v2 + findings[] pipe)_
- _(MUD-028 done — token/structure report-only checker)_
- _(MUD-029 done — touched-path `--files` / `--git-diff`)_
- _(MUD-030 done — verify wire + soft token pilot / `MUD_TOKEN_HARD` / `--token-hard`)_

#### Q2 — hard ratchet (serial after Q1)
- _(MUD-031 done — hard-on-touched default)_
- _(MUD-032 done — no live LLM in unit tests)_
- _(MUD-033 done — plan/brief token preflight)_

#### Q3 — split gods (umbrella done; children open)
- _(MUD-034 done — ranked list + 14 children 034a–n; tickets-only)_
- _(MUD-034a done — client facade pure-move extracts; residual override lowered)_
- _(MUD-034b done — GraphGenerator pure-move layout/MST/edges/typing; override removed)_
- _(MUD-034c done — IntentRecognizer pure-move Direction/Say/Trade/LLM/Fallback; host 10293→533; override removed; Intent residual override retargeted MUD-034c)_
- _(MUD-034d done — app runtime pure-move MU/Npc/Room/Quest/Death + GameServer Item/Nav/Social/Quest; MultiUserGame override removed; residual hosts retargeted MUD-034d)_
- _(MUD-034e done — skill/quest handlers parity; both host overrides removed)_
- _(MUD-034f done — testbot god split; residual FN overrides retargeted MUD-034f)_
- _(MUD-034g done — world gen cluster pure-move; residual overrides retargeted MUD-034g)_
- _(MUD-034h done — item handlers parity; both host overrides removed)_
- **MUD-034i** — movement handlers parity (`med`) · **in_progress** · grok · plan APPROVED · `tmp/workers/MUD-034i` · depends 034/031 · app+client Movement*
- **MUD-034j** — skill data/mgr split (`med`) · open · depends 034/031 · Perk/SkillDefinitions + SkillManager
- **MUD-034k** — combat surface split (`med`) · open · depends 034/031 · handlers + AttackResolver/Flee/AI/Narrator
- **MUD-034l** — social/trade/treasure split (`med`) · open · depends 034/031 · app+client pairs
- **MUD-034m** — memory + core split (`med`) · open · depends 034/031 · SQLite* / WorldDatabase / WorldState
- **MUD-034n** — misc reasoning split (`med`) · open · depends 034/031 · disposition/NPC/pickpocket/gens

#### Q4 — strength / product-adjacent
- **MUD-035** — PIT threshold raise toward 80% (`med`) · open · depends 034
- **MUD-036** — duplication gate handlers (`low`) · open · depends 031
- **MUD-037** — handler parity contracts (`med`) · open · depends 031
- **MUD-038** — headless command smoke (`low`) · open · depends 031 · not core-blocking

### Waves A–G
_(complete — harness modernization closed MUD-025)_

## Plan review

_(empty)_

## Scheduled / In progress

- **MUD-034i** — movement handlers parity (`med`) · in_progress / implementing · plan APPROVED by Astra · worker `tmp/workers/MUD-034i/` · fresh IMPL · hosts MovementHandlers + ClientMovementHandlers

## Blocked (awaiting Jason)

_(empty — harness posture: no Jason playtest blockers)_ 

## Recently done
- **MUD-034h** — item handlers parity (`med`) · done · pure-move Inventory(+format)/Take/DropGive/Equip/Consumable lockstep + app Loot multi-file · hosts **6253→366** / **4667→335** · both host overrides **removed** (under global E; never raised; no Added override) · CorpseHandlers + client loot stub left · consumable named `ItemConsumableHandlers` (no ItemUseHandlers clash) · no `src/test/**` · `--core` PASS · closeout `tmp/workers/MUD-034h/CLOSEOUT.md` · plan `plans/2026-08-12-ai-mud-MUD-034h-item-handlers-split.md`
- **MUD-034g** — world gen cluster split (`med`) · done · pure-move 7 hosts + 32 extracts (ER/MS/WG/EL/TG/HEP/DI) · hosts **7653→2063** / **6327→3162** / **3906→2308** / **3316→1385** / **3016→919** / **2894→1157** / **2597→1685** · ExitLinker+MobSpawner overrides **removed** · residual hosts **lower+retarget MUD-034g** (never raised; no Added override) · parity N/A · no app/client · no `src/test/**` · `--core` PASS · closeout `tmp/workers/MUD-034g/CLOSEOUT.md` · plan `plans/2026-08-12-ai-mud-MUD-034g-world-gen-cluster-split.md`
- **MUD-034f** — testbot god split (`med`) · done · pure-move Models/V3 engine/Runner/ValPrompts/CodeVal/InputGen · hosts **9131→1727** / **7761→741** / **5412→1556** / **4420→997** / **3616→1330** / **2636→940** · residual FN overrides **lower+retarget MUD-034f** (never raised; no Added override) · no app/client · no `src/test/**` · `--core` PASS · closeout `tmp/workers/MUD-034f/CLOSEOUT.md` · plan `plans/2026-08-12-ai-mud-MUD-034f-testbot-god-split.md`
- **MUD-034e** — skill/quest handlers parity (`med`) · done · pure-move Meta/Quest/Train/SkillUse(+heal/infer)/Craft(+results)/Interact(+harvest/fountain)/app Check · hosts **9827→539** / **9517→658** · both host overrides **removed** (under global E; never raised; no Added override) · client Craft still stubbed · no `src/test/**` · `--core` PASS · closeout `tmp/workers/MUD-034e/CLOSEOUT.md` · plan `plans/2026-08-12-ai-mud-MUD-034e-skill-quest-handlers-split.md`
- **MUD-034d** — app runtime split (`med`) · done · pure-move: MultiUserFallbacks · MudGameNpc* · MudGameRoomDescribe · MudGameQuestSupport · MudGameDeathRespawn · GameServer Item/Nav/Social/Quest · hosts **10257→4635** / **10127→2940** / **2730→1704** · MultiUserGame override **removed** · residual hosts **retargeted MUD-034d** (lower-only; no Added override) · MU stubs/order preserved · no `src/test/**` · `--core` PASS · closeout `tmp/workers/MUD-034d/CLOSEOUT.md` · plan `plans/2026-08-12-ai-mud-MUD-034d-app-runtime-split.md`
- **MUD-034c** — intent recognizer split (`med`) · done · pure-move: Direction/Say/Trade/LlmPrompt*/LlmJsonMap*/LlmParse/Fallback* · host **10293→533** · IntentRecognizer override **removed** · Intent residual override **retargeted MUD-034c** (tok_E 2707; multi-file deferred) · public `parseIntent` preserved · no `src/test/**` · `--core` PASS · closeout `tmp/workers/MUD-034c/CLOSEOUT.md` · plan `plans/2026-08-12-ai-mud-MUD-034c-intent-recognizer-split.md`

- **MUD-034b** — GraphGenerator layout/MST split (`med`) · done · pure-move: `GraphLayoutNodes` / `GraphMst` / `GraphEdgeDirections` + direction geometry/assign/fix/unique fragments / `GraphNodeTyping` · host **11932→561** · override **removed** (under global E; never raised; no Added override) · public `generate` pipeline + RNG order preserved · no `src/test/**` · `--core` PASS · closeout `tmp/workers/MUD-034b/CLOSEOUT.md` · plan `plans/2026-08-12-ai-mud-MUD-034b-graph-generator-split.md`
- **MUD-034a** — client facade split (`med`) · done · pure-move extracts: UI `StatusBar`/`GameLogWindow`/`GameInputField` + `ClientItemTemplateCache`/`ClientSpaceContent`/`ClientSpaceDescribe`/`ClientFrontierExpansion`/`ClientQuestDeathSupport`/`ClientNpcCombat`/`ClientNpcAttack` · hosts **14209→6282** / **3033→590** · overrides lower+retarget `MUD-034a` only (no raise; no Added override) · intent router stayed on facade (global FN_E) · TICKET_RE allows `MUD-034a` · Compose `FunctionNaming` ignoreAnnotated · `--core` PASS · closeout `tmp/workers/MUD-034a/CLOSEOUT.md` · plan `plans/2026-08-12-ai-mud-MUD-034a-client-facade-split.md`
- **MUD-034** — god-file split umbrella (`med`) · done · ranked 55 override hosts → `tmp/workers/MUD-034/RANKED_GODS.md` + full JSON · 14 children **034a–n** filed (letter suffix; 035–038 free) · tickets-only close (no product god extract) · no override raise · BOARD Q3 lists children · TOKEN_BUDGET_KT pointer · `--core` PASS · closeout `tmp/workers/MUD-034/CLOSEOUT.md` · plan `plans/2026-08-12-ai-mud-MUD-034-god-file-split-program.md`
- **MUD-033** — plan/brief token preflight (`med`) · done · `tools/quality/check_builder_preflight.py` D1/D2 (plan 2k/3.5k, brief 1.2k/2k, ceil chars/4) · exit 0/1/2 + `--allow-warn` · optional `./tools/verify_mud.sh --preflight <path>` (warn→pass+note; fail→verify fail; not on default lanes) · `docs/BUILDER_PREFLIGHT.md` + ORCH before APPROVED + DESIGN D1/D2 live · no product `*.kt` · `--core` PASS · closeout `tmp/workers/MUD-033/CLOSEOUT.md` · plan `plans/2026-08-12-ai-mud-MUD-033-builder-preflight-token.md`
- **MUD-032** — no live LLM in unit tests (`med`) · done · static `rg` gate `tools/quality/check_no_live_llm_unit.sh` · forbid `OpenAIClient(` / `OPENAI_API_KEY` / `openai.api.key` under `*/src/test/**` · hard-exclude `testbot/**` · empty allowlist · `no_live_llm_unit` hard on default/fast/core/full/pitest · skip quarantine · findings `LIVE_LLM_*` · `docs/NO_LIVE_LLM_UNIT.md` + AGENTS + DESIGN B2 + DOD_SUMMARY · no product `*.kt` · `--core` PASS · closeout `tmp/workers/MUD-032/CLOSEOUT.md` · plan `plans/2026-08-12-ai-mud-MUD-032-no-live-llm-unit-tests.md`
- **MUD-031** — token/structure **hard-on-touched** default (`high`) · done · invert 030: hard default on default/fast/core/full scoped git-diff `*_E` · soft opt-out `MUD_TOKEN_SOFT`/`--token-soft` · 55 overrides `ticket: MUD-034` + measured caps · new/Added ban · override E `metric > limit` · AGENTS+DESIGN+TOKEN_BUDGET_KT · no product `*.kt` · `--core` PASS · closeout `tmp/workers/MUD-031/CLOSEOUT.md` · plan `plans/2026-08-12-ai-mud-MUD-031-token-hard-on-touched.md`
- **MUD-030** — verify wire token pilot (`high`) · done · `run_token_budget` on default/fast/core/full (soft report-only → `findings[]` + `gates.token_budget`) · hard via `MUD_TOKEN_HARD=1` / `--token-hard` on scoped git-diff `*_E` only · quarantine+pitest skip · `docs/TOKEN_BUDGET_KT.md` + DOD_SUMMARY + AGENTS one-liner · no product `*.kt` · `--core` PASS · closeout `tmp/workers/MUD-030/CLOSEOUT.md` · plan `plans/2026-08-12-ai-mud-MUD-030-verify-wire-token-pilot.md`
- **MUD-029** — touched-path quality mode (`high`) · done · `--files` / `--git-diff` / `--git-base` (default `origin/master`) on `check_token_budget_kt.py` · union when both · full-repo when neither · prod `src/main/**/*.kt` only · empty touch exit 0 · report-only · `docs/TOKEN_BUDGET_KT.md` · no verify wire (030) · no hard-on-touched (031) · no product `*.kt` · `--core` PASS · closeout `tmp/workers/MUD-029/CLOSEOUT.md` · plan `plans/2026-08-11-ai-mud-MUD-029-touched-path-quality-mode.md`
- **MUD-028** — token/structure report-only checker (`high`) · done · `tools/quality/check_token_budget_kt.py` + `config/quality/token_budget_kt.json` (file tok W2000/E2500; fn 200/250; structure A7) · report-only exit 0 · 55 override_candidates on tree · `docs/TOKEN_BUDGET_KT.md` · DESIGN A6/A7 + §7.1 pointer · no verify hard-wire · no product `*.kt` · `--core` PASS · closeout `tmp/workers/MUD-028/CLOSEOUT.md` · plan `plans/2026-08-11-ai-mud-MUD-028-token-budget-kt-report.md`
- **MUD-027** — dod-summary v2 + finding codes (`high`) · done · `schema_version: 2` + always `findings[]` · `config/quality/dod_summary.schema.json` · `validate_dod_summary` (python3) · `docs/DOD_SUMMARY.md` · AGENTS Verification v2 clause · DESIGN §5 schema pointer · `--core` PASS · closeout `tmp/workers/MUD-027/CLOSEOUT.md` · plan `plans/2026-08-11-ai-mud-MUD-027-dod-summary-v2-findings.md`
- **MUD-026** — agent quality gates design lock (`high`) · done · DESIGN accepted · BOARD Wave Q · AGENTS pointer · OVERNIGHT note · `--core` PASS · Astra DIY
- **MUD-025** — modernization program closeout (`med`) · done · docs/board only · quarantine **0** truth · `TESTING.md` 644 green · `docs/MODERNIZATION_STATUS.md` one-pager · Wave G complete · Open empty (harness) · OVERNIGHT Live=none · pushed `6b9e0fb` · `--core` exit 0 · closeout `tmp/workers/MUD-025/CLOSEOUT.md` · plan `plans/2026-08-11-ai-mud-MUD-025-modernization-closeout.md`
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
