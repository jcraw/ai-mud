# MUD-034b Plan — GraphGenerator layout/MST split (Wave Q3)

**Ticket:** MUD-034b · **Phase:** implementing (APPROVED) · **Worker:** grok  
**Status:** DONE (impl complete 2026-08-12) · was APPROVED by Astra 2026-08-12 03:33 MST
**Plan / mirror:** `plans/2026-08-12-ai-mud-MUD-034b-graph-generator-split.md` · `tmp/workers/MUD-034b/PLAN.md`  
**Depends:** MUD-034, MUD-031 (done) · **Parent:** MUD-034 · **Prior:** 034a done (do not reopen)  
**Verify (post-impl):** `./tools/verify_mud.sh --core`  
**Pattern:** pure moves / thin entrypoints (034a CLOSEOUT; MUD-019/023). No features.

---

## 1. Goal / acceptance mapping

| # | Acceptance | Plan how |
|---|------------|----------|
| 1 | Behavior-preserving extract | Pure-move private clusters; keep public `GraphGenerator(rng, difficultyLevel)` + `generate(chunkId, layout)` thin; no algorithm change |
| 2 | Console+GUI parity | **N/A** — reasoning-only host; app/client only construct + `generate` |
| 3 | `--core` exit 0 | After cuts + override lower/retarget |
| 4 | Remeasure + lower/remove overrides | `check_token_budget_kt.py --files <touched>`; never raise; no override on Added |
| 5 | Retarget residual ticket → `MUD-034b` | If host still needs override |
| 6 | New `.kt` meet global E | tok 2500 / LOC 1100 / fn tok 250; fragment or residual host |
| 7 | No unauthorized `src/test/**` | Prefer none; existing `GraphGeneratorTest` covers API |

---

## 2. Current inventory

| path | file_tokens | file_loc | override tok_E | fn_E | ticket |
|------|------------:|---------:|---------------:|-----:|--------|
| `reasoning/.../worldgen/GraphGenerator.kt` | **11932** | **1058** (~1234 wc) | **11932** | **1226** | MUD-034 |

Global: file tok **E2500**, LOC **E1100**, fn tok **E250**. Ranked #2 · peak 11932 · layout/MST extract.  
Already separate: `GraphLayout.kt`, `GraphValidator.kt`. Callers: `WorldGenerator`, `WorldInitializationHelper`, `MudGameEngine`, `EngineGameClient`, tests.

**Hotspots (symbol map only):**

| lines (approx) | cluster | peak fn tok | extract |
|---------------:|---------|------------:|---------|
| 52–91 | `generate` pipeline | 301 | **stay** thin |
| 92–250 | Grid/BSP/Flood + `BSPRoom` | Flood 477, subdivide 380 | **GraphLayoutNodes** |
| 252–447 | Kruskal + loops + path | kruskal 356 | **GraphMst** |
| 448–1068 | edges + directions | fixBidi 1226, uniqueBidi 1161, buildNodeEdges 947 | **GraphEdgeDirections** |
| 1069–1234 | types + BFS + hidden | types 908, hidden 376 | **GraphNodeTyping** |

**FN cliff:** many private fns ≫ **FN_E 250**. Added files cannot use overrides → same trap as 034a `processIntent`.

---

## 3. Recommended extract approach

Same package `com.jcraw.mud.reasoning.worldgen`. Pass `Random` / `difficultyLevel`. Preserve RNG call order (seed determinism). Public API unchanged.

**Order (layout/MST first, then bulk):**

1. **`GraphLayoutNodes.kt`** — grid/BSP/flood (+ `BSPRoom`). Flood/subdivide >250 → pure-split helpers **or** leave residual on host.
2. **`GraphMst.kt`** — `kruskalMST`, `Edge`, distance, `addLoopEdges`, adjacency/shortestPath. Kruskal 356 → same rule.
3. **`GraphEdgeDirections.kt`** — `buildNodeEdges` through direction helpers + companion dir buckets / `DirectionBucket` / `NeighborContext`. Fragment peaks to ≤250 **or** residual host (prefer fragment so host can hit file E).
4. **`GraphNodeTyping.kt`** — `assignNodeTypes`, BFS/boundary, `markHiddenEdges`.
5. Thin host: `generate` only sequences layout → MST → loops → edges → types → hidden.
6. Remeasure each cut; stop at host ≤ global file E **or** residual override = measured + `ticket: MUD-034b`.

**Do not:** change algorithms, hidden %, directions, ctor/`generate` signature; re-split `GraphLayout`/`GraphValidator` (import-only OK).

---

## 4. Files / override edit plan

**Edit:** `GraphGenerator.kt` (thin); `config/quality/token_budget_kt.json` **only** GraphGenerator row — **lower** file/fn error (structure if still high) to measured; `ticket: "MUD-034b"`; **remove** if under global E.

**Create (no override rows; names flexible):** `GraphLayoutNodes.kt`, `GraphMst.kt`, `GraphEdgeDirections.kt`, `GraphNodeTyping.kt`.

**Do not touch:** 034a/c–n hosts, app/client handlers, other overrides, raise any cap, unauthorized tests.

---

## 5. Specs / docs

None beyond `docs/TOKEN_BUDGET_KT.md` lower-only rules. Optional closeout pointer post-impl only.

---

## 6. Tests / verify

```bash
python3 tools/quality/check_token_budget_kt.py \
  --files reasoning/src/main/kotlin/com/jcraw/mud/reasoning/worldgen/GraphGenerator.kt \
          reasoning/src/main/kotlin/com/jcraw/mud/reasoning/worldgen/GraphLayoutNodes.kt \
          reasoning/src/main/kotlin/com/jcraw/mud/reasoning/worldgen/GraphMst.kt \
          reasoning/src/main/kotlin/com/jcraw/mud/reasoning/worldgen/GraphEdgeDirections.kt \
          reasoning/src/main/kotlin/com/jcraw/mud/reasoning/worldgen/GraphNodeTyping.kt \
  --json-out tmp/workers/MUD-034b/token_remeasure.json

./tools/verify_mud.sh --core
```

`:reasoning` worldgen tests via `--core`. New tests only if forced → ticket scope + `MUD_ALLOW_TEST_CHANGES=1 ./tools/test_lock.sh --write`. Optional same-seed topology smoke if drift suspected.

---

## 7. Ordered impl steps

1. Baseline host + callers + override row  
2. Extract layouts → compile + remeasure  
3. Extract MST/loops → remeasure  
4. Extract directions (fragment FN peaks) → remeasure  
5. Extract typing/hidden → remeasure  
6. Lower/remove override; retarget `MUD-034b` if residual  
7. `--core` → 0; CLOSEOUT + cite `tmp/dod-summary.json`  
8. Ticket `done` + BOARD (impl session — not this turn)

---

## 8. Out-of-scope

Raise caps · mass detekt · PIT 80% (035) · 036–038 · other gods (034c–n; WorldGenerator → **034g**) · features · git commit/push · unauthorized test edits

---

## 9. Risks

| Risk | Mitigation |
|------|------------|
| Seed/RNG order drift | Pure moves; preserve `rng`/shuffle order |
| New-file TOKEN_FN_E | Pure-fragment **or** residual host (034a pattern) |
| Override raise / Added row | Lower/remove/retarget host only; remeasure first |
| Serial tree | One builder; do not reopen 034a or start 034c |
| Layout/MST alone still > E | Expected — continue directions/typing |
| Untracked `.kt` vs git-diff | Always `--files`; stage before final verify |

**Handoff:** plan only — **no implementation this session.** Fresh impl after Astra/Jason APPROVED.
