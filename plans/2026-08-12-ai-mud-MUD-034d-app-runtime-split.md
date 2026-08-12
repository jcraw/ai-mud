# MUD-034d Plan — App runtime split (Wave Q3)

**Ticket:** MUD-034d · implementing (APPROVED) · grok
**Status: APPROVED by Astra 2026-08-12 04:26 MST** → fresh IMPL session  
**Plan/mirror:** `plans/2026-08-12-ai-mud-MUD-034d-app-runtime-split.md` · `tmp/workers/MUD-034d/PLAN.md`  
**Depends:** MUD-034, MUD-031 · parent 034 · prior 034a–c done  
**Verify:** `./tools/verify_mud.sh --core` · pure moves; no features

---

## 1. Goal / acceptance

| # | Acceptance | How |
|---|------------|-----|
| 1 | Extract 3 hosts, behavior-preserving | Pure-move clusters; thin public entry; no protocol/feature |
| 2 | Console+GUI parity where pairs exist | Keep `MudGame` + `handlers/*` contracts; **no** client edits (034a done). Multi-user path: **preserve** stubs/order; do not “finish” MU |
| 3 | `--core` = 0 | After cuts + overrides |
| 4 | Remeasure; lower/remove overrides | `--files <touched>`; never raise; no Added override |
| 5 | Residual ticket → `MUD-034d` | If host still needs override |
| 6 | New `.kt` ≤ global E | tok 2500 / LOC 1100 / fn 250; fragment if needed |
| 7 | No unauthorized tests | Prefer none; lock regen only if scoped |

---

## 2. Inventory

| path | file_tok | loc | ov tok_E | peak fn | ticket |
|------|--------:|----:|---------:|---------|--------|
| `MudGameEngine.kt` (`MudGame`) | **10257** | ~787 | 10257 | processIntent **1593** | MUD-034 |
| `GameServer.kt` | **10127** | ~812 | 10127 | handleIntent **1217** | MUD-034 |
| `MultiUserGame.kt` | **2730** | ~233 | 2730 | runPlayerSession **611** | MUD-034 |

Global E2500/1100/fn250. Ranked #4/#5/#45. Peers out-of-family: `handlers/*` (034e+), client (034a).

**MudGame map:** ctor/deps **stay**; `start` stay; room/treasure describe → extract; `processIntent` when→`handlers/*` (optional router; else residual FN like 034a); quests/maxHP → extract; NPC turns/attack/skill (**1046**) → extract; death/respawn → extract; exits/cost/narration fold.

**GameServer map:** public session/API+mutex **stay**; **inline** MU handlers (not `handlers/*`/`MudGame` receiver): nav move/look/search; items take*/drop/give/equip/use; social talk/check/persuade/intimidate; quest track/format; thin `handleIntent` when + stubs **verbatim**.

**MultiUserGame:** start wiring; session loop; V3 exits; 4 fallback factories — barely over file E.

---

## 3. Extract approach

**Principles:** same package `com.jcraw.app`; pure moves; helpers take `MudGame` or GameServer deps/params. **Do not** merge MU into `handlers/*`. Mutex stays on GameServer public entry. No MU feature fill-in.

**Cut order:**

1. **MU fallbacks** (+ exits) → drop MU override if ≤E  
2. **MudGameNpcCombat** (± Attack fragment for FN_E; mirror 034a)  
3. **MudGameRoomDescribe**  
4. **MudGameQuestSupport** + **DeathRespawn** (merge if small)  
5. Intent router only if host still ≫E **and** FN-safe; else leave on host  
6. **GameServer** Item → Nav → Social → Quest → thin when/router  
7. Remeasure each cut; fragment new files to global E; residual host override = measured + ticket `MUD-034d`

**API thin:** `MudGame` ctor/`start` + `internal` surface for handlers; `GameServer` add/remove/processIntent/get/updateWorldState/`itemRepository`; `MultiUserGame.start()`.

---

## 4. Files / overrides

**Edit:** 3 hosts + `config/quality/token_budget_kt.json` (those 3 rows only).

**Create (≤E each; names flexible):**  
`MultiUserFallbacks` · `MudGameNpcCombat` (±Attack) · `MudGameRoomDescribe` · `MudGameQuestSupport` · `MudGameDeathRespawn` · `GameServerItemHandlers` · `GameServerNavHandlers` · `GameServerSocialHandlers` · `GameServerQuestSupport` · optional routers.

**Not:** `handlers/*`, client, 034e–n, mass detekt, `src/test/**`, other override rows.

**Overrides:** remove if ≤E; else lower to measured + `ticket: MUD-034d`; never raise; no Added override. Stage new `.kt` for hard-on-touched.

---

## 5. Specs/docs

None beyond TOKEN_BUDGET lower-only. Closeout in worker dir only.

---

## 6. Tests / verify

- `./tools/verify_mud.sh --core`  
- Remeasure:  
  `python3 tools/quality/check_token_budget_kt.py --files <hosts+extracts> --json-out tmp/workers/MUD-034d/token_remeasure.json --quiet-stdout`  
- Optional manual MU: one look/move; no new multi-user tests unless scoped.

---

## 7. Impl steps

1. Baseline → `tmp/workers/MUD-034d/token_baseline.json`  
2. MU fallbacks → remeasure/remove override  
3. MudGame NPC combat → describe → quest/death  
4. Optional MudGame router  
5. GameServer domain clusters; keep mutex shells  
6. Thin hosts; `:app:compileKotlin`  
7. Remeasure; override lower/remove/retarget only  
8. `--core` (N≤3 flaky then escalate)  
9. CLOSEOUT + board done (impl session)

---

## 8. Out-of-scope

Raise caps · Added overrides · mass detekt · PIT 80% · 036–038 · handlers/client/034e–n · MU feature/protocol · behavior change · unify GameServer↔MudGame handlers · git push unless Jason asks

---

## 9. Risks

| Risk | Mitigation |
|------|------------|
| Mutex/session/broadcast order | Lock stays on public entry; no reorder |
| NPC/death/quest drift | Pure-move; preserve side-effect order |
| Break `handlers/*` via MudGame surface | Keep needed `internal` on class |
| New-file FN_E/FILE_E | Fragment; no Added grandfather |
| Override raise/wrong ticket | Lower-only; retarget 034d; remeasure first |
| Out-of-family thrash (034c lesson) | No renames outside family |
| Residual host ≫E | OK with measured residual override |

**Handoff:** PLAN ONLY → APPROVED → **fresh** impl. No product code this turn.
