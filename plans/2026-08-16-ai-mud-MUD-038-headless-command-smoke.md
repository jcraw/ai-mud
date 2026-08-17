# MUD-038 Plan — Headless command smoke (Wave Q4 / E1)

**Ticket:** MUD-038 · **Phase:** plan_review (await Astra approve)  
**Plan:** `plans/2026-08-16-ai-mud-MUD-038-headless-command-smoke.md`  
**Mirror:** `tmp/workers/MUD-038/PLAN.md`  
**Verify (post-impl):** `./tools/verify_mud.sh --core` · smoke proof: `./tools/smoke_commands.sh`  
**Depends:** MUD-031 (done). Not impl this turn.

---

## 1. Goal / acceptance

| # | Acceptance | Impl |
|---|------------|------|
| 1 | Script or testbot path runs **fixed** `look` / `take` / `inv` / `attack` with mocked/fallback LLM | New `:app` `CommandSmokeKt` + `tools/smoke_commands.sh`. **Not** TestBotRunner (live ReAct + API key) |
| 2 | Exit 0 on success; compact failure | One `PASS` line or `FAIL <step>: <reason>` on stderr; no stack unless crash |
| 3 | Optional verify lane **or** docs hook; **not** on `--core` until Jason | `--smoke` lane (preflight twin); skip default/fast/core/full/pitest/quarantine |
| 4 | No live OpenAI | `IntentRecognizer(null)` fallback; `MudGame(..., llmClient=null)`; never `OpenAIClient(` / key load |

---

## 2. Current inventory

| Piece | Truth |
|-------|--------|
| `App.main` | Interactive menus; **returns if no API key**; cannot pipe stdin |
| Testbot | `V3TestWorldHelper` / `TestBotMain` **require key**; ReAct LLM generate+validate; Ancient Abyss |
| `MudGame` | Already runs with `llmClient=null` (fallback look/combat). `processIntent` + handlers are the real console path |
| `RealGameEngineAdapter` | Captures stdout; `parseIntent` + `processIntent` + time/NPC tick. **Reuse** |
| Fallback parse | `look`/`l`, `take <x>`, `inventory`/`i`, `attack <x>` already work without LLM |
| Look | `describeCurrentRoom` **needs space + graph node** or prints `[No space data` |
| Take | `ItemTakeHandlers` → `FloorItemTakeApply` + **JSON templates** (`iron_sword` exists) |
| Attack | `CombatAttackPrep` + `AttackResolver`; **Hit or Miss both real**; missing NPC/combat → fail strings. Safe-zone check is **SQLite** (`findByChunkId`); in-memory space → not safe |
| Contracts (037) | take/drop/equip/use/hit/emote **unit** applies — not a command-path smoke |
| `DatabaseConfig` | Hardcoded cwd `data/` — smoke would clobber play DBs without override |
| Verify | No E1 gate. `--preflight` is the optional-lane pattern. Required schema tuple unchanged |
| DESIGN | E1 / Q23: scripted look/take/inv/attack; **not drain-blocking** until product phase |

---

## 3. Design

**KISS: fixture world + null-LLM `MudGame` + adapter + shell wrapper. Do not teach App.main or rewrite testbot.**

1. **`CommandSmokeWorld`** (app, no LLM): 1 V3 space + `GraphNodeComponent` + player + pickupable floor `Entity.Item` (`templateId=iron_sword`, name `Iron Sword`) + hostile `Entity.NPC` (`rat`) with `CombatComponent`. Player `currentRoomId` = that space.

2. **`CommandSmokeKt.main`:** set data dir first → `DatabaseConfig.init()` → `MudGame(world, llmClient=null)` → `RealGameEngineAdapter`. Sequence:
   - `look` — stdout contains space name; **not** `[No space data`
   - `take iron sword` — `inventoryComponent.items` has `templateId==iron_sword` (state, not “msg contains ok”)
   - `inventory` — stdout mentions `Iron Sword`
   - `attack rat` — stdout is **not** “don’t see anyone” / “Attack whom?”. **Hit or Miss = pass** (RNG). `AttackResult.Failure` for missing combat = fixture bug → fail.

3. **`tools/smoke_commands.sh`:** `MUD_DATA_DIR=$(mktemp -d)` → `./gradlew :app:run -PcommandSmoke=1` (or dedicated `runCommandSmoke` JavaExec). Unset `OPENAI_API_KEY` for the child. Trap/rm temp dir. Timeout ~3 min first compile. Exit = main exit.

4. **`DatabaseConfig`:** `MUD_DATA_DIR` env or `-Dmud.data.dir` **before** first access; default remains `data`. `init()` creates that dir. **No other core behavior.**

5. **Verify (optional):** `--smoke` lane like `--preflight`: run script only; record `gates.command_smoke` via `additionalProperties`; required tuple **unchanged**; other gates `skipped`. Missing script → **fail** on `--smoke` only. **Never** run on `--core`. Ticket `verify:` stays `--core`.

6. **Docs:** short `docs/COMMAND_SMOKE.md`; AGENTS one Verification row; DESIGN E1 pointer; DOD_SUMMARY optional gate; README one line. No `src/test/**` (no lock). New kt under global E (2500/250); **no Added override**.

---

## 4. Files

| Action | Path |
|--------|------|
| Create | `app/.../CommandSmoke.kt` (main) |
| Create | `app/.../CommandSmokeWorld.kt` (fixture) |
| Create | `tools/smoke_commands.sh` (executable) |
| Create | `docs/COMMAND_SMOKE.md` |
| Edit | `core/.../DatabaseConfig.kt` — data-dir override only |
| Edit | `app/build.gradle.kts` — `-PcommandSmoke=1` mainClass / JavaExec |
| Edit | `tools/verify_mud.sh` — `--smoke` lane + help; **not** default/core |
| Edit | `AGENTS.md` Verification table 1 row; `docs/AGENT_QUALITY_GATES_DESIGN.md` E1 live pointer; `docs/DOD_SUMMARY.md` optional gate; `README.md` 1 line |

No `src/test/**`. No handler/apply rewrite. No App.main key/menu change. No testbot ReAct.

---

## 5. Non-goals

- Android / device / GUI / Compose smoke
- Replacing playtest taste; full dungeon / friends multiplayer / GameServer two-session (E3)
- Live OpenAI; rewriting TestBotRunner / Ancient Abyss / App.main
- Wiring E1 onto `--core` / `--full` / CI (Jason later)
- New contract tests; 035/036/037 reopen; token override raise

---

## 6. Confirm acceptance

- [ ] `MUD_DATA_DIR` temp; `./tools/smoke_commands.sh` exit **0** with no API key (`env -u OPENAI_API_KEY`)
- [ ] Sequence look → take → inv → attack; take = V2 `iron_sword` on player; look/inv/attack as §3
- [ ] Fail compact: `FAIL take: inventory missing iron_sword`
- [ ] `rg 'OpenAIClient\(|OPENAI_API_KEY' app/**/CommandSmoke*.kt` empty
- [ ] `./tools/verify_mud.sh --core` still **0**; no `command_smoke` hard on core
- [ ] `./tools/verify_mud.sh --smoke` records `gates.command_smoke=pass`; `--dry-run --smoke` no gradle product suite
- [ ] Play `data/*.db` untouched (temp dir)
- [ ] Closeout cites `tmp/dod-summary.json` from `--core`

---

## 7. Impl steps

1. `DatabaseConfig` override + `CommandSmokeWorld` + `CommandSmokeKt` asserts.
2. Gradle `-PcommandSmoke=1`; `tools/smoke_commands.sh`; run once **without** key.
3. `--smoke` lane + docs (AGENTS / DESIGN E1 / DOD_SUMMARY / README).
4. `./tools/verify_mud.sh --core` (N≤3 flake only). Closeout: paths, smoke log, dod-summary, residual (E1 not core-hard).
5. Ticket → done (agent OK; no Jason playtest).

---

## 8. Risks

| Risk | Mitigation |
|------|------------|
| Attack RNG miss treated as fail | Hit **or** Miss pass; only “no target” / crash fail |
| Look needs graph node | Fixture includes `GraphNodeComponent` |
| Template miss | Use catalog `iron_sword` / `Iron Sword` |
| Smoke writes `data/` | `MUD_DATA_DIR` mktemp; fail if unset in script |
| Gradle first-run slow | Document; timeout; not on `--core` |
| `verify_mud.sh` size | Copy `--preflight` case; do not refactor other lanes |
| Token E on new kt | Two small files; no override |
| Adapter `runBlocking` inside `suspend` | Existing pattern; do not “fix” |

**Blast radius:** `:app` smoke-only + 1 `core` path + verify optional lane + docs. Console/GUI play path unchanged. `--core` meaning unchanged.

**Cheat-mode:** no `src/test` delete/edit; no live LLM; no `--core` fake-green via skip; no override raise; no App.main “just require key” dodge.
