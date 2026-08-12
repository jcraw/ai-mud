# MUD-034j Plan — Skill data/mgr split (Wave Q3)

**Ticket:** MUD-034j · plan_review · grok  
**Status:** APPROVED by Astra 2026-08-12 07:35 MST → fresh IMPL session
**Plan/mirror:** `plans/2026-08-12-ai-mud-MUD-034j-skill-data-mgr-split.md` · `tmp/workers/MUD-034j/PLAN.md`  
**Depends:** MUD-034, MUD-031 · **034a–i done** · pattern `tmp/workers/MUD-034i/CLOSEOUT.md`  
**Verify (post-impl):** `./tools/verify_mud.sh --core` · pure moves; no features  
**Baseline:** `tmp/workers/MUD-034j/token_baseline.json`  
**Global E:** file tok **2500** / LOC **1100** / fn tok **250**

---

## 1. Goal / acceptance

| # | Acceptance | How |
|---|------------|-----|
| 1 | Extract 3 hosts, behavior-preserving | Pure-move data tables + manager apply; thin public entrypoints |
| 2 | Console+GUI parity where pairs exist | **N/A handlers** — keep `SkillManager` / `SkillDefinitions` / `PerkDefinitions` / `StarterSkillSets` API stable |
| 3 | `--core` = 0 | After cuts + override edit |
| 4 | Remeasure; lower/remove overrides | `--files <touched>`; never raise; no Added override |
| 5 | Residual ticket → `MUD-034j` | If host still needs override |
| 6 | New `.kt` ≤ global E | tok 2500 / LOC 1100 / fn 250; fragment if needed |
| 7 | No unauthorized tests | Prefer none |

---

## 2. Inventory

| path | file_tok | loc* | ov file_E | ov peak fn_E | ticket |
|------|--------:|-----:|----------:|-------------:|--------|
| `reasoning/.../skill/PerkDefinitions.kt` | **7304** | 747 | 7304 | (fn E 250) | MUD-034 |
| `reasoning/.../skill/SkillDefinitions.kt` | **4419** | 490 | 4419 | (fn E 250) | MUD-034 |
| `reasoning/.../skill/SkillManager.kt` | **5594** | 510 | 5594 | **grantXp 1143** | MUD-034 |

\*token-tool LOC (wc ≈ 774/517/585). Ranked #11/#20/#15 · note: **data vs logic**.

**Manager peak FN tok:** `checkSkill` ~1593 · `grantXp` ~1143 · `attemptSkillProgress` ~979 · `unlockSkill` ~931 — all ≫250 → fragment on extract.

**Public keep:**  
- `PerkDefinitions`: `getPerkChoices` / `hasPerks` / `getMilestoneCount` (21 trees; caller `PerkSelector`)  
- `SkillDefinition` + `SkillDefinitions` (`allSkills`, getters, `getSkill`/`skillExists`/`getSkillCount`/`getSkillsByTag`) + `StarterSkillSets`  
- `SkillManager` class name + ctor (`internal skillRepo`, componentRepo, memory?, rng) + `grantXp` / `attemptSkillProgress` / `unlockSkill` / `checkSkill` / component CRUD / `recallSkillHistory` / `getSkillComponentRepository`  
- Construct sites (engine/client/app/testbot) unchanged  

**Override rows only:** 3 paths in `config/quality/token_budget_kt.json` (`ticket: MUD-034` today).

---

## 3. Design / approach

**Axis:** **data tables vs manager/apply logic** — catalogs never absorb progression; manager never absorbs perk/skill tables.

**Principles:** package `com.jcraw.mud.reasoning.skill`; pure-move; **same type names** (zero import churn); new extracts ≤ global E (**no grandfather**); fragment FN>250; no features/balance/new perks; no app/client handlers.

### A. Data — PerkDefinitions (7304)

| extract (names flexible) | ~tok | content |
|--------------------------|-----:|---------|
| `PerkTreesCombat.kt` | ~1717 | COMBAT + ARMOR |
| `PerkTreesRogue.kt` | ~953 | ROGUE |
| `PerkTreesElemental.kt` | ~1469 | ELEMENTAL |
| `PerkTreesResource.kt` | ~1265 | RESOURCE + RESISTANCE (split if >E) |
| `PerkTreesCoreStats.kt` | ~1553 | CORE STATS |
| Host facade | tiny | merge partial maps → `perkTrees`; keep 3 public funs |

`internal object` map holders; sole public entry remains `object PerkDefinitions`.

### B. Data — SkillDefinitions (4419)

| extract | ~tok | content |
|---------|-----:|---------|
| `SkillDefinition.kt` | small | data class |
| `SkillCatalogCoreCombat.kt` | ~1182 | core + combat |
| `SkillCatalogRogueMagic.kt` | ~1178 | rogue + elemental + advanced |
| `SkillCatalogResourceOther.kt` | ~1058 | resource + resistance + other |
| `StarterSkillSets.kt` | ~607 | whole object |
| Host facade | thin | `allSkills` + getters |

### C. Logic — SkillManager (5594)

Package apply objects with **explicit deps** (repos, memory, rng, host helpers):

1. XP/lucky — `grantXp` + `attemptSkillProgress` + `calculateLuckyChance` (**multi-file**; peaks ≫250)  
2. Unlock — `unlockSkill` fragment  
3. Check — locked / opposed / regular paths (**multi-file**; peak ~1593)  
4. Host — ctor + public one-liners + small component/repo/history  

Shared micro-helper OK for duplicated event-log + optional `runBlocking` memory write (preserve order). Keep `internal val skillRepo`.

**Cut order:** Perk data → Skill data → SkillManager. Prefer **remove** overrides if ≤E; else **lower** + `ticket: MUD-034j`.

**Parity:** API stability only (no handler pair work this ticket).

---

## 4. Files to create/touch

**Edit:** 3 hosts; `token_budget_kt.json` **only** those 3 rows (lower/remove + retarget).

**Create (~12–18 `.kt` under `skill/`, ≤E each):** PerkTrees* · SkillDefinition + SkillCatalog* + StarterSkillSets · SkillManager* fragments.

**Not:** app/client handlers · PerkSelector rewrites · 034a–i/k–n · mass detekt · `src/test/**` · other overrides · gameplay redesign. **Stage** new `.kt` for hard-on-touched.

---

## 5. Non-goals

Raise caps · Added overrides · mass detekt · PIT 80% (035) · 036–038 · outside family · reopen 034a–i · 034k–n · features/balance · commit/push unless Jason asks.

---

## 6. Acceptance checklist (impl)

- [ ] Hosts = thin public entrypoints; bodies in extracts  
- [ ] Data vs logic boundary held  
- [ ] Public names/signatures stable  
- [ ] `./tools/verify_mud.sh --core` = 0  
- [ ] Remeasure → `tmp/workers/MUD-034j/token_remeasure.json`  
- [ ] Overrides removed or lowered + `MUD-034j`; never raised; no Added  
- [ ] New `.kt` file_tok≤2500, peak fn≤250  
- [ ] No unauthorized `src/test/**`  
- [ ] CLOSEOUT: paths, before/after tokens, residual risk  

---

## 7. Ordered impl steps

1. Confirm baseline; optional dry remeasure  
2. PerkDefinitions category maps → facade → `:reasoning` compile  
3. SkillDefinitions data class + catalogs + move StarterSkillSets → facade  
4. SkillManager XP/lucky multi-file → unlock → check multi-file → thin host  
5. Remeasure; remove/lower 3 overrides; retarget residual `MUD-034j`  
6. Stage new `.kt`; `--core` (N≤3 flaky then escalate)  
7. CLOSEOUT + ticket/board done (**fresh impl session**, post-APPROVED)

---

## 8. Risks

| Risk | Mitigation |
|------|------------|
| FN_E on Added manager extracts | Multi-file fragment; shared log helper; no Added override |
| Missing map key / allSkills size | Pure-move; verify 21 perk keys + catalog union |
| `internal skillRepo` / DispositionManager | Keep field on host class |
| Memory `runBlocking` order | Preserve log → memory sequence per path |
| Detekt on moved code | Carry suppress; no mass baseline |
| Locked tests on object names | Do not rename public objects |

---

**Handoff:** APPROVED by Astra 2026-08-12 07:35 MST. Fresh IMPL authorized — execute plan; do not re-plan unless blocked.
