# Konsist architecture (MUD-011)

Package-boundary gate for AI MUD. Encodes the **current** Gradle module graph as Konsist layers so **new worse** cross-module imports fail tests.

## Paths

| Path | Role |
|------|------|
| `core/src/test/kotlin/com/jcraw/mud/architecture/ModuleBoundaryTest.kt` | Layer rules + optional residual allowlist |
| `gradle/libs.versions.toml` | Pin: `konsist = "0.17.3"` (`com.lemonappdev:konsist`) |
| `core/build.gradle.kts` | `testImplementation(libs.konsist)` only |

## Verify behavior

- **Default / fast / core / full:** `./tools/verify_mud.sh` runs hard filtered  
  `./gradlew :core:test --tests 'com.jcraw.mud.architecture.*'` after lane body (+ detekt).
- **Quarantine lane:** Konsist is **not** run (debt-only lane).
- Scope: **production** sources only (`Konsist.scopeFromProduction()`).

```bash
./tools/verify_mud.sh              # includes Konsist arch step
./tools/verify_mud.sh --core       # includes Konsist arch step
./tools/verify_mud.sh --dry-run    # prints: ./gradlew :core:test --tests 'com.jcraw.mud.architecture.*'
./gradlew :core:test --tests 'com.jcraw.mud.architecture.*'   # direct
```

## Allowed graph (declared edges)

Layers use **real** package roots (not research/sample names). Utils has no main package — no layer.

| Layer | Package | May depend on (declared) |
|-------|---------|--------------------------|
| config | `com.jcraw.mud.config..` | *(nothing)* |
| core | `com.jcraw.mud.core..` | config |
| llm | `com.jcraw.sophia.llm..` | config |
| action | `com.jcraw.mud.action..` | core |
| memory | `com.jcraw.mud.memory..` | core, llm |
| perception | `com.jcraw.mud.perception..` | core, llm |
| reasoning | `com.jcraw.mud.reasoning..` | config, core, llm, memory |
| app | `com.jcraw.app..` | core, config, perception, reasoning, memory, action, llm |
| client | `com.jcraw.mud.client..` | core, perception, reasoning, memory, llm, config, action |
| testbot | `com.jcraw.mud.testbot..` | core, config, llm, perception, reasoning, action, memory, **app** |

Notes:

- `app` is `com.jcraw.app`, not `com.jcraw.mud.app`.
- `llm` is `com.jcraw.sophia.llm`, not `com.jcraw.mud.llm`.
- `testbot → app` is unusual but real Gradle; encoded honestly.
- App/client may depend on `:utils` at the Gradle level; utils has no package root to assert.

Konsist `dependsOn` uses default `strict = false` (**may** depend). Among defined layers, imports outside the allowed set fail.

## How to add a deliberate exception

1. Prefer **fixing** the illegal import (move code, invert dependency, extract shared type to a lower layer).
2. If residual debt must stay temporarily: add a ticket-scoped entry to `ModuleBoundaryTest.ALLOWED_RESIDUALS` with a comment:
   ```kotlin
   // allow: MUD-011 residual → MUD-XXX
   "com.example.IllegalImporter"
   ```
   Wire the allowlist into the assertion only when needed (import-prefix / FQCN guard). Do **not** silently broaden `dependsOn` to hide the edge.
3. Cite a follow-up ticket that will remove the residual. Empty allowlist is preferred when the graph is clean.

## When **not** to weaken rules

- Do **not** add a new package to a layer’s `dependsOn` just to make a one-off import green.
- Do **not** mass-refactor package layout under this gate without a dedicated ticket.
- Do **not** point layers at wrong roots (`com.jcraw.mud.llm`, `com.jcraw.mud.app`).

## Non-goals (other tickets)

- Detekt smells → MUD-010 / `docs/DETEKT.md`
- Test-file lock → MUD-012
- PIT mutation → MUD-014
- CI wiring → MUD-016
- Mass package moves / full historical layering debt cleanup
