# Property-based testing (PBT)

MUD-015 introduces Kotest property checks on pure `:core` hot paths only.

## Scope

| In scope | Out of scope |
|----------|----------------|
| `:core` `GraphNodeComponent` edge/degree/validate laws | `reasoning` combat math (`DamageCalculator`, `AttackResolver`) |
| `:core` `CombatComponent` HP clamp / maxHp laws | Live LLM / testbot |
| Fixed-seed `checkAll` in JUnit5 `@Test` | Statistical / distribution soft props |
| `testImplementation` of `kotest-property` on **`:core` only** | Full Kotest style runner (optional later) |

Example (example-based) suites stay: `CombatComponentTest`, `GraphNodeComponentTest`. PBT **adds** laws; it does not replace or weaken them.

## Seed policy

- Every property uses `PropTestConfig(seed = fixed, iterations = 100)` (see test companions: `PBT_SEED = 15_015L`).
- Same seed → same counterexamples for CI and local repro.
- Do **not** use unseeded `Random.Default` inside property bodies.
- Keep iterations modest (50–200) so `--core` stays fast.

## Law vs soft

### Law (hard fail)

| ID | Surface | Property |
|----|---------|----------|
| **G1** | Graph | `degree() == neighbors.size` |
| **G2** | Graph | Successful `addEdge` (unique target+dir) → degree +1; original unchanged |
| **G3** | Graph | Well-formed node (non-blank id/chunk, type-consistent degree, no dups) → `validate()==true` |
| **G4** | Graph | DeadEnd/Linear/Hub degree rule violations → `validate()==false` |
| **S2** | Graph | `removeEdge`: missing target → `IllegalArgumentException`; present → degree decreases |
| **C1** | Combat | `amount ≥ 0` ⇒ `applyDamage` HP ∈ `[0, currentHp]`, never negative |
| **C2** | Combat | `amount ≥ 0` ⇒ `heal` HP ∈ `[currentHp, maxHp]` |
| **C3** | Combat | `calculateMaxHp(skills=null \| non-neg stubs, itemHpBonus)` ≥ 10 |

### Soft (document only / excluded)

| ID | Note |
|----|------|
| **S1** | No distribution / variance checks (avoid flaky stats). |
| **S2 note** | Do **not** claim `removeEdge` is idempotent — product **requires** edge present (throws). |
| **S3** | Negative `amount` to `applyDamage`/`heal` is **undefined** today — generators use non-negative only. |

## Wiring

- Catalog: `kotest = "5.9.1"` → `libs.kotestProperty` (`io.kotest:kotest-property-jvm`).
- Module: `core/build.gradle.kts` `testImplementation(libs.kotestProperty)` only.
- Style: property-in-JUnit5 via `runBlocking { checkAll(PropTestConfig(...)) { ... } }` (kotlin-test assertions).

## Test-lock

New/changed `*/src/test/**` require ticket scope and:

```bash
MUD_ALLOW_TEST_CHANGES=1 ./tools/test_lock.sh --write
```

See `docs/TEST_LOCK.md`.

## Verify

```bash
./gradlew :core:test --tests '*PropertyTest*'
./tools/verify_mud.sh --core
```
