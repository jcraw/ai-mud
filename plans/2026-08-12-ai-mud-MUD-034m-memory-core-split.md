# MUD-034m Plan — Memory repos + core gods split (Wave Q3)

**Ticket:** MUD-034m · implementing (APPROVED) · grok  
**Status:** APPROVED by Astra 2026-08-12 08:53 MST → fresh IMPL session
**Paths:** `plans/2026-08-12-ai-mud-MUD-034m-memory-core-split.md` · `tmp/workers/MUD-034m/PLAN.md`  
**Depends:** MUD-034, MUD-031 · 034l done (no reopen 034a–l) · not 034n / 035–038  
**Verify (post-impl):** `./tools/verify_mud.sh --core` · pure moves  
**Pattern:** 034l thin facades · same-package · stable public FQCN

---

## 1. Goal / acceptance

| # | Acceptance | How |
|---|------------|-----|
| 1 | Extract 7 hosts | Pure-move; thin public entrypoints; no features |
| 2 | Console+GUI parity | **N/A** — memory/core; no app/client pairs in family |
| 3 | `--core` = 0 | After cuts + override edit |
| 4 | Remeasure; lower/remove overrides | never raise; no Added override |
| 5 | Residual ticket → `MUD-034m` | If still needed |
| 6 | New `.kt` ≤ global E | 2500 / 1100 / fn 250; fragment if needed |
| 7 | No unauthorized tests | Prefer none |

Repos first (ticket: easier). WorldState/CombatComponent last (public API).

---

## 2. Inventory

| path | file_tok | loc | ov file_E | peak FN (ov fn_E) |
|------|--------:|----:|----------:|------------------:|
| `memory/.../skill/SQLiteSkillRepository.kt` | **2564** | 252 | 2564 | 377 |
| `memory/.../item/SQLiteItemRepository.kt` | **3215** | 293 | 3215 | 336 |
| `memory/.../combat/SQLiteCombatRepository.kt` | **2641** | 245 | 2641 | 400 |
| `memory/.../world/WorldDatabase.kt` | **2506** | 237 | 2506 | **1972** (`initializeSchema`) |
| `memory/.../combat/NarrationVariantGenerator.kt` | **2967** | 290 | 2967 | **623** (`generateSingleNarrative`) |
| `core/.../WorldState.kt` | **3762** | 337 | 3762 | 309 (`movePlayerV3` / `removeDroppedItem`) |
| `core/.../CombatComponent.kt` | **2599** | 277 | 2599 | **456** (`applyStatus`) |

Overrides all `ticket: MUD-034`. Global E: **2500 / 1100 / 250**.

**Public keep (FQCN + signatures):**
- `SQLiteSkillRepository` — all 10 `SkillRepository` overrides
- `SQLiteItemRepository` — all 12 `ItemRepository` overrides
- `SQLiteCombatRepository` — all 11 `CombatRepository` overrides (stubs `findActiveThreats` / `findCombatantsInRoom` stay empty)
- `WorldDatabase` — ctor, `getConnection`, `close`, `clearAll`
- `NarrationVariantGenerator` — ctor, `generateAllVariants`
- `WorldState` data class + **member** methods (player/quest/time, space/node/chunk, nav, entity CRUD, `removeDroppedItem`). Not extensions — callers + `WorldStateTest` use members.
- `CombatComponent` data class + companion `calculateMaxHp`/`create` + members + same-pkg `CombatPosition` / `EffectApplication` / `EffectResult`

**Callers (do not rewire):** `MudGameEngine` · `App.kt` · `EngineGameClient` · `PersistenceManager` · existing tests (untouched). Interfaces in `core.repository.*` stay.

**Parity:** N/A. State in CLOSEOUT.

---

## 3. Design / approach

Same-package flat (`memory.skill` / `memory.item` / `memory.combat` / `memory.world` / `core`); pure-move; fragment FN>250; no engine rewire. One ticket; stages serial. Hosts = thin delegates.

### A. Repos (stage 1 — prefer)

| # | extract | notes |
|--:|---------|-------|
| 1 | `SkillRepoQueries` · `SkillRepoWrites` · `SkillRepoEvents` | queries / save-xp-unlock-delete / log+history; share `SkillRepoMapping` if row decode repeats |
| 2 | `ItemRepoTemplates` · `ItemRepoInstances` | template CRUD vs instance CRUD; mapping helper if needed |
| 3 | `CombatRepoComponents` · `CombatRepoEffects` · `CombatRepoEvents` | CRUD+findAll / apply-remove-`saveStatusEffects` / log+history |
| 4 | thin repo hosts | overrides → extracts; stubs stay on combat host |

### B. WorldDatabase (stage 2)

`initializeSchema` **1972** — must fragment (DDL tokens). Orchestrator stays private on host.

| # | extract |
|--:|---------|
| 5 | `WorldSchemaSeedChunks` (seed+ALTER, chunks+ALTER) |
| 6 | `WorldSchemaGraphSpaces` (graph_nodes, space_properties+ALTERs, space_entities) |
| 7 | `WorldSchemaRespawnCorpse` · `WorldSchemaTreasure` (treasure+pedestals+indices) |

Each `apply(stmt)` ≤ fn 250; split table if not.

### C. Narration (stage 3)

| # | extract |
|--:|---------|
| 8 | `NarrationVariantSupport` — `generateSingleNarrative` + fallback; **fragment prompt vs chat** if FN>250 |
| 9 | `NarrationMeleeVariants` · `NarrationRangedVariants` · `NarrationSpellCritVariants` · `NarrationStatusDeathVariants` |
| 10 | thin host `generateAllVariants` → those |

Pass `llmClient`/`memoryManager` into extracts (same ctor deps).

### D. Core (stage 4 — careful)

Keep **members** as one-liners: `fun movePlayerV3(...) = WorldStateNav.move(this, ...)`.

| # | extract | notes |
|--:|---------|-------|
| 11 | `WorldStateNav` | `movePlayerV3` (both), `movePlayerByExit` (both), `getAvailableExitsV3` (both) |
| 12 | `WorldStateEntities` | entity CRUD + space membership + `replaceEntityInSpace` |
| 13 | `WorldStateItems` | `removeDroppedItem` (gold/corpse order exact) |
| 14 | host keeps | data fields, player/quest/time, space/node/chunk/treasure one-liners |
| 15 | `CombatStatusOps` | `applyStatus` (peak 456 — fragment DOT/buff/single if needed) + remove/has/magnitude |
| 16 | `CombatTickOps` | `tickEffects` |
| 17 | host keeps | companion, damage/heal/timer/alive/hp%, nested types (same pkg if moved) |

Do **not** implement combat-repo TODOs. Do **not** change `@Serializable` shape.

Prefer **remove** all 7 overrides if ≤E; else **lower** + `ticket: MUD-034m`. Never raise; no Added override.

---

## 4. Files to create/touch

**Edit:** 7 hosts; `token_budget_kt.json` only those 7 rows.

**Create (~18–24 `.kt` ≤E):** repo query/write/event(+mapping) · WorldSchema* · Narration*Variants + Support · WorldStateNav/Entities/Items · CombatStatusOps/TickOps.

**Not:** Skill/Item/CombatDatabase · `core.repository.*` interfaces · app/client handlers · 034a–l/n · mass detekt · `src/test/**` · other overrides.

---

## 5. Non-goals

Raise caps · Added overrides · mass detekt · PIT 80% · 036–038 · outside family · reopen 034a–l · 034n · fill combat-repo stubs · WorldState field/API change · features · commit/push unless Jason asks.

---

## 6. Acceptance checklist (impl)

- [ ] 7 hosts thin; bodies in same-package extracts
- [ ] Parity N/A (memory/core) — CLOSEOUT states it
- [ ] Public FQCN/signatures unchanged (WorldState/CombatComponent **members**)
- [ ] Combat stubs remain empty TODOs
- [ ] `./tools/verify_mud.sh --core` = 0
- [ ] Remeasure → `tmp/workers/MUD-034m/token_remeasure.json`
- [ ] Overrides removed or lowered+`MUD-034m`; never raised; no Added
- [ ] New `.kt` ≤E (file/fn)
- [ ] No unauthorized `src/test/**`
- [ ] CLOSEOUT: paths, tokens before/after, residual risk

---

## 7. Ordered impl steps

1. Baseline → `tmp/workers/MUD-034m/token_baseline.json` (`check_token_budget_kt.py --files` 7 hosts)
2. Skill → Item → Combat repo extracts; thin hosts
3. WorldDatabase schema fragments; thin `initializeSchema`
4. Narration support + scenario clusters; thin orchestrator
5. WorldState Nav/Entities/Items; member delegates
6. CombatStatusOps + CombatTickOps; member delegates
7. Remeasure; override remove or lower+`MUD-034m`
8. `--core` (N≤3 flaky → escalate)
9. CLOSEOUT + ticket/board done (**fresh impl**, post-APPROVED)

---

## 8. Risks

| Risk | Mitigation |
|------|------------|
| WorldState member→extension breaks callers/tests | Keep members; delegate only |
| `removeDroppedItem` / nav condition order | Pure-move; same early-returns |
| `initializeSchema` FN still >250 | One table per fn if needed |
| `generateSingleNarrative` 623 | Split prompt builder vs LLM call |
| `applyStatus` when-branches | Fragment by effect family |
| Accidental stub fill / interface edit | Explicit non-goal |
| Override raise / wrong ticket | Remeasure; lower-only; retarget 034m |
| Detekt ID shift | Suppress carry; no mass regen |
| Serial tree | One builder; no 034n |

---

**Handoff:** APPROVED by Astra 2026-08-12 08:53 MST. Fresh IMPL authorized — execute plan; do not re-plan unless blocked. Do not resume plan session.
