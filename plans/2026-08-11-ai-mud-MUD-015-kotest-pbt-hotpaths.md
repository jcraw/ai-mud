# MUD-015 Plan — Kotest PBT on combat/graph hot paths

**Ticket:** MUD-015 · **Worker:** grok · **Phase:** plan_review  
**Impl = fresh session** after Astra approve. Do not resume plan session for product/test edits.  
**Plan path:** `plans/2026-08-11-ai-mud-MUD-015-kotest-pbt-hotpaths.md`  
**Worker mirror:** `tmp/workers/MUD-015/PLAN.md`  
**Post-impl verify:** `./tools/verify_mud.sh --core`  
**Jason domain pick:** **not required** — pure `:core` surfaces exist; Astra common-sense approve OK.

---

## 1. Goal / acceptance mapping

| # | Acceptance | Impl delivers |
|---|------------|---------------|
| 1 | Kotest property tests on pure hot paths (combat and/or graph) | **Both domains in `:core` only:** (A) graph edge/degree/validate laws · (B) combat HP clamp / maxHp laws — ~6–8 properties total |
| 2 | No live LLM; deterministic seeds | No reasoning/LLM/testbot; `PropTestConfig(seed = fixed)` on every `checkAll` |
| 3 | Test-lock escape only with ticket scope | New `src/test` files authorized by this ticket; `MUD_ALLOW_TEST_CHANGES=1 ./tools/test_lock.sh --write` after tests land |
| 4 | Law vs soft documented | Short § in `docs/PBT.md` + property names in test files tagged law/soft |
| 5 | Jason opinion if domain ambiguous | **Skip pause** — inventory chose pure core; escalate only if impl finds contract mismatch requiring product design change |

---

## 2. Current inventory

| Item | Truth |
|------|--------|
| **Kotest** | **None.** Catalog: detekt/konsist/pitest only. `core/build.gradle.kts`: `kotlin("test")` + konsist. JUnit Platform via `kotlin-jvm` convention. |
| **(A) Graph pure** | `GraphNodeComponent` — immutable `addEdge`/`removeEdge`/`removeEdgeByDirection`, `degree()=neighbors.size`, `validate()` (blank id/chunk, DeadEnd deg=1, Linear=2, Hub≥3, no dup target+dir). `EdgeData` pure data. |
| **(B) Combat pure** | `CombatComponent.applyDamage` → `coerceAtLeast(0)`; `heal` → `coerceAtMost(maxHp)`; `calculateMaxHp` → min 10. `DamageType` enum only (no math). `DamageType` unused in applyDamage body today. |
| **reasoning combat** | `DamageCalculator` / `AttackResolver`: `Random`, `WorldState`, equipment maps, suspend — **not** day-one PBT. Seeded Random exists but fixtures heavy; **out of scope**. |
| **Example tests** | `CombatComponentTest` (~574 lines, JUnit5) covers clamp examples. `GraphNodeComponentTest` (~651 lines) covers add/remove/validate examples. Keep; do not weaken. |
| **Test-lock** | Hard on core lane. New/changed `src/test` need env-gated `--write` + commit manifest with tests. |
| **DIGEST** | Pointer only (gate #7 / 60d). |

---

## 3. Design / recommended approach

### Chosen domains (lock for Astra)
1. **Graph invariants** (`GraphNodeComponent` + `EdgeData`) — pure core  
2. **Combat HP / maxHp clamps** (`CombatComponent`) — pure core  

**Not chosen:** reasoning `DamageCalculator`/`AttackResolver` (impure/random/world). Revisit only if core properties ship and a later ticket wants seeded combat math.

### Law vs soft (assert **existing** contracts; no product rewrites)

**Law (hard fail):**
| ID | Property |
|----|----------|
| G1 | `degree() == neighbors.size` for any generated node |
| G2 | Successful `addEdge` (unique target+dir) → degree +1; original unchanged (immutability) |
| G3 | After well-formed construction (non-blank id/chunk, type-consistent degree, no dups) → `validate()==true` |
| G4 | `validate()==false` when DeadEnd/Linear/Hub degree rules violated (typed generators) |
| C1 | `amount ≥ 0` ⇒ `applyDamage(amount).currentHp ∈ [0, currentHp]` and never negative |
| C2 | `amount ≥ 0` ⇒ `heal(amount).currentHp ∈ [currentHp, maxHp]` (cap at max) |
| C3 | `calculateMaxHp(skills=null, itemHpBonus ∈ arb)` ≥ 10; with non-neg skill stubs if used |

**Soft (document only / optional weak asserts — avoid flaky stats):**
| ID | Note |
|----|------|
| S1 | No distribution/variance checks |
| S2 | Do **not** claim `removeEdge` idempotent — prod **requires** edge present (throws). Law = missing → `IllegalArgumentException`; present → degree decreases |
| S3 | Negative `amount` to applyDamage/heal is **undefined contract** today (can raise/lower HP oddly) — **exclude from generators** (`Arb.nonNegativeInt`) rather than “fix” product |

### Kotest wiring
- Catalog: `kotest = "5.9.1"` (or latest stable 5.x at impl; pin one).
- Libraries: `kotest-property` (+ `kotest-assertions-core` if needed).  
  Prefer **property lib inside existing JUnit5 `@Test`** (`checkAll(PropTestConfig(seed=…)) { … }`) — **no** full Kotest style runner required for coexistence with kotlin-test/JUnit Platform.
- **Module:** `testImplementation` on **`:core` only**. Do not add to reasoning/testbot.
- Optional: `kotest-runner-junit5` only if plain `checkAll` integration fails under Platform — default KISS without it.

### Seed policy
- Fixed seeds in test source, e.g. `const val PBT_SEED = 0xMUD015L` (or `42L` / domain-specific constants).
- Every property: `PropTestConfig(seed = PBT_SEED, iterations = 100)` (or 50–200; keep CI fast).
- Document in `docs/PBT.md` (½ screen): seed fixed; re-run same seed for repro; no live LLM; law vs soft table pointer.
- Do **not** bloat AGENTS.md (link `docs/PBT.md` only if verify/AGENTS already points at quality docs — optional one-line under Verification is OK **only if** Astra wants; default: docs only).

### Generators (sketch)
- Graph: `Arb.string(1..12, Codepoint.alphanumeric())` for ids; `Arb.enum<NodeType>()` or constrained type+degree pairs; edges with unique (targetId, direction) sets.
- Combat: `Arb.int(1..10_000)` maxHp; `Arb.int(0..maxHp)` current; non-neg damage/heal amounts.

---

## 4. Files to create/touch

| Path | Action |
|------|--------|
| `gradle/libs.versions.toml` | Add kotest version + `kotest-property` (and assertions if used) |
| `core/build.gradle.kts` | `testImplementation(libs.kotest.property)` (catalog alias as wired) |
| `core/src/test/.../CombatComponentPropertyTest.kt` | **New** — C1–C3 |
| `core/src/test/.../GraphNodeComponentPropertyTest.kt` | **New** — G1–G4 (+ removeEdge throw/present) |
| `docs/PBT.md` | **New** — seed policy, law/soft, module scope |
| `tools/test-lock/manifest.sha256` | Regen after new tests |
| Product `src/main` | **None** (no gameplay change) |
| AGENTS.md | **Prefer no edit**; link from docs only |

---

## 5. Non-goals

- MUD-007 playtest, MUD-009 git, MUD-014 PIT, MUD-016 CI, MUD-017 quarantine, MUD-018
- Live LLM / testbot PBT; statistical flaky suites
- Rewriting combat design or making removeEdge idempotent
- Weakening/deleting example tests; assert-gaming; mass PBT monorepo
- reasoning-module property suite; commit/push this session

---

## 6. How impl confirms acceptance

- [ ] Kotest on catalog + `:core` testImplementation only  
- [ ] ≥1 property file per domain (graph + combat); total ~6–8 laws  
- [ ] All `checkAll` use fixed seed; no Random.Default without seed; no LLM  
- [ ] `docs/PBT.md` lists law vs soft  
- [ ] `MUD_ALLOW_TEST_CHANGES=1 ./tools/test_lock.sh --write` after tests; manifest dirty with tests  
- [ ] `./tools/verify_mud.sh --core` exit 0; cite `tmp/dod-summary.json`  
- [ ] Example tests still pass; product code untouched  

---

## 7. Ordered impl steps

1. Catalog + `:core` test dep (kotest-property).  
2. `GraphNodeComponentPropertyTest` — G1–G4 + removeEdge contracts.  
3. `CombatComponentPropertyTest` — C1–C3 with non-neg generators.  
4. `docs/PBT.md` (seed + law/soft).  
5. `./gradlew :core:test --tests '*PropertyTest*'` green.  
6. Test-lock `--write` with env gate.  
7. `./tools/verify_mud.sh --core` green.  
8. Closeout: paths, dod-summary, residual risk. **No commit unless drain allowlist.**

---

## 8. Risks

| Risk | Mitigation |
|------|------------|
| Impure combat in reasoning | Stay in core HP/clamp only |
| Flaky PBT | Fixed seeds; no statistical soft props; bounded iterations |
| Test-lock fail-closed | Ticket-scoped new files + env `--write` |
| Jason opinion | **Not blocking** for domain pick; only if law ≠ actual code and product must change |
| Wrong module | `:core` only; do not pull testbot/reasoning |
| Generator hits `require` on addEdge | Build unique edge sets; test throw paths as separate unit/property |
| Kotest vs JUnit Platform clash | Prefer property-in-JUnit5 first; add runner only if needed |

---

**Handoff:** Astra approve (common-sense domains locked) → **fresh** impl brief. No impl this session.


---
Status: APPROVED by Astra 2026-08-11 01:40 MST
