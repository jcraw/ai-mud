# MUD-006 — Plan/impl brief templates (PLAN)

**Status:** APPROVED by Astra · **Worker:** grok · **Phase:** implementing  
**Ticket:** `issues/MUD-006-brief-templates.md`  
**Out:** `tmp/workers/MUD-006/`  
**Verify:** docs-only (greps / line checks; no product test run required)

## APPROVED by Astra

- **When:** 2026-08-10 ~20:15 AZ
- **By:** Astra (common-sense plan_review)
- **Verdict:** Approve as written — docs-only templates + short ORCHESTRATION/AGENTS pointers; no product/launcher scope creep.
- **Next:** Fresh IMPL session (never resume plan session).

---

## 1. Goal / acceptance mapping

| Acceptance | How we hit it |
|---|---|
| `issues/_templates/plan-brief.md` + `implement-brief.md` | Create both scaffolds from MUD-003/004/005 patterns |
| Mandatory read capped; CLAUDE.md not default pack | Templates bake lean ≤~6k list + explicit **NEVER** (CLAUDE/CODEX/full BOARD/DIGEST novels) |
| Worker out dir `tmp/workers/<ID>/` documented | Template tokens + short ORCHESTRATION/AGENTS bullets |
| ORCHESTRATION and/or AGENTS points at templates | Replace MUD-006 stub; add 1–2 bullets only |

---

## 2. Current inventory

- **`issues/_templates/`:** only `ticket.md` (frontmatter scaffold; live fields from MUD-005). **No** plan/impl brief templates.
- **Ad-hoc briefs (working pattern):** `tmp/workers/MUD-00{3,4,5}/PLAN_BRIEF.md` (~52–71 lines) + `IMPL_BRIEF.md` (~24–34 lines). Consistent sections; reinvented each drain.
- **Worker out convention (reality):** `tmp/workers/<ID>/` holds `PLAN_BRIEF.md`, `IMPL_BRIEF.md`, `PLAN.md`, logs, pids, `CLOSEOUT.md`, meta — already used; not templated in-repo.
- **ORCHESTRATION:** short ops note; “Out of scope” line: *Plan/impl brief templates → MUD-006*. Worker dirs section already names `tmp/workers/<ID>/`.
- **AGENTS:** Workflow / DoD / serial builder present; no brief-template pointer.
- **BOARD:** “How agents use this” points AGENTS + ORCHESTRATION; MUD-006 under Scheduled / PLAN live.

**Capture from 003/004/005 (keep):**
- Phase lock (PLAN ONLY vs IMPLEMENT fresh)
- Absolute ticket + repo paths
- Lean read + NEVER list
- Dual plan paths: `plans/YYYY-MM-DD-…` + `tmp/workers/<ID>/PLAN.md`
- After-plan: `status: plan_review`, BOARD surgical, STOP
- Fresh impl: never `-r` plan session for product
- Constraints: no secrets, no force-push, serial one builder

---

## 3. Recommended template contents

### Placeholder tokens (replace on fill)
- `{{ID}}` — e.g. `MUD-006`
- `{{TITLE}}` — short title
- `{{TICKET_PATH}}` — absolute or repo-relative ticket path
- `{{REPO}}` — absolute repo root
- `{{PLAN_PATH}}` — `plans/YYYY-MM-DD-ai-mud-{{ID}}-….md`
- `{{WORKER_OUT}}` — `tmp/workers/{{ID}}`
- `{{DEPENDS}}` — depends_on list or `none`
- `{{VERIFY}}` — default `./tools/verify_mud.sh` or `docs-only`
- `{{READ_EXTRA}}` — ticket-specific lean reads (bullets; keep pack ≤~6k total)
- `{{INTENT}}` / `{{DO_THIS}}` — binding work bullets
- `{{NON_GOALS}}` — explicit out-of-scope
- `{{DATE}}` — YYYY-MM-DD for plan filename / frontmatter

### `issues/_templates/plan-brief.md` (Turn-1 PLAN ONLY)

Outline (target ≤~80 lines filled):
1. `# Task: {{ID}} PLAN ONLY — {{TITLE}}`
2. Role line: fresh session, Turn 1 plan phase
3. **Phase** — PLAN ONLY; bookkeeping + plan files only; **not** impl approval
4. **Ticket** / **Repo / cwd** — absolute paths
5. **Read first (lean ≤~6k)** — ticket; AGENTS Workflow/DoD only; ORCHESTRATION if ops; ticket-relevant files; `{{READ_EXTRA}}`
6. **NEVER full-read** — CLAUDE.md, CODEX.md, whole BOARD as pack, DIGEST bodies, full worker logs, game_jam novels
7. **Intent (binding)** — `{{INTENT}}`
8. **Deliverable** — dual write `plans/…` + `{{WORKER_OUT}}/PLAN.md`; plan size ≤~2k tok; required plan sections (goal, inventory, design, files, non-goals, acceptance check, ordered steps, risks)
9. **After plan written** — ticket frontmatter `plan_review`; BOARD surgical; STOP
10. **Constraints** — serial one builder; no secrets; no force-push; no product unless ticket says; no git commit/push unless asked

### `issues/_templates/implement-brief.md` (fresh IMPL after APPROVED)

Outline (target ≤~40 lines filled):
1. `# {{ID}} — IMPLEMENT (fresh session)`
2. Fresh session; no prior CLI memory
3. **Plan already APPROVED** — paths to `{{PLAN_PATH}}` + mirror `{{WORKER_OUT}}/PLAN.md`
4. Authorization line: this message **is** plan approval + implement authorization
5. **Do this turn** — read plan + ticket acceptance only; execute plan; self-check; closeout (acceptance boxes, ticket `done`, BOARD surgical, optional CLOSEOUT.md)
6. **Rules** — follow approved plan only; no redesign/re-plan unless blocked; no secrets/force-push; serial one builder; STOP when done
7. **Ticket** path
8. **verify (post-impl)** — `{{VERIFY}}` + plan checklist greps

### Shared discipline (both templates)
- Default pack ≤~6k; plans ≤~2k tok
- CLAUDE/CODEX **not** in default pack
- Worker artifacts: `{{WORKER_OUT}}/{PLAN_BRIEF,IMPL_BRIEF,PLAN,logs,CLOSEOUT}`
- Console+GUI parity note only when ticket is product handlers (optional one-liner in impl Rules for engine/client area)

---

## 4. Files to create/touch

| Path | Action |
|---|---|
| `issues/_templates/plan-brief.md` | **Create** |
| `issues/_templates/implement-brief.md` | **Create** |
| `issues/ORCHESTRATION.md` | Surgical: replace MUD-006 out-of-scope stub with pointer to both templates; optional 1 bullet under Plan→approve→fresh impl |
| `AGENTS.md` | Optional 1 short bullet under Workflow pointing at templates (keep lean; prefer ORCHESTRATION if AGENTS already dense) — **impl chooses: at least one of ORCHESTRATION or AGENTS; prefer both as 1-liners** |
| `issues/MUD-006-brief-templates.md` | Impl closeout: acceptance checkboxes, `status: done` |
| `issues/BOARD.md` | Surgical status moves only |

**No** `*.kt`, verify script, launcher, ticket.md redesign.

---

## 5. How Astra/drain fills templates

1. `cp issues/_templates/plan-brief.md tmp/workers/{{ID}}/PLAN_BRIEF.md` (or write filled copy).
2. Replace all `{{…}}` tokens; expand `{{INTENT}}` / `{{READ_EXTRA}}` from ticket acceptance.
3. Keep filled brief short (PLAN ~50–80 lines, IMPL ~25–40 — match 003–005 sizes).
4. Spawn **fresh** plan session with filled PLAN_BRIEF (not resume).
5. After APPROVED: fill `implement-brief.md` → `tmp/workers/{{ID}}/IMPL_BRIEF.md`; spawn **fresh** impl (never `-r` plan session).
6. No auto-fill script required this ticket (manual copy/replace OK).

---

## 6. Non-goals

- No launcher / supervisor rewrite
- No `tools/verify_mud.sh` changes
- No product / Kotlin
- No MUD-009 git hygiene policy
- No auto-fill scripts, Jinja, or generator required
- No game_jam ORCHESTRATION novel import
- No full AGENTS.md rewrite
- No git commit/push unless Jason asks

---

## 7. How impl confirms acceptance (checklist)

- [ ] `test -f issues/_templates/plan-brief.md && test -f issues/_templates/implement-brief.md`
- [ ] Both contain: Phase/fresh language, Ticket, lean Read/NEVER (or impl equivalent), worker out `tmp/workers/`, dual plan paths (plan) or APPROVED plan paths (impl)
- [ ] Grep: `CLAUDE.md` appears under NEVER / not-default (not as required read)
- [ ] Grep: `tmp/workers` convention present in at least one template + ORCHESTRATION or AGENTS pointer
- [ ] `rg -n 'plan-brief|implement-brief|_templates/' issues/ORCHESTRATION.md AGENTS.md` hits ≥1 file
- [ ] ORCHESTRATION no longer lists MUD-006 as future-only stub for templates
- [ ] Ticket acceptance boxes checked; BOARD MUD-006 → Recently done (surgical)
- [ ] Optional: `wc -l` templates ≤ ~100 each (scaffold before fill)

---

## 8. Ordered impl steps

1. Create `issues/_templates/plan-brief.md` per §3.
2. Create `issues/_templates/implement-brief.md` per §3.
3. Point `issues/ORCHESTRATION.md` at templates (replace MUD-006 stub; 1–2 bullets).
4. Optional: one AGENTS Workflow bullet → templates (if keeps AGENTS lean).
5. Run §7 greps / line checks.
6. Ticket closeout + BOARD surgical + optional `tmp/workers/MUD-006/CLOSEOUT.md`.
7. STOP.

---

## 9. Risks

| Risk | Mitigation |
|---|---|
| Templates too long → blow 6k pack | Scaffold short; guidance “filled ≤80/40 lines”; no embedded policy essays |
| Over-coupling to game_jam novels | Copy **structure** from ai-mud MUD-003–005 only; no jam prose |
| AGENTS grows again | Prefer ORCHESTRATION pointer; AGENTS one line max |
| Drainers still invent briefs | ORCHESTRATION “use these templates” bullet makes default path obvious |
| Token placeholders confusing | Document fill step in ORCHESTRATION one-liner + examples in template comments if needed (≤5 lines HTML/MD comments) |

---

## Residual for later tickets

- Auto-fill / launcher hook for templates (not MUD-006)
- Optional closeout template (out of scope unless trivial one-liner in worker convention bullet)
