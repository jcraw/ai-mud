# MUD-034a Plan — Client facade split (Wave Q3)

**Ticket:** MUD-034a · **Phase:** done · **Worker:** grok  
**Status:** DONE 2026-08-12 (APPROVED by Astra 2026-08-12 02:53 MST → fresh IMPL green)
**Plan:** `plans/2026-08-12-ai-mud-MUD-034a-client-facade-split.md`  
**Mirror:** `tmp/workers/MUD-034a/PLAN.md`  
**Depends:** MUD-034 (done), MUD-031 (done) · **Parent:** MUD-034  
**Verify (post-impl):** `./tools/verify_mud.sh --core`  
**Pattern pointer:** pure moves / thin entrypoints (MUD-034 §Extract patterns; MUD-019 `FloorItemTakeApply` + thin handlers; MUD-023 `FloorItemDropApply`). Client already uses `handlers/*` objects + `SpaceEntitySupport`.

---

## 1. Goal / acceptance mapping

| # | Acceptance | Plan how |
|---|------------|----------|
| 1 | Behavior-preserving extract of both hosts | Pure moves of private/internal clusters; keep `EngineGameClient` public `GameClient` surface; no features |
| 2 | Console+GUI parity where pairs exist | **MainGameScreen = GUI-only** (no app pair). EngineGameClient // MudGameEngine intent/NPC shape — **do not** split app hosts (→ **MUD-034d**); client-only pure moves; do not change handler contracts used by both |
| 3 | `--core` green | After all moves |
| 4 | Remeasure + **lower/remove** overrides | `check_token_budget_kt.py --files <touched>`; never raise; no override on Added extracts |
| 5 | Retarget override `ticket` → `MUD-034a` | Only if residual host still needs override |
| 6 | New `.kt` meet global E (tok 2500 / LOC 1100) | Cap each extract file; split further if over |
| 7 | No unauthorized `src/test/**` | Prefer no test edits; if needed → ticket scope + lock regen |

---

## 2. Current inventory

| path | file_tokens | file_loc (wc) | override tok_E | fn_E | ticket |
|------|------------:|--------------:|---------------:|-----:|--------|
| `client/.../EngineGameClient.kt` | **14209** | ~1204 | 14209 | 1455 | MUD-034 |
| `client/.../ui/MainGameScreen.kt` | **3033** | ~344 | 3033 | 701 | MUD-034 |

Global ceilings: file tok **E2500**, file LOC **E1100**. Ranked: #1 and #36 · family peak 14209.

**EngineGameClient structural hotspots** (symbol map, not source dump):

| approx lines | cluster | extract candidate |
|-------------:|---------|-------------------|
| 33–314 | ctor + deps + `init` world/quests | **stay** (facade) |
| 315–360 | `sendInput` / events / `close` / `emitEvent` | **stay** |
| 362–439 | item template load/cache/fallback | `ClientItemTemplateCache` (object) |
| 440–730 | space load, populate, describe, treasure status, frontier, exits | `ClientSpaceContent` (+ optional `ClientFrontierExpansion`) |
| 740–822 | `processIntent` when-dispatch → existing handlers | `ClientIntentRouter` (thin) **or** leave if shell fits E after other cuts |
| 823–892 | maxHP sync, quests, death | `ClientQuestDeathSupport` |
| 893–942 + sealed | respawn input + `RespawnState` | `ClientRespawnFlow` |
| 943–1199 | NPC turns / attack / skill prog / narration | `ClientNpcCombat` (largest risk block) |

**MainGameScreen hotspots:** already 5 top-level `@Composable`s in one file — `MainGameScreen`, `StatusBar`, `GameLogWindow`, `LogEntryText`, `GameInputField` (~62–89L each). Tokens over E despite LOC under; split by composable files.

**Existing layout:** `client/handlers/*` (movement/item/combat/social/trade/treasure/skill-quest), `SpaceEntitySupport.kt`, `GameViewModel.kt`, `ui/Theme.kt`. Do **not** re-split handler family hosts (034e/h/i/k/l).

---

## 3. Recommended extract approach

**Principles:** pure file moves; `internal` APIs on facade (`emitEvent`, `worldState` mutation paths, template getters) stay or become package-internal helpers receiving `EngineGameClient` / deps — mirror existing `Client*Handlers.handleX(this, …)` style. No behavior/feature change. New files under `client/src/main/kotlin/com/jcraw/mud/client/` (+ `ui/` for screens).

**Order of cuts (safest → densest):**

1. **MainGameScreen composables → sibling UI files**  
   - e.g. `ui/StatusBar.kt`, `ui/GameLogWindow.kt` (+ `LogEntryText`), `ui/GameInputField.kt`  
   - Keep `MainGameScreen.kt` as thin composition root  
   - Goal: host ≤ E2500 tok (likely remove override)

2. **Item templates** → `ClientItemTemplateCache.kt` (load/cache/fallback/`getItemTemplate`)

3. **Space content + describe** → `ClientSpaceContent.kt`  
   - populate, describe room/space, treasure status append, loadSpace/Entity, ensureGraphNodeLoaded, buildExitsWithNames, handlePlayerMovement glue  
   - **Frontier** `maybeExpandFrontier` (~71L): same file if under E; else `ClientFrontierExpansion.kt`

4. **Respawn + death + quests** → `ClientRespawnFlow.kt` / `ClientQuestDeathSupport.kt` (merge if both small)

5. **NPC combat loop** → `ClientNpcCombat.kt`  
   - `processNPCTurns`, `executeNPCDecision`, `executeNPCAttack`, skill progression, attack narration, base cost  
   - Keep call sites in `sendInput` path identical

6. **Intent router** (optional last): `ClientIntentRouter.process(client, intent)` only if facade still > E after 2–5

7. **Remeasure loop:** each cut → compile + token check; stop when both hosts ≤ global E **or** residual override only as low as measured + `ticket: MUD-034a`

**Public API kept thin:** `EngineGameClient` remains `GameClient` impl (`sendInput`, `observeEvents`, `getCurrentState`, `close`); handlers keep `EngineGameClient` receiver. No package rename / module split.

---

## 4. Files to touch / create; override edit plan

**Edit hosts:**
- `client/src/main/kotlin/com/jcraw/mud/client/EngineGameClient.kt`
- `client/src/main/kotlin/com/jcraw/mud/client/ui/MainGameScreen.kt`

**Create (names flexible; keep under E each):**
- `ui/StatusBar.kt`, `ui/GameLogWindow.kt`, `ui/GameInputField.kt` (or equivalent)
- `ClientItemTemplateCache.kt`
- `ClientSpaceContent.kt` (± `ClientFrontierExpansion.kt`)
- `ClientRespawnFlow.kt` (± quest/death merge)
- `ClientNpcCombat.kt`
- optional `ClientIntentRouter.kt`

**Do not touch:** app handlers, MudGameEngine, other 034b–n hosts, `token_budget` entries outside these two paths, `src/test/**` unless forced.

**Overrides (`config/quality/token_budget_kt.json`):**
- After remeasure of reduced hosts:
  - if ≤ global E → **remove** override entry
  - if still > E → **lower** `tokens.file.error` (and fn_E / structure if still over) to **new measured** only; set `"ticket": "MUD-034a"`
- **Never** raise caps; **no** override rows for new/Added `.kt`
- Function-level override ceilings may lower with file row; drop fn override fields if no longer needed

---

## 5. Specs / docs

- None required beyond existing `docs/TOKEN_BUDGET_KT.md` (overrides lower-only). No ARCHITECTURE novel. Closeout notes paths + remeasure in worker dir only.

---

## 6. Tests / verify

- `./tools/verify_mud.sh --core` → exit 0 (detekt, Konsist, test-lock, no_live_llm, token hard-on-touched).
- Remeasure:
  ```bash
  python3 tools/quality/check_token_budget_kt.py --files \
    client/src/main/kotlin/com/jcraw/mud/client/EngineGameClient.kt \
    client/src/main/kotlin/com/jcraw/mud/client/ui/MainGameScreen.kt \
    client/src/main/kotlin/com/jcraw/mud/client/<new>.kt \
    … \
    --json-out tmp/workers/MUD-034a/token_remeasure.json
  ```
- Parity checks (manual / smoke, not new product features):
  - Intent still routes to same `Client*Handlers` branches
  - GUI: MainGameScreen composes status/log/input; ViewModel wiring unchanged
  - No app/MudGameEngine edits this ticket
- Prefer **no** `src/test/**` changes; client has little unit surface. If contract smoke needed → explicit scope + `MUD_ALLOW_TEST_CHANGES=1 ./tools/test_lock.sh --write`.

---

## 7. Ordered impl steps

1. Confirm serial sole builder; dirty tree hygiene; no other 034 child live.
2. Split **MainGameScreen** composables → remeasure host.
3. Extract **item template** cluster → facade delegates.
4. Extract **space content / describe / frontier**.
5. Extract **respawn / death / quests**.
6. Extract **NPC combat** (preserve order of side effects vs narration).
7. Optional **intent router** if still over E.
8. `./gradlew :client:compileKotlin` smoke mid-way if helpful.
9. Remeasure all touched + new; edit overrides lower/remove/retarget only.
10. `./tools/verify_mud.sh --core`; cite `tmp/dod-summary.json`.
11. CLOSEOUT: paths, before/after tokens, override delta, residual risk → `tmp/workers/MUD-034a/CLOSEOUT.md`.
12. Ticket → done only after green verify + override discipline (fresh session post-approve).

---

## 8. Out-of-scope

- Raise override caps · new override on Added files  
- Mass detekt baseline · PIT 80% (035) · 036–038  
- Hosts outside family (034b–n, including **MudGameEngine** 034d)  
- Features / combat balance / UI redesign  
- Handler god splits (already separate tickets)  
- git commit/push unless Jason asks  
- Unauthorized test edits  

---

## 9. Risks

| Risk | Mitigation |
|------|------------|
| Behavior drift in NPC/death/respawn | Pure moves; same call order; no algorithm edits |
| GUI coupling / Compose visibility | Keep composables `internal`/`public` as today; same package `ui` |
| Handlers need `internal` members | Prefer package-private helpers; avoid widening API |
| New file fails global E | Split again before shipping; no grandfather override |
| Override mistakes (raise / wrong ticket) | Diff only two host keys; ticket `MUD-034a`; lower-only |
| Token gate fails on touch | Remeasure before claim done; hard-on-touched is live |
| Serial tree conflict | One builder; do not start 034b–n in same tree |
| Console parity misread | Document MainGameScreen GUI-only; do not “fix” app UI |

---

**Handoff:** Astra/Jason **APPROVE plan** → **fresh impl session** (this turn = plan only). Brief: `issues/_templates/implement-brief.md` → `tmp/workers/MUD-034a/`.
