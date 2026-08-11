# Detekt (MUD-010)

Static smell gate for AI MUD. **New code hard-fails** on the default rule set; **legacy debt is soft** via a committed baseline.

## Paths

| Path | Role |
|------|------|
| `config/detekt/detekt.yml` | Thin config overlay (`buildUponDefaultConfig = true`, `allRules = false`) |
| `config/detekt/baseline.xml` | Shared multi-module baseline (legacy soft) |
| `buildSrc/.../kotlin-jvm.gradle.kts` | Applies + configures detekt for every JVM module |
| `gradle/libs.versions.toml` | Pin: `detekt = "1.23.8"` |

## Verify behavior

- **Default / fast / core / full:** `./tools/verify_mud.sh` runs hard `./gradlew detekt` after lane Gradle steps.
- **Quarantine lane:** detekt is **not** run (debt-only lane).
- Findings **not** in the baseline fail the task (and verify exit 1).
- Findings listed in `baseline.xml` are ignored (soft legacy).

```bash
./tools/verify_mud.sh          # includes detekt
./tools/verify_mud.sh --fast   # includes detekt
./tools/verify_mud.sh --dry-run  # prints: ./gradlew detekt
./gradlew detekt                 # direct
```

No type-resolution on the fast path (main sources only; parallel).

## New smells

Agents and humans **fix** new detekt findings in changed code. Do **not** silence them by regenerating the baseline.

## Baseline regeneration (Jason / explicit only)

Mass baseline regen is **not** agent-default. Only **Jason** (or an explicit human instruction) may accept a bulk baseline update.

When authorized, preferred regen (shared baseline from all modules):

```bash
# 1) Per-module baselines (avoids multi-module overwrite of a single file)
mkdir -p config/detekt/module-baselines
./gradlew detektBaseline -PdetektModuleBaseline=true --continue

# 2) Merge CurrentIssues IDs into config/detekt/baseline.xml (stable sort)
#    (IDs are unique rule:signature strings; de-dupe across modules)

# 3) Drop temp module files; confirm green
rm -rf config/detekt/module-baselines
./gradlew detekt
```

Convention support: `-PdetektModuleBaseline=true` writes `config/detekt/module-baselines/<module>.xml` instead of the shared file. Normal builds always use `config/detekt/baseline.xml`.

**Do not** regenerate the baseline to “make CI green” after introducing new smells. Fix the smell or, with explicit approval, add only intentional exceptions.

## Non-goals (other tickets)

- Konsist architecture tests → MUD-011  
- Test-file lock → MUD-012  
- PIT mutation → MUD-014  
- CI wiring → MUD-016  
- Mass-fixing existing baselined findings (not this gate’s job)
