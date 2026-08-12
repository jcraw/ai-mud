# MUD-034e Plan — Skill/quest handlers parity split (Wave Q3)

**Ticket:** MUD-034e · plan_review · grok  
**Status:** APPROVED by Astra 2026-08-12 04:54 MST → fresh IMPL session
**Plan/mirror:** `plans/2026-08-12-ai-mud-MUD-034e-skill-quest-handlers-split.md` · `tmp/workers/MUD-034e/PLAN.md`  
**Depends:** MUD-034, MUD-031 · parent 034 · 034a–d done (do not reopen)  
**Verify:** `./tools/verify_mud.sh --core` · pure moves; no features  
**Baseline:** `tmp/workers/MUD-034e/token_baseline.json`

---

## 1. Goal / acceptance

| # | Acceptance | How |
|---|------------|-----|
| 1 | Extract both hosts, behavior-preserving | Pure-move domain clusters; thin public facades; no skill/quest/meta gameplay change |
| 2 | Console+GUI **parity** | Mirror app↔client extract names/order; keep public `handle*` signatures + call sites on `MudGame`/`EngineGameClient` |
| 3 | `--core` = 0 | After cuts + override edit |
| 4 | Remeasure; lower/remove overrides | `--files <touched>`; never raise; no Added override |
| 5 | Residual ticket → `MUD-034e` | If host still needs override |
| 6 | New `.kt` ≤ global E | file tok 2500 / LOC 1100 / fn 250; fragment bodies if needed |
| 7 | No unauthorized tests | Prefer none; lock regen only if scoped |

---

## 2. Inventory

| path | file_tok | loc | ov file_E | ov peak fn_E | ticket |
|------|--------:|----:|----------:|-------------:|--------|
| `app/.../handlers/SkillQuestHandlers.kt` | **9827** | 841 | 9827 | handleInteract **2218** | MUD-034 |
| `client/.../handlers/ClientSkillQuestHandlers.kt` | **9517** | 742 | 9517 | handleInteract **2394** | MUD-034 |

Global E: file 2500 / LOC 1100 / fn 250. Ranked #6/#7. Package flat today (`handlers/` only — no subpkgs). Sibling flat extracts live under `app/` (034d), not under `handlers/`.

**App public surface** (MudGameEngine call sites — keep signatures):  
Interact · Check · UseSkill · TrainSkill · ChoosePerk · ViewSkills · Craft · Save · Load · Help · Quests · Accept/Abandon/Claim · Quit  
(+ private: Fountain, HealingSpell, inferSkillFromAction)

**Client public surface** (EngineGameClient):  
UseSkill · Train · Perk · ViewSkills · Save · Load · Help · Quests · Accept/Abandon/Claim · Interact · Craft · Quit  
(+ private: Healing, Fountain, infer)

**Parity gaps (do not “fix”):**
- `handleCheck` **app-only** here; client `Intent.Check` → `ClientSocialHandlers` (leave routing)
- Client `Intent.Craft` still stubs “not yet integrated” on facade; `handleCraft` body exists but unused — pure-move body only; **no** wiring enable
- Client Interact also exists on `ClientMovementHandlers` for a different dispatch path — **do not** touch movement family (034i)

**Hotspots (baseline FN tok, heuristic):**  
Interact 2218/2394 · Craft 1504/1569 · UseSkill+Heal+infer ~1.8–2.0k · Train/Perk · Quest cluster · Help/Meta · app Check 731

---

## 3. Extract approach

**Principles (034a–d lessons):**
- Same packages: `com.jcraw.app.handlers` / `com.jcraw.mud.client.handlers`
- **Thin facade hosts** keep all public `fun handle*` names → zero/minimal call-site churn on `MudGameEngine`/`EngineGameClient` (avoid out-of-family thrash, 034c lesson)
- Pure-move bodies to sibling `object`s; helpers take `MudGame` / `EngineGameClient`
- **Fragment** any extracted fn that would exceed global FN_E 250 (Interact/Craft/UseSkill will); no Added override grandfather
- App↔client: extract **paired clusters in lockstep** (app cluster N then client twin N)
- Detekt legacy smells: `@file:Suppress` on extracts if needed; no mass baseline regen

**Recommended cut order (parity lockstep):**

| step | cluster | app extract (names flexible) | client twin | notes |
|-----:|---------|------------------------------|-------------|-------|
| 1 | Meta | `SkillQuestMetaHandlers` | `ClientSkillQuestMetaHandlers` | Save/Load/Help/Quit — small; warm path |
| 2 | Quest | `SkillQuestQuestHandlers` | `ClientSkillQuestQuestHandlers` | list/accept/abandon/claim |
| 3 | Train | `SkillQuestTrainHandlers` | `ClientSkillQuestTrainHandlers` | train/perk/view |
| 4 | Skill use | `SkillQuestSkillUseHandlers` | `ClientSkillQuestSkillUseHandlers` | use + heal + infer; fragment if FN>250 |
| 5 | Craft | `SkillQuestCraftHandlers` | `ClientSkillQuestCraftHandlers` | must fragment (~1.5k fn) |
| 6 | Interact | `SkillQuestInteractHandlers` (+ fragments) | `ClientSkillQuestInteractHandlers` (+fragments) | must multi-file; fountain stays with interact |
| 7 | Check | `SkillQuestCheckHandlers` | _(none)_ | app-only; pure-move only |
| 8 | Thin hosts | residual facades only | residual facades only | aim remove both overrides |

**API thin:** hosts remain the only objects named in intent dispatch; delegates one-liner to extracts. Optional later: re-point dispatch — not required if facades stay.

If residual host still >E (unlikely if only delegates): lower override to measured + `ticket: MUD-034e`. Prefer **remove** both override rows when under global file/fn E.

---

## 4. Files / overrides

**Edit:**
- `app/.../SkillQuestHandlers.kt`
- `client/.../ClientSkillQuestHandlers.kt`
- `config/quality/token_budget_kt.json` — **only** those 2 override rows

**Create (≤E each; names flexible; mirror prefixes):**  
Meta / Quest / Train / SkillUse / Craft / Interact(+fragments) / app Check — ~12–18 new `.kt` depending on FN fragmentation.

**Not:** MudGameEngine/GameServer/EngineGameClient logic (call sites only if forced), 034f–n hosts, other handlers (item/move/social/trade/treasure/combat), mass detekt, `src/test/**`, other override rows, craft-wiring enable.

**Overrides:** remove if ≤ global E; else lower to remeasured file/fn peaks + `ticket: MUD-034e`; never raise; no Added override. Stage new `.kt` so hard-on-touched git-diff sees them.

---

## 5. Specs/docs

None beyond TOKEN_BUDGET lower-only discipline. Closeout in `tmp/workers/MUD-034e/` only.

---

## 6. Tests / verify

- `./tools/verify_mud.sh --core`
- Remeasure:  
  `python3 tools/quality/check_token_budget_kt.py --files <hosts+extracts> --json-out tmp/workers/MUD-034e/token_remeasure.json --quiet-stdout`
- Parity smoke (manual, no new tests): console skill view / quest list / save·load / help · GUI same intents; craft still stub on GUI; interact gather path both sides if exercised
- No `src/test/**` unless ticket re-scopes + test-lock write

---

## 7. Impl steps

1. Confirm baseline `token_baseline.json` (already present)
2. Meta pair → compile `:app` + `:client`
3. Quest pair → Train pair → SkillUse pair (fragment as needed)
4. Craft pair (fragment) → Interact pair (multi-file fragment)
5. App Check extract
6. Collapse hosts to thin delegates; remeasure
7. Override remove or lower+retarget `MUD-034e` only
8. Stage new `.kt`; `--core` (N≤3 flaky then escalate)
9. CLOSEOUT + ticket/board done (impl session)

---

## 8. Out-of-scope

Raise caps · Added overrides · mass detekt · PIT 80% · 036–038 · 034f–n · enable client Craft dispatch · move Check into client skill host · unify with GameServer social check · gameplay/features · git commit/push unless Jason asks

---

## 9. Risks

| Risk | Mitigation |
|------|------------|
| App/client behavior drift | Lockstep pairs; pure-move; keep side-effect order |
| Interact/Craft FN_E on Added files | Body fragmentation before/with extract; no grandfather |
| Call-site thrash on Engine/MudGame | Keep facade `handle*` names on residual hosts |
| Residual FN_E on host if facade keeps fat when | Facades = delegates only; fat leaves host |
| Override raise / wrong ticket | Remeasure first; lower-only; retarget 034e |
| Out-of-family thrash (034c) | Touch only this family + 2 override rows |
| Client Craft dead code “fixed” accidentally | Do not wire Intent.Craft; pure-move only |
| Serial tree | One live builder; do not start 034f in parallel on same checkout |
| Detekt baseline ID shift | `@file:Suppress` carry; no mass regen |

---

**Handoff:** APPROVED by Astra 2026-08-12 04:54 MST. Fresh IMPL authorized.
