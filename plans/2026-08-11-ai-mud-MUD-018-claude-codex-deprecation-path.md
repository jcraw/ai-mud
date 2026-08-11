# MUD-018 Plan — CLAUDE/CODEX deprecation path (deep status only)

**Ticket:** MUD-018 · **Worker:** grok · **Phase:** plan_review (awaiting Astra approve)  
**Impl = fresh session** after Astra approve. Do not resume plan session for product edits.  
**Plan path:** `plans/2026-08-11-ai-mud-MUD-018-claude-codex-deprecation-path.md`  
**Worker mirror:** `tmp/workers/MUD-018/PLAN.md`  
**Post-impl verify:** docs-only (banner/`rg`); optional `./tools/verify_mud.sh --fast` only if needed (should not)

---

## 1. Goal / acceptance mapping

| # | Acceptance | Impl delivers |
|---|------------|---------------|
| 1 | Banner on `CLAUDE.md` + `CODEX.md`: AGENTS = ops/DoD/startup SoT; these = optional deep/historical; no default full-read | 5–15 line **top** banner (before existing H1 body content or replace lead paragraph) on both files |
| 2 | Stop growing `.claude/settings.local.json` as DoD | **Leave file as-is**; policy in plan + optional 1-line AGENTS note only if missing; not acceptance artifact |
| 3 | Optional slim later — **not** required now | No CLAUDE body rewrite; no delete; freeze growth only |
| 4 | AGENTS still says never full-read CLAUDE by default | **Preserve** Startup Reads item 4; no weakening |

---

## 2. Current inventory

| Item | Truth |
|------|--------|
| **Ops SoT** | `AGENTS.md` — Startup Reads already: never full-read CLAUDE; CODEX/guidelines not mandatory pack. Out of scope points deep status → optional CLAUDE/`docs/*`; MUD-018 named. |
| **CLAUDE.md** | Large Claude-era status narrative. Lead: “guidance to Claude Code…” + V3 overview. **No deprecation banner.** |
| **CODEX.md** | ~30 lines. **Treats CLAUDE as canonical overview** (“Review that first”, “Re-read CLAUDE for system design”). “Never edit CLAUDE/guidelines — Claude SoT.” Collab outdated vs AGENTS. |
| **CLAUDE_GUIDELINES.md** | House style / Linus voice / testing philosophy. Still Claude-framed; not ops DoD. |
| **README.md §Documentation (~128–142)** | Lists CLAUDE/CODEX/guidelines; **Note: “CLAUDE.md is the primary source of truth for project status and architecture.”** Status section still “See CLAUDE.md for detailed…”. |
| **`.claude/settings.local.json`** | Local Claude Code **permissions allow-list** only. Exists; not versioned DoD; not agent contract. |
| **Deps** | MUD-003, MUD-005 **done**. |

---

## 3. Design / recommended approach (lock for Astra)

### 3a. Banner outline (CLAUDE.md + CODEX.md) — 5–15 lines each

Place **above** current title or immediately under a short title. Shared intent (wording may differ slightly per audience):

```
# CLAUDE.md / CODEX.md  (title keep or retitle lightly)

> **Status: optional deep / historical — not ops SoT.**
> - **Ops, DoD, startup reads:** `AGENTS.md` (all agents: Grok, Codex, Claude, Cursor, humans).
> - **This file:** optional deep status / agent-specific notes. **Do not full-read by default.**
> - Architecture detail when needed: `docs/ARCHITECTURE.md` and other `docs/*`.
> - Do **not** grow this file as the agent contract; freeze Claude-only surface growth.
> - Local Claude tool perms (`.claude/settings.local.json`) are **not** DoD — leave local; do not expand as project contract.
```

- **CLAUDE:** keep body intact under banner; only prepend (or replace first 1–2 lead sentences that claim “guidance when working” as primary).
- **CODEX:** banner **plus** collab rewrite of stale bullets (same file, small):
  - Stop “canonical overview lives in CLAUDE / review that first”
  - Point ops/startup → **AGENTS**; deep status optional CLAUDE/`docs/*`
  - Soften “never edit CLAUDE/guidelines unless asked” → prefer neutral shared docs; do not treat Claude files as project SoT (edits still rare / ticket-scoped)
  - “When in doubt” → AGENTS + ticket + `docs/*`, not re-read full CLAUDE

### 3b. README

- **§Documentation:** demote CLAUDE to “optional deep / historical status”; promote **AGENTS.md** as ops/startup/DoD SoT.
- **Replace** “CLAUDE.md is the primary source of truth…” → e.g. ops/startup = `AGENTS.md`; CLAUDE optional deep; architecture = `docs/*`.
- Status “See CLAUDE…” → optional deep pointer; may add AGENTS one-liner. Keep list entries for CODEX/guidelines with demoted wording.
- **No** badge/CI churn beyond existing; docs-only.

### 3c. AGENTS yes/no

- **Default: no body rewrite.** Startup Reads item 4 already correct; Out of scope already lists MUD-018.
- **Optional 1 line only if Astra wants explicit settings policy:** e.g. under Startup or Protected: “`.claude/settings.local.json` = local Claude perms; **not** DoD; do not grow as contract.”
- **Do not** grow AGENTS into architecture or full CLAUDE digest.

### 3d. CLAUDE_GUIDELINES (optional short)

- **Recommend:** 3–8 line top note: engineering style still useful; **ops SoT = AGENTS**; not mandatory startup pack.
- Not required for ticket acceptance if CLAUDE+CODEX+README done; include if cheap.

### 3e. settings.local.json policy

- **Leave file contents as-is.** Do not delete. Do not expand allow-list “as DoD.”
- Document policy only (banner / optional AGENTS line / plan). Not a verify artifact.

### 3f. Explicit non-actions

- Do **not** delete CLAUDE / CODEX / guidelines / `.claude/`.
- Do **not** rewrite CLAUDE body (no huge slim).
- No `*.kt`, Gradle, verify script, test-lock, product code.

---

## 4. Files to create/touch

| Path | Action |
|------|--------|
| `CLAUDE.md` | **Prepend banner only** (body untouched) |
| `CODEX.md` | **Banner + collab pointer fix** (small rewrite; ≤ whole file ~40 lines after) |
| `README.md` | **§Documentation + status SoT claim** — AGENTS primary ops; CLAUDE demoted |
| `CLAUDE_GUIDELINES.md` | **Optional** short top note |
| `AGENTS.md` | **Touch only if** 1-line settings-not-DoD note needed; else leave |
| `.claude/settings.local.json` | **Do not touch** |
| Product / Gradle / verify / test-lock | **Do not touch** |

---

## 5. Non-goals

- MUD-007 playtest, MUD-009 git, MUD-017 residual quarantine
- Huge CLAUDE slim / archive / delete Claude surface
- Product, CI gates, Kotlin, Gradle, verify script, test-lock
- Force-push, secrets, commit-from-plan-session
- Grow AGENTS into architecture status
- Expand `.claude/settings.local.json` as project contract

---

## 6. Impl acceptance checklist

- [ ] `CLAUDE.md` top banner: AGENTS = ops/DoD/startup SoT; this file optional deep; no default full-read
- [ ] `CODEX.md` same banner intent; collab no longer treats CLAUDE as canonical overview
- [ ] `README.md`: no “CLAUDE primary SoT”; ops/startup → AGENTS; CLAUDE optional deep
- [ ] AGENTS Startup Reads still: never full-read CLAUDE by default (unchanged or stronger)
- [ ] `.claude/settings.local.json` unchanged; not cited as DoD artifact
- [ ] No CLAUDE body rewrite; no deletes of Claude/Codex/guidelines/`.claude/`
- [ ] No `*.kt` / Gradle / verify / test-lock churn
- [ ] Docs verify: `rg -n 'primary source of truth' README.md` empty or not CLAUDE; banners present (`rg -n 'AGENTS.md' CLAUDE.md CODEX.md`); optional `--fast` only if something unexpected

---

## 7. Ordered impl steps

1. Confirm deps still done; re-read ticket acceptance only (not full CLAUDE).
2. Prepend banner to `CLAUDE.md` (body zero-diff below banner).
3. Rewrite `CODEX.md` lead/collab: AGENTS ops + optional deep CLAUDE/`docs/*`; keep Codex-specific runtime notes.
4. Edit `README.md` Documentation (+ Current Status pointer): AGENTS first; demote CLAUDE claim.
5. Optional: short top note on `CLAUDE_GUIDELINES.md`; optional 1-line AGENTS settings-not-DoD.
6. **Do not** edit `.claude/settings.local.json`.
7. Verify: `rg` banners + README SoT claim; skip verify script unless dirty tree confuses humans.
8. Closeout: paths, rg results, residual (later slim ticket optional); ticket → done after Astra/impl; **fresh session only**.

---

## 8. Risks

| Risk | Mitigation |
|------|------------|
| Agents still auto-load full CLAUDE via tool defaults | Banner + AGENTS never-full-read; cannot control host IDE — document only |
| CODEX “never edit CLAUDE” fights this ticket | Scope this ticket’s edits as explicit; post-impl CODEX says prefer AGENTS/`docs/*` |
| Scope creep into CLAUDE slim | Hard stop: banner-only on CLAUDE; slim = future ticket |
| Dual SoT residual in other docs | Out of scope; only README claim + CODEX collab this ticket |
| Accidental product/verify touch | Checklist forbids; docs-only verify |
| settings file treated as acceptance | Explicit leave-as-is; not DoD |

**Residual after done:** CLAUDE body still large/historical (OK). Optional future slim/archive ticket. Local `.claude/` may keep growing on machines — policy is “not project DoD,” not file freeze enforcement.


---

Status: APPROVED by Astra 2026-08-11 02:40 MST
Common-sense: docs-only banners + CODEX collab fix + README SoT demote; CLAUDE body freeze; settings.local leave-as-is; no product/verify. Fresh impl session next (plan file handoff).
