# MUD-034n Plan — Misc reasoning leftovers split (Wave Q3)

**Status: APPROVED by Astra 2026-08-12 09:22 MST**


**Ticket:** MUD-034n · plan_review · grok  
**Status:** PLAN ONLY — Astra/Jason approve → **fresh** impl (do not resume this session)  
**Paths:** `plans/2026-08-12-ai-mud-MUD-034n-misc-reasoning-split.md` · `tmp/workers/MUD-034n/PLAN.md`  
**Depends:** MUD-034, MUD-031 · **034m done** (no reopen 034a–m) · **not** 035–038  
**Verify (post-impl):** `./tools/verify_mud.sh --core` · pure moves  
**Pattern:** 034m thin facades · same-package · stable public FQCN

---

## 1. Goal / acceptance

| # | Acceptance | How |
|---|------------|-----|
| 1 | Extract 7 hosts | Pure-move; thin public entrypoints; no features |
| 2 | Console+GUI parity (pairs) | **N/A** — reasoning-side; no app/client pair in family |
| 3 | `--core` = 0 | After cuts + override edit |
| 4 | Remeasure; lower/remove overrides | never raise; no Added override |
| 5 | Residual ticket → `MUD-034n` | If still needed |
| 6 | New `.kt` ≤ global E | 2500 / 1100 / fn 250; fragment if needed |
| 7 | No unauthorized tests | Prefer none |

One ticket; stage cuts serial if fat. Last Q3 child.

---

## 2. Inventory

| path | file_tok | loc | ov file_E | peak FN ov | ov fn_E |
|------|--------:|----:|----------:|------------|--------:|
| `reasoning/.../DispositionManager.kt` | **3667** | 346 | 3667 | `trainSkillWithNPC` / `applyEvent` | **938** |
| `reasoning/.../pickpocket/PickpocketHandler.kt` | **3572** | 312 | 3572 | `stealFromNPC` | **571** |
| `reasoning/.../procedural/NPCGenerator.kt` | **3372** | 328 | 3372 | `createSocialChallenges` / gens | **385** |
| `reasoning/.../NPCKnowledgeManager.kt` | **3028** | 306 | 3028 | `generateCanonKnowledge` / prompts | **725** |
| `reasoning/.../procedural/QuestGenerator.kt` | **2975** | 288 | 2975 | `generateKillQuest` et al. | **436** |
| `reasoning/.../treasureroom/TreasureRoomDescriptionGenerator.kt` | **2572** | 211 | 2572 | `generateRoomDescription` / biome map | **397** |
| `reasoning/.../town/TownMerchantTemplates.kt` | **2523** | 297 | 2523 | `createArmorMerchant` / blacksmith | **468** |

Overrides all `ticket: MUD-034`. Global E: **2500 / 1100 / 250**. Ranked #23–54.

**Public keep (members / object fns — FQCN stable):**  
- `DispositionManager` ctor (`socialRepo`, `eventRepo`, `skillManager?`) + `applyEvent` · `shouldProvideQuestHints` · `getDialogueTone` · `getPriceModifier` · `getRecentEvents` · `getDisposition` · `getDispositionTier` · `attemptPersuasion` · `attemptIntimidation` · `canTrainPlayer` · `getTrainingMultiplier` · `trainSkillWithNPC`  
- `NPCKnowledgeManager` ctor + `queryKnowledge` · `addPredefinedKnowledge` · `getAllKnowledge` · `getKnowledgeByCategory` + **nested** `KnowledgeResult`  
- `PickpocketHandler` ctor + `stealFromNPC` · `placeItemOnNPC` + **nested** `PickpocketResult`/`Success`/`Caught`/`Failure`  
- `NPCGenerator` ctor (`theme`, `random`) + `generateHostileNPC` · `generateFriendlyNPC` · `generateBoss` · `generateRoomNPC`  
- `QuestGenerator` ctor (`seed?`) + `generateQuest` · `generateQuestPool`  
- `TownMerchantTemplates` object + `createPotionsMerchant` · `createArmorMerchant` · `createBlacksmith` · `createInnkeeper` · `getAllMerchants`  
- `TreasureRoomDescriptionGenerator` ctor + `generateRoomDescription` · `generatePedestalDescription` + **nested** `BiomeTheme` + companion `DEFAULT_BIOME_THEMES` · `getBiomeTheme`

**Callers (do not rewire):** `MudGameEngine` / `GameServer` / `EngineGameClient` (Disposition + Knowledge + Quest) · `NPCInteractionGenerator` · `EmoteHandler` · `QuestTracker` · app `PickpocketHandlers` (nested Result only) · `ClientSocialDialogueHandlers` (`KnowledgeResult`) · `WorldInitializationHelper` · `TreasureRoomPlacer` (`getBiomeTheme`). Tests exist; **do not edit**.

**Parity:** no app/client pair in this family. App `PickpocketHandlers` / client social are **outside** (034l). Client has **no** pickpocket handler. Shared reasoning types used by both engines — keep FQCN.

---

## 3. Design / approach

Same-package flat (`reasoning` / `pickpocket` / `procedural` / `town` / `treasureroom`); pure-move; fragment FN>250; no engine rewire. Hosts = thin delegates. Pass ctor deps into extracts.

### A. Disposition + knowledge (stage 1)

| # | extract | notes |
|--:|---------|-------|
| 1 | `DispositionEvents` | `applyEvent` persist+log; `getRecentEvents` |
| 2 | `DispositionQueries` | hints / tone / price / getDisposition / tier |
| 3 | `DispositionChecks` | persuasion + intimidation (Diplomacy/Charisma deltas exact; call Events) |
| 4 | `DispositionTraining` | canTrain / multiplier / `trainSkillWithNPC` — **frag unlock vs grant-xp vs level-up** if FN>250 |
| 5 | `NPCKnowledgeTopics` | normalize / match / keywords / summary |
| 6 | `NPCKnowledgePrompts` | canon system + user prompts (token-heavy) |
| 7 | `NPCKnowledgeCanon` | `generateCanonKnowledge` LLM+save+social ref |
| 8 | `NPCKnowledgeStore` | `addPredefinedKnowledge` |
| 9 | thin hosts | public → extracts; **keep `KnowledgeResult` nested** |

### B. Pickpocket (stage 2)

| # | extract | notes |
|--:|---------|-------|
| 10 | `PickpocketSteal` · `PickpocketPlace` | gold 30% / item / weight fail order exact |
| 11 | `PickpocketCheck` · `PickpocketCaught` | max(Stealth,Agi) vs Perc; wariness +20; Δ −20..−50 |
| 12 | thin host | **keep `PickpocketResult` nested** (app + tests FQCN) |

### C. Procedural gens (stage 3)

| # | extract | notes |
|--:|---------|-------|
| 13 | `NPCGeneratorTables` | suffixes + personality/trait maps |
| 14 | `NPCGeneratorSocial` | hostile/friendly/boss SocialComponent |
| 15 | `NPCGeneratorStats` | stats / health / `createSocialChallenges` (frag if >250) |
| 16 | `NPCGeneratorEntities` | hostile / friendly / boss / room roll 60/20/20 |
| 17 | `QuestGeneratorTitles` | theme title map |
| 18 | `QuestKillGen` · `QuestCollectGen` · `QuestExploreGen` · `QuestTalkGen` · `QuestSkillGen` · `QuestFallbackGen` | **preserve fallback chain** kill→collect→explore; talk/skill→explore; empty→fallback |
| 19 | thin hosts | `generateQuest` dispatcher + pool stay |

### D. Town + treasure (stage 4)

| # | extract | notes |
|--:|---------|-------|
| 20 | `TownMerchantItems` | `createItemInstance` |
| 21 | `TownMerchantPotions` · `TownMerchantArmor` · `TownMerchantBlacksmith` · `TownMerchantInn` | one factory each if FN>250 |
| 22 | `TreasureRoomPrompts` · `TreasureRoomFallbacks` | system/user + looted/fallback |
| 23 | `TreasureBiomeThemes` | map body; companion **delegates** so `TreasureRoomDescriptionGenerator.getBiomeTheme` / `DEFAULT_BIOME_THEMES` FQCN stay |
| 24 | thin hosts | `getAllMerchants` + generate* one-liners; keep `BiomeTheme` nested |

Prefer **remove** all 7 overrides if ≤E; else **lower** + `ticket: MUD-034n`. Never raise; no Added override.

---

## 4. Files to create/touch

**Edit:** 7 hosts; `token_budget_kt.json` only those 7 rows.

**Create (~20–26 `.kt` ≤E):** Disposition* · NPCKnowledge* · Pickpocket* · NPCGenerator* · Quest*Gen · TownMerchant* · TreasureRoomPrompts/Fallbacks/BiomeThemes.

**Not:** app/client handlers · 034a–m reopen · 035–038 · mass detekt · `src/test/**` · other overrides · unused-file deletes.

---

## 5. Non-goals

Raise caps · Added overrides · mass detekt · PIT 80% · 036–038 · outside family · reopen 034a–m · fill unused TownMerchant / NPCGenerator callers · delete unused hosts · change quest fallback chain / pickpocket numbers / training multipliers · features · commit/push unless Jason asks.

---

## 6. Acceptance checklist (impl)

- [ ] 7 hosts thin; bodies in same-package extracts
- [ ] Parity N/A (reasoning) — CLOSEOUT states it
- [ ] Public FQCN/signatures unchanged (nested Result/KnowledgeResult/BiomeTheme + companion stay)
- [ ] Quest fallback chain + pickpocket/disposition numbers unchanged
- [ ] `./tools/verify_mud.sh --core` = 0
- [ ] Remeasure → `tmp/workers/MUD-034n/token_remeasure.json`
- [ ] Overrides removed or lowered+`MUD-034n`; never raised; no Added
- [ ] New `.kt` ≤E (file/fn)
- [ ] No unauthorized `src/test/**`
- [ ] CLOSEOUT: paths, tokens before/after, residual risk

---

## 7. Ordered impl steps

1. Baseline → `tmp/workers/MUD-034n/token_baseline.json` (`check_token_budget_kt.py --files` 7 hosts)
2. Disposition Events/Queries/Checks/Training; thin host
3. Knowledge Topics/Prompts/Canon/Store; thin host
4. Pickpocket Steal/Place/Check/Caught; thin host
5. NPC tables/social/stats/entities; Quest titles + type gens; thin hosts
6. Town merchants + treasure prompts/fallbacks/biome; thin hosts
7. Remeasure; override remove or lower+`MUD-034n`
8. `--core` (N≤3 flaky → escalate)
9. CLOSEOUT + ticket/board done (**fresh impl**, post-APPROVED)

---

## 8. Risks

| Risk | Mitigation |
|------|------------|
| Nested FQCN break (Result / KnowledgeResult / BiomeTheme) | Keep nested on host; companion delegates only |
| Quest fallback / pickpocket / training number drift | Pure-move; same early-returns + literals |
| `trainSkillWithNPC` / canon prompt / steal FN still >250 | Fragment unlock/xp, prompt vs chat, gold vs item |
| TownMerchant unused vs TownGeneratorMerchants | Extract only; do not delete / unify |
| Override raise / wrong ticket | Remeasure; lower-only; retarget 034n |
| Detekt ID shift | Suppress carry; no mass regen |
| Serial tree | One builder; do **not** start 035–038 |

---

**Handoff:** Plan for Astra/Jason. **STOP. No implementation this session.**
