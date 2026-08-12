# MUD-034 Plan — God-file split umbrella (Wave Q3)

**Status: APPROVED by Astra 2026-08-12 02:33 MST** · common-sense OK

**APPROVED by Astra**
- **When:** 2026-08-12 ~02:33 America/Phoenix
- **Verdict:** Approve as-is. Umbrella = ranked list + child tickets `MUD-034a`–`n` (letter suffix; leave 035–038 for Q4). Prefer **tickets-only close** — no product god extract in this umbrella unless micro-split is forced later. Overrides lower/remove only; never raise; new extracts must meet global E (no Added grandfather). Families + parity pairs look right. Fresh IMPL only.
- **Next:** Fresh IMPL session — do not resume plan session.

**was plan_review** · grok · depends MUD-031 (done) · verify: `./tools/verify_mud.sh --core`  
**Umbrella scope:** ranked list + child tickets; **prefer list+tickets-only close** (no mass product splits).

## 1. Goal / acceptance

| # | Acceptance | Deliverable |
|---|------------|-------------|
| 1 | Ranked list over file-token/LOC E | `tmp/workers/MUD-034/RANKED_GODS.md` from overrides + cheap remeasure |
| 2 | One child **per split family** | `issues/MUD-034a-…`–`034n` (letter suffix; **not** 035–038) |
| 3 | Child DoD | Behavior-preserving extract; tests green; **lower/remove** override; never raise |
| 4 | Umbrella close | **Prefer tickets-only.** First split only if tiny/safe — **default skip** |
| 5 | `--core` green | After any code/config touch; smoke even if docs-only |
| 6 | No drive-bys | No features / PIT 80% / detekt mass / 036–038 |

## 2. Inventory

**Sources:** `config/quality/token_budget_kt.json` `overrides` (**55**, all `ticket: MUD-034`); MUD-028 closeout 55 candidates; MUD-031 hard-on-touched + measured caps.  
**Global:** file tok W2000/E2500; LOC W700/E1100 (`docs/TOKEN_BUDGET_KT.md`).  
**Remeasure 2026-08-12 top8:** tokens match override `tokens.file.error`.

**Worst hosts (tok_E):**

| # | tok | path |
|--:|----:|------|
| 1 | 14209 | `client/.../EngineGameClient.kt` |
| 2 | 11932 | `reasoning/.../worldgen/GraphGenerator.kt` |
| 3 | 10293 | `perception/.../IntentRecognizer.kt` |
| 4 | 10257 | `app/.../MudGameEngine.kt` |
| 5 | 10127 | `app/.../GameServer.kt` |
| 6 | 9827 | `app/.../handlers/SkillQuestHandlers.kt` |
| 7 | 9517 | `client/.../handlers/ClientSkillQuestHandlers.kt` |
| 8 | 9131 | `testbot/.../InputGenerator.kt` |
| 9 | 7761 | `testbot/.../CodeValidationRules.kt` |
| 10 | 7653 | `reasoning/.../world/WorldGenerator.kt` |
| 11–55 | ≤7304 | skill defs, dungeon, item/move handlers, WorldState, combat/social, memory SQLite, … |

Bulk: reasoning-heavy, app/client handlers, testbot, memory, engines, perception, core.

## 3. Approach

**Strategy:** list + children first; **close without product extract** unless Astra forces a micro-split.

**IDs:** `MUD-034a`…`n` (one family ≠ one host unless singleton).

| ID | Family | Primary hosts | Note |
|----|--------|---------------|------|
| a | Client facade | `EngineGameClient` (+ later UI) | top god |
| b | Graph gen | `GraphGenerator` | layout/MST extract |
| c | Intent | `IntentRecognizer` (+ `Intent`) | by domain |
| d | App runtime | `MudGameEngine`, `GameServer`, `MultiUserGame` | careful multi-user |
| e | Skill/quest handlers | app+client pair | **parity** |
| f | Testbot | InputGen, validation, runner, V3 engine, models | lower product risk |
| g | World gen cluster | WorldGenerator, DungeonInit, town/exit/mob/hidden | subpackages |
| h | Item handlers | app+client | parity |
| i | Movement handlers | app+client | frontier/lazy-fill |
| j | Skill data/mgr | Perk/SkillDefinitions, SkillManager | data vs logic |
| k | Combat surface | CombatHandlers pairs + AttackResolver/Flee/AI/Narrator | pure first |
| l | Social/trade/treasure | app+client pairs | small; ≤1–2 pairs/PR |
| m | Memory + core | SQLite*, WorldDatabase, WorldState, CombatComponent | repos easier |
| n | Misc reasoning | Disposition, NPC knowledge, pickpocket, gens, merchants | split further if fat |

**Extract patterns:** pure moves / apply objects (019/023 style); thin public entrypoints; console+GUI parity; mock LLM; no unauthorized test edits (else lock regen). New `.kt`: **no override** (Added ban) — must meet global E.  
**Overrides:** caps **only lower**; post-split remeasure `--files`; delete or lower; retarget `ticket` to child; never raise.

**Rank cmd:**  
`python3 tools/quality/check_token_budget_kt.py --json-out tmp/workers/MUD-034/token_budget_full.json --quiet-stdout`  
→ sort candidates by `file_tokens` → `RANKED_GODS.md`.

## 4. Touch set (umbrella)

- Create: `RANKED_GODS.md`, optional full JSON, children `issues/MUD-034a-…`–`n`
- Edit: umbrella ticket → done; BOARD Q3 children + Recently done; optional 1-line `TOKEN_BUDGET_KT.md`
- **No** mass product `*.kt`; **no** override raise; prefer no override edits until a child lands a split

## 5. Non-goals

Detekt full burn-down · PIT 80% (035) · 036–038 work · raise caps · product features · one mega-PR for all 55 · commit/push unless Jason asks

## 6. Confirm acceptance

- [x] `RANKED_GODS.md` ranks all override hosts
- [x] ≥12 children filed (`034a`–`n`): acceptance extract+green+lower override; depends 034/031; verify `--core`
- [x] No first split (or micro only if approved)
- [x] No raised/new unjustified overrides
- [x] `--core` exit 0; cite `tmp/dod-summary.json`
- [x] CLOSEOUT + BOARD; children own splits

## 7. Impl steps

1. Full scan → `RANKED_GODS.md`  
2. Finalize family table (trim 034n if fat)  
3. File children from `issues/_templates/ticket.md`  
4. BOARD Q3 list children; close umbrella when criteria met  
5. Optional TOKEN_BUDGET_KT one-liner  
6. `--core` + CLOSEOUT  
7. **STOP** — no god extract unless Astra expands scope  

## 8. Risks

| Risk | Mitigation |
|------|------------|
| Behavior drift | Children only; pure moves; parity pairs |
| Test-lock | No test edits unless scoped + regen |
| Serial tree | One live builder; queue children |
| Scope creep | Tickets-only close; refuse mega-PR |
| Console≠GUI | Parity families e/h/i/k/l |
| New files fail hard-touch | Under global E; no Added override |
| Override still ticket 034 | Children retarget on land |

**Handoff:** Astra/Jason APPROVED → **fresh** impl brief — do not resume this plan session for product work.
