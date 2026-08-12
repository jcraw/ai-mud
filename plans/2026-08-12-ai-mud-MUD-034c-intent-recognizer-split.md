# MUD-034c Plan — Intent domain split (Wave Q3)

**Ticket:** MUD-034c · **implementing (APPROVED)** · **Worker:** grok  
**Status:** APPROVED by Astra 2026-08-12 03:53 MST → fresh IMPL session
**Plan/mirror:** `plans/2026-08-12-ai-mud-MUD-034c-intent-recognizer-split.md` · `tmp/workers/MUD-034c/PLAN.md`  
**Depends:** MUD-034, MUD-031 · **Parent:** MUD-034 · **Prior:** 034a/b done  
**Verify:** `./tools/verify_mud.sh --core` · Pattern: pure moves / thin entry (034b). No features.

---

## 1. Goal / acceptance

| # | Acceptance | How |
|---|------------|-----|
| 1 | Extract both hosts, behavior-preserving | Pure-move clusters; multi-file sealed Intent by domain; no NLP/semantics change |
| 2 | Console+GUI parity | **N/A shared** — perception-only; both clients use `parseIntent` + `Intent.*` |
| 3 | `--core` = 0 | After cuts + override discipline |
| 4 | Remeasure; lower/remove overrides | `--files <touched>`; never raise; no Added override |
| 5 | Residual ticket → `MUD-034c` | If host still needs override |
| 6 | New `.kt` ≤ global E | tok 2500 / LOC 1100 / fn tok 250 |
| 7 | No unauthorized tests | Prefer none; if Intent rename hits tests → scope + `MUD_ALLOW_TEST_CHANGES=1 ./tools/test_lock.sh --write` |

---

## 2. Inventory

| path | file_tok | loc | ov tok_E | peak fn_E | ticket |
|------|--------:|----:|---------:|----------:|--------|
| `perception/.../IntentRecognizer.kt` | **10293** | 773 | 10293 | **2827** parseFallback | MUD-034 |
| `perception/.../Intent.kt` | **2707** | 294 | 2707 | — | MUD-034 |

Global E2500/1100/fn250. Ranked #3/#46. Baseline: `tmp/workers/MUD-034c/token_baseline.json`.  
Callers: MudGameEngine, MultiUserGame, EngineGameClient, V3TestGameEngine + perception tests. ~446 `Intent.X` / 17 files.

**Recognizer hotspots (map only):**

| ~lines | cluster | fn_tok | extract |
|-------:|---------|-------:|---------|
| 12–63 | `parseIntent` shell | small | **stay** public |
| 68–130 | compound + cardinal/dir | small | `IntentDirectionParse` |
| 156–258 | `buildSystemPrompt` | **2142** | `IntentLlmPrompt*` fragments |
| 260–285 | `buildUserPrompt` | small | w/ prompt |
| 133–154, 287–420 | LLM + JSON when | **2015** | `IntentLlmParse` + `IntentLlmJsonMap*` |
| 424–553 | say helpers | 552 | `IntentSayParse` |
| 555–775 | `parseFallback` | **2827** | `IntentFallback*` + shell |
| 777–847 | trade/list | 361/307 | `IntentTradeParse` |

**Intent.kt:** `@Serializable sealed` + ~40 nested subtypes (nav/items/combat/social/skills+quests/meta). ~207 over E2500.

---

## 3. Extract approach

**API stay thin:** `IntentRecognizer(llmClient)` + `suspend parseIntent(...)`. Same package. `internal`/`object` pure moves. `@file:Suppress` for pure-moved detekt debt (no mass baseline).

### A. IntentRecognizer (primary)

| New file | Moves |
|----------|--------|
| `IntentDirectionParse` | splitCompound, cardinal, directionWord |
| `IntentSayParse` | SAY consts + say pipeline |
| `IntentTradeParse` | trade + list stock |
| `IntentLlmPrompt*` | system/user prompts; **const fragments** so fn≤250, file≤2500 |
| `IntentLlmJsonMap*` | response when-arms by domain; keys identical |
| `IntentLlmParse` | chatCompletion orchestrate |
| `IntentFallback{Nav,Items,Social,Skills,Meta}` | arm bodies |
| `IntentFallbackParse` | tokenize + **one `when (command)`** → helpers (first-match; keep dead `"pick"` arm order) |

**Cut order:** Direction → Say → Trade → LlmPrompt → JsonMap → LlmParse → Fallback → thin host → remeasure → fragment leftovers.

### B. Intent sealed

Multi-file sealed = top-level subtypes same package (nested `Intent.Move` cannot span files).

| File | Subtypes (groups) |
|------|-------------------|
| `Intent.kt` | sealed root only |
| `IntentNavigation` | Move Scout Travel Look Search Interact |
| `IntentItems` | Inventory Take* Drop Give Equip Use* Loot Craft treasure* |
| `IntentCombat` | Attack Flee |
| `IntentSocial` | Talk Say Emote Ask Persuade Intimidate Pickpocket Trade |
| `IntentSkillsQuests` | Check skills/quests Rest |
| `IntentMeta` | Save Load Help Quit Invalid |

Mechanical rename `Intent.X`→`X` (same package often free). Props/when exhaustiveness unchanged. Prefer full multi-file over residual Intent override.

---

## 4. Files / overrides

**Edit:** hosts + `config/quality/token_budget_kt.json`  
**Create:** modules above (+ extra FN/FILE fragments if needed, 034b-style)  
**Maybe:** app/client/testbot when/constructors if rename  
**Not:** 034a/b, 034d–n, features, mass detekt  

**Overrides:** remeasure touched; **remove** if ≤E; else **lower** + `ticket: MUD-034c`; never raise; no Added override.

---

## 5. Specs/docs

None beyond TOKEN_BUDGET lower-only. No user-guide churn.

---

## 6. Tests / verify

```bash
./tools/verify_mud.sh --core
python3 tools/quality/check_token_budget_kt.py --files \
  perception/src/main/kotlin/com/jcraw/mud/perception/*.kt \
  --json-out tmp/workers/MUD-034c/token_remeasure.json
```

Existing: Fallback / Direction / FastPath / IntentTest (null LLM). No test edits preferred.

---

## 7. Impl steps

1. Confirm baseline; serial one builder  
2. Extract Direction→Say→Trade (compile each)  
3. LLM prompt fragments + JsonMap + parseLLM  
4. Fallback helpers; preserve when first-match  
5. Thin host  
6. Multi-file Intent + mechanical renames  
7. `--core` green  
8. Remeasure → override lower/remove/retarget  
9. CLOSEOUT + ticket done (impl session only)

---

## 8. Out-of-scope

Raise caps · Added overrides · mass detekt · PIT/036–038 · other god families · prompt/command feature changes · commit/push unless Jason · reopen 034a/b

---

## 9. Risks

| Risk | Mitigation |
|------|------------|
| Fallback key order (`pick`) | Single when shell; move bodies only |
| LLM when/prompt drift | Verbatim keys/strings; concat-only split |
| Intent rename surface | Mechanical; compile as checklist; test-lock iff tests |
| FN_E250 leftovers | Further pure fragment |
| Override mistakes | lower/remove/retarget only |
| Serial tree | One builder; ignore 034d–n |

**Handoff:** Astra/Jason APPROVED → **fresh impl session**. STOP (plan only).
