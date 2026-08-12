# MUD-034g Plan — World gen cluster split (Wave Q3)

**Ticket:** MUD-034g · **Phase:** plan_review · **Worker:** grok  
**Status: APPROVED by Astra 2026-08-12 06:07 MST**
**Prior:** PLAN ONLY — not impl-approved  
**Plan / mirror:** `plans/2026-08-12-ai-mud-MUD-034g-world-gen-cluster-split.md` · `tmp/workers/MUD-034g/PLAN.md`  
**Depends:** MUD-034, MUD-031 · **Parent:** MUD-034 · **Siblings:** 034a–f done (no reopen); 034h–n open (no touch)  
**Verify (post-impl):** `./tools/verify_mud.sh --core`  
**Pattern:** pure moves / thin entrypoints (034b; 034f residual-FN). No features.

---

## 1. Goal / acceptance map

| # | Acceptance | Plan how |
|---|------------|----------|
| 1 | Behavior-preserving extract | Pure-move private clusters; hosts keep public ctors + entry funs; no algo/RNG/pipeline change |
| 2 | Console+GUI parity | **N/A** — reasoning `world/` only; app/client construct/call only (**do not edit** app/client) |
| 3 | `--core` exit 0 | After cuts + override lower/retarget |
| 4 | Remeasure + lower/remove overrides | `check_token_budget_kt.py --files <touched>`; never raise; **no** Added override |
| 5 | Residual `ticket` → `MUD-034g` | Host rows only when still needed |
| 6 | New `.kt` meet global E | tok **2500** / LOC **1100** / fn **250**; fragment or residual host |
| 7 | No unauthorized `src/test/**` | Prefer none; lock + allow if scoped |

---

## 2. Inventory

Pkg: `reasoning/.../world/` (flat). Global E: file **2500**, fn **250**, LOC **1100**. Peak **7653**.

| host | file_tok | LOC | file_E | fn_E | ticket |
|------|---------:|----:|-------:|-----:|--------|
| `WorldGenerator.kt` | **7653** | 664 | 7653 | 1020 | MUD-034 |
| `DungeonInitializer.kt` | **6327** | 529 | 6327 | 930 | MUD-034 |
| `TownGenerator.kt` | **3906** | 361 | 3906 | 1164 | MUD-034 |
| `ExitLinker.kt` | **3316** | 297 | 3316 | 250 | MUD-034 |
| `MobSpawner.kt` | **3016** | 288 | 3016 | 1130 | MUD-034 |
| `HiddenExitPlacer.kt` | **2894** | 240 | 2894 | 1544 | MUD-034 |
| `ExitResolver.kt` | **2597** | 256 | 2597 | 423 | MUD-034 |

**Deps:** WG/ER/MS leaf; EL→WG; TG→WG; HEP→WG; DI→WG+TG+HEP.  
**Callers (construct only):** `WorldInitializationHelper`, `MudGameEngine`, `EngineGameClient`. Keep public FQCN → no caller edits.

**Hotspots (span only):**

| host | public stay thin | extract clusters (~lines) |
|------|------------------|---------------------------|
| WG | chunk/space/stub/fill, trap, resource | topology; biome/theme; LLM chunk/space; fill/props; cache; models |
| DI | contract + `initializeDeep*` / `initializeAncientAbyss` | start; town; combat; linkTown; boss |
| TG | `generateTownSubzone`, `populateTownSpace` | 4 merchants + itemInstance |
| EL | `linkExits`, `createReciprocalExit` | vertical; adj; within; reciprocal/NL |
| MS | `spawnEntities`, respawn*, open class | combat comps; LLM spawn; fallback |
| HEP | place / surface / discover / hint | **place ~152 (fn cliff)**; surface |
| ER | `resolve`, visible/describe + `ResolveResult` | phase1–3; conditions; levenshtein |

---

## 3. Extract approach

- Same package `com.jcraw.mud.reasoning.world` (034b). Subdir optional only if file-count hygiene needs it.
- Pure-move; hosts = thin facades. Preserve **RNG order**, LLM prompt order, pipeline steps, public signatures.
- **FN_E:** Added cannot grandfather. Peaks (HEP 1544, MS 1130, TG 1164, WG 1020) → fragment ≤250 **or** residual host + lower override (034f). Prefer fragment for file_E burn-down.
- Detekt: `@file:Suppress` on pure-moved smells; no mass baseline regen.

**Cut order (leaf → orchestrator; remeasure each):**

1. **ExitResolver** — phases + levenshtein (+ conditions).
2. **MobSpawner** — LLM / fallback / combat-components.
3. **WorldGenerator** — topology · biome · LLM chunk/space · fill/props · trap/resource · models.
4. **ExitLinker** — vertical / adj / within / reciprocal.
5. **TownGenerator** — merchant pack(s).
6. **HiddenExitPlacer** — fragment `placeHiddenExit` first; surface second.
7. **DungeonInitializer** last — start/town/combat/link/boss stages; keep contract + two initialize*.

Extract names flexible (`WorldGeneratorTopology.kt`, `MobSpawnerLlm.kt`, …). **No** override on new files.

**Do not:** change algorithms, seed/RNG, public API, app/client/testbot, non-family hosts.

---

## 4. Files + override edit plan

**Edit:** seven hosts (thin). **Create:** extracts under `reasoning/.../world/`.  
**Config:** `config/quality/token_budget_kt.json` — **only** those seven rows: after remeasure **lower** or **remove**; residual `ticket: "MUD-034g"`; never raise; no Added rows; no other families.

**Do not touch:** 034a–f, 034h–n, app/client, unauthorized tests, commit/push.

---

## 5. Specs / docs

None. Optional CLOSEOUT post-impl only.

---

## 6. Tests / verify / remeasure

```bash
python3 tools/quality/check_token_budget_kt.py \
  --files reasoning/src/main/kotlin/com/jcraw/mud/reasoning/world/WorldGenerator.kt \
          reasoning/src/main/kotlin/com/jcraw/mud/reasoning/world/DungeonInitializer.kt \
          reasoning/src/main/kotlin/com/jcraw/mud/reasoning/world/TownGenerator.kt \
          reasoning/src/main/kotlin/com/jcraw/mud/reasoning/world/ExitLinker.kt \
          reasoning/src/main/kotlin/com/jcraw/mud/reasoning/world/MobSpawner.kt \
          reasoning/src/main/kotlin/com/jcraw/mud/reasoning/world/HiddenExitPlacer.kt \
          reasoning/src/main/kotlin/com/jcraw/mud/reasoning/world/ExitResolver.kt \
  --json-out tmp/workers/MUD-034g/token_baseline.json
# after cuts: same + new extract paths → token_remeasure.json
./tools/verify_mud.sh --core   # exit 0
```

Prefer no new tests. Forced test edits → scope + `MUD_ALLOW_TEST_CHANGES=1 ./tools/test_lock.sh --write`. Stage new `.kt`; untracked need `--files`.

---

## 7. Ordered impl steps

1. Baseline seven hosts → `token_baseline.json`  
2–8. Cut ER → MS → WG → EL → TG → HEP → DI; remeasure each  
9. Lower/remove overrides; residual `MUD-034g` only  
10. `--core` → 0; CLOSEOUT + `tmp/dod-summary.json`  
11. Ticket `done` + BOARD (impl session)

---

## 8. Out-of-scope

Raise caps · mass detekt · PIT 80% (035) · 036–038 · other families · algo/RNG/behavior · app/client/testbot · unauthorized tests · commit/push · product `*.kt` this session

---

## 9. Risks

| Risk | Mitigation |
|------|------------|
| RNG / LLM order drift | Pure moves; preserve facade call sequence |
| DI pipeline order | Thin `initialize*` keeps stage order |
| FN_E on Added | Fragment ≤250 or residual host (034f) |
| Override raise / Added row | Remeasure first; host-only; never raise |
| Cross-host shared types | Same package/FQCN; no caller churn |
| Residual FILE_E | Accept lowered MUD-034g residual |
| Serial tree | One builder; no 034a–f reopen / no 034h–n |
| Untracked vs git-diff | `--files` + stage before final verify |

**Handoff:** plan only — **STOP. No implementation.** Fresh impl after Astra/Jason **APPROVED**.
