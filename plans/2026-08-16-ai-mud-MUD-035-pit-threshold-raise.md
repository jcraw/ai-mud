# MUD-035 Plan — PIT threshold raise schedule toward 80% (Wave Q4)

**Ticket:** MUD-035 · **Phase:** done  
**Status:** APPROVED — IMPL shipped (live stay R0; remasured min 9.8%)  
**Plan (wave artifact):** `tmp/wave-runs/PAR-prefix-MUD-MUD-035-171644/MUD-035/PLAN/1/PLAN.md`  
**Plan (product copy):** `plans/2026-08-16-ai-mud-MUD-035-pit-threshold-raise.md`  
**Verify (post-impl):** `./tools/verify_mud.sh --pitest` · depends **MUD-034** (done; children **034a–n** done)  
**Fresh IMPL authorized** — do not resume plan session.

---

## 1. Goal / acceptance mapping

| # | Acceptance | Impl delivers |
|---|------------|---------------|
| 1 | Document schedule in `docs/PIT.md`: **60 soft → 70 soft → 80 hard** (opt-in then default on `--pitest`) | PIT.md becomes schedule SoT (rungs, promote rules, anti-game, nightly sketch) |
| 2 | Only raise after ≥1 god-split landed **or** measurement shows headroom | Split gate **met** (034c perception + 034m memory/core + rest of 034a–n). Headroom **not** met (day-one min **~9.1%**). **Live stay 60.** Do not flip 70/80 in this ticket. |
| 3 | Keep PIT out of fast/core | No lane table change. `PITEST_IN_FULL_LANE=0` stays. `--pitest` only. |
| 4 | Nightly/CI optional job sketch | **Docs-only** YAML in PIT.md. Do **not** add `.github/workflows` (Actions minutes + ~7m wall + memory TIMED_OUT). |
| 5 | No weakening tests to hit score | No `src/test/**`. No mutator/target shrink. Test-lock untouched. |

**Honest one-liner:** this ticket **schedules** the ratchet and names the flip conditions. It does **not** claim 80% and does **not** brick `--pitest` / `MUD_PITEST_HARD`.

---

## 2. Current inventory

| Item | Truth |
|------|--------|
| **Policy today** | `tools/verify_mud.sh`: `PITEST_SOFT_THRESHOLD=60`. Soft below-60 = **pass + note**. `MUD_PITEST_HARD=1` (or `mud_pitestHard=true`) fails if min &lt; **same 60**. Gradle `mutationThreshold=0` (report-only). |
| **Scope** | `:core` / `:perception` / `:memory` only via `buildSrc/.../pitest-pure.gradle.kts`. Mutators `STRONGER`. Score = **min** of three XML mutation-coverage %. |
| **Lanes** | default/fast/core: **never** PIT. full: skip (`PITEST_IN_FULL_LANE=0`; core wall ~130s &gt; 45s). `--pitest`: always. quarantine: never. |
| **Baseline (2026-08-11, MUD-014)** | perception **~9.1%** (min) · core **~25–26%** (~130s) · memory **~39%** (threaded ~6–7m). Three-module wall **~440s**. Soft 60 **not** met — expected. |
| **CI** | `.github/workflows/verify.yml` = `--core` only (MUD-016). No scheduled `--pitest`. |
| **Design (accepted)** | `docs/AGENT_QUALITY_GATES_DESIGN.md` §7.4 / C1: Now soft 60 → after structure wave soft 70 → target **hard 80** pure; nightly if &gt;45s. Jason: PIT hard 80% **only after splits start**; threshold policy is **human**. DIGEST-007/025 pointer only (do not dual-copy). |
| **Q3 splits** | 034 umbrella tickets-only; **children landed** extracts. PIT-relevant: **034c** IntentRecognizer split; **034m** memory repos + WorldState/CombatComponent. Splits **do not** raise mutation score. |
| **Docs gap** | PIT.md still “day-one soft 60 / 80% hard is a non-goal.” No 60→70→80 table, no promote rules, no nightly sketch. AGENTS: “Soft 60%; hard opt-in `MUD_PITEST_HARD=1`.” |
| **Not present** | Separate hard vs soft constants. `PITEST_HARD_DEFAULT`. Score-headroom remeasure since 034*. |

---

## 3. Design / recommended approach

### Verdict
**Document + plumb. Do not raise live numbers.**

Q3 satisfies “≥1 god-split landed.” It does **not** satisfy “measurement shows headroom.” Flipping soft 60→70 only changes the *wording* of a below-threshold note. Flipping hard/default to 80 **fails every `--pitest`** until perception/core/memory asserts get stronger — that work is **out of scope** (would need authorized `src/test` tickets + lock regen).

### Schedule (write verbatim into PIT.md)

| Rung | Policy | Live after MUD-035? | Promote when **all** hold |
|------|--------|---------------------|---------------------------|
| **R0 (now)** | soft **60**; hard-opt-in **60** | **yes** | already |
| **R1** | soft **70**; hard-opt-in **70** | no | remasured min ≥ **72** (2pp buffer); same modules/mutators; Jason/Astra OK |
| **R2a** | soft 70 or 80; hard-opt-in **80** (flag still required) | no | remasured min ≥ **82**; bake on opt-in |
| **R2b (target)** | **hard 80 default** on `--pitest` (`PITEST_HARD_DEFAULT=1`) | no | R2a baked; Jason/Astra OK (human threshold policy) |

Never put PIT (soft or hard) on default/fast/core. Never put 80-hard on `--full` while core PIT &gt;45s.

**Headroom** = `mutation_score` (min of three) from a green `--pitest` `tmp/dod-summary.json`, not line coverage, not “splits landed.”

**Follow-on (do not file here):** assertion-strength tickets on **perception first** (current min), then core, then memory. This ticket only *points* at that path.

### Plumbing (small, so the next flip is one constant)

In `verify_mud.sh`, keep behavior identical at 60, but name the rungs:

```bash
PITEST_SOFT_THRESHOLD=60          # R0; next 70 then 80 — docs/PIT.md
PITEST_HARD_THRESHOLD=60          # hard-opt-in bar; may later exceed soft (R2a)
PITEST_HARD_DEFAULT=0             # 1 = --pitest always hard (R2b only)
```

`pitest_hard_mode`: true if `PITEST_HARD_DEFAULT=1` **or** existing `MUD_PITEST_HARD` / `mud_pitestHard`. Compare fail bar to **`PITEST_HARD_THRESHOLD`**, not the soft constant. Soft note still uses `PITEST_SOFT_THRESHOLD`.

Gradle `mutationThreshold` stays **0**. Policy stays in verify + PIT.md (KISS; no second source of fail).

dod-summary: keep `mutation_score` = min. Extend **note** only, e.g. `soft 60% (schedule R0; next 70 when min≥72) — docs/PIT.md`. No schema change.

### Nightly / CI (docs-only)

Sketch in PIT.md — **do not land YAML**:

```yaml
# optional — not in this ticket
name: PIT nightly
on:
  schedule: [{ cron: "0 8 * * *" }]   # ~01:00 AZ
  workflow_dispatch:
jobs:
  pitest:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      # same JDK17 / Gradle / rg as verify.yml
      - run: ./tools/verify_mud.sh --pitest
# required check: never, until R2b
# continue-on-error: yes while still R0/R1
```

Why docs-only: MUD-016 already decided `--pitest` is not required CI; wall ~7m (memory-dominated; first-run historically ~37m); TIMED_OUT minions; Actions minutes. Jason can add the file later in one PR.

### Anti-game (PIT.md + standing design §10)

Forbidden as a way to “make 70/80”: delete/weaken tests; shrink `targetClasses` / exclude packages; drop `STRONGER`; raise Gradle threshold to hide survivors; skip a module and take max instead of min. Excludes only with measured thrash **and** Jason. Coverage % ≠ done (keep AGENTS).

### Docs

- **`docs/PIT.md`**: schedule table; R0–R2b; promote checklist; remasure instruction; nightly sketch; anti-game; update non-goals (80 hard is **target**, not day-one). Keep module table, lane table, baseline; add “remeasure (impl)” row if `--pitest` numbers differ.
- **`AGENTS.md`**: one surgical phrase — soft 60% now; schedule 60→70→80 → `docs/PIT.md`. Do not rewrite Verification table.
- **DESIGN.md**: optional one-line “schedule live in PIT.md (MUD-035)” under §7.4 — skip if it smells like drive-by.
- Ticket/BOARD: impl closeout only (`plan:` path, `plan_review`→`done`).

---

## 4. Files to create/touch

| Path | Action |
|------|--------|
| `docs/PIT.md` | **Main.** Schedule + promote + anti-game + nightly sketch + remasure |
| `tools/verify_mud.sh` | Named `PITEST_HARD_THRESHOLD` + `PITEST_HARD_DEFAULT=0`; help/header one-liners; note mentions R0. **Behavior at 60 unchanged.** |
| `AGENTS.md` | Surgical PIT sentence only |
| `docs/AGENT_QUALITY_GATES_DESIGN.md` | Optional §7.4 pointer — default **skip** |
| `issues/MUD-035-*.md` + BOARD | Closeout / `plan:` only (impl) |
| `plans/2026-08-16-ai-mud-MUD-035-pit-threshold-raise.md` | Product-canonical copy of this plan |

**Do not touch:** `src/test/**`, test-lock, `pitest-pure.gradle.kts` mutators/targets, `gradle/libs.versions.toml`, product `*.kt`, `.github/workflows/*`, app/client/reasoning/testbot.

---

## 5. Non-goals

- Mutating app/client/UI (ticket). Mutating reasoning/testbot/llm.
- Raising live soft to 70 or hard/default to 80 **this ticket**.
- Writing or strengthening tests to chase the score.
- Required CI PIT job; landing nightly YAML.
- Putting PIT on fast/core/full.
- Weakening STRONGER / narrowing targets / changing score = min.
- Coverage theater; live LLM; force-push; secrets; Gateway; deploy/push/merge.

---

## 6. How impl confirms acceptance

**Checklist**
- [ ] `docs/PIT.md` has 60 soft → 70 soft → 80 hard (opt-in then default on `--pitest`) plus promote rules + anti-game + nightly sketch
- [ ] Live constants still **60 / 60 / HARD_DEFAULT=0** unless remasured min ≥ 72 (unexpected — then stop and ask Astra before flipping)
- [ ] `./tools/verify_mud.sh` and `--core` → `pitest.status=skipped`; no PIT Gradle
- [ ] `./tools/verify_mud.sh --pitest` exit 0; JSON `mutation_score` number; soft-below-60 **pass + note** (unless impl flipped — it should not)
- [ ] `MUD_PITEST_HARD=1` still fails when min &lt; hard threshold (60)
- [ ] No `src/test` / lock / workflow / mutator edits
- [ ] Closeout cites `tmp/dod-summary.json` (or `$MUD_DOD_SUMMARY`), module scores, residual (still far from 70/80)

**Verify**
```bash
./tools/verify_mud.sh                 # PIT skipped
./tools/verify_mud.sh --core          # PIT skipped
./tools/verify_mud.sh --dry-run --pitest
./tools/verify_mud.sh --pitest        # ticket verify; ~7m
```

---

## 7. Ordered impl steps

1. Copy this plan to `plans/2026-08-16-ai-mud-MUD-035-pit-threshold-raise.md` if not already there; ticket `plan:` + `plan_review`.
2. Split `PITEST_HARD_THRESHOLD` / `PITEST_HARD_DEFAULT` in `verify_mud.sh`; keep 60/0; update help one-liners.
3. Rewrite PIT.md schedule + sketch + anti-game; surgical AGENTS phrase.
4. Run ticket verify `--pitest`; record fresh min + per-module + wall into PIT.md “remeasure” row (replace or annotate 2026-08-11).
5. **If min ≥ 72:** do **not** silently flip — note in closeout and wait Astra (threshold policy is human). Else leave R0.
6. Confirm default + `--core` still skip PIT.
7. Closeout: paths, scores, `tmp/dod-summary.json`, residual follow-on (perception asserts). No push/merge.

---

## 8. Risks

| Risk | Mitigation |
|------|------------|
| Flip 70/80 now because “Q3 done” | Splits ≠ mutation headroom. Promote table requires remasured min. |
| Brick `--pitest` / hard-opt-in | Live stay 60. R2b only after Jason. |
| Nightly YAML burns minutes / flakes | Docs-only; MUD-016 already excluded PIT from required CI. |
| Score chase via weaker suite | No test edits; anti-game list; test-lock. |
| Memory PIT wall / TIMED_OUT | Keep `--pitest` nightly-shaped; N≤3 then escalate. |
| Two fail policies (Gradle vs verify) | Gradle threshold stays 0. |
| DESIGN/AGENTS drift | PIT.md SoT; AGENTS one line only. |

**Handoff:** Astra/Jason approve this plan → **fresh IMPL** (do not resume this session).

---

## Learn

- **bite:** PIT raise tickets are schedule/ratchet docs until remasured min clears the next rung; god-splits do not move mutation %.
- **do:** keep live bar at the last honest number; name the next flip + buffer; nightly PIT stays optional/docs while wall &gt;45s.
- **don't:** treat “structure wave done” as permission to hard-fail 80% or land CI YAML for a 9% suite.
