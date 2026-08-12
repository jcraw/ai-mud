# MUD-034k Plan — Combat surface split (Wave Q3)

**Ticket:** MUD-034k · implementing (APPROVED) · grok  
**Status:** APPROVED by Astra 2026-08-12 08:03 MST → fresh IMPL session
**Plan/mirror:** `plans/2026-08-12-ai-mud-MUD-034k-combat-surface-split.md` · `tmp/workers/MUD-034k/PLAN.md`  
**Depends:** MUD-034, MUD-031 · 034a–j done (do not reopen)  
**Verify (post-impl):** `./tools/verify_mud.sh --core` · pure moves; no features  
**Pattern:** 034i parity facades · 034j pure-move thin hosts (stable FQCN)

---

## 1. Goal / acceptance

| # | Acceptance | How |
|---|------------|-----|
| 1 | Extract 6 hosts, behavior-preserving | Pure-move; thin public entrypoints; no combat change |
| 2 | Console+GUI **parity** | Lockstep `CombatHandlers` ↔ `ClientCombatHandlers` |
| 3 | `--core` = 0 | After cuts + override edit |
| 4 | Remeasure; lower/remove overrides | never raise; no Added override |
| 5 | Residual ticket → `MUD-034k` | If host still needs override |
| 6 | New `.kt` ≤ global E | tok 2500 / LOC 1100 / fn 250; fragment if needed |
| 7 | No unauthorized tests | Prefer none |

---

## 2. Inventory

| path | file_tok | wc_loc | ov file_E | peak FN (tok) | ov fn_E |
|------|--------:|-------:|----------:|---------------|--------:|
| `app/.../CombatHandlers.kt` | **3249** | 307 | 3249 | `handleAttack` **1978** · skillProg 517 | 1978 |
| `client/.../ClientCombatHandlers.kt` | **3432** | 319 | 3432 | `handleAttack` **2145** · skill 534 · display 306 | 2145 |
| `reasoning/.../combat/AttackResolver.kt` | **3614** | 397 | 3614 | `resolveAttack` ~**1715** · helpers ~254 | 254 |
| `reasoning/.../combat/FleeResolver.kt` | **2794** | 318 | 2794 | `resolveFlee` **1312** | 1312 |
| `reasoning/.../combat/MonsterAIHandler.kt` | **2863** | 347 | 2863 | decide 495 · buildPrompt 477 · tryLLM 291 | 495 |
| `reasoning/.../CombatNarrator.kt` | **3219** | 341 | 3219 | round **1017** · start 447 · live 386 | 1017 |

All overrides `ticket: MUD-034`. Global E: **2500 / 1100 / 250**. Ranked pure-first + parity.

**Public keep (stable FQCN):**  
`CombatHandlers.handleAttack` · `ClientCombatHandlers.handleAttack` · `AttackResolver`+`resolveAttack` · `FleeResolver`+`resolveFlee` · `MonsterAIHandler`+`decideAction` · `CombatNarrator`+`narrateAction`/`narrateCombatRound`/`narrateCombatStart` · sealed `AttackResult`/`DefenseOutcome`/`DamageContext`/`DamageResult`/`FleeResult`/`AIDecision` (same package; callers include MovementFlee*, ClientNpcAttack, PersonalityAI, DamageCalculator).

**Coupling (do not “fix”):** Narration ≠ resolve math; Monster AI ≠ attack apply; handlers orchestrate resolver+narrator+death/quests/counter (stay app/client); Attack+Flee share entity lookup → one same-package helper.

---

## 3. Design / approach

**Principles:** same-package (`app.handlers` / `client.handlers` flat; `reasoning.combat`; narrator `reasoning`); thin facades; **pure first** reasoning; then parity handlers; fragment FN>250; no mass detekt; no engine rewire.

### A. Pure reasoning (first)

| step | host | extracts | notes |
|-----:|------|----------|-------|
| 1 | AttackResolver | `AttackResult` · `DefenseOutcome` · Damage types file | type bulk off host |
| 2 | Attack+Flee | `CombatEntityLookup` (findEntity/getComponent) | dedupe |
| 3 | AttackResolver | `AttackResolveApply` (+frag hit/miss/damage) | thin `resolveAttack` |
| 4 | FleeResolver | `FleeResult` · `FleeResolveApply` (+frag free-atk) | thin `resolveFlee` |
| 5 | MonsterAIHandler | `AIDecision` · Prompts · Parse · Fallback · optional Llm | thin `decideAction` |
| 6 | CombatNarrator | Action (+live/cache) · Round · Start (+frag) | host delegates; **no** merge into combat/ |

### B. Parity handlers

| step | cluster | app | client |
|-----:|---------|-----|--------|
| 7 | Attack body | `CombatAttackHandlers` (+frag) | `ClientCombatAttackHandlers` (+frag) |
| 8 | Skill progress | `CombatSkillProgressHandlers` | `ClientCombatSkillProgressHandlers` |
| 9 | Display helpers | fold or tiny shared-name pair | same |
| 10 | Thin hosts | `handleAttack` → extract | same |

Prefer **remove** all 6 overrides if ≤E; else **lower** + `ticket: MUD-034k`.

---

## 4. Files to create/touch

**Edit:** 6 hosts; `token_budget_kt.json` **only** those 6 rows.

**Create (~12–20 `.kt`, ≤E each):** AttackResult · DefenseOutcome · Damage types · CombatEntityLookup · AttackResolve* · FleeResult · FleeResolve* · AIDecision · MonsterAI{Prompts,Parse,Fallback} · CombatNarrator{Action,Round,Start} · CombatAttack* + Client* · CombatSkillProgress* pair.

**Not:** engine construct sites, PersonalityAI rewrite, ClientNpcAttack logic, MovementFlee* bodies, DeathHandler, CombatBehavior, 034a–j/l–n, mass detekt, `src/test/**`, other overrides. Stage new `.kt`.

---

## 5. Non-goals

Raise caps · Added overrides · mass detekt · PIT 80% · 036–038 · outside family · reopen 034a–j · 034l–n · merge narrator↔resolver · balance/features · commit/push unless Jason asks.

---

## 6. Acceptance checklist (impl)

- [ ] Reasoning hosts thin public entrypoints; bodies in extracts
- [ ] Handler hosts thin `handleAttack`; lockstep app↔client
- [ ] Public FQCN/signatures unchanged (construct + sealed same package)
- [ ] Narration / AI / resolve stay decoupled
- [ ] `./tools/verify_mud.sh --core` = 0
- [ ] Remeasure → `tmp/workers/MUD-034k/token_remeasure.json`
- [ ] Overrides removed or lowered+`MUD-034k`; never raised; no Added
- [ ] New `.kt` file≤2500, peak fn≤250
- [ ] No unauthorized `src/test/**`
- [ ] CLOSEOUT: paths, before/after tokens, residual risk

---

## 7. Ordered impl steps

1. Baseline → `tmp/workers/MUD-034k/token_baseline.json`
2. Attack types + CombatEntityLookup out of AttackResolver
3. AttackResolve* → thin `resolveAttack`; `:reasoning` compile
4. FleeResult + FleeResolve* → thin `resolveFlee`
5. MonsterAI prompts/parse/fallback/AIDecision → thin `decideAction`
6. CombatNarrator Action/Round/Start → thin public methods
7. Handler parity Attack(+frag)+SkillProgress; collapse hosts
8. Remeasure; override remove or lower+`MUD-034k`
9. Stage new `.kt`; `--core` (N≤3 flaky then escalate)
10. CLOSEOUT + ticket/board done (**fresh impl**, post-APPROVED)

---

## 8. Risks

| Risk | Mitigation |
|------|------------|
| Break hit/miss/damage order | Pure-move resolve pipeline; same step order |
| Sealed-type import thrash | Same package; no rename |
| App/client drift | Lockstep pure-move pairs |
| FN_E on Added (resolve/flee/round/attack) | Fragment; no grandfather |
| Narrator↔resolver “unification” | Explicit non-goal |
| Flee callers (MovementFlee*) break | Keep FleeResult FQCN |
| Override raise / wrong ticket | Remeasure; lower-only; retarget 034k |
| Serial tree | One builder; no parallel 034l |
| Detekt ID shift | Suppress carry; no mass regen |

---

**Handoff:** APPROVED by Astra 2026-08-12 08:03 MST. Fresh IMPL authorized — execute plan; do not re-plan unless blocked. Do not resume plan session.
