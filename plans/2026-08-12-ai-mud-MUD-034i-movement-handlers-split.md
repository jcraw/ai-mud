# MUD-034i Plan — Movement handlers parity split (Wave Q3)

**Ticket:** MUD-034i · plan_review · grok  
**Status:** APPROVED by Astra 2026-08-12 07:13 MST → fresh IMPL session
**Plan/mirror:** `plans/2026-08-12-ai-mud-MUD-034i-movement-handlers-split.md` · `tmp/workers/MUD-034i/PLAN.md`  
**Depends:** MUD-034, MUD-031 · 034a–h done (do not reopen)  
**Verify (post-impl):** `./tools/verify_mud.sh --core` · pure moves; no features  
**Pattern:** 034h pure-move facades + lockstep (`tmp/workers/MUD-034h/CLOSEOUT.md`)

---

## 1. Goal / acceptance

| # | Acceptance | How |
|---|------------|-----|
| 1 | Extract both hosts, behavior-preserving | Pure-move clusters; thin public facades; no movement change |
| 2 | Console+GUI **parity** | Lockstep app↔client pairs; public `handle*` stay on hosts |
| 3 | `--core` = 0 | After cuts + override edit |
| 4 | Remeasure; lower/remove overrides | never raise; no Added override |
| 5 | Residual ticket → `MUD-034i` | If host still needs override |
| 6 | New `.kt` ≤ global E | tok 2500 / LOC 1100 / fn 250; fragment if needed |
| 7 | No unauthorized tests | Prefer none |

---

## 2. Inventory

| path | file_tok | wc_loc | ov file_E | ov peak fn_E | ticket |
|------|--------:|-------:|----------:|-------------:|--------|
| `app/.../handlers/MovementHandlers.kt` | **5770** | 541 | 5770 | Search **819** | MUD-034 |
| `client/.../handlers/ClientMovementHandlers.kt` | **4738** | 451 | 4738 | Search **924** | MUD-034 |

Global E: 2500 / 1100 / 250. Ranked #14/#17 · note: **frontier/lazy-fill**.

**Peak FN tok:** App Search 819 · postMove **~1223** · Flee **~706** · Travel 580 · performMove 526 · Scout 401 · Look 375 · populate 365. Client Search 924 · Flee **~787** · Look 748 · Travel 744 · Scout 551.

**App public (keep on host):** Move · Scout · Travel · Look · Search (Flee intent → `handleMove`).

**Client public (keep on host):** Move · Scout · Travel · Look · Search · **Interact** stub (Flee → `handleMove`).

**Parity gaps (do not “fix”):**
- **App-only** `postMove`: lazy-fill + populate + Frontier chunk gen. Client uses `EngineGameClient.handlePlayerMovement` → `ClientSpaceContent` (**out of family**).
- **Client-only** Interact stub; app `Intent.Interact` → SkillQuest (034e). Keep stub.
- `finalizeTreasureRoomExit` both sides; move path commented (swap-anytime); live on some travel branches — pure-move as-is.

---

## 3. Design / approach

**Principles:** packages `com.jcraw.app.handlers` / `com.jcraw.mud.client.handlers` (flat); thin facade `handle*`; pure-move siblings; **fragment FN>250**; lockstep pairs; no mass detekt; no engine rewire.

| step | cluster | app | client | notes |
|-----:|---------|-----|--------|-------|
| 1 | Move+flee+hostiles | `MovementMoveHandlers` (+frag Flee) | `ClientMovementMoveHandlers` (+frag) | app→post; client→`handlePlayerMovement` |
| 2 | **Post-move fill (APP ONLY)** | `MovementPostMoveHandlers` (+frag) | _(none)_ | cohesive lazy-fill→populate→frontier→quests→describe |
| 3 | Treasure finalize | `MovementTreasureExit` | `ClientMovementTreasureExit` | tiny; travel (+ dead move sites) |
| 4 | Look | `MovementLookHandlers` | `ClientMovementLookHandlers` | client body richer |
| 5 | Search | `MovementSearchHandlers` (+frag) | `ClientMovementSearchHandlers` (+frag) | peaks 819/924 |
| 6 | Travel | `MovementTravelHandlers` | `ClientMovementTravelHandlers` | |
| 7 | Scout | `MovementScoutHandlers` | `ClientMovementScoutHandlers` | |
| 8 | Interact stub | _(none)_ | stay on host | 1-liner |
| 9 | Thin hosts | facades | facades | prefer **remove** both overrides |

**Frontier/lazy-fill:** all in app `postMove` (~95 LOC / ~1223 tok). Keep sequential side effects. One cluster; fragment only package helpers in same order. Do not move frontier into `:core`/`:reasoning`. Client post-move out of scope.

Facades sole dispatch targets. Residual host >E → lower + `ticket: MUD-034i`.

---

## 4. Files to create/touch

**Edit:** both hosts; `token_budget_kt.json` **only** those 2 override rows.

**Create (~8–14 `.kt`, ≤E each):** Move(+Flee) pair · app PostMove(+frag) · TreasureExit pair · Look · Search(+frag) · Travel · Scout.

**Not:** engine dispatch bodies, `ClientSpaceContent`, SkillQuest interact, GameServer*, 034a–h/j–n, mass detekt, `src/test/**`, other overrides.

**Overrides:** remove if ≤ global E; else lower + `MUD-034i`; never raise; no Added. Stage new `.kt`.

---

## 5. Non-goals

Raise caps · Added overrides · mass detekt · PIT 80% · 036–038 · outside family · reopen 034a–h · 034j–n · “fix” client post-move/interact · rewrite frontier · features · commit/push unless Jason asks.

---

## 6. Acceptance checklist (impl)

- [ ] Hosts = thin public `handle*` only; bodies in extracts
- [ ] Pairs Move/Flee · Look · Search · Travel · Scout; app-only PostMove; Interact stub kept
- [ ] Dispatch signatures unchanged
- [ ] postMove order: lazy-fill → populate → frontier → quests → describe
- [ ] `./tools/verify_mud.sh --core` = 0
- [ ] Remeasure → `tmp/workers/MUD-034i/token_remeasure.json`
- [ ] Overrides removed or lowered+`MUD-034i`; never raised; no Added
- [ ] New `.kt` file≤2500, peak fn≤250
- [ ] No unauthorized `src/test/**`
- [ ] CLOSEOUT: paths, before/after tokens, residual risk

---

## 7. Ordered impl steps

1. Baseline → `tmp/workers/MUD-034i/token_baseline.json`
2. App PostMove extract (+frag) → `:app` compile
3. Move+Flee pair (+frag Flee) → `:app`+`:client`
4. TreasureExit · Look pairs
5. Search(+frag) · Travel · Scout pairs
6. Collapse hosts; Interact stays on client host
7. Remeasure; override remove or lower+`MUD-034i`
8. Stage new `.kt`; `--core` (N≤3 flaky then escalate)
9. CLOSEOUT + ticket/board done (**fresh impl**, post-APPROVED)

---

## 8. Risks

| Risk | Mitigation |
|------|------------|
| Break frontier/lazy-fill order | Single PostMove cluster; same call order |
| App/client drift | Lockstep pure-move; leave intentional gaps |
| FN_E on Added (postMove/Flee/Search) | Fragment; no grandfather |
| Call-site thrash | Keep facade `handle*` names |
| Touch ClientSpaceContent for “parity” | Out of family |
| Override raise / wrong ticket | Remeasure; lower-only; retarget 034i |
| Serial tree | One builder; no parallel 034j |
| Detekt ID shift | Suppress carry; no mass regen |

---

**Handoff:** APPROVED by Astra 2026-08-12 07:13 MST. Fresh IMPL authorized — execute plan; do not re-plan unless blocked.
