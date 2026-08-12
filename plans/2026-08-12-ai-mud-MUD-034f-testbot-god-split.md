# MUD-034f Plan — Testbot god split (Wave Q3)

**Ticket:** MUD-034f · plan_review · grok  
**Status:** APPROVED by Astra 2026-08-12 05:28 MST → fresh IMPL session
**Plan/mirror:** `plans/2026-08-12-ai-mud-MUD-034f-testbot-god-split.md` · `tmp/workers/MUD-034f/PLAN.md`  
**Depends:** MUD-034, MUD-031 · parent 034 · **034a–e done** (do not reopen)  
**Verify:** `./tools/verify_mud.sh --core` · pure moves; no features  
**Baseline:** `tmp/workers/MUD-034f/token_baseline.json`

---

## 1. Goal / acceptance

| # | Acceptance | How |
|---|------------|-----|
| 1 | Extract six hosts, behavior-preserving | Pure-move clusters; thin public entrypoints; no scenario/feature work |
| 2 | Console+GUI **parity** | **N/A** — testbot-only; **no app/client pairs**; never edit `app/`/`client/` |
| 3 | `--core` = 0 | After cuts + override edit (+ `:testbot:compileKotlin` smoke) |
| 4 | Remeasure; lower/remove overrides | `--files <touched>`; never raise; no Added override |
| 5 | Residual ticket → `MUD-034f` | If host still needs override |
| 6 | New `.kt` ≤ global E | file 2500 / LOC 1100 / **fn 250**; fragment; no grandfather |
| 7 | No unauthorized tests | Prefer **no** `src/test/**` |

---

## 2. Inventory

| path | file_tok | loc | ov file_E | peak fn (measured) | ticket |
|------|--------:|----:|----------:|-------------------:|--------|
| `testbot/.../InputGenerator.kt` | **9131** | 592 | 9131 | `buildUserContext` **7884** | MUD-034 |
| `…/validation/CodeValidationRules.kt` | **7761** | 616 | 7761 | Social **2351** / Item **1856** / Combat **1434** | MUD-034 |
| `…/validation/ValidationPrompts.kt` | **5412** | 414 | 5412 | `buildUserContext` **914** | MUD-034 |
| `testbot/.../TestBotRunner.kt` | **4420** | 365 | 4420 | summary **1137** / step **959** | MUD-034 |
| `testbot/.../V3TestGameEngine.kt` | **3616** | 308 | 3616 | Attack **421** / look **321** | MUD-034 |
| `testbot/.../TestModels.kt` | **2636** | 266 | 2636 | `fromTestState` **425** | MUD-034 |

Global E: file **2500** / LOC **1100** / fn **250**. Packages: `com.jcraw.mud.testbot` (+ `.validation`). Prefer **same-package** flat extracts.

**Keep public names (thin facades):**  
`InputGenerator.generateInput` + `GeneratedInput` · `CodeValidationRules.validate` (+ inventory track) · `ValidationPrompts.buildSystemPrompt`/`buildUserContext` · `TestBotRunner.run` · `V3TestGameEngine` GameEngine surface · model types (`TestStep`…`TestReport`).

**Callers:** `OutputValidator`, `TestBotMain`, scenario runners — no thrash. Hotspots: InputGen `when(scenario)` L72–631 · CodeVal Item/Move/Combat/Social · ValPrompts `build*Criteria` · Runner step/summary/complete · V3 handle* · TestReport companion metrics.

---

## 3. Extract approach

**Principles:** pure-move; same package; thin facades; fragment FN>250; `@file:Suppress` for legacy detekt; no mass baseline; **no** app/client.

| step | host | extracts (names flexible) | notes |
|-----:|------|---------------------------|-------|
| 1 | TestModels | `TestReportFactory` / `TestReportMetrics` | Keep small data classes; aim remove override |
| 2 | V3TestGameEngine | Intent/move/item/combat/meta handler objects | Host: state + thin `processIntent` |
| 3 | TestBotRunner | StepExecutor · ContextBuilder · Summary · ScenarioComplete · DebugFilter | Host: `run()` + deps |
| 4 | ValidationPrompts | Criteria packs (core + playthroughs if needed) | Host: public `build*` delegates |
| 5 | CodeValidationRules | Item · Movement · Combat · Social · Inventory | Host: `validate` router; multi-file required |
| 6 | InputGenerator | `InputGuidanceCore` + `InputGuidancePlaythroughs` (+ more if FN) | Host: generate/system/parse; multi-file required |
| 7 | Overrides | remeasure → remove or lower+`ticket: MUD-034f` | Never raise; no Added |

---

## 4. Files / overrides

**Edit:** six hosts + `config/quality/token_budget_kt.json` (**only** those six rows).  
**Create:** ~12–20 new testbot `.kt`; each ≤ global E; **no** override on Added.  
**Not:** app/client, 034g–n, TestScenario redesign, OutputValidator rewrite, mass detekt, `src/test/**`, other override rows, commit/push.  
**Overrides:** remove if under global file+fn E; else lower to remeasured peaks + **MUD-034f**.

---

## 5. Specs/docs

None. Closeout only under `tmp/workers/MUD-034f/`.

---

## 6. Tests / verify / remeasure

- `./tools/verify_mud.sh --core`
- Smoke: `./gradlew :testbot:compileKotlin` per major cut
- Remeasure: `python3 tools/quality/check_token_budget_kt.py --files <hosts+extracts> --json-out tmp/workers/MUD-034f/token_remeasure.json --quiet-stdout`
- No `src/test/**` unless re-scoped + test-lock write
- N≤3 flaky then escalate

---

## 7. Ordered impl steps

1. Baseline OK; no parallel family work  
2. Models → V3 engine → Runner → ValPrompts → CodeVal → InputGen (compile each)  
3. Thin hosts; remeasure  
4. Override remove / lower+retarget **MUD-034f** only  
5. Stage new `.kt`; `--core`; CLOSEOUT + board done (**impl session**)

---

## 8. Out-of-scope

Raise caps · Added overrides · mass detekt · PIT 80% · 036–038 · 034g–n · app/client · behavior change · this-session product `*.kt` · commit/push

---

## 9. Risks

| Risk | Mitigation |
|------|------------|
| Guidance/validation string drift | Pure-move; keep match order |
| FN_E on Added | Fragment ≤250; no grandfather |
| Caller thrash | Keep facade type/fun names |
| Residual host FN_E | Delegates only |
| Override raise / wrong ticket | Remeasure; lower-only; **034f** |
| Serial tree / reopen 034a–e | One builder; leave done alone |
| testbot not in `--core` unit set | compileKotlin + detekt + token on touch set |
| Accidental `src/test/**` | Don't touch; test-lock |
| Detekt baseline shift | `@file:Suppress` carry |

---

**Handoff:** APPROVED by Astra 2026-08-12 05:28 MST. Fresh IMPL authorized.
