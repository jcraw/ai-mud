# MUD-022 Plan — Clear SkillManager quarantine ×8

**Ticket:** MUD-022 · **Worker:** grok · **Phase:** plan_review (await Astra)  
**Impl = fresh session** after Astra approve. Do **not** resume this plan session for product edits.  
**Plan path:** `plans/2026-08-11-ai-mud-MUD-022-skillmanager-quarantine-clear.md`  
**Worker mirror:** `tmp/workers/MUD-022/PLAN.md`  
**Post-impl verify:** `./tools/verify_mud.sh --core` **and** `./tools/verify_mud.sh --quarantine`  
**Depends:** MUD-021 done (residual **8**). **Target:** quarantine **8 → 0**.  
**Design SoT:** `docs/requirements/V2/FEATURE_PLAN_generic_skill_progression.md` (dual-path).

---

## 1. Goal / acceptance map

| Acceptance | How plan hits it |
|------------|------------------|
| All 8 `SkillManagerTest` un-quarantined + green under excludeTags | Fix prod drift + re-contract only where plan differs; remove `@Tag("quarantine")` |
| Prefer **fix prod** vs plan; re-contract test only when intentional | Clusters C1–C3 below |
| `TEST_QUARANTINE.md` count **0** | Empty residual table; baseline notes updated |
| `--core` exit 0 | Green reasoning + gates |
| `--quarantine` exit 0 | Empty tag set OK |
| test-lock regen if tests touched | `MUD_ALLOW_TEST_CHANGES=1 ./tools/test_lock.sh --write` |
| BOARD / AGENTS residual SkillManager notes cleared | BOARD residual 0; no AGENTS SkillManager debt (none today) |

---

## 2. Current inventory (8 fails + prod formulas)

**Prod formulas (truth today):**
- `grantXp`: success = `baseXp * mult`; failure = `(baseXp * 0.2) * mult` (`SkillManager.kt`)
- `GameConfig.skillXpMultiplier` **default = `10.0f`** (comment says `1.0f` normal) ← **root of XP scale drift**
- Thresholds: `SkillState.calculateXpToNext(forLevel) = 100 * (forLevel+1)^2` (L0→1: 100, L1→2: 400, L2→3: 900, L3→4: 1600)
- Dual-path `attemptSkillProgress`: lucky roll → instant unlock/level; else `grantXp`
- Lucky unlock: `unlock().copy(level = 1)` (plan: **land level 1**)
- Locked skills: `grantXp` **auto-unlocks** when `level >= 1` (plan: use-based unlock OK; does **not** fail)

| # | Method | Observed / reason | Root |
|---|--------|-------------------|------|
| 1 | `grantXp grants full XP on success` | expect 100; mult→1000 + may LevelUp | mult=10 |
| 2 | `grantXp grants 20 percent XP on failure` | expect 20, got 200 | 20×10 |
| 3 | `grantXp triggers level-up when threshold crossed` | 150×10 + 300xp → multi-level not 1→2 | mult |
| 4 | `grantXp handles multiple level-ups` | 3000×10 → newLevel **9** not **4** | mult |
| 5 | `grantXp fails for unlocked skill` | setup is **locked** (`unlocked=false`); prod succeeds + auto-unlock | test vs plan |
| 6 | `attemptSkillProgress … unlocks skill at level 1` | unlock event but level **2** | XP path + mult (1000 XP from L0 → L2) if lucky misses; soft `if (hasUnlock)` |
| 7 | `defender can unlock Dodge through lucky progression` | same L1 vs L2 | same |
| 8 | `defensive skills progress independently…` | both lucky → level++ xp preserved **0** → `skill2.xp > skill1.xp` false | test path |

**FEATURE_PLAN contracts (binding):** unlock 0→1 at **level 1**; failure XP **~20%** (not 10×); quadratic `100*level^2`; lucky OR grind; defensive skills per-entity.

---

## 3. Design (clusters)

### C1 — XP scale / level math (tests 1–4) → **PROD first**
- Set `GameConfig.skillXpMultiplier` default **`1.0f`** (align comment + plan base XP; 10× was test speed leak).
- Keep mult as tunable; green tests already use `* skillXpMultiplier` where adaptive.
- After mult=1: test1 → 100 XP no level-up; test2 → 20; test3 → 300+150 crosses 400 → L2; test4 → 3000 covers 400+900+1600=2900 → L4.
- Optional harness belt: `@BeforeTest` pin mult=1.0 + restore in `@AfterTest` so suite immune to config mutation. Prefer pin only if cross-test pollution observed.
- **Do not** change threshold formula (already matches plan / `SkillStateTest`).

### C2 — Locked-skill grantXp (test 5) → **TEST re-contract**
- Plan: XP path unlocks locked skills (use-based). Prod correct.
- Rename/replace: assert `grantXp` on locked skill **succeeds**, grants XP, and auto-unlocks when level≥1 (e.g. baseXp≥100 at L0 → unlock + LevelUp).
- **Not** assert Failure for locked. Name was wrong (“unlocked” vs locked setup).

### C3 — Lucky unlock L1 + isolation (tests 6–8) → **TEST (+ prod only if L1 wrong on pure lucky)**
- **6/7:** With mult=1, XP path unlock lands **L1** (100 XP → L0→1). Force deterministic lucky where asserting lucky: known seed with `roll <= luckyChance`, **or** assert either path ends `unlocked && level == 1`. Drop soft `if (hasUnlock)` that skips real contract — hard assert unlock+L1.
- If pure lucky path ever yields L≠1 → **prod bug** in `attemptSkillProgress` unlock branch (should stay `level = 1`).
- **8 isolation:** Force XP path for both entities (`enableLuckyProgression = false` in test, restore after) **or** unlucky seed covering **both** rolls; then baseXp 100 vs 300 → `skill2.xp > skill1.xp`. Keep entity independence contract strong.
- Optional non-block: lucky 0→1 chance table says 15%; formula `floor(15/sqrt(targetLevel+1))` with target=1 → 10%. Only touch if unlock flaky; prefer seed/disable over formula redesign this ticket.

### Docs / bookkeeping
- `docs/TEST_QUARANTINE.md`: residual table empty, count **0**, drop “Deferred residual SkillManager×8”.
- BOARD: clear SkillManager residual notes on done (impl session).

---

## 4. Files (impl touch list)

| Path | Change |
|------|--------|
| `config/.../GameConfig.kt` | `skillXpMultiplier` default `1.0f` |
| `reasoning/.../skill/SkillManager.kt` | only if lucky unlock ≠ L1 after mult fix |
| `reasoning/.../skill/SkillManagerTest.kt` | un-tag ×8; re-contract #5; harden #6–8; optional mult pin |
| `docs/TEST_QUARANTINE.md` | count 0, empty residual |
| `tools/test-lock/manifest.sha256` | regen if tests touched |
| ticket + BOARD + closeout | impl session |

No combat/balance redesign. No detekt mass regen. No commit/push this plan turn.

---

## 5. Non-goals

- Full skill redesign / L3+ economy / combat balance
- Detekt baseline mass regen / PIT hard threshold
- Drop/V1 purge (MUD-023/024)
- git commit/push (Astra Wave G post-done)

---

## 6. Acceptance checklist (impl)

- [ ] All 8 tags removed; methods green under default excludeTags
- [ ] Prod mult default 1.0f; failure still ~20% of base; multi-level math via `100*(L+1)^2`
- [ ] Lucky/use unlock lands **level 1**; defensive isolation green
- [ ] Locked-skill test re-contracted to dual-path (no false Failure expect)
- [ ] `TEST_QUARANTINE.md` count **0**
- [ ] `./tools/verify_mud.sh --core` exit 0
- [ ] `./tools/verify_mud.sh --quarantine` exit 0 (empty set)
- [ ] test-lock regen if `src/test` touched
- [ ] Closeout + `tmp/dod-summary.json`; BOARD residual cleared

---

## 7. Ordered steps (impl session)

1. Confirm 8 tags still only SkillManager residual (`rg '@Tag\("quarantine"\)'`).
2. **Prod:** `skillXpMultiplier = 1.0f` in `GameConfig.kt`.
3. Run quarantined methods only; note remaining fails after mult fix.
4. Re-contract test5 (locked auto-unlock success).
5. Harden tests 6–7 (assert L1 unlock; deterministic lucky or XP-path L1).
6. Fix test8 isolation (disable lucky or dual-unlucky; compare XP).
7. Prod-touch `SkillManager` **only if** pure lucky still ≠ L1.
8. Remove all 8 `@Tag("quarantine")` + quarantine comments.
9. Update `TEST_QUARANTINE.md` → 0.
10. `MUD_ALLOW_TEST_CHANGES=1 ./tools/test_lock.sh --write` if tests changed.
11. `./tools/verify_mud.sh --core` then `--quarantine` (both exit 0).
12. Closeout + ticket/BOARD → done (impl session; **push only if Jason/Astra allowlisted**).

---

## 8. Risks

| Risk | Mitigation |
|------|------------|
| mult 10→1 slows live progression feel | Intended normal rate; 10× was mis-default; still tunable |
| Other suites assume mult=10 absolute XP | Grep shows adaptive asserts; core SkillState tests independent |
| Lucky seed flaky across Kotlin Random | Prefer `enableLuckyProgression=false` for XP contracts; hard assert L1 for unlock |
| test-lock fail closed | Regen with env flag; commit lock with test edits |
| Scope creep into chance formula | Defer formula 10% vs 15% unless unlock flaky |

**Residual after done:** quarantine **0**; Wave G continues MUD-023.

---
Status: APPROVED by Astra 2026-08-11 16:30 MST
Common-sense: C1 skillXpMultiplier default 1.0f (prod); C2 re-contract locked grantXp; C3 lucky L1 + isolation; quarantine 0. No balance redesign.
Impl = fresh session (do not resume plan 019ff325-b279-7e31-85aa-b36814f24a5a).
